package ru.aritmos.integrationbroker.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сервис REST outbox.
 * <p>
 * Назначение:
 * <ul>
 *   <li>надёжно сохранять REST-вызовы, которые должны быть выполнены;</li>
 *   <li>поддерживать ретраи/backoff и эксплуатационную диагностику;</li>
 *   <li>поддерживать {@code Idempotency-Key} и трактовку части 4xx как логического успеха.</li>
 * </ul>
 */
@Singleton
public class RestOutboxService {

    public enum Status {
        PENDING,
        SENDING,
        SENT,
        DEAD
    }

    public enum Mode {
        ON_FAILURE,
        ALWAYS
    }

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final RestOutboundSender sender;

    public RestOutboxService(DataSource dataSource, ObjectMapper objectMapper, RestOutboundSender sender) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.sender = sender;
    }

    /**
     * Выполнить REST-вызов согласно политике outbox.
     *
     * @return id записи outbox или 0, если outbox выключен и вызов выполнен напрямую
     */
    public long call(RuntimeConfigStore.RestOutboxConfig cfg,
                     String method,
                     String url,
                     Map<String, String> headers,
                     Object body,
                     String idempotencyKey,
                     String sourceMessageId,
                     String correlationId,
                     String idemKey) {
        if (cfg == null || !cfg.enabled()) {
            // Если outbox выключен — выполняем прямой вызов.
            String idemHeader = cfg == null ? "Idempotency-Key" : cfg.idempotencyHeaderName();
            RestOutboundSender.Result r = sender.send(method, url, SensitiveDataSanitizer.sanitizeHeaders(headers), toJson(body), idemHeader, idempotencyKey);
            if (r.success() || isTreat4xxAsSuccess(cfg, r.httpStatus())) {
                return 0;
            }
            int maxAttempts = cfg == null ? 10 : cfg.maxAttempts();
            String treat = cfg == null ? null : cfg.treat4xxAsSuccess();
            return enqueue(method, url, null, null, headers, body, idempotencyKey, sourceMessageId, correlationId, idemKey, maxAttempts, treat);
        }

        Mode mode = parseMode(cfg.mode());
        if (mode == Mode.ALWAYS) {
            return enqueue(method, url, null, null, headers, body, idempotencyKey, sourceMessageId, correlationId, idemKey, cfg.maxAttempts(), cfg.treat4xxAsSuccess());
        }

        RestOutboundSender.Result r = sender.send(method, url, SensitiveDataSanitizer.sanitizeHeaders(headers), toJson(body), cfg.idempotencyHeaderName(), idempotencyKey);
        if (r.success() || isTreat4xxAsSuccess(cfg, r.httpStatus())) {
            return 0;
        }
        return enqueue(method, url, null, null, headers, body, idempotencyKey, sourceMessageId, correlationId, idemKey, cfg.maxAttempts(), cfg.treat4xxAsSuccess());
    }

    
/**
 * Выполнить REST-вызов через заранее описанный коннектор.
 * <p>
 * Коннектор позволяет:
 * <ul>
 *   <li>не хранить секреты/авторизацию в outbox-таблице;</li>
 *   <li>формировать итоговые заголовки (включая Authorization/API-key) только в момент отправки;</li>
 *   <li>унифицировать вызовы внешних систем в закрытых контурах.</li>
 * </ul>
 *
 * @param effective effective-конфигурация (нужна, чтобы получить baseUrl и параметры авторизации)
 * @param connectorId идентификатор коннектора из runtime-конфига
 * @param method HTTP-метод
 * @param path относительный путь (например, /v1/customers/find)
 * @return id записи outbox или 0, если outbox выключен и вызов выполнен напрямую
 */
