package ru.aritmos.integrationbroker.api;

import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
