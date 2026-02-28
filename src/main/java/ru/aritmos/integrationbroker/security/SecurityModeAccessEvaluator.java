package ru.aritmos.integrationbroker.security;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.IntegrationBrokerSecurityProperties;

import java.util.List;

/**
 * Оценивает inbound-доступ согласно режиму безопасности Integration Broker.
 */
@Singleton
public class SecurityModeAccessEvaluator {

    public enum Decision {
        ALLOW,
        REQUIRE_AUTH
    }

    public Decision evaluate(String path, IntegrationBrokerSecurityProperties props) {
        IntegrationBrokerSecurityProperties effective = props == null
                ? new IntegrationBrokerSecurityProperties()
                : props;
        IntegrationBrokerSecurityProperties.Mode mode = effective.getMode();
        if (mode == null || mode == IntegrationBrokerSecurityProperties.Mode.OPEN) {
            return Decision.ALLOW;
        }

        if (mode == IntegrationBrokerSecurityProperties.Mode.KEYCLOAK_OPTIONAL
                && effective.getAnonymous() != null
                && effective.getAnonymous().isEnabled()
                && matchesAny(path, effective.getAnonymous().getAllowPaths())) {
            return Decision.ALLOW;
        }

        if (matchesAny(path, java.util.List.of("/health", "/health/**", "/info", "/info/**", "/swagger/**", "/swagger-ui/**"))) {
            return Decision.ALLOW;
        }
        return Decision.REQUIRE_AUTH;
    }

    boolean matchesAny(String path, List<String> patterns) {
        if (path == null || path.isBlank() || patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String p : patterns) {
            if (matches(path, p)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String path, String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        String p = pattern.trim();
        if ("/**".equals(p) || "*".equals(p)) {
            return true;
        }
        if (p.endsWith("/**")) {
            String prefix = p.substring(0, p.length() - 3);
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
        if (path.equals(p)) {
            return true;
        }
        return p.endsWith("/") && path.equals(p.substring(0, p.length() - 1));
    }
}
