package ru.aritmos.integrationbroker.visionlabs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.InboundDlqService;
import ru.aritmos.integrationbroker.core.InboundProcessingService;
import ru.aritmos.integrationbroker.core.SensitiveDataSanitizer;
import ru.aritmos.integrationbroker.model.InboundEnvelope;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Получение результатов аналитики VisionLabs, сохранённых в Events (callback type: luna-event).
 * <p>
 * В этом режиме агент не пушит события напрямую в Integration Broker.
 * Вместо этого события сохраняются в базу Events, а Integration Broker периодически
 * опрашивает API ("get general events") с фильтром по {@code stream_id}.
 * <p>
 * Важно:
 * <ul>
 *   <li>Checkpoint хранится в PostgreSQL (таблица ib_visionlabs_events_checkpoint);</li>
 *   <li>Обработка событий выполняется через общий pipeline (idempotency/DLQ/outbox);</li>
 *   <li>Payload не логируется.</li>
 * </ul>
 */
@Singleton
public class VisionLabsEventsPoller {

    private static final Logger log = LoggerFactory.getLogger(VisionLabsEventsPoller.class);

    private final RuntimeConfigStore configStore;
    private final VisionLabsAnalyticsIngressService ingressService;
    private final InboundDlqService inboundDlqService;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile Instant lastPoll = Instant.EPOCH;

    public VisionLabsEventsPoller(RuntimeConfigStore configStore,
                                 VisionLabsAnalyticsIngressService ingressService,
                                 InboundDlqService inboundDlqService,
                                 DataSource dataSource,
                                 ObjectMapper objectMapper) {
        this.configStore = configStore;
        this.ingressService = ingressService;
        this.inboundDlqService = inboundDlqService;
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    /**
     * Пуллинг выполняется часто (например, раз в 2 секунды), но реальный интервал управляется runtime-config.
     * Такой подход позволяет менять интервал без перезапуска и без динамической перестройки scheduler.
     */
    @Scheduled(fixedDelay = "2s")
    void poll() {
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        RuntimeConfigStore.VisionLabsAnalyticsConfig vcfg = cfg.visionLabsAnalytics();
        if (vcfg == null || !vcfg.enabled() || vcfg.events() == null || !vcfg.events().enabled()) {
            return;
        }

        int intervalSec = Math.max(1, vcfg.events().pollIntervalSec());
        if (Instant.now().isBefore(lastPoll.plusSeconds(intervalSec))) {
            return;
        }
        lastPoll = Instant.now();

        String connectorId = vcfg.events().connectorId();
        RuntimeConfigStore.RestConnectorConfig connector = cfg.restConnectors().get(connectorId);
        if (connector == null || connector.baseUrl() == null || connector.baseUrl().isBlank()) {
            log.warn("VisionLabs events poller: не найден REST-коннектор '{}' в restConnectors", connectorId);
            return;
        }

        List<String> streamIds = (vcfg.events().streamIds() == null) ? List.of() : vcfg.events().streamIds();
        if (streamIds.isEmpty()) {
            log.warn("VisionLabs events poller: streamIds пуст — нечего опрашивать");
            return;
        }

        for (String streamId : streamIds) {
            if (streamId == null || streamId.isBlank()) {
                continue;
            }
            pollStream(cfg, vcfg, connector, streamId.trim());
        }
    }

    private void pollStream(RuntimeConfigStore.RuntimeConfig cfg,
                            RuntimeConfigStore.VisionLabsAnalyticsConfig vcfg,
                            RuntimeConfigStore.RestConnectorConfig connector,
                            String streamId) {
        String checkpointKey = "visionlabs-events:" + streamId;
        String lastEventId = getCheckpoint(checkpointKey);

        try {
            String url = buildEventsUrl(connector.baseUrl(), vcfg.events(), streamId, lastEventId);
            String body = httpGet(url, connector);

            JsonNode root = objectMapper.readTree(body);
            JsonNode listNode = root.at(safePointer(vcfg.events().listJsonPointer(), "/events"));
            if (listNode == null || !listNode.isArray()) {
                return;
            }

            List<JsonNode> events = new ArrayList<>();
            for (JsonNode n : listNode) {
                if (n != null && !n.isNull()) {
                    events.add(n);
                }
            }

            String maxId = lastEventId;
            for (JsonNode ev : events) {
                String evId = ev.at(safePointer(vcfg.events().idJsonPointer(), "/id")).asText(null);
                if (evId != null) {
                    maxId = pickMaxId(maxId, evId);
                }

                Map<String, Object> sourceMeta = new LinkedHashMap<>();
                sourceMeta.put("streamId", streamId);
                if (evId != null) {
                    sourceMeta.put("eventId", evId);
                }

                try {
                    ingressService.ingestJson("luna-event", ev, Map.of(), sourceMeta);
                } catch (InboundProcessingService.StoredInDlqException ex) {
                    // Уже сохранено в DLQ — можно продолжать.
                } catch (IllegalArgumentException ex) {
                    // Например, нет flow. Чтобы не потерять событие, пытаемся сохранить в DLQ вручную.
                    storeNoFlowToDlq(vcfg, ev, streamId, evId, ex.getMessage());
                }
            }

            // Продвигаем checkpoint после прохода, чтобы исключить бесконечные повторы.
            if (maxId != null && (lastEventId == null || !maxId.equals(lastEventId))) {
                upsertCheckpoint(checkpointKey, maxId);
            }
        } catch (Exception e) {
            log.warn("VisionLabs events poller: ошибка опроса streamId='{}': {}", streamId, SensitiveDataSanitizer.sanitizeText(e.getMessage()));
        }
    }

    private void storeNoFlowToDlq(RuntimeConfigStore.VisionLabsAnalyticsConfig vcfg,
                                 JsonNode payload,
                                 String streamId,
                                 String eventId,
                                 String error) {
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        if (cfg.inboundDlq() == null || !cfg.inboundDlq().enabled()) {
            return;
        }
        String safe = SensitiveDataSanitizer.sanitizeText(error);

        Map<String, Object> src = new LinkedHashMap<>();
        src.put("source", "visionlabs");
        src.put("callbackType", "luna-event");
        src.put("streamId", streamId);
        if (eventId != null) {
            src.put("eventId", eventId);
        }

        InboundEnvelope env = new InboundEnvelope(
                InboundEnvelope.Kind.EVENT,
                (vcfg.inboundTypePrefix() == null ? "visionlabs.analytics." : vcfg.inboundTypePrefix()) + "generic",
                payload,
                Map.of(),
                eventId == null ? null : ("visionlabs:luna-event:" + eventId),
                streamId,
                null,
                null,
                src
        );

        inboundDlqService.put(env, null, "NO_FLOW", safe, cfg.inboundDlq().maxAttempts(), cfg.inboundDlq().sanitizeHeaders());
    }

    private String buildEventsUrl(String baseUrl,
                                  RuntimeConfigStore.VisionLabsEventsConfig ec,
                                  String streamId,
                                  String lastEventId) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = (ec.path() == null || ec.path().isBlank()) ? "/events" : ec.path();
        String p = path.startsWith("/") ? path : ("/" + path);

        String streamParam = (ec.streamIdParam() == null || ec.streamIdParam().isBlank()) ? "stream_id" : ec.streamIdParam();
        String afterParam = (ec.afterIdParam() == null || ec.afterIdParam().isBlank()) ? "after_id" : ec.afterIdParam();
        String limitParam = (ec.limitParam() == null || ec.limitParam().isBlank()) ? "limit" : ec.limitParam();
        int limit = Math.min(Math.max(1, ec.limit()), 500);

        StringBuilder q = new StringBuilder();
        q.append(streamParam).append("=").append(urlEncode(streamId));
        if (lastEventId != null && !lastEventId.isBlank()) {
            q.append("&").append(afterParam).append("=").append(urlEncode(lastEventId));
        }
        q.append("&").append(limitParam).append("=").append(limit);

        return base + p + "?" + q;
    }

