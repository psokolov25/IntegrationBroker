package ru.aritmos.integrationbroker.crm;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Реестр CRM-клиентов по профилям.
 * <p>
 * Расширение:
 * <ul>
 *   <li>для добавления нового CRM-профиля достаточно реализовать {@link CrmClient} и зарегистрировать бин;</li>
 *   <li>ядро будет выбирать реализацию по {@link RuntimeConfigStore.CrmProfile}.</li>
 * </ul>
 */
@Singleton
public class CrmClientRegistry {

    private final Map<RuntimeConfigStore.CrmProfile, CrmClient> byProfile = new EnumMap<>(RuntimeConfigStore.CrmProfile.class);

    public CrmClientRegistry(List<CrmClient> clients) {
        if (clients != null) {
            for (CrmClient c : clients) {
                if (c == null || c.profile() == null) {
                    continue;
                }
                byProfile.put(c.profile(), c);
            }
        }
    }

    /**
     * Получить клиента по профилю.
     * <p>
     * Если конкретный профиль не зарегистрирован, выбирается GENERIC (если есть), иначе выбрасывается исключение.
     */
    public CrmClient get(RuntimeConfigStore.CrmProfile profile) {
        RuntimeConfigStore.CrmProfile p = (profile == null) ? RuntimeConfigStore.CrmProfile.GENERIC : profile;
        CrmClient exact = byProfile.get(p);
        if (exact != null) {
            return exact;
        }
        CrmClient generic = byProfile.get(RuntimeConfigStore.CrmProfile.GENERIC);
        if (generic != null) {
            return generic;
        }
        throw new IllegalStateException("Не найден CRM-клиент для профиля: " + p + ". Добавьте реализацию CrmClient.");
    }

    /**
     * @return снимок зарегистрированных профилей (для диагностики/метрик)
     */
    public Map<RuntimeConfigStore.CrmProfile, CrmClient> snapshot() {
        return Map.copyOf(byProfile);
    }
}
