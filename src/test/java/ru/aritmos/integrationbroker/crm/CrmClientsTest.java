package ru.aritmos.integrationbroker.crm;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CrmClientsTest {

    @Test
    void genericCreateLead_shouldReturnDeterministicCreatedAt() {
        CrmClients.GenericCrmClient client = new CrmClients.GenericCrmClient();

        CrmModels.CreateLeadRequest req = new CrmModels.CreateLeadRequest("Lead A", "CRM-100", Map.of("source", "test"));

        CrmModels.CrmOutcome<CrmModels.LeadRef> first = client.createLead(null, req, Map.of());
        CrmModels.CrmOutcome<CrmModels.LeadRef> second = client.createLead(null, req, Map.of());

        assertTrue(first.success());
        assertTrue(second.success());
        assertNotNull(first.result());
        assertEquals(first.result().createdAt(), second.result().createdAt());
    }

    @Test
    void genericCreateTask_shouldReturnDeterministicCreatedAt() {
        CrmClients.GenericCrmClient client = new CrmClients.GenericCrmClient();

        CrmModels.CreateTaskRequest req = new CrmModels.CreateTaskRequest(
                "Call customer",
                "desc",
                "operator",
                "CRM-100",
                Map.of("source", "test")
        );

        CrmModels.CrmOutcome<CrmModels.TaskRef> first = client.createTask(null, req, Map.of());
        CrmModels.CrmOutcome<CrmModels.TaskRef> second = client.createTask(null, req, Map.of());

        assertTrue(first.success());
        assertTrue(second.success());
        assertNotNull(first.result());
        assertEquals(first.result().createdAt(), second.result().createdAt());
    }
}