    private String httpGet(String url, RuntimeConfigStore.RestConnectorConfig connector) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET();

        Map<String, String> authHeaders = buildAuthHeaders(connector);
        for (Map.Entry<String, String> e : authHeaders.entrySet()) {
            b.header(e.getKey(), e.getValue());
        }

        HttpResponse<String> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString());
        int sc = resp.statusCode();
        if (sc < 200 || sc >= 300) {
            throw new IllegalStateException("HTTP_" + sc);
        }
        return resp.body() == null ? "" : resp.body();
    }

    private Map<String, String> buildAuthHeaders(RuntimeConfigStore.RestConnectorConfig connector) {
        if (connector == null || connector.auth() == null || connector.auth().type() == null) {
            return Map.of();
        }
        RuntimeConfigStore.RestConnectorAuth a = connector.auth();
        return switch (a.type()) {
            case NONE -> Map.of();
            case BEARER -> (a.bearerToken() == null || a.bearerToken().isBlank())
                    ? Map.of()
                    : Map.of("Authorization", "Bearer " + a.bearerToken());
            case BASIC -> {
                if (a.basicUsername() == null || a.basicPassword() == null) {
                    yield Map.of();
                }
                String token = java.util.Base64.getEncoder().encodeToString((a.basicUsername() + ":" + a.basicPassword()).getBytes());
                yield Map.of("Authorization", "Basic " + token);
            }
            case API_KEY_HEADER -> {
                String hn = (a.headerName() == null || a.headerName().isBlank()) ? "X-Api-Key" : a.headerName();
                if (a.apiKey() == null || a.apiKey().isBlank()) {
                    yield Map.of();
                }
                yield Map.of(hn, a.apiKey());
            }
            case OAUTH2_CLIENT_CREDENTIALS -> Map.of();
        };
    }

    private String getCheckpoint(String sourceId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT last_event_id FROM ib_visionlabs_events_checkpoint WHERE source_id=?")) {
            ps.setString(1, sourceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (Exception e) {
            // no-op
        }
        return null;
    }

    private void upsertCheckpoint(String sourceId, String lastEventId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO ib_visionlabs_events_checkpoint(source_id,last_event_id,updated_at) VALUES (?,?,?) " +
                             "ON CONFLICT (source_id) DO UPDATE SET last_event_id=EXCLUDED.last_event_id, updated_at=EXCLUDED.updated_at")) {
            ps.setString(1, sourceId);
            ps.setString(2, lastEventId);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (Exception e) {
            // no-op
        }
    }

    private String pickMaxId(String a, String b) {
        // Если идентификаторы числовые — сравниваем как long, иначе сравниваем лексикографически.
        try {
            if (a == null) {
                return b;
            }
            long la = Long.parseLong(a);
            long lb = Long.parseLong(b);
            return (lb > la) ? b : a;
        } catch (Exception ignore) {
            if (a == null) {
                return b;
            }
            return (b != null && b.compareTo(a) > 0) ? b : a;
        }
    }

    private String safePointer(String p, String fallback) {
        if (p == null || p.isBlank()) {
            return fallback;
        }
        return p.startsWith("/") ? p : ("/" + p);
    }

    private String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
