package ru.aritmos.integrationbroker.appointment;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentClientsTest {

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
}
