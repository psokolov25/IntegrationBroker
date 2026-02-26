package ru.aritmos.integrationbroker.databus;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.FlowEngine;
import ru.aritmos.integrationbroker.core.RestOutboxService;
import ru.aritmos.integrationbroker.core.SensitiveDataSanitizer;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groovy-адаптер для публикации событий в DataBus.
 *
 * <p>Экспортируется в Groovy как переменная {@code bus}.
 *
 * <p>DataBus в референсе принимает события через REST endpoint:
 * {@code POST /databus/events/types/{type}} и требует заголовки:
 * <ul>
 *   <li>{@code Service-Destination} — целевой сервис (или список через запятую, или {@code *});</li>
 *   <li>{@code Send-To-OtherBus} — пересылать ли на другие шины;</li>
 *   <li>{@code Send-Date} — дата RFC1123;</li>
 *   <li>{@code Service-Sender} — имя сервиса-отправителя.</li>
 * </ul>
 *
 * <p>Важно: данный адаптер не хранит и не логирует секреты.
 * Авторизация берётся из {@code restConnectors} по {@code dataBus.connectorId}.
 */
@Singleton
@FlowEngine.GroovyExecutable("bus")
public class DataBusGroovyAdapter {

    private static final DateTimeFormatter RFC1123 = DateTimeFormatter.RFC_1123_DATE_TIME;

    private final RuntimeConfigStore configStore;
    private final RestOutboxService restOutboxService;

    public DataBusGroovyAdapter(RuntimeConfigStore configStore, RestOutboxService restOutboxService) {
        this.configStore = configStore;
        this.restOutboxService = restOutboxService;
    }

    /**
     * Публикация события в DataBus.
     */
    public long publishEvent(String type, String destination, Object body) {
        return publishEvent(type, destination, null, body, null, null, null);
    }

    /**
     * Публикация события с явным флагом пересылки на другие шины.
     */
    public long publishEvent(String type, String destination, Boolean sendToOtherBus, Object body) {
        return publishEvent(type, destination, sendToOtherBus, body, null, null, null);
    }

    /**
     * Публикация события с метаданными трассировки.
     */
    public long publishEvent(String type,
                             String destination,
                             Boolean sendToOtherBus,
                             Object body,
                             String sourceMessageId,
                             String correlationId,
                             String idempotencyKey) {
        RuntimeConfigStore.RuntimeConfig eff = requireConfig();
        RuntimeConfigStore.DataBusIntegrationConfig cfg = eff.dataBus();

        String path = pathOrDefault(cfg.publishEventPathTemplate(), "/databus/events/types/{type}")
                .replace("{type}", safe(type, "UNKNOWN"));
        return doCall(eff, cfg, destination, sendToOtherBus, path, body, sourceMessageId, correlationId, idempotencyKey, null, null, true);
    }

    /**
     * Прототип DataBus request с метаданными трассировки.
     */

    /**
     * Публикация события в DataBus с маршрутизацией на список внешних шин.
     */
    public long publishEventRoute(String type,
                                  String destination,
                                  List<String> dataBusUrls,
                                  Object body,
                                  String sourceMessageId,
                                  String correlationId,
                                  String idempotencyKey) {
        RuntimeConfigStore.RuntimeConfig eff = requireConfig();
        RuntimeConfigStore.DataBusIntegrationConfig cfg = eff.dataBus();

        String path = pathOrDefault(cfg.routeEventPathTemplate(), "/databus/events/types/{type}/route")
                .replace("{type}", safe(type, "UNKNOWN"));

        Map<String, Object> routeBody = Map.of(
                "dataBusUrls", dataBusUrls == null ? List.of() : dataBusUrls,
                "body", body
        );

        return doCall(eff, cfg, destination, null, path, routeBody, sourceMessageId, correlationId, idempotencyKey, null, null, false);
    }

    public long sendRequest(String function,
                            String destination,
                            Boolean sendToOtherBus,
                            Map<String, Object> params,
                            String sourceMessageId,
                            String correlationId,
                            String idempotencyKey) {
        RuntimeConfigStore.RuntimeConfig eff = requireConfig();
        RuntimeConfigStore.DataBusIntegrationConfig cfg = eff.dataBus();

        String functionName = safe(function, "unknown");
        String path = pathOrDefault(cfg.requestPathTemplate(), "/databus/requests/{function}")
                .replace("{function}", functionName);
        Object body = params == null ? Map.of() : params;
        return doCall(eff, cfg, destination, sendToOtherBus, path, body, sourceMessageId, correlationId, idempotencyKey, null, null, true);
    }

