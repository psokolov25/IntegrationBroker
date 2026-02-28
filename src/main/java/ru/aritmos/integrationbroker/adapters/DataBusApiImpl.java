package ru.aritmos.integrationbroker.adapters;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.FlowEngine;
import ru.aritmos.integrationbroker.databus.DataBusGroovyAdapter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Реализация {@link DataBusApi} поверх текущего DataBus event-адаптера.
 */
@Singleton
@FlowEngine.GroovyExecutable("dataBus")
public class DataBusApiImpl implements DataBusApi {

    private static final int DEFAULT_MAX_PAYLOAD_BYTES = 262_144;
    private static final DateTimeFormatter RFC1123 = DateTimeFormatter.RFC_1123_DATE_TIME;

    private final DataBusGroovyAdapter dataBus;
    private final RuntimeConfigStore configStore;

    public DataBusApiImpl(DataBusGroovyAdapter dataBus, RuntimeConfigStore configStore) {
        this.dataBus = dataBus;
        this.configStore = configStore;
    }

    @Override
    public Map<String, Object> publishEvent(String target,
                                            String type,
                                            String destination,
                                            Object payload,
                                            Boolean sendToOtherBus,
                                            String sourceMessageId,
                                            String correlationId,
                                            String idempotencyKey) {
        String effectiveType = safeType(type, "UNKNOWN");
        String normalizedDestination = normalizeOrDefault(destination, "*");
        Map<String, Object> envelope = canonicalEventEnvelope(target, normalizedDestination, "events", effectiveType, correlationId, sourceMessageId, idempotencyKey, sendToOtherBus, payload);
        ensurePayloadWithinLimit(envelope.get("payload"));
        long outboxId = dataBus.publishEvent(effectiveType, normalizedDestination, sendToOtherBus, envelope, sourceMessageId, correlationId, idempotencyKey);
        return response("events", outboxId, envelope);
    }

    @Override
    public Map<String, Object> publishEventRoute(String target,
                                                 String destination,
                                                 String type,
                                                 List<String> dataBusUrls,
                                                 Object payload,
                                                 Boolean sendToOtherBus,
                                                 String sourceMessageId,
                                                 String correlationId,
                                                 String idempotencyKey) {
        String effectiveType = safeType(type, "UNKNOWN");
        String normalizedDestination = normalizeOrDefault(destination, "*");
        List<String> normalizedRouteUrls = normalizeRouteUrls(dataBusUrls);
        int requestedRouteCount = dataBusUrls == null ? 0 : dataBusUrls.size();
        int normalizedRouteCount = normalizedRouteUrls.size();
        Map<String, Object> envelope = canonicalEventEnvelope(target, normalizedDestination, "events.route", effectiveType, correlationId, sourceMessageId, idempotencyKey, sendToOtherBus, payload);
        ensurePayloadWithinLimit(envelope.get("payload"));
        envelope.put("routeDataBusUrls", normalizedRouteUrls);
        if (normalizedRouteUrls.isEmpty()) {
            return responseWithFanOutReport("events.route", 0, envelope, requestedRouteCount, normalizedRouteCount, 0, "FAILED");
        }
        long outboxId = dataBus.publishEventRoute(effectiveType, normalizedDestination, sendToOtherBus, normalizedRouteUrls, envelope, sourceMessageId, correlationId, idempotencyKey);
        String reportStatus = (requestedRouteCount > normalizedRouteCount) ? "PARTIAL_SUCCESS" : "SUCCESS";
        return responseWithFanOutReport("events.route", outboxId, envelope, requestedRouteCount, normalizedRouteCount, normalizedRouteCount, reportStatus);
    }

    @Override
    public Map<String, Object> sendRequest(String target,
                                           String destination,
                                           String function,
                                           Map<String, Object> params,
                                           Boolean sendToOtherBus,
                                           String sourceMessageId,
                                           String correlationId,
                                           String idempotencyKey) {
        String effectiveFunction = safeType(function, "unknown");
        String normalizedDestination = normalizeOrDefault(destination, "*");
        Map<String, Object> envelope = canonicalRequestEnvelope(target, normalizedDestination, effectiveFunction, correlationId, sourceMessageId, idempotencyKey, sendToOtherBus, params);
        ensurePayloadWithinLimit(envelope.get("payload"));
        long outboxId = dataBus.sendRequest(effectiveFunction, normalizedDestination, sendToOtherBus, envelope, sourceMessageId, correlationId, idempotencyKey);
        return response("requests", outboxId, envelope);
    }

