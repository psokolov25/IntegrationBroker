package ru.aritmos.integrationbroker.appointment;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.util.List;
import java.util.Map;

/**
 * Сервис слоя предварительной записи, который выбирает активный профиль и делегирует в соответствующий {@link AppointmentClient}.
 * <p>
 * Важно: слой должен быть отключаемым. Во многих проектах предварительная запись присутствует не всегда
 * или интеграция может временно отсутствовать.
 */
@Singleton
public class AppointmentService {

    private final RuntimeConfigStore configStore;
    private final AppointmentClientRegistry registry;

    public AppointmentService(RuntimeConfigStore configStore, AppointmentClientRegistry registry) {
        this.configStore = configStore;
        this.registry = registry;
    }

    public AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> getAppointments(AppointmentModels.GetAppointmentsRequest request, Map<String, Object> meta) {
        RuntimeConfigStore.AppointmentConfig cfg = configStore.getEffective().appointment();
        if (cfg == null || !cfg.enabled()) {
            return AppointmentModels.AppointmentOutcome.disabled("Слой предварительной записи выключен конфигурацией");
        }
        AppointmentClient c = registry.get(cfg.profile());
        return c.getAppointments(request, safeMeta(meta));
    }

    public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> getNearestAppointment(AppointmentModels.GetNearestAppointmentRequest request, Map<String, Object> meta) {
        RuntimeConfigStore.AppointmentConfig cfg = configStore.getEffective().appointment();
        if (cfg == null || !cfg.enabled()) {
            return AppointmentModels.AppointmentOutcome.disabled("Слой предварительной записи выключен конфигурацией");
        }
        AppointmentClient c = registry.get(cfg.profile());
        return c.getNearestAppointment(request, safeMeta(meta));
    }

    public AppointmentModels.AppointmentOutcome<List<AppointmentModels.Slot>> getAvailableSlots(AppointmentModels.GetAvailableSlotsRequest request, Map<String, Object> meta) {
        RuntimeConfigStore.AppointmentConfig cfg = configStore.getEffective().appointment();
        if (cfg == null || !cfg.enabled()) {
            return AppointmentModels.AppointmentOutcome.disabled("Слой предварительной записи выключен конфигурацией");
        }
        AppointmentClient c = registry.get(cfg.profile());
        return c.getAvailableSlots(request, safeMeta(meta));
    }

    public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> bookSlot(AppointmentModels.BookSlotRequest request, Map<String, Object> meta) {
        RuntimeConfigStore.AppointmentConfig cfg = configStore.getEffective().appointment();
        if (cfg == null || !cfg.enabled()) {
            return AppointmentModels.AppointmentOutcome.disabled("Слой предварительной записи выключен конфигурацией");
        }
        AppointmentClient c = registry.get(cfg.profile());
        return c.bookSlot(request, safeMeta(meta));
    }

    public AppointmentModels.AppointmentOutcome<Boolean> cancelAppointment(AppointmentModels.CancelAppointmentRequest request, Map<String, Object> meta) {
        RuntimeConfigStore.AppointmentConfig cfg = configStore.getEffective().appointment();
        if (cfg == null || !cfg.enabled()) {
            return AppointmentModels.AppointmentOutcome.disabled("Слой предварительной записи выключен конфигурацией");
        }
        AppointmentClient c = registry.get(cfg.profile());
        return c.cancelAppointment(request, safeMeta(meta));
    }

    public AppointmentModels.AppointmentOutcome<AppointmentModels.QueuePlan> buildQueuePlan(AppointmentModels.BuildQueuePlanRequest request, Map<String, Object> meta) {
        RuntimeConfigStore.AppointmentConfig cfg = configStore.getEffective().appointment();
        if (cfg == null || !cfg.enabled()) {
            return AppointmentModels.AppointmentOutcome.disabled("Слой предварительной записи выключен конфигурацией");
        }
        AppointmentClient c = registry.get(cfg.profile());
        return c.buildQueuePlan(request, safeMeta(meta));
    }

    private Map<String, Object> safeMeta(Map<String, Object> meta) {
        return meta == null ? Map.of() : meta;
    }
}
