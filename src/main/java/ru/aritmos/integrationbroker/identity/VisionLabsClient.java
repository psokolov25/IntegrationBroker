package ru.aritmos.integrationbroker.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import jakarta.inject.Singleton;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Минимальный HTTP-клиент для интеграции с VisionLabs (LUNA PLATFORM / LUNA CARS).
 * <p>
 * Важные свойства:
 * <ul>
 *   <li>использует REST-коннекторы из runtime-config (baseUrl + auth);</li>
 *   <li>не логирует секреты и не возвращает их в исключениях;</li>
 *   <li>предназначен только для синхронных вызовов в рамках идентификации (identity).</li>
 * </ul>
 * <p>
 * Примечание: конкретные endpoint'ы и форматы запроса/ответа зависят от установленного решения VisionLabs.
 * Поэтому пути и JSON-указатели результата задаются конфигурацией провайдера.
 */
@Singleton
public class VisionLabsClient {

    private final ObjectMapper objectMapper;

    public VisionLabsClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Выполнить POST (JSON) к заданному REST-коннектору.
     *
     * @param connector конфигурация коннектора
     * @param path путь (относительный), например "/api/identify"
     * @param body тело запроса (будет сериализовано в JSON)
     * @param timeoutMs таймаут запроса
     * @return пара: httpStatus + JSON body (может быть null, если тело пустое)
     */
    public Response postJson(RuntimeConfigStore.RestConnectorConfig connector,
                             String path,
                             Object body,
                             int timeoutMs) {
        if (connector == null || connector.baseUrl() == null || connector.baseUrl().isBlank()) {
            return new Response(0, null);
        }

        String baseUrl = connector.baseUrl().endsWith("/")
                ? connector.baseUrl().substring(0, connector.baseUrl().length() - 1)
                : connector.baseUrl();
        String p = (path == null || path.isBlank()) ? "/" : (path.startsWith("/") ? path : ("/" + path));

        URI uri;
        try {
            uri = URI.create(baseUrl + p);
        } catch (Exception e) {
            return new Response(0, null);
        }

        String json = "{}";
        try {
            if (body != null) {
                json = objectMapper.writeValueAsString(body);
            }
        } catch (Exception e) {
            // Некорректное тело запроса — считаем как ошибку формирования.
            return new Response(0, null);
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(200, timeoutMs)))
                .build();

        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMillis(Math.max(200, timeoutMs)))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");

        applyConnectorAuthHeaders(connector.auth(), b);

        try {
            HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int sc = resp.statusCode();
            String bodyText = resp.body();
            if (bodyText == null || bodyText.isBlank()) {
                return new Response(sc, null);
            }
            try {
                return new Response(sc, objectMapper.readTree(bodyText));
            } catch (Exception ex) {
                // Тело не является JSON — в рамках identity считаем это ошибкой интеграции.
                return new Response(sc, null);
            }
        } catch (Exception e) {
            return new Response(0, null);
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
        }
    }

    /**
     * Ответ синхронного REST-вызова.
     *
     * @param httpStatus HTTP-статус (0, если запрос не был выполнен)
     * @param json JSON-тело ответа (может быть null)
     */
    public record Response(int httpStatus, JsonNode json) {
    }
}
