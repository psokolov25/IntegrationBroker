package ru.aritmos.integrationbroker.checks;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.event.ApplicationStartupEvent;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Исполнитель стартовых проверок зависимостей (fail-fast).
 * <p>
 * Реализовано как listener на {@link ApplicationStartupEvent}.
 * <p>
 * Важно:
 * <ul>
 *   <li>если проверка помечена как critical и failFast=true — сервис падает на старте;</li>
 *   <li>если critical=false — ошибка логируется как предупреждение, сервис продолжает запуск.</li>
 * </ul>
 */
@Singleton
public class StartupChecksRunner implements ApplicationEventListener<ApplicationStartupEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupChecksRunner.class);

    private final StartupChecksConfiguration cfg;
    private final RemoteConfigChecker remoteConfigChecker;
    private final RestConnectorsChecker restConnectorsChecker;
    private final MessagingProvidersChecker messagingProvidersChecker;

    public StartupChecksRunner(StartupChecksConfiguration cfg,
                              RemoteConfigChecker remoteConfigChecker,
                              RestConnectorsChecker restConnectorsChecker,
                              MessagingProvidersChecker messagingProvidersChecker) {
        this.cfg = cfg;
        this.remoteConfigChecker = remoteConfigChecker;
        this.restConnectorsChecker = restConnectorsChecker;
        this.messagingProvidersChecker = messagingProvidersChecker;
    }

    @Override
    public void onApplicationEvent(ApplicationStartupEvent event) {
        if (!cfg.isEnabled()) {
            log.info("Стартовые проверки зависимостей отключены настройкой integrationbroker.startup-checks.enabled=false");
            return;
        }

        runOne("remote-config", cfg.getRemoteConfig(), () -> remoteConfigChecker.check(cfg.getRemoteConfig()));
        runOne("rest-connectors", cfg.getRestConnectors(), () -> restConnectorsChecker.check(cfg.getRestConnectors()));
        runOne("messaging-providers", cfg.getMessagingProviders(), () -> messagingProvidersChecker.check(cfg.getMessagingProviders()));
    }

    private void runOne(String name, StartupChecksConfiguration.CheckConfig c, Runnable action) {
        if (c == null || !c.isEnabled()) {
            return;
        }
        try {
            action.run();
            log.info("Стартовая проверка '{}' успешно пройдена", name);
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (c.isCritical() && c.isFailFast()) {
                throw new IllegalStateException("Критичная зависимость недоступна (" + name + "): " + msg, e);
            }
            if (c.isCritical()) {
                log.error("Критичная зависимость недоступна ({}), но fail-fast выключен: {}", name, msg);
            } else {
                log.warn("Некритичная зависимость недоступна ({}): {}", name, msg);
            }
        }
    }
}
