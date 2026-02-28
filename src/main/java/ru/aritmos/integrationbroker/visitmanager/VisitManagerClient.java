package ru.aritmos.integrationbroker.visitmanager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.RestOutboxService;
import ru.aritmos.integrationbroker.core.SensitiveDataSanitizer;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Клиент взаимодействия с VisitManager.
 *
 * <p>Цель данного клиента — дать Groovy-flow и Java-слою типизированный, эксплуатационно безопасный
 * способ:
 * <ul>
 *   <li>создавать визит в VisitManager по списку услуг и параметрам (VisitParameters);</li>
 *   <li>получать каталог услуг отделения для последующего сопоставления внешних процедур.</li>
 * </ul>
 *
 * <p>Важно:
 * <ul>
 *   <li>Integration Broker не реализует правила вызова и сегментации — это ответственность VisitManager;</li>
 *   <li>секреты (Authorization/API keys) берутся только из {@code restConnectors} и не сохраняются в outbox;</li>
 *   <li>при ошибке прямого REST-вызова возможна постановка запроса в REST outbox (fallback).</li>
 * </ul>
 */
@Singleton
public class VisitManagerClient {

    private final RuntimeConfigStore configStore;
    private final RestOutboxService restOutboxService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public VisitManagerClient(RuntimeConfigStore configStore, RestOutboxService restOutboxService, ObjectMapper objectMapper) {
        this.configStore = configStore;
        this.restOutboxService = restOutboxService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Создать визит через REST API VisitManager.
     *
     * <p>Алгоритм:
     * <ol>
     *   <li>формирует URL на основе runtime-конфига VisitManager (connectorId/baseUrl);</li>
     *   <li>выполняет прямой HTTP вызов, чтобы получить ответ VisitManager (ид визита/талон и т.п.);</li>
     *   <li>если прямой вызов не удался и включён REST outbox — сохраняет запрос в outbox для повтора.</li>
     * </ol>
     *
     * @param branchId идентификатор отделения
     * @param entryPointId идентификатор точки создания визита
     * @param serviceIds список услуг VisitManager
     * @param parameters дополнительные параметры визита (будут доступны VisitManager для его логики)
     * @param printTicket печатать талон
     * @param segmentationRuleId опциональный идентификатор правила сегментации (обычно не задаётся: VisitManager решает сам)
     * @param extraHeaders дополнительные заголовки (не должны содержать секретов)
     * @param sourceMessageId id входящего сообщения
     * @param correlationId correlation id
     * @param idempotencyKey ключ идемпотентности
     * @return результат вызова: либо ответ VisitManager, либо постановка в outbox
     */
    public CallResult createVisitWithParametersRest(String branchId,
                                                   String entryPointId,
                                                   List<String> serviceIds,
                                                   Map<String, String> parameters,
                                                   boolean printTicket,
                                                   String segmentationRuleId,
                                                   Map<String, String> extraHeaders,
                                                   String sourceMessageId,
                                                   String correlationId,
                                                   String idempotencyKey) {

        RuntimeConfigStore.RuntimeConfig eff = configStore.getEffective();
        RuntimeConfigStore.VisitManagerIntegrationConfig vm = eff == null ? null : eff.visitManager();
        if (vm == null || !vm.enabled()) {
            return CallResult.error("DISABLED", "Интеграция с VisitManager отключена (visitManager.enabled=false)");
        }

        String connectorId = safe(vm.connectorId(), "visitmanager");
        RuntimeConfigStore.RestConnectorConfig conn = (eff.restConnectors() == null) ? null : eff.restConnectors().get(connectorId);
        String baseUrl = conn == null ? null : safe(conn.baseUrl(), null);
        if (baseUrl == null) {
            return CallResult.error("NO_CONNECTOR", "Не найден restConnectors." + connectorId + " или не задан baseUrl");
        }

        String ep = safe(entryPointId, safe(vm.defaultEntryPointId(), "1"));
        String path = buildCreateVisitWithParametersPath(branchId, ep, printTicket, segmentationRuleId);
        String url = buildUrl(baseUrl, path);

        Map<String, String> storedHeaders = withCorrelationHeaders(SensitiveDataSanitizer.sanitizeHeaders(extraHeaders), correlationId, sourceMessageId);
        Map<String, String> directHeaders = mergeHeaders(storedHeaders, buildAuthHeaders(conn == null ? null : conn.auth()));
        directHeaders.putIfAbsent("Content-Type", "application/json");

        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(new VisitParametersPayload(serviceIds, parameters));
        } catch (Exception e) {
            return CallResult.error("SERIALIZE_FAILED", "Не удалось сериализовать тело запроса: " + safeMsg(e));
        }

        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson));
            for (Map.Entry<String, String> h : directHeaders.entrySet()) {
                if (h.getKey() != null && h.getValue() != null) {
                    rb.header(h.getKey(), h.getValue());
                }
            }
            HttpResponse<String> resp = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status >= 200 && status < 300) {
                JsonNode node;
                try {
                    node = resp.body() == null || resp.body().isBlank() ? null : objectMapper.readTree(resp.body());
                } catch (Exception parseEx) {
                    node = null;
                }
                return CallResult.direct(status, node);
            }

            // Ошибка — fallback в outbox (если включено)
            long outboxId = enqueueFallback("POST", eff, connectorId, path, url, storedHeaders, bodyJson, sourceMessageId, correlationId, idempotencyKey);
            if (outboxId > 0) {
                return CallResult.queued(status, outboxId);
            }
            return CallResult.error("HTTP_" + status, "VisitManager вернул статус " + status);

        } catch (Exception ex) {
            long outboxId = enqueueFallback("POST", eff, connectorId, path, url, storedHeaders, bodyJson, sourceMessageId, correlationId, idempotencyKey);
            if (outboxId > 0) {
                return CallResult.queued(0, outboxId);
            }
            return CallResult.error("CALL_FAILED", "Ошибка вызова VisitManager: " + safeMsg(ex));
        }
    }

    /**
     * Создать визит через REST API VisitManager по списку услуг
     * (endpoint {@code POST /entrypoint/branches/{branchId}/entry-points/{entryPointId}/visits}).
     */
    public CallResult createVisitRest(String branchId,
                                      String entryPointId,
                                      List<String> serviceIds,
                                      boolean printTicket,
                                      String segmentationRuleId,
                                      Map<String, String> extraHeaders,
                                      String sourceMessageId,
                                      String correlationId,
                                      String idempotencyKey) {
        RuntimeConfigStore.RuntimeConfig eff = configStore.getEffective();
        RuntimeConfigStore.VisitManagerIntegrationConfig vm = eff == null ? null : eff.visitManager();
        if (vm == null || !vm.enabled()) {
            return CallResult.error("DISABLED", "Интеграция с VisitManager отключена (visitManager.enabled=false)");
        }

        String connectorId = safe(vm.connectorId(), "visitmanager");
        RuntimeConfigStore.RestConnectorConfig conn = (eff.restConnectors() == null) ? null : eff.restConnectors().get(connectorId);
        String baseUrl = conn == null ? null : safe(conn.baseUrl(), null);
        if (baseUrl == null) {
            return CallResult.error("NO_CONNECTOR", "Не найден restConnectors." + connectorId + " или не задан baseUrl");
        }

        String ep = safe(entryPointId, safe(vm.defaultEntryPointId(), "1"));
        String path = buildCreateVisitPath(branchId, ep, printTicket, segmentationRuleId);
        String url = buildUrl(baseUrl, path);

        Map<String, String> storedHeaders = withCorrelationHeaders(SensitiveDataSanitizer.sanitizeHeaders(extraHeaders), correlationId, sourceMessageId);
        Map<String, String> directHeaders = mergeHeaders(storedHeaders, buildAuthHeaders(conn == null ? null : conn.auth()));
        directHeaders.putIfAbsent("Content-Type", "application/json");

        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(serviceIds == null ? List.of() : List.copyOf(serviceIds));
        } catch (Exception e) {
            return CallResult.error("SERIALIZE_FAILED", "Не удалось сериализовать список услуг: " + safeMsg(e));
        }

        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson));
            for (Map.Entry<String, String> h : directHeaders.entrySet()) {
                if (h.getKey() != null && h.getValue() != null) {
                    rb.header(h.getKey(), h.getValue());
                }
            }
            HttpResponse<String> resp = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status >= 200 && status < 300) {
                JsonNode node;
                try {
                    node = resp.body() == null || resp.body().isBlank() ? null : objectMapper.readTree(resp.body());
                } catch (Exception parseEx) {
                    node = null;
                }
                return CallResult.direct(status, node);
            }

            long outboxId = enqueueFallback("POST", eff, connectorId, path, url, storedHeaders, bodyJson, sourceMessageId, correlationId, idempotencyKey);
            if (outboxId > 0) {
                return CallResult.queued(status, outboxId);
            }
            return CallResult.error("HTTP_" + status, "VisitManager вернул статус " + status);

        } catch (Exception ex) {
            long outboxId = enqueueFallback("POST", eff, connectorId, path, url, storedHeaders, bodyJson, sourceMessageId, correlationId, idempotencyKey);
            if (outboxId > 0) {
                return CallResult.queued(0, outboxId);
            }
            return CallResult.error("CALL_FAILED", "Ошибка вызова VisitManager: " + safeMsg(ex));
        }
    }


    /**
     * Обновить параметры визита через PUT /entrypoint/branches/{branchId}/visits/{visitId}.
     */
    public CallResult updateVisitParametersRest(String branchId,
                                                String visitId,
                                                Map<String, String> parameters,
                                                Map<String, String> extraHeaders,
                                                String sourceMessageId,
                                                String correlationId,
                                                String idempotencyKey) {
        RuntimeConfigStore.RuntimeConfig eff = configStore.getEffective();
        RuntimeConfigStore.VisitManagerIntegrationConfig vm = eff == null ? null : eff.visitManager();
        if (vm == null || !vm.enabled()) {
            return CallResult.error("DISABLED", "Интеграция с VisitManager отключена (visitManager.enabled=false)");
        }

        String connectorId = safe(vm.connectorId(), "visitmanager");
        RuntimeConfigStore.RestConnectorConfig conn = (eff.restConnectors() == null) ? null : eff.restConnectors().get(connectorId);
        String baseUrl = conn == null ? null : safe(conn.baseUrl(), null);
        if (baseUrl == null) {
            return CallResult.error("NO_CONNECTOR", "Не найден restConnectors." + connectorId + " или не задан baseUrl");
        }

        String path = "/entrypoint/branches/" + urlEncode(branchId) + "/visits/" + urlEncode(visitId);
        String url = buildUrl(baseUrl, path);

        Map<String, String> storedHeaders = withCorrelationHeaders(SensitiveDataSanitizer.sanitizeHeaders(extraHeaders), correlationId, sourceMessageId);
        Map<String, String> directHeaders = mergeHeaders(storedHeaders, buildAuthHeaders(conn == null ? null : conn.auth()));
        directHeaders.putIfAbsent("Content-Type", "application/json");

        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(parameters == null ? Map.of() : parameters);
        } catch (Exception e) {
            return CallResult.error("SERIALIZE_FAILED", "Не удалось сериализовать параметры визита: " + safeMsg(e));
        }

        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .PUT(HttpRequest.BodyPublishers.ofString(bodyJson));
            for (Map.Entry<String, String> h : directHeaders.entrySet()) {
                if (h.getKey() != null && h.getValue() != null) {
                    rb.header(h.getKey(), h.getValue());
                }
            }
            HttpResponse<String> resp = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status >= 200 && status < 300) {
                JsonNode node;
                try {
                    node = resp.body() == null || resp.body().isBlank() ? null : objectMapper.readTree(resp.body());
                } catch (Exception parseEx) {
                    node = null;
                }
                return CallResult.direct(status, node);
            }
            long outboxId = enqueueFallback("PUT", eff, connectorId, path, url, storedHeaders, bodyJson, sourceMessageId, correlationId, idempotencyKey);
            if (outboxId > 0) {
                return CallResult.queued(status, outboxId);
            }
            return CallResult.error("HTTP_" + status, "VisitManager вернул статус " + status);
        } catch (Exception ex) {
            long outboxId = enqueueFallback("PUT", eff, connectorId, path, url, storedHeaders, bodyJson, sourceMessageId, correlationId, idempotencyKey);
            if (outboxId > 0) {
                return CallResult.queued(0, outboxId);
            }
            return CallResult.error("CALL_FAILED", "Ошибка вызова VisitManager: " + safeMsg(ex));
        }
    }

    /**
     * Получить каталог услуг отделения из VisitManager.
     *
     * @param branchId идентификатор отделения
     * @return JSON-ответ VisitManager (обычно массив услуг)
     */
    public CallResult getServicesCatalog(String branchId) {
        RuntimeConfigStore.RuntimeConfig eff = configStore.getEffective();
        RuntimeConfigStore.VisitManagerIntegrationConfig vm = eff == null ? null : eff.visitManager();
        if (vm == null || !vm.enabled()) {
            return CallResult.error("DISABLED", "Интеграция с VisitManager отключена (visitManager.enabled=false)");
        }

        String connectorId = safe(vm.connectorId(), "visitmanager");
        RuntimeConfigStore.RestConnectorConfig conn = (eff.restConnectors() == null) ? null : eff.restConnectors().get(connectorId);
        String baseUrl = conn == null ? null : safe(conn.baseUrl(), null);
        if (baseUrl == null) {
            return CallResult.error("NO_CONNECTOR", "Не найден restConnectors." + connectorId + " или не задан baseUrl");
        }

        String path = safe(vm.servicesCatalogPathTemplate(), "/entrypoint/branches/{branchId}/services/catalog")
                .replace("{branchId}", urlEncode(branchId));
        String url = buildUrl(baseUrl, path);

        Map<String, String> directHeaders = buildAuthHeaders(conn == null ? null : conn.auth());

        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET();
            for (Map.Entry<String, String> h : directHeaders.entrySet()) {
                if (h.getKey() != null && h.getValue() != null) {
                    rb.header(h.getKey(), h.getValue());
                }
            }

            HttpResponse<String> resp = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status >= 200 && status < 300) {
                JsonNode node;
                try {
                    node = resp.body() == null || resp.body().isBlank() ? null : objectMapper.readTree(resp.body());
                } catch (Exception parseEx) {
                    node = null;
                }
                return CallResult.direct(status, node);
            }
            return CallResult.error("HTTP_" + status, "VisitManager вернул статус " + status);
        } catch (Exception ex) {
            return CallResult.error("CALL_FAILED", "Ошибка вызова VisitManager: " + safeMsg(ex));
        }
    }

    /**
     * Универсальный REST-вызов endpoint VisitManager через connector-конфигурацию.
     *
     * <p>Позволяет вызывать дополнительные endpoint'ы VisitManager до появления
     * специализированного typed-метода в {@code VisitManagerApi}.
     */
    public CallResult callRestEndpoint(String method,
                                       String path,
                                       Object body,
                                       Map<String, String> extraHeaders,
                                       String sourceMessageId,
                                       String correlationId,
                                       String idempotencyKey) {

        RuntimeConfigStore.RuntimeConfig eff = configStore.getEffective();
        RuntimeConfigStore.VisitManagerIntegrationConfig vm = eff == null ? null : eff.visitManager();
        if (vm == null || !vm.enabled()) {
            return CallResult.error("DISABLED", "Интеграция с VisitManager отключена (visitManager.enabled=false)");
        }

        String connectorId = safe(vm.connectorId(), "visitmanager");
        RuntimeConfigStore.RestConnectorConfig conn = (eff.restConnectors() == null) ? null : eff.restConnectors().get(connectorId);
        String baseUrl = conn == null ? null : safe(conn.baseUrl(), null);
        if (baseUrl == null) {
            return CallResult.error("NO_CONNECTOR", "Не найден restConnectors." + connectorId + " или не задан baseUrl");
        }

        String effMethod = safe(method, "GET").toUpperCase();
        String reqPath = safe(path, "/");
        String url = buildUrl(baseUrl, reqPath);

        Map<String, String> storedHeaders = withCorrelationHeaders(extraHeaders, correlationId, sourceMessageId);
        Map<String, String> directHeaders = mergeHeaders(buildAuthHeaders(conn == null ? null : conn.auth()), storedHeaders);

        String bodyJson = null;
        if (!"GET".equals(effMethod) && !"DELETE".equals(effMethod) && body != null) {
            try {
                bodyJson = objectMapper.writeValueAsString(body);
            } catch (Exception e) {
                return CallResult.error("SERIALIZE_FAILED", "Не удалось сериализовать тело запроса");
            }
        }

        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10));
            if ("GET".equals(effMethod)) {
                rb.GET();
            } else if ("DELETE".equals(effMethod)) {
                rb.DELETE();
            } else {
                rb.method(effMethod, HttpRequest.BodyPublishers.ofString(bodyJson == null ? "" : bodyJson));
                rb.header("Content-Type", "application/json");
            }

            for (Map.Entry<String, String> h : directHeaders.entrySet()) {
                if (h.getKey() != null && h.getValue() != null) {
                    rb.header(h.getKey(), h.getValue());
                }
            }

            HttpResponse<String> resp = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status >= 200 && status < 300) {
                JsonNode node;
                try {
                    node = resp.body() == null || resp.body().isBlank() ? null : objectMapper.readTree(resp.body());
                } catch (Exception parseEx) {
                    node = null;
                }
                return CallResult.direct(status, node);
            }

            long outboxId = enqueueFallback(effMethod, eff, connectorId, reqPath, url, storedHeaders,
                    bodyJson, sourceMessageId, correlationId, idempotencyKey);
            if (outboxId > 0) {
                return CallResult.queued(status, outboxId);
            }
            return CallResult.error("HTTP_" + status, "VisitManager вернул статус " + status);
        } catch (Exception ex) {
            long outboxId = enqueueFallback(effMethod, eff, connectorId, reqPath, url, storedHeaders,
                    bodyJson, sourceMessageId, correlationId, idempotencyKey);
            if (outboxId > 0) {
                return CallResult.queued(0, outboxId);
            }
            return CallResult.error("CALL_FAILED", "Ошибка вызова VisitManager: " + safeMsg(ex));
        }
    }

    private long enqueueFallback(String method, RuntimeConfigStore.RuntimeConfig eff,
                                 String connectorId,
                                 String path,
                                 String url,
                                 Map<String, String> storedHeaders,
                                 String bodyJson,
                                 String sourceMessageId,
                                 String correlationId,
                                 String idempotencyKey) {

        RuntimeConfigStore.RestOutboxConfig oc = eff == null ? null : eff.restOutbox();
        if (oc == null || !oc.enabled()) {
            return 0;
        }

        // В outbox сохраняем только санитизированные заголовки (без auth).
        // Итоговый auth будет добавлен диспетчером по restConnectors.
        String idKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : sourceMessageId;

        return restOutboxService.enqueue(
                safe(method, "POST"),
                url,
                connectorId,
                path,
                storedHeaders,
                rawJsonBody(bodyJson),
                idKey,
                sourceMessageId,
                correlationId,
                idempotencyKey,
                oc.maxAttempts(),
                oc.treat4xxAsSuccess()
        );
    }

    private Object rawJsonBody(String bodyJson) {
        if (bodyJson == null) {
            return null;
        }
        try {
            return objectMapper.readTree(bodyJson);
        } catch (Exception e) {
            return null;
        }
    }

    private static String buildCreateVisitPath(String branchId, String entryPointId, boolean printTicket, String segmentationRuleId) {
        String base = "/entrypoint/branches/" + urlEncode(branchId)
                + "/entry-points/" + urlEncode(entryPointId)
                + "/visits?printTicket=" + (printTicket ? "true" : "false");
        if (segmentationRuleId != null && !segmentationRuleId.isBlank()) {
            base += "&segmentationRuleId=" + urlEncode(segmentationRuleId);
        }
        return base;
    }

    private static String buildCreateVisitWithParametersPath(String branchId, String entryPointId, boolean printTicket, String segmentationRuleId) {
        String base = "/entrypoint/branches/" + urlEncode(branchId)
                + "/entry-points/" + urlEncode(entryPointId)
                + "/visits/parameters?printTicket=" + (printTicket ? "true" : "false");

        String seg = safe(segmentationRuleId, null);
        if (seg != null) {
            base = base + "&segmentationRuleId=" + urlEncode(seg);
        }
        return base;
    }

    private static String buildUrl(String baseUrl, String path) {
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String p = (path == null) ? "" : path;
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return b + p;
    }

    private static Map<String, String> buildAuthHeaders(RuntimeConfigStore.RestConnectorAuth auth) {
        if (auth == null || auth.type() == null) {
            return Map.of();
        }
        return switch (auth.type()) {
            case NONE -> Map.of();
            case BASIC -> {
                String user = safe(auth.basicUsername(), "");
                String pass = safe(auth.basicPassword(), "");
                String token = Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
                yield Map.of("Authorization", "Basic " + token);
            }
            case BEARER -> {
                String token = safe(auth.bearerToken(), null);
                if (token == null) {
                    yield Map.of();
                }
                yield Map.of("Authorization", "Bearer " + token);
            }
            case API_KEY_HEADER -> {
                String name = safe(auth.headerName(), "X-Api-Key");
                String key = safe(auth.apiKey(), null);
                if (key == null) {
                    yield Map.of();
                }
                yield Map.of(name, key);
            }
        };
    }

    private static Map<String, String> mergeHeaders(Map<String, String> a, Map<String, String> b) {
        if (a == null || a.isEmpty()) {
            return (b == null) ? new java.util.HashMap<>() : new java.util.HashMap<>(b);
        }
        java.util.HashMap<String, String> out = new java.util.HashMap<>(a);
        if (b != null) {
            out.putAll(b);
        }
        return out;
    }


    private static Map<String, String> withCorrelationHeaders(Map<String, String> headers,
                                                              String correlationId,
                                                              String requestId) {
        java.util.HashMap<String, String> out = headers == null ? new java.util.HashMap<>() : new java.util.HashMap<>(headers);
        String corr = safe(correlationId, null);
        String req = safe(requestId, null);
        if (corr != null) {
            out.putIfAbsent("X-Correlation-Id", corr);
        }
        if (req != null) {
            out.putIfAbsent("X-Request-Id", req);
        }
        return out;
    }

    private static String urlEncode(String s) {
        if (s == null) {
            return "";
        }
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String safe(String v, String def) {
        if (v == null) {
            return def;
        }
        String t = v.trim();
        return t.isEmpty() ? def : t;
    }

    private static String safeMsg(Throwable t) {
        if (t == null) {
            return "";
        }
        return SensitiveDataSanitizer.sanitizeText(t.getMessage());
    }

    /**
     * Тело запроса создания визита (структура VisitParameters VisitManager).
     */
    public record VisitParametersPayload(List<String> serviceIds, Map<String, String> parameters) {
    }

    /**
     * Результат обращения к VisitManager.
     */
    public record CallResult(
            String mode,
            int httpStatus,
            long outboxId,
            JsonNode response,
            String errorCode,
            String errorMessage
    ) {
        public static CallResult direct(int status, JsonNode resp) {
            return new CallResult("DIRECT", status, 0, resp, null, null);
        }

        public static CallResult queued(int status, long outboxId) {
            return new CallResult("OUTBOX_ENQUEUED", status, outboxId, null, null, null);
        }

        public static CallResult error(String code, String message) {
            return new CallResult("ERROR", 0, 0, null, code, SensitiveDataSanitizer.sanitizeText(message));
        }
    }
}
