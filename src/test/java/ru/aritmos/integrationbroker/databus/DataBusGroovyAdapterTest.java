package ru.aritmos.integrationbroker.databus;

import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.RestOutboxService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DataBusGroovyAdapterTest {

    @Test
    void sendRequest_overloadWithForwardFlag_shouldPropagateFlag() {
        StubRestOutboxService outbox = new StubRestOutboxService();
        DataBusGroovyAdapter adapter = new DataBusGroovyAdapter(runtimeConfigStore(), outbox);

        long id = adapter.sendRequest("resolve", "crm", true, Map.of("x", 1), "corr-9");

        assertEquals(77L, id);
        assertEquals("/databus/requests/resolve", outbox.lastPath);
        assertEquals("true", outbox.lastHeaders.get("Send-To-OtherBus"));
        assertEquals("corr-9", outbox.lastHeaders.get("X-Correlation-Id"));
    }

    @Test
    void publishEventRoute_shortOverload_shouldNotSendForwardHeaderWhenFlagMissing() {
        StubRestOutboxService outbox = new StubRestOutboxService();
        DataBusGroovyAdapter adapter = new DataBusGroovyAdapter(runtimeConfigStore(), outbox);

        long id = adapter.publishEventRoute(
                "visit.created",
                "crm",
                List.of("http://bus-1", "http://bus-2"),
                Map.of("visitId", "V-1"),
                "corr-1"
        );

        assertEquals(77L, id);
        assertEquals("POST", outbox.lastMethod);
        assertEquals("/databus/events/types/visit.created/route", outbox.lastPath);
        assertEquals("crm", outbox.lastHeaders.get("Service-Destination"));
        assertFalse(outbox.lastHeaders.containsKey("Send-To-OtherBus"));
        assertEquals("corr-1", outbox.lastHeaders.get("X-Correlation-Id"));
    }

    @Test
    void sendResponse_shortOverload_shouldUseDefaultForwardHeader() {
        StubRestOutboxService outbox = new StubRestOutboxService();
        DataBusGroovyAdapter adapter = new DataBusGroovyAdapter(runtimeConfigStore(), outbox);

        long id = adapter.sendResponse("crm", 200, "ok", Map.of("accepted", true), "corr-2");

        assertEquals(77L, id);
        assertEquals("/databus/responses", outbox.lastPath);
        assertEquals("false", outbox.lastHeaders.get("Send-To-OtherBus"));
        assertEquals("200", outbox.lastHeaders.get("Response-Status"));
        assertEquals("ok", outbox.lastHeaders.get("Response-Message"));
        assertEquals("corr-2", outbox.lastHeaders.get("X-Correlation-Id"));
    }

    private RuntimeConfigStore runtimeConfigStore() {
        RuntimeConfigStore.DataBusIntegrationConfig cfg = new RuntimeConfigStore.DataBusIntegrationConfig(
                true,
                "databus",
                "/databus/events/types/{type}",
                "/databus/events/types/{type}/route",
                "/databus/requests/{function}",
                "/databus/responses",
                "Service-Destination",
                "Send-To-OtherBus",
                "Send-Date",
                "Service-Sender",
                "Response-Status",
                "Response-Message",
                "integration-broker",
                false
        );
        RuntimeConfigStore.RuntimeConfig runtime = new RuntimeConfigStore.RuntimeConfig(
                "test", List.of(), new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.AUTO, 60),
                new RuntimeConfigStore.InboundDlqConfig(true, 10, true),
                new RuntimeConfigStore.KeycloakProxyEnrichmentConfig(false, false, "keycloakProxy", List.of(RuntimeConfigStore.KeycloakProxyFetchMode.USER_ID_HEADER), "x-user-id", "Authorization", "/authorization/users/{userName}", "/authentication/userInfo", true, 60, 5000, true, List.of()),
                new RuntimeConfigStore.MessagingOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50),
                new RuntimeConfigStore.RestOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50, "Idempotency-Key", "409"),
                Map.of(), RuntimeConfigStore.CrmConfig.disabled(), RuntimeConfigStore.MedicalConfig.disabled(), RuntimeConfigStore.AppointmentConfig.disabled(), RuntimeConfigStore.IdentityConfig.defaultConfig(), RuntimeConfigStore.VisionLabsAnalyticsConfig.disabled(), RuntimeConfigStore.BranchResolutionConfig.defaultConfig(), RuntimeConfigStore.VisitManagerIntegrationConfig.disabled(), cfg
        );
        return new RuntimeConfigStore(null, null, null, null, false, null) {
            @Override
            public RuntimeConfig getEffective() {
                return runtime;
            }
        };
    }

    static class StubRestOutboxService extends RestOutboxService {
        String lastMethod;
        String lastPath;
        Map<String, String> lastHeaders;

        StubRestOutboxService() {
            super(null, null, null);
        }

        @Override
        public long callViaConnector(RuntimeConfigStore.RuntimeConfig effective,
                                     RuntimeConfigStore.RestOutboxConfig cfg,
                                     String connectorId,
                                     String method,
                                     String path,
                                     Map<String, String> headers,
                                     Object body,
                                     String idempotencyKey,
                                     String sourceMessageId,
                                     String correlationId,
                                     String idemKey) {
            this.lastMethod = method;
            this.lastPath = path;
            this.lastHeaders = headers;
            return 77L;
        }
    }
}