public long callViaConnector(RuntimeConfigStore.RuntimeConfig effective,
                             RuntimeConfigStore.RestOutboxConfig cfg,
                             String connectorId,
                             String method,
                             String path,
                             Map<String, String> headers,
                             Object body,
                             String idempotencyKey,
                             String sourceMessageId,
                             String correlationId,
                             String idemKey) {

    RuntimeConfigStore.RestConnectorConfig connector = (effective == null || effective.restConnectors() == null)
            ? null
            : effective.restConnectors().get(connectorId);

    String baseUrl = connector == null ? null : connector.baseUrl();
    String url = buildUrl(baseUrl, path);

    Map<String, String> stored = SensitiveDataSanitizer.sanitizeHeaders(headers);
    Map<String, String> authHeaders = buildAuthHeaders(connector == null ? null : connector.auth());

    // Итоговые заголовки для прямой отправки: stored + auth.
    Map<String, String> direct = mergeHeaders(stored, authHeaders);

    if (cfg == null || !cfg.enabled()) {
        // outbox выключен — прямой вызов
        String idemHeader = cfg == null ? "Idempotency-Key" : cfg.idempotencyHeaderName();
        RestOutboundSender.Result r = sender.send(method, url, direct, toJson(body), idemHeader, idempotencyKey);
        if (r.success() || isTreat4xxAsSuccess(cfg, r.httpStatus())) {
            return 0;
        }
        int maxAttempts = cfg == null ? 10 : cfg.maxAttempts();
        String treat = cfg == null ? null : cfg.treat4xxAsSuccess();
        return enqueue(method, url, connectorId, path, stored, body, idempotencyKey, sourceMessageId, correlationId, idemKey, maxAttempts, treat);
    }

    Mode mode = parseMode(cfg.mode());
    if (mode == Mode.ALWAYS) {
        return enqueue(method, url, connectorId, path, stored, body, idempotencyKey, sourceMessageId, correlationId, idemKey, cfg.maxAttempts(), cfg.treat4xxAsSuccess());
    }

    RestOutboundSender.Result r = sender.send(method, url, direct, toJson(body), cfg.idempotencyHeaderName(), idempotencyKey);
    if (r.success() || isTreat4xxAsSuccess(cfg, r.httpStatus())) {
        return 0;
    }

    return enqueue(method, url, connectorId, path, stored, body, idempotencyKey, sourceMessageId, correlationId, idemKey, cfg.maxAttempts(), cfg.treat4xxAsSuccess());
}

