package ru.aritmos.integrationbroker.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Санитайзер чувствительных данных.
 * <p>
 * Назначение:
 * <ul>
 *   <li>защитить логи и DLQ от случайного хранения/вывода токенов, cookies и секретов;</li>
 *   <li>обеспечить повторяемую и единообразную политику «маскирования».</li>
 * </ul>
 * <p>
 * Важно:
 * <ul>
 *   <li>санитайзер не является DLP-системой и работает эвристически;</li>
 *   <li>payload по умолчанию не модифицируется, т.к. нужен для replay (поэтому критично не логировать payload).</li>
 * </ul>
 */
public final class SensitiveDataSanitizer {

    private SensitiveDataSanitizer() {
    }

    /**
     * Заголовки/поля, которые нельзя хранить или логировать в сыром виде.
     * <p>
     * Список расширяемый — при добавлении новых интеграций следует дополнять.
     */
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "x-authorization",
            "x-auth-token",
            "x-access-token",
            "access_token",
            "refresh_token",
            "client_secret"
    );

    /**
     * Заголовки, которые не должны проксироваться в admin-driven proxy/replay операциях.
     */
    private static final Set<String> ADMIN_PROXY_DENY_HEADERS = Set.of(
            "host",
            "content-length",
            "connection",
            "transfer-encoding",
            "te",
            "trailer",
            "upgrade",
            "proxy-authorization",
            "proxy-authenticate",
            "forwarded",
            "via",
            "x-forwarded-for",
            "x-forwarded-host",
            "x-forwarded-port",
            "x-forwarded-proto"
    );

    /**
     * Маска для скрытия чувствительных значений.
     */
    private static final String MASK = "***";

    /**
     * Санитизировать карту заголовков для хранения (например, в DLQ).
     */
    public static Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }

        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k == null) {
                continue;
            }

            String keyNorm = k.toLowerCase(Locale.ROOT).trim();
            if (FORBIDDEN_KEYS.contains(keyNorm)) {
                out.put(k, MASK);
                continue;
            }

            out.put(k, sanitizeText(v));
        }
        return out;
    }

    /**
     * Санитизировать заголовки для admin proxy/replay вызовов:
     * <ul>
     *   <li>удаляет hop-by-hop и forward-заголовки из deny-list;</li>
     *   <li>остальные значения прогоняет через обычную sanitize-политику.</li>
     * </ul>
     */
    public static Map<String, String> sanitizeHeadersForAdminProxy(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sanitized = sanitizeHeaders(headers);
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : sanitized.entrySet()) {
            String key = e.getKey();
            if (key == null) {
                continue;
            }
            String normalized = key.toLowerCase(Locale.ROOT).trim();
            if (ADMIN_PROXY_DENY_HEADERS.contains(normalized)) {
                continue;
            }
            out.put(key, e.getValue());
        }
        return out;
    }

    /**
     * Санитизировать вложенный объект (Map/List/scalar) для debug-preview.
     * <p>
     * Вложенные blob-поля и массивы бинарных данных маскируются.
     */
    public static Object sanitizeStructuredData(Object value) {
        return sanitizeStructuredData(value, null, 0);
    }

    private static Object sanitizeStructuredData(Object value, String keyHint, int depth) {
        if (value == null) {
            return null;
        }
        if (depth > 32) {
            return MASK;
        }

        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                String key = String.valueOf(e.getKey());
                out.put(key, sanitizeStructuredData(e.getValue(), key, depth + 1));
            }
            return out;
        }

        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(sanitizeStructuredData(item, keyHint, depth + 1));
            }
            return out;
        }

        if (value instanceof byte[]) {
            return MASK;
        }

        if (value instanceof String s) {
            if (looksLikeBinaryKey(keyHint) && looksLikeBinaryValue(s)) {
                return MASK;
            }
            return sanitizeText(s);
        }

        return value;
    }

    private static boolean looksLikeBinaryKey(String keyHint) {
        if (keyHint == null || keyHint.isBlank()) {
            return false;
        }
        String k = keyHint.toLowerCase(Locale.ROOT);
        return k.contains("blob")
                || k.contains("binary")
                || k.contains("bytes")
                || k.contains("base64")
                || k.equals("data")
                || k.endsWith("_data");
    }

    private static boolean looksLikeBinaryValue(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim();
        if (v.length() < 24) {
            return false;
        }
        if (!v.matches("^[A-Za-z0-9+/=\\r\\n]+$")) {
            return false;
        }
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(v.replaceAll("\\s+", ""));
            return decoded.length >= 24 || !new String(decoded, StandardCharsets.UTF_8).chars().allMatch(ch -> ch >= 32 && ch <= 126);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Санитизировать текст (сообщения об ошибках, диагностические строки).
     */
    public static String sanitizeText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String t = text;

        // Bearer <token>
        t = t.replaceAll("(?i)bearer\\s+[^\\s]+", "Bearer " + MASK);

        // Параметры формата key=value (минимальная эвристика)
        t = t.replaceAll("(?i)(client_secret|access_token|refresh_token|sid)\\s*=\\s*[^\\s&;]+", "$1=" + MASK);

        // Cookie: sid=...
        t = t.replaceAll("(?i)(cookie\\s*:\\s*[^\\r\\n]*?sid)\\s*=\\s*[^\\s;]+", "$1=" + MASK);

        // Избегаем многострочности в сообщениях.
        t = t.replaceAll("[\\r\\n\\t]", " ").trim();
        return t;
    }

}
