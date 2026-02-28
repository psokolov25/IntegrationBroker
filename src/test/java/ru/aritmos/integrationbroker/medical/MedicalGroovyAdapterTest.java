package ru.aritmos.integrationbroker.medical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MedicalGroovyAdapterTest {





    @Test
    void buildRoutingContextByPatientId_shouldBuildRequestAndDelegate() {
        StubMedicalService service = new StubMedicalService();
        MedicalGroovyAdapter adapter = new MedicalGroovyAdapter(service, new ObjectMapper());

        MedicalModels.MedicalOutcome<MedicalModels.MedicalRoutingContext> out =
                adapter.buildRoutingContextByPatientId("P-222", Map.of("branchId", "B222"), Map.of("channel", "desk"));

        assertEquals(true, out.success());
        assertEquals("P-222", service.lastRoutingRequest.patientId());
        assertEquals("B222", service.lastRoutingRequest.context().get("branchId"));
        assertEquals("desk", service.lastMeta.get("channel"));
    }

    @Test
    void buildRoutingContextSimple_shouldBuildRequestAndDelegate() {
        StubMedicalService service = new StubMedicalService();
        MedicalGroovyAdapter adapter = new MedicalGroovyAdapter(service, new ObjectMapper());

        MedicalModels.MedicalOutcome<MedicalModels.MedicalRoutingContext> out =
                adapter.buildRoutingContextSimple(
                        "P-11",
                        List.of(Map.of("type", "snils", "value", "112-233-445 95")),
                        Map.of("branchId", "B11"),
                        Map.of("channel", "desk")
                );

        assertEquals(true, out.success());
        assertEquals("P-11", service.lastRoutingRequest.patientId());
        assertEquals("B11", service.lastRoutingRequest.context().get("branchId"));
        assertEquals("desk", service.lastMeta.get("channel"));
    }


    @Test
    void getUpcomingServicesByKeys_shouldBuildRequestAndDelegate() {
        StubMedicalService service = new StubMedicalService();
        MedicalGroovyAdapter adapter = new MedicalGroovyAdapter(service, new ObjectMapper());

        MedicalModels.MedicalOutcome<java.util.List<MedicalModels.UpcomingService>> out =
                adapter.getUpcomingServicesByKeys(
                        List.of(Map.of("type", "snils", "value", "112-233-445 95")),
                        Map.of("channel", "terminal")
                );

        assertEquals(true, out.success());
        assertEquals("112-233-445 95", service.lastUpcomingRequest.keys().get(0).value());
        assertEquals("terminal", service.lastMeta.get("channel"));
    }


    @Test
    void getPatientByPatientId_shouldBuildPatientIdKeyAndDelegate() {
        StubMedicalService service = new StubMedicalService();
        MedicalGroovyAdapter adapter = new MedicalGroovyAdapter(service, new ObjectMapper());

        MedicalModels.MedicalOutcome<MedicalModels.Patient> out = adapter.getPatientByPatientId(
                "P-100",
                Map.of("channel", "mobile")
        );

        assertEquals(true, out.success());
        assertEquals("patientId", service.lastRequest.keys().get(0).type());
        assertEquals("P-100", service.lastRequest.keys().get(0).value());
        assertEquals("mobile", service.lastMeta.get("channel"));
    }

    @Test
    void getPatientBySnils_shouldBuildSnilsKeyAndDelegate() {
        StubMedicalService service = new StubMedicalService();
        MedicalGroovyAdapter adapter = new MedicalGroovyAdapter(service, new ObjectMapper());

        MedicalModels.MedicalOutcome<MedicalModels.Patient> out = adapter.getPatientBySnils(
                "112-233-445 95",
                Map.of("channel", "mobile")
        );

        assertEquals(true, out.success());
        assertEquals("snils", service.lastRequest.keys().get(0).type());
        assertEquals("112-233-445 95", service.lastRequest.keys().get(0).value());
        assertEquals("mobile", service.lastMeta.get("channel"));
    }

    @Test
    void getUpcomingServicesByPatientAndBranch_shouldBuildRequestAndDelegate() {
        StubMedicalService service = new StubMedicalService();
        MedicalGroovyAdapter adapter = new MedicalGroovyAdapter(service, new ObjectMapper());

        MedicalModels.MedicalOutcome<java.util.List<MedicalModels.UpcomingService>> out =
                adapter.getUpcomingServicesByPatientAndBranch("P-333", "B333", Map.of("channel", "terminal"));

        assertEquals(true, out.success());
        assertEquals("P-333", service.lastUpcomingRequest.patientId());
        assertEquals("B333", service.lastUpcomingRequest.context().get("branchId"));
        assertEquals("terminal", service.lastMeta.get("channel"));
    }

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
        MedicalModels.BuildRoutingContextRequest lastRoutingRequest;
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
        public MedicalModels.MedicalOutcome<MedicalModels.MedicalRoutingContext> buildRoutingContext(MedicalModels.BuildRoutingContextRequest request,
                                                                                                       Map<String, Object> meta) {
            this.lastRoutingRequest = request;
            this.lastMeta = meta;
            return MedicalModels.MedicalOutcome.ok(new MedicalModels.MedicalRoutingContext(
                    new MedicalModels.Patient("P1", "Иванов Иван", null, Map.of(), Map.of()),
                    List.of(),
                    Map.of("priority", "NORMAL")
            ), Map.of());
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
