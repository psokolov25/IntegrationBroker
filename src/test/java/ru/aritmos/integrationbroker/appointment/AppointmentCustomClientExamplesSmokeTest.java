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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentCustomClientExamplesSmokeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void examples_shouldResolveHappyFlowFromCustomClientFixtures() throws Exception {
        HttpServer server = startServer("/api/v2/appointments/search", 200,
                resourceText("/examples/appointment/appointment-custom-client-response-happy.json"));

        try {
            FixtureRequest fixture = fixture("/examples/appointment/appointment-custom-client-request-happy-envelope.json");
            AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> out = customClient(server).getAppointments(
                    fixture.asAppointmentsRequest(), fixture.meta());
            assertAppointmentOutcomeMatchesFixture(out, fixture);
            assertTrue(out.success());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void examples_shouldResolveRetryableFlowFromCustomClientFixtures() throws Exception {
        HttpServer server = startServer("/api/v2/appointments/search", 429,
                resourceText("/examples/appointment/appointment-custom-client-response-retryable.json"));

        try {
            FixtureRequest fixture = fixture("/examples/appointment/appointment-custom-client-request-rate-limit.json");
            AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> out = customClient(server).getAppointments(
                    fixture.asAppointmentsRequest(), fixture.meta());

            Map<String, Object> expected = fixture.expected();
            assertFalse(out.success());
            assertEquals(expected.get("httpStatus"), out.details().get("httpStatus"));
            assertEquals(expected.get("mappedOutcome"), out.details().get("mappedOutcome"));
            assertEquals(expected.get("retriable"), out.details().get("retriable"));
            assertEquals(fixture.meta().get("correlationId"), out.details().get("correlationId"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void examples_shouldResolveSlotsFixtureWithExtendedSettings() throws Exception {
        HttpServer server = startServer("/api/v2/slots/search", 200,
                resourceText("/examples/appointment/appointment-custom-client-response-slots.json"));

        try {
            FixtureRequest fixture = fixture("/examples/appointment/appointment-custom-client-request-slots.json");
            AppointmentModels.AppointmentOutcome<List<AppointmentModels.Slot>> out = customClient(server).getAvailableSlots(
                    fixture.asSlotsRequest(), fixture.meta());

            Map<String, Object> expected = fixture.expected();
            assertTrue(out.success());
            assertEquals(1, out.result().size());
            assertEquals(expected.get("slotId"), out.result().get(0).slotId());
            assertEquals(expected.get("serviceCode"), out.result().get(0).serviceCode());
            assertEquals(expected.get("httpStatus"), out.details().get("httpStatus"));
        } finally {
            server.stop(0);
        }
    }

    private static void assertAppointmentOutcomeMatchesFixture(AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> out,
                                                               FixtureRequest fixture) {
        Map<String, Object> expected = fixture.expected();
        assertEquals(1, out.result().size());
        assertEquals(expected.get("appointmentId"), out.result().get(0).appointmentId());
        assertEquals(expected.get("serviceCode"), out.result().get(0).serviceCode());
        assertEquals(expected.get("specialistName"), out.result().get(0).specialistName());
        assertEquals(expected.get("httpStatus"), out.details().get("httpStatus"));
    }

    private static AppointmentClient customClient(HttpServer server) {
        return new AppointmentCustomConnectorClient(
                () -> customConfig("http://localhost:" + server.getAddress().getPort()),
                MAPPER,
                null
        );
    }

    private static HttpServer startServer(String path, int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, ex -> writeJson(ex, status, body));
        server.start();
        return server;
    }

    private static FixtureRequest fixture(String resourcePath) {
        try {
            Map<String, Object> json = castMap(MAPPER.readValue(resourceText(resourcePath), Map.class));
            return new FixtureRequest(json);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to parse fixture: " + resourcePath, e);
        }
    }

    private static String resourceText(String resourcePath) {
        try (var in = AppointmentCustomClientExamplesSmokeTest.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Fixture not found: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read fixture: " + resourcePath, e);
        }
    }

    private static RuntimeConfigStore.RuntimeConfig customConfig(String baseUrl) {
        try {
            RuntimeConfigStore.RuntimeConfig cfg = MAPPER.readValue(
                    AppointmentCustomClientExamplesSmokeTest.class.getResourceAsStream("/examples/sample-system-config.json"),
                    RuntimeConfigStore.RuntimeConfig.class
            ).normalize();
            Map<String, Object> appointmentCfgJson = castMap(MAPPER.readValue(
                    AppointmentCustomClientExamplesSmokeTest.class.getResourceAsStream("/examples/appointment/appointment-custom-client-settings-extended.json"),
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
            connectors.put("appointmentGeneric", new RuntimeConfigStore.RestConnectorConfig(baseUrl,
                    old == null ? null : old.auth(),
                    old == null ? null : old.retryPolicy(),
                    old == null ? null : old.circuitBreaker()));

            return new RuntimeConfigStore.RuntimeConfig(
                    cfg.revision(), cfg.flows(), cfg.idempotency(), cfg.inboundDlq(), cfg.keycloakProxy(), cfg.messagingOutbox(), cfg.restOutbox(), connectors,
                    cfg.crm(), cfg.medical(), appointment, cfg.identity(), cfg.visionLabsAnalytics(), cfg.branchResolution(), cfg.visitManager(), cfg.dataBus()
            ).normalize();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load custom client fixtures", e);
        }
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

    private record FixtureRequest(Map<String, Object> root) {

        Map<String, Object> meta() {
            return castMap(root.get("meta"));
        }

        Map<String, Object> expected() {
            return castMap(root.get("expected"));
        }

        AppointmentModels.GetAppointmentsRequest asAppointmentsRequest() {
            Map<String, Object> request = castMap(root.get("request"));
            List<AppointmentModels.BookingKey> keys = castList(request.get("keys")).stream()
                    .map(AppointmentCustomClientExamplesSmokeTest::castMap)
                    .map(k -> new AppointmentModels.BookingKey(
                            String.valueOf(k.getOrDefault("type", "")),
                            String.valueOf(k.getOrDefault("value", "")),
                            castMap(k.get("attrs"))
                    ))
                    .toList();
            return new AppointmentModels.GetAppointmentsRequest(
                    keys,
                    parseInstant(request.get("from")),
                    parseInstant(request.get("to")),
                    castMap(request.get("context"))
            );
        }

        AppointmentModels.GetAvailableSlotsRequest asSlotsRequest() {
            Map<String, Object> request = castMap(root.get("request"));
            return new AppointmentModels.GetAvailableSlotsRequest(
                    asString(request.get("serviceCode")),
                    asString(request.get("locationId")),
                    parseInstant(request.get("from")),
                    parseInstant(request.get("to")),
                    castMap(request.get("context"))
            );
        }

        private Instant parseInstant(Object value) {
            String text = asString(value);
            return text == null ? null : Instant.parse(text);
        }

        private String asString(Object value) {
            if (value == null) {
                return null;
            }
            String s = String.valueOf(value);
            return s.isBlank() ? null : s;
        }

        @SuppressWarnings("unchecked")
        private List<Object> castList(Object value) {
            if (value instanceof List<?> l) {
                return (List<Object>) l;
            }
            return List.of();
        }
    }
}
