package ru.aritmos.integrationbroker.adapters;

import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.visitmanager.VisitManagerClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisitManagerApiImplTest {




    @Test
    void createVisit_shouldUseServicesEndpointClientMethod() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.createVisit(
                "default",
                "branch-1",
                "ep-1",
                java.util.List.of("svc-1", "svc-1", " svc-2 "),
                true,
                "seg-1",
                Map.of("X-Req", "1"),
                "m1",
                "c1",
                "i1"
        );

        assertEquals("createVisitRest", client.lastCall);
        assertEquals(java.util.List.of("svc-1", "svc-2"), client.lastServiceIds);
    }

    @Test
    void createVisitFromEvent_shouldMapVisitCreatePayload() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.createVisitFromEvent(
                "default",
                Map.of(
                        "branchId", "br-1",
                        "entryPointId", "ep-1",
                        "serviceIds", java.util.List.of("svc-1", 77),
                        "parameters", Map.of("segment", "VIP", "priority", 5),
                        "printTicket", true,
                        "segmentationRuleId", "seg-1"
                ),
                Map.of("X-Trace", "t-1"),
                "m-evt-1",
                "c-evt-1",
                "i-evt-1"
        );

        assertEquals("createVisitWithParametersRest", client.lastCall);
        assertEquals(java.util.List.of("svc-1", "77"), client.lastServiceIds);
        assertEquals(java.util.Map.of("segment", "VIP", "priority", "5"), client.lastParameters);
    }




    @Test
    void getServicesCatalogWithHeadersConvenience_shouldDelegateAndUseDefaultTarget() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.getServicesCatalogWithHeaders("dep-77", Map.of("X-Req", "77"), "corr-77");

        assertEquals("callRestEndpoint", client.lastCall);
        assertEquals("GET", client.lastMethod);
        assertEquals("/entrypoint/branches/dep-77/services/catalog", client.lastPath);
        assertEquals("77", client.lastHeaders.get("X-Req"));
    }
    @Test
    void getServicesCatalogWithHeaders_shouldUseCatalogEndpointAndPassHeaders() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.getServicesCatalog("default", "dep/1", Map.of("X-Custom", "v1"), "m-c1", "corr-c1", "idem-c1");

        assertEquals("callRestEndpoint", client.lastCall);
        assertEquals("GET", client.lastMethod);
        assertEquals("/entrypoint/branches/dep%2F1/services/catalog", client.lastPath);
        assertEquals("v1", client.lastHeaders.get("X-Custom"));
    }
    @Test
    void getBranchState_shouldEncodePathSegment() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        Map<String, Object> result = api.getBranchState("default", "dep/1 room 2", Map.of(), "m1", "c1", "i1");

        assertEquals("GET", client.lastMethod);
        assertEquals("/managementinformation/branches/dep%2F1%20room%202", client.lastPath);
        assertEquals("DIRECT", result.get("mode"));
        assertEquals(200, result.get("httpStatus"));
    }

    @Test
    void getBranchesState_shouldEncodeQueryParam() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.getBranchesState("default", "ivan petrov+ops@example.com", Map.of(), "m1", "c1", "i1");

        assertEquals("GET", client.lastMethod);
        assertEquals("/managementinformation/branches?userName=ivan%20petrov%2Bops%40example.com", client.lastPath);
    }

    @Test
    void callNextVisit_shouldEncodeBranchAndServicePoint() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.callNextVisit("default", "b/ 1", "sp/ 2", true, Map.of(), "m1", "c1", "i1");

        assertEquals("POST", client.lastMethod);
        assertEquals("/servicepoint/branches/b%2F%201/servicePoints/sp%2F%202/call?isAutoCallEnabled=true", client.lastPath);
    }

    @Test
    void postponeCurrentVisit_shouldEncodeBranchAndServicePoint() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.postponeCurrentVisit("default", "b/ 1", "sp/ 2", Map.of(), "m1", "c1", "i1");

        assertEquals("PUT", client.lastMethod);
        assertEquals("/servicepoint/branches/b%2F%201/servicePoints/sp%2F%202/postpone", client.lastPath);
    }


    @Test
    void enterServicePointMode_shouldEncodeBranchAndQuery() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.enterServicePointMode("default", "dep/ 1", Map.of("mode", "operator on", "line", 2), Map.of(), "m1", "c1", "i1");

        assertEquals("POST", client.lastMethod);
        org.junit.jupiter.api.Assertions.assertTrue(client.lastPath.startsWith("/servicepoint/branches/dep%2F%201/enter?"));
        org.junit.jupiter.api.Assertions.assertTrue(client.lastPath.contains("mode=operator%20on"));
        org.junit.jupiter.api.Assertions.assertTrue(client.lastPath.contains("line=2"));
    }

    @Test
    void exitServicePointMode_shouldEncodeBranchAndQuery() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.exitServicePointMode("default", "dep/ 1", Map.of("isForced", true, "reason", "late shift"), Map.of(), "m1", "c1", "i1");

        assertEquals("POST", client.lastMethod);
        org.junit.jupiter.api.Assertions.assertTrue(client.lastPath.startsWith("/servicepoint/branches/dep%2F%201/exit?"));
        org.junit.jupiter.api.Assertions.assertTrue(client.lastPath.contains("reason=late%20shift"));
        org.junit.jupiter.api.Assertions.assertTrue(client.lastPath.contains("isForced=true"));
    }

    @Test
    void autoCallMethods_shouldUseServicePointsDashedPath() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.startAutoCall("default", "b/ 1", "sp/ 2", Map.of(), "m1", "c1", "i1");
        assertEquals("PUT", client.lastMethod);
        assertEquals("/servicepoint/branches/b%2F%201/service-points/sp%2F%202/auto-call/start", client.lastPath);

        api.cancelAutoCall("default", "b/ 1", "sp/ 2", Map.of(), "m1", "c1", "i1");
        assertEquals("PUT", client.lastMethod);
        assertEquals("/servicepoint/branches/b%2F%201/service-points/sp%2F%202/auto-call/cancel", client.lastPath);
    }


    @Test
    void shouldReturnInvalidArgumentWhenBranchMissing() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        Map<String, Object> result = api.getBranchState("default", "  ", Map.of(), "m1", "c1", "i1");

        assertEquals("ERROR", result.get("mode"));
        assertEquals("INVALID_ARGUMENT", result.get("errorCode"));
    }

    @Test
    void shouldReturnInvalidArgumentWhenServicePointMissing() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        Map<String, Object> result = api.startAutoCall("default", "branch-1", " ", Map.of(), "m1", "c1", "i1");

        assertEquals("ERROR", result.get("mode"));
        assertEquals("INVALID_ARGUMENT", result.get("errorCode"));
    }


    @Test
    void shouldNotInvokeClientWhenBranchMissingForCatalog() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        Map<String, Object> result = api.getServicesCatalog("default", " ");

        assertEquals("ERROR", result.get("mode"));
        assertEquals("INVALID_ARGUMENT", result.get("errorCode"));
        assertNull(client.lastMethod);
    }

    @Test
    void shouldAllowBlankEntryPointForCreateAndDelegateToClientDefaults() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        Map<String, Object> result = api.createVisitWithParameters(
                "default",
                "branch-1",
                " ",
                java.util.List.of("s1"),
                Map.of(),
                false,
                null,
                Map.of(),
                "m1",
                "c1",
                "i1"
        );

        assertEquals("DIRECT", result.get("mode"));
        assertEquals("createVisitWithParametersRest", client.lastCall);
    }

    @Test
    void createVisitWithParameters_shouldNormalizeAndDeduplicateServiceIdsBeforeClientCall() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.createVisitWithParameters(
                "default",
                "branch-1",
                "ep-1",
                java.util.List.of(" svc-1 ", "svc-1", "svc-2", " "),
                Map.of("segment", "VIP"),
                false,
                null,
                Map.of(),
                "m1",
                "c1",
                "i1"
        );

        assertEquals(java.util.List.of("svc-1", "svc-2"), client.lastServiceIds);
    }

    @Test
    void createVisitWithParameters_shouldValidateServiceIds() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        Map<String, Object> result = api.createVisitWithParameters(
                "default",
                "branch-1",
                "ep-1",
                java.util.Arrays.asList(" ", null),
                Map.of("segment", "VIP"),
                false,
                null,
                Map.of(),
                "m1",
                "c1",
                "i1"
        );

        assertEquals("ERROR", result.get("mode"));
        assertEquals("INVALID_ARGUMENT", result.get("errorCode"));
        assertNull(client.lastServiceIds);
    }

    @Test
    void createVisitWithParameters_shouldNormalizeParametersBeforeClientCall() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.createVisitWithParameters(
                "default",
                "branch-1",
                "ep-1",
                java.util.List.of("s1"),
                new java.util.LinkedHashMap<>(java.util.Map.of(" segment ", " VIP ", "channel", " kiosks ")),
                false,
                null,
                Map.of(),
                "m1",
                "c1",
                "i1"
        );

        assertEquals(java.util.Map.of("segment", "VIP", "channel", "kiosks"), client.lastParameters);
    }

    @Test
    void updateVisitParameters_shouldNormalizeParametersBeforeClientCall() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.updateVisitParameters(
                "default",
                "branch-1",
                "visit-1",
                new java.util.LinkedHashMap<>(java.util.Map.of(" queue ", " A1 ", " window", " 5 ")),
                Map.of(),
                "m1",
                "c1",
                "i1"
        );

        assertEquals(java.util.Map.of("queue", "A1", "window", "5"), client.lastParameters);
    }

    @Test
    void callEndpoint_shouldValidateMethodAndPath() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        Map<String, Object> methodRes = api.callEndpoint("default", " ", "/x", Map.of(), Map.of(), "m1", "c1", "i1");
        assertEquals("ERROR", methodRes.get("mode"));
        assertEquals("INVALID_ARGUMENT", methodRes.get("errorCode"));

        Map<String, Object> pathRes = api.callEndpoint("default", "get", " ", Map.of(), Map.of(), "m1", "c1", "i1");
        assertEquals("ERROR", pathRes.get("mode"));
        assertEquals("INVALID_ARGUMENT", pathRes.get("errorCode"));
        assertNull(client.lastCall);
    }

    @Test
    void callEndpoint_shouldNormalizeMethodAndPath() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.callEndpoint("default", " post ", " /custom/path ", Map.of("a", 1), Map.of(), "m1", "c1", "i1");

        assertEquals("POST", client.lastMethod);
        assertEquals("/custom/path", client.lastPath);
    }


    @Test
    void callEndpoint_shouldRejectUnsupportedMethod() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        Map<String, Object> result = api.callEndpoint("default", "TRACE", "/custom/path", Map.of(), Map.of(), "m1", "c1", "i1");

        assertEquals("ERROR", result.get("mode"));
        assertEquals("INVALID_ARGUMENT", result.get("errorCode"));
        assertNull(client.lastCall);
    }

    @Test
    void shouldNotInvokeClientWhenVisitIdMissingForUpdate() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        Map<String, Object> result = api.updateVisitParameters("default", "branch-1", " ", Map.of(), Map.of(), "m1", "c1", "i1");

        assertEquals("ERROR", result.get("mode"));
        assertEquals("INVALID_ARGUMENT", result.get("errorCode"));
        assertNull(client.lastMethod);
    }


    @Test
    void shouldNotInvokeClientWhenServicePointMissingForCallNext() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        Map<String, Object> result = api.callNextVisit("default", "branch-1", " ", true, Map.of(), "m1", "c1", "i1");

        assertEquals("ERROR", result.get("mode"));
        assertEquals("INVALID_ARGUMENT", result.get("errorCode"));
        assertNull(client.lastCall);
        assertNull(client.lastMethod);
    }


    @Test
    void createVirtualVisit_shouldUseVirtualVisitsEndpoint() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.createVirtualVisit("default", "br/ 1", "sp/ 2", java.util.List.of("svc-1"), Map.of(), "m1", "c1", "i1");

        assertEquals("POST", client.lastMethod);
        assertEquals("/entrypoint/branches/br%2F%201/service-points/sp%2F%202/virtual-visits", client.lastPath);
        assertEquals("callRestEndpoint", client.lastCall);
    }

    @Test
    void createVisitOnPrinterWithServices_shouldBuildPrinterPathAndQuery() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.createVisitOnPrinterWithServices("default", "br/ 1", "pr/ 9", java.util.List.of("svc-1"), true, "seg A", Map.of(), "m1", "c1", "i1");

        assertEquals("POST", client.lastMethod);
        assertEquals("/entrypoint/branches/br%2F%201/printers/pr%2F%209/visits?printTicket=true&segmentationRuleId=seg%20A", client.lastPath);
        assertEquals("callRestEndpoint", client.lastCall);
    }

    @Test
    void createVisitOnPrinterWithParameters_shouldSendBodyMap() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.createVisitOnPrinterWithParameters(
                "default",
                "br-1",
                "pr-1",
                java.util.List.of("svc-1", "svc-2"),
                Map.of("segment", "VIP"),
                false,
                null,
                Map.of(),
                "m1",
                "c1",
                "i1"
        );

        assertEquals("POST", client.lastMethod);
        assertEquals("/entrypoint/branches/br-1/printers/pr-1/visits?printTicket=false", client.lastPath);
        assertTrue(client.lastBody instanceof Map);
        Map<?, ?> body = (Map<?, ?>) client.lastBody;
        assertTrue(body.containsKey("serviceIds"));
        assertTrue(body.containsKey("parameters"));
    }

    @Test
    void createVisitOnPrinterWithParameters_shouldNormalizeParametersMap() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.createVisitOnPrinterWithParameters(
                "default",
                "br-1",
                "pr-1",
                java.util.List.of("svc-1"),
                new java.util.LinkedHashMap<>(java.util.Map.of("segment", " VIP ", "channel", " kiosks ")),
                true,
                null,
                Map.of(),
                "m1",
                "c1",
                "i1"
        );

        assertTrue(client.lastBody instanceof Map);
        Map<?, ?> body = (Map<?, ?>) client.lastBody;
        assertEquals(java.util.Map.of("segment", "VIP", "channel", "kiosks"), body.get("parameters"));
    }

    @Test
    void createVisitOnPrinter_shouldReturnInvalidArgumentWhenPrinterMissing() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        Map<String, Object> result = api.createVisitOnPrinterWithServices("default", "br-1", " ", java.util.List.of("svc"), true, null, Map.of(), "m1", "c1", "i1");

        assertEquals("ERROR", result.get("mode"));
        assertEquals("INVALID_ARGUMENT", result.get("errorCode"));
        assertNull(client.lastCall);
    }

    @Test
    void defaultConvenienceMethods_shouldDelegate() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.getBranchesTiny("default", "corr-1");
        assertEquals("callRestEndpoint", client.lastCall);
        assertEquals("GET", client.lastMethod);

        api.createVirtualVisit("br-1", "sp-1", java.util.List.of("svc"), "corr-2");
        assertEquals("callRestEndpoint", client.lastCall);
        assertEquals("POST", client.lastMethod);
    }


    @Test
    void withSidConvenienceForEnter_shouldAttachCookieHeader() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.enterServicePointModeWithSid("default", "br-1", Map.of("mode", "operator"), "SID-1", "corr");

        assertEquals("POST", client.lastMethod);
        assertEquals("sid=SID-1", client.lastHeaders.get("Cookie"));
    }

    @Test
    void withSidConvenienceForExit_shouldAttachCookieHeader() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.exitServicePointModeWithSid("default", "br-1", Map.of("isForced", true), "SID-2", "corr");

        assertEquals("POST", client.lastMethod);
        assertEquals("sid=SID-2", client.lastHeaders.get("Cookie"));
    }

    @Test
    void withSidConvenienceForCallNext_shouldAttachCookieHeader() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.callNextVisitWithSid("default", "br-1", "sp-1", true, "SID-3", "corr");

        assertEquals("POST", client.lastMethod);
        assertEquals("sid=SID-3", client.lastHeaders.get("Cookie"));
    }

    @Test
    void withSidConvenienceForVirtualVisit_shouldAttachCookieHeader() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.createVirtualVisitWithSid("default", "br-1", "sp-1", java.util.List.of("svc-1"), "SID-4", "corr");

        assertEquals("POST", client.lastMethod);
        assertEquals("sid=SID-4", client.lastHeaders.get("Cookie"));
    }

    @Test
    void withSidConvenienceForPrinterVisit_shouldAttachCookieHeader() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.createVisitOnPrinterWithServicesAndSid("default", "br-1", "pr-1", java.util.List.of("svc-1"), true, null, "SID-5", "corr");

        assertEquals("POST", client.lastMethod);
        assertEquals("sid=SID-5", client.lastHeaders.get("Cookie"));
    }

    @Test
    void withQuery_shouldSkipBlankStringValues() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.exitServicePointMode("default", "br-1", Map.of("reason", "   ", "isForced", true), Map.of(), "m1", "c1", "i1");

        assertEquals("POST", client.lastMethod);
        assertTrue(client.lastPath.contains("isForced=true"));
        assertTrue(!client.lastPath.contains("reason="));
    }

    @Test
    void enterWithTypedSidConvenience_shouldBuildExpectedQuery() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.enterServicePointModeWithSid("default", "br-1", "operator", true, "SID-6", "corr");

        assertEquals("POST", client.lastMethod);
        assertTrue(client.lastPath.contains("mode=operator"));
        assertTrue(client.lastPath.contains("isAutoCallEnabled=true"));
    }

    @Test
    void exitWithTypedSidConvenience_shouldBuildExpectedQuery() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.exitServicePointModeWithSid("default", "br-1", true, "manual reason", "SID-7", "corr");

        assertEquals("POST", client.lastMethod);
        assertTrue(client.lastPath.contains("isForced=true"));
        assertTrue(client.lastPath.contains("reason=manual%20reason"));
    }


    @Test
    void createVirtualVisit_shouldValidateServiceIds() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        Map<String, Object> result = api.createVirtualVisit("default", "br-1", "sp-1", java.util.Arrays.asList(" ", null), Map.of(), "m1", "c1", "i1");

        assertEquals("ERROR", result.get("mode"));
        assertEquals("INVALID_ARGUMENT", result.get("errorCode"));
        assertNull(client.lastCall);
    }

    @Test
    void createVisitOnPrinter_shouldNormalizeAndDeduplicateServiceIds() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.createVisitOnPrinterWithServices("default", "br-1", "pr-1", java.util.List.of(" svc1 ", "svc1", "svc2", " "), true, null, Map.of(), "m1", "c1", "i1");

        assertTrue(client.lastBody instanceof java.util.List);
        assertEquals(java.util.List.of("svc1", "svc2"), client.lastBody);
    }

    @Test
    void sidConvenienceForPostponeAndAutoCall_shouldAttachCookieHeader() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.postponeCurrentVisitWithSid("default", "br-1", "sp-1", "SID-8", "corr");
        assertEquals("sid=SID-8", client.lastHeaders.get("Cookie"));

        api.startAutoCallWithSid("default", "br-1", "sp-1", "SID-9", "corr");
        assertEquals("sid=SID-9", client.lastHeaders.get("Cookie"));

        api.cancelAutoCallWithSid("default", "br-1", "sp-1", "SID-10", "corr");
        assertEquals("sid=SID-10", client.lastHeaders.get("Cookie"));
    }

    @Test
    void createVisitOnPrinterWithParametersAndSid_shouldAttachCookieHeader() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.createVisitOnPrinterWithParametersAndSid("default", "br-1", "pr-1", java.util.List.of("svc-1"), Map.of("p", "v"), true, null, "SID-11", "corr");

        assertEquals("POST", client.lastMethod);
        assertEquals("sid=SID-11", client.lastHeaders.get("Cookie"));
    }


    @Test
    void withSidAndHeaders_shouldMergeCookieWhenMissingSidInExistingCookie() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.enterServicePointModeWithSidAndHeaders(
                "default",
                "br-1",
                Map.of("mode", "operator"),
                "SID-12",
                Map.of("Cookie", "lang=ru", "X-Debug", "1"),
                "corr"
        );

        assertEquals("POST", client.lastMethod);
        assertEquals("lang=ru; sid=SID-12", client.lastHeaders.get("Cookie"));
        assertEquals("1", client.lastHeaders.get("X-Debug"));
    }

    @Test
    void withSidAndHeaders_shouldNotDuplicateSidWhenAlreadyPresent() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.exitServicePointModeWithSidAndHeaders(
                "default",
                "br-1",
                Map.of("isForced", true),
                "SID-12",
                Map.of("Cookie", "sid=SID-12; lang=ru"),
                "corr"
        );

        assertEquals("POST", client.lastMethod);
        assertEquals("sid=SID-12; lang=ru", client.lastHeaders.get("Cookie"));
    }

    @Test
    void printerWithSidAndHeaders_shouldPassMergedHeaders() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.createVisitOnPrinterWithParametersAndSidAndHeaders(
                "default",
                "br-1",
                "pr-1",
                java.util.List.of("svc-1"),
                Map.of("segment", "VIP"),
                true,
                null,
                "SID-13",
                Map.of("X-Trace", "abc"),
                "corr"
        );

        assertEquals("POST", client.lastMethod);
        assertEquals("sid=SID-13", client.lastHeaders.get("Cookie"));
        assertEquals("abc", client.lastHeaders.get("X-Trace"));
    }

    @Test
    void callNextWithSidAndHeaders_shouldAttachSidAndKeepCustomHeader() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.callNextVisitWithSidAndHeaders("default", "br-1", "sp-1", true, "SID-14", Map.of("X-Flow", "k1"), "corr");

        assertEquals("POST", client.lastMethod);
        assertEquals("sid=SID-14", client.lastHeaders.get("Cookie"));
        assertEquals("k1", client.lastHeaders.get("X-Flow"));
    }


    @Test
    void withSidAndHeaders_shouldHandleLowercaseCookieHeaderName() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.callNextVisitWithSidAndHeaders(
                "default",
                "br-1",
                "sp-1",
                true,
                "SID-15",
                Map.of("cookie", "lang=ru"),
                "corr"
        );

        assertEquals("POST", client.lastMethod);
        assertEquals("lang=ru; sid=SID-15", client.lastHeaders.get("cookie"));
    }


    @Test
    void withSidAndHeaders_shouldNotDuplicateSidCookieWhenAlreadyPresent() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.callNextVisitWithSidAndHeaders(
                "default",
                "br-1",
                "sp-1",
                true,
                "SID-33",
                Map.of("Cookie", "lang=ru; SID=SID-OLD"),
                "corr"
        );

        assertEquals("POST", client.lastMethod);
        assertEquals("lang=ru; SID=SID-OLD", client.lastHeaders.get("Cookie"));
    }

    @Test
    void withSidAndHeaders_shouldAppendSidWhenCookieContainsSimilarNameOnly() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.callNextVisitWithSidAndHeaders(
                "default",
                "br-1",
                "sp-1",
                true,
                "SID-34",
                Map.of("Cookie", "x-sid=123; lang=ru"),
                "corr"
        );

        assertEquals("POST", client.lastMethod);
        assertEquals("x-sid=123; lang=ru; sid=SID-34", client.lastHeaders.get("Cookie"));
    }

    @Test
    void postponeWithSidAndHeaders_shouldMergeHeaders() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.postponeCurrentVisitWithSidAndHeaders("default", "br-1", "sp-1", "SID-16", Map.of("X-Op", "p"), "corr");

        assertEquals("PUT", client.lastMethod);
        assertEquals("sid=SID-16", client.lastHeaders.get("Cookie"));
        assertEquals("p", client.lastHeaders.get("X-Op"));
    }

    @Test
    void autoCallWithSidAndHeaders_shouldMergeHeaders() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.startAutoCallWithSidAndHeaders("default", "br-1", "sp-1", "SID-17", Map.of("X-Op", "s"), "corr");
        assertEquals("PUT", client.lastMethod);
        assertEquals("sid=SID-17", client.lastHeaders.get("Cookie"));
        assertEquals("s", client.lastHeaders.get("X-Op"));

        api.cancelAutoCallWithSidAndHeaders("default", "br-1", "sp-1", "SID-18", Map.of("X-Op", "c"), "corr");
        assertEquals("PUT", client.lastMethod);
        assertEquals("sid=SID-18", client.lastHeaders.get("Cookie"));
        assertEquals("c", client.lastHeaders.get("X-Op"));
    }

    @Test
    void managementWithHeadersConvenience_shouldPassHeaders() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.getBranchStateWithHeaders("default", "br-1", Map.of("X-Req", "1"), "corr");
        assertEquals("GET", client.lastMethod);
        assertEquals("1", client.lastHeaders.get("X-Req"));

        api.getBranchesStateWithHeaders("default", "u1", Map.of("X-Req", "2"), "corr");
        assertEquals("GET", client.lastMethod);
        assertEquals("2", client.lastHeaders.get("X-Req"));

        api.getBranchesTinyWithHeaders("default", Map.of("X-Req", "3"), "corr");
        assertEquals("GET", client.lastMethod);
        assertEquals("3", client.lastHeaders.get("X-Req"));
    }


    @Test
    void headerNormalization_shouldTrimAndDropEmptyEntries() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("  X-A  ", "  1  ");
        headers.put("X-Empty", "   ");
        headers.put("   ", "v");
        headers.put(null, "v");
        headers.put("X-Null", null);

        api.getBranchState("default", "br-1", headers, "m1", "c1", "i1");

        assertEquals("GET", client.lastMethod);
        assertEquals("1", client.lastHeaders.get("X-A"));
        assertTrue(!client.lastHeaders.containsKey("X-Empty"));
    }

    @Test
    void callEndpoint_shouldNormalizeHeaders() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("  X-T  ", "  ok  ");
        headers.put("X-Drop", " ");

        api.callEndpoint("default", "GET", "/managementinformation/branches/tiny", null, headers, "m1", "c1", "i1");

        assertEquals("GET", client.lastMethod);
        assertEquals("ok", client.lastHeaders.get("X-T"));
        assertTrue(!client.lastHeaders.containsKey("X-Drop"));
    }

    @Test
    void defaultTargetWithSidAndHeadersConveniences_shouldDelegate() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.enterServicePointModeWithSidAndHeaders("br-1", Map.of("mode", "operator"), "SID-20", Map.of("X-1", "v1"), "corr");
        assertEquals("POST", client.lastMethod);
        assertEquals("sid=SID-20", client.lastHeaders.get("Cookie"));
        assertEquals("v1", client.lastHeaders.get("X-1"));

        api.startAutoCallWithSidAndHeaders("br-1", "sp-1", "SID-21", Map.of("X-2", "v2"), "corr");
        assertEquals("PUT", client.lastMethod);
        assertEquals("sid=SID-21", client.lastHeaders.get("Cookie"));
        assertEquals("v2", client.lastHeaders.get("X-2"));

        api.cancelAutoCallWithSidAndHeaders("br-1", "sp-1", "SID-22", Map.of("X-3", "v3"), "corr");
        assertEquals("PUT", client.lastMethod);
        assertEquals("sid=SID-22", client.lastHeaders.get("Cookie"));
        assertEquals("v3", client.lastHeaders.get("X-3"));
    }

    @Test
    void defaultTargetPrinterWithSidAndHeadersConveniences_shouldDelegate() {
        StubVisitManagerClient client = new StubVisitManagerClient();
        VisitManagerApiImpl api = new VisitManagerApiImpl(client);

        api.createVisitOnPrinterWithServicesAndSidAndHeaders("br-1", "pr-1", java.util.List.of("svc-1"), true, null, "SID-23", Map.of("X-4", "v4"), "corr");
        assertEquals("POST", client.lastMethod);
        assertEquals("sid=SID-23", client.lastHeaders.get("Cookie"));
        assertEquals("v4", client.lastHeaders.get("X-4"));

        api.createVisitOnPrinterWithParametersAndSidAndHeaders("br-1", "pr-1", java.util.List.of("svc-1"), Map.of("p", "v"), true, null, "SID-24", Map.of("X-5", "v5"), "corr");
        assertEquals("POST", client.lastMethod);
        assertEquals("sid=SID-24", client.lastHeaders.get("Cookie"));
        assertEquals("v5", client.lastHeaders.get("X-5"));
    }


    private static final class StubVisitManagerClient extends VisitManagerClient {
        private String lastMethod;
        private String lastPath;
        private String lastCall;
        private Object lastBody;
        private Map<String, String> lastHeaders;
        private Map<String, String> lastParameters;
        private java.util.List<String> lastServiceIds;

        private StubVisitManagerClient() {
            super(null, null, null, null);
        }

        @Override
        public CallResult callRestEndpoint(String method,
                                           String path,
                                           Object body,
                                           Map<String, String> extraHeaders,
                                           String sourceMessageId,
                                           String correlationId,
                                           String idempotencyKey) {
            this.lastCall = "callRestEndpoint";
            this.lastMethod = method;
            this.lastPath = path;
            this.lastBody = body;
            this.lastHeaders = extraHeaders;
            return CallResult.direct(200, null);
        }

        @Override
        public CallResult createVisitRest(String branchId,
                                          String entryPointId,
                                          java.util.List<String> serviceIds,
                                          boolean printTicket,
                                          String segmentationRuleId,
                                          Map<String, String> extraHeaders,
                                          String sourceMessageId,
                                          String correlationId,
                                          String idempotencyKey) {
            this.lastCall = "createVisitRest";
            this.lastServiceIds = serviceIds;
            this.lastHeaders = extraHeaders;
            return CallResult.direct(200, null);
        }

        @Override
        public CallResult createVisitWithParametersRest(String branchId,
                                                        String entryPointId,
                                                        java.util.List<String> serviceIds,
                                                        Map<String, String> parameters,
                                                        boolean printTicket,
                                                        String segmentationRuleId,
                                                        Map<String, String> extraHeaders,
                                                        String sourceMessageId,
                                                        String correlationId,
                                                        String idempotencyKey) {
            this.lastCall = "createVisitWithParametersRest";
            this.lastServiceIds = serviceIds;
            this.lastParameters = parameters;
            return CallResult.direct(200, null);
        }

        @Override
        public CallResult updateVisitParametersRest(String branchId,
                                                    String visitId,
                                                    Map<String, String> parameters,
                                                    Map<String, String> extraHeaders,
                                                    String sourceMessageId,
                                                    String correlationId,
                                                    String idempotencyKey) {
            this.lastCall = "updateVisitParametersRest";
            this.lastParameters = parameters;
            return CallResult.direct(200, null);
        }

        @Override
        public CallResult getServicesCatalog(String branchId) {
            this.lastCall = "getServicesCatalog";
            return CallResult.direct(200, null);
        }
    }
}