private static boolean isTreat4xxAsSuccess(RuntimeConfigStore.RestOutboxConfig cfg, int status) {
        if (cfg == null || cfg.treat4xxAsSuccess() == null || cfg.treat4xxAsSuccess().isBlank()) {
            return false;
        }
        if (status < 400 || status >= 500) {
            return false;
        }
        String s = "," + cfg.treat4xxAsSuccess().replaceAll("\\s+", "") + ",";
        return s.contains("," + status + ",");
    }

    private static Mode parseMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return Mode.ON_FAILURE;
        }
        try {
            return Mode.valueOf(mode.trim().toUpperCase());
        } catch (Exception e) {
            return Mode.ON_FAILURE;
        }
    }

    public long enqueue(String method,
                        String url,
                        String connectorId,
                        String path,
                        Map<String, String> headers,
                        Object body,
                        String idempotencyKey,
                        String sourceMessageId,
                        String correlationId,
                        String idemKey,
                        int maxAttempts,
                        String treat4xxAsSuccess) {
        Instant now = Instant.now();
        String hdr = toJson(SensitiveDataSanitizer.sanitizeHeaders(headers));
        String bodyJson = body == null ? null : toJson(body);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO ib_rest_outbox (status, created_at, updated_at, http_method, url, connector_id, path, headers_json, body_json, idempotency_key, source_message_id, correlation_id, idem_key, attempts, max_attempts, next_attempt_at, treat_4xx_as_success) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)",
                     new String[]{"id"})) {
            ps.setString(1, Status.PENDING.name());
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setString(4, safeShort(method, 16, "POST"));
            ps.setString(5, safeShort(url, 4000, ""));
            ps.setString(6, safeShort(connectorId, 100, null));
            ps.setString(7, safeShort(path, 2000, null));
            ps.setString(8, hdr);
            ps.setString(9, bodyJson);
            ps.setString(10, safeShort(idempotencyKey, 128, null));
            ps.setString(11, safeShort(sourceMessageId, 128, null));
            ps.setString(12, safeShort(correlationId, 128, null));
            ps.setString(13, safeShort(idemKey, 128, null));
            ps.setInt(14, Math.max(1, maxAttempts));
            ps.setTimestamp(15, Timestamp.from(now));
            ps.setString(16, safeShort(treat4xxAsSuccess, 200, null));

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (Exception e) {
            // no-op
        }
        return 0;
    }

    public RestRecord get(long id) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, status, http_method, url, connector_id, path, headers_json, body_json, idempotency_key, source_message_id, correlation_id, idem_key, attempts, max_attempts, next_attempt_at, treat_4xx_as_success, last_error_code, last_error_message, last_http_status, updated_at " +
                             "FROM ib_rest_outbox WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return map(rs);
            }
        } catch (Exception e) {
            return null;
        }
    }

    public List<RestListItem> list(String status, int limit) {
        int lim = Math.min(Math.max(1, limit), 200);
        List<RestListItem> out = new ArrayList<>();
        String sql = (status == null || status.isBlank())
                ? "SELECT id, status, http_method, url, connector_id, path, attempts, max_attempts, next_attempt_at, updated_at FROM ib_rest_outbox ORDER BY updated_at DESC LIMIT ?"
                : "SELECT id, status, http_method, url, connector_id, path, attempts, max_attempts, next_attempt_at, updated_at FROM ib_rest_outbox WHERE status=? ORDER BY updated_at DESC LIMIT ?";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int idx = 1;
            if (status != null && !status.isBlank()) {
                ps.setString(idx++, status);
            }
            ps.setInt(idx, lim);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new RestListItem(
                            rs.getLong(1),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getString(6),
                            rs.getInt(7),
                            rs.getInt(8),
                            rs.getTimestamp(9) == null ? null : rs.getTimestamp(9).toInstant().toString(),
                            rs.getTimestamp(10) == null ? null : rs.getTimestamp(10).toInstant().toString()
                    ));
                }
            }
        } catch (Exception e) {
            // no-op
        }
        return out;
    }

    public List<RestRecord> pickDue(int limit) {
        int lim = Math.min(Math.max(1, limit), 200);
        List<RestRecord> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, status, http_method, url, connector_id, path, headers_json, body_json, idempotency_key, source_message_id, correlation_id, idem_key, attempts, max_attempts, next_attempt_at, treat_4xx_as_success, last_error_code, last_error_message, last_http_status, updated_at " +
                             "FROM ib_rest_outbox WHERE status=? AND next_attempt_at<=? ORDER BY id ASC LIMIT ?")) {
            ps.setString(1, Status.PENDING.name());
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setInt(3, lim);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        } catch (Exception e) {
            // no-op
        }
        return out;
    }

    public boolean markSending(long id) {
        Instant now = Instant.now();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE ib_rest_outbox SET status=?, updated_at=? WHERE id=? AND status=?")) {
            ps.setString(1, Status.SENDING.name());
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setLong(3, id);
            ps.setString(4, Status.PENDING.name());
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            return false;
        }
    }

    public void markSent(long id, int httpStatus) {
        Instant now = Instant.now();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE ib_rest_outbox SET status=?, updated_at=?, last_error_at=NULL, last_error_code=NULL, last_error_message=NULL, last_http_status=? WHERE id=?")) {
            ps.setString(1, Status.SENT.name());
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setInt(3, httpStatus);
            ps.setLong(4, id);
            ps.executeUpdate();
        } catch (Exception e) {
            // no-op
        }
    }

    public void markFailed(long id,
                           int attemptsAlready,
                           int maxAttempts,
                           Instant nextAttemptAt,
                           String errorCode,
                           String errorMessage,
                           int httpStatus,
                           boolean dead) {
        Instant now = Instant.now();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE ib_rest_outbox SET status=?, updated_at=?, attempts=?, max_attempts=?, next_attempt_at=?, last_error_at=?, last_error_code=?, last_error_message=?, last_http_status=? WHERE id=?")) {
            ps.setString(1, dead ? Status.DEAD.name() : Status.PENDING.name());
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setInt(3, attemptsAlready);
            ps.setInt(4, maxAttempts);
            ps.setTimestamp(5, Timestamp.from(nextAttemptAt));
            ps.setTimestamp(6, Timestamp.from(now));
            ps.setString(7, safeShort(errorCode, 64, "HTTP_ERROR"));
            ps.setString(8, safeShort(errorMessage, 1000, ""));
            ps.setInt(9, httpStatus);
            ps.setLong(10, id);
            ps.executeUpdate();
        } catch (Exception e) {
            // no-op
        }
    }

    /**
     * Переотправка записи REST outbox.
     *
     * @param id идентификатор записи
     * @param resetAttempts сбросить счётчик попыток
     * @return true, если запись обновлена
     */
    public boolean replay(long id, boolean resetAttempts) {
        Instant now = Instant.now();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE ib_rest_outbox SET status=?, updated_at=?, next_attempt_at=?, attempts=?, last_error_at=NULL, last_error_code=NULL, last_error_message=NULL WHERE id=?")) {
            ps.setString(1, Status.PENDING.name());
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setInt(4, resetAttempts ? 0 : getAttempts(id));
            ps.setLong(5, id);
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private int getAttempts(long id) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT attempts FROM ib_rest_outbox WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            return 0;
        }
        return 0;
    }

    public Map<String, String> parseHeaders(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    public long countByStatus(Status status) {
        if (status == null) {
            return 0;
        }
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(1) FROM ib_rest_outbox WHERE status=?")) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (Exception e) {
            return 0;
        }
        return 0;
    }

    /**
     * Выполнить одну попытку отправки по записи outbox.
     * <p>
     * Это используется диспетчером. Логика "treat 4xx as success" учитывается здесь.
     */
    /**
 * Выполнить одну попытку отправки по записи outbox.
 * <p>
 * Это используется диспетчером. Логика "treat 4xx as success" учитывается здесь.
 * <p>
 * Важно: если запись привязана к коннектору (connectorId), то базовый URL и авторизация
 * берутся из runtime-конфига в момент отправки. Это позволяет не хранить секреты в БД.
 */
