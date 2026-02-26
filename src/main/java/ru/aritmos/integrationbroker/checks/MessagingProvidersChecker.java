package ru.aritmos.integrationbroker.checks;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.core.MessagingProvider;
import ru.aritmos.integrationbroker.core.MessagingProviderRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Проверка доступности messaging provider-ов.
 * <p>
 * Проверка выполняет:
 * <ol>
 *   <li>валидацию requiredProviderIds (если список задан);</li>
 *   <li>вызов {@link MessagingProvider#healthCheck()} для каждого requiredProviderId.</li>
 * </ol>
 */
@Singleton
public class MessagingProvidersChecker {

    private final MessagingProviderRegistry registry;

    public MessagingProvidersChecker(MessagingProviderRegistry registry) {
        this.registry = registry;
    }

    /**
     * Выполнить проверку messaging provider-ов.
     *
     * @param cfg настройки проверки
     */
    public void check(StartupChecksConfiguration.MessagingProvidersCheckConfig cfg) {
        List<String> required = (cfg.getRequiredProviderIds() == null) ? List.of() : cfg.getRequiredProviderIds();
        if (required.isEmpty()) {
            return;
        }

        List<String> failures = new ArrayList<>();

        for (String id : required) {
            MessagingProvider p = registry.getExact(id);
            if (p == null) {
                failures.add("provider '" + id + "' не зарегистрирован");
                continue;
            }

            try {
                MessagingProvider.HealthStatus hs = p.healthCheck();
                if (hs != null && !hs.ok()) {
                    failures.add("provider '" + id + "' недоступен: " + (hs.message() == null ? "" : hs.message()));
                }
            } catch (Exception e) {
                failures.add("provider '" + id + "' недоступен (" + e.getClass().getSimpleName() + ")");
            }
        }

        if (!failures.isEmpty()) {
            throw new IllegalStateException("Проблемы messaging provider-ов: " + String.join("; ", failures));
        }
    }
}
