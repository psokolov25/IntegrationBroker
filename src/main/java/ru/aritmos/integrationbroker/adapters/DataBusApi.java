package ru.aritmos.integrationbroker.adapters;

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
     * @return нормализованный результат (outbox/direct)
     */
    Map<String, Object> publishEvent(String target, String type, String destination, Object payload, Boolean sendToOtherBus);

    /**
     * Прототип request-вызова (не основной путь на текущем этапе).
     */
    Map<String, Object> sendRequest(String target, String function, String destination, Map<String, Object> params);

    /**
     * Прототип response-вызова (не основной путь на текущем этапе).
     */
    Map<String, Object> sendResponse(String target, String destination, Integer status, String message, Object response);
}
