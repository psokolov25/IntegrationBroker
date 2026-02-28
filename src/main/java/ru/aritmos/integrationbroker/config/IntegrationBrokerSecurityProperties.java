package ru.aritmos.integrationbroker.config;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.List;

/**
 * Typed-конфигурация security-контура Integration Broker.
 * <p>
 * Используется как единая точка чтения security-настроек из application.yml/ENV
 * для текущей и следующих итераций (OPEN/KEYCLOAK_OPTIONAL/KEYCLOAK_REQUIRED, RBAC, anonymous policy).
 */
@ConfigurationProperties("integrationbroker.security")
public class IntegrationBrokerSecurityProperties {

    public enum Mode {
        OPEN,
        KEYCLOAK_OPTIONAL,
        KEYCLOAK_REQUIRED
    }
    private Mode mode = Mode.OPEN;

    private Anonymous anonymous = new Anonymous();
    private Rbac rbac = new Rbac();

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.OPEN : mode;
    }

    public Anonymous getAnonymous() {
        return anonymous;
    }

    public void setAnonymous(Anonymous anonymous) {
        this.anonymous = anonymous == null ? new Anonymous() : anonymous;
    }

    public Rbac getRbac() {
        return rbac;
    }

    public void setRbac(Rbac rbac) {
        this.rbac = rbac == null ? new Rbac() : rbac;
    }

    @ConfigurationProperties("anonymous")
    public static class Anonymous {
        private boolean enabled = true;
        private List<String> allowPaths = List.of("/api/inbound", "/api/identity/resolve", "/health", "/swagger-ui/**");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getAllowPaths() {
            return allowPaths;
        }

        public void setAllowPaths(List<String> allowPaths) {
            this.allowPaths = (allowPaths == null || allowPaths.isEmpty()) ? List.of() : List.copyOf(allowPaths);
        }
    }

    @ConfigurationProperties("rbac")
    public static class Rbac {
        private String adminRole = "IB_ADMIN";
        private String operatorRole = "IB_OPERATOR";
        private String flowEditorRole = "IB_FLOW_EDITOR";
        private String apiClientRole = "IB_API_CLIENT";
        private String readonlyRole = "IB_READONLY";

        public String getAdminRole() {
            return adminRole;
        }

        public void setAdminRole(String adminRole) {
            this.adminRole = normalizeRole(adminRole, "IB_ADMIN");
        }

        public String getOperatorRole() {
            return operatorRole;
        }

        public void setOperatorRole(String operatorRole) {
            this.operatorRole = normalizeRole(operatorRole, "IB_OPERATOR");
        }

        public String getFlowEditorRole() {
            return flowEditorRole;
        }

        public void setFlowEditorRole(String flowEditorRole) {
            this.flowEditorRole = normalizeRole(flowEditorRole, "IB_FLOW_EDITOR");
        }

        public String getApiClientRole() {
            return apiClientRole;
        }

        public void setApiClientRole(String apiClientRole) {
            this.apiClientRole = normalizeRole(apiClientRole, "IB_API_CLIENT");
        }

        public String getReadonlyRole() {
            return readonlyRole;
        }

        public void setReadonlyRole(String readonlyRole) {
            this.readonlyRole = normalizeRole(readonlyRole, "IB_READONLY");
        }

        private static String normalizeRole(String value, String fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value.trim();
        }
    }
}
