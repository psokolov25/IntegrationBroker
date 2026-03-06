package ru.aritmos.integrationbroker.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestOutboxServiceLatencyHistogramTest {

    @Test
    void callViaConnectorShouldCollectLatencyHistogramByConnector() {
        RestOutboundSender sender = (method, url, headers, bodyJson, idempotencyHeaderName, idempotencyValue) -> {
            try {
                Thread.sleep(120L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return RestOutboundSender.Result.ok(200);
        };

        RestOutboxService service = new RestOutboxService(null, new ObjectMapper(), sender, null);

        RuntimeConfigStore.RuntimeConfig runtime = runtimeWithConnector("crmConnector");
        RuntimeConfigStore.RestOutboxConfig outbox = runtime.restOutbox();

        long result = service.callViaConnector(
                runtime,
                outbox,
                "crmConnector",
                "POST",
                "/api/v1/events",
                Map.of(),
                Map.of("ok", true),
                "idem-1",
                "msg-1",
                "corr-1",
                "key-1"
        );

        assertEquals(0L, result);
        Map<String, Map<String, Long>> histogram = service.connectorLatencyHistogram();
        assertTrue(histogram.containsKey("crmConnector"));
        Map<String, Long> buckets = histogram.get("crmConnector");
        assertEquals(0L, buckets.getOrDefault("lt100ms", 0L));
        assertTrue(buckets.getOrDefault("lt300ms", 0L) >= 1L);
    }

    private static RuntimeConfigStore.RuntimeConfig runtimeWithConnector(String connectorId) {
        RuntimeConfigStore.RestConnectorAuth auth = new RuntimeConfigStore.RestConnectorAuth(
                RuntimeConfigStore.RestConnectorAuthType.NONE,
                null, null, null, null, null,
                null, null, null, null, null
        );
        RuntimeConfigStore.CircuitBreakerPolicy cb = new RuntimeConfigStore.CircuitBreakerPolicy(true, 3, 30, 1);

        return new RuntimeConfigStore.RuntimeConfig(
                "test",
                List.of(),
                new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.AUTO, 60),
                new RuntimeConfigStore.InboundDlqConfig(true, 10, true),
                new RuntimeConfigStore.KeycloakProxyEnrichmentConfig(false, false, "keycloakProxy", List.of(RuntimeConfigStore.KeycloakProxyFetchMode.USER_ID_HEADER), "x-user-id", "Authorization", "/authorization/users/{userName}", "/authentication/userInfo", true, 60, 5000, true, List.of()),
                new RuntimeConfigStore.MessagingOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50),
                new RuntimeConfigStore.RestOutboxConfig(false, "ON_FAILURE", 10, 10, 120, 50, "Idempotency-Key", "409"),
                Map.of(connectorId, new RuntimeConfigStore.RestConnectorConfig("http://localhost", auth, null, cb)),
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
}
