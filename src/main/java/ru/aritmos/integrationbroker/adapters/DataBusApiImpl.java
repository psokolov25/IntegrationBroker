package ru.aritmos.integrationbroker.adapters;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.core.FlowEngine;
import ru.aritmos.integrationbroker.databus.DataBusGroovyAdapter;

import java.util.List;
import java.util.Map;

/**
 * Реализация {@link DataBusApi} поверх текущего DataBus event-адаптера.
 */
@Singleton
@FlowEngine.GroovyExecutable("dataBus")
public class DataBusApiImpl implements DataBusApi {

    private final DataBusGroovyAdapter dataBus;

    public DataBusApiImpl(DataBusGroovyAdapter dataBus) {
        this.dataBus = dataBus;
    }

    @Override
    public Map<String, Object> publishEvent(String target,
                                            String type,
                                            String destination,
                                            Object payload,
                                            Boolean sendToOtherBus,
                                            String sourceMessageId,
                                            String correlationId,
                                            String idempotencyKey) {
        long outboxId = dataBus.publishEvent(type, destination, sendToOtherBus, payload, sourceMessageId, correlationId, idempotencyKey);
        return Map.of(
                "transport", "events",
                "outboxId", outboxId
        );
    }

    @Override
    public Map<String, Object> publishEventRoute(String target,
                                                 String destination,
                                                 String type,
                                                 List<String> dataBusUrls,
                                                 Object payload,
                                                 String sourceMessageId,
                                                 String correlationId,
                                                 String idempotencyKey) {
        long outboxId = dataBus.publishEventRoute(type, destination, dataBusUrls, payload, sourceMessageId, correlationId, idempotencyKey);
        return Map.of(
                "transport", "events.route",
                "outboxId", outboxId
        );
    }

    @Override
    public Map<String, Object> sendRequest(String target,
                                           String destination,
                                           String function,
                                           Map<String, Object> params,
                                           Boolean sendToOtherBus,
                                           String sourceMessageId,
                                           String correlationId,
                                           String idempotencyKey) {
        long outboxId = dataBus.sendRequest(function, destination, sendToOtherBus, params, sourceMessageId, correlationId, idempotencyKey);
        return Map.of(
                "transport", "requests",
                "outboxId", outboxId
        );
    }

    @Override
    public Map<String, Object> sendResponse(String target,
                                            String destination,
                                            Integer status,
                                            String message,
                                            Object response,
                                            Boolean sendToOtherBus,
                                            String sourceMessageId,
                                            String correlationId,
                                            String idempotencyKey) {
        long outboxId = dataBus.sendResponse(destination, sendToOtherBus, status, message, response, sourceMessageId, correlationId, idempotencyKey);
        return Map.of(
                "transport", "responses",
                "outboxId", outboxId
        );
    }
}
