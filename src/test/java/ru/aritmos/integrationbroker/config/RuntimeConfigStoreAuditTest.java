package ru.aritmos.integrationbroker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeConfigStoreAuditTest {

    @Test
    void shouldWriteAuditRecordOnManualUpdate() {
        RuntimeConfigStore store = new RuntimeConfigStore(
                null,
                new ObjectMapper(),
                null,
                "classpath:examples/sample-system-config.json",
                false,
                "/configuration/config/system/integrationbroker"
        );

        RuntimeConfigStore.RuntimeConfig cfg = new RuntimeConfigStore.RuntimeConfig(
                "rev-audit-1",
                List.of(),
                new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.AUTO, 60),
                new RuntimeConfigStore.InboundDlqConfig(true, 10, true),
                new RuntimeConfigStore.KeycloakProxyEnrichmentConfig(false, false, "keycloakProxy", List.of(RuntimeConfigStore.KeycloakProxyFetchMode.USER_ID_HEADER), "x-user-id", "Authorization", "/authorization/users/{userName}", "/authentication/userInfo", true, 60, 5000, true, List.of()),
                new RuntimeConfigStore.MessagingOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50),
                new RuntimeConfigStore.RestOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50, "Idempotency-Key", "409"),
                Map.of(),
                RuntimeConfigStore.CrmConfig.disabled(),
                RuntimeConfigStore.MedicalConfig.disabled(),
                RuntimeConfigStore.AppointmentConfig.disabled(),
                RuntimeConfigStore.IdentityConfig.defaultConfig(),
                RuntimeConfigStore.VisionLabsAnalyticsConfig.disabled(),
                RuntimeConfigStore.BranchResolutionConfig.defaultConfig(),
                RuntimeConfigStore.VisitManagerIntegrationConfig.disabled(),
                RuntimeConfigStore.DataBusIntegrationConfig.disabled()
        );

        RuntimeConfigStore.RuntimeConfig applied = store.applyManual(cfg, "qa.admin", "manual test update");
        assertEquals("rev-audit-1", applied.revision());

        List<RuntimeConfigStore.RuntimeConfigAuditEntry> audit = store.getAuditTrail(50);
        assertFalse(audit.isEmpty(), "TEST_EXPECTED: журнал аудита должен содержать запись");

        RuntimeConfigStore.RuntimeConfigAuditEntry last = audit.get(audit.size() - 1);
        assertEquals("qa.admin", last.actor());
        assertEquals("MANUAL_UPDATE", last.source());
        assertEquals("rev-audit-1", last.toRevision());
        assertTrue(last.changedSections() != null && !last.changedSections().isBlank());
    }
}
