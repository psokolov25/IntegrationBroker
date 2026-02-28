package ru.aritmos.integrationbroker.databus;

import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.RestOutboxService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DataBusGroovyAdapterTest {



    @Test
    void publishVisitUpdated_shouldBuildCanonicalPayload() {
        StubRestOutboxService outbox = new StubRestOutboxService();
        DataBusGroovyAdapter adapter = new DataBusGroovyAdapter(runtimeConfigStore(), outbox);

        long id = adapter.publishVisitUpdated(
                "visitmanager",
                "V-100",
                Map.of("segment", "VIP"),
                "msg-u",
                "corr-u",
                "idem-u"
        );

        assertEquals(77L, id);
        assertEquals("/databus/events/types/VISIT_UPDATED", outbox.lastPath);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) outbox.lastBody;
        assertEquals("V-100", payload.get("visitId"));
        @SuppressWarnings("unchecked")
        Map<String, String> parameters = (Map<String, String>) payload.get("parameters");
        assertEquals("VIP", parameters.get("segment"));
    }


    @Test
    void publishVisitCalled_shouldBuildCanonicalPayload() {
        StubRestOutboxService outbox = new StubRestOutboxService();
        DataBusGroovyAdapter adapter = new DataBusGroovyAdapter(runtimeConfigStore(), outbox);

        long id = adapter.publishVisitCalled(
                "display",
                "B9",
                "SP9",
                "V9",
                "msg-call",
                "corr-call",
                "idem-call"
        );

        assertEquals(77L, id);
        assertEquals("/databus/events/types/VISIT_CALLED", outbox.lastPath);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) outbox.lastBody;
        assertEquals("B9", payload.get("branchId"));
        assertEquals("SP9", payload.get("servicePointId"));
        assertEquals("V9", payload.get("visitId"));
    }


    @Test
    void publishVisitPostponed_shouldBuildCanonicalPayload() {
        StubRestOutboxService outbox = new StubRestOutboxService();
        DataBusGroovyAdapter adapter = new DataBusGroovyAdapter(runtimeConfigStore(), outbox);

        long id = adapter.publishVisitPostponed(
                "display",
                "B10",
                "SP10",
                "V10",
                "msg-postpone",
                "corr-postpone",
                "idem-postpone"
        );

        assertEquals(77L, id);
        assertEquals("/databus/events/types/VISIT_POSTPONED", outbox.lastPath);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) outbox.lastBody;
        assertEquals("B10", payload.get("branchId"));
        assertEquals("SP10", payload.get("servicePointId"));
        assertEquals("V10", payload.get("visitId"));
    }


    @Test
    void publishAutoCallStateChanged_shouldBuildCanonicalPayload() {
        StubRestOutboxService outbox = new StubRestOutboxService();
        DataBusGroovyAdapter adapter = new DataBusGroovyAdapter(runtimeConfigStore(), outbox);

        long id = adapter.publishAutoCallStateChanged(
                "display",
                "B11",
                "SP11",
                true,
                "msg-auto",
                "corr-auto",
                "idem-auto"
        );

        assertEquals(77L, id);
        assertEquals("/databus/events/types/AUTO_CALL_STATE_CHANGED", outbox.lastPath);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) outbox.lastBody;
        assertEquals("B11", payload.get("branchId"));
        assertEquals("SP11", payload.get("servicePointId"));
        assertEquals(true, payload.get("enabled"));
    }


    @Test
    void publishServicePointModeChanged_shouldBuildCanonicalPayload() {
        StubRestOutboxService outbox = new StubRestOutboxService();
        DataBusGroovyAdapter adapter = new DataBusGroovyAdapter(runtimeConfigStore(), outbox);

        long id = adapter.publishServicePointModeChanged(
                "display",
                "B12",
                "WORK",
                true,
                "msg-mode",
                "corr-mode",
                "idem-mode"
        );

        assertEquals(77L, id);
        assertEquals("/databus/events/types/SERVICE_POINT_MODE_CHANGED", outbox.lastPath);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) outbox.lastBody;
        assertEquals("B12", payload.get("branchId"));
        assertEquals("WORK", payload.get("mode"));
        assertEquals(true, payload.get("entered"));
    }


    @Test
    void publishBranchStateSnapshot_shouldBuildCanonicalPayload() {
        StubRestOutboxService outbox = new StubRestOutboxService();
        DataBusGroovyAdapter adapter = new DataBusGroovyAdapter(runtimeConfigStore(), outbox);

        long id = adapter.publishBranchStateSnapshot(
                "display",
                "B13",
                Map.of("queueSize", 3),
                "msg-branch",
                "corr-branch",
                "idem-branch"
        );

        assertEquals(77L, id);
        assertEquals("/databus/events/types/BRANCH_STATE_SNAPSHOT", outbox.lastPath);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) outbox.lastBody;
        assertEquals("B13", payload.get("branchId"));
        assertEquals(Map.of("queueSize", 3), payload.get("state"));
    }

    @Test
    void publishVisitCreate_shouldBuildCanonicalPayload() {
        StubRestOutboxService outbox = new StubRestOutboxService();
        DataBusGroovyAdapter adapter = new DataBusGroovyAdapter(runtimeConfigStore(), outbox);

        long id = adapter.publishVisitCreate(
                "visitmanager",
                "B1",
                "EP1",
                List.of("S1", "S2"),
                Map.of("segment", "VIP"),
                true,
                "rule-1",
                "msg-1",
                "corr-1",
                "idem-1"
        );

        assertEquals(77L, id);
        assertEquals("/databus/events/types/VISIT_CREATE", outbox.lastPath);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) outbox.lastBody;
        assertEquals("B1", payload.get("branchId"));
        assertEquals("EP1", payload.get("entryPointId"));
        assertEquals(List.of("S1", "S2"), payload.get("serviceIds"));
        assertEquals(true, payload.get("printTicket"));
        assertEquals("rule-1", payload.get("segmentationRuleId"));
    }


    @Test
    void publishVisitCreateRoute_shouldWrapCanonicalPayloadIntoRouteBody() {
        StubRestOutboxService outbox = new StubRestOutboxService();
        DataBusGroovyAdapter adapter = new DataBusGroovyAdapter(runtimeConfigStore(), outbox);

        long id = adapter.publishVisitCreateRoute(
                "visitmanager",
                List.of("http://bus-a", "http://bus-b"),
                "B2",
                "EP2",
                List.of("S9"),
                Map.of("segment", "REG"),
                false,
                null,
                true,
                "msg-2",
                "corr-2",
                "idem-2"
        );

        assertEquals(77L, id);
        assertEquals("/databus/events/types/VISIT_CREATE/route", outbox.lastPath);
        assertEquals("true", outbox.lastHeaders.get("Send-To-OtherBus"));
        @SuppressWarnings("unchecked")
        Map<String, Object> routeBody = (Map<String, Object>) outbox.lastBody;
        assertEquals(List.of("http://bus-a", "http://bus-b"), routeBody.get("dataBusUrls"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) routeBody.get("body");
        assertEquals("B2", payload.get("branchId"));
        assertEquals("EP2", payload.get("entryPointId"));
        assertEquals(List.of("S9"), payload.get("serviceIds"));
    }


    @Test
    void sendResponseOk_shouldUse200AndOkMessage() {
        StubRestOutboxService outbox = new StubRestOutboxService();
        DataBusGroovyAdapter adapter = new DataBusGroovyAdapter(runtimeConfigStore(), outbox);

        long id = adapter.sendResponseOk("crm", Map.of("done", true), "msg-ok", "corr-ok", "idem-ok");

        assertEquals(77L, id);
        assertEquals("/databus/responses", outbox.lastPath);
        assertEquals("200", outbox.lastHeaders.get("Response-Status"));
        assertEquals("OK", outbox.lastHeaders.get("Response-Message"));
        assertEquals("corr-ok", outbox.lastHeaders.get("X-Correlation-Id"));
    }

    @Test
    void sendResponseError_shouldNormalizeStatusAndMessage() {
        StubRestOutboxService outbox = new StubRestOutboxService();
        DataBusGroovyAdapter adapter = new DataBusGroovyAdapter(runtimeConfigStore(), outbox);

        long id = adapter.sendResponseError("crm", 200, "   ", Map.of("error", true), "msg-err", "corr-err", "idem-err");

        assertEquals(77L, id);
        assertEquals("500", outbox.lastHeaders.get("Response-Status"));
        assertEquals("ERROR", outbox.lastHeaders.get("Response-Message"));
    }


    @Test
    void publishEventRoute_shouldFilterBlankDataBusUrls() {
        StubRestOutboxService outbox = new StubRestOutboxService();
        DataBusGroovyAdapter adapter = new DataBusGroovyAdapter(runtimeConfigStore(), outbox);

        long id = adapter.publishEventRoute(
                "visit.created",
                "crm",
                java.util.Arrays.asList("http://bus-1", "  ", null, "http://bus-2"),
                Map.of("visitId", "V-2"),
                "corr-3"
        );

        assertEquals(77L, id);
        @SuppressWarnings("unchecked")
        Map<String, Object> routeBody = (Map<String, Object>) outbox.lastBody;
        assertEquals(List.of("http://bus-1", "http://bus-2"), routeBody.get("dataBusUrls"));
    }

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
        Object lastBody;

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
            this.lastBody = body;
            return 77L;
        }
    }
}
