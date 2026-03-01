package ru.aritmos.integrationbroker.core;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime-переключатель dry-run режима outbound.
 * <p>
 * Позволяет временно включать/выключать dry-run через Admin API без рестарта.
 */
@Singleton
public class OutboundDryRunState {

    private final AtomicReference<Boolean> override = new AtomicReference<>();
    private final boolean configuredDefault;

    public OutboundDryRunState(@Value("${integrationbroker.outbound.dry-run:false}") boolean configuredDefault) {
        this.configuredDefault = configuredDefault;
    }

    public OutboundDryRunState(boolean configuredDefault, Boolean overrideValue) {
        this.configuredDefault = configuredDefault;
        this.override.set(overrideValue);
    }

    public boolean isDryRun(boolean fallback) {
        Boolean v = override.get();
        if (v != null) {
            return v;
        }
        return fallback;
    }

    public boolean configuredDefault() {
        return configuredDefault;
    }

    public Boolean overrideValue() {
        return override.get();
    }

    public boolean effective(boolean fallback) {
        return isDryRun(fallback);
    }

    public void setOverride(Boolean enabled) {
        override.set(enabled);
    }

    public void resetOverride() {
        override.set(null);
    }
}