    @Override
    public Map<String, Object> sendResponse(String target,
                                            String destination,
                                            Integer status,
                                            String message,
                                            Object response,
                                            Boolean sendToOtherBus,
                                            String sourceMessageId,
                                            String correlationId,
                                            String idempotencyKey) {
        String normalizedDestination = normalizeOrDefault(destination, "*");
        Map<String, Object> envelope = canonicalResponseEnvelope(target, normalizedDestination, correlationId, sourceMessageId, idempotencyKey, sendToOtherBus, status, message, response);
        ensurePayloadWithinLimit(envelope.get("payload"));
        long outboxId = dataBus.sendResponse(normalizedDestination, sendToOtherBus, status, message, envelope, sourceMessageId, correlationId, idempotencyKey);
        return response("responses", outboxId, envelope);
    }

    private List<String> normalizeRouteUrls(List<String> dataBusUrls) {
        if (dataBusUrls == null || dataBusUrls.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String raw : dataBusUrls) {
            String v = normalize(raw);
            if (v != null) {
                out.add(v);
            }
        }
        return List.copyOf(out);
    }

    private Map<String, Object> response(String transport, long outboxId, Map<String, Object> envelope) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("transport", transport);
        out.put("outboxId", outboxId);
        out.put("envelope", envelope);
        return out;
    }

    private Map<String, Object> responseWithFanOutReport(String transport,
                                                          long outboxId,
                                                          Map<String, Object> envelope,
                                                          int requested,
                                                          int normalized,
                                                          int accepted,
                                                          String status) {
        Map<String, Object> out = response(transport, outboxId, envelope);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("requested", requested);
        report.put("normalized", normalized);
        report.put("accepted", accepted);
        report.put("rejected", Math.max(0, requested - normalized));
        report.put("status", status);
        out.put("fanOutReport", report);
        return out;
    }

    private Map<String, Object> canonicalEventEnvelope(String target,
                                                      String destination,
                                                      String operation,
                                                      String type,
                                                      String correlationId,
                                                      String sourceMessageId,
                                                      String idempotencyKey,
                                                      Boolean sendToOtherBus,
                                                      Object payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("target", normalizeOrDefault(target, "default"));
        envelope.put("destination", normalizeOrDefault(destination, "*"));
        envelope.put("type", safeType(type, "UNKNOWN"));
        envelope.put("operation", normalizeOrDefault(operation, "events"));
        envelope.put("sendToOtherBus", sendToOtherBus);
        envelope.put("source", resolveSenderServiceName());
        envelope.put("timestamp", Instant.now().toString());
        envelope.put("envelopeVersion", "ib.v1");
        String resolvedCorrelationId = resolveCorrelationId(correlationId, sourceMessageId);
        String resolvedRequestId = resolveRequestId(sourceMessageId, correlationId);
        String resolvedIdempotencyKey = resolveIdempotencyKey(idempotencyKey, sourceMessageId, correlationId);
        envelope.put("correlationId", resolvedCorrelationId);
        envelope.put("requestId", resolvedRequestId);
        envelope.put("sourceMessageId", normalize(sourceMessageId));
        envelope.put("idempotencyKey", resolvedIdempotencyKey);
        envelope.put("payload", payload);
        Map<String, Object> ibMeta = buildIntegrationMetadata(resolvedCorrelationId, resolvedRequestId, resolvedIdempotencyKey);
        ibMeta.put("requiredHeaders", buildMandatoryHeaders());
        envelope.put("_ib", ibMeta);
        envelope.put("params", buildIntegrationMetadata(resolvedCorrelationId, resolvedRequestId, resolvedIdempotencyKey));
        envelope.put("request", null);
        envelope.put("response", null);
        envelope.put("status", null);
        envelope.put("message", null);
        return envelope;
    }

    private Map<String, Object> canonicalRequestEnvelope(String target, String destination, String function, String correlationId, String sourceMessageId, String idempotencyKey, Boolean sendToOtherBus, Map<String, Object> params) {
        Map<String, Object> envelope = canonicalEventEnvelope(target, destination, "requests", "request." + function, correlationId, sourceMessageId, idempotencyKey, sendToOtherBus, params == null ? Map.of() : params);
        String request = function;
        envelope.put("request", request);
        envelope.put("response", false);
        return envelope;
    }

    private Map<String, Object> canonicalResponseEnvelope(String target, String destination, String correlationId, String sourceMessageId, String idempotencyKey, Boolean sendToOtherBus, Integer status, String message, Object response) {
        Map<String, Object> envelope = canonicalEventEnvelope(target, destination, "responses", "response", correlationId, sourceMessageId, idempotencyKey, sendToOtherBus, response);
        envelope.put("response", true);
        envelope.put("status", status);
        envelope.put("message", normalize(message));
        return envelope;
    }

    private Map<String, Object> buildIntegrationMetadata(String correlationId, String requestId, String idempotencyKey) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (correlationId != null) {
            meta.put("correlationId", correlationId);
        }
        if (requestId != null) {
            meta.put("requestId", requestId);
        }
        if (idempotencyKey != null) {
            meta.put("idempotencyKey", idempotencyKey);
        }
        return meta;
    }

    private Map<String, Object> buildMandatoryHeaders() {
        RuntimeConfigStore.RuntimeConfig cfg = configStore == null ? null : configStore.getEffective();
        RuntimeConfigStore.DataBusIntegrationConfig db = cfg == null ? null : cfg.dataBus();
        String senderHeaderName = db == null || normalize(db.senderHeaderName()) == null ? "Service-Sender" : normalize(db.senderHeaderName());
        String sendDateHeaderName = db == null || normalize(db.sendDateHeaderName()) == null ? "Send-Date" : normalize(db.sendDateHeaderName());
        String senderService = resolveSenderServiceName();
        String sendDate = RFC1123.format(ZonedDateTime.now(ZoneId.of("GMT")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(senderHeaderName, senderService);
        out.put(sendDateHeaderName, sendDate);
        return out;
    }

    private void ensurePayloadWithinLimit(Object payload) {
        int maxBytes = resolveMaxPayloadBytes();
        if (maxBytes <= 0 || payload == null) {
            return;
        }
        int sizeBytes = String.valueOf(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (sizeBytes > maxBytes) {
            throw new IllegalArgumentException("DataBus payload превышает лимит: " + sizeBytes + " > " + maxBytes + " bytes");
        }
    }

    private int resolveMaxPayloadBytes() {
        String value = System.getProperty("ib.databus.max-payload-bytes");
        if (value == null || value.isBlank()) {
            value = System.getenv("IB_DATABUS_MAX_PAYLOAD_BYTES");
        }
        if (value == null || value.isBlank()) {
            return DEFAULT_MAX_PAYLOAD_BYTES;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return DEFAULT_MAX_PAYLOAD_BYTES;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveCorrelationId(String correlationId, String sourceMessageId) {
        String corr = normalize(correlationId);
        if (corr != null) {
            return corr;
        }
        return normalize(sourceMessageId);
    }



    private String resolveRequestId(String sourceMessageId, String correlationId) {
        String req = normalize(sourceMessageId);
        if (req != null) {
            return req;
        }
        return normalize(correlationId);
    }

    private String resolveIdempotencyKey(String idempotencyKey, String sourceMessageId, String correlationId) {
        String idem = normalize(idempotencyKey);
        if (idem != null) {
            return idem;
        }
        String source = normalize(sourceMessageId);
        if (source != null) {
            return source;
        }
        return normalize(correlationId);
    }

    private String normalizeOrDefault(String value, String def) {
        String normalized = normalize(value);
        return normalized == null ? def : normalized;
    }

    private String safeType(String value, String def) {
        String normalized = normalize(value);
        return normalized == null ? def : normalized;
    }

    private String resolveSenderServiceName() {
        RuntimeConfigStore.RuntimeConfig cfg = configStore == null ? null : configStore.getEffective();
        RuntimeConfigStore.DataBusIntegrationConfig db = cfg == null ? null : cfg.dataBus();
        String sender = db == null ? null : normalize(db.defaultSenderServiceName());
        return sender == null ? "integration-broker" : sender;
    }

}
