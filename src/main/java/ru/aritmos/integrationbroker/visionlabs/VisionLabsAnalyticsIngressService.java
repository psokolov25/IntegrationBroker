package ru.aritmos.integrationbroker.visionlabs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.InboundProcessingService;
import ru.aritmos.integrationbroker.core.SensitiveDataSanitizer;
import ru.aritmos.integrationbroker.model.InboundEnvelope;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Единая точка приёма результатов аналитики VisionLabs (LUNA PLATFORM).
 * <p>
 * VisionLabs Video Manager / Agent может отдавать события (результаты аналитики) через разные механизмы callback:
 * <ul>
 *   <li><b>http</b> — POST на заранее подготовленный endpoint;</li>
 *   <li><b>luna-ws-notification</b> — WebSocket уведомления;</li>
 *   <li><b>luna-event</b> — сохранение в Events и последующая выборка через API;</li>
 *   <li><b>luna-kafka</b> — публикация в Kafka (topic).</li>
 * </ul>
 * <p>
 * Этот сервис нормализует полученные события в {@link InboundEnvelope} и запускает основной pipeline
 * (idempotency/enrichment/flow/DLQ/outbox).
 * <p>
 * Важно:
 * <ul>
 *   <li>в качестве messageId используется устойчивое значение (если возможно) либо хэш payload;</li>
 *   <li>сырой токен/секреты не логируются и не сохраняются;</li>
 *   <li>изображения (base64) не должны попадать в DLQ/outbox — для этого предназначены отдельные хранилища.</li>
 * </ul>
 */
@Singleton
public class VisionLabsAnalyticsIngressService {

    private final RuntimeConfigStore configStore;
    private final InboundProcessingService processingService;
    private final ObjectMapper objectMapper;

    public VisionLabsAnalyticsIngressService(RuntimeConfigStore configStore,
                                            InboundProcessingService processingService,
                                            ObjectMapper objectMapper) {
        this.configStore = configStore;
        this.processingService = processingService;
        this.objectMapper = objectMapper;
    }

    /**
     * Принять событие аналитики в виде JSON.
     *
     * @param callbackType тип источника (http / luna-ws-notification / luna-event / luna-kafka)
     * @param jsonPayload JSON полезной нагрузки
     * @param headers нормализованные заголовки (уже без секретов)
     * @param sourceMeta служебная информация источника
     * @return результат обработки
     */
    public InboundProcessingService.ProcessingResult ingestJson(String callbackType,
                                                               JsonNode jsonPayload,
                                                               Map<String, String> headers,
                                                               Map<String, Object> sourceMeta) {
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        RuntimeConfigStore.VisionLabsAnalyticsConfig vcfg = cfg.visionLabsAnalytics();
        if (vcfg == null || !vcfg.enabled()) {
            throw new IllegalArgumentException("Приём VisionLabs аналитики отключён (visionLabsAnalytics.enabled=false)");
        }

        String type = buildInboundType(vcfg, jsonPayload);
        String messageId = pickMessageId(jsonPayload, callbackType);
        String correlationId = pickCorrelationId(jsonPayload);

        Map<String, Object> meta = new HashMap<>();
        meta.put("source", "visionlabs");
        meta.put("callbackType", callbackType);
        meta.put("receivedAt", Instant.now().toString());
        if (sourceMeta != null) {
            meta.putAll(sourceMeta);
        }

        String branchId = pickFirstString(jsonPayload, "branchId", "branch_id", "officeId");
        String userId = pickFirstString(jsonPayload, "userId", "user_id", "operatorId");

        InboundEnvelope env = new InboundEnvelope(
                InboundEnvelope.Kind.EVENT,
                type,
                jsonPayload,
                headers == null ? Map.of() : headers,
                messageId,
                correlationId,
                branchId,
                userId,
                meta
        );

        return processingService.process(env);
    }

    /**
     * Распарсить тело как JSON и принять событие.
     *
     * @param callbackType тип источника
     * @param rawBody тело запроса/сообщения
     * @param headers нормализованные заголовки
     * @param sourceMeta служебные поля источника
     * @return результат обработки
     */
    public InboundProcessingService.ProcessingResult ingestJson(String callbackType,
                                                               String rawBody,
                                                               Map<String, String> headers,
                                                               Map<String, Object> sourceMeta) {
        try {
            JsonNode node = objectMapper.readTree(rawBody);
            return ingestJson(callbackType, node, headers, sourceMeta);
        } catch (Exception e) {
            throw new IllegalArgumentException("Некорректный JSON от VisionLabs: " + SensitiveDataSanitizer.sanitizeText(e.getMessage()));
        }
    }

    private String buildInboundType(RuntimeConfigStore.VisionLabsAnalyticsConfig vcfg, JsonNode payload) {
        String base = (vcfg.inboundTypePrefix() == null || vcfg.inboundTypePrefix().isBlank())
                ? "visionlabs.analytics."
                : vcfg.inboundTypePrefix();

        String raw = pickFirstString(payload, "event_type", "eventType", "type", "analytics", "name");
        if (raw == null || raw.isBlank()) {
            return base + "generic";
        }
        String normalized = raw.trim()
                .replace(' ', '.')
                .replace('/', '.')
                .replace('\\', '.')
                .replace(':', '.')
                .replaceAll("[^A-Za-z0-9._-]", "");
        if (normalized.isBlank()) {
            normalized = "generic";
        }
        return base + normalized;
    }

    private String pickMessageId(JsonNode payload, String callbackType) {
        // Пытаемся извлечь устойчивый id события.
        String id = pickFirstString(payload, "event_id", "eventId", "id", "uuid", "request_id", "requestId");
        if (id != null && !id.isBlank()) {
            return "visionlabs:" + callbackType + ":" + id.trim();
        }
        // Fallback: хэш payload -> устойчиво для повторной доставки.
        String text = payload == null ? "" : payload.toString();
        return "visionlabs:" + callbackType + ":hash:" + sha256Short(text);
    }

    private String pickCorrelationId(JsonNode payload) {
        String cid = pickFirstString(payload, "correlation_id", "correlationId", "stream_id", "streamId", "track_id", "trackId");
        return (cid == null || cid.isBlank()) ? null : cid.trim();
    }

    private String pickFirstString(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        for (String k : keys) {
            if (k == null) {
                continue;
            }
            JsonNode v = node.get(k);
            if (v != null && !v.isNull()) {
                String s = v.asText(null);
                if (s != null && !s.isBlank()) {
                    return s;
                }
            }
        }
        return null;
    }

    private String sha256Short(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig).substring(0, 16);
        } catch (Exception e) {
            return "na";
        }
    }
}
