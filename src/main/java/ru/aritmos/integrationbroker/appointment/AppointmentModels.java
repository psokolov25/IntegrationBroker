package ru.aritmos.integrationbroker.appointment;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Контракты слоя предварительной записи (appointment/booking/schedule).
 * <p>
 * Слой используется для сценариев:
 * <ul>
 *   <li>получение ближайшей записи и контекста записи;</li>
 *   <li>получение доступных слотов;</li>
 *   <li>бронирование/отмена записи;</li>
 *   <li>построение первичного queue plan для СУО на основе записи.</li>
 * </ul>
 * <p>
 * Важно: слой должен быть расширяемым. Идентификаторы клиента/пациента/заявки
 * передаются как набор ключей {@code type + value}.
 */
public final class AppointmentModels {

    private AppointmentModels() {
        // утилитарный класс
    }

    /**
     * Один ключ для поиска записи/слотов.
     * <p>
     * Тип ключа не ограничивается фиксированным списком (расширяемая модель).
     * Примеры: clientId, patientId, phone, email, policyNumber, requestId, ticketNumber.
     */
    @Schema(description = "Ключ для поиска записи/слотов. Тип не ограничен фиксированным списком (расширяемая модель).")
    public record BookingKey(
            @Schema(description = "Тип ключа (например: clientId, patientId, phone, email, requestId).")
            String type,
            @Schema(description = "Значение ключа.")
            String value,
            @Schema(description = "Дополнительные параметры (опционально).")
            Map<String, Object> attributes
    ) {
    }

    @Schema(description = "Запрос на получение списка записей клиента/пациента.")
    public record GetAppointmentsRequest(
            @Schema(description = "Набор ключей для поиска (порядок важен).")
            List<BookingKey> keys,
            @Schema(description = "Начало периода (опционально).")
            Instant from,
            @Schema(description = "Конец периода (опционально).")
            Instant to,
            @Schema(description = "Контекст (например: branchId, channel, serviceCode).")
            Map<String, Object> context
    ) {
    }

    @Schema(description = "Запрос на получение ближайшей актуальной записи.")
    public record GetNearestAppointmentRequest(
            @Schema(description = "Набор ключей для поиска.")
            List<BookingKey> keys,
            @Schema(description = "Контекст (например: branchId, channel, serviceCode).")
            Map<String, Object> context
    ) {
    }

    @Schema(description = "Запрос на получение доступных слотов для предварительной записи.")
    public record GetAvailableSlotsRequest(
            @Schema(description = "Код услуги/приёма (опционально).")
            String serviceCode,
            @Schema(description = "Код/идентификатор площадки/филиала (опционально).")
            String locationId,
            @Schema(description = "Период начала (опционально).")
            Instant from,
            @Schema(description = "Период конца (опционально).")
            Instant to,
            @Schema(description = "Контекст (например: branchId, channel).")
            Map<String, Object> context
    ) {
    }

    @Schema(description = "Запрос на бронирование слота.")
    public record BookSlotRequest(
            @Schema(description = "Идентификатор слота или предложения (в зависимости от профиля).")
            String slotId,
            @Schema(description = "Код услуги/приёма (опционально).")
            String serviceCode,
            @Schema(description = "Набор ключей клиента/пациента.")
            List<BookingKey> keys,
            @Schema(description = "Контекст (например: branchId, channel).")
            Map<String, Object> context
    ) {
    }

    @Schema(description = "Запрос на отмену записи.")
    public record CancelAppointmentRequest(
            @Schema(description = "Идентификатор записи.")
            String appointmentId,
            @Schema(description = "Причина отмены (опционально).")
            String reason,
            @Schema(description = "Контекст (например: branchId, channel).")
            Map<String, Object> context
    ) {
    }

    @Schema(description = "Запрос на построение queue plan на основе записи.")
    public record BuildQueuePlanRequest(
            @Schema(description = "Идентификатор записи.")
            String appointmentId,
            @Schema(description = "Набор ключей клиента/пациента (для enrichment).")
            List<BookingKey> keys,
            @Schema(description = "Контекст (например: branchId, segment, serviceCode).")
            Map<String, Object> context
    ) {
    }