    /**
     * Прототип DataBus response с метаданными трассировки.
     */
    public long sendResponse(String destination,
                             Boolean sendToOtherBus,
                             Integer status,
                             String message,
                             Object response,
                             String sourceMessageId,
                             String correlationId,
                             String idempotencyKey) {
        RuntimeConfigStore.RuntimeConfig eff = requireConfig();
        RuntimeConfigStore.DataBusIntegrationConfig cfg = eff.dataBus();

        String path = pathOrDefault(cfg.responsePathTemplate(), "/databus/responses");
        return doCall(
                eff,
                cfg,
                destination,
                sendToOtherBus,
                path,
                response,
                sourceMessageId,
                correlationId,
                idempotencyKey,
                status,
                message,
                true
        );
    }

    private long doCall(RuntimeConfigStore.RuntimeConfig eff,
                        RuntimeConfigStore.DataBusIntegrationConfig cfg,
                        String destination,
                        Boolean sendToOtherBus,
                        String path,
                        Object body,
                        String sourceMessageId,
                        String correlationId,
                        String idempotencyKey,
                        Integer responseStatus,
                        String responseMessage,
                        boolean includeForwardHeader) {
        boolean forward = sendToOtherBus != null ? sendToOtherBus : cfg.defaultSendToOtherBus();

        Map<String, String> headers = baseHeaders(cfg, destination, forward, includeForwardHeader);
        appendTraceHeaders(headers, sourceMessageId, correlationId);
        if (responseStatus != null) {
            headers.put(safe(cfg.responseStatusHeaderName(), "Response-Status"), String.valueOf(responseStatus));
        }
        if (safeNullable(responseMessage) != null) {
            headers.put(safe(cfg.responseMessageHeaderName(), "Response-Message"), responseMessage.trim());
        }

        String requestId = safeNullable(sourceMessageId);
        if (requestId == null) {
            requestId = safeNullable(correlationId);
        }
        String idKey = safeNullable(idempotencyKey);
        if (idKey == null) {
            idKey = requestId;
        }

        return restOutboxService.callViaConnector(
                eff,
                eff.restOutbox(),
                cfg.connectorId(),
                "POST",
                path,
                SensitiveDataSanitizer.sanitizeHeaders(headers),
                body,
                idKey,
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
    }

    private RuntimeConfigStore.RuntimeConfig requireConfig() {
        RuntimeConfigStore.RuntimeConfig eff = configStore.getEffective();
        RuntimeConfigStore.DataBusIntegrationConfig cfg = eff == null ? null : eff.dataBus();
        if (cfg == null || !cfg.enabled()) {
            throw new IllegalStateException("DataBus интеграция отключена (dataBus.enabled=false)");
        }
        return eff;
    }

    private static Map<String, String> baseHeaders(RuntimeConfigStore.DataBusIntegrationConfig cfg,
                                                   String destination,
                                                   boolean forward,
                                                   boolean includeForwardHeader) {
        Map<String, String> headers = new HashMap<>();
        headers.put(cfg.destinationHeaderName(), safe(destination, "*"));
        if (includeForwardHeader) {
            headers.put(cfg.sendToOtherBusHeaderName(), String.valueOf(forward));
        }
        headers.put(cfg.sendDateHeaderName(), RFC1123.format(ZonedDateTime.now(ZoneId.of("GMT"))));
        headers.put(cfg.senderHeaderName(), safe(cfg.defaultSenderServiceName(), "integration-broker"));
        return headers;
    }

    private static void appendTraceHeaders(Map<String, String> headers,
                                           String sourceMessageId,
                                           String correlationId) {
        String corr = safeNullable(correlationId);
        if (corr != null) {
            headers.put("X-Correlation-Id", corr);
        }

        String requestId = safeNullable(sourceMessageId);
        if (requestId == null) {
            requestId = corr;
        }
        if (requestId != null) {
            headers.put("X-Request-Id", requestId);
        }
    }

    private static String pathOrDefault(String path, String def) {
        String p = safeNullable(path);
        return p == null ? def : p;
    }

    private static String safeNullable(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String safe(String v, String def) {
        if (v == null) {
            return def;
        }
        String t = v.trim();
        return t.isEmpty() ? def : t;
    }
}
