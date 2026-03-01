package ru.aritmos.integrationbroker.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.model.InboundEnvelope;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Сервис Inbound DLQ (Dead Letter Queue) для Integration Broker.
 * <p>
 * Назначение:
 * <ul>
 *   <li>сохранять входящие сообщения, которые не удалось обработать (flow/runtime);</li>
 *   <li>позволять эксплуатационно просматривать и повторно запускать обработку (replay);</li>
 *   <li>фиксировать количество попыток и причины падений.</li>
 * </ul>
 * <p>
 * Важно:
 * <ul>
 *   <li>санитизация заголовков выполняется перед записью (не храним токены/секреты);</li>
 *   <li>payload хранится как есть (для replay), поэтому его нельзя логировать.</li>
 * </ul>
 */
@Singleton
public class InboundDlqService {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public InboundDlqService(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    /**
     * Статусы DLQ.
     */
    public enum Status {
        /** Сообщение ожидает replay (или повторных попыток). */
        PENDING,
        /** Сообщение успешно переиграно (или уже было обработано ранее и подтверждено). */
        REPLAYED,
        /** Сообщение признано «мертвым» по лимиту попыток. */
        DEAD
    }

    /**
     * Запись DLQ для Admin API.
     */
    public record DlqRecord(
            long id,
            String status,
            String createdAt,
            String updatedAt,
            String kind,
            String type,
            String messageId,
            String correlationId,
            String branchId,
            String userId,
            String idempotencyKey,
            int attempts,
            int maxAttempts,
            String lastErrorAt,
            String errorCode,
            String errorMessage,
            String replayedAt
    ) {
    }

    /**
     * Полная запись DLQ, включая исходный envelope (для replay).
     */
    public record DlqFull(
            DlqRecord record,
            Map<String, String> headers,
            JsonNode payload,
            Map<String, Object> sourceMeta,
            String replayResultJson
    ) {
    }

    /**
     * Сохранить сообщение в DLQ.
     *
     * @param envelope входящий конверт
     * @param idemKey ключ идемпотентности (может быть null)
     * @param errorCode код ошибки
     * @param errorMessage сообщение (санитизированное и укороченное)
     * @param maxAttempts максимальное число попыток replay
     * @param sanitizeHeaders включить санитизацию заголовков
     * @return id записи DLQ
     */
    public long put(InboundEnvelope envelope,
                    String idemKey,
                    String errorCode,
                    String errorMessage,
                    int maxAttempts,
                    boolean sanitizeHeaders) {

        Instant now = Instant.now();
        Map<String, String> headers = sanitizeHeaders
                ? SensitiveDataSanitizer.sanitizeHeaders(envelope.headers())
                : (envelope.headers() == null ? Map.of() : envelope.headers());

        String headersJson = toJsonSafe(headers);
        String payloadJson = toJsonSafe(envelope.payload());
        String sourceMetaJson = toJsonSafe(envelope.sourceMeta());

        int maxAtt = Math.min(Math.max(1, maxAttempts), 100);

        String sql = "INSERT INTO ib_inbound_dlq (status, created_at, updated_at, kind, type, message_id, correlation_id, branch_id, user_id, headers_json, payload_json, source_meta_json, idem_key, attempts, max_attempts, last_error_at, error_code, error_message) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, Status.PENDING.name());
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setString(4, envelope.kind() == null ? "UNKNOWN" : envelope.kind().name());
            ps.setString(5, envelope.type());
            ps.setString(6, envelope.messageId());
            ps.setString(7, envelope.correlationId());
            ps.setString(8, envelope.branchId());
            ps.setString(9, envelope.userId());
            ps.setString(10, headersJson);
            ps.setString(11, payloadJson);
            ps.setString(12, sourceMetaJson);
            ps.setString(13, idemKey);
            ps.setInt(14, 0);
            ps.setInt(15, maxAtt);
            ps.setTimestamp(16, Timestamp.from(now));
            ps.setString(17, safeShort(errorCode, 64));
            ps.setString(18, safeShort(errorMessage, 800));
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (Exception e) {
            // DLQ не должен «убивать» основной путь обработки.
        }
        return -1;
    }

    /**
     * Получить запись DLQ (кратко).
     */
    public DlqRecord get(long id) {
        DlqFull full = getFull(id);
        return full == null ? null : full.record();
    }

    /**
     * Получить запись DLQ полностью (для replay).
     */
    public DlqFull getFull(long id) {
        String sql = "SELECT status, created_at, updated_at, kind, type, message_id, correlation_id, branch_id, user_id, idem_key, attempts, max_attempts, last_error_at, error_code, error_message, replayed_at, headers_json, payload_json, source_meta_json, replay_result_json " +
                "FROM ib_inbound_dlq WHERE id=?";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                String status = rs.getString(1);
                Timestamp createdAt = rs.getTimestamp(2);
                Timestamp updatedAt = rs.getTimestamp(3);
                String kind = rs.getString(4);
                String type = rs.getString(5);
                String messageId = rs.getString(6);
                String correlationId = rs.getString(7);
                String branchId = rs.getString(8);
                String userId = rs.getString(9);
                String idemKey = rs.getString(10);
                int attempts = rs.getInt(11);
                int maxAttempts = rs.getInt(12);
                Timestamp lastErrorAt = rs.getTimestamp(13);
                String errorCode = rs.getString(14);
                String errorMessage = rs.getString(15);
                Timestamp replayedAt = rs.getTimestamp(16);

                String headersJson = rs.getString(17);
                String payloadJson = rs.getString(18);
                String sourceMetaJson = rs.getString(19);
                String replayResultJson = rs.getString(20);

                DlqRecord record = new DlqRecord(
                        id,
                        status,
                        createdAt == null ? null : createdAt.toInstant().toString(),
                        updatedAt == null ? null : updatedAt.toInstant().toString(),
                        kind,
                        type,
                        messageId,
                        correlationId,
                        branchId,
                        userId,
                        idemKey,
                        attempts,
                        maxAttempts,
                        lastErrorAt == null ? null : lastErrorAt.toInstant().toString(),
                        errorCode,
                        errorMessage,
                        replayedAt == null ? null : replayedAt.toInstant().toString()
                );

                Map<String, String> headers = fromJsonSafe(headersJson, new TypeReference<Map<String, String>>() {
                });
                JsonNode payload = fromJsonSafe(payloadJson, JsonNode.class);
                Map<String, Object> sourceMeta = fromJsonSafe(sourceMetaJson, new TypeReference<Map<String, Object>>() {
                });

                return new DlqFull(record, headers, payload, sourceMeta, replayResultJson);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Список DLQ.
     */
    public List<DlqRecord> list(String status, int limit) {
        return list(status, null, null, null, limit);
    }

    /**
     * Список DLQ с фильтрацией для batch replay.
     * <p>
     * Фильтры:
     * <ul>
     *   <li>status — статус записи (PENDING/REPLAYED/DEAD);</li>
     *   <li>type — тип входящего сообщения;</li>
     *   <li>branchId — отделение;</li>
     *   <li>source — источник из sourceMeta (source/sourceSystem/system).</li>
     * </ul>
     */
    public List<DlqRecord> list(String status, String type, String source, String branchId, int limit) {
        int lim = Math.min(Math.max(1, limit), 200);
        boolean filter = status != null && !status.isBlank();
        boolean filterType = type != null && !type.isBlank();
        boolean filterBranch = branchId != null && !branchId.isBlank();
        boolean filterSource = source != null && !source.isBlank();

        StringBuilder sql = new StringBuilder("SELECT id, status, created_at, updated_at, kind, type, message_id, correlation_id, branch_id, user_id, idem_key, attempts, max_attempts, last_error_at, error_code, error_message, replayed_at, source_meta_json FROM ib_inbound_dlq");
        List<String> conditions = new ArrayList<>();
        if (filter) {
            conditions.add("status=?");
        }
        if (filterType) {
            conditions.add("type=?");
        }
        if (filterBranch) {
            conditions.add("branch_id=?");
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        sql.append(" ORDER BY updated_at DESC LIMIT ?");

        List<DlqRecord> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {

            int idx = 1;
            if (filter) {
                ps.setString(idx++, status);
            }
            if (filterType) {
                ps.setString(idx++, type);
            }
            if (filterBranch) {
                ps.setString(idx++, branchId);
            }
            ps.setInt(idx, lim);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    String st = rs.getString(2);
                    Timestamp createdAt = rs.getTimestamp(3);
                    Timestamp updatedAt = rs.getTimestamp(4);
                    String kind = rs.getString(5);
                    String recType = rs.getString(6);
                    String messageId = rs.getString(7);
                    String correlationId = rs.getString(8);
                    String recBranchId = rs.getString(9);
                    String userId = rs.getString(10);
                    String idemKey = rs.getString(11);
                    int attempts = rs.getInt(12);
                    int maxAttempts = rs.getInt(13);
                    Timestamp lastErrorAt = rs.getTimestamp(14);
                    String errorCode = rs.getString(15);
                    String errorMessage = rs.getString(16);
                    Timestamp replayedAt = rs.getTimestamp(17);
                    String sourceMetaJson = rs.getString(18);

                    if (filterSource && !Objects.equals(source, extractSource(sourceMetaJson))) {
                        continue;
                    }

                    out.add(new DlqRecord(
                            id, st,
                            createdAt == null ? null : createdAt.toInstant().toString(),
                            updatedAt == null ? null : updatedAt.toInstant().toString(),
                            kind, recType,
                            messageId, correlationId,
                            recBranchId, userId,
                            idemKey,
                            attempts, maxAttempts,
                            lastErrorAt == null ? null : lastErrorAt.toInstant().toString(),
                            errorCode, errorMessage,
                            replayedAt == null ? null : replayedAt.toInstant().toString()
                    ));
                }
            }
        } catch (Exception e) {
            return List.of();
        }

        return out;
    }

    private String extractSource(String sourceMetaJson) {
        Map<String, Object> sourceMeta = fromJsonSafe(sourceMetaJson, new TypeReference<Map<String, Object>>() {
        });
        if (sourceMeta == null || sourceMeta.isEmpty()) {
            return null;
        }
        Object source = sourceMeta.get("source");
        if (source == null) {
            source = sourceMeta.get("sourceSystem");
        }
        if (source == null) {
            source = sourceMeta.get("system");
        }
        return source == null ? null : String.valueOf(source);
    }

    /**
     * Зафиксировать неуспешную попытку replay.
     * <p>
     * Политика:
     * <ul>
     *   <li>attempts увеличивается на 1;</li>
     *   <li>если достигнут maxAttempts — статус становится DEAD;</li>
     *   <li>иначе статус остаётся PENDING.</li>
     * </ul>
     */
    public void markReplayFailed(long id, String errorCode, String errorMessage) {
        DlqRecord rec = get(id);
        if (rec == null) {
            return;
        }

        int nextAttempts = rec.attempts() + 1;
        boolean dead = nextAttempts >= rec.maxAttempts();
        Instant now = Instant.now();

        String sql = "UPDATE ib_inbound_dlq SET status=?, updated_at=?, last_error_at=?, attempts=?, error_code=?, error_message=? WHERE id=?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, dead ? Status.DEAD.name() : Status.PENDING.name());
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setInt(4, nextAttempts);
            ps.setString(5, safeShort(errorCode, 64));
            ps.setString(6, safeShort(errorMessage, 800));
            ps.setLong(7, id);
            ps.executeUpdate();
        } catch (Exception e) {
            // no-op
        }
    }

    /**
     * Зафиксировать успешный replay.
     */
    public void markReplayed(long id, String replayResultJson) {
        Instant now = Instant.now();
        String sql = "UPDATE ib_inbound_dlq SET status=?, updated_at=?, replayed_at=?, replay_result_json=? WHERE id=?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, Status.REPLAYED.name());
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setString(4, safeShort(replayResultJson, 50_000));
            ps.setLong(5, id);
            ps.executeUpdate();
        } catch (Exception e) {
            // no-op
        }
    }

    /**
     * Метрика: количество DLQ записей по статусу.
     */
    public long countByStatus(Status status) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(1) FROM ib_inbound_dlq WHERE status=?")) {
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

    private String toJsonSafe(Object o) {
        try {
            if (o == null) {
                return null;
            }
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> T fromJsonSafe(String json, Class<T> type) {
        try {
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> T fromJsonSafe(String json, TypeReference<T> type) {
        try {
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
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
}
