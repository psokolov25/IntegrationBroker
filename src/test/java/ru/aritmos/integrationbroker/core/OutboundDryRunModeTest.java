package ru.aritmos.integrationbroker.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutboundDryRunModeTest {

    @Test
    void messagingPublishShouldSkipDirectSendWhenDryRunIsEnabled() {
        CountingProvider provider = new CountingProvider();
        MessagingProviderRegistry registry = new MessagingProviderRegistry(List.of(provider));
        MessagingOutboxService service = new MessagingOutboxService(null, new ObjectMapper(), registry);
        service.outboundDryRun = true;

        long outboxId = service.publish(
                new RuntimeConfigStore.MessagingOutboxConfig(false, "ON_FAILURE", 3, 1, 5, 10),
                "logging",
                "topic-a",
                null,
                Map.of("x-test", "1"),
                Map.of("ok", true),
                "m-1",
                "c-1",
                "i-1"
        );

        assertEquals(0, outboxId);
        assertEquals(0, provider.calls);
    }

    @Test
    void restCallShouldSkipDirectSendWhenDryRunIsEnabled() {
        CountingRestSender sender = new CountingRestSender();
        RestOutboxService service = new RestOutboxService(null, new ObjectMapper(), sender, null);
        service.outboundDryRun = true;

        long outboxId = service.call(
                new RuntimeConfigStore.RestOutboxConfig(false, "ON_FAILURE", 3, 1, 5, 10, "Idempotency-Key", "409"),
                "POST",
                "http://localhost/test",
                Map.of("x-test", "1"),
                Map.of("ok", true),
                "idem-header",
                "m-1",
                "c-1",
                "i-1"
        );

        assertEquals(0, outboxId);
        assertEquals(0, sender.calls);
    }

    static class CountingProvider implements MessagingProvider {
        int calls;

        @Override
        public String id() {
            return "logging";
        }

        @Override
        public SendResult send(OutboundMessage message) {
            calls++;
            return SendResult.ok();
        }
    }

    static class CountingRestSender implements RestOutboundSender {
        int calls;

        @Override
        public Result send(String method, String url, Map<String, String> headers, String bodyJson, String idempotencyHeaderName, String idempotencyKey) {
            calls++;
            return Result.ok(200);
        }
    }
}
