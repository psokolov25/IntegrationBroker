package ru.aritmos.integrationbroker.adapters;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.core.FlowEngine;
import ru.aritmos.integrationbroker.databus.DataBusGroovyAdapter;

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
    public Map<String, Object> publishEvent(String target, String type, String destination, Object payload, Boolean sendToOtherBus) {
        long outboxId = dataBus.publishEvent(type, destination, sendToOtherBus, payload);
        return Map.of(
                "transport", "events",
                "outboxId", outboxId
        );
    }

    @Override
    public Map<String, Object> sendRequest(String target, String function, String destination, Map<String, Object> params) {
        throw new UnsupportedOperationException("DataBus request/response пока прототип: используйте events");
    }

    @Override
    public Map<String, Object> sendResponse(String target, String destination, Integer status, String message, Object response) {
        throw new UnsupportedOperationException("DataBus request/response пока прототип: используйте events");
    }
}
