package ru.aritmos.integrationbroker.medical;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MedicalClientsTest {

    @Test
    void genericBuildRoutingContext_shouldPopulateHintsWithoutNulls() {
        MedicalClients clients = new MedicalClients(null);
        MedicalClient generic = clients.fhirGeneric();

        MedicalModels.MedicalOutcome<MedicalModels.MedicalRoutingContext> out = generic.buildRoutingContext(
                new MedicalModels.BuildRoutingContextRequest(List.of(), null, Map.of()),
                Map.of()
        );

        assertTrue(out.success());
        assertNotNull(out.result());
        assertNotNull(out.result().routingHints());
        assertEquals("MULTI_STAGE_EXAM", out.result().routingHints().get("routeType"));
        assertNotNull(out.result().routingHints().get("recommendedStart"));
        assertEquals("FHIR_GENERIC", out.result().routingHints().get("profile"));
    }

    @Test
    void genericGetPatient_shouldReturnDeterministicPatientIdForSameKey() {
        MedicalClients clients = new MedicalClients(null);
        MedicalClient generic = clients.fhirGeneric();

        MedicalModels.GetPatientRequest req = new MedicalModels.GetPatientRequest(
                List.of(new MedicalModels.PatientKey("clientId", "C-501")),
                Map.of()
        );

        MedicalModels.MedicalOutcome<MedicalModels.Patient> first = generic.getPatient(req, Map.of());
        MedicalModels.MedicalOutcome<MedicalModels.Patient> second = generic.getPatient(req, Map.of());

        assertTrue(first.success());
        assertTrue(second.success());
        assertEquals(first.result().patientId(), second.result().patientId());
    }
    @Test
    void genericUpcomingServices_shouldReturnDeterministicStartAt() {
        MedicalClients clients = new MedicalClients(null);
        MedicalClient generic = clients.fhirGeneric();

        MedicalModels.UpcomingServicesRequest req = new MedicalModels.UpcomingServicesRequest(
                "P-100",
                List.of(new MedicalModels.PatientKey("clientId", "C-501")),
                Map.of()
        );

        MedicalModels.MedicalOutcome<List<MedicalModels.UpcomingService>> first = generic.getUpcomingServices(req, Map.of());
        MedicalModels.MedicalOutcome<List<MedicalModels.UpcomingService>> second = generic.getUpcomingServices(req, Map.of());

        assertTrue(first.success());
        assertTrue(second.success());
        assertNotNull(first.result());
        assertFalse(first.result().isEmpty());
        assertNotNull(first.result().get(0).startAt());
        assertEquals(first.result().get(0).startAt(), second.result().get(0).startAt());
    }

}
