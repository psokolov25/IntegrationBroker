package ru.aritmos.integrationbroker.medical;

import java.util.List;
import java.util.Map;

/**
 * Клиент медицинской интеграции (МИС/EMR/EHR).
 * <p>
 * Реальные интеграции (EMIAS-подобные, MEDESK-подобные, FHIR/generic) должны реализовывать этот интерфейс.
 * <p>
 * Важно:
 * <ul>
 *   <li>интерфейс ориентирован на консервативные контракты и эксплуатационную безопасность;</li>
 *   <li>методы должны возвращать типизированный {@link MedicalModels.MedicalOutcome} без утечек секретов;</li>
 *   <li>сетевые настройки и авторизация должны храниться в runtime-config (через restConnectors),
 *       а не протаскиваться через headers/payload сообщений.</li>
 * </ul>
 */
public interface MedicalClient {

    /**
     * Идентификатор профиля (для диагностики).
     *
     * @return строковый идентификатор профиля/клиента
     */
    String profileId();

    /**
     * Получить пациента.
     *
     * @param request запрос
     * @param meta метаданные ядра (branchId/userId/channel и т.п.)
     * @return результат
     */
    MedicalModels.MedicalOutcome<MedicalModels.Patient> getPatient(MedicalModels.GetPatientRequest request, Map<String, Object> meta);

    /**
     * Получить предстоящие медицинские услуги/этапы.
     *
     * @param request запрос
     * @param meta метаданные ядра
     * @return список услуг/этапов
     */
    MedicalModels.MedicalOutcome<List<MedicalModels.UpcomingService>> getUpcomingServices(MedicalModels.UpcomingServicesRequest request, Map<String, Object> meta);

    /**
     * Собрать медицинский routing context для СУО.
     * <p>
     * В базовой версии это агрегатор поверх {@link #getPatient(MedicalModels.GetPatientRequest, Map)} и
     * {@link #getUpcomingServices(MedicalModels.UpcomingServicesRequest, Map)}.
     *
     * @param request запрос
     * @param meta метаданные ядра
     * @return routing context
     */
    MedicalModels.MedicalOutcome<MedicalModels.MedicalRoutingContext> buildRoutingContext(MedicalModels.BuildRoutingContextRequest request, Map<String, Object> meta);
}
