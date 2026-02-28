package ru.aritmos.integrationbroker.appointment;

import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.GroovyObjectSupport;
import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.core.FlowEngine;

import java.util.HashMap;
import java.util.Map;

/**
 * Groovy-адаптер слоя предварительной записи.
 * <p>
 * Экспортируется в Groovy Binding под alias {@code appointment}.
 * <p>
 * Пример:
 * <pre>
 * {@code
 * def nearest = appointment.getNearestAppointment([
 *   keys: [[type:'clientId', value:'CLIENT-001']],
 *   context: [branchId: meta.branchId]
 * ], meta)
 * output.appointmentId = nearest.result().appointmentId()
 * }
 * </pre>
 */
@Singleton
@FlowEngine.GroovyExecutable("appointment")
public class AppointmentGroovyAdapter extends GroovyObjectSupport {

    private final AppointmentService service;
    private final ObjectMapper objectMapper;

    public AppointmentGroovyAdapter(AppointmentService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    public AppointmentModels.AppointmentOutcome<java.util.List<AppointmentModels.Appointment>> getAppointments(Object request) {
        return getAppointments(request, Map.of());
    }

    public AppointmentModels.AppointmentOutcome<java.util.List<AppointmentModels.Appointment>> getAppointments(Object request, Object meta) {
        AppointmentModels.GetAppointmentsRequest req = convert(request, AppointmentModels.GetAppointmentsRequest.class,
                "Некорректный запрос getAppointments: ожидается Map/JSON с полями keys/from/to/context");
        return service.getAppointments(req, metaMap(meta));
    }

    /**
     * Упрощённый helper для flow: получить список записей по набору keys.
     */
    public AppointmentModels.AppointmentOutcome<java.util.List<AppointmentModels.Appointment>> getAppointmentsByKeys(Object keys, Object meta) {
        java.util.Map<String, Object> req = new java.util.HashMap<>();
        req.put("keys", keys);
        return getAppointments(req, meta);
    }

    public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> getNearestAppointment(Object request) {
        return getNearestAppointment(request, Map.of());
    }

    public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> getNearestAppointment(Object request, Object meta) {
        AppointmentModels.GetNearestAppointmentRequest req = convert(request, AppointmentModels.GetNearestAppointmentRequest.class,
                "Некорректный запрос getNearestAppointment: ожидается Map/JSON с полями keys/context");
        return service.getNearestAppointment(req, metaMap(meta));
    }

    public AppointmentModels.AppointmentOutcome<java.util.List<AppointmentModels.Slot>> getAvailableSlots(Object request) {
        return getAvailableSlots(request, Map.of());
    }

    public AppointmentModels.AppointmentOutcome<java.util.List<AppointmentModels.Slot>> getAvailableSlots(Object request, Object meta) {
        AppointmentModels.GetAvailableSlotsRequest req = convert(request, AppointmentModels.GetAvailableSlotsRequest.class,
                "Некорректный запрос getAvailableSlots: ожидается Map/JSON с полями serviceCode/locationId/from/to/context");
        return service.getAvailableSlots(req, metaMap(meta));
    }

    public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> bookSlot(Object request) {
        return bookSlot(request, Map.of());
    }

    public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> bookSlot(Object request, Object meta) {
        AppointmentModels.BookSlotRequest req = convert(request, AppointmentModels.BookSlotRequest.class,
                "Некорректный запрос bookSlot: ожидается Map/JSON с полями slotId/serviceCode/keys/context");
        return service.bookSlot(req, metaMap(meta));
    }

    public AppointmentModels.AppointmentOutcome<Boolean> cancelAppointment(Object request) {
        return cancelAppointment(request, Map.of());
    }

    public AppointmentModels.AppointmentOutcome<Boolean> cancelAppointment(Object request, Object meta) {
        AppointmentModels.CancelAppointmentRequest req = convert(request, AppointmentModels.CancelAppointmentRequest.class,
                "Некорректный запрос cancelAppointment: ожидается Map/JSON с полями appointmentId/reason/context");
        return service.cancelAppointment(req, metaMap(meta));
    }

    public AppointmentModels.AppointmentOutcome<AppointmentModels.QueuePlan> buildQueuePlan(Object request) {
        return buildQueuePlan(request, Map.of());
    }

    public AppointmentModels.AppointmentOutcome<AppointmentModels.QueuePlan> buildQueuePlan(Object request, Object meta) {
        AppointmentModels.BuildQueuePlanRequest req = convert(request, AppointmentModels.BuildQueuePlanRequest.class,
                "Некорректный запрос buildQueuePlan: ожидается Map/JSON с полями appointmentId/keys/context");
        return service.buildQueuePlan(req, metaMap(meta));
    }

    /**
     * Упрощённый helper: построить queue plan по appointmentId/keys/context без ручной сборки request.
     */
    public AppointmentModels.AppointmentOutcome<AppointmentModels.QueuePlan> buildQueuePlanSimple(String appointmentId,
                                                                                                   Object keys,
                                                                                                   Object context,
                                                                                                   Object meta) {
        java.util.Map<String, Object> req = new java.util.HashMap<>();
        req.put("appointmentId", appointmentId);
        req.put("keys", keys);
        req.put("context", context);
        return buildQueuePlan(req, meta);
    }

    /**
     * Упрощённый helper для flow: получить ближайшую запись, передав только ключи клиента.
     */
    public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> getNearestAppointmentByKeys(Object keys, Object meta) {
        java.util.Map<String, Object> req = new java.util.HashMap<>();
        req.put("keys", keys);
        return getNearestAppointment(req, meta);
    }


    /**
     * Упрощённый helper: получить ближайшую запись по clientId.
     */
    public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> getNearestAppointmentByClientId(String clientId,
                                                                                                                Object meta) {
        java.util.Map<String, Object> req = new java.util.HashMap<>();
        req.put("keys", java.util.List.of(java.util.Map.of("type", "clientId", "value", clientId)));
        return getNearestAppointment(req, meta);
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
