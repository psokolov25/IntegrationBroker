package ru.aritmos.integrationbroker.identity;

import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.util.Map;
import java.util.Optional;

/**
 * Провайдер идентификации клиента.
 * <p>
 * Провайдер — это типизированный backend-источник, который умеет обрабатывать один или несколько типов идентификаторов.
 * <p>
 * Примеры провайдеров:
 * <ul>
 *   <li>CRM (Bitrix24/AmoCRM/RetailCRM/Generic)</li>
 *   <li>МИС/EMR/EHR</li>
 *   <li>внутренний справочник клиентов заказчика</li>
 *   <li>биометрическая подсистема</li>
 * </ul>
 * <p>
 * Важно: добавление нового способа идентификации не должно требовать изменений ядра.
 * Достаточно реализовать новый bean {@link IdentityProvider} и (при необходимости) описать его конфиг.
 */
public interface IdentityProvider {

    /**
     * @return стабильный идентификатор провайдера (например: crmGeneric, misFhir, biometrics, static)
     */
    String id();

    /**
     * Приоритет провайдера: чем больше значение — тем раньше провайдер будет вызван.
     * <p>
     * Используется, когда несколько провайдеров поддерживают один и тот же тип идентификатора.
     */
    int priority();

    /**
     * Проверить, поддерживает ли провайдер данный тип идентификатора.
     * <p>
     * Тип является строкой и может быть произвольным.
     *
     * @param type тип идентификатора (например: phone, email, inn, contractNumber)
     * @return true, если провайдер готов обработать данный тип
     */
    boolean supportsType(String type);

    /**
     * Выполнить разрешение идентификатора.
     *
     * @param attribute идентификатор
     * @param request исходный запрос
     * @param ctx контекст провайдера
     * @return частичный профиль клиента или empty, если совпадение не найдено
     */
    Optional<IdentityModels.IdentityProfile> resolve(IdentityModels.IdentityAttribute attribute,
                                                    IdentityModels.IdentityRequest request,
                                                    ProviderContext ctx);

    /**
     * Контекст провайдера.
     * <p>
     * Не содержит токенов/секретов «по умолчанию». Если конкретный провайдер использует секреты,
     * он должен брать их только из конфигурации коннектора и не логировать.
     */
    record ProviderContext(
            RuntimeConfigStore.RuntimeConfig cfg,
            Map<String, Object> meta
    ) {
    }
}
