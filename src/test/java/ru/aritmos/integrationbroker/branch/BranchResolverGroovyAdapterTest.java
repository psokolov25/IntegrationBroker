package ru.aritmos.integrationbroker.branch;

import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.model.InboundEnvelope;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BranchResolverGroovyAdapterTest {

    @Test
    void resolveOrDefault_shouldReturnDefaultWhenUnresolved() {
        RuntimeConfigStore.BranchResolutionConfig br = RuntimeConfigStore.BranchResolutionConfig.defaultConfig();
        RuntimeConfigStore.RuntimeConfig runtime = new RuntimeConfigStore.RuntimeConfig(
                "test", List.of(), new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.AUTO, 60),
                new RuntimeConfigStore.InboundDlqConfig(true, 10, true),
                new RuntimeConfigStore.KeycloakProxyEnrichmentConfig(false, false, "keycloakProxy", List.of(RuntimeConfigStore.KeycloakProxyFetchMode.USER_ID_HEADER), "x-user-id", "Authorization", "/authorization/users/{userName}", "/authentication/userInfo", true, 60, 5000, true, List.of()),
                new RuntimeConfigStore.MessagingOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50),
                new RuntimeConfigStore.RestOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50, "Idempotency-Key", "409"),
                Map.of(), RuntimeConfigStore.CrmConfig.disabled(), RuntimeConfigStore.MedicalConfig.disabled(), RuntimeConfigStore.AppointmentConfig.disabled(), RuntimeConfigStore.IdentityConfig.defaultConfig(), RuntimeConfigStore.VisionLabsAnalyticsConfig.disabled(), br, RuntimeConfigStore.VisitManagerIntegrationConfig.disabled(), RuntimeConfigStore.DataBusIntegrationConfig.disabled()
        );

        RuntimeConfigStore store = new RuntimeConfigStore(null, null, null, null, false, null) {
            @Override
            public RuntimeConfig getEffective() {
                return runtime;
            }
        };

        BranchResolverGroovyAdapter adapter = new BranchResolverGroovyAdapter(store);
        InboundEnvelope input = new InboundEnvelope(
                InboundEnvelope.Kind.EVENT,
                "identity.requested",
                null,
                Map.of(),
                "m1",
                "c1",
                null,
                null,
                Map.of()
        );

        Map<String, Object> out = adapter.resolveOrDefault(input, "B-DEFAULT");

        assertEquals("B-DEFAULT", out.get("branchId"));
        assertEquals("DEFAULT", out.get("strategy"));
    }
}
