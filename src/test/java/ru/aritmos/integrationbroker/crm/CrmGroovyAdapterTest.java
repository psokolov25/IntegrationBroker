package ru.aritmos.integrationbroker.crm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrmGroovyAdapterTest {








    @Test
    void createTaskWithNoteSimple_shouldComposeTaskAndNote() {
        StubCrmService service = new StubCrmService();
        CrmGroovyAdapter adapter = new CrmGroovyAdapter(service, new ObjectMapper());

        CrmModels.CrmOutcome<Map<String, Object>> out =
                adapter.createTaskWithNoteSimple("task-a", "crm-3", "op-1", "note-task", Map.of("channel", "desk"));

        assertEquals(true, out.success());
        assertEquals("task", service.lastNoteRequest.entityType());
        assertEquals("note-task", service.lastNoteRequest.text());
        assertEquals("desk", service.lastMeta.get("channel"));
    }

    @Test
    void createTaskWithNoteSimple_shouldReturnErrorWhenTaskCreationFails() {
        StubCrmService service = new StubCrmService();
        service.failTaskCreation = true;
        CrmGroovyAdapter adapter = new CrmGroovyAdapter(service, new ObjectMapper());

        CrmModels.CrmOutcome<Map<String, Object>> out =
                adapter.createTaskWithNoteSimple("task-b", "crm-4", "op-2", "note-task-b", Map.of());

        assertEquals(false, out.success());
        assertEquals("CRM_TASK_FAILED", out.errorCode());
    }

    @Test
    void createServiceCaseWithNoteSimple_shouldComposeCaseAndNote() {
        StubCrmService service = new StubCrmService();
        CrmGroovyAdapter adapter = new CrmGroovyAdapter(service, new ObjectMapper());

        CrmModels.CrmOutcome<Map<String, Object>> out =
                adapter.createServiceCaseWithNoteSimple("case-a", "crm-1", "kiosk", "note-a", Map.of("channel", "kiosk"));

        assertEquals(true, out.success());
        assertEquals("serviceCase", service.lastNoteRequest.entityType());
        assertEquals("note-a", service.lastNoteRequest.text());
        assertEquals("kiosk", service.lastMeta.get("channel"));
    }

    @Test
    void createServiceCaseWithNoteSimple_shouldReturnErrorWhenCaseCreationFails() {
        StubCrmService service = new StubCrmService();
        service.failCaseCreation = true;
        CrmGroovyAdapter adapter = new CrmGroovyAdapter(service, new ObjectMapper());

        CrmModels.CrmOutcome<Map<String, Object>> out =
                adapter.createServiceCaseWithNoteSimple("case-b", "crm-2", "desk", "note-b", Map.of());

        assertEquals(false, out.success());
        assertEquals("CRM_CASE_FAILED", out.errorCode());
    }

    @Test
    void createLeadSimple_shouldBuildRequestAndDelegate() {
        StubCrmService service = new StubCrmService();
        CrmGroovyAdapter adapter = new CrmGroovyAdapter(service, new ObjectMapper());

        CrmModels.CrmOutcome<CrmModels.LeadRef> out =
                adapter.createLeadSimple("lead-title", "crm-77", Map.of("source", "kiosk"), Map.of("channel", "kiosk"));

        assertEquals(true, out.success());
        assertEquals("lead-title", service.lastLeadRequest.title());
        assertEquals("crm-77", service.lastLeadRequest.customerCrmId());
        assertEquals("kiosk", service.lastMeta.get("channel"));
    }

    @Test
    void appendNoteSimple_shouldBuildRequestAndDelegate() {
        StubCrmService service = new StubCrmService();
        CrmGroovyAdapter adapter = new CrmGroovyAdapter(service, new ObjectMapper());

        CrmModels.CrmOutcome<Map<String, Object>> out =
                adapter.appendNoteSimple("customer", "crm-22", "note text", Map.of("channel", "desk"));

        assertEquals(true, out.success());
        assertEquals("customer", service.lastNoteRequest.entityType());
        assertEquals("crm-22", service.lastNoteRequest.entityId());
        assertEquals("note text", service.lastNoteRequest.text());
        assertEquals("desk", service.lastMeta.get("channel"));
    }

    @Test
    void createTaskSimple_shouldBuildRequestAndDelegate() {
        StubCrmService service = new StubCrmService();
        CrmGroovyAdapter adapter = new CrmGroovyAdapter(service, new ObjectMapper());

        CrmModels.CrmOutcome<CrmModels.TaskRef> out =
                adapter.createTaskSimple("call patient", "crm-11", "operator-1", Map.of("channel", "desk"));

        assertEquals(true, out.success());
        assertEquals("call patient", service.lastTaskRequest.title());
        assertEquals("crm-11", service.lastTaskRequest.customerCrmId());
        assertEquals("operator-1", service.lastTaskRequest.assignee());
        assertEquals("desk", service.lastMeta.get("channel"));
    }

    @Test
    void findCustomerByPhone_shouldBuildPhoneKeyAndDelegate() {
        StubCrmService service = new StubCrmService();
        CrmGroovyAdapter adapter = new CrmGroovyAdapter(service, new ObjectMapper());

        CrmModels.CrmOutcome<CrmModels.CustomerCard> out = adapter.findCustomerByPhone(
                "+79995554433",
                Map.of("channel", "mobile")
        );

        assertEquals(true, out.success());
        assertEquals("phone", service.lastRequest.keys().get(0).type());
        assertEquals("+79995554433", service.lastRequest.keys().get(0).value());
        assertEquals("mobile", service.lastMeta.get("channel"));
    }
    @Test
    void createServiceCaseSimple_shouldBuildRequestAndDelegate() {
        StubCrmService service = new StubCrmService();
        CrmGroovyAdapter adapter = new CrmGroovyAdapter(service, new ObjectMapper());

        CrmModels.CrmOutcome<CrmModels.ServiceCaseRef> out =
                adapter.createServiceCaseSimple("case-title", "crm-77", "kiosk", Map.of("branchId", "B1"));

        assertEquals(true, out.success());
        assertEquals("case-title", service.lastCaseRequest.title());
        assertEquals("crm-77", service.lastCaseRequest.customerCrmId());
        assertEquals("kiosk", service.lastCaseRequest.channel());
        assertEquals("B1", service.lastMeta.get("branchId"));
    }

    @Test
    void findCustomerByKeys_shouldBuildRequestAndDelegate() {
        StubCrmService service = new StubCrmService();
        CrmGroovyAdapter adapter = new CrmGroovyAdapter(service, new ObjectMapper());

        CrmModels.CrmOutcome<CrmModels.CustomerCard> out = adapter.findCustomerByKeys(
                List.of(Map.of("type", "phone", "value", "+79990000001")),
                Map.of("branchId", "B1")
        );

        assertEquals(true, out.success());
        assertEquals("+79990000001", service.lastRequest.keys().get(0).value());
        assertEquals("B1", service.lastMeta.get("branchId"));
    }

    static class StubCrmService extends CrmService {
        CrmModels.FindCustomerRequest lastRequest;
        CrmModels.CreateServiceCaseRequest lastCaseRequest;
        CrmModels.CreateTaskRequest lastTaskRequest;
        CrmModels.AppendNoteRequest lastNoteRequest;
        CrmModels.CreateLeadRequest lastLeadRequest;
        boolean failCaseCreation;
        boolean failTaskCreation;
        Map<String, Object> lastMeta;

        StubCrmService() {
            super(null, null);
        }

        @Override
        public CrmModels.CrmOutcome<CrmModels.CustomerCard> findCustomer(CrmModels.FindCustomerRequest request, Map<String, Object> meta) {
            this.lastRequest = request;
            this.lastMeta = meta;
            return CrmModels.CrmOutcome.ok(new CrmModels.CustomerCard("crm-1", "Иванов Иван", "VIP", Map.of(), Map.of()), Map.of());
        }




        @Override
        public CrmModels.CrmOutcome<CrmModels.LeadRef> createLead(CrmModels.CreateLeadRequest request,
                                                                   Map<String, Object> meta) {
            this.lastLeadRequest = request;
            this.lastMeta = meta;
            return CrmModels.CrmOutcome.ok(new CrmModels.LeadRef("lead-1", "NEW", java.time.Instant.now(), Map.of()), Map.of());
        }

        @Override
        public CrmModels.CrmOutcome<Map<String, Object>> appendNote(CrmModels.AppendNoteRequest request,
                                                                     Map<String, Object> meta) {
            this.lastNoteRequest = request;
            this.lastMeta = meta;
            return CrmModels.CrmOutcome.ok(Map.of("status", "OK"), Map.of());
        }

        @Override
        public CrmModels.CrmOutcome<CrmModels.TaskRef> createTask(CrmModels.CreateTaskRequest request,
                                                                   Map<String, Object> meta) {
            this.lastTaskRequest = request;
            this.lastMeta = meta;
            if (failTaskCreation) {
                return CrmModels.CrmOutcome.fail("CRM_TASK_FAILED", "task failed", Map.of());
            }
            return CrmModels.CrmOutcome.ok(new CrmModels.TaskRef("task-1", "OPEN", java.time.Instant.now(), Map.of()), Map.of());
        }
        @Override
        public CrmModels.CrmOutcome<CrmModels.ServiceCaseRef> createServiceCase(CrmModels.CreateServiceCaseRequest request,
                                                                                 Map<String, Object> meta) {
            this.lastCaseRequest = request;
            this.lastMeta = meta;
            if (failCaseCreation) {
                return CrmModels.CrmOutcome.fail("CRM_CASE_FAILED", "case failed", Map.of());
            }
            return CrmModels.CrmOutcome.ok(new CrmModels.ServiceCaseRef("case-1", "OPEN", java.time.Instant.now(), Map.of()), Map.of());
        }
    }
}
