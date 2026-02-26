package ru.aritmos.integrationbroker.core;

import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Реестр {@link MessagingProvider}.
 * <p>
 * Назначение:
 * <ul>
 *   <li>собрать доступные провайдеры из DI-контекста;</li>
 *   <li>давать быстрый доступ по строковому id;</li>
 *   <li>обеспечить безопасный дефолт ("logging"), чтобы среда разработки работала без внешнего брокера.</li>
 * </ul>
 */
@Singleton
public class MessagingProviderRegistry {

    private final Map<String, MessagingProvider> byId;

    public MessagingProviderRegistry(List<MessagingProvider> providers) {
        Map<String, MessagingProvider> map = new HashMap<>();
        for (MessagingProvider p : providers) {
            if (p == null || p.id() == null || p.id().isBlank()) {
                continue;
            }
            map.put(p.id().trim(), p);
        }
        this.byId = Map.copyOf(map);
    }

    /**
     * Получить провайдер по id.
     *
     * @param id идентификатор
     * @return провайдер или null
     */
    public MessagingProvider get(String id) {
        if (id == null || id.isBlank()) {
            return byId.get("logging");
        }
        MessagingProvider p = byId.get(id.trim());
        if (p != null) {
            return p;
        }
        return byId.get("logging");
    }

    /**
     * Получить провайдер строго по id без fallback на "logging".
     *
     * @param id идентификатор
     * @return провайдер или null
     */
    public MessagingProvider getExact(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return byId.get(id.trim());
    }

    /**
     * Проверка наличия провайдера в реестре.
     *
     * @param id идентификатор
     * @return true, если провайдер зарегистрирован
     */
    public boolean contains(String id) {
        return getExact(id) != null;
    }
}
