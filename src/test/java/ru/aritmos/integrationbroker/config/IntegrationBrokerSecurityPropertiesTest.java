package ru.aritmos.integrationbroker.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationBrokerSecurityPropertiesTest {

    @Test
    void shouldHaveExpectedDefaults() {
        IntegrationBrokerSecurityProperties props = new IntegrationBrokerSecurityProperties();

        assertEquals(IntegrationBrokerSecurityProperties.Mode.OPEN, props.getMode());
        assertTrue(props.getAnonymous().isEnabled());
        assertTrue(props.getAnonymous().getAllowPaths().contains("/api/inbound"));
        assertEquals("IB_ADMIN", props.getRbac().getAdminRole());
    }

    @Test
    void shouldNormalizeInputValues() {
        IntegrationBrokerSecurityProperties props = new IntegrationBrokerSecurityProperties();

        props.setMode(IntegrationBrokerSecurityProperties.Mode.KEYCLOAK_OPTIONAL);
        props.getAnonymous().setAllowPaths(List.of("/api/inbound", "/health"));
        props.getRbac().setAdminRole("  ROLE_ADMIN ");
        props.getRbac().setFlowEditorRole("  ");

        assertEquals(IntegrationBrokerSecurityProperties.Mode.KEYCLOAK_OPTIONAL, props.getMode());
        assertEquals(2, props.getAnonymous().getAllowPaths().size());
        assertEquals("ROLE_ADMIN", props.getRbac().getAdminRole());
        assertEquals("IB_FLOW_EDITOR", props.getRbac().getFlowEditorRole());
    }
}
