package ru.aritmos.integrationbroker.identity;

import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;

/**
 * Реестр провайдеров идентификации.
 * <p>
 * Реестр формируется через DI (Micronaut) из всех bean'ов {@link IdentityProvider}.
 *
 * Важно: ядро не должно знать о конкретных провайдерах. Добавление нового провайдера
 * выполняется через новый bean и (при необходимости) конфигурацию.
 */
@Singleton
public class IdentityProviderRegistry {

    private final List<IdentityProvider> providers;

    public IdentityProviderRegistry(List<IdentityProvider> providers) {
        this.providers = providers == null ? List.of() : providers;
    }

    /**
     * Получить список провайдеров, которые заявляют поддержку данного типа.
     * <p>
     * Результат отсортирован по убыванию {@link IdentityProvider#priority()}.
     */
    public List<IdentityProvider> providersForType(String type) {
        return providers.stream()
                .filter(p -> p != null && p.supportsType(type))
                .sorted(Comparator.comparingInt(IdentityProvider::priority).reversed())
                .toList();
    }
}
