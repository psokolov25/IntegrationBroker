package ru.aritmos.integrationbroker.crm;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Набор базовых реализаций CRM-клиентов.
 * <p>
 * На данной итерации реальные интеграции не реализуются полностью (требуют конкретных API/версий и
 * проектных параметров), однако каркас и типизированные контракты готовы.
 * <p>
 * Поведение по умолчанию:
 * <ul>
 *   <li>для профиля GENERIC реализован детерминированный stub (для демо и тестов);</li>
 *   <li>для остальных профилей возвращается NOT_IMPLEMENTED с пояснением.</li>
 * </ul>
 */
public final class CrmClients {

    private CrmClients() {
        // утилитарный класс
    }

    /**
     * Базовый каркас для не реализованных профилей.
     */
    private abstract static class AbstractNotImplementedCrmClient implements CrmClient {

        protected final RuntimeConfigStore.CrmProfile profile;

        protected AbstractNotImplementedCrmClient(RuntimeConfigStore.CrmProfile profile) {
            this.profile = profile;
        }

        @Override
        public RuntimeConfigStore.CrmProfile profile() {
            return profile;
        }

        @Override
        public CrmModels.CrmOutcome<CrmModels.CustomerCard> findCustomer(RuntimeConfigStore.RuntimeConfig cfg, CrmModels.FindCustomerRequest request, Map<String, Object> meta) {
            return CrmModels.CrmOutcome.fail("NOT_IMPLEMENTED", "CRM профиль " + profile + " подключён как каркас. Реализуйте интеграцию через CrmClient.", Map.of("profile", profile.name()));
        }

        @Override
        public CrmModels.CrmOutcome<CrmModels.CustomerCard> upsertCustomer(RuntimeConfigStore.RuntimeConfig cfg, CrmModels.UpsertCustomerRequest request, Map<String, Object> meta) {
            return CrmModels.CrmOutcome.fail("NOT_IMPLEMENTED", "Операция upsertCustomer не реализована для профиля " + profile, Map.of("profile", profile.name()));
        }

        @Override
        public CrmModels.CrmOutcome<CrmModels.LeadRef> createLead(RuntimeConfigStore.RuntimeConfig cfg, CrmModels.CreateLeadRequest request, Map<String, Object> meta) {
            return CrmModels.CrmOutcome.fail("NOT_IMPLEMENTED", "Операция createLead не реализована для профиля " + profile, Map.of("profile", profile.name()));
        }

        @Override
        public CrmModels.CrmOutcome<CrmModels.TaskRef> createTask(RuntimeConfigStore.RuntimeConfig cfg, CrmModels.CreateTaskRequest request, Map<String, Object> meta) {
            return CrmModels.CrmOutcome.fail("NOT_IMPLEMENTED", "Операция createTask не реализована для профиля " + profile, Map.of("profile", profile.name()));
        }

        @Override
        public CrmModels.CrmOutcome<Map<String, Object>> appendNote(RuntimeConfigStore.RuntimeConfig cfg, CrmModels.AppendNoteRequest request, Map<String, Object> meta) {
            return CrmModels.CrmOutcome.fail("NOT_IMPLEMENTED", "Операция appendNote не реализована для профиля " + profile, Map.of("profile", profile.name()));
        }

        @Override
        public CrmModels.CrmOutcome<CrmModels.ServiceCaseRef> createServiceCase(RuntimeConfigStore.RuntimeConfig cfg, CrmModels.CreateServiceCaseRequest request, Map<String, Object> meta) {
            return CrmModels.CrmOutcome.fail("NOT_IMPLEMENTED", "Операция createServiceCase не реализована для профиля " + profile, Map.of("profile", profile.name()));
        }

        @Override
        public CrmModels.CrmOutcome<CrmModels.SyncCustomerAndCreateCaseResult> syncCustomerAndCreateCase(RuntimeConfigStore.RuntimeConfig cfg, CrmModels.SyncCustomerAndCreateCaseRequest request, Map<String, Object> meta) {
            return CrmModels.CrmOutcome.fail("NOT_IMPLEMENTED", "Операция syncCustomerAndCreateCase не реализована для профиля " + profile, Map.of("profile", profile.name()));
        }
    }

    /**
     * GENERIC CRM клиент (stub) для демо, тестов и безопасного baseline.
     * <p>
     * Возвращает предопределённого клиента при совпадении ключей (phone/email) с известными значениями.
     * Это нужно, чтобы показать работу typed CRM слоя без внешних зависимостей.
     */
    @Singleton
    public static class GenericCrmClient implements CrmClient {

