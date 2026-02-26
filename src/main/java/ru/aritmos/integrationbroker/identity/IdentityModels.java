package ru.aritmos.integrationbroker.identity;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Контракты слоя идентификации клиента (identity/customerIdentity).
 * <p>
 * Ключевая идея: система не должна ограничиваться фиксированным набором полей.
 * Идентификатор описывается парой {@code type + value}, а дополнительные параметры передаются как атрибуты.
 * <p>
 * Примеры типов идентификаторов:
 * <ul>
 *   <li>phone, email, card, biometrics, snils, inn, document</li>
 *   <li>contractNumber, policyNumber, clientCode, qrToken, barcodeToken</li>
 *   <li>любой другой тип, добавляемый заказчиком через новый {@link IdentityProvider}</li>
 * </ul>
 */
public final class IdentityModels {

    private IdentityModels() {
        // утилитарный класс
    }

    /**
     * Запрос на идентификацию.
     */
    @Schema(description = "Запрос на идентификацию клиента. Содержит один или несколько идентификаторов и контекст.")
    public record IdentityRequest(
            @Schema(description = "Набор идентификаторов. Порядок важен: он влияет на приоритет и fallback.")
            List<IdentityAttribute> attributes,
            @Schema(description = "Дополнительный контекст (например, branchId, serviceCode, channel и т.д.).")
            Map<String, Object> context,
            @Schema(description = "Политика разрешения (приоритеты, условия остановки, fallback).")
            IdentityResolutionPolicy policy
    ) {
    }

    /**
     * Один идентификатор клиента.
     * <p>
     * Важно: тип не ограничивается фиксированным списком.
     */
    @Schema(description = "Один идентификатор клиента. Тип не ограничен фиксированным списком (расширяемая модель).")
    public record IdentityAttribute(
            @Schema(description = "Тип идентификатора (например: phone, email, snils, inn, contractNumber, qrToken).")
            String type,
            @Schema(description = "Значение идентификатора.")
            String value,
            @Schema(description = "Дополнительные параметры для конкретного способа идентификации (опционально).")
            Map<String, Object> attributes
    ) {
    }

    /**
     * Политика идентификации.
     * <p>
     * Позволяет задавать приоритеты и условия остановки цепочки.
     */
    @Schema(description = "Политика идентификации: приоритеты и условия остановки цепочки.")
    public record IdentityResolutionPolicy(
            @Schema(description = "Приоритетный порядок типов (если задан, то эти типы обрабатываются первыми).")
            List<String> preferredTypes,
            @Schema(description = "Остановить обработку, как только найден clientId (даже если есть другие идентификаторы).")
            boolean stopOnFirstClientId,
            @Schema(description = "Остановить обработку после первого успешного совпадения (любого).")
            boolean stopOnAnyMatch
    ) {
        /**
         * @return безопасная политика по умолчанию: выполняем агрегацию по всем доступным данным.
         */
        public static IdentityResolutionPolicy defaultPolicy() {
            return new IdentityResolutionPolicy(List.of(), false, false);
        }
    }

    /**
     * Нормализованный профиль клиента.
     * <p>
     * Важно: это результат слоя identity, а не «сырой ответ CRM».
     */
    @Schema(description = "Нормализованный профиль клиента, полученный в результате идентификации.")
    public record IdentityProfile(
            @Schema(description = "Единый идентификатор клиента в домене заказчика/интеграции.")
            String clientId,
            @Schema(description = "Внешние идентификаторы (CRM/МИС/другие источники).")
            Map<String, String> externalIds,
            @Schema(description = "ФИО клиента (если доступно).")
            String fullName,
            @Schema(description = "Нормализованный сегмент клиента (VIP/PREMIUM/DEFAULT и т.д.).")
            String segment,
            @Schema(description = "Числовой вес приоритета (используется для правил обслуживания/вызова).")
            Integer priorityWeight,
            @Schema(description = "Подсказки сервису (например: предпочтительное окно, специализированный маршрут).")
            List<String> serviceHints,
            @Schema(description = "Аффинити к отделению/филиалу (может использоваться для маршрутизации).")
            Map<String, Object> branchAffinity,
            @Schema(description = "Произвольные атрибуты профиля (расширяемая модель).")
            Map<String, Object> attributes
    ) {
    }

    /**
     * Диагностическая запись (evidence) для объяснимости решения.
     * <p>
     * Используется в Admin/диагностических сценариях, а также для отладки flow.
     */
    @Schema(description = "Диагностика по провайдерам: какие методы применились и что вернули.")
    public record IdentityEvidence(
            @Schema(description = "Идентификатор провайдера.")
            String providerId,
            @Schema(description = "Тип идентификатора.")
            String type,
            @Schema(description = "Значение идентификатора (внимание: не логировать и не сохранять чувствительные значения без необходимости).")
            String value,
            @Schema(description = "Исход выполнения: MATCH/NO_MATCH/ERROR/SKIPPED.")
            String outcome,
            @Schema(description = "Дополнительные детали (без секретов).")
            Map<String, Object> details
    ) {
    }

    /**
     * Результат идентификации.
     */
    @Schema(description = "Результат идентификации: профиль + диагностика.")
    public record IdentityResolution(
            @Schema(description = "Нормализованный профиль клиента (может быть частично заполнен).")
            IdentityProfile profile,
            @Schema(description = "Диагностические свидетельства по провайдерам.")
            List<IdentityEvidence> evidences,
            @Schema(description = "Дополнительная диагностика (например, флаги resolved, количество попыток).")
            Map<String, Object> diagnostics
    ) {
    }
}
