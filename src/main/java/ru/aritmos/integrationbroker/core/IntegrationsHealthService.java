package ru.aritmos.integrationbroker.core;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Проверка состояния интеграций (VM/DataBus) для Admin UI.
 */
@Singleton
public class IntegrationsHealthService {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final RuntimeConfigStore runtimeConfigStore;
    private final OAuth2ClientCredentialsService oauth2Service;

    public IntegrationsHealthService(RuntimeConfigStore runtimeConfigStore,
                                     OAuth2ClientCredentialsService oauth2Service) {
        this.runtimeConfigStore = runtimeConfigStore;
        this.oauth2Service = oauth2Service;
    }

    public List<IntegrationHealthRow> health() {
        RuntimeConfigStore.RuntimeConfig cfg = runtimeConfigStore.getEffective();
        Map<String, RuntimeConfigStore.RestConnectorConfig> connectors = cfg == null ? Map.of() : cfg.restConnectors();

        String vmConnectorId = (cfg == null || cfg.visitManager() == null || cfg.visitManager().connectorId() == null || cfg.visitManager().connectorId().isBlank())
                ? "visitmanager"
                : cfg.visitManager().connectorId();
        String dbConnectorId = (cfg == null || cfg.dataBus() == null || cfg.dataBus().connectorId() == null || cfg.dataBus().connectorId().isBlank())
                ? "databus"
                : cfg.dataBus().connectorId();

        List<IntegrationHealthRow> out = new ArrayList<>();
        out.add(checkConnector("VisitManager", vmConnectorId, connectors.get(vmConnectorId)));
        out.add(checkConnector("DataBus", dbConnectorId, connectors.get(dbConnectorId)));
        return out;
    }

    private IntegrationHealthRow checkConnector(String system,
                                                String connectorId,
                                                RuntimeConfigStore.RestConnectorConfig connector) {
        if (connector == null || connector.baseUrl() == null || connector.baseUrl().isBlank()) {
            return new IntegrationHealthRow(system, "DEGRADED", 0, "Connector not configured: " + connectorId);
        }

        String baseUrl = connector.baseUrl().endsWith("/")
                ? connector.baseUrl().substring(0, connector.baseUrl().length() - 1)
                : connector.baseUrl();

        URI uri;
        try {
            uri = URI.create(baseUrl + "/health");
        } catch (Exception ex) {
            return new IntegrationHealthRow(system, "DOWN", 0, "Invalid baseUrl");
        }

        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        HttpRequest.Builder builder = HttpRequest.newBuilder().GET().uri(uri).timeout(TIMEOUT);
        applyAuth(connector.auth(), builder);

        long startedAt = System.nanoTime();
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                return new IntegrationHealthRow(system, "UP", latencyMs, "HTTP " + statusCode);
            }
            return new IntegrationHealthRow(system, "DEGRADED", latencyMs, "HTTP " + statusCode);
        } catch (Exception ex) {
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
            return new IntegrationHealthRow(system, "DOWN", latencyMs, ex.getClass().getSimpleName());
        }
    }

    private void applyAuth(RuntimeConfigStore.RestConnectorAuth auth, HttpRequest.Builder request) {
        if (auth == null || auth.type() == null) {
            return;
        }
        switch (auth.type()) {
            case NONE -> {
            }
            case API_KEY_HEADER -> {
                String headerName = (auth.headerName() == null || auth.headerName().isBlank()) ? "X-API-Key" : auth.headerName();
                if (auth.apiKey() != null && !auth.apiKey().isBlank()) {
                    request.header(headerName, auth.apiKey());
                }
            }
            case BEARER -> {
                if (auth.bearerToken() != null && !auth.bearerToken().isBlank()) {
                    request.header("Authorization", "Bearer " + auth.bearerToken());
                }
            }
            case BASIC -> {
                if (auth.basicUsername() != null && auth.basicPassword() != null) {
                    String value = auth.basicUsername() + ":" + auth.basicPassword();
                    request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)));
                }
            }
            case OAUTH2_CLIENT_CREDENTIALS -> {
                String token = oauth2Service == null ? null : oauth2Service.resolveAccessToken(auth);
                if (token != null && !token.isBlank()) {
                    request.header("Authorization", "Bearer " + token);
                }
            }
        }
    }

    @Serdeable
    public record IntegrationHealthRow(String system, String status, long latencyMs, String details) {
    }
}
