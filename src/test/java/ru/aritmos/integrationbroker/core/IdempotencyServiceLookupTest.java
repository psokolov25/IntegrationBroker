package ru.aritmos.integrationbroker.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.model.InboundEnvelope;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyServiceLookupTest {

    @Test
    void findByExternalMessageId_shouldReturnRecordForMessageIdStrategy() throws Exception {
        IdempotencyService service = newService();
        InboundEnvelope env = new InboundEnvelope(
                InboundEnvelope.Kind.EVENT,
                "visit.created",
                new ObjectMapper().readTree("{}"),
                Map.of(),
                "ext-123",
                "corr-123",
                "BR-1",
                "u-1",
                Map.of()
        );

        IdempotencyService.IdempotencyDecision d = service.decide(env,
                new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.MESSAGE_ID, 60));
        assertEquals(IdempotencyService.Decision.PROCESS, d.decision());
        service.markCompleted(d.idemKey(), Map.of("ok", true));

        IdempotencyService.IdempotencyRecord rec = service.findByExternalMessageId("ext-123", RuntimeConfigStore.IdempotencyStrategy.MESSAGE_ID);
        assertNotNull(rec);
        assertEquals("COMPLETED", rec.status());
    }

    @Test
    void findByExternalMessageId_shouldReturnNullWhenAbsent() throws Exception {
        IdempotencyService service = newService();
        IdempotencyService.IdempotencyRecord rec = service.findByExternalMessageId("missing", RuntimeConfigStore.IdempotencyStrategy.MESSAGE_ID);
        assertNull(rec);
    }

    private static IdempotencyService newService() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:idem_lookup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
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