public RestOutboundSender.Result sendOnce(RestRecord record,
                                         String idempotencyHeaderName,
                                         RuntimeConfigStore.RuntimeConfig effective) {

    Map<String, String> storedHeaders = parseHeaders(record.headersJson());
    Map<String, String> authHeaders = Collections.emptyMap();

    String url = record.url();
    if (record.connectorId() != null && !record.connectorId().isBlank()) {
        RuntimeConfigStore.RestConnectorConfig connector = (effective == null || effective.restConnectors() == null)
                ? null
                : effective.restConnectors().get(record.connectorId());

        String baseUrl = connector == null ? null : connector.baseUrl();
        url = buildUrl(baseUrl, record.path());
        authHeaders = buildAuthHeaders(connector == null ? null : connector.auth());
    }

    Map<String, String> headers = mergeHeaders(storedHeaders, authHeaders);

    RestOutboundSender.Result r = sender.send(record.httpMethod(), url, headers, record.bodyJson(), idempotencyHeaderName, record.idempotencyKey());
    if (!r.success() && record.treat4xxAsSuccess() != null && !record.treat4xxAsSuccess().isBlank() && r.httpStatus() >= 400 && r.httpStatus() < 500) {
        String s = "," + record.treat4xxAsSuccess().replaceAll("\s+", "") + ",";
        if (s.contains("," + r.httpStatus() + ",")) {
            return RestOutboundSender.Result.ok(r.httpStatus());
        }
    }
    return r;
}

    private RestRecord map(ResultSet rs) throws Exception {
    long id = rs.getLong(1);
    String status = rs.getString(2);
    String method = rs.getString(3);
    String url = rs.getString(4);
    String connectorId = rs.getString(5);
    String path = rs.getString(6);
    String headersJson = rs.getString(7);
    String bodyJson = rs.getString(8);
    String idemHeaderKey = rs.getString(9);
    String sourceMessageId = rs.getString(10);
    String correlationId = rs.getString(11);
    String idemKey = rs.getString(12);
    int attempts = rs.getInt(13);
    int maxAttempts = rs.getInt(14);
    Timestamp next = rs.getTimestamp(15);
    String treat = rs.getString(16);
    String lastCode = rs.getString(17);
    String lastMsg = rs.getString(18);
    Integer lastStatus = rs.getObject(19, Integer.class);
    Timestamp updated = rs.getTimestamp(20);

    return new RestRecord(
            id,
            status,
            method,
            url,
            connectorId,
            path,
            headersJson,
            bodyJson,
            idemHeaderKey,
            sourceMessageId,
            correlationId,
            idemKey,
            attempts,
            maxAttempts,
            next == null ? null : next.toInstant().toString(),
            treat,
            lastCode,
            lastMsg,
            lastStatus,
            updated == null ? null : updated.toInstant().toString()
    );
}

    private static String buildUrl(String baseUrl, String path) {
    String b = baseUrl == null ? "" : baseUrl.trim();
    String p = path == null ? "" : path.trim();
    if (b.endsWith("/")) {
        b = b.substring(0, b.length() - 1);
    }
    if (!p.isEmpty() && !p.startsWith("/")) {
        p = "/" + p;
    }
    String u = b + p;
    if (u.isBlank()) {
        return "";
    }
    return u;
}