        @Override
        public RuntimeConfigStore.CrmProfile profile() {
            return RuntimeConfigStore.CrmProfile.GENERIC;
        }

        @Override
        public CrmModels.CrmOutcome<CrmModels.CustomerCard> findCustomer(RuntimeConfigStore.RuntimeConfig cfg, CrmModels.FindCustomerRequest request, Map<String, Object> meta) {
            if (request == null || request.keys() == null || request.keys().isEmpty()) {
                return CrmModels.CrmOutcome.fail("BAD_REQUEST", "Не задан ни один ключ поиска клиента (keys пуст)", Map.of());
            }

            for (CrmModels.LookupKey k : request.keys()) {
                if (k == null || isBlank(k.type()) || isBlank(k.value())) {
                    continue;
                }
                String t = k.type().trim();
                String v = k.value().trim();

                // Детерминированный демо-кейс: соответствует examples и тестам.
                if (t.equalsIgnoreCase("phone") && v.equals("+79990000001")) {
                    return CrmModels.CrmOutcome.ok(demoCustomer(), Map.of("matchedBy", "phone"));
                }
                if (t.equalsIgnoreCase("email") && v.equalsIgnoreCase("test@example.local")) {
                    return CrmModels.CrmOutcome.ok(demoCustomer(), Map.of("matchedBy", "email"));
                }
            }

            return CrmModels.CrmOutcome.fail("NOT_FOUND", "Клиент в CRM не найден по заданным ключам", Map.of());
        }

        @Override
        public CrmModels.CrmOutcome<CrmModels.CustomerCard> upsertCustomer(RuntimeConfigStore.RuntimeConfig cfg, CrmModels.UpsertCustomerRequest request, Map<String, Object> meta) {
            if (request == null || request.customer() == null) {
                return CrmModels.CrmOutcome.fail("BAD_REQUEST", "Не задан объект customer для upsert", Map.of());
            }
            // Для baseline возвращаем то, что прислали, добавив фиктивный id.
            CrmModels.CustomerCard in = request.customer();
            String id = isBlank(in.crmCustomerId()) ? "CRM-NEW-001" : in.crmCustomerId();

            Map<String, Object> attrs = new HashMap<>();
            if (in.attributes() != null) {
                attrs.putAll(in.attributes());
            }
            attrs.putIfAbsent("upserted", true);

            CrmModels.CustomerCard out = new CrmModels.CustomerCard(
                    id,
                    in.fullName(),
                    in.segmentAlias(),
                    in.externalIds() == null ? Map.of() : in.externalIds(),
                    Map.copyOf(attrs)
            );
            return CrmModels.CrmOutcome.ok(out, Map.of("mode", "stub"));
        }

        @Override
        public CrmModels.CrmOutcome<CrmModels.LeadRef> createLead(RuntimeConfigStore.RuntimeConfig cfg, CrmModels.CreateLeadRequest request, Map<String, Object> meta) {
            if (request == null || isBlank(request.title())) {
                return CrmModels.CrmOutcome.fail("BAD_REQUEST", "Не задан title для лида", Map.of());
            }
            CrmModels.LeadRef ref = new CrmModels.LeadRef("LEAD-001", "NEW", Instant.now(), request.attributes() == null ? Map.of() : request.attributes());
            return CrmModels.CrmOutcome.ok(ref, Map.of("mode", "stub"));
        }

        @Override
        public CrmModels.CrmOutcome<CrmModels.TaskRef> createTask(RuntimeConfigStore.RuntimeConfig cfg, CrmModels.CreateTaskRequest request, Map<String, Object> meta) {
            if (request == null || isBlank(request.title())) {
                return CrmModels.CrmOutcome.fail("BAD_REQUEST", "Не задан title для задачи", Map.of());
            }
            CrmModels.TaskRef ref = new CrmModels.TaskRef("TASK-001", "NEW", Instant.now(), request.attributes() == null ? Map.of() : request.attributes());
            return CrmModels.CrmOutcome.ok(ref, Map.of("mode", "stub"));
        }

        @Override
        public CrmModels.CrmOutcome<Map<String, Object>> appendNote(RuntimeConfigStore.RuntimeConfig cfg, CrmModels.AppendNoteRequest request, Map<String, Object> meta) {
            if (request == null || isBlank(request.entityType()) || isBlank(request.entityId()) || isBlank(request.text())) {
                return CrmModels.CrmOutcome.fail("BAD_REQUEST", "Для appendNote обязательны entityType/entityId/text", Map.of());
            }
            return CrmModels.CrmOutcome.ok(Map.of("status", "OK"), Map.of("mode", "stub"));
        }

