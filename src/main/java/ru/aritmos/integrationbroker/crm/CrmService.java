package ru.aritmos.integrationbroker.crm;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.util.Map;

/**
 * Сервис CRM-операций.
 * <p>
 * Отвечает за:
 * <ul>
 *   <li>проверку включения CRM слоя;</li>
 *   <li>выбор активного профиля CRM и нужной реализации {@link CrmClient};</li>
 *   <li>единый формат ошибок и безопасное поведение.</li>
 * </ul>
 */
@Singleton
public class CrmService {

    private final RuntimeConfigStore configStore;
    private final CrmClientRegistry registry;

    public CrmService(RuntimeConfigStore configStore, CrmClientRegistry registry) {
        this.configStore = configStore;
        this.registry = registry;
    }

    /**
     * Поиск клиента в CRM.
     */
    public CrmModels.CrmOutcome<CrmModels.CustomerCard> findCustomer(CrmModels.FindCustomerRequest request, Map<String, Object> meta) {
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        RuntimeConfigStore.CrmConfig crm = cfg.crm();
        if (crm == null || !crm.enabled()) {
            return CrmModels.CrmOutcome.disabled();
        }
        CrmClient client = registry.get(crm.profile());
        return client.findCustomer(cfg, normalizeFind(request), meta == null ? Map.of() : meta);
    }

    /**
     * Upsert клиента.
     */
    public CrmModels.CrmOutcome<CrmModels.CustomerCard> upsertCustomer(CrmModels.UpsertCustomerRequest request, Map<String, Object> meta) {
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        RuntimeConfigStore.CrmConfig crm = cfg.crm();
        if (crm == null || !crm.enabled()) {
            return CrmModels.CrmOutcome.disabled();
        }
        CrmClient client = registry.get(crm.profile());
        return client.upsertCustomer(cfg, request, meta == null ? Map.of() : meta);
    }

    /**
     * Создание лида.
     */
    public CrmModels.CrmOutcome<CrmModels.LeadRef> createLead(CrmModels.CreateLeadRequest request, Map<String, Object> meta) {
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        RuntimeConfigStore.CrmConfig crm = cfg.crm();
        if (crm == null || !crm.enabled()) {
            return CrmModels.CrmOutcome.disabled();
        }
        CrmClient client = registry.get(crm.profile());
        return client.createLead(cfg, request, meta == null ? Map.of() : meta);
    }

    /**
     * Создание задачи.
     */
    public CrmModels.CrmOutcome<CrmModels.TaskRef> createTask(CrmModels.CreateTaskRequest request, Map<String, Object> meta) {
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        RuntimeConfigStore.CrmConfig crm = cfg.crm();
        if (crm == null || !crm.enabled()) {
            return CrmModels.CrmOutcome.disabled();
        }
        CrmClient client = registry.get(crm.profile());
        return client.createTask(cfg, request, meta == null ? Map.of() : meta);
    }

    /**
     * Добавить заметку.
     */
    public CrmModels.CrmOutcome<Map<String, Object>> appendNote(CrmModels.AppendNoteRequest request, Map<String, Object> meta) {
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        RuntimeConfigStore.CrmConfig crm = cfg.crm();
        if (crm == null || !crm.enabled()) {
            return CrmModels.CrmOutcome.disabled();
        }
        CrmClient client = registry.get(crm.profile());
        return client.appendNote(cfg, request, meta == null ? Map.of() : meta);
    }

    /**
     * Создать обращение.
     */
    public CrmModels.CrmOutcome<CrmModels.ServiceCaseRef> createServiceCase(CrmModels.CreateServiceCaseRequest request, Map<String, Object> meta) {
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        RuntimeConfigStore.CrmConfig crm = cfg.crm();
        if (crm == null || !crm.enabled()) {
            return CrmModels.CrmOutcome.disabled();
        }
        CrmClient client = registry.get(crm.profile());
        return client.createServiceCase(cfg, request, meta == null ? Map.of() : meta);
    }

    /**
     * Комплексная операция: синхронизация клиента и создание обращения.
     */
    public CrmModels.CrmOutcome<CrmModels.SyncCustomerAndCreateCaseResult> syncCustomerAndCreateCase(CrmModels.SyncCustomerAndCreateCaseRequest request,
                                                                                                    Map<String, Object> meta) {
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        RuntimeConfigStore.CrmConfig crm = cfg.crm();
        if (crm == null || !crm.enabled()) {
            return CrmModels.CrmOutcome.disabled();
        }
        CrmClient client = registry.get(crm.profile());
        return client.syncCustomerAndCreateCase(cfg, request, meta == null ? Map.of() : meta);
    }

    private static CrmModels.FindCustomerRequest normalizeFind(CrmModels.FindCustomerRequest r) {
        if (r == null) {
            return new CrmModels.FindCustomerRequest(java.util.List.of(), Map.of(), CrmModels.ResolvePolicy.defaultPolicy());
        }
        return new CrmModels.FindCustomerRequest(
                r.keys() == null ? java.util.List.of() : r.keys(),
                r.context() == null ? Map.of() : r.context(),
                r.policy() == null ? CrmModels.ResolvePolicy.defaultPolicy() : r.policy()
        );
    }
}
