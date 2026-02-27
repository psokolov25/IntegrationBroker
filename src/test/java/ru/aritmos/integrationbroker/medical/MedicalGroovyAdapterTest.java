package ru.aritmos.integrationbroker.medical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MedicalGroovyAdapterTest {

    @Test
    void getUpcomingServicesByPatient_shouldBuildRequestAndDelegate() {
        StubMedicalService service = new StubMedicalService();
        MedicalGroovyAdapter adapter = new MedicalGroovyAdapter(service, new ObjectMapper());

        MedicalModels.MedicalOutcome<java.util.List<MedicalModels.UpcomingService>> out =
                adapter.getUpcomingServicesByPatient("P-777", Map.of("channel", "kiosk"));

        assertEquals(true, out.success());
        assertEquals("P-777", service.lastUpcomingRequest.patientId());
        assertEquals("kiosk", service.lastMeta.get("channel"));
    }

    @Test
    void getPatientByKeys_shouldBuildRequestAndDelegate() {
        StubMedicalService service = new StubMedicalService();
        MedicalGroovyAdapter adapter = new MedicalGroovyAdapter(service, new ObjectMapper());

        MedicalModels.MedicalOutcome<MedicalModels.Patient> out = adapter.getPatientByKeys(
                List.of(Map.of("type", "snils", "value", "112-233-445 95")),
                Map.of("channel", "kiosk")
        );

        assertEquals(true, out.success());
        assertEquals("112-233-445 95", service.lastRequest.keys().get(0).value());
        assertEquals("kiosk", service.lastMeta.get("channel"));
    }

    static class StubMedicalService extends MedicalService {
        MedicalModels.GetPatientRequest lastRequest;
        MedicalModels.UpcomingServicesRequest lastUpcomingRequest;
        Map<String, Object> lastMeta;

        StubMedicalService() {
            super(null, null);
        }

        @Override
        public MedicalModels.MedicalOutcome<MedicalModels.Patient> getPatient(MedicalModels.GetPatientRequest request, Map<String, Object> meta) {
            this.lastRequest = request;
            this.lastMeta = meta;
            return MedicalModels.MedicalOutcome.ok(new MedicalModels.Patient("P1", "Иванов Иван", null, Map.of(), Map.of()), Map.of());
        }

        @Override
        public MedicalModels.MedicalOutcome<java.util.List<MedicalModels.UpcomingService>> getUpcomingServices(MedicalModels.UpcomingServicesRequest request,
                                                                                                                 Map<String, Object> meta) {
            this.lastUpcomingRequest = request;
            this.lastMeta = meta;
            return MedicalModels.MedicalOutcome.ok(java.util.List.of(new MedicalModels.UpcomingService("CODE", "Name", "dep", "101", null, java.util.Map.of())), java.util.Map.of());
        }
    }
}
