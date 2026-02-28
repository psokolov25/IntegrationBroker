package ru.aritmos.integrationbroker.checks;

import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.OAuth2ClientCredentialsService;

import jakarta.inject.Singleton;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Проверка доступности REST-коннекторов из runtime-config.
 * <p>
 * Проверка выполняется вызовом health endpoint для каждого коннектора.
 * Важно: секреты (API-key, bearer, basic) могут быть использованы для вызова, но не логируются.
 */
@Singleton
public class RestConnectorsChecker {

    private final RuntimeConfigStore configStore;
    private final OAuth2ClientCredentialsService oauth2Service;

    public RestConnectorsChecker(RuntimeConfigStore configStore, OAuth2ClientCredentialsService oauth2Service) {
        this.configStore = configStore;
        this.oauth2Service = oauth2Service;
    }

    /**
     * Выполнить проверку REST-коннекторов.
     *
     * @param cfg настройки проверки
     */
    public void check(StartupChecksConfiguration.RestConnectorsCheckConfig cfg) {
        RuntimeConfigStore.RuntimeConfig effective = configStore.getEffective();
        Map<String, RuntimeConfigStore.RestConnectorConfig> connectors = effective == null ? Map.of() : effective.restConnectors();
        if (connectors == null || connectors.isEmpty()) {
            return;
        }

        String healthPath = (cfg.getHealthPath() == null || cfg.getHealthPath().isBlank()) ? "/health" : cfg.getHealthPath();
        String normalizedHealthPath = healthPath.startsWith("/") ? healthPath : ("/" + healthPath);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(200, cfg.getTimeoutMs())))
                .build();

        List<String> failures = new ArrayList<>();

        for (Map.Entry<String, RuntimeConfigStore.RestConnectorConfig> e : connectors.entrySet()) {
            String id = e.getKey();
            RuntimeConfigStore.RestConnectorConfig c = e.getValue();
            if (c == null || c.baseUrl() == null || c.baseUrl().isBlank()) {
                failures.add("restConnectors['" + id + "']: пустой baseUrl");
                continue;
            }

            String baseUrl = c.baseUrl().endsWith("/") ? c.baseUrl().substring(0, c.baseUrl().length() - 1) : c.baseUrl();
            URI uri;
            try {
                uri = URI.create(baseUrl + normalizedHealthPath);
            } catch (Exception ex) {
                failures.add("restConnectors['" + id + "']: некорректный URL: " + safe(baseUrl));
                continue;
            }

            try {
                if (cfg.isValidateOauth2TokenEndpoint()
                        && c.auth() != null
                        && c.auth().type() == RuntimeConfigStore.RestConnectorAuthType.OAUTH2_CLIENT_CREDENTIALS) {
                    String token = oauth2Service == null ? null : oauth2Service.resolveAccessToken(c.auth());
                    if (token == null || token.isBlank()) {
                        failures.add("restConnectors['" + id + "']: не удалось получить OAuth2 access token");
                        continue;
                    }
                }

                HttpRequest.Builder b = HttpRequest.newBuilder()
                        .GET()
                        .uri(uri)
                        .timeout(Duration.ofMillis(Math.max(200, cfg.getTimeoutMs())));

                applyConnectorAuthHeaders(c.auth(), b);

                HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int sc = resp.statusCode();
                if (cfg.getExpectedStatus() != null && !cfg.getExpectedStatus().isEmpty()) {
                    if (!cfg.getExpectedStatus().contains(sc)) {
                        failures.add("restConnectors['" + id + "']: health вернул статус " + sc);
                    }
                } else {
                    if (sc < 200 || sc >= 300) {
                        failures.add("restConnectors['" + id + "']: health вернул статус " + sc);
                    }
                }
            } catch (Exception ex) {
                failures.add("restConnectors['" + id + "']: недоступен (" + ex.getClass().getSimpleName() + ")");
            }
        }

        if (!failures.isEmpty()) {
            throw new IllegalStateException("Проблемы REST-коннекторов: " + String.join("; ", failures));
        }
    }

    private void applyConnectorAuthHeaders(RuntimeConfigStore.RestConnectorAuth auth, HttpRequest.Builder b) {
        if (auth == null || auth.type() == null) {
            return;
        }

        switch (auth.type()) {
            case NONE -> {
                // ничего
            }
            case API_KEY_HEADER -> {
                String hn = (auth.headerName() == null || auth.headerName().isBlank()) ? "X-API-Key" : auth.headerName();
                if (auth.apiKey() != null && !auth.apiKey().isBlank()) {
                    b.header(hn, auth.apiKey());
                }
            }
            case BEARER -> {
                if (auth.bearerToken() != null && !auth.bearerToken().isBlank()) {
                    b.header("Authorization", "Bearer " + auth.bearerToken());
                }
            }
            case BASIC -> {
                if (auth.basicUsername() != null && auth.basicPassword() != null) {
                    String v = auth.basicUsername() + ":" + auth.basicPassword();
                    String encoded = java.util.Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8));
                    b.header("Authorization", "Basic " + encoded);
                }
            }
            case OAUTH2_CLIENT_CREDENTIALS -> {
                String token = oauth2Service == null ? null : oauth2Service.resolveAccessToken(auth);
                if (token != null && !token.isBlank()) {
                    b.header("Authorization", "Bearer " + token);
                }
            }
        }
    }

    private String safe(String s) {
        // В логах и ошибках не должно появляться ничего, кроме самого URL.
        // URL считается допустимым к выводу, т.к. не является секретом.
        return s;
    }
}
