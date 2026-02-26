package ru.aritmos.integrationbroker.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Статический провайдер идентификации.
 * <p>
 * Нужен как демонстрационный backend, чтобы показать сценарии идентификации без подключения реальных CRM/МИС.
 * В production обычно заменяется на типизированные адаптеры (CRM/MIS/Biometrics и т.д.).
 * <p>
 * Конфигурация берётся из {@code runtime-config.identity.providers.static}.
 */
@Singleton
public class StaticIdentityProvider implements IdentityProvider {

    private final RuntimeConfigStore configStore;
    private final ObjectMapper objectMapper;

    private final AtomicReference<String> cachedRevision = new AtomicReference<>();
    private final AtomicReference<Index> cachedIndex = new AtomicReference<>(new Index(Map.of(), Map.of(), false, 0));

    public StaticIdentityProvider(RuntimeConfigStore configStore, ObjectMapper objectMapper) {
        this.configStore = configStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "static";
    }

    @Override
    public int priority() {
        // Приоритет берём из конфига, иначе 100.
        Index idx = cachedIndex.get();
        return idx.priority;
    }

    @Override
    public boolean supportsType(String type) {
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        refreshIndexIfNeeded(cfg);

        Index idx = cachedIndex.get();
        if (!idx.enabled) {
            return false;
        }
        if (type == null || type.isBlank()) {
            return false;
        }
        return idx.supportedTypes.containsKey(normalizeKey(type));
    }

    @Override
    public Optional<IdentityModels.IdentityProfile> resolve(IdentityModels.IdentityAttribute attribute,
                                                           IdentityModels.IdentityRequest request,
                                                           ProviderContext ctx) {
        if (attribute == null || ctx == null || ctx.cfg() == null) {
            return Optional.empty();
        }
        RuntimeConfigStore.RuntimeConfig cfg = ctx.cfg();
        refreshIndexIfNeeded(cfg);

        Index idx = cachedIndex.get();
        if (!idx.enabled) {
            return Optional.empty();
        }
        String type = attribute.type();
        String value = attribute.value();
        if (type == null || type.isBlank() || value == null || value.isBlank()) {
            return Optional.empty();
        }

        IdentityModels.IdentityProfile prof = idx.byKey.get(normalizeKey(type) + "|" + normalizeValue(value));
        return Optional.ofNullable(prof);
    }

    private void refreshIndexIfNeeded(RuntimeConfigStore.RuntimeConfig cfg) {
        String rev = cfg.revision();
        String prev = cachedRevision.get();
        if (rev != null && rev.equals(prev)) {
            return;
        }

        Map<String, Object> providers = cfg.identity() == null ? Map.of() : cfg.identity().providers();
        Object raw = providers == null ? null : providers.get("static");

        StaticProviderConfig sp = toConfig(raw);
        Index idx = buildIndex(sp);

        cachedIndex.set(idx);
        cachedRevision.set(rev);
    }

    private StaticProviderConfig toConfig(Object raw) {
        if (raw == null) {
            return new StaticProviderConfig(false, 100, List.of());
        }
        try {
            return objectMapper.convertValue(raw, StaticProviderConfig.class);
        } catch (Exception e) {
            // Важно: не пробрасываем детальную ошибку в логах/исключениях с потенциальными секретами.
            return new StaticProviderConfig(false, 100, List.of());
        }
    }

    private Index buildIndex(StaticProviderConfig cfg) {
        boolean enabled = cfg != null && cfg.enabled;
        int prio = cfg == null ? 100 : cfg.priority;

        Map<String, IdentityModels.IdentityProfile> map = new HashMap<>();
        Map<String, Boolean> types = new HashMap<>();

        if (enabled && cfg.mappings != null) {
            for (StaticMapping m : cfg.mappings) {
                if (m == null || m.type == null || m.value == null || m.profile == null) {
                    continue;
                }
                String type = normalizeKey(m.type);
                String value = normalizeValue(m.value);
                map.put(type + "|" + value, m.profile);
                types.put(type, Boolean.TRUE);
            }
        }
        return new Index(Map.copyOf(map), Map.copyOf(types), enabled, prio);
    }

    private static String normalizeKey(String type) {
        return type.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeValue(String value) {
        return value.trim();
    }

    private record Index(
            Map<String, IdentityModels.IdentityProfile> byKey,
            Map<String, Boolean> supportedTypes,
            boolean enabled,
            int priority
    ) {
    }

    /**
     * Конфиг статического провайдера.
     */
    private static final class StaticProviderConfig {
        public boolean enabled;
        public int priority;
        public List<StaticMapping> mappings;

        public StaticProviderConfig() {
        }

        public StaticProviderConfig(boolean enabled, int priority, List<StaticMapping> mappings) {
            this.enabled = enabled;
            this.priority = priority;
            this.mappings = mappings;
        }
    }

    /**
     * Одна статическая запись сопоставления.
     */
    private static final class StaticMapping {
        public String type;
        public String value;
        public IdentityModels.IdentityProfile profile;

        public StaticMapping() {
        }
    }
}
