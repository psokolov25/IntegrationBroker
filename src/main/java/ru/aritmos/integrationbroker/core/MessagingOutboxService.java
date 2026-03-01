package ru.aritmos.integrationbroker.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Value;
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
 * Сервис messaging outbox.
 * <p>
 * Назначение:
 * <ul>
 *   <li>надёжно сохранять сообщения, которые должны быть отправлены во внешний брокер;</li>
 *   <li>поддерживать ретраи/статусы/диагностику;</li>
 *   <li>реализовать режимы ALWAYS/ON_FAILURE.</li>
 * </ul>
 * <p>
 * Важно: outbox является долговременным хранилищем, поэтому по умолчанию используется PostgreSQL.
 */
@Singleton
public class MessagingOutboxService {

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
    private final MessagingProviderRegistry providerRegistry;
    private final OutboundDryRunState outboundDryRunState;
    @Value("${integrationbroker.outbound.dry-run:false}")
    protected boolean outboundDryRun;

    public MessagingOutboxService(DataSource dataSource,
                                 ObjectMapper objectMapper,
                                 MessagingProviderRegistry providerRegistry,
                                 OutboundDryRunState outboundDryRunState) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.providerRegistry = providerRegistry;
        this.outboundDryRunState = outboundDryRunState;
    }

    public MessagingOutboxService(DataSource dataSource,
                                 ObjectMapper objectMapper,
                                 MessagingProviderRegistry providerRegistry) {
        this(dataSource, objectMapper, providerRegistry, new OutboundDryRunState(false, null));
    }

    /**
     * Публикация сообщения согласно политике outbox.
     *
     * @param cfg effective-конфиг outbox
     * @param providerId id провайдера (например, kafka/logging)
     * @param destination топик/очередь/subject
     * @param messageKey ключ сообщения (может быть null)
     * @param headers заголовки
     * @param payload полезная нагрузка (будет сериализована в JSON)
     * @param sourceMessageId messageId входящего сообщения
     * @param correlationId correlationId
     * @param idempotencyKey idem key
     * @return id записи outbox или 0, если outbox выключен и отправка выполнена напрямую
     */
    public long publish(RuntimeConfigStore.MessagingOutboxConfig cfg,
                        String providerId,
                        String destination,
                        String messageKey,
                        Map<String, String> headers,
                        Object payload,
                        String sourceMessageId,
                        String correlationId,
                        String idempotencyKey) {
        if (outboundDryRunState.isDryRun(outboundDryRun)) {
            Mode dryRunMode = cfg == null ? Mode.ON_FAILURE : parseMode(cfg.mode());
            if (cfg == null || !cfg.enabled() || dryRunMode == Mode.ON_FAILURE) {
                return 0;
            }
        }

        if (cfg == null || !cfg.enabled()) {
            // Если outbox выключен — пытаемся отправить напрямую.
            MessagingProvider.SendResult r = sendDirect(providerId, destination, messageKey, headers, payload, correlationId, sourceMessageId, idempotencyKey);
            int maxAttempts = cfg == null ? 10 : cfg.maxAttempts();
            return r.success() ? 0 : enqueue(providerId, destination, messageKey, headers, payload, sourceMessageId, correlationId, idempotencyKey, maxAttempts);
        }

        Mode mode = parseMode(cfg.mode());
        if (mode == Mode.ALWAYS) {
            return enqueue(providerId, destination, messageKey, headers, payload, sourceMessageId, correlationId, idempotencyKey, cfg == null ? 10 : cfg.maxAttempts());
        }

        // ON_FAILURE: сначала пытаемся отправить напрямую.
        MessagingProvider.SendResult r = sendDirect(providerId, destination, messageKey, headers, payload, correlationId, sourceMessageId, idempotencyKey);
        if (r.success()) {
            return 0;
        }
        return enqueue(providerId, destination, messageKey, headers, payload, sourceMessageId, correlationId, idempotencyKey, cfg == null ? 10 : cfg.maxAttempts());
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

    private MessagingProvider.SendResult sendDirect(String providerId,
                                                    String destination,
                                                    String messageKey,
                                                    Map<String, String> headers,
                                                    Object payload,
                                                    String correlationId,
                                                    String sourceMessageId,
                                                    String idempotencyKey) {
        String payloadJson = toJson(payload);
        Map<String, String> safeHeaders = SensitiveDataSanitizer.sanitizeHeaders(headers);
        MessagingProvider provider = providerRegistry.get(providerId);
        return provider.send(new MessagingProvider.OutboundMessage(destination, messageKey, safeHeaders, payloadJson, correlationId, sourceMessageId, idempotencyKey));
    }

    /**
     * Добавить запись в outbox.
     */
    public long enqueue(String providerId,
                        String destination,
                        String messageKey,
                        Map<String, String> headers,
                        Object payload,
                        String sourceMessageId,
                        String correlationId,
                        String idempotencyKey,
                        int maxAttempts) {
        Instant now = Instant.now();
        String hdr = toJson(SensitiveDataSanitizer.sanitizeHeaders(headers));
        String payloadJson = toJson(payload);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO ib_messaging_outbox (status, created_at, updated_at, provider, destination, message_key, headers_json, payload_json, source_message_id, correlation_id, idem_key, attempts, max_attempts, next_attempt_at) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                     new String[]{"id"})) {
            ps.setString(1, Status.PENDING.name());
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setString(4, safeShort(providerId, 64, "logging"));
            ps.setString(5, safeShort(destination, 2000, "unknown"));
            ps.setString(6, safeShort(messageKey, 2000, null));
            ps.setString(7, hdr);
            ps.setString(8, payloadJson);
            ps.setString(9, safeShort(sourceMessageId, 128, null));
            ps.setString(10, safeShort(correlationId, 128, null));
            ps.setString(11, safeShort(idempotencyKey, 128, null));
            ps.setInt(12, Math.max(1, maxAttempts));
            ps.setTimestamp(13, Timestamp.from(now));

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (Exception e) {
            // Outbox не должен ломать основной сценарий.
        }
        return 0;
    }

    /**
     * Получить запись.
     */
    public OutboxRecord get(long id) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, status, provider, destination, message_key, headers_json, payload_json, source_message_id, correlation_id, idem_key, attempts, max_attempts, next_attempt_at, last_error_code, last_error_message, updated_at " +
                             "FROM ib_messaging_outbox WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapRecord(rs);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Список (без payload).
     */
    public List<OutboxListItem> list(String status, int limit) {
        int lim = Math.min(Math.max(1, limit), 200);
        List<OutboxListItem> out = new ArrayList<>();

        String sql = (status == null || status.isBlank())
                ? "SELECT id, status, provider, destination, attempts, max_attempts, next_attempt_at, updated_at FROM ib_messaging_outbox ORDER BY updated_at DESC LIMIT ?"
                : "SELECT id, status, provider, destination, attempts, max_attempts, next_attempt_at, updated_at FROM ib_messaging_outbox WHERE status=? ORDER BY updated_at DESC LIMIT ?";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int idx = 1;
            if (status != null && !status.isBlank()) {
                ps.setString(idx++, status);
            }
            ps.setInt(idx, lim);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new OutboxListItem(
                            rs.getLong(1),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getInt(5),
                            rs.getInt(6),
                            rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant().toString(),
                            rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant().toString()
                    ));
                }
            }
        } catch (Exception e) {
            // no-op
        }
        return out;
    }

    /**
     * Выбрать пачку для отправки.
     */
    public List<OutboxRecord> pickDue(int limit) {
        int lim = Math.min(Math.max(1, limit), 200);
        List<OutboxRecord> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, status, provider, destination, message_key, headers_json, payload_json, source_message_id, correlation_id, idem_key, attempts, max_attempts, next_attempt_at, last_error_code, last_error_message, updated_at " +
                             "FROM ib_messaging_outbox WHERE status=? AND next_attempt_at<=? ORDER BY id ASC LIMIT ?")) {
            ps.setString(1, Status.PENDING.name());
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setInt(3, lim);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapRecord(rs));
                }
            }
        } catch (Exception e) {
            // no-op
        }
        return out;
    }

    /**
     * Попытаться «захватить» запись для отправки.
     */
    public boolean markSending(long id) {
        Instant now = Instant.now();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE ib_messaging_outbox SET status=?, updated_at=? WHERE id=? AND status=?")) {
            ps.setString(1, Status.SENDING.name());
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setLong(3, id);
            ps.setString(4, Status.PENDING.name());
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Отметить отправку как успешную.
     */
    public void markSent(long id) {
        Instant now = Instant.now();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE ib_messaging_outbox SET status=?, updated_at=?, last_error_at=NULL, last_error_code=NULL, last_error_message=NULL WHERE id=?")) {
            ps.setString(1, Status.SENT.name());
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (Exception e) {
            // no-op
        }
    }

    /**
     * Отметить ошибку и назначить следующую попытку.
     */
    public void markFailed(long id,
                           int attemptsAlready,
                           int maxAttempts,
                           Instant nextAttemptAt,
                           String errorCode,
                           String errorMessage,
                           boolean dead) {
        Instant now = Instant.now();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE ib_messaging_outbox SET status=?, updated_at=?, attempts=?, max_attempts=?, next_attempt_at=?, last_error_at=?, last_error_code=?, last_error_message=? WHERE id=?")) {
            ps.setString(1, dead ? Status.DEAD.name() : Status.PENDING.name());
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setInt(3, attemptsAlready);
            ps.setInt(4, maxAttempts);
            ps.setTimestamp(5, Timestamp.from(nextAttemptAt));
            ps.setTimestamp(6, Timestamp.from(now));
            ps.setString(7, safeShort(errorCode, 64, "SEND_ERROR"));
            ps.setString(8, safeShort(errorMessage, 1000, ""));
            ps.setLong(9, id);
            ps.executeUpdate();
        } catch (Exception e) {
            // no-op
        }
    }

    /**
     * Переотправка записи outbox.
     * <p>
     * Используется Admin API: переводит запись в {@code PENDING} и назначает ближайшую попытку.
     *
     * @param id идентификатор записи
     * @param resetAttempts сбросить счётчик попыток
     * @return true, если запись обновлена
     */
    public boolean replay(long id, boolean resetAttempts) {
        Instant now = Instant.now();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE ib_messaging_outbox SET status=?, updated_at=?, next_attempt_at=?, attempts=?, last_error_at=NULL, last_error_code=NULL, last_error_message=NULL WHERE id=?")) {
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
             PreparedStatement ps = c.prepareStatement("SELECT attempts FROM ib_messaging_outbox WHERE id=?")) {
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

    /**
     * Распарсить headers_json.
     */
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

    /**
     * Простая метрика: количество записей по статусу.
     */
    public long countByStatus(Status status) {
        if (status == null) {
            return 0;
        }
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(1) FROM ib_messaging_outbox WHERE status=?")) {
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

    private OutboxRecord mapRecord(ResultSet rs) throws Exception {
        long id = rs.getLong(1);
        String status = rs.getString(2);
        String provider = rs.getString(3);
        String dest = rs.getString(4);
        String key = rs.getString(5);
        String headersJson = rs.getString(6);
        String payloadJson = rs.getString(7);
        String sourceMessageId = rs.getString(8);
        String correlationId = rs.getString(9);
        String idemKey = rs.getString(10);
        int attempts = rs.getInt(11);
        int maxAttempts = rs.getInt(12);
        Timestamp nextTs = rs.getTimestamp(13);
        String lastCode = rs.getString(14);
        String lastMsg = rs.getString(15);
        Timestamp updated = rs.getTimestamp(16);

        return new OutboxRecord(
                id,
                status,
                provider,
                dest,
                key,
                headersJson,
                payloadJson,
                sourceMessageId,
                correlationId,
                idemKey,
                attempts,
                maxAttempts,
                nextTs == null ? null : nextTs.toInstant().toString(),
                lastCode,
                lastMsg,
                updated == null ? null : updated.toInstant().toString()
        );
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String s) {
            // Если это уже JSON-строка, оставляем как есть.
            String trimmed = s.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                return s;
            }
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"note\":\"payload не сериализован\"}";
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

    /**
     * Полная запись (включая payload_json).
     */
    @Serdeable
    public record OutboxRecord(
            long id,
            String status,
            String provider,
            String destination,
            String messageKey,
            String headersJson,
            String payloadJson,
            String sourceMessageId,
            String correlationId,
            String idempotencyKey,
            int attempts,
            int maxAttempts,
            String nextAttemptAt,
            String lastErrorCode,
            String lastErrorMessage,
            String updatedAt
    ) {
    }

    /**
     * Элемент списка (без payload).
     */
    @Serdeable
    public record OutboxListItem(
            long id,
            String status,
            String provider,
            String destination,
            int attempts,
            int maxAttempts,
            String nextAttemptAt,
            String updatedAt
    ) {
    }

}
