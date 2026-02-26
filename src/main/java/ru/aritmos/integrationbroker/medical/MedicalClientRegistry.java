package ru.aritmos.integrationbroker.medical;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.util.EnumMap;
import java.util.Map;

/**
 * Реестр медицинских клиентов по профилям.
 * <p>
 * Нужен, чтобы добавление новых типов интеграции (новых профилей) не ломало ядро:
 * достаточно добавить реализацию {@link MedicalClient} и зарегистрировать её в {@link MedicalClients}.
 */
@Singleton
public class MedicalClientRegistry {

    private final Map<RuntimeConfigStore.MedicalProfile, MedicalClient> byProfile;

    public MedicalClientRegistry(MedicalClients clients) {
        EnumMap<RuntimeConfigStore.MedicalProfile, MedicalClient> map = new EnumMap<>(RuntimeConfigStore.MedicalProfile.class);
        map.put(RuntimeConfigStore.MedicalProfile.EMIAS_LIKE, clients.emiasLike());
        map.put(RuntimeConfigStore.MedicalProfile.MEDESK_LIKE, clients.medeskLike());
        map.put(RuntimeConfigStore.MedicalProfile.FHIR_GENERIC, clients.fhirGeneric());
        this.byProfile = Map.copyOf(map);
    }

    /**
     * Получить клиента по профилю.
     *
     * @param profile профиль
     * @return клиент
     */
    public MedicalClient get(RuntimeConfigStore.MedicalProfile profile) {
        MedicalClient c = byProfile.get(profile);
        if (c == null) {
            throw new IllegalStateException("Не зарегистрирован MedicalClient для профиля: " + profile);
        }
        return c;
    }
}
