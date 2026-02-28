package ru.aritmos.integrationbroker.core;

import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.model.InboundEnvelope;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationContextTest {

    @Test
    void resolve_shouldGenerateBothIdsWhenMissing() {
        CorrelationContext ctx = CorrelationContext.resolve(" ", null);
        assertNotNull(ctx.correlationId());
        assertNotNull(ctx.requestId());
        assertEquals(ctx.correlationId(), ctx.requestId());
    }

    @Test
    void resolve_shouldReuseProvidedCorrelationAsRequestFallback() {
        CorrelationContext ctx = CorrelationContext.resolve("corr-1", " ");
        assertEquals("corr-1", ctx.correlationId());
        assertEquals("corr-1", ctx.requestId());
    }

    @Test
    void fromInbound_shouldReadHeadersWhenFieldsMissing() {
        InboundEnvelope envelope = new InboundEnvelope(
                InboundEnvelope.Kind.EVENT,
                "demo.event",
                null,
                Map.of("X-Correlation-Id", "corr-h", "X-Request-Id", "req-h"),
                null,
                null,
                null,
                null,
                Map.of()
        );

        CorrelationContext ctx = CorrelationContext.fromInbound(envelope);
        assertEquals("corr-h", ctx.correlationId());
        assertEquals("req-h", ctx.requestId());
    }
}
