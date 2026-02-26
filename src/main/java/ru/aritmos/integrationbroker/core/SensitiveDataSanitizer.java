package ru.aritmos.integrationbroker.core;

import java.util.LinkedHashMap;
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
     * Маска для скрытия чувствительных значений.
     */
    private static final String MASK = "***";

    /**
     * Санитизировать карту заголовков для хранения (например, в DLQ).
     * <p>
     * Политика:
     * <ul>
     *   <li>ключи сохраняются;</li>
     *   <li>значения чувствительных ключей заменяются на {@value #MASK};</li>
     *   <li>для остальных значений выполняется лёгкая эвристика (Bearer -> маска).</li>
     * </ul>
     *
     * @param headers исходные заголовки
     * @return санитизированные заголовки (новая карта)
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
     * Санитизировать текст (сообщения об ошибках, диагностические строки).
     * <p>
     * Эвристика:
     * <ul>
     *   <li>маскируем Bearer-токены;</li>
     *   <li>маскируем client_secret=... и похожие паттерны.</li>
     * </ul>
     *
     * @param text исходный текст
     * @return санитизированный текст
     */
    public static String sanitizeText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String t = text;

        // Bearer <token>
        t = t.replaceAll("(?i)bearer\\s+[^\\s]+", "Bearer " + MASK);

        // Параметры формата key=value (минимальная эвристика)
        t = t.replaceAll("(?i)(client_secret|access_token|refresh_token)\\s*=\\s*[^\\s&]+", "$1=" + MASK);

        // Избегаем многострочности в сообщениях.
        t = t.replaceAll("[\\r\\n\\t]", " ").trim();
        return t;
    }
}