        @Override
        public CrmModels.CrmOutcome<CrmModels.ServiceCaseRef> createServiceCase(RuntimeConfigStore.RuntimeConfig cfg, CrmModels.CreateServiceCaseRequest request, Map<String, Object> meta) {
            if (request == null || isBlank(request.title())) {
                return CrmModels.CrmOutcome.fail("BAD_REQUEST", "Не задан title для обращения", Map.of());
            }
            CrmModels.ServiceCaseRef ref = new CrmModels.ServiceCaseRef("CASE-001", "OPEN", Instant.now(), request.attributes() == null ? Map.of() : request.attributes());
            return CrmModels.CrmOutcome.ok(ref, Map.of("mode", "stub"));
        }

        @Override
        public CrmModels.CrmOutcome<CrmModels.SyncCustomerAndCreateCaseResult> syncCustomerAndCreateCase(RuntimeConfigStore.RuntimeConfig cfg, CrmModels.SyncCustomerAndCreateCaseRequest request, Map<String, Object> meta) {
            if (request == null || request.find() == null || request.serviceCase() == null) {
                return CrmModels.CrmOutcome.fail("BAD_REQUEST", "Для syncCustomerAndCreateCase обязательны find и serviceCase", Map.of());
            }

            CrmModels.CrmOutcome<CrmModels.CustomerCard> found = findCustomer(cfg, request.find(), meta);
            CrmModels.CustomerCard customer;
            if (found.success()) {
                customer = found.result();
            } else {
                // Если не найден — upsert (если задан), иначе создаём минимальный stub.
                if (request.upsert() != null) {
                    CrmModels.CrmOutcome<CrmModels.CustomerCard> up = upsertCustomer(cfg, request.upsert(), meta);
                    if (!up.success()) {
                        return CrmModels.CrmOutcome.fail("UPSERT_FAILED", "Не удалось выполнить upsert клиента: " + safe(up.errorMessage()), Map.of());
                    }
                    customer = up.result();
                } else {
                    customer = demoCustomer();
                }
            }

            CrmModels.CreateServiceCaseRequest scReq = request.serviceCase();
            CrmModels.CreateServiceCaseRequest sc = new CrmModels.CreateServiceCaseRequest(
                    scReq.title(),
                    customer.crmCustomerId(),
                    scReq.channel(),
                    scReq.attributes() == null ? Map.of() : scReq.attributes()
            );

            CrmModels.CrmOutcome<CrmModels.ServiceCaseRef> created = createServiceCase(cfg, sc, meta);
            if (!created.success()) {
                return CrmModels.CrmOutcome.fail("CASE_FAILED", "Не удалось создать обращение: " + safe(created.errorMessage()), Map.of());
            }

            return CrmModels.CrmOutcome.ok(
                    new CrmModels.SyncCustomerAndCreateCaseResult(customer, created.result(), Map.of(
                            "mode", "stub",
                            "found", found.success()
                    )),
                    Map.of("mode", "stub")
            );
        }

        private static CrmModels.CustomerCard demoCustomer() {
            return new CrmModels.CustomerCard(
                    "CRM-001",
                    "Иванов Иван Иванович",
                    "VIP_CLIENT",
                    Map.of("crm", "CRM-001"),
                    Map.of("phone", "+79990000001", "email", "test@example.local")
            );
        }

        private static boolean isBlank(String s) {
            return s == null || s.isBlank();
        }

        private static String safe(String s) {
            return s == null ? "" : s;
        }
    }

    /**
     * Bitrix24 CRM клиент (каркас).
     */
    @Singleton
    public static class Bitrix24CrmClient extends AbstractNotImplementedCrmClient {
        public Bitrix24CrmClient() {
            super(RuntimeConfigStore.CrmProfile.BITRIX24);
        }
    }

    /**
     * amoCRM клиент (каркас).
     */
    @Singleton
    public static class AmoCrmClient extends AbstractNotImplementedCrmClient {
        public AmoCrmClient() {
            super(RuntimeConfigStore.CrmProfile.AMOCRM);
        }
    }

    /**
     * RetailCRM клиент (каркас).
     */
    @Singleton
    public static class RetailCrmClient extends AbstractNotImplementedCrmClient {
        public RetailCrmClient() {
            super(RuntimeConfigStore.CrmProfile.RETAILCRM);
        }
    }

    /**
     * Мегаплан клиент (каркас).
     */
    @Singleton
    public static class MegaplanCrmClient extends AbstractNotImplementedCrmClient {
        public MegaplanCrmClient() {
            super(RuntimeConfigStore.CrmProfile.MEGAPLAN);
        }
    }
}
