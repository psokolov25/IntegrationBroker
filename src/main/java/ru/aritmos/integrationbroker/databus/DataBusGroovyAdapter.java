package ru.aritmos.integrationbroker.databus;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.FlowEngine;
import ru.aritmos.integrationbroker.core.SensitiveDataSanitizer;
import ru.aritmos.integrationbroker.core.RestOutboxService;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Groovy-адаптер для публикации событий в DataBus.
 *
 * <p>Экспортируется в Groovy как переменная {@code bus}.
 *
 * <p>DataBus в референсе принимает события через REST endpoint:
 * {@code POST /databus/events/types/{type}} и требует заголовки:
 * <ul>
 *   <li>{@code Service-Destination} — целевой сервис (или список через запятую, или {@code *});</li>
 *   <li>{@code Send-To-OtherBus} — пересылать ли на другие шины;</li>
 *   <li>{@code Send-Date} — дата RFC1123;</li>
 *   <li>{@code Service-Sender} — имя сервиса-отправителя.</li>
 * </ul>
 *
 * <p>Важно: данный адаптер не хранит и не логирует секреты.
 * Авторизация берётся из {@code restConnectors} по {@code dataBus.connectorId}.
 */
@Singleton
@FlowEngine.GroovyExecutable("bus")
public class DataBusGroovyAdapter {

    private static final DateTimeFormatter RFC1123 = DateTimeFormatter.RFC_1123_DATE_TIME;

    private final RuntimeConfigStore configStore;
    private final RestOutboxService restOutboxService;

    public DataBusGroovyAdapter(RuntimeConfigStore configStore, RestOutboxService restOutboxService) {
        this.configStore = configStore;
        this.restOutboxService = restOutboxService;
    }

    /**
     * Публикация события в DataBus.
     *
     * <p>Типовой сценарий: отправить событие {@code VISIT_CREATE} в VisitManager:
     * <pre>
     * {@code
     * bus.publishEvent('VISIT_CREATE', 'visitmanager', [branchId:'...', entryPointId:'1', serviceIds:['...'], parameters:[:]])
     * }
     * </pre>
     *
     * @param type тип события (например, VISIT_CREATE)
     * @param destination целевые сервисы (имя/список/*)
     * @param body произвольное JSON-тело события
     * @return id записи REST outbox (0, если выполнен прямой вызов)
     */
    public long publishEvent(String type, String destination, Object body) {
        return publishEvent(type, destination, null, body);
    }

    /**
     * Публикация события с явным флагом пересылки на другие шины.
     *
     * @param type тип события
     * @param destination destination services
     * @param sendToOtherBus если null — используется дефолт из конфига
     * @param body тело
     * @return id outbox
     */
    public long publishEvent(String type, String destination, Boolean sendToOtherBus, Object body) {
        RuntimeConfigStore.RuntimeConfig eff = configStore.getEffective();
        RuntimeConfigStore.DataBusIntegrationConfig cfg = eff == null ? null : eff.dataBus();
        if (cfg == null || !cfg.enabled()) {
            throw new IllegalStateException("DataBus интеграция отключена (dataBus.enabled=false)");
        }

        boolean forward = sendToOtherBus != null ? sendToOtherBus : cfg.defaultSendToOtherBus();

        String path = cfg.publishEventPathTemplate() == null ? "/databus/events/types/{type}" : cfg.publishEventPathTemplate();
        path = path.replace("{type}", safe(type, "UNKNOWN"));

        Map<String, String> headers = new HashMap<>();
        headers.put(cfg.destinationHeaderName(), safe(destination, "*"));
        headers.put(cfg.sendToOtherBusHeaderName(), String.valueOf(forward));
        headers.put(cfg.sendDateHeaderName(), RFC1123.format(ZonedDateTime.now(ZoneId.of("GMT"))));
        headers.put(cfg.senderHeaderName(), safe(cfg.defaultSenderServiceName(), "integration-broker"));

        // Idempotency-Key для DataBus обычно не требуется: события маршрутизируются дальше.
        // Если понадобится — заказчик может добавить свой заголовок через Groovy.
        return restOutboxService.callViaConnector(
                eff,
                eff.restOutbox(),
                cfg.connectorId(),
                "POST",
                path,
                SensitiveDataSanitizer.sanitizeHeaders(headers),
                body,
                null,
                null,
                null,
                null
        );
    }

    private static String safe(String v, String def) {
        if (v == null) {
            return def;
        }
        String t = v.trim();
        return t.isEmpty() ? def : t;
    }
}
