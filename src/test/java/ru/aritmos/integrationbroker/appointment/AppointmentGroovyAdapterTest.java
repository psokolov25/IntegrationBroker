package ru.aritmos.integrationbroker.appointment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppointmentGroovyAdapterTest {



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
        Map<String, Object> lastMeta;

        StubAppointmentService() {
            super(null, null);
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