    @Schema(description = "Запись (appointment), полученная из системы предварительной записи.")
    public record Appointment(
            @Schema(description = "Идентификатор записи.")
            String appointmentId,
            @Schema(description = "Начало записи.")
            Instant startAt,
            @Schema(description = "Окончание записи (опционально).")
            Instant endAt,
            @Schema(description = "Код услуги/приёма.")
            String serviceCode,
            @Schema(description = "ФИО врача/специалиста (опционально).")
            String specialistName,
            @Schema(description = "Кабинет/окно/локация (опционально).")
            String room,
            @Schema(description = "Статус записи (CONFIRMED/CANCELLED/UNKNOWN и т.п.).")
            String status,
            @Schema(description = "Дополнительные атрибуты (расширяемая модель).")
            Map<String, Object> attributes
    ) {
    }

    @Schema(description = "Доступный слот предварительной записи.")
    public record Slot(
            @Schema(description = "Идентификатор слота.")
            String slotId,
            @Schema(description = "Время начала.")
            Instant startAt,
            @Schema(description = "Время окончания (опционально).")
            Instant endAt,
            @Schema(description = "Код услуги/приёма (опционально).")
            String serviceCode,
            @Schema(description = "Дополнительные атрибуты (например: врач, кабинет, ограничения).")
            Map<String, Object> attributes
    ) {
    }

    @Schema(description = "Этап обслуживания/маршрута, сформированный на основе записи.")
    public record QueueStep(
            @Schema(description = "Код очереди/зоны/этапа.")
            String queueCode,
            @Schema(description = "Зона отображения (displayZone), если применимо.")
            String displayZone,
            @Schema(description = "Рекомендованный кабинет/окно (опционально).")
            String room,
            @Schema(description = "Комментарий/подсказка оператору (опционально).")
            String hint,
            @Schema(description = "Произвольные атрибуты этапа.")
            Map<String, Object> attributes
    ) {
    }

    @Schema(description = "Queue plan — нормализованный план обслуживания, который можно передавать в СУО/DeviceManager.")
    public record QueuePlan(
            @Schema(description = "Идентификатор записи, из которой построен план.")
            String appointmentId,
            @Schema(description = "Сегмент клиента (если известен).")
            String segment,
            @Schema(description = "Список этапов/очередей обслуживания.")
            List<QueueStep> steps,
            @Schema(description = "Дополнительные атрибуты плана.")
            Map<String, Object> attributes
    ) {
    }

    /**
     * Унифицированный исход операции appointment слоя.
     * <p>
     * Важно: результат может быть DISABLED/NOT_IMPLEMENTED в зависимости от профиля и конфигурации.
     */
    @Schema(description = "Результат операции appointment слоя.")
    public record AppointmentOutcome<T>(
            @Schema(description = "Флаг успеха.")
            boolean success,
            @Schema(description = "Код исхода (OK/DISABLED/NOT_IMPLEMENTED/ERROR).")
            String code,
            @Schema(description = "Человекочитаемое сообщение.")
            String message,
            @Schema(description = "Результат (если есть).")
            T result,
            @Schema(description = "Дополнительные детали (без секретов).")
            Map<String, Object> details
    ) {
        public static <T> AppointmentOutcome<T> ok(T result) {
            return new AppointmentOutcome<>(true, "OK", "", result, Map.of());
        }

        public static <T> AppointmentOutcome<T> disabled(String message) {
            return new AppointmentOutcome<>(false, "DISABLED", message, null, Map.of());
        }

        public static <T> AppointmentOutcome<T> notImplemented(String message) {
            return new AppointmentOutcome<>(false, "NOT_IMPLEMENTED", message, null, Map.of());
        }

        public static <T> AppointmentOutcome<T> error(String message) {
            return new AppointmentOutcome<>(false, "ERROR", message, null, Map.of());
        }
    }
}
