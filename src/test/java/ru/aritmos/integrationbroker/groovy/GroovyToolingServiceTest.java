package ru.aritmos.integrationbroker.groovy;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GroovyToolingServiceTest {

    @Test
    void validate_shouldReturnTrueForCompilableScript() {
        GroovyToolingService service = new GroovyToolingService();

        GroovyToolingService.ValidationResult result = service.validate("return [ok:true]");

        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void validate_shouldReturnFalseForBrokenScript() {
        GroovyToolingService service = new GroovyToolingService();

        GroovyToolingService.ValidationResult result = service.validate("return [ok:true");

        assertFalse(result.valid());
        assertFalse(result.errors().isEmpty());
    }

    @Test
    void emulate_shouldUseMocksAndCollectCalls() {
        GroovyToolingService service = new GroovyToolingService();

        String script = """
                def found = crm.findCustomerByPhone('+7999', meta)
                def created = visit.createVisitRest([branchId:'B1'], meta)
                debug.log('done')
                output.crm = found
                output.visit = created
                return output
                """;

        GroovyToolingService.EmulationResult result = service.emulate(
                script,
                Map.of("payload", Map.of("x", 1)),
                Map.of("correlationId", "c1"),
                Map.of(
                        "crm.findCustomerByPhone", Map.of("success", true, "result", Map.of("crmCustomerId", "CRM-1")),
                        "visit.createVisitRest", Map.of("success", true, "body", Map.of("id", "V-100"))
                )
        );

        assertTrue(result.success());
        assertEquals(2, result.calls().size());
        assertEquals("crm", result.calls().get(0).alias());
        assertEquals("findCustomerByPhone", result.calls().get(0).method());
        assertEquals("V-100", ((Map<?, ?>) ((Map<?, ?>) result.output().get("visit")).get("body")).get("id"));
        assertTrue(result.debugMessages().contains("done"));
    }
}
