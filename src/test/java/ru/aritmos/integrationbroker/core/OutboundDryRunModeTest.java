package ru.aritmos.integrationbroker.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.time.Instant;
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


    @Test
    void restCallViaConnectorShouldUseConnectorRetryMaxAttemptsWhenEnqueueing() {
        CapturingRestOutboxService service = new CapturingRestOutboxService(new FailingRestSender());

        RuntimeConfigStore.RuntimeConfig runtime = runtimeWithConnectorRetry("conn-1", new RuntimeConfigStore.RetryPolicy(3, 2, 4));
        RuntimeConfigStore.RestOutboxConfig cfg = new RuntimeConfigStore.RestOutboxConfig(true, "ON_FAILURE", 10, 10, 120, 50, "Idempotency-Key", "409");

        long outboxId = service.callViaConnector(
                runtime,
                cfg,
                "conn-1",
                "POST",
                "/api/demo",
                Map.of("x-test", "1"),
                Map.of("ok", true),
                "idem-header",
                "m-1",
                "c-1",
                "i-1"
        );

        assertEquals(101L, outboxId);
        assertEquals(3, service.capturedMaxAttempts);
    }


    @Test
    void restCallViaConnectorShouldOpenCircuitBreakerAndShortCircuitNextCall() {
        CountingFailingRestSender sender = new CountingFailingRestSender();
        RestOutboxService service = new RestOutboxService(null, new ObjectMapper(), sender, null);

        RuntimeConfigStore.RestConnectorAuth auth = new RuntimeConfigStore.RestConnectorAuth(
                RuntimeConfigStore.RestConnectorAuthType.NONE,
                null, null, null, null, null,
                null, null, null, null, null
        );
        RuntimeConfigStore.CircuitBreakerPolicy cb = new RuntimeConfigStore.CircuitBreakerPolicy(true, 2, 60);
        RuntimeConfigStore.RuntimeConfig runtime = new RuntimeConfigStore.RuntimeConfig(
                "test",
                List.of(),
                new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.AUTO, 60),
                new RuntimeConfigStore.InboundDlqConfig(true, 10, true),
                new RuntimeConfigStore.KeycloakProxyEnrichmentConfig(false, false, "keycloakProxy", List.of(RuntimeConfigStore.KeycloakProxyFetchMode.USER_ID_HEADER), "x-user-id", "Authorization", "/authorization/users/{userName}", "/authentication/userInfo", true, 60, 5000, true, List.of()),
                new RuntimeConfigStore.MessagingOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50),
                new RuntimeConfigStore.RestOutboxConfig(false, "ON_FAILURE", 10, 10, 120, 50, "Idempotency-Key", "409"),
                Map.of("conn-1", new RuntimeConfigStore.RestConnectorConfig("http://example", auth, null, cb)),
                RuntimeConfigStore.CrmConfig.disabled(),
                RuntimeConfigStore.MedicalConfig.disabled(),
                RuntimeConfigStore.AppointmentConfig.disabled(),
                RuntimeConfigStore.IdentityConfig.defaultConfig(),
                RuntimeConfigStore.VisionLabsAnalyticsConfig.disabled(),
                RuntimeConfigStore.BranchResolutionConfig.defaultConfig(),
                RuntimeConfigStore.VisitManagerIntegrationConfig.disabled(),
                RuntimeConfigStore.DataBusIntegrationConfig.disabled()
        );

        RuntimeConfigStore.RestOutboxConfig cfg = runtime.restOutbox();

        long r1 = service.callViaConnector(runtime, cfg, "conn-1", "POST", "/api/demo", Map.of(), Map.of("n", 1), "i1", "m1", "c1", "k1");
        long r2 = service.callViaConnector(runtime, cfg, "conn-1", "POST", "/api/demo", Map.of(), Map.of("n", 2), "i2", "m2", "c2", "k2");
        long r3 = service.callViaConnector(runtime, cfg, "conn-1", "POST", "/api/demo", Map.of(), Map.of("n", 3), "i3", "m3", "c3", "k3");

        assertEquals(0L, r1);
        assertEquals(0L, r2);
        assertEquals(0L, r3);
        assertEquals(2, sender.calls, "третья попытка должна быть заблокирована circuit-breaker и не доходить до sender");
    }

    @Test
    void dispatcherShouldUseConnectorRetryBackoffPolicyForRestFailures() {
        RuntimeConfigStore.RuntimeConfig runtime = runtimeWithConnectorRetry("conn-1", new RuntimeConfigStore.RetryPolicy(3, 2, 4));
        RuntimeConfigStore store = new RuntimeConfigStore(null, null, null, null, false, null) {
            @Override
            public RuntimeConfig getEffective() {
                return runtime;
            }
        };

        StubMessagingOutboxService msg = new StubMessagingOutboxService();
        StubRestOutboxService rest = new StubRestOutboxService();
        OutboxDispatcher dispatcher = new OutboxDispatcher(store, msg, rest, new MessagingProviderRegistry(List.of()));

        dispatcher.dispatchRest();

        assertEquals(3, rest.failedMaxAttempts);
        long delaySec = Instant.now().until(rest.failedNextAttemptAt, java.time.temporal.ChronoUnit.SECONDS);
        // для attempts=1 -> nextAttempts=2; delay = base(2) * 2^(2-1) = 4 сек (cap 4)
        org.junit.jupiter.api.Assertions.assertTrue(delaySec >= 0 && delaySec <= 6, "ожидается backoff около 4 секунд с коннекторным override");
    }

    private RuntimeConfigStore.RuntimeConfig runtimeWithConnectorRetry(String connectorId,
                                                                       RuntimeConfigStore.RetryPolicy retryPolicy) {
        RuntimeConfigStore.RestConnectorAuth auth = new RuntimeConfigStore.RestConnectorAuth(
                RuntimeConfigStore.RestConnectorAuthType.NONE,
                null, null, null, null, null,
                null, null, null, null, null
        );
        return new RuntimeConfigStore.RuntimeConfig(
                "test",
                List.of(),
                new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.AUTO, 60),
                new RuntimeConfigStore.InboundDlqConfig(true, 10, true),
                new RuntimeConfigStore.KeycloakProxyEnrichmentConfig(false, false, "keycloakProxy", List.of(RuntimeConfigStore.KeycloakProxyFetchMode.USER_ID_HEADER), "x-user-id", "Authorization", "/authorization/users/{userName}", "/authentication/userInfo", true, 60, 5000, true, List.of()),
                new RuntimeConfigStore.MessagingOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50),
                new RuntimeConfigStore.RestOutboxConfig(true, "ON_FAILURE", 10, 10, 120, 50, "Idempotency-Key", "409"),
                Map.of(connectorId, new RuntimeConfigStore.RestConnectorConfig("http://example", auth, retryPolicy, null)),
                RuntimeConfigStore.CrmConfig.disabled(),
                RuntimeConfigStore.MedicalConfig.disabled(),
                RuntimeConfigStore.AppointmentConfig.disabled(),
                RuntimeConfigStore.IdentityConfig.defaultConfig(),
                RuntimeConfigStore.VisionLabsAnalyticsConfig.disabled(),
                RuntimeConfigStore.BranchResolutionConfig.defaultConfig(),
                RuntimeConfigStore.VisitManagerIntegrationConfig.disabled(),
                RuntimeConfigStore.DataBusIntegrationConfig.disabled()
        );
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

    static class CountingFailingRestSender implements RestOutboundSender {
        int calls;

        @Override
        public Result send(String method, String url, Map<String, String> headers, String bodyJson, String idempotencyHeaderName, String idempotencyKey) {
            calls++;
            return Result.fail("UPSTREAM", "temporary", 503);
        }
    }

    static class FailingRestSender implements RestOutboundSender {
        @Override
        public Result send(String method, String url, Map<String, String> headers, String bodyJson, String idempotencyHeaderName, String idempotencyKey) {
            return Result.fail("UPSTREAM", "temporary", 503);
        }
    }

    static class CapturingRestOutboxService extends RestOutboxService {
        int capturedMaxAttempts;

        CapturingRestOutboxService(RestOutboundSender sender) {
            super(null, new ObjectMapper(), sender, null);
        }

        @Override
        public long enqueue(String method, String url, String connectorId, String path, Map<String, String> headers, Object body, String idempotencyKey, String sourceMessageId, String correlationId, String idemKey, int maxAttempts, String treat4xxAsSuccess) {
            this.capturedMaxAttempts = maxAttempts;
            return 101L;
        }
    }

    static class StubMessagingOutboxService extends MessagingOutboxService {
        StubMessagingOutboxService() {
            super(null, new ObjectMapper(), new MessagingProviderRegistry(List.of()));
        }
    }

    static class StubRestOutboxService extends RestOutboxService {
        Instant failedNextAttemptAt;
        int failedMaxAttempts;
        private final RestRecord due = new RestRecord(
                11L, "PENDING", "POST", "http://example/api", "conn-1", "/api",
                "{}", "{}", null, "m-1", "c-1", "i-1", 1, 10,
                Instant.now().minusSeconds(1).toString(), "409", null, null, null, Instant.now().toString()
        );

        StubRestOutboxService() {
            super(null, new ObjectMapper(), new FailingRestSender(), null);
        }

        @Override
        public java.util.List<RestRecord> pickDue(int limit) {
            return List.of(due);
        }

        @Override
        public boolean markSending(long id) {
            return true;
        }

        @Override
        public RestOutboundSender.Result sendOnce(RestRecord rec, String idempotencyHeaderName, RuntimeConfigStore.RuntimeConfig effective) {
            return RestOutboundSender.Result.fail("UPSTREAM", "temporary", 503);
        }

        @Override
        public void markFailed(long id, int attempts, int maxAttempts, Instant nextAttemptAt, String errorCode, String errorMessage, int lastHttpStatus, boolean dead) {
            this.failedNextAttemptAt = nextAttemptAt;
            this.failedMaxAttempts = maxAttempts;
        }
    }

}
