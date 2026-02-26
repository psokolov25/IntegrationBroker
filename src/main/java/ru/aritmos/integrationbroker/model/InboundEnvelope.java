package ru.aritmos.integrationbroker.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Единая нормализованная модель входящего сообщения для Integration Broker.
 * <p>
 * Любой inbound-канал (REST/Kafka/RabbitMQ/NATS/JetStream и т.д.) обязан приводить вход к данному контракту.
 * Это ключевой архитектурный инвариант, позволяющий:
 * <ul>
 *   <li>унифицировать idempotency/outbox/DLQ/replay;</li>
 *   <li>выбирать flow по единым правилам;</li>
 *   <li>обеспечивать эксплуатационную предсказуемость интеграции в закрытых контурах.</li>
 * </ul>
 */
@Serdeable
@Introspected
@Schema(name = "InboundEnvelope", description = "Единый конверт входящего события/команды для Integration Broker")
public record InboundEnvelope(
        @Schema(description = "Тип сообщения: событие или команда", requiredMode = Schema.RequiredMode.REQUIRED)
        Kind kind,

        @Schema(description = "Тип сообщения (например: visit.created, ticket.call.requested)", requiredMode = Schema.RequiredMode.REQUIRED)
        String type,

        @Schema(description = "Полезная нагрузка (JSON)", requiredMode = Schema.RequiredMode.REQUIRED)
        JsonNode payload,

        @Schema(description = "Нормализованные заголовки/атрибуты (без секретов)")
        Map<String, String> headers,

        @Schema(description = "Уникальный идентификатор сообщения (желательно глобально уникальный)")
        String messageId,

        @Schema(description = "Идентификатор корреляции для сквозных сценариев")
        String correlationId,

        @Schema(description = "Идентификатор отделения/филиала")
        String branchId,

        @Schema(description = "Идентификатор пользователя/оператора (если применимо)")
        String userId,

        @Schema(description = "Служебные поля источника (channel/source/partition/offset и т.п.)")
        Map<String, Object> sourceMeta
) {

    /**
     * Вариант типа входящего сообщения.
     */
    public enum Kind {
        /** Событие доменной/интеграционной модели. */
        EVENT,
        /** Команда на выполнение действия. */
        COMMAND
    }

    /**
     * Безопасное получение значения заголовка.
     * <p>
     * Важно: заголовки могут содержать чувствительные данные.
     * На уровне Integration Broker следует пробрасывать только нормализованные и санитизированные значения.
     *
     * @param name имя заголовка
     * @return значение или {@code null}
     */
    public String header(String name) {
        if (headers == null || name == null) {
            return null;
        }
        return headers.get(name);
    }
}
