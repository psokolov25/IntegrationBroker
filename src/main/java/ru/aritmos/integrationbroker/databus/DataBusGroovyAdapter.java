package ru.aritmos.integrationbroker.databus;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.CorrelationContext;
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
     * Каноническая публикация события VISIT_CREATE с минимальным payload контрактом.
     */
    public long publishVisitCreate(String destination,
                                   String branchId,
                                   String entryPointId,
                                   List<String> serviceIds,
                                   Map<String, String> parameters,
                                   boolean printTicket,
                                   String segmentationRuleId,
                                   String sourceMessageId,
                                   String correlationId,
                                   String idempotencyKey) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("branchId", safeNullable(branchId));
        payload.put("entryPointId", safeNullable(entryPointId));
        payload.put("serviceIds", serviceIds == null ? List.of() : List.copyOf(serviceIds));
        payload.put("parameters", parameters == null ? Map.of() : Map.copyOf(parameters));
        payload.put("printTicket", printTicket);
        payload.put("segmentationRuleId", safeNullable(segmentationRuleId));
        return publishEvent("VISIT_CREATE", destination, null, payload, sourceMessageId, correlationId, idempotencyKey);
    }



    /**
     * Каноническая публикация события VISIT_UPDATED (например, после updateVisitParameters).
     */
    public long publishVisitUpdated(String destination,
                                    String visitId,
                                    Map<String, String> parameters,
                                    String sourceMessageId,
                                    String correlationId,
                                    String idempotencyKey) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("visitId", safeNullable(visitId));
        payload.put("parameters", parameters == null ? Map.of() : Map.copyOf(parameters));
        return publishEvent("VISIT_UPDATED", destination, null, payload, sourceMessageId, correlationId, idempotencyKey);
    }


    /**
     * Каноническая публикация события VISIT_CALLED для service-point цикла.
     */
    public long publishVisitCalled(String destination,
                                   String branchId,
                                   String servicePointId,
                                   String visitId,
                                   String sourceMessageId,
                                   String correlationId,
                                   String idempotencyKey) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("branchId", safeNullable(branchId));
        payload.put("servicePointId", safeNullable(servicePointId));
        payload.put("visitId", safeNullable(visitId));
        return publishEvent("VISIT_CALLED", destination, null, payload, sourceMessageId, correlationId, idempotencyKey);
    }


    /**
     * Каноническая публикация события VISIT_POSTPONED для service-point цикла.
     */
    public long publishVisitPostponed(String destination,
                                      String branchId,
                                      String servicePointId,
                                      String visitId,
                                      String sourceMessageId,
                                      String correlationId,
                                      String idempotencyKey) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("branchId", safeNullable(branchId));
        payload.put("servicePointId", safeNullable(servicePointId));
        payload.put("visitId", safeNullable(visitId));
        return publishEvent("VISIT_POSTPONED", destination, null, payload, sourceMessageId, correlationId, idempotencyKey);
    }


    /**
     * Каноническая публикация события AUTO_CALL_STATE_CHANGED.
     */
    public long publishAutoCallStateChanged(String destination,
                                            String branchId,
                                            String servicePointId,
                                            boolean enabled,
                                            String sourceMessageId,
                                            String correlationId,
                                            String idempotencyKey) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("branchId", safeNullable(branchId));
        payload.put("servicePointId", safeNullable(servicePointId));
        payload.put("enabled", enabled);
        return publishEvent("AUTO_CALL_STATE_CHANGED", destination, null, payload, sourceMessageId, correlationId, idempotencyKey);
    }


    /**
     * Каноническая публикация события SERVICE_POINT_MODE_CHANGED.
     */
    public long publishServicePointModeChanged(String destination,
                                               String branchId,
                                               String mode,
                                               Boolean entered,
                                               String sourceMessageId,
                                               String correlationId,
                                               String idempotencyKey) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("branchId", safeNullable(branchId));
        payload.put("mode", safeNullable(mode));
        payload.put("entered", entered);
        return publishEvent("SERVICE_POINT_MODE_CHANGED", destination, null, payload, sourceMessageId, correlationId, idempotencyKey);
    }

    /**
     * Каноническая публикация события BRANCH_STATE_SNAPSHOT.
     */
    public long publishBranchStateSnapshot(String destination,
                                           String branchId,
                                           Object state,
                                           String sourceMessageId,
                                           String correlationId,
                                           String idempotencyKey) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("branchId", safeNullable(branchId));
        payload.put("state", state);
        return publishEvent("BRANCH_STATE_SNAPSHOT", destination, null, payload, sourceMessageId, correlationId, idempotencyKey);
    }

    /**
     * Каноническая route-публикация события VISIT_CREATE.
     */
    public long publishVisitCreateRoute(String destination,
                                        List<String> dataBusUrls,
                                        String branchId,
                                        String entryPointId,
                                        List<String> serviceIds,
                                        Map<String, String> parameters,
                                        boolean printTicket,
                                        String segmentationRuleId,
                                        Boolean sendToOtherBus,
                                        String sourceMessageId,
                                        String correlationId,
                                        String idempotencyKey) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("branchId", safeNullable(branchId));
        payload.put("entryPointId", safeNullable(entryPointId));
        payload.put("serviceIds", serviceIds == null ? List.of() : List.copyOf(serviceIds));
        payload.put("parameters", parameters == null ? Map.of() : Map.copyOf(parameters));
        payload.put("printTicket", printTicket);
        payload.put("segmentationRuleId", safeNullable(segmentationRuleId));
        return publishEventRoute(
                "VISIT_CREATE",
                destination,
                sendToOtherBus,
                dataBusUrls,
                payload,
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
    }

    /**
     * Публикация события в DataBus с маршрутизацией на список внешних шин.
     */
    public long publishEventRoute(String type,
                                  String destination,
                                  Boolean sendToOtherBus,
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
                "dataBusUrls", normalizeUrls(dataBusUrls),
                "body", body
        );

        boolean includeForwardHeader = sendToOtherBus != null;
        return doCall(eff, cfg, destination, sendToOtherBus, path, routeBody, sourceMessageId, correlationId, idempotencyKey, null, null, includeForwardHeader);
    }

    public long publishEventRoute(String type,
                                  String destination,
                                  List<String> dataBusUrls,
                                  Object body,
                                  String sourceMessageId,
                                  String correlationId,
                                  String idempotencyKey) {
        return publishEventRoute(type, destination, null, dataBusUrls, body, sourceMessageId, correlationId, idempotencyKey);
    }
    /**
     * Публикация route-события с минимальным набором аргументов.
     */
    public long publishEventRoute(String type,
                                  String destination,
                                  List<String> dataBusUrls,
                                  Object body,
                                  String correlationId) {
        return publishEventRoute(type, destination, null, dataBusUrls, body, null, correlationId, null);
    }


    /**
     * Публикация route-события с флагом sendToOtherBus и correlationId.
     */
    public long publishEventRoute(String type,
                                  String destination,
                                  Boolean sendToOtherBus,
                                  List<String> dataBusUrls,
                                  Object body,
                                  String correlationId) {
        return publishEventRoute(type, destination, sendToOtherBus, dataBusUrls, body, null, correlationId, null);
    }

    /**
     * Прототип DataBus request (упрощённый вариант для Groovy flow).
     */
    public long sendRequest(String function,
                            String destination,
                            Map<String, Object> params) {
        return sendRequest(function, destination, null, params, null, null, null);
    }

    /**
     * Прототип DataBus request с correlationId.
     */
    public long sendRequest(String function,
                            String destination,
                            Map<String, Object> params,
                            String correlationId) {
        return sendRequest(function, destination, null, params, null, correlationId, null);
    }

    /**
     * Прототип DataBus request с sendToOtherBus и correlationId.
     */
    public long sendRequest(String function,
                            String destination,
                            Boolean sendToOtherBus,
                            Map<String, Object> params,
                            String correlationId) {
        return sendRequest(function, destination, sendToOtherBus, params, null, correlationId, null);
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
     * Прототип DataBus response (упрощённый вариант для Groovy flow).
     */
    public long sendResponse(String destination,
                             Integer status,
                             String message,
                             Object response) {
        return sendResponse(destination, null, status, message, response, null, null, null);
    }

    /**
     * Прототип DataBus response с correlationId.
     */
    public long sendResponse(String destination,
                             Integer status,
                             String message,
                             Object response,
                             String correlationId) {
        return sendResponse(destination, null, status, message, response, null, correlationId, null);
    }


    /**
     * Типовой helper: отправить успешный response (200, "OK").
     */
    public long sendResponseOk(String destination,
                               Object response,
                               String sourceMessageId,
                               String correlationId,
                               String idempotencyKey) {
        return sendResponse(destination, null, 200, "OK", response, sourceMessageId, correlationId, idempotencyKey);
    }

    /**
     * Типовой helper: отправить ошибочный response с кодом и сообщением.
     */
    public long sendResponseError(String destination,
                                  Integer status,
                                  String message,
                                  Object response,
                                  String sourceMessageId,
                                  String correlationId,
                                  String idempotencyKey) {
        int resolvedStatus = status == null || status < 400 ? 500 : status;
        String resolvedMessage = safeNullable(message) == null ? "ERROR" : message.trim();
        return sendResponse(destination, null, resolvedStatus, resolvedMessage, response, sourceMessageId, correlationId, idempotencyKey);
    }

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
        CorrelationContext ctx = CorrelationContext.resolve(correlationId, sourceMessageId);
        headers.put("X-Correlation-Id", ctx.correlationId());
        headers.put("X-Request-Id", ctx.requestId());
    }


    private static List<String> normalizeUrls(List<String> dataBusUrls) {
        if (dataBusUrls == null || dataBusUrls.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (String u : dataBusUrls) {
            String t = safeNullable(u);
            if (t != null) {
                out.add(t);
            }
        }
        return List.copyOf(out);
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
