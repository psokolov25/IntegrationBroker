package ru.aritmos.integrationbroker.core;

import java.util.Map;

/**
 * Отправка REST-запросов для REST outbox.
 * <p>
 * Вынесено в интерфейс, чтобы:
 * <ul>
 *   <li>легко подменять реализацию (например, Micronaut HTTP client с TLS/MTLS/прокси);</li>
 *   <li>тестировать диспетчер без реальных сетевых вызовов.</li>
 * </ul>
 */
public interface RestOutboundSender {

    /**
     * Выполнить HTTP-запрос.
     *
     * @param method HTTP-метод
     * @param url целевой URL
     * @param headers заголовки (уже санитизированные для хранения, но сюда можно передавать реальные заголовки)
     * @param bodyJson тело (JSON-строка) или null
     * @param idempotencyHeaderName имя заголовка идемпотентности (например, {@code Idempotency-Key})
     * @param idempotencyKey значение ключа идемпотентности или null
     * @return результат
     */
    Result send(String method,
                String url,
                Map<String, String> headers,
                String bodyJson,
                String idempotencyHeaderName,
                String idempotencyKey);

    /**
     * Результат HTTP-вызова.
     */
    record Result(boolean success, int httpStatus, String errorCode, String errorMessage) {

        public static Result ok(int status) {
            return new Result(true, status, null, null);
        }

        public static Result fail(String code, String message, int status) {
            return new Result(false,
                    status,
                    SensitiveDataSanitizer.sanitizeText(code),
                    SensitiveDataSanitizer.sanitizeText(message));
        }
    }
}
