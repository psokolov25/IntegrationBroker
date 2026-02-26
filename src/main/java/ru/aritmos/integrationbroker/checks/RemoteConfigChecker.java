package ru.aritmos.integrationbroker.checks;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

/**
 * Проверка доступности remote configuration (SystemConfiguration).
 * <p>
 * Проверка выполняет «пробный» запрос к remote-config и убеждается, что:
 * <ul>
 *   <li>endpoint доступен (HTTP 200/304);</li>
 *   <li>конфигурация не пуста (для 200);</li>
 *   <li>JSON корректен и может быть распарсен.</li>
 * </ul>
 * <p>
 * Важно: сами секреты/токены в логах отсутствуют.
 */
@Singleton
public class RemoteConfigChecker {

    private final RuntimeConfigStore configStore;

    public RemoteConfigChecker(RuntimeConfigStore configStore) {
        this.configStore = configStore;
    }

    /**
     * Выполняет проверку доступности remote-config.
     *
     * @param cfg настройки проверки (enabled/critical/failFast)
     */
    public void check(StartupChecksConfiguration.CheckConfig cfg) {
        if (!configStore.isRemoteEnabled()) {
            throw new IllegalStateException("remote-config отключён (integrationbroker.remote-config.enabled=false)");
        }
        configStore.assertRemoteAvailable();
    }
}
