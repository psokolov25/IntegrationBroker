package ru.aritmos.integrationbroker.crm;

import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.util.Map;

/**
 * Типизированный клиент CRM.
 * <p>
 * Реальные интеграции (Bitrix24/amoCRM/RetailCRM/Мегаплан и т.д.) должны реализовывать этот интерфейс.
 * <p>
 * Важно:
 * <ul>
 *   <li>клиент не должен логировать секреты и токены;</li>
 *   <li>секреты должны храниться только в runtime-конфиге (restConnectors + crm.settings),
 *       а не в outbox/DLQ.</li>
 * </ul>
 */
public interface CrmClient {

    /**
     * Профиль CRM, который обслуживает клиент.
     */
    RuntimeConfigStore.CrmProfile profile();

    /**
     * Поиск клиента по одному или нескольким ключам.
     */
    CrmModels.CrmOutcome<CrmModels.CustomerCard> findCustomer(RuntimeConfigStore.RuntimeConfig cfg,
                                                              CrmModels.FindCustomerRequest request,
                                                              Map<String, Object> meta);

    /**
     * Создание или обновление клиента.
     */
    CrmModels.CrmOutcome<CrmModels.CustomerCard> upsertCustomer(RuntimeConfigStore.RuntimeConfig cfg,
                                                                CrmModels.UpsertCustomerRequest request,
                                                                Map<String, Object> meta);

    /**
     * Создание или обновление компании.
     * <p>
     * Для каркаса возвращается ошибка NOT_IMPLEMENTED.
     */
    default CrmModels.CrmOutcome<Map<String, Object>> upsertCompany(RuntimeConfigStore.RuntimeConfig cfg,
                                                                    Map<String, Object> request,
                                                                    Map<String, Object> meta) {
        return CrmModels.CrmOutcome.fail("NOT_IMPLEMENTED", "Операция upsertCompany не реализована в каркасе CRM-адаптера", Map.of());
    }

    /**
     * Создание лида.
     */
    CrmModels.CrmOutcome<CrmModels.LeadRef> createLead(RuntimeConfigStore.RuntimeConfig cfg,
                                                       CrmModels.CreateLeadRequest request,
                                                       Map<String, Object> meta);

    /**
     * Создание задачи.
     */
    CrmModels.CrmOutcome<CrmModels.TaskRef> createTask(RuntimeConfigStore.RuntimeConfig cfg,
                                                       CrmModels.CreateTaskRequest request,
                                                       Map<String, Object> meta);

    /**
     * Добавление заметки/комментария.
     */
    CrmModels.CrmOutcome<Map<String, Object>> appendNote(RuntimeConfigStore.RuntimeConfig cfg,
                                                         CrmModels.AppendNoteRequest request,
                                                         Map<String, Object> meta);

    /**
     * Создать сервисное обращение.
     */
    CrmModels.CrmOutcome<CrmModels.ServiceCaseRef> createServiceCase(RuntimeConfigStore.RuntimeConfig cfg,
                                                                     CrmModels.CreateServiceCaseRequest request,
                                                                     Map<String, Object> meta);

    /**
     * Комплексная операция: найти/создать клиента и создать сервисное обращение.
     * <p>
     * В типовой интеграции это может означать: поиск по идентификаторам, затем upsert карточки,
     * затем создание обращения/кейса, привязанного к клиенту.
     */
    CrmModels.CrmOutcome<CrmModels.SyncCustomerAndCreateCaseResult> syncCustomerAndCreateCase(RuntimeConfigStore.RuntimeConfig cfg,
                                                                                              CrmModels.SyncCustomerAndCreateCaseRequest request,
                                                                                              Map<String, Object> meta);
}
