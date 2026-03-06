package ru.aritmos.integrationbroker.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpResponse;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.IdempotencyService;
import ru.aritmos.integrationbroker.model.InboundEnvelope;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdminIdempotencyControllerTest {

    @Test
    void lookupByExternalMessageId_shouldReturnFoundRecord() throws Exception {
        IdempotencyService service = newService();
        InboundEnvelope env = new InboundEnvelope(
                InboundEnvelope.Kind.EVENT, "test", new ObjectMapper().readTree("{}"), Map.of(),
                "msg-lookup-1", "corr-1", null, null, Map.of()
        );
        IdempotencyService.IdempotencyDecision d = service.decide(env,
                new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.MESSAGE_ID, 60));
        service.markCompleted(d.idemKey(), Map.of("status", "OK"));

        AdminIdempotencyController controller = new AdminIdempotencyController(service);
        HttpResponse<IdempotencyService.IdempotencyRecord> response = controller.lookupByExternalMessageId("msg-lookup-1", "MESSAGE_ID");

        assertEquals(200, response.getStatus().getCode());
        assertNotNull(response.body());
        assertEquals("COMPLETED", response.body().status());
    }

    @Test
    void lookupByExternalMessageId_shouldReturnBadRequestForUnknownStrategy() throws Exception {
        AdminIdempotencyController controller = new AdminIdempotencyController(newService());
        HttpResponse<IdempotencyService.IdempotencyRecord> response = controller.lookupByExternalMessageId("msg", "UNKNOWN");
        assertEquals(400, response.getStatus().getCode());
    }



    @Test
    void exportAudit_shouldReturnItems() throws Exception {
        IdempotencyService service = newService();
        InboundEnvelope env = new InboundEnvelope(
                InboundEnvelope.Kind.EVENT, "test", new ObjectMapper().readTree("{}"), Map.of(),
                "msg-export-1", "corr-1", null, null, Map.of()
        );
        IdempotencyService.IdempotencyDecision d = service.decide(env,
                new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.MESSAGE_ID, 60));
        service.markCompleted(d.idemKey(), Map.of("status", "OK"));

        AdminIdempotencyController controller = new AdminIdempotencyController(service);
        AdminIdempotencyController.IdempotencyAuditExportResponse response = controller.exportAudit("COMPLETED", 10);

        assertEquals("COMPLETED", response.status());
        assertTrue(response.count() >= 1);
        assertTrue(response.items().size() >= 1);
    }
    private static IdempotencyService newService() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:admin_idem;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS ib_idempotency (
                        idem_key VARCHAR(128) PRIMARY KEY,
                        strategy VARCHAR(32) NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
                        result_json TEXT NULL,
                        last_error_code VARCHAR(64) NULL,
                        last_error_message TEXT NULL,
                        skipped_reason VARCHAR(32) NULL
                    )
                    """);
        }
        return new IdempotencyService(ds, new ObjectMapper());
    }
}
