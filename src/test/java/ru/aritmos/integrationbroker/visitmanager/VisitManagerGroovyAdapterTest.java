package ru.aritmos.integrationbroker.visitmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisitManagerGroovyAdapterTest {

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

        StubVisitManagerClient() {
            super(null, null, new ObjectMapper());
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
