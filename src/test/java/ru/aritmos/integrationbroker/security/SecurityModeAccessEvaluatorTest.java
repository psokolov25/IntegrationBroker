package ru.aritmos.integrationbroker.security;

import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.IntegrationBrokerSecurityProperties;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityModeAccessEvaluatorTest {

    private final SecurityModeAccessEvaluator evaluator = new SecurityModeAccessEvaluator();

    @Test
    void shouldAllowEverythingInOpenMode() {
        IntegrationBrokerSecurityProperties props = new IntegrationBrokerSecurityProperties();
        props.setMode(IntegrationBrokerSecurityProperties.Mode.OPEN);

        assertEquals(SecurityModeAccessEvaluator.Decision.ALLOW, evaluator.evaluate("/admin/idempotency", props));
        assertEquals(SecurityModeAccessEvaluator.Decision.ALLOW, evaluator.evaluate("/api/inbound", props));
    }

    @Test
    void shouldAllowConfiguredAnonymousInOptionalMode() {
        IntegrationBrokerSecurityProperties props = new IntegrationBrokerSecurityProperties();
        props.setMode(IntegrationBrokerSecurityProperties.Mode.KEYCLOAK_OPTIONAL);
        props.getAnonymous().setEnabled(true);
        props.getAnonymous().setAllowPaths(List.of("/api/inbound", "/swagger-ui/**"));

        assertEquals(SecurityModeAccessEvaluator.Decision.ALLOW, evaluator.evaluate("/api/inbound", props));
        assertEquals(SecurityModeAccessEvaluator.Decision.ALLOW, evaluator.evaluate("/swagger-ui/index.html", props));
        assertEquals(SecurityModeAccessEvaluator.Decision.REQUIRE_AUTH, evaluator.evaluate("/admin/idempotency", props));
    }

    @Test
    void shouldRequireAuthInRequiredModeForBusinessEndpoints() {
        IntegrationBrokerSecurityProperties props = new IntegrationBrokerSecurityProperties();
        props.setMode(IntegrationBrokerSecurityProperties.Mode.KEYCLOAK_REQUIRED);

        assertEquals(SecurityModeAccessEvaluator.Decision.REQUIRE_AUTH, evaluator.evaluate("/api/inbound", props));
        assertEquals(SecurityModeAccessEvaluator.Decision.REQUIRE_AUTH, evaluator.evaluate("/admin/groovy-tooling/validate", props));
    }

    @Test
    void shouldKeepTechnicalEndpointsOpenInRequiredMode() {
        IntegrationBrokerSecurityProperties props = new IntegrationBrokerSecurityProperties();
        props.setMode(IntegrationBrokerSecurityProperties.Mode.KEYCLOAK_REQUIRED);

        assertEquals(SecurityModeAccessEvaluator.Decision.ALLOW, evaluator.evaluate("/health", props));
        assertEquals(SecurityModeAccessEvaluator.Decision.ALLOW, evaluator.evaluate("/swagger-ui/index.html", props));
    }

    @Test
    void shouldKeepNestedTechnicalEndpointsOpenInRequiredMode() {
        IntegrationBrokerSecurityProperties props = new IntegrationBrokerSecurityProperties();
        props.setMode(IntegrationBrokerSecurityProperties.Mode.KEYCLOAK_REQUIRED);

        assertEquals(SecurityModeAccessEvaluator.Decision.ALLOW, evaluator.evaluate("/health/liveness", props));
        assertEquals(SecurityModeAccessEvaluator.Decision.ALLOW, evaluator.evaluate("/info/readiness", props));
        assertEquals(SecurityModeAccessEvaluator.Decision.ALLOW, evaluator.evaluate("/swagger/index.html", props));
    }

    @Test
    void shouldNotTreatLookalikePathAsTechnicalOpenEndpoint() {
        IntegrationBrokerSecurityProperties props = new IntegrationBrokerSecurityProperties();
        props.setMode(IntegrationBrokerSecurityProperties.Mode.KEYCLOAK_REQUIRED);

        assertEquals(SecurityModeAccessEvaluator.Decision.REQUIRE_AUTH, evaluator.evaluate("/swaggered/internal", props));
    }

}