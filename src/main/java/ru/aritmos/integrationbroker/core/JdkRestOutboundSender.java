package ru.aritmos.integrationbroker.core;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * REST sender на базе стандартного JDK {@link java.net.http.HttpClient}.
 * <p>
 * Причины выбора на старте:
 * <ul>
 *   <li>минимум зависимостей;</li>
 *   <li>предсказуемость поведения в закрытых контурах;</li>
 *   <li>простота тестирования и замены на более сложную реализацию позже.</li>
 * </ul>
 */
@Singleton
public class JdkRestOutboundSender implements RestOutboundSender {

    private final HttpClient client;
    private final Duration requestTimeout;

    public JdkRestOutboundSender(
            @Value("${integrationbroker.rest-outbox.http-timeout-ms:8000}") long httpTimeoutMs
    ) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, httpTimeoutMs)))
                .build();
        this.requestTimeout = Duration.ofMillis(Math.max(1000, httpTimeoutMs));
    }

    @Override
    public Result send(String method,
                       String url,
                       Map<String, String> headers,
                       String bodyJson,
                       String idempotencyHeaderName,
                       String idempotencyKey) {
        try {
            String m = method == null ? "POST" : method.trim().toUpperCase();
            URI uri = URI.create(url);

            Map<String, String> hdrs = new HashMap<>();
            if (headers != null) {
                hdrs.putAll(headers);
            }
            if (idempotencyHeaderName != null && !idempotencyHeaderName.isBlank() && idempotencyKey != null && !idempotencyKey.isBlank()) {
                hdrs.put(idempotencyHeaderName, idempotencyKey);
            }

            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(requestTimeout);

            for (Map.Entry<String, String> e : hdrs.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()) {
                    continue;
                }
                String v = e.getValue() == null ? "" : e.getValue();
                b.header(e.getKey(), v);
            }

            if ("GET".equals(m) || "DELETE".equals(m)) {
                b.method(m, HttpRequest.BodyPublishers.noBody());
            } else {
                String body = bodyJson == null ? "" : bodyJson;
                b.method(m, HttpRequest.BodyPublishers.ofString(body));
                if (!hdrs.containsKey("Content-Type")) {
                    b.header("Content-Type", "application/json");
                }
            }

            HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();

            if (status >= 200 && status < 300) {
                return Result.ok(status);
            }

            return Result.fail("HTTP_" + status, "HTTP вызов завершился неуспешно: статус=" + status, status);
        } catch (Exception e) {
            return Result.fail("HTTP_CLIENT_ERROR", e.getMessage(), -1);
        }
    }
}
