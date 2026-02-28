package ru.aritmos.integrationbroker.checks;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Introspected;

import java.util.List;

/**
 * Конфигурация стартовых проверок зависимостей (fail-fast).
 * <p>
 * Цель проверок:
 * <ul>
 *   <li>на ранней стадии обнаружить недоступность критичных внешних компонентов;</li>
 *   <li>в контролируемом виде «уронить» сервис, если это требуется эксплуатационной политикой.</li>
 * </ul>
 * <p>
 * Важно: для закрытых контуров РФ и on-prem сценариев политика fail-fast должна быть
 * настраиваемой, т.к. в некоторых режимах допустим «degraded mode» (например, отключённые
 * внешние коннекторы или временно недоступный remote-config).
 */
@Introspected
@ConfigurationProperties("integrationbroker.startup-checks")
public class StartupChecksConfiguration {

    /**
     * Глобальный флаг включения стартовых проверок.
     */
    private boolean enabled = true;

    private CheckConfig remoteConfig = new CheckConfig();
    private RestConnectorsCheckConfig restConnectors = new RestConnectorsCheckConfig();
    private MessagingProvidersCheckConfig messagingProviders = new MessagingProvidersCheckConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public CheckConfig getRemoteConfig() {
        return remoteConfig;
    }

    public void setRemoteConfig(CheckConfig remoteConfig) {
        this.remoteConfig = remoteConfig;
    }

    public RestConnectorsCheckConfig getRestConnectors() {
        return restConnectors;
    }

    public void setRestConnectors(RestConnectorsCheckConfig restConnectors) {
        this.restConnectors = restConnectors;
    }

    public MessagingProvidersCheckConfig getMessagingProviders() {
        return messagingProviders;
    }

    public void setMessagingProviders(MessagingProvidersCheckConfig messagingProviders) {
        this.messagingProviders = messagingProviders;
    }

    /**
     * Базовая конфигурация проверки зависимости.
     */
    @Introspected
    public static class CheckConfig {
        /** включена ли проверка */
        private boolean enabled = false;
        /** считать ли зависимость критичной */
        private boolean critical = false;
        /** «ронять» ли сервис при ошибке (если зависимость критична) */
        private boolean failFast = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isCritical() {
            return critical;
        }

        public void setCritical(boolean critical) {
            this.critical = critical;
        }

        public boolean isFailFast() {
            return failFast;
        }

        public void setFailFast(boolean failFast) {
            this.failFast = failFast;
        }
    }

    /**
     * Проверка доступности REST-коннекторов.
     * <p>
     * Проверка выполняется для каждого коннектора из runtime-config (restConnectors).
     */
    @Introspected
    public static class RestConnectorsCheckConfig extends CheckConfig {
        /** относительный путь health endpoint, добавляется к baseUrl */
        private String healthPath = "/health";
        /** таймаут проверки (мс) */
        private int timeoutMs = 1500;
        /** список допустимых HTTP статусов (по умолчанию 200 и 204) */
        private List<Integer> expectedStatus = List.of(200, 204);
        /** для OAuth2 коннекторов проверять доступность token endpoint и получение access token */
        private boolean validateOauth2TokenEndpoint = true;

        public String getHealthPath() {
            return healthPath;
        }

        public void setHealthPath(String healthPath) {
            this.healthPath = healthPath;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }


        public boolean isValidateOauth2TokenEndpoint() {
            return validateOauth2TokenEndpoint;
        }

        public void setValidateOauth2TokenEndpoint(boolean validateOauth2TokenEndpoint) {
            this.validateOauth2TokenEndpoint = validateOauth2TokenEndpoint;
        }
        public List<Integer> getExpectedStatus() {
            return expectedStatus;
        }

        public void setExpectedStatus(List<Integer> expectedStatus) {
            this.expectedStatus = expectedStatus;
        }
    }

    /**
     * Проверка доступности messaging provider-ов.
     * <p>
     * Проверка может использоваться в двух режимах:
     * <ul>
     *   <li>валидация того, что requiredProviderIds присутствуют в реестре;</li>
     *   <li>вызов healthCheck() провайдера (если реализация поддерживает).</li>
     * </ul>
     */
    @Introspected
    public static class MessagingProvidersCheckConfig extends CheckConfig {
        /** какие провайдеры считаются обязательными для запуска */
        private List<String> requiredProviderIds = List.of();

        public List<String> getRequiredProviderIds() {
            return requiredProviderIds;
        }

        public void setRequiredProviderIds(List<String> requiredProviderIds) {
            this.requiredProviderIds = requiredProviderIds;
        }
    }
}
