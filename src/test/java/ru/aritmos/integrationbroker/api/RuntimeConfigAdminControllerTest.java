package ru.aritmos.integrationbroker.api;

import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeConfigAdminControllerTest {

    @Test
    void getCurrentShouldMaskConnectorSecrets() {
        RuntimeConfigStore.RestConnectorAuth auth = new RuntimeConfigStore.RestConnectorAuth(
                RuntimeConfigStore.RestConnectorAuthType.OAUTH2_CLIENT_CREDENTIALS,
                "X-Api-Key",
                "api-key-123",
                "bearer-token-123",
                "client-user",
                "basic-pass",
                "https://auth/token",
                "client-id",
                "client-secret-123",
                "scope",
                "aud"
        );
        RuntimeConfigStore.RuntimeConfig runtime = new RuntimeConfigStore.RuntimeConfig(
                "rev-1",
                List.of(),
                new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.AUTO, 60),
                new RuntimeConfigStore.InboundDlqConfig(true, 10, true),
                new RuntimeConfigStore.KeycloakProxyEnrichmentConfig(false, false, "keycloakProxy", List.of(RuntimeConfigStore.KeycloakProxyFetchMode.USER_ID_HEADER), "x-user-id", "Authorization", "/authorization/users/{userName}", "/authentication/userInfo", true, 60, 5000, true, List.of()),
                new RuntimeConfigStore.MessagingOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50),
                new RuntimeConfigStore.RestOutboxConfig(true, "ON_FAILURE", 10, 10, 120, 50, "Idempotency-Key", "409"),
                Map.of("vm", new RuntimeConfigStore.RestConnectorConfig("http://vm", auth, null, null)),
                RuntimeConfigStore.CrmConfig.disabled(),
                RuntimeConfigStore.MedicalConfig.disabled(),
                RuntimeConfigStore.AppointmentConfig.disabled(),
                RuntimeConfigStore.IdentityConfig.defaultConfig(),
                RuntimeConfigStore.VisionLabsAnalyticsConfig.disabled(),
                RuntimeConfigStore.BranchResolutionConfig.defaultConfig(),
                RuntimeConfigStore.VisitManagerIntegrationConfig.disabled(),
                RuntimeConfigStore.DataBusIntegrationConfig.disabled()
        );

        RuntimeConfigStore store = new RuntimeConfigStore(null, null, null, null, false, null) {
            @Override
            public RuntimeConfig getEffective() {
                return runtime;
            }
        };

        RuntimeConfigAdminController controller = new RuntimeConfigAdminController(store);
        RuntimeConfigAdminController.RuntimeConfigResponse response = controller.getCurrent();
        RuntimeConfigStore.RestConnectorAuth sanitized = response.config().restConnectors().get("vm").auth();

        assertEquals("***", sanitized.apiKey());
        assertEquals("***", sanitized.bearerToken());
        assertEquals("***", sanitized.basicPassword());
        assertEquals("***", sanitized.oauth2ClientSecret());
        assertEquals("client-id", sanitized.oauth2ClientId());
    }

    @Test
    void dryRunShouldReportFallbackWarningsForNonGenericProfiles() {
        RuntimeConfigStore.RuntimeConfig runtime = new RuntimeConfigStore.RuntimeConfig(
                "rev-2",
                List.of(),
                new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.AUTO, 60),
                new RuntimeConfigStore.InboundDlqConfig(true, 10, true),
                new RuntimeConfigStore.KeycloakProxyEnrichmentConfig(false, false, "keycloakProxy", List.of(RuntimeConfigStore.KeycloakProxyFetchMode.USER_ID_HEADER), "x-user-id", "Authorization", "/authorization/users/{userName}", "/authentication/userInfo", true, 60, 5000, true, List.of()),
                new RuntimeConfigStore.MessagingOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50),
                new RuntimeConfigStore.RestOutboxConfig(true, "ON_FAILURE", 10, 10, 120, 50, "Idempotency-Key", "409"),
                Map.of(),
                new RuntimeConfigStore.CrmConfig(true, RuntimeConfigStore.CrmProfile.BITRIX24, "crm", Map.of()),
                new RuntimeConfigStore.MedicalConfig(true, RuntimeConfigStore.MedicalProfile.EMIAS_LIKE, "medical", Map.of()),
                new RuntimeConfigStore.AppointmentConfig(true, RuntimeConfigStore.AppointmentProfile.YCLIENTS_LIKE, "appointment", Map.of()),
                RuntimeConfigStore.IdentityConfig.defaultConfig(),
                RuntimeConfigStore.VisionLabsAnalyticsConfig.disabled(),
                RuntimeConfigStore.BranchResolutionConfig.defaultConfig(),
                RuntimeConfigStore.VisitManagerIntegrationConfig.disabled(),
                RuntimeConfigStore.DataBusIntegrationConfig.disabled()
        );

        RuntimeConfigStore store = new RuntimeConfigStore(null, null, null, null, false, null) {
            @Override
            public RuntimeConfig getEffective() {
                return runtime;
            }
        };

        RuntimeConfigAdminController controller = new RuntimeConfigAdminController(store);
        RuntimeConfigAdminController.DryRunResponse response = controller.dryRun(runtime);

        assertTrue(response.warnings().stream().anyMatch(w -> w.contains("CRM profile BITRIX24")));
        assertTrue(response.warnings().stream().anyMatch(w -> w.contains("Medical profile EMIAS_LIKE")));
        assertTrue(response.warnings().stream().anyMatch(w -> w.contains("Appointment profile YCLIENTS_LIKE")));
    }

    @Test
    void connectorPolicySnapshotShouldReturnEffectiveRetryAndCircuitBreaker() {
        RuntimeConfigStore.RuntimeConfig runtime = new RuntimeConfigStore.RuntimeConfig(
                "rev-3",
                List.of(),
                new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.AUTO, 60),
                new RuntimeConfigStore.InboundDlqConfig(true, 10, true),
                new RuntimeConfigStore.KeycloakProxyEnrichmentConfig(false, false, "keycloakProxy", List.of(RuntimeConfigStore.KeycloakProxyFetchMode.USER_ID_HEADER), "x-user-id", "Authorization", "/authorization/users/{userName}", "/authentication/userInfo", true, 60, 5000, true, List.of()),
                new RuntimeConfigStore.MessagingOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50),
                new RuntimeConfigStore.RestOutboxConfig(true, "ON_FAILURE", 7, 3, 90, 50, "Idempotency-Key", "409"),
                Map.of(
                        "vm", new RuntimeConfigStore.RestConnectorConfig("http://vm", null,
                                new RuntimeConfigStore.RetryPolicy(5, 2, 30),
                                new RuntimeConfigStore.CircuitBreakerPolicy(true, 4, 20, 2)),
                        "db", new RuntimeConfigStore.RestConnectorConfig("http://db", null, null, null)
                ),
                RuntimeConfigStore.CrmConfig.disabled(),
                RuntimeConfigStore.MedicalConfig.disabled(),
                RuntimeConfigStore.AppointmentConfig.disabled(),
                RuntimeConfigStore.IdentityConfig.defaultConfig(),
                RuntimeConfigStore.VisionLabsAnalyticsConfig.disabled(),
                RuntimeConfigStore.BranchResolutionConfig.defaultConfig(),
                RuntimeConfigStore.VisitManagerIntegrationConfig.disabled(),
                RuntimeConfigStore.DataBusIntegrationConfig.disabled()
        );

        RuntimeConfigStore store = new RuntimeConfigStore(null, null, null, null, false, null) {
            @Override
            public RuntimeConfig getEffective() {
                return runtime;
            }
        };

        RuntimeConfigAdminController controller = new RuntimeConfigAdminController(store);
        RuntimeConfigAdminController.ConnectorPolicySnapshotResponse response = controller.connectorPolicySnapshot();

        assertEquals(2, response.items().size());
        RuntimeConfigAdminController.ConnectorPolicySnapshotItem vm = response.items().stream().filter(i -> "vm".equals(i.connectorId())).findFirst().orElseThrow();
        assertEquals(5, vm.retryMaxAttempts());
        assertEquals(2, vm.retryBaseDelaySec());
        assertEquals(30, vm.retryMaxDelaySec());
        assertTrue(vm.circuitBreakerEnabled());

        RuntimeConfigAdminController.ConnectorPolicySnapshotItem db = response.items().stream().filter(i -> "db".equals(i.connectorId())).findFirst().orElseThrow();
        assertEquals(7, db.retryMaxAttempts());
        assertEquals(3, db.retryBaseDelaySec());
        assertEquals(90, db.retryMaxDelaySec());
        assertFalse(db.circuitBreakerEnabled());
    }


    @Test
    void connectorPolicyDiffShouldCompareEffectiveAgainstBaseline() {
        RuntimeConfigStore.RuntimeConfig baseline = new RuntimeConfigStore.RuntimeConfig(
                "rev-base",
                List.of(),
                new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.AUTO, 60),
                new RuntimeConfigStore.InboundDlqConfig(true, 10, true),
                new RuntimeConfigStore.KeycloakProxyEnrichmentConfig(false, false, "keycloakProxy", List.of(RuntimeConfigStore.KeycloakProxyFetchMode.USER_ID_HEADER), "x-user-id", "Authorization", "/authorization/users/{userName}", "/authentication/userInfo", true, 60, 5000, true, List.of()),
                new RuntimeConfigStore.MessagingOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50),
                new RuntimeConfigStore.RestOutboxConfig(true, "ON_FAILURE", 7, 3, 90, 50, "Idempotency-Key", "409"),
                Map.of(
                        "vm", new RuntimeConfigStore.RestConnectorConfig(
                                "http://vm-base",
                                null,
                                new RuntimeConfigStore.RetryPolicy(5, 2, 30),
                                new RuntimeConfigStore.CircuitBreakerPolicy(true, 5, 25, 2)
                        ),
                        "crm", new RuntimeConfigStore.RestConnectorConfig(
                                "http://crm-base",
                                null,
                                null,
                                null
                        )
                ),
                RuntimeConfigStore.CrmConfig.disabled(),
                RuntimeConfigStore.MedicalConfig.disabled(),
                RuntimeConfigStore.AppointmentConfig.disabled(),
                RuntimeConfigStore.IdentityConfig.defaultConfig(),
                RuntimeConfigStore.VisionLabsAnalyticsConfig.disabled(),
                RuntimeConfigStore.BranchResolutionConfig.defaultConfig(),
                RuntimeConfigStore.VisitManagerIntegrationConfig.disabled(),
                RuntimeConfigStore.DataBusIntegrationConfig.disabled()
        );

        RuntimeConfigStore.RuntimeConfig effective = new RuntimeConfigStore.RuntimeConfig(
                "rev-eff",
                List.of(),
                new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.AUTO, 60),
                new RuntimeConfigStore.InboundDlqConfig(true, 10, true),
                new RuntimeConfigStore.KeycloakProxyEnrichmentConfig(false, false, "keycloakProxy", List.of(RuntimeConfigStore.KeycloakProxyFetchMode.USER_ID_HEADER), "x-user-id", "Authorization", "/authorization/users/{userName}", "/authentication/userInfo", true, 60, 5000, true, List.of()),
                new RuntimeConfigStore.MessagingOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50),
                new RuntimeConfigStore.RestOutboxConfig(true, "ON_FAILURE", 7, 3, 90, 50, "Idempotency-Key", "409"),
                Map.of(
                        "vm", new RuntimeConfigStore.RestConnectorConfig(
                                "http://vm-eff",
                                null,
                                new RuntimeConfigStore.RetryPolicy(8, 2, 30),
                                new RuntimeConfigStore.CircuitBreakerPolicy(true, 7, 25, 2)
                        ),
                        "db", new RuntimeConfigStore.RestConnectorConfig(
                                "http://db-eff",
                                null,
                                null,
                                new RuntimeConfigStore.CircuitBreakerPolicy(false, 4, 15, 1)
                        )
                ),
                RuntimeConfigStore.CrmConfig.disabled(),
                RuntimeConfigStore.MedicalConfig.disabled(),
                RuntimeConfigStore.AppointmentConfig.disabled(),
                RuntimeConfigStore.IdentityConfig.defaultConfig(),
                RuntimeConfigStore.VisionLabsAnalyticsConfig.disabled(),
                RuntimeConfigStore.BranchResolutionConfig.defaultConfig(),
                RuntimeConfigStore.VisitManagerIntegrationConfig.disabled(),
                RuntimeConfigStore.DataBusIntegrationConfig.disabled()
        );

        RuntimeConfigStore store = new RuntimeConfigStore(null, null, null, null, false, null) {
            @Override
            public RuntimeConfig getEffective() {
                return effective;
            }

            @Override
            public RuntimeConfig getBaseline() {
                return baseline;
            }
        };

        RuntimeConfigAdminController controller = new RuntimeConfigAdminController(store);
        RuntimeConfigAdminController.ConnectorPolicyDiffResponse response = controller.connectorPolicyDiff();

        assertEquals(3, response.items().size());
        RuntimeConfigAdminController.ConnectorPolicyDiffItem vm = response.items().stream().filter(i -> "vm".equals(i.connectorId())).findFirst().orElseThrow();
        assertTrue(vm.changed());
        assertEquals(5, vm.baseline().retryMaxAttempts());
        assertEquals(8, vm.effective().retryMaxAttempts());

        RuntimeConfigAdminController.ConnectorPolicyDiffItem crm = response.items().stream().filter(i -> "crm".equals(i.connectorId())).findFirst().orElseThrow();
        assertFalse(crm.changed());
        assertEquals("http://crm-base", crm.baseline().baseUrl());
        assertEquals(7, crm.effective().retryMaxAttempts());

        RuntimeConfigAdminController.ConnectorPolicyDiffItem db = response.items().stream().filter(i -> "db".equals(i.connectorId())).findFirst().orElseThrow();
        assertTrue(db.changed());
        assertEquals(7, db.baseline().retryMaxAttempts());
        assertEquals("http://db-eff", db.effective().baseUrl());
    }

    @Test
    void exportShouldSupportRedactedAndRawModes() {
        RuntimeConfigStore.RestConnectorAuth auth = new RuntimeConfigStore.RestConnectorAuth(
                RuntimeConfigStore.RestConnectorAuthType.API_KEY_HEADER,
                "X-Api-Key",
                "api-key-raw",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        RuntimeConfigStore.RuntimeConfig runtime = new RuntimeConfigStore.RuntimeConfig(
                "rev-export",
                List.of(),
                new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.AUTO, 60),
                new RuntimeConfigStore.InboundDlqConfig(true, 10, true),
                new RuntimeConfigStore.KeycloakProxyEnrichmentConfig(false, false, "keycloakProxy", List.of(RuntimeConfigStore.KeycloakProxyFetchMode.USER_ID_HEADER), "x-user-id", "Authorization", "/authorization/users/{userName}", "/authentication/userInfo", true, 60, 5000, true, List.of()),
                new RuntimeConfigStore.MessagingOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50),
                new RuntimeConfigStore.RestOutboxConfig(true, "ON_FAILURE", 10, 10, 120, 50, "Idempotency-Key", "409"),
                Map.of("vm", new RuntimeConfigStore.RestConnectorConfig("http://vm", auth, null, null)),
                RuntimeConfigStore.CrmConfig.disabled(),
                RuntimeConfigStore.MedicalConfig.disabled(),
                RuntimeConfigStore.AppointmentConfig.disabled(),
                RuntimeConfigStore.IdentityConfig.defaultConfig(),
                RuntimeConfigStore.VisionLabsAnalyticsConfig.disabled(),
                RuntimeConfigStore.BranchResolutionConfig.defaultConfig(),
                RuntimeConfigStore.VisitManagerIntegrationConfig.disabled(),
                RuntimeConfigStore.DataBusIntegrationConfig.disabled()
        );
        RuntimeConfigStore store = new RuntimeConfigStore(null, null, null, null, false, null) {
            @Override
            public RuntimeConfig getEffective() {
                return runtime;
            }
        };

        RuntimeConfigAdminController controller = new RuntimeConfigAdminController(store);
        RuntimeConfigAdminController.RuntimeConfigExportResponse redacted = controller.export(true);
        RuntimeConfigAdminController.RuntimeConfigExportResponse raw = controller.export(false);

        assertTrue(redacted.redacted());
        assertEquals("***", redacted.config().restConnectors().get("vm").auth().apiKey());
        assertFalse(raw.redacted());
        assertEquals("api-key-raw", raw.config().restConnectors().get("vm").auth().apiKey());
    }



    @Test
    void dryRunShouldReportSubprofileSchemaErrors() {
        RuntimeConfigStore.RuntimeConfig runtime = new RuntimeConfigStore.RuntimeConfig(
                "rev-schema",
                List.of(),
                new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.AUTO, 60),
                new RuntimeConfigStore.InboundDlqConfig(true, 10, true),
                new RuntimeConfigStore.KeycloakProxyEnrichmentConfig(false, false, "keycloakProxy", List.of(RuntimeConfigStore.KeycloakProxyFetchMode.USER_ID_HEADER), "x-user-id", "Authorization", "/authorization/users/{userName}", "/authentication/userInfo", true, 60, 5000, true, List.of()),
                new RuntimeConfigStore.MessagingOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50),
                new RuntimeConfigStore.RestOutboxConfig(true, "ON_FAILURE", 10, 10, 120, 50, "Idempotency-Key", "409"),
                Map.of(),
                RuntimeConfigStore.CrmConfig.disabled(),
                new RuntimeConfigStore.MedicalConfig(true, RuntimeConfigStore.MedicalProfile.FHIR_GENERIC, "", null),
                new RuntimeConfigStore.AppointmentConfig(true, RuntimeConfigStore.AppointmentProfile.CUSTOM_CONNECTOR, "", Map.of()),
                RuntimeConfigStore.IdentityConfig.defaultConfig(),
                RuntimeConfigStore.VisionLabsAnalyticsConfig.disabled(),
                RuntimeConfigStore.BranchResolutionConfig.defaultConfig(),
                RuntimeConfigStore.VisitManagerIntegrationConfig.disabled(),
                RuntimeConfigStore.DataBusIntegrationConfig.disabled()
        );

        RuntimeConfigStore store = new RuntimeConfigStore(null, null, null, null, false, null) {
            @Override
            public RuntimeConfig getEffective() {
                return runtime;
            }
        };

        RuntimeConfigAdminController controller = new RuntimeConfigAdminController(store);
        RuntimeConfigAdminController.DryRunResponse response = controller.dryRun(runtime);

        assertFalse(response.ok());
        assertTrue(response.warnings().stream().anyMatch(w -> w.contains("appointment: для enabled=true требуется непустой connectorId")));
        assertTrue(response.warnings().stream().anyMatch(w -> w.contains("medical: для enabled=true требуется непустой connectorId")));
    }

}
