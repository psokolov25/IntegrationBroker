package ru.aritmos.integrationbroker.core;

import ru.aritmos.integrationbroker.model.InboundEnvelope;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Единый контекст корреляции для inbound/outbound сценариев.
 */
public record CorrelationContext(String correlationId, String requestId) {

    public static CorrelationContext resolve(String correlationId, String requestId) {
        String corr = normalize(correlationId);
        String req = normalize(requestId);

        if (corr == null && req == null) {
            String generated = "ib-" + UUID.randomUUID();
            return new CorrelationContext(generated, generated);
        }
        if (corr == null) {
            return new CorrelationContext(req, req);
        }
        if (req == null) {
            return new CorrelationContext(corr, corr);
        }
        return new CorrelationContext(corr, req);
    }

    public static CorrelationContext fromInbound(InboundEnvelope envelope) {
        if (envelope == null) {
            return resolve(null, null);
        }
        String corr = envelope.correlationId();
        String req = envelope.messageId();
        if ((corr == null || corr.isBlank()) && envelope.headers() != null) {
            corr = firstHeader(envelope.headers(), "X-Correlation-Id");
        }
        if ((req == null || req.isBlank()) && envelope.headers() != null) {
            req = firstHeader(envelope.headers(), "X-Request-Id");
        }
        return resolve(corr, req);
    }

    public Map<String, String> asHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Correlation-Id", correlationId);
        headers.put("X-Request-Id", requestId);
        return headers;
    }

    private static String firstHeader(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && e.getValue() != null && !e.getValue().isBlank()) {
                return e.getValue().trim();
            }
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}

