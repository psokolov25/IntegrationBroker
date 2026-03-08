package ru.aritmos.integrationbroker.api;

import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.InboundDlqService;
import ru.aritmos.integrationbroker.core.MessagingOutboxService;
import ru.aritmos.integrationbroker.core.RestOutboxService;
import ru.aritmos.integrationbroker.templates.IntegrationTemplateService;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkbenchAdminControllerTest {

    @Test
    void fallbackActivations_shouldReturnWidgetRows() {
        RuntimeConfigStore.RuntimeConfig cfg = new RuntimeConfigStore.RuntimeConfig(
                "rev-wb",
                List.of(),
                new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.AUTO, 60),
                new RuntimeConfigStore.InboundDlqConfig(true, 10, true),
                new RuntimeConfigStore.KeycloakProxyEnrichmentConfig(false, false, "keycloakProxy", List.of(RuntimeConfigStore.KeycloakProxyFetchMode.USER_ID_HEADER), "x-user-id", "Authorization", "/authorization/users/{userName}", "/authentication/userInfo", true, 60, 5000, true, List.of()),
                new RuntimeConfigStore.MessagingOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50),
                new RuntimeConfigStore.RestOutboxConfig(true, "ON_FAILURE", 10, 10, 120, 50, "Idempotency-Key", "409"),
                Map.of(),
                new RuntimeConfigStore.CrmConfig(true, RuntimeConfigStore.CrmProfile.BITRIX24, "crm", Map.of()),
                new RuntimeConfigStore.MedicalConfig(true, RuntimeConfigStore.MedicalProfile.EMIAS_LIKE, "med", Map.of()),
                new RuntimeConfigStore.AppointmentConfig(true, RuntimeConfigStore.AppointmentProfile.YCLIENTS_LIKE, "app", Map.of()),
                RuntimeConfigStore.IdentityConfig.defaultConfig(),
                RuntimeConfigStore.VisionLabsAnalyticsConfig.disabled(),
                RuntimeConfigStore.BranchResolutionConfig.defaultConfig(),
                RuntimeConfigStore.VisitManagerIntegrationConfig.disabled(),
                RuntimeConfigStore.DataBusIntegrationConfig.disabled()
        );

        WorkbenchAdminController controller = new WorkbenchAdminController(
                runtimeStore(cfg), dlqStub(), msgStub(), restStub(), templateStub()
        );

        WorkbenchAdminController.FallbackActivationsResponse res = controller.fallbackActivations();
        assertEquals(3, res.items().size());
        assertEquals(3, res.activeFallbacks());
    }

    @Test
    void templateSetImportPreview_shouldReturnDryRunConflicts() {
        IntegrationTemplateService templates = new IntegrationTemplateService(null, null) {
            @Override
            public ImportDryRunResult importDryRun(byte[] archiveBytes, String mergeStrategy, String branchIdHint) {
                return new ImportDryRunResult(true, "ibts", "merge", List.of(), List.of(), List.of("s"), List.of("a"), List.of("c"), Map.of());
            }
        };

        WorkbenchAdminController controller = new WorkbenchAdminController(
                runtimeStore(null), dlqStub(), msgStub(), restStub(), templates
        );

        WorkbenchAdminController.TemplateSetImportPreviewResponse res = controller.templateSetImportPreview(
                new WorkbenchAdminController.TemplateSetImportPreviewRequest(Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8)), "merge", "b1")
        );

        assertTrue(res.ok());
        assertEquals("ibts", res.format());
        assertEquals(List.of("c"), res.conflicts());
    }


    @Test
    void templateSetImportPreview_shouldReturnErrorForInvalidBase64() {
        class CountingTemplateService extends IntegrationTemplateService {
            int calls;
            CountingTemplateService() { super(null, null); }
            @Override
            public ImportDryRunResult importDryRun(byte[] archiveBytes, String mergeStrategy, String branchIdHint) {
                calls++;
                return new ImportDryRunResult(true, "ibts", "merge", List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
            }
        }
        CountingTemplateService templates = new CountingTemplateService();

        WorkbenchAdminController controller = new WorkbenchAdminController(
                runtimeStore(null), dlqStub(), msgStub(), restStub(), templates
        );

        WorkbenchAdminController.TemplateSetImportPreviewResponse res = controller.templateSetImportPreview(
                new WorkbenchAdminController.TemplateSetImportPreviewRequest("%%%not-base64%%%", "merge", "b1")
        );

        assertFalse(res.ok());
        assertEquals(0, templates.calls);
        assertTrue(res.errors().stream().anyMatch(e -> e.contains("valid Base64")));
    }

    @Test
    void incidentsByCorrelation_shouldAggregateSources() {
        InboundDlqService dlq = new InboundDlqService(null, null) {
            @Override
            public List<DlqRecord> list(String status, String type, String source, String branchId, String ignoredReason, String correlationId, int limit) {
                return List.of(new DlqRecord(1, "DEAD", null, "2026-01-01T00:00:00Z", "K", "T", "m", correlationId, "b", "u", "i", 1, 3, null, "E1", "err", null));
            }
        };
        MessagingOutboxService msg = new MessagingOutboxService(null, null, null, null) {
            @Override
            public List<OutboxListItem> listByCorrelation(String correlationId, int limit) {
                return List.of(new OutboxListItem(2, "DEAD", "vm", "dest", 1, 3, null, "2026-01-02T00:00:00Z"));
            }

            @Override
            public OutboxRecord get(long id) {
                return new OutboxRecord(id, "DEAD", "vm", "dest", "k", "{}", "{}", "m", "c", "i", 1, 3, null, "E2", "err2", "2026-01-02T00:00:00Z");
            }
        };
        RestOutboxService rest = new RestOutboxService(null, null, null, null, null) {
            @Override
            public List<RestListItem> listByCorrelation(String correlationId, int limit) {
                return List.of(new RestListItem(3, "DEAD", "POST", "http://x", "vm", "/p", 1, 3, null, "2026-01-03T00:00:00Z"));
            }

            @Override
            public RestRecord get(long id) {
                return new RestRecord(id, "DEAD", "POST", "http://x", "vm", "/p", "{}", "{}", "ik", "m", "c", "i", 1, 3, null, "false", "E3", "err3", 500, "2026-01-03T00:00:00Z");
            }
        };

        WorkbenchAdminController controller = new WorkbenchAdminController(runtimeStore(null), dlq, msg, rest, templateStub());
        WorkbenchAdminController.CorrelationIncidentsResponse res = controller.incidentsByCorrelation("corr-1", 10);

        assertEquals(3, res.items().size());
        assertEquals("REST_OUTBOX", res.items().get(0).source());
    }


    @Test
    void incidentsByCorrelation_shouldReturnEmptyForBlankCorrelation() {
        WorkbenchAdminController controller = new WorkbenchAdminController(runtimeStore(null), dlqStub(), msgStub(), restStub(), templateStub());
        WorkbenchAdminController.CorrelationIncidentsResponse res = controller.incidentsByCorrelation("   ", 10);
        assertTrue(res.items().isEmpty());
    }

    @Test
    void incidentsByCorrelation_shouldTrimCorrelationInResponse() {
        InboundDlqService dlq = new InboundDlqService(null, null) {
            @Override
            public List<DlqRecord> list(String status, String type, String source, String branchId, String ignoredReason, String correlationId, int limit) {
                return List.of(new DlqRecord(7, "DEAD", null, "2026-01-01T00:00:00Z", "K", "T", "m", correlationId, "b", "u", "i", 1, 3, null, "E", "err", null));
            }
        };
        WorkbenchAdminController controller = new WorkbenchAdminController(runtimeStore(null), dlq, msgStub(), restStub(), templateStub());
        WorkbenchAdminController.CorrelationIncidentsResponse res = controller.incidentsByCorrelation(" corr-trim ", 10);
        assertEquals("corr-trim", res.correlationId());
        assertEquals(1, res.items().size());
    }

    @Test
    void runtimeAuditRecent_shouldParseChangedSections() {
        RuntimeConfigStore store = new RuntimeConfigStore(null, null, null, null, false, null) {
            @Override
            public List<RuntimeConfigAuditEntry> getAuditTrail(int limit) {
                return List.of(new RuntimeConfigAuditEntry("2026-01-01T00:00:00Z", "actor", "MANUAL_UPDATE", "r1", "r2", "note", "crm, medical"));
            }
        };

        WorkbenchAdminController controller = new WorkbenchAdminController(store, dlqStub(), msgStub(), restStub(), templateStub());
        WorkbenchAdminController.RuntimeAuditWorkbenchResponse res = controller.runtimeAuditRecent(10);

        assertEquals(1, res.items().size());
        assertEquals(List.of("crm", "medical"), res.items().get(0).changedSections());
    }

    private static RuntimeConfigStore runtimeStore(RuntimeConfigStore.RuntimeConfig cfg) {
        return new RuntimeConfigStore(null, null, null, null, false, null) {
            @Override
            public RuntimeConfig getEffective() {
                return cfg;
            }
        };
    }

    private static InboundDlqService dlqStub() {
        return new InboundDlqService(null, null);
    }

    private static MessagingOutboxService msgStub() {
        return new MessagingOutboxService(null, null, null, null);
    }

    private static RestOutboxService restStub() {
        return new RestOutboxService(null, null, null, null, null);
    }

    private static IntegrationTemplateService templateStub() {
        return new IntegrationTemplateService(null, null);
    }
}
