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


    @Test
    void delegatedProfile_shouldFallbackToGenericWithDetails() {
        MedicalClients clients = new MedicalClients(null);

        MedicalModels.MedicalOutcome<MedicalModels.Patient> out = clients.emiasLike().getPatient(
                new MedicalModels.GetPatientRequest(List.of(new MedicalModels.PatientKey("clientId", "C-77")), Map.of()),
                Map.of()
        );

        assertTrue(out.success());
        assertNotNull(out.result());
        assertEquals("EMIAS_LIKE", out.details().get("requestedProfile"));
        assertEquals("FHIR_GENERIC", out.details().get("executionProfile"));
        assertEquals(Boolean.TRUE, out.details().get("fallback"));
    }


    @Test
    void delegatedFallback_shouldStayDeterministicForSameInput() {
        MedicalClients clients = new MedicalClients(null);
        MedicalClient delegated = clients.emiasLike();

        MedicalModels.GetPatientRequest req = new MedicalModels.GetPatientRequest(
                List.of(new MedicalModels.PatientKey("clientId", "C-77")),
                Map.of()
        );

        MedicalModels.MedicalOutcome<MedicalModels.Patient> first = delegated.getPatient(req, Map.of());
        MedicalModels.MedicalOutcome<MedicalModels.Patient> second = delegated.getPatient(req, Map.of());

        assertTrue(first.success());
        assertTrue(second.success());
        assertNotNull(first.result());
        assertNotNull(second.result());
        assertEquals(first.result().patientId(), second.result().patientId());
        assertEquals(first.details().get("requestedProfile"), second.details().get("requestedProfile"));
        assertEquals(first.details().get("executionProfile"), second.details().get("executionProfile"));
        assertEquals(first.details().get("fallback"), second.details().get("fallback"));
    }

}
