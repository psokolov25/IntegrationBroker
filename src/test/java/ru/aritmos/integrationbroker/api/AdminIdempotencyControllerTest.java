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
        AdminIdempotencyController.IdempotencyAuditExportResponse response = controller.exportAudit("COMPLETED", null, 10);

        assertEquals("COMPLETED", response.status());
        assertNull(response.skippedReason());
        assertTrue(response.count() >= 1);
        assertTrue(response.items().size() >= 1);
    }




    @Test
    void exportAudit_shouldExposeSkippedReasonFilter() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:admin_idem_export_filter;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
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
            st.execute("INSERT INTO ib_idempotency(idem_key,strategy,status,created_at,updated_at,lock_until,skipped_reason) VALUES ('d-2','AUTO','FAILED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'DUPLICATE')");
            st.execute("INSERT INTO ib_idempotency(idem_key,strategy,status,created_at,updated_at,lock_until,skipped_reason) VALUES ('e-2','AUTO','FAILED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'EXPIRED')");
        }
        IdempotencyService service = new IdempotencyService(ds, new ObjectMapper());
        AdminIdempotencyController controller = new AdminIdempotencyController(service);

        AdminIdempotencyController.IdempotencyAuditExportResponse response = controller.exportAudit("FAILED", " DUPLICATE ", 20);
        assertEquals("DUPLICATE", response.skippedReason());
        assertEquals(1, response.items().size());
    }

    @Test
    void list_shouldSupportSkippedReasonFilter() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:admin_idem_list_filter;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
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
            st.execute("INSERT INTO ib_idempotency(idem_key,strategy,status,created_at,updated_at,lock_until,skipped_reason) VALUES ('d-1','AUTO','FAILED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'DUPLICATE')");
            st.execute("INSERT INTO ib_idempotency(idem_key,strategy,status,created_at,updated_at,lock_until,skipped_reason) VALUES ('l-1','AUTO','FAILED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'LOCKED')");
        }
        IdempotencyService service = new IdempotencyService(ds, new ObjectMapper());
        AdminIdempotencyController controller = new AdminIdempotencyController(service);

        AdminIdempotencyController.IdempotencyListResponse response = controller.list(" FAILED ", " LOCKED ", 20);
        assertEquals(1, response.items().size());
        assertEquals("LOCKED", response.items().get(0).skippedReason());
    }

    @Test
    void purgeExpiredAndBatchUnlock_shouldSupportDryRunAndApply() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:admin_idem_ops;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
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
            st.execute("INSERT INTO ib_idempotency(idem_key,strategy,status,created_at,updated_at,lock_until,skipped_reason) VALUES ('exp-1','AUTO','FAILED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP - INTERVAL '2' DAY,CURRENT_TIMESTAMP - INTERVAL '1' DAY,'EXPIRED')");
            st.execute("INSERT INTO ib_idempotency(idem_key,strategy,status,created_at,updated_at,lock_until) VALUES ('lock-1','AUTO','IN_PROGRESS',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP - INTERVAL '1' HOUR)");
        }
        IdempotencyService service = new IdempotencyService(ds, new ObjectMapper());
        AdminIdempotencyController controller = new AdminIdempotencyController(service);

        AdminIdempotencyController.PurgeExpiredResponse dry = controller.purgeExpired(new AdminIdempotencyController.PurgeExpiredRequest(java.time.Instant.now().toString(), 10, true));
        assertEquals(1, dry.affected());
        AdminIdempotencyController.PurgeExpiredResponse applied = controller.purgeExpired(new AdminIdempotencyController.PurgeExpiredRequest(java.time.Instant.now().toString(), 10, false));
        assertEquals(1, applied.affected());

        AdminIdempotencyController.BatchUnlockResponse unlockDry = controller.unlockBatch(new AdminIdempotencyController.BatchUnlockRequest(10, true, "tester", "dry"));
        assertEquals(1, unlockDry.selected());
        assertEquals(0, unlockDry.unlocked());
        AdminIdempotencyController.BatchUnlockResponse unlockApplied = controller.unlockBatch(new AdminIdempotencyController.BatchUnlockRequest(10, false, "tester", "apply"));
        assertEquals(1, unlockApplied.selected());
        assertEquals(1, unlockApplied.unlocked());
    }



    @Test
    void conflictsBySource_shouldReturnConflictShare() throws Exception {
        IdempotencyService service = newService();
        RuntimeConfigStore.IdempotencyConfig cfg = new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.MESSAGE_ID, 60);

        InboundEnvelope env1 = new InboundEnvelope(InboundEnvelope.Kind.EVENT, "t", new ObjectMapper().readTree("{}"), Map.of(), "m-c1", "corr", null, null, Map.of("source", "crm"));
        IdempotencyService.IdempotencyDecision d1 = service.decide(env1, cfg);
        service.markCompleted(d1.idemKey(), Map.of("ok", true));
        service.decide(env1, cfg); // duplicate

        InboundEnvelope env2 = new InboundEnvelope(InboundEnvelope.Kind.EVENT, "t", new ObjectMapper().readTree("{}"), Map.of(), "m-c2", "corr", null, null, Map.of("source", "crm"));
        service.decide(env2, cfg);
        service.decide(env2, cfg); // locked

        AdminIdempotencyController controller = new AdminIdempotencyController(service);
        AdminIdempotencyController.IdempotencyConflictShareResponse response = controller.conflictsBySource(10);
        assertTrue(response.count() >= 1);
        assertTrue(response.items().stream().anyMatch(i -> "crm".equals(i.source()) && i.conflictRatio() > 0));
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
