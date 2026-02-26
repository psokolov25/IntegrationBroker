package ru.aritmos.integrationbroker.core;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.model.InboundEnvelope;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enrichment пользователя через KeycloakProxy.
 * <p>
 * Требования безопасности:
 * <ul>
 *   <li>нельзя хранить и логировать сырые токены;</li>
 *   <li>в качестве ключа кэша для token-mode используется SHA-256(token);</li>
 *   <li>из ответа удаляются поля access_token/refresh_token и подобные (если включено stripTokensFromResponse).</li>
 * </ul>
 * <p>
 * Enrichment является опциональным. При {@code critical=false} ошибки enrichment не валят обработку.
 */
@Singleton
public class KeycloakProxyEnrichmentService {

    private static final List<String> TOKEN_KEYS = List.of(
            "access_token",
            "refresh_token",
            "token",
            "client_secret",
            "authorization"
    );

    private final KeycloakProxyClient client;
    private final TtlCache<String, Map<String, Object>> cache;

    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();

    public KeycloakProxyEnrichmentService(KeycloakProxyClient client) {
        this.client = client;
        // Значения maxEntries/ttl берём из runtime-config, но кэш как структура создаётся один раз.
        this.cache = new TtlCache<>(java.time.Clock.systemUTC(), 5000);
    }

    /**
     * Выполнить enrichment (если включено) и дополнить meta.
     *
     * @param envelope исходный inbound
     * @param cfg effective-конфигурация
     * @param meta карта meta (будет дополнена полями user/principal/userId/branchId)
     * @return обогащённый envelope (возможна автоподстановка branchId/userId)
     */
    public InboundEnvelope enrichIfEnabled(InboundEnvelope envelope,
                                          RuntimeConfigStore.RuntimeConfig cfg,
                                          Map<String, Object> meta) {
        RuntimeConfigStore.KeycloakProxyEnrichmentConfig kc = (cfg == null) ? null : cfg.keycloakProxy();
        if (kc == null || !kc.enabled()) {
            return envelope;
        }

        List<RuntimeConfigStore.KeycloakProxyFetchMode> modes = (kc.modes() == null || kc.modes().isEmpty())
                ? List.of(RuntimeConfigStore.KeycloakProxyFetchMode.USER_ID_HEADER)
                : kc.modes();

        String userIdCandidate = firstNonBlank(
                envelope == null ? null : envelope.userId(),
                header(envelope, kc.userIdHeaderName())
        );

        String tokenCandidate = extractBearerToken(header(envelope, kc.tokenHeaderName()));

        try {
            for (RuntimeConfigStore.KeycloakProxyFetchMode mode : modes) {
                Optional<Map<String, Object>> userOpt = Optional.empty();

                if (mode == RuntimeConfigStore.KeycloakProxyFetchMode.USER_ID_HEADER) {
                    if (userIdCandidate == null || userIdCandidate.isBlank()) {
                        continue;
                    }
                    String cacheKey = "uid:" + userIdCandidate;
                    userOpt = cache.get(cacheKey);
                    if (userOpt.isPresent()) {
                        cacheHits.incrementAndGet();
                    } else {
                        cacheMisses.incrementAndGet();
                        userOpt = client.fetchUserById(cfg, userIdCandidate);
                        userOpt = userOpt.map(u -> sanitizeUser(u, kc.stripTokensFromResponse()));
                        userOpt.ifPresent(u -> cache.put(cacheKey, u, kc.cacheTtlSeconds()));
                    }
                }

                if (mode == RuntimeConfigStore.KeycloakProxyFetchMode.BEARER_TOKEN) {
                    if (tokenCandidate == null || tokenCandidate.isBlank()) {
                        continue;
                    }
                    String tokenHash = sha256Hex(tokenCandidate);
                    String cacheKey = "tok:" + tokenHash;
                    userOpt = cache.get(cacheKey);
                    if (userOpt.isPresent()) {
                        cacheHits.incrementAndGet();
                    } else {
                        cacheMisses.incrementAndGet();
                        userOpt = client.fetchUserByToken(cfg, tokenCandidate);
                        userOpt = userOpt.map(u -> sanitizeUser(u, kc.stripTokensFromResponse()));
                        userOpt.ifPresent(u -> cache.put(cacheKey, u, kc.cacheTtlSeconds()));
                    }
                }

                if (userOpt.isPresent()) {
                    Map<String, Object> user = userOpt.get();
                    applyToMeta(meta, user, userIdCandidate);
                    InboundEnvelope updated = autoFillEnvelope(envelope, kc, meta, user, userIdCandidate);
                    return updated;
                }
            }
            return envelope;
        } catch (Exception e) {
            errors.incrementAndGet();
            if (kc.critical()) {
                throw new IllegalStateException("Ошибка enrichment пользователя через KeycloakProxy", e);
            }
            return envelope;
        }
    }

