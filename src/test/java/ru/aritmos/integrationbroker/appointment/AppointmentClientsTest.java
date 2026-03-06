package ru.aritmos.integrationbroker.appointment;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentClientsTest {


    @Test
    void customConnectorProfile_shouldReturnNotImplementedStub() {
        AppointmentClients clients = new AppointmentClients(null);
        AppointmentClient custom = clients.customConnector();

        AppointmentModels.GetAppointmentsRequest req = new AppointmentModels.GetAppointmentsRequest(
                List.of(new AppointmentModels.BookingKey("clientId", "C-700", Map.of())),
                null,
                null,
                Map.of()
        );

        AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> out = custom.getAppointments(req, Map.of());

        assertFalse(out.success());
        assertEquals("NOT_IMPLEMENTED", out.code());
        assertTrue(out.message().contains("CUSTOM_CONNECTOR"));
    }

    @Test
    void genericNearestAppointment_shouldBeDeterministicForSameKey() {
        AppointmentClients clients = new AppointmentClients(null);
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
        AppointmentClients clients = new AppointmentClients(null);
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
        AppointmentClients clients = new AppointmentClients(null);
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
        AppointmentClients clients = new AppointmentClients(null);
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
        AppointmentClients clients = new AppointmentClients(null);
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
        AppointmentClients clients = new AppointmentClients(null);
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

}
