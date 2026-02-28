package ru.aritmos.integrationbroker.appointment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppointmentGroovyAdapterTest {





    @Test
    void getAvailableSlotsSimple_shouldBuildRequestAndDelegate() {
        StubAppointmentService service = new StubAppointmentService();
        AppointmentGroovyAdapter adapter = new AppointmentGroovyAdapter(service, new ObjectMapper());

        AppointmentModels.AppointmentOutcome<List<AppointmentModels.Slot>> out =
                adapter.getAvailableSlotsSimple("SERV-2", "LOC-1", Instant.parse("2026-01-01T10:00:00Z"), Instant.parse("2026-01-01T12:00:00Z"), Map.of("branchId", "B2"), Map.of("channel", "terminal"));

        assertEquals(true, out.success());
        assertEquals("SERV-2", service.lastSlotsRequest.serviceCode());
        assertEquals("LOC-1", service.lastSlotsRequest.locationId());
        assertEquals("terminal", service.lastMeta.get("channel"));
    }

    @Test
    void bookSlotSimple_shouldBuildRequestAndDelegate() {
        StubAppointmentService service = new StubAppointmentService();
        AppointmentGroovyAdapter adapter = new AppointmentGroovyAdapter(service, new ObjectMapper());

        AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> out =
                adapter.bookSlotSimple("SL-1", "SERV-1", List.of(Map.of("type", "clientId", "value", "C1")), Map.of("branchId", "B1"), Map.of("channel", "desk"));

        assertEquals(true, out.success());
        assertEquals("SL-1", service.lastBookRequest.slotId());
        assertEquals("SERV-1", service.lastBookRequest.serviceCode());
        assertEquals("desk", service.lastMeta.get("channel"));
    }

    @Test
    void cancelAppointmentSimple_shouldBuildRequestAndDelegate() {
        StubAppointmentService service = new StubAppointmentService();
        AppointmentGroovyAdapter adapter = new AppointmentGroovyAdapter(service, new ObjectMapper());

        AppointmentModels.AppointmentOutcome<Boolean> out =
                adapter.cancelAppointmentSimple("A-10", "client request", Map.of("branchId", "B10"), Map.of("channel", "mobile"));

        assertEquals(true, out.success());
        assertEquals("A-10", service.lastCancelRequest.appointmentId());
        assertEquals("client request", service.lastCancelRequest.reason());
        assertEquals("mobile", service.lastMeta.get("channel"));
    }


    @Test
    void getNearestAppointmentSimple_shouldBuildClientIdAndBranchContext() {
        StubAppointmentService service = new StubAppointmentService();
        AppointmentGroovyAdapter adapter = new AppointmentGroovyAdapter(service, new ObjectMapper());

        AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> out =
                adapter.getNearestAppointmentSimple("CLIENT-101", "B101", Map.of("channel", "desk"));

        assertEquals(true, out.success());
        assertEquals("clientId", service.lastRequest.keys().get(0).type());
        assertEquals("CLIENT-101", service.lastRequest.keys().get(0).value());
        assertEquals("B101", service.lastRequest.context().get("branchId"));
    }

    @Test
    void getNearestAppointmentByClientId_shouldBuildClientIdKey() {
        StubAppointmentService service = new StubAppointmentService();
        AppointmentGroovyAdapter adapter = new AppointmentGroovyAdapter(service, new ObjectMapper());

        AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> out =
                adapter.getNearestAppointmentByClientId("CLIENT-77", Map.of("channel", "mobile"));

        assertEquals(true, out.success());
        assertEquals("clientId", service.lastRequest.keys().get(0).type());
        assertEquals("CLIENT-77", service.lastRequest.keys().get(0).value());
        assertEquals("mobile", service.lastMeta.get("channel"));
    }
    @Test
    void buildQueuePlanSimple_shouldBuildRequestAndDelegate() {
        StubAppointmentService service = new StubAppointmentService();
        AppointmentGroovyAdapter adapter = new AppointmentGroovyAdapter(service, new ObjectMapper());

        AppointmentModels.AppointmentOutcome<AppointmentModels.QueuePlan> out =
                adapter.buildQueuePlanSimple("A-9", List.of(Map.of("type", "clientId", "value", "C9")), Map.of("branchId", "B9"), Map.of("channel", "kiosk"));

        assertEquals(true, out.success());
        assertEquals("A-9", service.lastPlanRequest.appointmentId());
        assertEquals("B9", service.lastPlanRequest.context().get("branchId"));
        assertEquals("kiosk", service.lastMeta.get("channel"));
    }



    @Test
    void getAppointmentsByClientIdAndPeriod_shouldBuildRequestAndDelegate() {
        StubAppointmentService service = new StubAppointmentService();
        AppointmentGroovyAdapter adapter = new AppointmentGroovyAdapter(service, new ObjectMapper());

        AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> out =
                adapter.getAppointmentsByClientIdAndPeriod(
                        "CLIENT-303",
                        Instant.parse("2026-01-01T10:00:00Z"),
                        Instant.parse("2026-01-01T12:00:00Z"),
                        Map.of("branchId", "B303"),
                        Map.of("channel", "desk")
                );

        assertEquals(true, out.success());
        assertEquals("CLIENT-303", service.lastAppointmentsRequest.keys().get(0).value());
        assertEquals(Instant.parse("2026-01-01T10:00:00Z"), service.lastAppointmentsRequest.from());
        assertEquals(Instant.parse("2026-01-01T12:00:00Z"), service.lastAppointmentsRequest.to());
    }

    @Test
    void getAppointmentsByClientId_shouldBuildRequestAndDelegate() {
        StubAppointmentService service = new StubAppointmentService();
        AppointmentGroovyAdapter adapter = new AppointmentGroovyAdapter(service, new ObjectMapper());

        AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> out =
                adapter.getAppointmentsByClientId("CLIENT-202", Map.of("branchId", "B202"), Map.of("channel", "mobile"));

        assertEquals(true, out.success());
        assertEquals("clientId", service.lastAppointmentsRequest.keys().get(0).type());
        assertEquals("CLIENT-202", service.lastAppointmentsRequest.keys().get(0).value());
        assertEquals("mobile", service.lastMeta.get("channel"));
    }

    @Test
    void getAppointmentsByKeys_shouldBuildRequestAndDelegate() {
        StubAppointmentService service = new StubAppointmentService();
        AppointmentGroovyAdapter adapter = new AppointmentGroovyAdapter(service, new ObjectMapper());

        AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> out =
                adapter.getAppointmentsByKeys(List.of(Map.of("type", "phone", "value", "+7999")), Map.of("channel", "kiosk"));

        assertEquals(true, out.success());
        assertEquals("+7999", service.lastAppointmentsRequest.keys().get(0).value());
        assertEquals("kiosk", service.lastMeta.get("channel"));
    }

    @Test
    void getNearestAppointmentByKeys_shouldBuildRequestAndDelegate() {
        StubAppointmentService service = new StubAppointmentService();
        AppointmentGroovyAdapter adapter = new AppointmentGroovyAdapter(service, new ObjectMapper());

        AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> out =
                adapter.getNearestAppointmentByKeys(List.of(Map.of("type", "clientId", "value", "C1")), Map.of("branchId", "B1"));

        assertEquals(true, out.success());
        assertEquals("C1", service.lastRequest.keys().get(0).value());
        assertEquals("B1", service.lastMeta.get("branchId"));
    }

    static class StubAppointmentService extends AppointmentService {
        AppointmentModels.GetNearestAppointmentRequest lastRequest;
        AppointmentModels.GetAppointmentsRequest lastAppointmentsRequest;
        AppointmentModels.BuildQueuePlanRequest lastPlanRequest;
        AppointmentModels.BookSlotRequest lastBookRequest;
        AppointmentModels.CancelAppointmentRequest lastCancelRequest;
        AppointmentModels.GetAvailableSlotsRequest lastSlotsRequest;
        Map<String, Object> lastMeta;

        StubAppointmentService() {
            super(null, null);
        }



        @Override
        public AppointmentModels.AppointmentOutcome<List<AppointmentModels.Slot>> getAvailableSlots(AppointmentModels.GetAvailableSlotsRequest request,
                                                                                                      Map<String, Object> meta) {
            this.lastSlotsRequest = request;
            this.lastMeta = meta;
            return AppointmentModels.AppointmentOutcome.ok(List.of(new AppointmentModels.Slot(
                    "SL-2",
                    java.time.Instant.parse("2026-01-01T11:00:00Z"),
                    java.time.Instant.parse("2026-01-01T11:15:00Z"),
                    request.serviceCode(),
                    Map.of("locationId", request.locationId())
            )));
        }

        @Override
        public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> bookSlot(AppointmentModels.BookSlotRequest request,
                                                                                              Map<String, Object> meta) {
            this.lastBookRequest = request;
            this.lastMeta = meta;
            return AppointmentModels.AppointmentOutcome.ok(new AppointmentModels.Appointment(
                    "B1", Instant.now(), null, request.serviceCode(), null, null, "CONFIRMED", Map.of()
            ));
        }

        @Override
        public AppointmentModels.AppointmentOutcome<Boolean> cancelAppointment(AppointmentModels.CancelAppointmentRequest request,
                                                                                Map<String, Object> meta) {
            this.lastCancelRequest = request;
            this.lastMeta = meta;
            return AppointmentModels.AppointmentOutcome.ok(true);
        }

        @Override
        public AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> getAppointments(AppointmentModels.GetAppointmentsRequest request,
                                                                                                         Map<String, Object> meta) {
            this.lastAppointmentsRequest = request;
            this.lastMeta = meta;
            return AppointmentModels.AppointmentOutcome.ok(List.of(new AppointmentModels.Appointment(
                    "A2", Instant.now(), null, "S2", null, null, "CONFIRMED", Map.of()
            )));
        }

        @Override
        public AppointmentModels.AppointmentOutcome<AppointmentModels.QueuePlan> buildQueuePlan(AppointmentModels.BuildQueuePlanRequest request,
                                                                                                  Map<String, Object> meta) {
            this.lastPlanRequest = request;
            this.lastMeta = meta;
            return AppointmentModels.AppointmentOutcome.ok(new AppointmentModels.QueuePlan(
                    request.appointmentId(), "DEFAULT", List.of(), Map.of()
            ));
        }

        @Override
        public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> getNearestAppointment(AppointmentModels.GetNearestAppointmentRequest request,
                                                                                                         Map<String, Object> meta) {
            this.lastRequest = request;
            this.lastMeta = meta;
            return AppointmentModels.AppointmentOutcome.ok(new AppointmentModels.Appointment(
                    "A1", Instant.now(), null, "S1", null, null, "CONFIRMED", Map.of()
            ));
        }
    }
}
