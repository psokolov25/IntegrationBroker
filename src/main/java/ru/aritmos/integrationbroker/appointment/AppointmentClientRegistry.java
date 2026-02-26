package ru.aritmos.integrationbroker.appointment;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.util.EnumMap;
import java.util.Map;

/**
 * Реестр клиентов appointment слоя по профилям.
 * <p>
 * Нужен, чтобы добавление новых типов интеграции не ломало ядро:
 * достаточно добавить реализацию {@link AppointmentClient} и зарегистрировать её в {@link AppointmentClients}.
 */
@Singleton
public class AppointmentClientRegistry {

    private final Map<RuntimeConfigStore.AppointmentProfile, AppointmentClient> byProfile;

    public AppointmentClientRegistry(AppointmentClients clients) {
        EnumMap<RuntimeConfigStore.AppointmentProfile, AppointmentClient> map = new EnumMap<>(RuntimeConfigStore.AppointmentProfile.class);
        map.put(RuntimeConfigStore.AppointmentProfile.EMIAS_APPOINTMENT, clients.emiasAppointment());
        map.put(RuntimeConfigStore.AppointmentProfile.MEDTOCHKA_LIKE, clients.medtochkaLike());
        map.put(RuntimeConfigStore.AppointmentProfile.PRODOCTOROV_LIKE, clients.prodoctorovLike());
        map.put(RuntimeConfigStore.AppointmentProfile.YCLIENTS_LIKE, clients.yclientsLike());
        map.put(RuntimeConfigStore.AppointmentProfile.NAPOPRAVKU_LIKE, clients.napopravkuLike());
        map.put(RuntimeConfigStore.AppointmentProfile.GENERIC, clients.generic());
        this.byProfile = Map.copyOf(map);
    }

    /**
     * Получить клиента по профилю.
     *
     * @param profile профиль
     * @return клиент
     */
    public AppointmentClient get(RuntimeConfigStore.AppointmentProfile profile) {
        AppointmentClient c = byProfile.get(profile);
        if (c == null) {
            throw new IllegalStateException("Не зарегистрирован AppointmentClient для профиля: " + profile);
        }
        return c;
    }
}
