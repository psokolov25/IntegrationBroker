package ru.aritmos.integrationbroker.adapters;

import java.util.List;
import java.util.Map;

/**
 * Высокоуровневый API взаимодействия с DataBus.
 *
 * <p>Основной канал на текущем этапе — events.
 * request/response сохраняются как расширяемый контракт на будущее.
 */
public interface DataBusApi {

    /**
     * Публикация события в DataBus.
     *
     * @param type тип события
     * @param destination получатели
     * @param payload тело события
     * @param sendToOtherBus пересылка в другие шины
     * @param sourceMessageId id входного сообщения
     * @param correlationId correlation id
     * @param idempotencyKey idempotency key
     * @return нормализованный результат (outbox/direct)
     */
    Map<String, Object> publishEvent(String target,
                                     String type,
                                     String destination,
                                     Object payload,
                                     Boolean sendToOtherBus,
                                     String sourceMessageId,
                                     String correlationId,
                                     String idempotencyKey);

    /**
     * Упрощённая публикация события (минимальный контракт для Groovy flow).
     */
    default Map<String, Object> publishEvent(String target,
                                             String destination,
                                             String type,
                                             Object payload,
                                             String correlationId) {
        return publishEvent(target, type, destination, payload, null, null, correlationId, null);
    }

    /**
     * Публикация события в DataBus с явной маршрутизацией на список внешних шин.
     */
    default Map<String, Object> publishEventRoute(String target,
                                                   String destination,
                                                   String type,
                                                   List<String> dataBusUrls,
                                                   Object payload,
                                                   String correlationId) {
        return publishEventRoute(target, destination, type, dataBusUrls, payload, null, null, correlationId, null);
    }

    /**
     * Публикация route-события с явным управлением флагом sendToOtherBus.
     */
    default Map<String, Object> publishEventRoute(String target,
                                                   String destination,
                                                   String type,
                                                   List<String> dataBusUrls,
                                                   Object payload,
                                                   Boolean sendToOtherBus,
                                                   String correlationId) {
        return publishEventRoute(target, destination, type, dataBusUrls, payload, sendToOtherBus, null, correlationId, null);
    }

    /**
     * Публикация события в DataBus с явной маршрутизацией на список внешних шин.
     */
    default Map<String, Object> publishEventRoute(String target,
                                          String destination,
                                          String type,
                                          List<String> dataBusUrls,
                                          Object payload,
                                          String sourceMessageId,
                                          String correlationId,
                                          String idempotencyKey) {
        return publishEventRoute(target, destination, type, dataBusUrls, payload, null, sourceMessageId, correlationId, idempotencyKey);
    }

    Map<String, Object> publishEventRoute(String target,
                                          String destination,
                                          String type,
                                          List<String> dataBusUrls,
                                          Object payload,
                                          Boolean sendToOtherBus,
                                          String sourceMessageId,
                                          String correlationId,
                                          String idempotencyKey);

    /**
     * Прототип request-вызова (не основной путь на текущем этапе).
     */
    default Map<String, Object> sendRequest(String target, String destination, String function, Map<String, Object> params, String correlationId) {
        return sendRequest(target, destination, function, params, null, null, correlationId, null);
    }

    /**
     * Прототип request-вызова (не основной путь на текущем этапе).
     */
    default Map<String, Object> sendRequest(String target, String destination, String function, Map<String, Object> params) {
        return sendRequest(target, destination, function, params, null, null, null, null);
    }

    /**
     * Прототип request-вызова с метаданными трассировки.
     */
    Map<String, Object> sendRequest(String target,
                                    String destination,
                                    String function,
                                    Map<String, Object> params,
                                    Boolean sendToOtherBus,
                                    String sourceMessageId,
                                    String correlationId,
                                    String idempotencyKey);

    /**
     * Прототип response-вызова (не основной путь на текущем этапе).
     */
    default Map<String, Object> sendResponse(String target,
                                             String destination,
                                             Integer status,
                                             String message,
                                             Object response,
                                             String correlationId) {
        return sendResponse(target, destination, status, message, response, null, null, correlationId, null);
    }

    /**
     * Прототип response-вызова (не основной путь на текущем этапе).
     */
    default Map<String, Object> sendResponse(String target,
                                             String destination,
                                             Integer status,
                                             String message,
                                             Object response) {
        return sendResponse(target, destination, status, message, response, null, null, null, null);
    }

    /**
     * Прототип response-вызова с метаданными трассировки.
     */
    Map<String, Object> sendResponse(String target,
                                     String destination,
                                     Integer status,
                                     String message,
                                     Object response,
                                     Boolean sendToOtherBus,
                                     String sourceMessageId,
                                     String correlationId,
                                     String idempotencyKey);
}