    /**
     * @return число попаданий в кэш enrichment
     */
    public long cacheHits() {
        return cacheHits.get();
    }

    /**
     * @return число промахов кэша enrichment
     */
    public long cacheMisses() {
        return cacheMisses.get();
    }

    /**
     * @return число ошибок enrichment
     */
    public long errors() {
        return errors.get();
    }

    private String header(InboundEnvelope envelope, String name) {
        if (envelope == null || envelope.headers() == null || name == null) {
            return null;
        }
        return envelope.headers().get(name);
    }

    private void applyToMeta(Map<String, Object> meta, Map<String, Object> user, String userIdCandidate) {
        if (meta == null) {
            return;
        }
        meta.put("user", user);

        // principal: сначала username/login, затем id, затем userIdCandidate.
        String principal = firstNonBlank(
                toStr(user.get("username")),
                toStr(user.get("login")),
                toStr(user.get("id")),
                userIdCandidate
        );
        if (principal != null) {
            meta.put("principal", principal);
        }

        if (meta.get("userId") == null) {
            String uid = firstNonBlank(toStr(user.get("id")), toStr(user.get("username")), userIdCandidate);
            if (uid != null) {
                meta.put("userId", uid);
            }
        }
    }

    private InboundEnvelope autoFillEnvelope(InboundEnvelope original,
                                             RuntimeConfigStore.KeycloakProxyEnrichmentConfig kc,
                                             Map<String, Object> meta,
                                             Map<String, Object> user,
                                             String userIdCandidate) {
        if (original == null) {
            return null;
        }

        String branchId = original.branchId();
        String uid = original.userId();

        if ((uid == null || uid.isBlank()) && userIdCandidate != null && !userIdCandidate.isBlank()) {
            uid = userIdCandidate;
        }

        if ((branchId == null || branchId.isBlank()) && kc.autoBranchIdFromUser()) {
            String extracted = extractBranchId(user, kc.branchIdAttributeKeys());
            if (extracted != null && !extracted.isBlank()) {
                branchId = extracted;
                if (meta != null) {
                    meta.put("branchId", branchId);
                }
            }
        }

        if (equalsSafe(branchId, original.branchId()) && equalsSafe(uid, original.userId())) {
            return original;
        }

        return new InboundEnvelope(
                original.kind(),
                original.type(),
                original.payload(),
                original.headers(),
                original.messageId(),
                original.correlationId(),
                branchId,
                uid,
                original.sourceMeta()
        );
    }

    private String extractBranchId(Map<String, Object> user, List<String> keys) {
        if (user == null) {
            return null;
        }
        List<String> k = (keys == null || keys.isEmpty()) ? List.of("branchId") : keys;

        // 1) Прямое поле branchId (если есть)
        for (String kk : k) {
            Object v = user.get(kk);
            String s = toStr(v);
            if (s != null && !s.isBlank()) {
                return s;
            }
        }

        // 2) Keycloak attributes: Map<String, List<String>>
        Object attrs = user.get("attributes");
        if (attrs instanceof Map<?, ?> am) {
            for (String kk : k) {
                Object v = am.get(kk);
                if (v instanceof List<?> list && !list.isEmpty()) {
                    String s = toStr(list.get(0));
                    if (s != null && !s.isBlank()) {
                        return s;
                    }
                }
            }
        }
        return null;
    }

    private Map<String, Object> sanitizeUser(Map<String, Object> raw, boolean stripTokens) {
        if (raw == null) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>(raw);

        if (stripTokens) {
            for (String key : TOKEN_KEYS) {
                out.remove(key);
                out.remove(key.toUpperCase());
            }
        }
        return out;
    }

    private String extractBearerToken(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String v = headerValue.trim();
        if (v.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return v.substring(7).trim();
        }
        // допускаем, что токен могут передать без префикса
        return v;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "sha256_error";
        }
    }

    private String toStr(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private boolean equalsSafe(String a, String b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }
}
