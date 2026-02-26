package ru.aritmos.integrationbroker.medical;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Контракты слоя Medical/MIS/EMR/EHR.
 * <p>
 * Слой предназначен для получения медицинского контекста пациента и данных, необходимых для
 * маршрутизации и обслуживания в СУО (VisitManager/Integration Broker).
 * <p>
 * Важно:
 * <ul>
 *   <li>это типизированный слой интеграции; внешние МИС/EMR/EHR подключаются через {@link MedicalClient};</li>
 *   <li>контракты ориентированы на закрытые контуры РФ: минимум ПДн, отсутствие токенов в логах/хранилищах outbox/DLQ;</li>
 *   <li>в дальнейшем слой используется для сценариев precheck/профосмотров/медицинских маршрутов.</li>
 * </ul>
 */
public final class MedicalModels {

    private MedicalModels() {
        // утилитарный класс
    }

    /**
     * Универсальный исход выполнения операции в медицинском слое.
     * <p>
     * Нужен для консервативных интеграционных контрактов: вызывающая сторона (flow) должна получать
     * типизированный статус и диагностику, не зависящую от конкретного вендора.
     */
    @Schema(description = "Результат операции медицинского слоя (успех/ошибка/не реализовано) + данные + диагностика.")
    public record MedicalOutcome<T>(
            @Schema(description = "Флаг успеха. true означает, что result заполнен корректными данными (хотя и частичными).")
            boolean success,
            @Schema(description = "Код исхода (OK/DISABLED/NOT_IMPLEMENTED/ERROR).")
            String outcome,
            @Schema(description = "Код ошибки (если есть).")
            String errorCode,
            @Schema(description = "Сообщение об ошибке (без секретов и без избыточных ПДн).")
            String message,
            @Schema(description = "Результат операции (если success=true).")
            T result,
            @Schema(description = "Дополнительная диагностика (без секретов).")
            Map<String, Object> details
    ) {
        public static <T> MedicalOutcome<T> ok(T result, Map<String, Object> details) {
            return new MedicalOutcome<>(true, "OK", null, null, result, details == null ? Map.of() : details);
        }

        public static <T> MedicalOutcome<T> disabled(String message) {
            return new MedicalOutcome<>(false, "DISABLED", "MEDICAL_DISABLED", message, null, Map.of());
        }

        public static <T> MedicalOutcome<T> notImplemented(String profile, String operation) {
            return new MedicalOutcome<>(false, "NOT_IMPLEMENTED", "MEDICAL_NOT_IMPLEMENTED",
                    "Операция медицинского профиля пока не реализована: profile=" + profile + ", op=" + operation,
                    null,
                    Map.of("profile", profile, "operation", operation));
        }

        public static <T> MedicalOutcome<T> error(String code, String message, Map<String, Object> details) {
            return new MedicalOutcome<>(false, "ERROR", code, message, null, details == null ? Map.of() : details);
        }
    }

    /**
     * Один ключ пациента.
     * <p>
     * В медицинской интеграции часто встречаются разные ключи: СНИЛС, полис, номер карты пациента,
     * внутренний patientId, номер заявки и т.д.
     */
    @Schema(description = "Ключ пациента (расширяемая модель type+value).")
    public record PatientKey(
            @Schema(description = "Тип ключа (например: snils, policy, patientId, document, phone).")
            String type,
            @Schema(description = "Значение ключа.")
            String value
    ) {
    }

    /**
     * Запрос на получение пациента.
     */
    @Schema(description = "Запрос на получение данных пациента по одному или нескольким ключам.")
    public record GetPatientRequest(
            @Schema(description = "Ключи пациента. Порядок важен: позволяет задавать приоритет.")
            List<PatientKey> keys,
            @Schema(description = "Дополнительный контекст (branchId, serviceCode, channel, и т.д.).")
            Map<String, Object> context
    ) {
    }

    /**
     * Пациент (нормализованная модель).
     */
    @Schema(description = "Нормализованная карточка пациента для задач маршрутизации в СУО.")
    public record Patient(
            @Schema(description = "Внутренний идентификатор пациента в домене МИС/EMR.")
            String patientId,
            @Schema(description = "ФИО пациента (если доступно).")
            String fullName,
            @Schema(description = "Дата рождения (строка ISO, если доступно).")
            String birthDate,
            @Schema(description = "Внешние идентификаторы (например: emrId, fhirId, policyId).")
            Map<String, String> externalIds,
            @Schema(description = "Атрибуты пациента (расширяемая модель).")
            Map<String, Object> attributes
    ) {
    }

    /**
     * Запрос на получение предстоящих услуг/приёмов.
     */
    @Schema(description = "Запрос на получение предстоящих медицинских услуг/этапов для пациента.")
    public record UpcomingServicesRequest(
            @Schema(description = "Идентификатор пациента (если уже известен).")
            String patientId,
            @Schema(description = "Ключи пациента, если patientId неизвестен.")
            List<PatientKey> keys,
            @Schema(description = "Дополнительный контекст (branchId, appointmentId, и т.д.).")
            Map<String, Object> context
    ) {
    }

    /**
     * Одна медицинская услуга/этап.
     */
    @Schema(description = "Одна медицинская услуга/этап (нормализовано для построения маршрута).")
    public record UpcomingService(
            @Schema(description = "Код услуги/этапа.")
            String code,
            @Schema(description = "Название услуги/этапа.")
            String name,
            @Schema(description = "Подразделение/отделение (если есть).")
            String department,
            @Schema(description = "Кабинет/зона обслуживания (если есть).")
            String cabinet,
            @Schema(description = "Время начала (ISO строка), если известно.")
            String startAt,
            @Schema(description = "Произвольные атрибуты услуги.")
            Map<String, Object> attributes
    ) {
    }

    /**
     * Запрос на построение routing context для СУО.
     */
    @Schema(description = "Запрос на построение routing context (пациент + предстоящие услуги + подсказки).")
    public record BuildRoutingContextRequest(
            @Schema(description = "Ключи пациента.")
            List<PatientKey> keys,
            @Schema(description = "Идентификатор пациента (если уже известен).")
            String patientId,
            @Schema(description = "Дополнительный контекст (branchId, channel, appointmentId, и т.д.).")
            Map<String, Object> context
    ) {
    }

    /**
     * Итоговый медицинский контекст для маршрутизации.
     */
    @Schema(description = "Медицинский routing context: пациент + услуги + подсказки для маршрутизации.")
    public record MedicalRoutingContext(
            @Schema(description = "Пациент.")
            Patient patient,
            @Schema(description = "Предстоящие услуги/этапы.")
            List<UpcomingService> upcomingServices,
            @Schema(description = "Подсказки/атрибуты для маршрутизации (например: предпочтительная зона, быстрый маршрут).")
            Map<String, Object> routingHints
    ) {
    }
}
