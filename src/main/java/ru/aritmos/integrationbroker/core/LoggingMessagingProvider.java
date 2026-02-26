package ru.aritmos.integrationbroker.core;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Провайдер "logging" — безопасная заглушка для разработки.
 * <p>
 * Он не требует внешнего брокера и позволяет:
 * <ul>
 *   <li>протестировать работу outbox/ретраев/статусов;</li>
 *   <li>оставить Groovy-flow и ядро функциональными в закрытой среде без Kafka/Rabbit/NATS.</li>
 * </ul>
 * <p>
 * Важно: в лог не выводятся токены/секреты. Заголовки уже проходят санитизацию,
 * но здесь всё равно применяется дополнительная защита.
 */
@Singleton
public class LoggingMessagingProvider implements MessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingMessagingProvider.class);

    @Override
    public String id() {
        return "logging";
    }

    @Override
    public SendResult send(OutboundMessage message) {
        String dest = message == null ? "?" : message.destination();
        String corr = message == null ? null : message.correlationId();
        String mid = message == null ? null : message.sourceMessageId();

        Map<String, String> headers = message == null ? Map.of() : message.headers();
        Map<String, String> safeHeaders = SensitiveDataSanitizer.sanitizeHeaders(headers);

        log.info("[OUTBOX][MSG][LOGGING] destination={} correlationId={} messageId={} headersKeys={} payloadSize={}",
                dest,
                corr,
                mid,
                safeHeaders.keySet(),
                message == null || message.payloadJson() == null ? 0 : message.payloadJson().length());

        return SendResult.ok();
    }
}
