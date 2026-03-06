package ru.aritmos.integrationbroker.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpResponse;
import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.core.InboundProcessingService;
import ru.aritmos.integrationbroker.model.InboundEnvelope;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InboundControllerRateLimitTest {

    @Test
    void inbound_shouldReturn429WhenPerSourceLimitExceeded() throws Exception {
        InboundProcessingService stub = new InboundProcessingService(null, null, null, null, null, null, new ObjectMapper()) {
            @Override
            public ProcessingResult process(InboundEnvelope envelope) {
                return new ProcessingResult("PROCESSED", "idem-1", Map.of("ok", true));
            }
        };

        InboundController controller = new InboundController(
                stub,
                null,
                null,
                null,
                null,
                null,
                null,
                new ObjectMapper(),
                true,
                1
        );

        InboundEnvelope env = new InboundEnvelope(
                InboundEnvelope.Kind.EVENT,
                "visit.created",
                new ObjectMapper().readTree("{}"),
                Map.of(),
                "msg-1",
                "corr-1",
                "B1",
                "u1",
                Map.of("source", "crm")
        );

        HttpResponse<InboundController.InboundResult> first = controller.inbound(env);
        HttpResponse<InboundController.InboundResult> second = controller.inbound(env);

        assertEquals(200, first.getStatus().getCode());
        assertEquals(429, second.getStatus().getCode());
        assertEquals("RATE_LIMITED", second.body().outcome());
    }
}
