package ru.aritmos.integrationbroker.adapters;

import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.databus.DataBusGroovyAdapter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DataBusApiImplTest {

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
        assertEquals(payload, envelope.get("payload"));
        assertEquals("src-1", envelope.get("sourceMessageId"));
        assertEquals("idem-1", envelope.get("idempotencyKey"));
        assertNotNull(envelope.get("timestamp"));
        assertNull(envelope.get("request"));
        assertNull(envelope.get("response"));
        assertNull(envelope.get("status"));
        assertNull(envelope.get("message"));

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
        assertEquals(List.of("http://bus-2"), sent.get("routeDataBusUrls"));
        assertEquals("corr-2", sent.get("correlationId"));
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
        public long publishEventRoute(String type, String destination, List<String> dataBusUrls, Object body,
                                      String sourceMessageId, String correlationId, String idempotencyKey) {
            this.lastType = type;
            this.lastDestination = destination;
            this.lastBody = body;
            return 111L;
        }

        @Override
        public long sendRequest(String function, String destination, Boolean sendToOtherBus, Map<String, Object> params,
                                String sourceMessageId, String correlationId, String idempotencyKey) {
            this.lastFunction = function;
            this.lastBody = params;
            return 202L;
        }

        @Override
        public long sendResponse(String destination, Boolean sendToOtherBus, Integer status, String message,
                                 Object response, String sourceMessageId, String correlationId, String idempotencyKey) {
            this.lastBody = response;
            return 303L;
        }
    }
}
