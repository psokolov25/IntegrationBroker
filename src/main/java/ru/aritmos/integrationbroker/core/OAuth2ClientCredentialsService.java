package ru.aritmos.integrationbroker.core;

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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Получение OAuth2 access token по grant type client_credentials с коротким in-memory кэшем.
 */
@Singleton
public class OAuth2ClientCredentialsService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    public OAuth2ClientCredentialsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    public String resolveAccessToken(RuntimeConfigStore.RestConnectorAuth auth) {
        if (auth == null || auth.type() != RuntimeConfigStore.RestConnectorAuthType.OAUTH2_CLIENT_CREDENTIALS) {
            return null;
        }
        String tokenUrl = normalize(auth.oauth2TokenUrl());
        String clientId = normalize(auth.oauth2ClientId());
        String clientSecret = normalize(auth.oauth2ClientSecret());
        if (tokenUrl == null || clientId == null || clientSecret == null) {
            return null;
        }

        String key = tokenUrl + "|" + clientId + "|" + normalize(auth.oauth2Scope()) + "|" + normalize(auth.oauth2Audience());
        CachedToken existing = cache.get(key);
        long now = System.currentTimeMillis();
        if (existing != null && existing.expiresAtEpochMs() - now > 10_000) {
            return existing.accessToken();
        }

        String body = buildBody(auth);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return null;
            }
            JsonNode node = objectMapper.readTree(resp.body());
            String accessToken = normalize(node.path("access_token").asText(null));
            if (accessToken == null) {
                return null;
            }
            long expiresIn = node.path("expires_in").asLong(60);
            long expiresAt = now + Math.max(15, expiresIn) * 1000L;
            cache.put(key, new CachedToken(accessToken, expiresAt));
            return accessToken;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildBody(RuntimeConfigStore.RestConnectorAuth auth) {
        StringBuilder sb = new StringBuilder("grant_type=client_credentials");
        append(sb, "client_id", auth.oauth2ClientId());
        append(sb, "client_secret", auth.oauth2ClientSecret());
        append(sb, "scope", auth.oauth2Scope());
        append(sb, "audience", auth.oauth2Audience());
        return sb.toString();
    }

    private void append(StringBuilder sb, String key, String value) {
        String v = normalize(value);
        if (v == null) {
            return;
        }
        sb.append('&').append(key).append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8));
    }

    private String normalize(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private record CachedToken(String accessToken, long expiresAtEpochMs) {
    }
}