private static Map<String, String> mergeHeaders(Map<String, String> a, Map<String, String> b) {
    if ((a == null || a.isEmpty()) && (b == null || b.isEmpty())) {
        return Collections.emptyMap();
    }
    Map<String, String> out = new HashMap<>();
    if (a != null) {
        out.putAll(a);
    }
    if (b != null) {
        out.putAll(b);
    }
    return out;
}

private static Map<String, String> buildAuthHeaders(RuntimeConfigStore.RestConnectorAuth auth) {
    if (auth == null || auth.type() == null) {
        return Collections.emptyMap();
    }
    RuntimeConfigStore.RestConnectorAuthType t = auth.type();
    return switch (t) {
        case NONE -> Collections.emptyMap();
        case API_KEY_HEADER -> {
            String name = (auth.headerName() == null || auth.headerName().isBlank()) ? "X-API-Key" : auth.headerName().trim();
            String key = auth.apiKey();
            if (key == null || key.isBlank()) {
                yield Collections.emptyMap();
            }
            yield Map.of(name, auth.apiKey().trim());
        }
        case BEARER -> {
            if (auth.bearerToken() == null || auth.bearerToken().isBlank()) {
                yield Collections.emptyMap();
            }
            // В outbox и логах токен не раскрываем.
            yield Map.of("Authorization", "Bearer " + auth.bearerToken().trim());
        }
        case BASIC -> {
            if (auth.basicUsername() == null || auth.basicPassword() == null) {
                yield Collections.emptyMap();
            }
            // В outbox и логах креды не раскрываем.
            String token = java.util.Base64.getEncoder().encodeToString((auth.basicUsername() + ":" + auth.basicPassword()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                yield Map.of("Authorization", "Basic " + token);
        }
    };
}

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String s) {
            String trimmed = s.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                return s;
            }
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"note\":\"body не сериализован\"}";
        }
    }

    private static String safeShort(String s, int max, String def) {
        if (s == null) {
            return def;
        }
        String v = SensitiveDataSanitizer.sanitizeText(s);
        if (v == null) {
            return def;
        }
        v = v.trim();
        if (v.isEmpty()) {
            return def;
        }
        if (v.length() <= max) {
            return v;
        }
        return v.substring(0, max);
    }

    @Serdeable
    public record RestRecord(
            long id,
            String status,
            String httpMethod,
            String url,
            String connectorId,
            String path,
            String headersJson,
            String bodyJson,
            String idempotencyKey,
            String sourceMessageId,
            String correlationId,
            String idemKey,
            int attempts,
            int maxAttempts,
            String nextAttemptAt,
            String treat4xxAsSuccess,
            String lastErrorCode,
            String lastErrorMessage,
            Integer lastHttpStatus,
            String updatedAt
    ) {
    }

    @Serdeable
    public record RestListItem(
            long id,
            String status,
            String httpMethod,
            String url,
            String connectorId,
            String path,
            int attempts,
            int maxAttempts,
            String nextAttemptAt,
            String updatedAt
    ) {
    }
}
