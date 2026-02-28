package ru.aritmos.integrationbroker.adapters;

import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.databus.DataBusGroovyAdapter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DataBusApiImplTest {







    @Test
    void sendResponseOk_shouldUseStatus200AndMessageOk() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> result = api.sendResponseOk(
                "target-1",
                "crm",
                Map.of("accepted", true),
                "src-ok-1",
                "corr-ok-1",
                "idem-ok-1"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals(200, envelope.get("status"));
        assertEquals("OK", envelope.get("message"));
    }

    @Test
    void sendResponseError_shouldFallbackTo500AndErrorMessage() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> result = api.sendResponseError(
                "target-1",
                "crm",
                null,
                "   ",
                Map.of("accepted", false),
                "src-err-1",
                "corr-err-1",
                "idem-err-1"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals(500, envelope.get("status"));
        assertEquals("ERROR", envelope.get("message"));
    }
    @Test
    void publishVisitCreateRoute_shouldBuildRouteEnvelopeWithVisitCreateType() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> result = api.publishVisitCreateRoute(
                "target-r1",
                "visitmanager",
                List.of("http://bus-a", "http://bus-b"),
                "BR-10",
                "EP-10",
                List.of("S10"),
                Map.of("segment", "A"),
                true,
                "seg-10",
                true,
                "src-r1",
                "corr-r1",
                "idem-r1"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals("VISIT_CREATE", envelope.get("type"));
        assertEquals(List.of("http://bus-a", "http://bus-b"), envelope.get("routeDataBusUrls"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
        assertEquals("BR-10", payload.get("branchId"));
        assertEquals(true, payload.get("printTicket"));

        assertEquals("VISIT_CREATE", stub.lastType);
        assertEquals(List.of("http://bus-a", "http://bus-b"), stub.lastDataBusUrls);
        assertEquals(true, stub.lastSendToOtherBus);
    }
    @Test
    void publishVisitCreate_shouldBuildCanonicalVisitCreatePayload() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> result = api.publishVisitCreate(
                "target-1",
                "visitmanager",
                "BR-1",
                "EP-1",
                List.of("S1", "S2"),
                Map.of("segment", "VIP"),
                false,
                null,
                "src-vc-1",
                "corr-vc-1",
                "idem-vc-1"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals("VISIT_CREATE", envelope.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
        assertEquals("BR-1", payload.get("branchId"));
        assertEquals("EP-1", payload.get("entryPointId"));
        assertEquals(List.of("S1", "S2"), payload.get("serviceIds"));
        assertEquals(Map.of("segment", "VIP"), payload.get("parameters"));
        assertEquals(false, payload.get("printTicket"));

        assertEquals("VISIT_CREATE", stub.lastType);
        assertEquals("visitmanager", stub.lastDestination);
    }
    @Test
    void publishEvent_shouldReturnCanonicalEnvelope() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> payload = Map.of("visitId", "V-1");
        Map<String, Object> result = api.publishEvent("target-1", "visit.created", "crm", payload, false, "src-1", "corr-1", "idem-1");

        assertEquals("events", result.get("transport"));
        assertEquals(101L, result.get("outboxId"));

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals("target-1", envelope.get("target"));
        assertEquals("crm", envelope.get("destination"));
        assertEquals("ib.v1", envelope.get("envelopeVersion"));
        assertEquals("events", envelope.get("operation"));
        assertEquals(false, envelope.get("sendToOtherBus"));
        assertEquals("visit.created", envelope.get("type"));
        assertEquals("integration-broker", envelope.get("source"));
        assertEquals("corr-1", envelope.get("correlationId"));
        assertEquals("src-1", envelope.get("requestId"));
        assertEquals(payload, envelope.get("payload"));
        assertEquals("src-1", envelope.get("sourceMessageId"));
        assertEquals("idem-1", envelope.get("idempotencyKey"));
        assertNotNull(envelope.get("timestamp"));
        assertNull(envelope.get("request"));
        assertNull(envelope.get("response"));
        assertNull(envelope.get("status"));
        assertNull(envelope.get("message"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ibMeta = (Map<String, Object>) envelope.get("_ib");
        assertEquals("corr-1", ibMeta.get("correlationId"));
        assertEquals("src-1", ibMeta.get("requestId"));
        assertEquals("idem-1", ibMeta.get("idempotencyKey"));

        assertEquals("visit.created", stub.lastType);
        assertEquals("crm", stub.lastDestination);
        @SuppressWarnings("unchecked")
        Map<String, Object> sentBody = (Map<String, Object>) stub.lastBody;
        assertEquals("visit.created", sentBody.get("type"));
        assertEquals("corr-1", sentBody.get("correlationId"));
    }




    @Test
    void publishEvent_shouldFallbackDestinationToWildcard() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> result = api.publishEvent(" ", "visit.created", " ", Map.of("visitId", "V-1"), false, "src-1", "corr-1", "idem-1");

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals("default", envelope.get("target"));
        assertEquals("*", envelope.get("destination"));
    }


    @Test
    void publishEvent_shouldFallbackIdempotencyKeyFromSourceMessageId() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> result = api.publishEvent("target-1", "visit.created", "crm", Map.of("visitId", "V-1"), false, "src-700", "corr-700", " ");

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals("src-700", envelope.get("idempotencyKey"));
    }

    @Test
    void publishEvent_shouldFallbackIdempotencyKeyFromCorrelationIdWhenSourceMissing() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> result = api.publishEvent("target-1", "visit.created", "crm", Map.of("visitId", "V-1"), false, " ", "corr-701", null);

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals("corr-701", envelope.get("idempotencyKey"));
        assertEquals("corr-701", envelope.get("correlationId"));
        assertEquals("corr-701", envelope.get("requestId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ibMeta = (Map<String, Object>) envelope.get("_ib");
        assertEquals("corr-701", ibMeta.get("correlationId"));
        assertEquals("corr-701", ibMeta.get("requestId"));
    }

    @Test
    void publishEvent_shouldNotFailWhenCorrelationRequestAndIdempotencyAbsent() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> result = api.publishEvent("target-1", "visit.created", "crm", Map.of("visitId", "V-1"), false, " ", " ", " ");

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertNull(envelope.get("correlationId"));
        assertNull(envelope.get("requestId"));
        assertNull(envelope.get("idempotencyKey"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ibMeta = (Map<String, Object>) envelope.get("_ib");
        assertTrue(ibMeta.isEmpty());
    }

    @Test
    void publishEvent_shouldFallbackToUnknownType() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> result = api.publishEvent("target-1", "   ", "crm", Map.of("visitId", "V-1"), false, "src-1", "corr-1", "idem-1");

        assertEquals("UNKNOWN", stub.lastType);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals("UNKNOWN", envelope.get("type"));
    }

    @Test
    void publishEventRoute_shouldSendCanonicalEnvelopeToAdapter() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> result = api.publishEventRoute("target-1", "crm", "visit.created", List.of("http://bus-2"), Map.of("visitId", "V-2"), "src-2", "corr-2", "idem-2");

        assertEquals("events.route", result.get("transport"));
        assertEquals(111L, result.get("outboxId"));
        assertEquals("visit.created", stub.lastType);
        @SuppressWarnings("unchecked")
        Map<String, Object> sent = (Map<String, Object>) stub.lastBody;
        assertEquals("visit.created", sent.get("type"));
        assertEquals("events.route", sent.get("operation"));
        assertEquals("src-2", sent.get("requestId"));
        assertEquals(List.of("http://bus-2"), sent.get("routeDataBusUrls"));
        assertEquals("corr-2", sent.get("correlationId"));
        assertTrue(sent.containsKey("_ib"));
    }


    @Test
    void publishEventRoute_shouldNormalizeAndDeduplicateUrls() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> result = api.publishEventRoute(
                "target-1",
                "crm",
                "visit.created",
                java.util.Arrays.asList(" http://bus-1 ", "http://bus-1", " ", null, "http://bus-2"),
                Map.of("visitId", "V-2"),
                "src-2",
                "corr-2",
                "idem-2"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals(List.of("http://bus-1", "http://bus-2"), envelope.get("routeDataBusUrls"));
        assertEquals(List.of("http://bus-1", "http://bus-2"), stub.lastDataBusUrls);
    }

    @Test
    void publishEventRoute_shouldFallbackToUnknownType() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> result = api.publishEventRoute("target-1", "crm", " ", List.of("http://bus-2"), Map.of("visitId", "V-2"), "src-2", "corr-2", "idem-2");

        assertEquals("UNKNOWN", stub.lastType);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals("UNKNOWN", envelope.get("type"));
    }

    @Test
    void publishEventRoute_shouldPropagateSendToOtherBusFlag() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> result = api.publishEventRoute("target-1", "crm", "visit.created", List.of("http://bus-2"), Map.of("visitId", "V-2"), true, "src-2", "corr-2", "idem-2");

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals(true, envelope.get("sendToOtherBus"));
        assertEquals(true, stub.lastSendToOtherBus);
    }

    @Test
    void sendRequest_shouldFallbackToUnknownFunction() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> result = api.sendRequest("target-1", "visitmanager", " ", Map.of(), true, "src-3", "corr-3", "idem-3");

        assertEquals("unknown", stub.lastFunction);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals("request.unknown", envelope.get("type"));
        assertEquals("unknown", envelope.get("request"));
    }

    @Test
    void sendRequest_shouldExposeRequestMetadataInEnvelope() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> params = Map.of("clientId", "C-1");
        Map<String, Object> result = api.sendRequest("target-1", "visitmanager", "resolveVisit", params, true, "src-2", "corr-2", "idem-2");

        assertEquals("requests", result.get("transport"));
        assertEquals(202L, result.get("outboxId"));

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals("target-1", envelope.get("target"));
        assertEquals("visitmanager", envelope.get("destination"));
        assertEquals("requests", envelope.get("operation"));
        assertEquals(true, envelope.get("sendToOtherBus"));
        assertEquals("request.resolveVisit", envelope.get("type"));
        assertEquals("resolveVisit", envelope.get("request"));
        assertEquals("src-2", envelope.get("sourceMessageId"));
        assertEquals("idem-2", envelope.get("idempotencyKey"));
        assertEquals(params, envelope.get("payload"));
        assertEquals(false, envelope.get("response"));
        @SuppressWarnings("unchecked")
        Map<String, Object> sentParams = (Map<String, Object>) stub.lastBody;
        assertEquals("request.resolveVisit", sentParams.get("type"));
        assertNull(envelope.get("status"));
        assertNull(envelope.get("message"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ibMeta = (Map<String, Object>) envelope.get("_ib");
        assertEquals("corr-2", ibMeta.get("correlationId"));
        assertEquals("src-2", ibMeta.get("requestId"));
        assertEquals("idem-2", ibMeta.get("idempotencyKey"));
    }

    @Test
    void sendResponse_shouldExposeStatusMessageAndResponseMarker() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> body = Map.of("ok", true);
        Map<String, Object> result = api.sendResponse("target-1", "crm", 207, "partial", body, false, "src-3", "corr-3", "idem-3");

        assertEquals("responses", result.get("transport"));
        assertEquals(303L, result.get("outboxId"));

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals("target-1", envelope.get("target"));
        assertEquals("crm", envelope.get("destination"));
        assertEquals("responses", envelope.get("operation"));
        assertEquals(false, envelope.get("sendToOtherBus"));
        assertEquals("response", envelope.get("type"));
        assertEquals(true, envelope.get("response"));
        assertEquals("src-3", envelope.get("sourceMessageId"));
        assertEquals("idem-3", envelope.get("idempotencyKey"));
        assertEquals(207, envelope.get("status"));
        assertEquals("partial", envelope.get("message"));
        assertEquals(body, envelope.get("payload"));
        @SuppressWarnings("unchecked")
        Map<String, Object> sentResponse = (Map<String, Object>) stub.lastBody;
        assertEquals(true, sentResponse.get("response"));
    }


    @Test
    void publishEvent_shouldUseSourceMessageIdWhenCorrelationMissing() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> result = api.publishEvent("target-1", "visit.created", "crm", Map.of("visitId", "V-1"), false, "src-100", " ", "idem-1");

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals("src-100", envelope.get("correlationId"));
        assertEquals("src-100", envelope.get("sourceMessageId"));
    }

    @Test
    void publishEvent_shouldUseConfiguredSenderWhenPresent() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, runtimeConfigStoreWithSender("ib-custom-sender"));

        Map<String, Object> result = api.publishEvent("target-1", "visit.created", "crm", Map.of("visitId", "V-1"), false, "src-1", "corr-1", "idem-1");

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals("ib-custom-sender", envelope.get("source"));
    }


    @Test
    void publishEvent_shouldFallbackToDefaultSenderWhenConfigBlank() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, runtimeConfigStoreWithSender("   "));

        Map<String, Object> result = api.publishEvent("target-1", "visit.created", "crm", Map.of("visitId", "V-1"), false, "src-1", "corr-1", "idem-1");

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals("integration-broker", envelope.get("source"));
    }


    @Test
    void sendRequest_shouldNormalizeDestinationBeforeAdapterCall() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> result = api.sendRequest("target-1", "  visitmanager  ", "resolveVisit", Map.of(), true, "src-3", "corr-3", "idem-3");

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals("visitmanager", envelope.get("destination"));
        assertEquals("visitmanager", stub.lastDestination);
    }

    @Test
    void sendResponse_shouldNormalizeDestinationBeforeAdapterCall() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> result = api.sendResponse("target-1", "  crm  ", 200, "ok", Map.of("accepted", true), false, "src-3", "corr-3", "idem-3");

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals("crm", envelope.get("destination"));
        assertEquals("crm", stub.lastDestination);
    }

    @Test
    void sendResponse_shouldFallbackCorrelationIdFromSourceMessageId() {
        StubDataBusGroovyAdapter stub = new StubDataBusGroovyAdapter();
        DataBusApiImpl api = new DataBusApiImpl(stub, null);

        Map<String, Object> result = api.sendResponse("target-1", "crm", 200, "ok", Map.of("accepted", true), false, "src-500", " ", "idem-5");

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) result.get("envelope");
        assertEquals("src-500", envelope.get("correlationId"));
    }

    private RuntimeConfigStore runtimeConfigStoreWithSender(String sender) {
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
                sender,
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

    private static class StubDataBusGroovyAdapter extends DataBusGroovyAdapter {

        private String lastType;
        private String lastDestination;
        private String lastFunction;
        private Object lastBody;
        private List<String> lastDataBusUrls;
        private Boolean lastSendToOtherBus;

        StubDataBusGroovyAdapter() {
            super(null, null);
        }

        @Override
        public long publishEvent(String type, String destination, Boolean sendToOtherBus, Object body,
                                 String sourceMessageId, String correlationId, String idempotencyKey) {
            this.lastType = type;
            this.lastDestination = destination;
            this.lastBody = body;
            return 101L;
        }

        @Override
        public long publishEventRoute(String type, String destination, Boolean sendToOtherBus, List<String> dataBusUrls, Object body,
                                      String sourceMessageId, String correlationId, String idempotencyKey) {
            this.lastType = type;
            this.lastDestination = destination;
            this.lastBody = body;
            this.lastDataBusUrls = dataBusUrls;
            this.lastSendToOtherBus = sendToOtherBus;
            return 111L;
        }

        @Override
        public long sendRequest(String function, String destination, Boolean sendToOtherBus, Map<String, Object> params,
                                String sourceMessageId, String correlationId, String idempotencyKey) {
            this.lastFunction = function;
            this.lastDestination = destination;
            this.lastBody = params;
            return 202L;
        }

        @Override
        public long sendResponse(String destination, Boolean sendToOtherBus, Integer status, String message,
                                 Object response, String sourceMessageId, String correlationId, String idempotencyKey) {
            this.lastDestination = destination;
            this.lastBody = response;
            return 303L;
        }
    }
}
