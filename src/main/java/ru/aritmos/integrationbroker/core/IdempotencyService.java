package ru.aritmos.integrationbroker.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.model.InboundEnvelope;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Сервис идемпотентности Integration Broker.
 * <p>
 * Назначение:
 * <ul>
 *   <li>обеспечить exactly-once-like семантику на уровне интеграционного посредника;</li>
 *   <li>исключить повторную обработку при ретраях брокеров/клиентов/повторных запросах;</li>
 *   <li>поддержать DLQ replay и outbox без риска «дубликатов».</li>
 * </ul>
 * <p>
 * Статусы:
 * <ul>
 *   <li>IN_PROGRESS — обработка выполняется (или считалась выполняющейся);</li>
 *   <li>COMPLETED — обработка завершена, результат сохранён;</li>
 *   <li>FAILED — обработка завершилась ошибкой (может быть переобработана согласно правилам).</li>
 * </ul>
 * <p>
 * Важно: решение LOCKED <b>не считается poison message</b>.
 * Это нормальная ситуация конкурентной доставки или параллельных ретраев.
 */
@Singleton
public class IdempotencyService {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public IdempotencyService(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    /**
     * Решение по идемпотентности.
     */
    public enum Decision {
        /** Сообщение следует обработать. */
        PROCESS,
        /** Сообщение уже обработано успешно — обработку следует пропустить. */
        SKIP_COMPLETED,
        /** Сообщение «заблокировано» другой обработкой и не является poison. */
        LOCKED
    }

    /**
     * Статус записи идемпотентности.
     */
    public enum Status {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    /**
     * Результат принятия решения идемпотентности.
     *
     * @param idemKey ключ идемпотентности (нормализованный, SHA-256)
     * @param decision решение
     * @param existingResultJson результат (если decision=SKIP_COMPLETED и результат был сохранён)
     */
    public record IdempotencyDecision(String idemKey, Decision decision, String existingResultJson) {
    }

    /**
     * Принять решение по обработке inbound-сообщения.
     *
     * @param envelope входящее сообщение
     * @param config   настройки идемпотентности
     * @return решение
     */
    public IdempotencyDecision decide(InboundEnvelope envelope, RuntimeConfigStore.IdempotencyConfig config) {
        if (config == null || !config.enabled()) {
            return new IdempotencyDecision(null, Decision.PROCESS, null);
        }

        String idemKey = computeKey(envelope, config.strategy());
        Instant now = Instant.now();
        Instant lockUntil = now.plusSeconds(Math.max(1, config.lockTtlSec()));

        // 1) Пытаемся вставить новую запись.
        boolean inserted = tryInsert(idemKey, config.strategy().name(), Status.IN_PROGRESS, now, lockUntil);
        if (inserted) {
            return new IdempotencyDecision(idemKey, Decision.PROCESS, null);
        }

        // 2) Читаем существующую.
        Row row = getRow(idemKey);
        if (row == null) {
            // Теоретически возможно только при гонке. В этом случае безопаснее обработать.
            return new IdempotencyDecision(idemKey, Decision.PROCESS, null);
        }

        if (row.status == Status.COMPLETED) {
            return new IdempotencyDecision(idemKey, Decision.SKIP_COMPLETED, row.resultJson);
        }

        if (row.status == Status.IN_PROGRESS && row.lockUntil != null && row.lockUntil.isAfter(now)) {
            return new IdempotencyDecision(idemKey, Decision.LOCKED, null);
        }

        // 3) FAILED или истёкший IN_PROGRESS -> пробуем «захватить» обработку.
        boolean updated = tryUpdateToInProgress(idemKey, now, lockUntil);
        if (updated) {
            return new IdempotencyDecision(idemKey, Decision.PROCESS, null);
        }

        // Если не удалось — считаем LOCKED (не poison).
        return new IdempotencyDecision(idemKey, Decision.LOCKED, null);
    }

    /**
     * Отметить выполнение как успешное.
     *
     * @param idemKey ключ
     * @param result  результат выполнения (будет сохранён в JSON)
     */
    public void markCompleted(String idemKey, Object result) {
        if (idemKey == null) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            // Если сериализация результата невозможна, сохраняем минимальный маркер.
            json = "{\"note\":\"Результат не сериализован\"}";
        }

        Instant now = Instant.now();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE ib_idempotency SET status=?, updated_at=?, lock_until=?, result_json=?, last_error_code=NULL, last_error_message=NULL WHERE idem_key=?")) {
            ps.setString(1, Status.COMPLETED.name());
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setString(4, json);
            ps.setString(5, idemKey);
            ps.executeUpdate();
        } catch (Exception e) {
            // Идемпотентность не должна ломать основной сценарий.
        }
    }

