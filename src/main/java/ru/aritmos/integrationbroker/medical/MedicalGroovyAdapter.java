package ru.aritmos.integrationbroker.medical;

import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.GroovyObjectSupport;
import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.core.FlowEngine;

import java.util.HashMap;
import java.util.Map;

/**
 * Groovy-адаптер медицинского слоя.
 * <p>
 * Экспортируется в Groovy Binding под alias {@code medical}.
 * <p>
 * Пример:
 * <pre>
 * {@code
 * def res = medical.buildRoutingContext([
 *   keys: [[type:'snils', value:'112-233-445 95']],
 *   context: [branchId: meta.branchId]
 * ], meta)
 * output.route = res.result
 * }
 * </pre>
 */
@Singleton
@FlowEngine.GroovyExecutable("medical")
public class MedicalGroovyAdapter extends GroovyObjectSupport {

    private final MedicalService medicalService;
    private final ObjectMapper objectMapper;

    public MedicalGroovyAdapter(MedicalService medicalService, ObjectMapper objectMapper) {
        this.medicalService = medicalService;
        this.objectMapper = objectMapper;
    }

    /**
     * Получить пациента.
     */
    public MedicalModels.MedicalOutcome<MedicalModels.Patient> getPatient(Object request) {
        return getPatient(request, Map.of());
    }

    public MedicalModels.MedicalOutcome<MedicalModels.Patient> getPatient(Object request, Object meta) {
        MedicalModels.GetPatientRequest req = convert(request, MedicalModels.GetPatientRequest.class,
                "Некорректный запрос getPatient: ожидается Map/JSON с полями keys/context");
        return medicalService.getPatient(req, metaMap(meta));
    }

    /**
     * Упрощённый helper: получить пациента только по keys.
     */
    public MedicalModels.MedicalOutcome<MedicalModels.Patient> getPatientByKeys(Object keys, Object meta) {
        java.util.Map<String, Object> req = new java.util.HashMap<>();
        req.put("keys", keys);
        return getPatient(req, meta);
    }


    /**
     * Упрощённый helper: получить пациента по СНИЛС.
     */
    public MedicalModels.MedicalOutcome<MedicalModels.Patient> getPatientBySnils(String snils, Object meta) {
        java.util.Map<String, Object> req = new java.util.HashMap<>();
        req.put("keys", java.util.List.of(java.util.Map.of("type", "snils", "value", snils)));
        return getPatient(req, meta);
    }


    /**
     * Упрощённый helper: получить пациента по patientId.
     */
    public MedicalModels.MedicalOutcome<MedicalModels.Patient> getPatientByPatientId(String patientId, Object meta) {
        java.util.Map<String, Object> req = new java.util.HashMap<>();
        req.put("keys", java.util.List.of(java.util.Map.of("type", "patientId", "value", patientId)));
        return getPatient(req, meta);
    }

    /**
     * Получить предстоящие услуги/этапы.
     */
    public MedicalModels.MedicalOutcome<java.util.List<MedicalModels.UpcomingService>> getUpcomingServices(Object request) {
        return getUpcomingServices(request, Map.of());
    }

    public MedicalModels.MedicalOutcome<java.util.List<MedicalModels.UpcomingService>> getUpcomingServices(Object request, Object meta) {
        MedicalModels.UpcomingServicesRequest req = convert(request, MedicalModels.UpcomingServicesRequest.class,
                "Некорректный запрос getUpcomingServices: ожидается Map/JSON с полями patientId/keys/context");
        return medicalService.getUpcomingServices(req, metaMap(meta));
    }

    /**
     * Упрощённый helper: получить предстоящие услуги по patientId.
     */
    public MedicalModels.MedicalOutcome<java.util.List<MedicalModels.UpcomingService>> getUpcomingServicesByPatient(String patientId,
                                                                                                                     Object meta) {
        java.util.Map<String, Object> req = new java.util.HashMap<>();
        req.put("patientId", patientId);
        return getUpcomingServices(req, meta);
    }


    /**
     * Упрощённый helper: получить предстоящие услуги только по ключам.
     */
    public MedicalModels.MedicalOutcome<java.util.List<MedicalModels.UpcomingService>> getUpcomingServicesByKeys(Object keys,
                                                                                                                  Object meta) {
        java.util.Map<String, Object> req = new java.util.HashMap<>();
        req.put("keys", keys);
        return getUpcomingServices(req, meta);
    }

    /**
     * Собрать routing context.
     */
    public MedicalModels.MedicalOutcome<MedicalModels.MedicalRoutingContext> buildRoutingContext(Object request) {
        return buildRoutingContext(request, Map.of());
    }

    public MedicalModels.MedicalOutcome<MedicalModels.MedicalRoutingContext> buildRoutingContext(Object request, Object meta) {
        MedicalModels.BuildRoutingContextRequest req = convert(request, MedicalModels.BuildRoutingContextRequest.class,
                "Некорректный запрос buildRoutingContext: ожидается Map/JSON с полями keys/patientId/context");
        return medicalService.buildRoutingContext(req, metaMap(meta));
    }


    /**
     * Упрощённый helper: собрать routing context из patientId/keys/context без ручной сборки request.
     */
    public MedicalModels.MedicalOutcome<MedicalModels.MedicalRoutingContext> buildRoutingContextSimple(String patientId,
                                                                                                         Object keys,
                                                                                                         Object context,
                                                                                                         Object meta) {
        java.util.Map<String, Object> req = new java.util.HashMap<>();
        req.put("patientId", patientId);
        req.put("keys", keys);
        req.put("context", context);
        return buildRoutingContext(req, meta);
    }


    /**
     * Упрощённый helper: собрать routing context только по patientId.
     */
    public MedicalModels.MedicalOutcome<MedicalModels.MedicalRoutingContext> buildRoutingContextByPatientId(String patientId,
                                                                                                              Object context,
                                                                                                              Object meta) {
        java.util.Map<String, Object> req = new java.util.HashMap<>();
        req.put("patientId", patientId);
        req.put("context", context);
        return buildRoutingContext(req, meta);
    }


    /**
     * Упрощённый helper: получить предстоящие услуги по patientId и branchId.
     */
    public MedicalModels.MedicalOutcome<java.util.List<MedicalModels.UpcomingService>> getUpcomingServicesByPatientAndBranch(String patientId,
                                                                                                                             String branchId,
                                                                                                                             Object meta) {
        java.util.Map<String, Object> req = new java.util.HashMap<>();
        req.put("patientId", patientId);
        req.put("context", branchId == null ? java.util.Map.of() : java.util.Map.of("branchId", branchId));
        return getUpcomingServices(req, meta);
    }

    private <T> T convert(Object raw, Class<T> clazz, String message) {
        if (raw == null) {
            throw new IllegalArgumentException(message);
        }
        if (clazz.isInstance(raw)) {
            return clazz.cast(raw);
        }
        try {
            return objectMapper.convertValue(raw, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Нормализовать meta: часто приходит как Map из Groovy, нужно приводить ключи к строкам.
     */
    private Map<String, Object> metaMap(Object meta) {
        if (meta == null) {
            return Map.of();
        }
        if (meta instanceof Map<?, ?> m) {
            Map<String, Object> out = new HashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            return Map.copyOf(out);
        }
        return Map.of();
    }
}
