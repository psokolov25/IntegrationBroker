package ru.aritmos.integrationbroker.checks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.OAuth2ClientCredentialsService;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestConnectorsCheckerTest {

    @Test
    void shouldFailWhenOauthTokenCannotBeResolved() throws Exception {
        HttpServer healthServer = HttpServer.create(new InetSocketAddress(0), 0);
        healthServer.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        healthServer.start();
        try {
            String baseUrl = "http://localhost:" + healthServer.getAddress().getPort();
            RuntimeConfigStore.RuntimeConfig cfg = runtimeWithConnector(baseUrl,
                    new RuntimeConfigStore.RestConnectorAuth(
                            RuntimeConfigStore.RestConnectorAuthType.OAUTH2_CLIENT_CREDENTIALS,
                            null, null, null, null, null,
                            "http://localhost:1/oauth/token",
                            "client", "secret", null, null
                    ));

            RestConnectorsChecker checker = new RestConnectorsChecker(runtimeStore(cfg), new OAuth2ClientCredentialsService(new ObjectMapper()));
            StartupChecksConfiguration.RestConnectorsCheckConfig checkCfg = new StartupChecksConfiguration.RestConnectorsCheckConfig();
            checkCfg.setTimeoutMs(800);
            checkCfg.setValidateOauth2TokenEndpoint(true);

            assertThrows(IllegalStateException.class, () -> checker.check(checkCfg));
        } finally {
            healthServer.stop(0);
        }
    }

    @Test
    void shouldPassWhenOauthTokenAndHealthAreAvailable() throws Exception {
        HttpServer tokenServer = HttpServer.create(new InetSocketAddress(0), 0);
        tokenServer.createContext("/oauth/token", exchange -> {
            byte[] body = "{\"access_token\":\"tok-ok\",\"expires_in\":120}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        tokenServer.start();

        HttpServer healthServer = HttpServer.create(new InetSocketAddress(0), 0);
        healthServer.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        healthServer.start();

        try {
            String baseUrl = "http://localhost:" + healthServer.getAddress().getPort();
            String tokenUrl = "http://localhost:" + tokenServer.getAddress().getPort() + "/oauth/token";
            RuntimeConfigStore.RuntimeConfig cfg = runtimeWithConnector(baseUrl,
                    new RuntimeConfigStore.RestConnectorAuth(
                            RuntimeConfigStore.RestConnectorAuthType.OAUTH2_CLIENT_CREDENTIALS,
                            null, null, null, null, null,
                            tokenUrl,
                            "client", "secret", "scope-a", null
                    ));

            RestConnectorsChecker checker = new RestConnectorsChecker(runtimeStore(cfg), new OAuth2ClientCredentialsService(new ObjectMapper()));
            StartupChecksConfiguration.RestConnectorsCheckConfig checkCfg = new StartupChecksConfiguration.RestConnectorsCheckConfig();
            checkCfg.setTimeoutMs(1000);
            checkCfg.setValidateOauth2TokenEndpoint(true);

            assertDoesNotThrow(() -> checker.check(checkCfg));
        } finally {
            healthServer.stop(0);
            tokenServer.stop(0);
        }
    }

    private RuntimeConfigStore runtimeStore(RuntimeConfigStore.RuntimeConfig runtime) {
        return new RuntimeConfigStore(null, null, null, null, false, null) {
            @Override
            public RuntimeConfig getEffective() {
                return runtime;
            }
        };
    }

    private RuntimeConfigStore.RuntimeConfig runtimeWithConnector(String baseUrl, RuntimeConfigStore.RestConnectorAuth auth) {
        return new RuntimeConfigStore.RuntimeConfig(
                "test",
                List.of(),
                new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.AUTO, 60),
                new RuntimeConfigStore.InboundDlqConfig(true, 10, true),
                new RuntimeConfigStore.KeycloakProxyEnrichmentConfig(false, false, "keycloakProxy", List.of(RuntimeConfigStore.KeycloakProxyFetchMode.USER_ID_HEADER), "x-user-id", "Authorization", "/authorization/users/{userName}", "/authentication/userInfo", true, 60, 5000, true, List.of()),
                new RuntimeConfigStore.MessagingOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50),
                new RuntimeConfigStore.RestOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50, "Idempotency-Key", "409"),
                Map.of("conn-1", new RuntimeConfigStore.RestConnectorConfig(baseUrl, auth, null, null)),
                RuntimeConfigStore.CrmConfig.disabled(),
                RuntimeConfigStore.MedicalConfig.disabled(),
                RuntimeConfigStore.AppointmentConfig.disabled(),
                RuntimeConfigStore.IdentityConfig.defaultConfig(),
                RuntimeConfigStore.VisionLabsAnalyticsConfig.disabled(),
                RuntimeConfigStore.BranchResolutionConfig.defaultConfig(),
                RuntimeConfigStore.VisitManagerIntegrationConfig.disabled(),
                RuntimeConfigStore.DataBusIntegrationConfig.disabled()
        );
    }
}
