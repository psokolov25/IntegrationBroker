package ru.aritmos.integrationbroker.appointment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentClientsTest {

    @Test
    void customConnectorProfile_shouldCallVendorAndMapAppointments() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> corr = new AtomicReference<>();
        server.createContext("/api/v1/appointments/search", ex -> {
            corr.set(ex.getRequestHeaders().getFirst("X-Correlation-Id"));
            writeJson(ex, 200, "{\"data\":{\"items\":[{\"id\":\"APT-1\",\"start\":\"2026-03-06T08:30:00Z\",\"end\":\"2026-03-06T09:00:00Z\",\"status\":\"CONFIRMED\",\"cabinet\":\"301\",\"service\":{\"code\":\"CONSULT\"},\"doctor\":{\"name\":\"Иванов\"}}]}}");
        });
        server.start();

        try {
            RuntimeConfigStore.RuntimeConfig cfg = customConfigWithCustomOp(
                    "http://localhost:" + server.getAddress().getPort(),
                    "getAppointments",
                    Map.of("path", "/api/v1/appointments/search", "queryTemplate", Map.of())
            );
            AppointmentClient custom = new AppointmentCustomConnectorClient(() -> cfg, new ObjectMapper(), null);

            AppointmentModels.GetAppointmentsRequest req = new AppointmentModels.GetAppointmentsRequest(
                    List.of(new AppointmentModels.BookingKey("clientId", "C-700", Map.of())),
                    null,
                    null,
                    Map.of("branchId", "BR-1")
            );

            AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> out = custom.getAppointments(req, Map.of("correlationId", "corr-123", "requestId", "req-123"));

            assertTrue(out.success());
            assertEquals(1, out.result().size());
            assertEquals("APT-1", out.result().get(0).appointmentId());
            assertEquals("CONSULT", out.result().get(0).serviceCode());
            assertEquals("corr-123", corr.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void customConnectorProfile_shouldExposeRetryableErrorOn429() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/appointments/search", ex -> writeJson(ex, 429, "{\"error\":\"RATE_LIMIT\"}"));
        server.start();

        try {
            RuntimeConfigStore.RuntimeConfig cfg = customConfigWithCustomOp(
                    "http://localhost:" + server.getAddress().getPort(),
                    "getAppointments",
                    Map.of("path", "/api/v1/appointments/search", "queryTemplate", Map.of())
            );
            AppointmentClient custom = new AppointmentCustomConnectorClient(() -> cfg, new ObjectMapper(), null);

            AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> out = custom.getAppointments(
                    new AppointmentModels.GetAppointmentsRequest(List.of(), null, null, Map.of()),
                    Map.of()
            );

            assertFalse(out.success());
            assertTrue(out.message().contains("ERROR_RETRYABLE"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void customConnectorProfile_shouldApplyDefaultTemplateValueInQuery() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> query = new AtomicReference<>();
        server.createContext("/api/v1/appointments/search", ex -> {
            query.set(ex.getRequestURI().getQuery());
            writeJson(ex, 200, "{\"data\":{\"items\":[]}}");
        });
        server.start();

        try {
            RuntimeConfigStore.RuntimeConfig cfg = customConfigWithCustomOp(
                    "http://localhost:" + server.getAddress().getPort(),
                    "getAppointments",
                    Map.of("path", "/api/v1/appointments/search", "queryTemplate", Map.of("limit", "${context.limit:50}"))
            );
            AppointmentClient custom = new AppointmentCustomConnectorClient(() -> cfg, new ObjectMapper(), null);

            AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> out = custom.getAppointments(
                    new AppointmentModels.GetAppointmentsRequest(List.of(), null, null, Map.of()),
                    Map.of()
            );

            assertTrue(out.success());
            assertEquals("limit=50", query.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void customConnectorProfile_shouldMapAvailableSlots() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/slots", ex -> writeJson(ex, 200,
                "{\"data\":{\"items\":[{\"id\":\"S-1\",\"start\":\"2026-03-07T09:00:00Z\",\"end\":\"2026-03-07T09:30:00Z\",\"service\":{\"code\":\"CONSULT\"}}]}}"));
        server.start();

        try {
            RuntimeConfigStore.RuntimeConfig cfg = customConfigWithCustomOp(
                    "http://localhost:" + server.getAddress().getPort(),
                    "getAvailableSlots",
                    Map.of(
                            "method", "POST",
                            "path", "/api/v1/slots",
                            "requestTemplate", Map.of("service", "${serviceCode}"),
                            "responseMapping", Map.of(
                                    "itemsPath", "$.data.items[*]",
                                    "slotId", "$.id",
                                    "startAt", "$.start",
                                    "endAt", "$.end",
                                    "serviceCode", "$.service.code"
                            )
                    )
            );
            AppointmentClient custom = new AppointmentCustomConnectorClient(() -> cfg, new ObjectMapper(), null);

            AppointmentModels.AppointmentOutcome<List<AppointmentModels.Slot>> out = custom.getAvailableSlots(
                    new AppointmentModels.GetAvailableSlotsRequest("CONSULT", "LOC-1", null, null, Map.of()),
                    Map.of()
            );

            assertTrue(out.success());
            assertEquals(1, out.result().size());
            assertEquals("S-1", out.result().get(0).slotId());
            assertEquals("CONSULT", out.result().get(0).serviceCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void genericNearestAppointment_shouldBeDeterministicForSameKey() {
        AppointmentClients clients = new AppointmentClients(null, new NoopCustomClient());
        AppointmentClient generic = clients.generic();

        AppointmentModels.GetNearestAppointmentRequest req = new AppointmentModels.GetNearestAppointmentRequest(
                List.of(new AppointmentModels.BookingKey("clientId", "C-100", Map.of())),
                Map.of()
        );

        AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> first = generic.getNearestAppointment(req, Map.of());
        AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> second = generic.getNearestAppointment(req, Map.of());

        assertTrue(first.success());
        assertTrue(second.success());
        assertNotNull(first.result());
        assertNotNull(second.result());
        assertEquals(first.result().appointmentId(), second.result().appointmentId());
        assertEquals(first.result().startAt(), second.result().startAt());
        assertEquals(first.result().endAt(), second.result().endAt());
    }

    @Test
    void genericBookSlot_shouldHandleMissingSlotIdWithoutFailure() {
        AppointmentClients clients = new AppointmentClients(null, new NoopCustomClient());
        AppointmentClient generic = clients.generic();

        AppointmentModels.BookSlotRequest req = new AppointmentModels.BookSlotRequest(
                null,
                "CONSULT",
                List.of(new AppointmentModels.BookingKey("clientId", "C-101", Map.of())),
                Map.of()
        );

        AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> out = generic.bookSlot(req, Map.of());

        assertTrue(out.success());
        assertNotNull(out.result());
        assertEquals("generic", out.result().attributes().get("source"));
        assertFalse(out.result().attributes().containsKey("slotId"));
    }

    @Test
    void genericGetAppointments_shouldRespectPeriodFilterAndServiceCodeFromContext() {
        AppointmentClients clients = new AppointmentClients(null, new NoopCustomClient());
        AppointmentClient generic = clients.generic();

        AppointmentModels.GetNearestAppointmentRequest nearestReq = new AppointmentModels.GetNearestAppointmentRequest(
                List.of(new AppointmentModels.BookingKey("clientId", "C-300", Map.of())),
                Map.of("serviceCode", "THERAPY", "branchId", "BR-10")
        );
        AppointmentModels.Appointment nearest = generic.getNearestAppointment(nearestReq, Map.of()).result();

        AppointmentModels.GetAppointmentsRequest listReq = new AppointmentModels.GetAppointmentsRequest(
                List.of(new AppointmentModels.BookingKey("clientId", "C-300", Map.of())),
                nearest.startAt().minusSeconds(1),
                nearest.startAt().plusSeconds(1),
                Map.of("serviceCode", "THERAPY", "branchId", "BR-10")
        );

        AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> out = generic.getAppointments(listReq, Map.of());

        assertTrue(out.success());
        assertEquals(1, out.result().size());
        AppointmentModels.Appointment found = out.result().get(0);
        assertEquals("THERAPY", found.serviceCode());
        assertEquals("BR-10", found.attributes().get("branchId"));
    }

    @Test
    void genericGetAvailableSlots_shouldFilterByRange() {
        AppointmentClients clients = new AppointmentClients(null, new NoopCustomClient());
        AppointmentClient generic = clients.generic();

        AppointmentModels.GetAvailableSlotsRequest baselineReq = new AppointmentModels.GetAvailableSlotsRequest(
                "CONSULT",
                "LOC-1",
                null,
                null,
                Map.of()
        );
        List<AppointmentModels.Slot> baseline = generic.getAvailableSlots(baselineReq, Map.of()).result();
        assertEquals(3, baseline.size());

        Instant from = baseline.get(0).startAt().plusSeconds(1);
        Instant to = baseline.get(1).startAt().plusSeconds(1);

        AppointmentModels.GetAvailableSlotsRequest rangedReq = new AppointmentModels.GetAvailableSlotsRequest(
                "CONSULT",
                "LOC-1",
                from,
                to,
                Map.of()
        );
        AppointmentModels.AppointmentOutcome<List<AppointmentModels.Slot>> ranged = generic.getAvailableSlots(rangedReq, Map.of());

        assertTrue(ranged.success());
        assertEquals(1, ranged.result().size());
        assertEquals("SLOT-002", ranged.result().get(0).slotId());
    }

    @Test
    void genericBuildQueuePlan_shouldExposeNumericStepOrder() {
        AppointmentClients clients = new AppointmentClients(null, new NoopCustomClient());
        AppointmentClient generic = clients.generic();

        AppointmentModels.BuildQueuePlanRequest req = new AppointmentModels.BuildQueuePlanRequest(
                "APPT-42",
                List.of(new AppointmentModels.BookingKey("clientId", "C-500", Map.of())),
                Map.of("branchId", "BR-55", "segment", "VIP")
        );

        AppointmentModels.AppointmentOutcome<AppointmentModels.QueuePlan> out = generic.buildQueuePlan(req, Map.of());

        assertTrue(out.success());
        assertNotNull(out.result());
        assertEquals("VIP", out.result().segment());
        assertEquals(2, out.result().steps().size());
        assertEquals(1, out.result().steps().get(0).attributes().get("order"));
        assertEquals("BR-55", out.result().steps().get(0).attributes().get("branchId"));
        assertEquals(2, out.result().steps().get(1).attributes().get("order"));
    }

    @Test
    void genericBookSlot_shouldExposeBranchMetadataWhenProvided() {
        AppointmentClients clients = new AppointmentClients(null, new NoopCustomClient());
        AppointmentClient generic = clients.generic();

        AppointmentModels.BookSlotRequest req = new AppointmentModels.BookSlotRequest(
                "SLOT-100",
                "CONSULT",
                List.of(new AppointmentModels.BookingKey("clientId", "C-101", Map.of())),
                Map.of("branchId", "BR-42")
        );

        AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> out = generic.bookSlot(req, Map.of());

        assertTrue(out.success());
        assertNotNull(out.result());
        assertEquals("BR-42", out.result().attributes().get("branchId"));
        assertEquals("SLOT-100", out.result().attributes().get("slotId"));
    }

    private static RuntimeConfigStore.RuntimeConfig customConfig(String baseUrl) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RuntimeConfigStore.RuntimeConfig cfg = mapper.readValue(
                AppointmentClientsTest.class.getResourceAsStream("/examples/sample-system-config.json"),
                RuntimeConfigStore.RuntimeConfig.class
        ).normalize();
        Map<String, Object> appointmentCfgJson = castMap(mapper.readValue(
                AppointmentClientsTest.class.getResourceAsStream("/examples/appointment/appointment-custom-client-settings.json"),
                Map.class
        ));

        Map<String, Object> appointmentJson = castMap(appointmentCfgJson.get("appointment"));
        RuntimeConfigStore.AppointmentConfig appointment = new RuntimeConfigStore.AppointmentConfig(
                asBoolean(appointmentJson.get("enabled"), true),
                RuntimeConfigStore.AppointmentProfile.valueOf(String.valueOf(appointmentJson.getOrDefault("profile", "CUSTOM_CONNECTOR"))),
                String.valueOf(appointmentJson.getOrDefault("connectorId", "appointmentGeneric")),
                castMap(appointmentJson.get("settings"))
        );

        Map<String, RuntimeConfigStore.RestConnectorConfig> connectors = new LinkedHashMap<>(cfg.restConnectors());
        RuntimeConfigStore.RestConnectorConfig old = connectors.get("appointmentGeneric");
        connectors.put("appointmentGeneric", new RuntimeConfigStore.RestConnectorConfig(baseUrl, old == null ? null : old.auth(), old == null ? null : old.retryPolicy(), old == null ? null : old.circuitBreaker()));

        return new RuntimeConfigStore.RuntimeConfig(
                cfg.revision(), cfg.flows(), cfg.idempotency(), cfg.inboundDlq(), cfg.keycloakProxy(), cfg.messagingOutbox(), cfg.restOutbox(), connectors,
                cfg.crm(), cfg.medical(), appointment, cfg.identity(), cfg.visionLabsAnalytics(), cfg.branchResolution(), cfg.visitManager(), cfg.dataBus()
        ).normalize();
    }

    private static RuntimeConfigStore.RuntimeConfig customConfigWithCustomOp(String baseUrl, String operationName, Map<String, Object> operationConfig) throws Exception {
        RuntimeConfigStore.RuntimeConfig cfg = customConfig(baseUrl);
        Map<String, Object> settings = new LinkedHashMap<>(cfg.appointment().settings());
        Map<String, Object> customClient = new LinkedHashMap<>(castMap(settings.get("customClient")));
        Map<String, Object> operations = new LinkedHashMap<>(castMap(customClient.get("operations")));
        Map<String, Object> existing = new LinkedHashMap<>(castMap(operations.get(operationName)));
        existing.putAll(operationConfig);
        operations.put(operationName, existing);
        customClient.put("operations", operations);
        settings.put("customClient", customClient);

        RuntimeConfigStore.AppointmentConfig appointment = new RuntimeConfigStore.AppointmentConfig(
                cfg.appointment().enabled(),
                cfg.appointment().profile(),
                cfg.appointment().connectorId(),
                settings
        );
        return new RuntimeConfigStore.RuntimeConfig(
                cfg.revision(), cfg.flows(), cfg.idempotency(), cfg.inboundDlq(), cfg.keycloakProxy(), cfg.messagingOutbox(), cfg.restOutbox(), cfg.restConnectors(),
                cfg.crm(), cfg.medical(), appointment, cfg.identity(), cfg.visionLabsAnalytics(), cfg.branchResolution(), cfg.visitManager(), cfg.dataBus()
        ).normalize();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static boolean asBoolean(Object value, boolean def) {
        if (value == null) {
            return def;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static void writeJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static class NoopCustomClient implements AppointmentClient {
        @Override public AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> getAppointments(AppointmentModels.GetAppointmentsRequest request, Map<String, Object> meta) { return AppointmentModels.AppointmentOutcome.notImplemented("test"); }
        @Override public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> getNearestAppointment(AppointmentModels.GetNearestAppointmentRequest request, Map<String, Object> meta) { return AppointmentModels.AppointmentOutcome.notImplemented("test"); }
        @Override public AppointmentModels.AppointmentOutcome<List<AppointmentModels.Slot>> getAvailableSlots(AppointmentModels.GetAvailableSlotsRequest request, Map<String, Object> meta) { return AppointmentModels.AppointmentOutcome.notImplemented("test"); }
        @Override public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> bookSlot(AppointmentModels.BookSlotRequest request, Map<String, Object> meta) { return AppointmentModels.AppointmentOutcome.notImplemented("test"); }
        @Override public AppointmentModels.AppointmentOutcome<Boolean> cancelAppointment(AppointmentModels.CancelAppointmentRequest request, Map<String, Object> meta) { return AppointmentModels.AppointmentOutcome.notImplemented("test"); }
        @Override public AppointmentModels.AppointmentOutcome<AppointmentModels.QueuePlan> buildQueuePlan(AppointmentModels.BuildQueuePlanRequest request, Map<String, Object> meta) { return AppointmentModels.AppointmentOutcome.notImplemented("test"); }
    }
}
