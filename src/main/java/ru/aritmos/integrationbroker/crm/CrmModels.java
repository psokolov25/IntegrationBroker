package ru.aritmos.integrationbroker.crm;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Модели CRM-слоя Integration Broker.
 * <p>
 * CRM-слой отвечает за типизированные операции с внешними CRM (Bitrix24/amoCRM/RetailCRM/Мегаплан и т.д.).
 * Важно: CRM может использоваться как backend-источник для идентификации, но слой идентификации (identity)
 * остаётся самостоятельным и не «сшивается» жёстко с CRM.
 * <p>
 * Принципиально:
 * <ul>
 *   <li>контракты расширяемы: новые поля и профили CRM должны добавляться без ломки ядра;</li>
 *   <li>в outbox/DLQ не должны попадать секреты/токены/сырые персональные данные сверх необходимого.</li>
 * </ul>
 */
public final class CrmModels {

    private CrmModels() {
        // утилитарный класс
    }

    /**
     * Универсальный результат CRM-операции.
     * <p>
     * Поле {@code raw} предназначено только для диагностики и не должно содержать секретов.
     */
    public record CrmOutcome<T>(
            boolean success,
            T result,
            String errorCode,
            String errorMessage,
            Map<String, Object> raw
    ) {
        public static <T> CrmOutcome<T> ok(T result, Map<String, Object> raw) {
            return new CrmOutcome<>(true, result, null, null, raw == null ? Map.of() : raw);
        }

        public static <T> CrmOutcome<T> fail(String errorCode, String errorMessage, Map<String, Object> raw) {
            return new CrmOutcome<>(false, null, errorCode, errorMessage, raw == null ? Map.of() : raw);
        }

        public static <T> CrmOutcome<T> disabled() {
            return fail("DISABLED", "CRM слой отключён настройкой runtime-config.crm.enabled=false", Map.of());
        }
    }

    /**
     * Ключ для поиска клиента в CRM.
     * <p>
     * Схема не ограничена фиксированным списком: используется {@code type + value + attributes}.
     */
    public record LookupKey(
            String type,
            String value,
            Map<String, Object> attributes
    ) {
    }

    /**
     * Политика поиска/разрешения в CRM.
     */
    public record ResolvePolicy(
            List<String> preferredTypes,
            boolean stopOnAnyMatch
    ) {
        public static ResolvePolicy defaultPolicy() {
            return new ResolvePolicy(List.of(), true);
        }
    }

    /**
     * Запрос на поиск клиента.
     */
    public record FindCustomerRequest(
            List<LookupKey> keys,
            Map<String, Object> context,
            ResolvePolicy policy
    ) {
    }

    /**
     * Нормализованная карточка клиента CRM.
     * <p>
     * {@code segmentAlias} может быть в "сыром" виде (алиас из CRM); нормализация сегмента выполняется в identity.
     */
    public record CustomerCard(
            String crmCustomerId,
            String fullName,
            String segmentAlias,
            Map<String, String> externalIds,
            Map<String, Object> attributes
    ) {
    }

    /**
     * Запрос на upsert клиента.
     */
    public record UpsertCustomerRequest(
            CustomerCard customer,
            Map<String, Object> context
    ) {
    }

    /**
     * Запрос на создание лида.
     */
    public record CreateLeadRequest(
            String title,
            String customerCrmId,
            Map<String, Object> attributes
    ) {
    }

    /**
     * Ссылка на лид.
     */
    public record LeadRef(
            String leadId,
            String status,
            Instant createdAt,
            Map<String, Object> attributes
    ) {
    }

    /**
     * Запрос на создание задачи.
     */
    public record CreateTaskRequest(
            String title,
            String description,
            String assignee,
            String customerCrmId,
            Map<String, Object> attributes
    ) {
    }

    /**
     * Ссылка на задачу.
     */
    public record TaskRef(
            String taskId,
            String status,
            Instant createdAt,
            Map<String, Object> attributes
    ) {
    }

    /**
     * Запрос на добавление заметки/комментария.
     */
    public record AppendNoteRequest(
            String entityType,
            String entityId,
            String text,
            Map<String, Object> attributes
    ) {
    }

    /**
     * Запрос на создание сервисного обращения.
     */
    public record CreateServiceCaseRequest(
            String title,
            String customerCrmId,
            String channel,
            Map<String, Object> attributes
    ) {
    }

    /**
     * Ссылка на сервисное обращение.
     */
    public record ServiceCaseRef(
            String caseId,
            String status,
            Instant createdAt,
            Map<String, Object> attributes
    ) {
    }

    /**
     * Комплексная операция: найти/создать клиента и создать обращение.
     */
    public record SyncCustomerAndCreateCaseRequest(
            FindCustomerRequest find,
            UpsertCustomerRequest upsert,
            CreateServiceCaseRequest serviceCase
    ) {
    }

    /**
     * Результат комплексной операции.
     */
    public record SyncCustomerAndCreateCaseResult(
            CustomerCard customer,
            ServiceCaseRef serviceCase,
            Map<String, Object> diagnostics
    ) {
    }
}
