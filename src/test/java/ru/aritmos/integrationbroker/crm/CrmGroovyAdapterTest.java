package ru.aritmos.integrationbroker.crm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrmGroovyAdapterTest {



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
        public CrmModels.CrmOutcome<CrmModels.ServiceCaseRef> createServiceCase(CrmModels.CreateServiceCaseRequest request,
                                                                                 Map<String, Object> meta) {
            this.lastCaseRequest = request;
            this.lastMeta = meta;
            return CrmModels.CrmOutcome.ok(new CrmModels.ServiceCaseRef("case-1", "OPEN", java.time.Instant.now(), Map.of()), Map.of());
        }
    }
}
