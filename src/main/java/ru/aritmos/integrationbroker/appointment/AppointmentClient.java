package ru.aritmos.integrationbroker.appointment;

import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.util.List;
import java.util.Map;

/**
 * Типизированный клиент слоя предварительной записи (appointment/booking).
 * <p>
 * Реализация зависит от профиля интеграции (EMIAS_APPOINTMENT, YCLIENTS и т.п.).
 * Важно: интерфейс остаётся стабильным и расширяемым — сложные детали конкретной системы
 * прячутся за {@link RuntimeConfigStore.AppointmentConfig#settings()}.
 */
public interface AppointmentClient {

    /**
     * Получить список записей.
     */
    AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> getAppointments(AppointmentModels.GetAppointmentsRequest request, Map<String, Object> meta);

    /**
     * Получить ближайшую актуальную запись.
     */
    AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> getNearestAppointment(AppointmentModels.GetNearestAppointmentRequest request, Map<String, Object> meta);

    /**
     * Получить доступные слоты.
     */
    AppointmentModels.AppointmentOutcome<List<AppointmentModels.Slot>> getAvailableSlots(AppointmentModels.GetAvailableSlotsRequest request, Map<String, Object> meta);

    /**
     * Забронировать слот.
     */
    AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> bookSlot(AppointmentModels.BookSlotRequest request, Map<String, Object> meta);

    /**
     * Отменить запись.
     */
    AppointmentModels.AppointmentOutcome<Boolean> cancelAppointment(AppointmentModels.CancelAppointmentRequest request, Map<String, Object> meta);

    /**
     * Построить первичный queue plan для СУО на основе записи.
     */
    AppointmentModels.AppointmentOutcome<AppointmentModels.QueuePlan> buildQueuePlan(AppointmentModels.BuildQueuePlanRequest request, Map<String, Object> meta);
}
