package ru.aritmos.integrationbroker.crm;

import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.GroovyObjectSupport;
import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.core.FlowEngine;

import java.util.HashMap;
import java.util.Map;

/**
 * Groovy-адаптер для доступа к CRM-слою из flow.
 * <p>
 * Экспортируется в Binding под alias {@code crm}.
 * <p>
 * Пример использования в Groovy:
 * <pre>
 * {@code
 * def req = [keys: [[type:'phone', value:'+79990000001']]]
 * def res = crm.findCustomer(req, meta)
 * if (res.success()) {
 *   output.crmCustomerId = res.result().crmCustomerId()
 * }
 * }
 * </pre>
 */
@Singleton
@FlowEngine.GroovyExecutable("crm")
public class CrmGroovyAdapter extends GroovyObjectSupport {

    private final CrmService crmService;
    private final ObjectMapper objectMapper;

    public CrmGroovyAdapter(CrmService crmService, ObjectMapper objectMapper) {
        this.crmService = crmService;
        this.objectMapper = objectMapper;
    }

    /**
     * Поиск клиента.
     *
     * @param request Map/JSON или типизированный {@link CrmModels.FindCustomerRequest}
     * @return результат
     */
    public CrmModels.CrmOutcome<CrmModels.CustomerCard> findCustomer(Object request) {
        return findCustomer(request, Map.of());
    }

    /**
     * Поиск клиента с передачей meta/context ядра.
     * <p>
     * Это полезно, если CRM-интеграция использует branchId/userId/channel для маршрутизации или аудита.
     */
    public CrmModels.CrmOutcome<CrmModels.CustomerCard> findCustomer(Object request, Object meta) {
        CrmModels.FindCustomerRequest req = convert(request, CrmModels.FindCustomerRequest.class,
                "Некорректный запрос findCustomer: ожидается Map/JSON с полями keys/context/policy");
        return crmService.findCustomer(req, metaMap(meta));
    }

    /**
     * Упрощённый helper: поиск клиента по набору keys без сборки полного request вручную.
     */
    public CrmModels.CrmOutcome<CrmModels.CustomerCard> findCustomerByKeys(Object keys, Object meta) {
        java.util.Map<String, Object> req = new java.util.HashMap<>();
        req.put("keys", keys);
        return findCustomer(req, meta);
    }

    /**
     * Upsert клиента.
     */
    public CrmModels.CrmOutcome<CrmModels.CustomerCard> upsertCustomer(Object request) {
        return upsertCustomer(request, Map.of());
    }

    public CrmModels.CrmOutcome<CrmModels.CustomerCard> upsertCustomer(Object request, Object meta) {
        CrmModels.UpsertCustomerRequest req = convert(request, CrmModels.UpsertCustomerRequest.class,
                "Некорректный запрос upsertCustomer: ожидается Map/JSON с полями customer/context");
        return crmService.upsertCustomer(req, metaMap(meta));
    }

    /**
     * Создание лида.
     */
    public CrmModels.CrmOutcome<CrmModels.LeadRef> createLead(Object request) {
        return createLead(request, Map.of());
    }

    public CrmModels.CrmOutcome<CrmModels.LeadRef> createLead(Object request, Object meta) {
        CrmModels.CreateLeadRequest req = convert(request, CrmModels.CreateLeadRequest.class,
                "Некорректный запрос createLead: ожидается Map/JSON с полями title/customerCrmId/attributes");
        return crmService.createLead(req, metaMap(meta));
    }

    /**
     * Создание задачи.
     */
    public CrmModels.CrmOutcome<CrmModels.TaskRef> createTask(Object request) {
        return createTask(request, Map.of());
    }

    public CrmModels.CrmOutcome<CrmModels.TaskRef> createTask(Object request, Object meta) {
        CrmModels.CreateTaskRequest req = convert(request, CrmModels.CreateTaskRequest.class,
                "Некорректный запрос createTask: ожидается Map/JSON с полями title/description/assignee/customerCrmId");
        return crmService.createTask(req, metaMap(meta));
    }

    /**
     * Добавление заметки.
     */
    public CrmModels.CrmOutcome<Map<String, Object>> appendNote(Object request) {
        return appendNote(request, Map.of());
    }

    public CrmModels.CrmOutcome<Map<String, Object>> appendNote(Object request, Object meta) {
        CrmModels.AppendNoteRequest req = convert(request, CrmModels.AppendNoteRequest.class,
                "Некорректный запрос appendNote: ожидается Map/JSON с полями entityType/entityId/text");
        return crmService.appendNote(req, metaMap(meta));
    }

    /**
     * Упрощённый helper: создать сервисное обращение по базовым полям.
     */
    public CrmModels.CrmOutcome<CrmModels.ServiceCaseRef> createServiceCaseSimple(String title,
                                                                                   String customerCrmId,
                                                                                   String channel,
                                                                                   Object meta) {
        java.util.Map<String, Object> req = new java.util.HashMap<>();
        req.put("title", title);
        req.put("customerCrmId", customerCrmId);
        req.put("channel", channel);
        return createServiceCase(req, meta);
    }

    /**
     * Создание сервисного обращения.
     */
    public CrmModels.CrmOutcome<CrmModels.ServiceCaseRef> createServiceCase(Object request) {
        return createServiceCase(request, Map.of());
    }

    public CrmModels.CrmOutcome<CrmModels.ServiceCaseRef> createServiceCase(Object request, Object meta) {
        CrmModels.CreateServiceCaseRequest req = convert(request, CrmModels.CreateServiceCaseRequest.class,
                "Некорректный запрос createServiceCase: ожидается Map/JSON с полями title/customerCrmId/channel");
        return crmService.createServiceCase(req, metaMap(meta));
    }

    /**
     * Комплексная операция: синхронизация клиента и создание обращения.
     */
    public CrmModels.CrmOutcome<CrmModels.SyncCustomerAndCreateCaseResult> syncCustomerAndCreateCase(Object request) {
        return syncCustomerAndCreateCase(request, Map.of());
    }

    public CrmModels.CrmOutcome<CrmModels.SyncCustomerAndCreateCaseResult> syncCustomerAndCreateCase(Object request, Object meta) {
        CrmModels.SyncCustomerAndCreateCaseRequest req = convert(request, CrmModels.SyncCustomerAndCreateCaseRequest.class,
                "Некорректный запрос syncCustomerAndCreateCase: ожидается Map/JSON с полями find/upsert/serviceCase");
        return crmService.syncCustomerAndCreateCase(req, metaMap(meta));
    }

    private <T> T convert(Object raw, Class<T> clazz, String message) {
        if (raw == null) {
            throw new IllegalArgumentException(message);
        }
        if (clazz.isInstance(raw)) {
            return clazz.cast(raw);
        }
        try {
            return objectMapper.convertValue(raw, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Нормализовать meta (часто приходит как Map из Groovy), приводя ключи к строкам.
     */
    private Map<String, Object> metaMap(Object meta) {
        if (meta == null) {
            return Map.of();
        }
        if (meta instanceof Map<?, ?> m) {
            Map<String, Object> out = new HashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            return Map.copyOf(out);
        }
        return Map.of();
    }
}
