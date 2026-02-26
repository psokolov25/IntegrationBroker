package ru.aritmos.integrationbroker.core;

import java.util.Map;

/**
 * Провайдер отправки сообщений во внешний брокер.
 * <p>
 * Это расширяемая точка системы: добавление нового брокера выполняется через реализацию
 * данного интерфейса без ломки API ядра.
 * <p>
 * На текущей итерации поставляется базовый провайдер {@code logging}, который
 * имитирует отправку и пишет безопасный лог (без токенов/секретов).
 */
public interface MessagingProvider {

    /**
     * Идентификатор провайдера.
     * <p>
     * Примеры: {@code kafka}, {@code rabbitmq}, {@code nats}, {@code mqtt}, {@code redis-streams}, {@code logging}.
     *
     * @return строковый id
     */
    String id();

    /**
     * Отправить сообщение.
     *
     * @param message сообщение
     * @return результат отправки
     */
    SendResult send(OutboundMessage message);

    /**
     * Проверка доступности провайдера.
     * <p>
     * По умолчанию считается, что провайдер доступен.
     * Реальные провайдеры (Kafka/RabbitMQ/NATS и т.д.) должны переопределять метод,
     * выполняя лёгкую проверку подключения (без отправки бизнес-сообщений).
     *
     * @return результат health-check
     */
    default HealthStatus healthCheck() {
        return HealthStatus.ok();
    }

    /**
     * Нормализованное сообщение, которое готово к отправке во внешний брокер.
     * <p>
     * Payload хранится как JSON-строка: типизированные адаптеры и провайдеры
     * сами решают, как и во что её преобразовывать.
     */
    record OutboundMessage(
            String destination,
            String messageKey,
            Map<String, String> headers,
            String payloadJson,
            String correlationId,
            String sourceMessageId,
            String idempotencyKey
    ) {
    }

    /**
     * Результат отправки.
     */
    record SendResult(boolean success, String errorCode, String errorMessage) {

        /**
         * Успешная отправка.
         */
        public static SendResult ok() {
            return new SendResult(true, null, null);
        }

        /**
         * Ошибка отправки.
         *
         * @param code код (короткий)
         * @param message сообщение (без секретов)
         */
        public static SendResult fail(String code, String message) {
            return new SendResult(false,
                    SensitiveDataSanitizer.sanitizeText(code),
                    SensitiveDataSanitizer.sanitizeText(message));
        }
    }

    /**
     * Результат проверки доступности провайдера.
     * <p>
     * Сообщение должно быть безопасным: без секретов/токенов.
     */
    record HealthStatus(boolean ok, String message) {

        /**
         * Провайдер доступен.
         */
        public static HealthStatus ok() {
            return new HealthStatus(true, null);
        }

        /**
         * Провайдер недоступен.
         *
         * @param message диагностическое сообщение (без секретов)
         */
        public static HealthStatus fail(String message) {
            return new HealthStatus(false, SensitiveDataSanitizer.sanitizeText(message));
        }
    }
}