    /**
     * Отметить выполнение как ошибочное.
     * <p>
     * Важно: сообщение об ошибке должно быть санитизировано (без секретов и токенов).
     *
     * @param idemKey ключ
     * @param errorCode код ошибки
     * @param errorMessage сообщение ошибки (короткое)
     */
    public void markFailed(String idemKey, String errorCode, String errorMessage) {
        if (idemKey == null) {
            return;
        }
        Instant now = Instant.now();
        String code = safeShort(SensitiveDataSanitizer.sanitizeText(errorCode), 64);
        String msg = safeShort(SensitiveDataSanitizer.sanitizeText(errorMessage), 500);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE ib_idempotency SET status=?, updated_at=?, lock_until=?, last_error_code=?, last_error_message=? WHERE idem_key=?")) {
            ps.setString(1, Status.FAILED.name());
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setString(4, code);
            ps.setString(5, msg);
            ps.setString(6, idemKey);
            ps.executeUpdate();
        } catch (Exception e) {
            // Безопасный no-op.
        }
    }

    /**
     * Получить запись идемпотентности.
     *
     * @param idemKey ключ
     * @return запись или null
     */
    public IdempotencyRecord get(String idemKey) {
        Row row = getRow(idemKey);
        if (row == null) {
            return null;
        }
        return new IdempotencyRecord(idemKey, row.status.name(), row.lockUntil == null ? null : row.lockUntil.toString(), row.updatedAt == null ? null : row.updatedAt.toString());
    }

    /**
     * Список записей идемпотентности.
     *
     * @param status фильтр статуса (может быть null)
     * @param limit лимит
     * @return список
     */
    public List<IdempotencyRecord> list(String status, int limit) {
        int lim = Math.min(Math.max(1, limit), 200);
        List<IdempotencyRecord> out = new ArrayList<>();

        String sql = (status == null || status.isBlank())
                ? "SELECT idem_key, status, lock_until, updated_at FROM ib_idempotency ORDER BY updated_at DESC LIMIT ?"
                : "SELECT idem_key, status, lock_until, updated_at FROM ib_idempotency WHERE status=? ORDER BY updated_at DESC LIMIT ?";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            int idx = 1;
            if (status != null && !status.isBlank()) {
                ps.setString(idx++, status);
            }
            ps.setInt(idx, lim);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString(1);
                    String st = rs.getString(2);
                    Timestamp lock = rs.getTimestamp(3);
                    Timestamp upd = rs.getTimestamp(4);
                    out.add(new IdempotencyRecord(key, st,
                            lock == null ? null : lock.toInstant().toString(),
                            upd == null ? null : upd.toInstant().toString()));
                }
            }
        } catch (Exception e) {
            // no-op
        }

        return out;
    }

    /**
     * Простая метрика: количество записей по статусу.
     */
    public long countByStatus(Status status) {
        Objects.requireNonNull(status, "status");
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(1) FROM ib_idempotency WHERE status=?")) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (Exception e) {
            // no-op
        }
        return 0;
    }

    private boolean tryInsert(String key, String strategy, Status status, Instant now, Instant lockUntil) {
        String sql = "INSERT INTO ib_idempotency (idem_key, strategy, status, created_at, updated_at, lock_until) VALUES (?,?,?,?,?,?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, strategy);
            ps.setString(3, status.name());
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setTimestamp(5, Timestamp.from(now));
            ps.setTimestamp(6, Timestamp.from(lockUntil));
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            // Обычно это duplicate key.
            return false;
        }
    }

    private boolean tryUpdateToInProgress(String key, Instant now, Instant lockUntil) {
        String sql = "UPDATE ib_idempotency SET status=?, updated_at=?, lock_until=? WHERE idem_key=?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, Status.IN_PROGRESS.name());
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setTimestamp(3, Timestamp.from(lockUntil));
            ps.setString(4, key);
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private Row getRow(String key) {
        String sql = "SELECT status, lock_until, result_json, updated_at FROM ib_idempotency WHERE idem_key=?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String st = rs.getString(1);
                Timestamp lock = rs.getTimestamp(2);
                String resultJson = rs.getString(3);
                Timestamp upd = rs.getTimestamp(4);
                Status status = Status.valueOf(st);
                return new Row(status,
                        lock == null ? null : lock.toInstant(),
                        resultJson,
                        upd == null ? null : upd.toInstant());
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String computeKey(InboundEnvelope envelope, RuntimeConfigStore.IdempotencyStrategy strategy) {
        String raw;
        RuntimeConfigStore.IdempotencyStrategy eff = (strategy == null) ? RuntimeConfigStore.IdempotencyStrategy.AUTO : strategy;

        switch (eff) {
            case MESSAGE_ID -> raw = nullToEmpty(envelope.messageId());
            case CORRELATION_ID -> raw = nullToEmpty(envelope.correlationId());
            case PAYLOAD_HASH -> raw = payloadHash(envelope.payload());
            case AUTO -> {
                String mid = nullToEmpty(envelope.messageId());
                if (!mid.isBlank()) {
                    raw = mid;
                } else {
                    String cid = nullToEmpty(envelope.correlationId());
                    raw = !cid.isBlank() ? cid : payloadHash(envelope.payload());
                }
            }
            default -> raw = payloadHash(envelope.payload());
        }

        String material = eff.name() + ":" + raw;
        return sha256Hex(material);
    }

    private String payloadHash(JsonNode payload) {
        try {
            if (payload == null) {
                return "";
            }
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            return sha256HexBytes(bytes);
        } catch (Exception e) {
            return "";
        }
    }

    private static String sha256Hex(String input) {
        return sha256HexBytes(input.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256HexBytes(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось вычислить SHA-256", e);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String safeShort(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        String trimmed = s.replaceAll("[\\r\\n\\t]", " ").trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen);
    }

    private record Row(Status status, Instant lockUntil, String resultJson, Instant updatedAt) {
    }

    /**
     * DTO для Admin API (минимальная форма).
     */
    public record IdempotencyRecord(
            String idemKey,
            String status,
            String lockUntil,
            String updatedAt
    ) {
    }
}
