package ru.aritmos.integrationbroker.adapters;

import java.util.List;
import java.util.LinkedHashMap;
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
     * Публикация канонического события VISIT_CREATE для асинхронной интеграции с VisitManager.
     */
    default Map<String, Object> publishVisitCreate(String target,
                                                   String destination,
                                                   String branchId,
                                                   String entryPointId,
                                                   List<String> serviceIds,
                                                   Map<String, String> parameters,
                                                   boolean printTicket,
                                                   String segmentationRuleId,
                                                   String sourceMessageId,
                                                   String correlationId,
                                                   String idempotencyKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("branchId", branchId);
        payload.put("entryPointId", entryPointId);
        payload.put("serviceIds", serviceIds == null ? List.of() : List.copyOf(serviceIds));
        payload.put("parameters", parameters == null ? Map.of() : Map.copyOf(parameters));
        payload.put("printTicket", printTicket);
        payload.put("segmentationRuleId", segmentationRuleId);
        return publishEvent(target, "VISIT_CREATE", destination, payload, null, sourceMessageId, correlationId, idempotencyKey);
    }


    /**
     * Публикация route-события VISIT_CREATE с каноническим payload.
     */
    default Map<String, Object> publishVisitCreateRoute(String target,
                                                        String destination,
                                                        List<String> dataBusUrls,
                                                        String branchId,
                                                        String entryPointId,
                                                        List<String> serviceIds,
                                                        Map<String, String> parameters,
                                                        boolean printTicket,
                                                        String segmentationRuleId,
                                                        Boolean sendToOtherBus,
                                                        String sourceMessageId,
                                                        String correlationId,
                                                        String idempotencyKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("branchId", branchId);
        payload.put("entryPointId", entryPointId);
        payload.put("serviceIds", serviceIds == null ? List.of() : List.copyOf(serviceIds));
        payload.put("parameters", parameters == null ? Map.of() : Map.copyOf(parameters));
        payload.put("printTicket", printTicket);
        payload.put("segmentationRuleId", segmentationRuleId);
        return publishEventRoute(target,
                destination,
                "VISIT_CREATE",
                dataBusUrls,
                payload,
                sendToOtherBus,
                sourceMessageId,
                correlationId,
                idempotencyKey);
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
     * Упрощённый успешный response (status=200, message=OK).
     */
    default Map<String, Object> sendResponseOk(String target,
                                               String destination,
                                               Object response,
                                               String sourceMessageId,
                                               String correlationId,
                                               String idempotencyKey) {
        return sendResponse(target, destination, 200, "OK", response, null, sourceMessageId, correlationId, idempotencyKey);
    }

    /**
     * Упрощённый error response (status=500).
     */
    default Map<String, Object> sendResponseError(String target,
                                                  String destination,
                                                  Integer status,
                                                  String message,
                                                  Object response,
                                                  String sourceMessageId,
                                                  String correlationId,
                                                  String idempotencyKey) {
        Integer code = status == null ? 500 : status;
        String msg = (message == null || message.isBlank()) ? "ERROR" : message;
        return sendResponse(target, destination, code, msg, response, null, sourceMessageId, correlationId, idempotencyKey);
    }
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
