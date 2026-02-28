package ru.aritmos.integrationbroker.visitmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisitManagerGroovyAdapterTest {




    @Test
    void createVisitFromEventRest_shouldDelegateCanonicalPayload() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.createVisitFromEventRest(Map.of(
                "branchId", "B-1",
                "entryPointId", "EP-1",
                "serviceIds", List.of("S-1"),
                "parameters", Map.of("segment", "VIP"),
                "printTicket", true,
                "segmentationRuleId", "rule-1"
        ), Map.of("messageId", "m13", "correlationId", "c13", "idempotencyKey", "i13"));

        assertEquals(true, result.get("success"));
        assertEquals("POST", client.lastMethod);
        assertEquals("/entrypoint/branches/B-1/entry-points/EP-1/visits/parameters?printTicket=true&segmentationRuleId=rule-1", client.lastPath);
    }

    @Test
    void servicesCatalogRest_shouldBuildCatalogPath() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.servicesCatalogRest(Map.of(
                "branchId", "branch 9"
        ), Map.of("messageId", "m12", "correlationId", "c12", "idempotencyKey", "i12"));

        assertEquals(true, result.get("success"));
        assertEquals("GET", client.lastMethod);
        assertEquals("/entrypoint/branches/branch+9/services/catalog", client.lastPath);
    }

    @Test
    void getBranchStateRest_shouldBuildManagementPath() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.getBranchStateRest(Map.of(
                "branchId", "branch 1"
        ), Map.of("messageId", "m8", "correlationId", "c8", "idempotencyKey", "i8"));

        assertEquals(true, result.get("success"));
        assertEquals("GET", client.lastMethod);
        assertEquals("/managementinformation/branches/branch+1", client.lastPath);
    }


    @Test
    void getBranchesStateRest_shouldBuildPathWithOptionalUserName() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.getBranchesStateRest(Map.of(
                "userName", "operator 1"
        ), Map.of("messageId", "m10", "correlationId", "c10", "idempotencyKey", "i10"));

        assertEquals(true, result.get("success"));
        assertEquals("GET", client.lastMethod);
        assertEquals("/managementinformation/branches?userName=operator+1", client.lastPath);
    }

    @Test
    void getBranchesTinyRest_shouldCallTinyEndpoint() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.getBranchesTinyRest(Map.of(), Map.of(
                "messageId", "m9",
                "correlationId", "c9",
                "idempotencyKey", "i9"
        ));

        assertEquals(true, result.get("success"));
        assertEquals("GET", client.lastMethod);
        assertEquals("/managementinformation/branches/tiny", client.lastPath);
    }


    @Test
    void updateVisitParametersRest_shouldBuildPathAndSendStringMap() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.updateVisitParametersRest(Map.of(
                "branchId", "branch 1",
                "visitId", "visit/1",
                "parameters", Map.of("segment", "VIP", "priority", 1)
        ), Map.of("messageId", "m11", "correlationId", "c11", "idempotencyKey", "i11"));

        assertEquals(true, result.get("success"));
        assertEquals("PUT", client.lastMethod);
        assertEquals("/entrypoint/branches/branch+1/visits/visit%2F1", client.lastPath);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) client.lastBody;
        assertEquals("1", body.get("priority"));
    }

    @Test
    void postponeCurrentVisitRest_shouldBuildPathAndAttachSidCookie() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.postponeCurrentVisitRest(Map.of(
                "branchId", "branch 1",
                "servicePointId", "sp/2",
                "sid", "SID-7"
        ), Map.of("messageId", "m7", "correlationId", "c7", "idempotencyKey", "i7"));

        assertEquals(true, result.get("success"));
        assertEquals("PUT", client.lastMethod);
        assertEquals("/servicepoint/branches/branch+1/servicePoints/sp%2F2/postpone", client.lastPath);
        assertEquals("sid=SID-7", client.lastHeaders.get("Cookie"));
    }

    @Test
    void startAutoCallRest_shouldBuildPathAndAttachSidCookie() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.startAutoCallRest(Map.of(
                "branchId", "branch 1",
                "servicePointId", "sp/2",
                "sid", "SID-3"
        ), Map.of("messageId", "m5", "correlationId", "c5", "idempotencyKey", "i5"));

        assertEquals(true, result.get("success"));
        assertEquals("PUT", client.lastMethod);
        assertEquals("/servicepoint/branches/branch+1/service-points/sp%2F2/auto-call/start", client.lastPath);
        assertEquals("sid=SID-3", client.lastHeaders.get("Cookie"));
    }

    @Test
    void cancelAutoCallRest_shouldBuildPathAndValidateServicePointId() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> invalid = adapter.cancelAutoCallRest(Map.of("branchId", "b1"), Map.of());
        assertEquals("INVALID_ARGUMENT", invalid.get("errorCode"));

        Map<String, Object> ok = adapter.cancelAutoCallRest(Map.of(
                "branchId", "branch 1",
                "servicePointId", "sp/2"
        ), Map.of("messageId", "m6", "correlationId", "c6", "idempotencyKey", "i6"));

        assertEquals(true, ok.get("success"));
        assertEquals("PUT", client.lastMethod);
        assertEquals("/servicepoint/branches/branch+1/service-points/sp%2F2/auto-call/cancel", client.lastPath);
    }

    @Test
    void createVisitRest_shouldReturnInvalidArgumentWhenScalarServiceIdIsBlank() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.createVisitRest(Map.of(
                "branchId", "b1",
                "serviceIds", "   "
        ), Map.of());

        assertEquals(false, result.get("success"));
        assertEquals("INVALID_ARGUMENT", result.get("errorCode"));
    }


    @Test
    void enterServicePointModeFromEventRest_shouldDelegateToEnterEndpoint() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.enterServicePointModeFromEventRest(Map.of(
                "branchId", "branch 7",
                "mode", "WORK",
                "autoCallEnabled", true,
                "sid", "SID-77"
        ), Map.of("messageId", "m16", "correlationId", "c16", "idempotencyKey", "i16"));

        assertEquals(true, result.get("success"));
        assertEquals("POST", client.lastMethod);
        assertEquals("/servicepoint/branches/branch+7/enter?mode=WORK&isAutoCallEnabled=true", client.lastPath);
        assertEquals("sid=SID-77", client.lastHeaders.get("Cookie"));
    }

    @Test
    void enterServicePointModeRest_shouldBuildPathAndAttachSidCookie() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.enterServicePointModeRest(Map.of(
                "branchId", "branch 1",
                "mode", "WORK",
                "autoCallEnabled", true,
                "sid", "SID-1"
        ), Map.of("messageId", "m3", "correlationId", "c3", "idempotencyKey", "i3"));

        assertEquals(true, result.get("success"));
        assertEquals("POST", client.lastMethod);
        assertEquals("/servicepoint/branches/branch+1/enter?mode=WORK&isAutoCallEnabled=true", client.lastPath);
        assertEquals("sid=SID-1", client.lastHeaders.get("Cookie"));
    }



    @Test
    void startAutoCallFromEventRest_shouldDelegateToStartAutoCallEndpoint() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.startAutoCallFromEventRest(Map.of(
                "branchId", "branch 9",
                "servicePointId", "sp 9"
        ), Map.of("messageId", "m18", "correlationId", "c18", "idempotencyKey", "i18"));

        assertEquals(true, result.get("success"));
        assertEquals("PUT", client.lastMethod);
        assertEquals("/servicepoint/branches/branch+9/service-points/sp+9/auto-call/start", client.lastPath);
    }

    @Test
    void exitServicePointModeFromEventRest_shouldDelegateToExitEndpoint() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.exitServicePointModeFromEventRest(Map.of(
                "branchId", "branch 8",
                "isForced", true,
                "reason", "shift done",
                "sid", "SID-88"
        ), Map.of("messageId", "m17", "correlationId", "c17", "idempotencyKey", "i17"));

        assertEquals(true, result.get("success"));
        assertEquals("POST", client.lastMethod);
        assertEquals("/servicepoint/branches/branch+8/exit?isForced=true&reason=shift+done", client.lastPath);
        assertEquals("sid=SID-88", client.lastHeaders.get("Cookie"));
    }

    @Test
    void exitServicePointModeRest_shouldBuildPathWithReasonAndKeepExistingCookies() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.exitServicePointModeRest(Map.of(
                "branchId", "branch 1",
                "isForced", true,
                "reason", "operator request",
                "sid", "SID-2",
                "headers", Map.of("Cookie", "theme=dark")
        ), Map.of("messageId", "m4", "correlationId", "c4", "idempotencyKey", "i4"));

        assertEquals(true, result.get("success"));
        assertEquals("POST", client.lastMethod);
        assertEquals("/servicepoint/branches/branch+1/exit?isForced=true&reason=operator+request", client.lastPath);
        assertEquals("theme=dark; sid=SID-2", client.lastHeaders.get("Cookie"));
    }


    @Test
    void createVirtualVisitFromEventRest_shouldDelegateToVirtualVisitEndpoint() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.createVirtualVisitFromEventRest(Map.of(
                "branchId", "branch 5",
                "servicePointId", "sp-9",
                "serviceIds", List.of("S-10")
        ), Map.of("messageId", "m14", "correlationId", "c14", "idempotencyKey", "i14"));

        assertEquals(true, result.get("success"));
        assertEquals("POST", client.lastMethod);
        assertEquals("/entrypoint/branches/branch+5/service-points/sp-9/virtual-visits", client.lastPath);
    }

    @Test
    void createVirtualVisitRest_shouldValidateRequiredArgs() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.createVirtualVisitRest(Map.of("branchId", "b1"), Map.of());

        assertEquals(false, result.get("success"));
        assertEquals("INVALID_ARGUMENT", result.get("errorCode"));
    }

    @Test
    void createVisitOnPrinterRest_shouldUsePrinterPathAndMapBodyWhenParametersPresent() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.createVisitOnPrinterRest(Map.of(
                "branchId", "branch 1",
                "printerId", "printer/2",
                "serviceIds", List.of("s1"),
                "parameters", Map.of("segment", "VIP"),
                "printTicket", true,
                "segmentationRuleId", "rule 1"
        ), Map.of("messageId", "m1", "correlationId", "c1", "idempotencyKey", "i1"));

        assertEquals(true, result.get("success"));
        assertEquals("POST", client.lastMethod);
        assertEquals("/entrypoint/branches/branch+1/printers/printer%2F2/visits?printTicket=true&segmentationRuleId=rule+1", client.lastPath);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) client.lastBody;
        assertEquals(List.of("s1"), body.get("serviceIds"));
    }


    @Test
    void callNextVisitFromEventRest_shouldDelegateToCallEndpoint() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.callNextVisitFromEventRest(Map.of(
                "branchId", "branch 6",
                "servicePointId", "sp-6",
                "autoCallEnabled", true
        ), Map.of("messageId", "m15", "correlationId", "c15", "idempotencyKey", "i15"));

        assertEquals(true, result.get("success"));
        assertEquals("POST", client.lastMethod);
        assertEquals("/servicepoint/branches/branch+6/servicePoints/sp-6/call?isAutoCallEnabled=true", client.lastPath);
    }

    @Test
    void callNextVisitRest_shouldBuildServicePointCallPath() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerGroovyAdapter adapter = new VisitManagerGroovyAdapter(client);

        Map<String, Object> result = adapter.callNextVisitRest(Map.of(
                "branchId", "branch 1",
                "servicePointId", "sp/2",
                "autoCallEnabled", true
        ), Map.of("messageId", "m2", "correlationId", "c2", "idempotencyKey", "i2"));

        assertEquals(true, result.get("success"));
        assertEquals("POST", client.lastMethod);
        assertEquals("/servicepoint/branches/branch+1/servicePoints/sp%2F2/call?isAutoCallEnabled=true", client.lastPath);
    }

    static class StubVisitManagerClient extends VisitManagerClient {
        String lastMethod;
        String lastPath;
        Object lastBody;
        Map<String, String> lastHeaders;
        String lastCreateVisitBranchId;
        String lastCreateVisitEntryPointId;

        StubVisitManagerClient() {
            super(null, null, new ObjectMapper(), null);
        }


        @Override
        public CallResult createVisitWithParametersRest(String branchId,
                                                        String entryPointId,
                                                        List<String> serviceIds,
                                                        Map<String, String> parameters,
                                                        boolean printTicket,
                                                        String segmentationRuleId,
                                                        Map<String, String> extraHeaders,
                                                        String sourceMessageId,
                                                        String correlationId,
                                                        String idempotencyKey) {
            this.lastCreateVisitBranchId = branchId;
            this.lastCreateVisitEntryPointId = entryPointId;
            this.lastMethod = "POST";
            this.lastPath = "/entrypoint/branches/" + branchId + "/entry-points/" + entryPointId
                    + "/visits/parameters?printTicket=" + printTicket
                    + (segmentationRuleId == null ? "" : "&segmentationRuleId=" + segmentationRuleId);
            this.lastBody = Map.of("serviceIds", serviceIds, "parameters", parameters);
            this.lastHeaders = extraHeaders;
            return CallResult.direct(200, null);
        }

        @Override
        public CallResult callRestEndpoint(String method,
                                           String path,
                                           Object body,
                                           Map<String, String> extraHeaders,
                                           String sourceMessageId,
                                           String correlationId,
                                           String idempotencyKey) {
            this.lastMethod = method;
            this.lastPath = path;
            this.lastBody = body;
            this.lastHeaders = extraHeaders;
            return CallResult.direct(200, null);
        }
    }
}
