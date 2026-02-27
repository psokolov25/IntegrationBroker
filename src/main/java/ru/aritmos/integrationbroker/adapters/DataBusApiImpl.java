package ru.aritmos.integrationbroker.adapters;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.FlowEngine;
import ru.aritmos.integrationbroker.databus.DataBusGroovyAdapter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Реализация {@link DataBusApi} поверх текущего DataBus event-адаптера.
 */
@Singleton
@FlowEngine.GroovyExecutable("dataBus")
public class DataBusApiImpl implements DataBusApi {

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
        Map<String, Object> envelope = canonicalEventEnvelope(target, destination, "events", effectiveType, correlationId, sourceMessageId, idempotencyKey, sendToOtherBus, payload);
        long outboxId = dataBus.publishEvent(effectiveType, destination, sendToOtherBus, envelope, sourceMessageId, correlationId, idempotencyKey);
        return response("events", outboxId, envelope);
    }

    @Override
    public Map<String, Object> publishEventRoute(String target,
                                                 String destination,
                                                 String type,
                                                 List<String> dataBusUrls,
                                                 Object payload,
                                                 String sourceMessageId,
                                                 String correlationId,
                                                 String idempotencyKey) {
        String effectiveType = safeType(type, "UNKNOWN");
        Map<String, Object> envelope = canonicalEventEnvelope(target, destination, "events.route", effectiveType, correlationId, sourceMessageId, idempotencyKey, null, payload);
        envelope.put("routeDataBusUrls", dataBusUrls == null ? List.of() : dataBusUrls);
        long outboxId = dataBus.publishEventRoute(effectiveType, destination, dataBusUrls, envelope, sourceMessageId, correlationId, idempotencyKey);
        return response("events.route", outboxId, envelope);
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
        Map<String, Object> envelope = canonicalRequestEnvelope(target, destination, effectiveFunction, correlationId, sourceMessageId, idempotencyKey, sendToOtherBus, params);
        long outboxId = dataBus.sendRequest(effectiveFunction, destination, sendToOtherBus, envelope, sourceMessageId, correlationId, idempotencyKey);
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
        Map<String, Object> envelope = canonicalResponseEnvelope(target, destination, correlationId, sourceMessageId, idempotencyKey, sendToOtherBus, status, message, response);
        long outboxId = dataBus.sendResponse(destination, sendToOtherBus, status, message, envelope, sourceMessageId, correlationId, idempotencyKey);
        return response("responses", outboxId, envelope);
    }

    private Map<String, Object> response(String transport, long outboxId, Map<String, Object> envelope) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("transport", transport);
        out.put("outboxId", outboxId);
        out.put("envelope", envelope);
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
        envelope.put("correlationId", resolveCorrelationId(correlationId, sourceMessageId));
        envelope.put("sourceMessageId", normalize(sourceMessageId));
        envelope.put("idempotencyKey", resolveIdempotencyKey(idempotencyKey, sourceMessageId, correlationId));
        envelope.put("payload", payload);
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
