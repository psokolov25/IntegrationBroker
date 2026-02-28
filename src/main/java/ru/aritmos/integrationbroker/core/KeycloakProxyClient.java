package ru.aritmos.integrationbroker.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Минимальный HTTP-клиент для обращения к KeycloakProxy.
 * <p>
 * Реализация специально сделана без внешних библиотек и без сохранения чувствительных данных.
 * Авторизация service-to-service берётся из REST-коннектора {@code restConnectors[connectorId]}.
 */
@Singleton
public class KeycloakProxyClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public KeycloakProxyClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Получить профиль пользователя по userId (чаще всего это username/login).
     *
     * @param cfg effective-конфигурация
     * @param userId идентификатор пользователя
     * @return ответ как Map (санитизированный)
     */
    public Optional<Map<String, Object>> fetchUserById(RuntimeConfigStore.RuntimeConfig cfg, String userId) {
        RuntimeConfigStore.KeycloakProxyEnrichmentConfig kc = cfg == null ? null : cfg.keycloakProxy();
        if (kc == null) {
            return Optional.empty();
        }

        String path = substitute(kc.userByIdPathTemplate(), "{userName}", userId);
        return doGet(cfg, kc.connectorId(), path, Map.of());
    }

    /**
     * Получить профиль пользователя по Bearer-токену.
     * <p>
     * Важно: токен не должен логироваться и не должен сохраняться ни в БД, ни в кэш «как есть».
     */
    public Optional<Map<String, Object>> fetchUserByToken(RuntimeConfigStore.RuntimeConfig cfg, String bearerToken) {
        RuntimeConfigStore.KeycloakProxyEnrichmentConfig kc = cfg == null ? null : cfg.keycloakProxy();
        if (kc == null) {
            return Optional.empty();
        }
        if (bearerToken == null || bearerToken.isBlank()) {
            return Optional.empty();
        }

        // Передаём токен только в рамках конкретного сетевого вызова.
        Map<String, String> extra = Map.of("Authorization", "Bearer " + bearerToken);
        return doGet(cfg, kc.connectorId(), kc.userByTokenPath(), extra);
    }

    private Optional<Map<String, Object>> doGet(RuntimeConfigStore.RuntimeConfig cfg,
                                                String connectorId,
                                                String path,
                                                Map<String, String> extraHeaders) {
        RuntimeConfigStore.RestConnectorConfig connector = (cfg == null || cfg.restConnectors() == null)
                ? null
                : cfg.restConnectors().get(connectorId);
        if (connector == null || connector.baseUrl() == null || connector.baseUrl().isBlank()) {
            return Optional.empty();
        }

        String baseUrl = connector.baseUrl().endsWith("/")
                ? connector.baseUrl().substring(0, connector.baseUrl().length() - 1)
                : connector.baseUrl();

        String p = (path == null) ? "" : path;
        String normalizedPath = p.startsWith("/") ? p : ("/" + p);

        URI uri = URI.create(baseUrl + normalizedPath);
        HttpRequest.Builder b = HttpRequest.newBuilder().GET().uri(uri).timeout(Duration.ofSeconds(5));

        // Service-to-service авторизация (из коннектора).
        applyConnectorAuthHeaders(connector.auth(), b);

        // Пользовательский токен (если выбран режим по token).
        if (extraHeaders != null) {
            for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && !e.getValue().isBlank()) {
                    b.header(e.getKey(), e.getValue());
                }
            }
        }

        HttpRequest req = b.build();
        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                JsonNode node = objectMapper.readTree(resp.body());
                Map<String, Object> map = objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {
                });
                return Optional.of(map);
            }
            // Для enrichment достаточно «пустого результата». Ошибки решаются уровнем критичности.
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
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
                // Для этого клиента OAuth2 client_credentials будет добавлен отдельной итерацией.
            }
        }
    }

    private String substitute(String template, String placeholder, String value) {
        if (template == null) {
            return "";
        }
        if (value == null) {
            return template;
        }
        String enc = URLEncoder.encode(value, StandardCharsets.UTF_8);
        return template.replace(placeholder, enc).replace("{userId}", enc);
    }
}
