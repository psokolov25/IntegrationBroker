package ru.aritmos.integrationbroker.appointment;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Набор реализаций клиентов слоя предварительной записи.
 * <p>
 * В этой итерации реальных интеграций нет: для EMIAS/YCLIENTS/и т.п. даём каркасы "NOT_IMPLEMENTED",
 * а для GENERIC — детерминированную заглушку, удобную для разработки flow и тестов.
 */
@Singleton
public class AppointmentClients {

    private final AppointmentClient emiasAppointment;
    private final AppointmentClient medtochkaLike;
    private final AppointmentClient prodoctorovLike;
    private final AppointmentClient yclientsLike;
    private final AppointmentClient napopravkuLike;
    private final AppointmentClient customConnector;
    private final AppointmentClient generic;

    public AppointmentClients(ObjectMapper objectMapper) {
        // objectMapper оставлен на будущее (маппинг под реальные API), но сейчас не используется.
        this.emiasAppointment = new NotImplementedAppointmentClient("EMIAS_APPOINTMENT");
        this.medtochkaLike = new NotImplementedAppointmentClient("MEDTOCHKA_LIKE");
        this.prodoctorovLike = new NotImplementedAppointmentClient("PRODOCTOROV_LIKE");
        this.yclientsLike = new NotImplementedAppointmentClient("YCLIENTS_LIKE");
        this.napopravkuLike = new NotImplementedAppointmentClient("NAPOPRAVKU_LIKE");
        this.customConnector = new NotImplementedAppointmentClient("CUSTOM_CONNECTOR");
        this.generic = new GenericAppointmentClient();
    }

    public AppointmentClient emiasAppointment() {
        return emiasAppointment;
    }

    public AppointmentClient medtochkaLike() {
        return medtochkaLike;
    }

    public AppointmentClient prodoctorovLike() {
        return prodoctorovLike;
    }

    public AppointmentClient yclientsLike() {
        return yclientsLike;
    }

    public AppointmentClient napopravkuLike() {
        return napopravkuLike;
    }

    public AppointmentClient customConnector() {
        return customConnector;
    }

    public AppointmentClient generic() {
        return generic;
    }

    /**
     * Заглушка для профилей, которые ещё не реализованы.
     */
    private static final class NotImplementedAppointmentClient implements AppointmentClient {
        private final String profile;

        private NotImplementedAppointmentClient(String profile) {
            this.profile = profile;
        }

        @Override
        public AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> getAppointments(AppointmentModels.GetAppointmentsRequest request, Map<String, Object> meta) {
            return AppointmentModels.AppointmentOutcome.notImplemented(profile + ": getAppointments не реализован");
        }

        @Override
        public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> getNearestAppointment(AppointmentModels.GetNearestAppointmentRequest request, Map<String, Object> meta) {
            return AppointmentModels.AppointmentOutcome.notImplemented(profile + ": getNearestAppointment не реализован");
        }

        @Override
        public AppointmentModels.AppointmentOutcome<List<AppointmentModels.Slot>> getAvailableSlots(AppointmentModels.GetAvailableSlotsRequest request, Map<String, Object> meta) {
            return AppointmentModels.AppointmentOutcome.notImplemented(profile + ": getAvailableSlots не реализован");
        }

        @Override
        public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> bookSlot(AppointmentModels.BookSlotRequest request, Map<String, Object> meta) {
            return AppointmentModels.AppointmentOutcome.notImplemented(profile + ": bookSlot не реализован");
        }

        @Override
        public AppointmentModels.AppointmentOutcome<Boolean> cancelAppointment(AppointmentModels.CancelAppointmentRequest request, Map<String, Object> meta) {
            return AppointmentModels.AppointmentOutcome.notImplemented(profile + ": cancelAppointment не реализован");
        }

        @Override
        public AppointmentModels.AppointmentOutcome<AppointmentModels.QueuePlan> buildQueuePlan(AppointmentModels.BuildQueuePlanRequest request, Map<String, Object> meta) {
            return AppointmentModels.AppointmentOutcome.notImplemented(profile + ": buildQueuePlan не реализован");
        }
    }

    /**
     * Детерминированный "generic" клиент для разработки.
     * <p>
     * Цель: дать предсказуемые данные без внешних зависимостей.
     */
    private static final class GenericAppointmentClient implements AppointmentClient {

        @Override
        public AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> getAppointments(AppointmentModels.GetAppointmentsRequest request, Map<String, Object> meta) {
            String key = firstKey(request == null ? null : request.keys());
            String serviceCode = resolveServiceCode(request == null ? null : request.context(), "CONSULT");
            String branchId = resolveBranchId(request == null ? null : request.context());

            List<AppointmentModels.Appointment> generated = List.of(
                    demoAppointment(key, serviceCode, branchId),
                    appointmentWithOffset(key, serviceCode, branchId, 3600, 5400, "APPT-FOLLOW-UP", "302"),
                    appointmentWithOffset(key, serviceCode, branchId, -7200, -5400, "APPT-HISTORY", "300")
            );

            Instant from = request == null ? null : request.from();
            Instant to = request == null ? null : request.to();

            List<AppointmentModels.Appointment> filtered = generated.stream()
                    .filter(a -> inRange(a.startAt(), from, to))
                    .sorted(Comparator.comparing(AppointmentModels.Appointment::startAt))
                    .toList();

            return AppointmentModels.AppointmentOutcome.ok(filtered);
        }

        @Override
        public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> getNearestAppointment(AppointmentModels.GetNearestAppointmentRequest request, Map<String, Object> meta) {
            String key = firstKey(request == null ? null : request.keys());
            String serviceCode = resolveServiceCode(request == null ? null : request.context(), "CONSULT");
            String branchId = resolveBranchId(request == null ? null : request.context());
            List<AppointmentModels.Appointment> candidates = List.of(
                    demoAppointment(key, serviceCode, branchId),
                    appointmentWithOffset(key, serviceCode, branchId, 2700, 4500, "APPT-ALT", "302")
            );
            AppointmentModels.Appointment nearest = candidates.stream()
                    .filter(a -> "CONFIRMED".equalsIgnoreCase(a.status()))
                    .min(Comparator.comparing(AppointmentModels.Appointment::startAt))
                    .orElseGet(() -> demoAppointment(key, serviceCode, branchId));
            return AppointmentModels.AppointmentOutcome.ok(nearest);
        }

        @Override
        public AppointmentModels.AppointmentOutcome<List<AppointmentModels.Slot>> getAvailableSlots(AppointmentModels.GetAvailableSlotsRequest request, Map<String, Object> meta) {
            String svc = request == null ? null : request.serviceCode();
            Instant now = deterministicBaseTime(svc);
            String locationId = request == null ? null : request.locationId();

            List<AppointmentModels.Slot> generated = List.of(
                    new AppointmentModels.Slot("SLOT-001", now.plusSeconds(3600), now.plusSeconds(5400), svc == null ? "CONSULT" : svc,
                            metadata("source", "generic", "locationId", locationId)),
                    new AppointmentModels.Slot("SLOT-002", now.plusSeconds(7200), now.plusSeconds(9000), svc == null ? "CONSULT" : svc,
                            metadata("source", "generic", "locationId", locationId)),
                    new AppointmentModels.Slot("SLOT-003", now.plusSeconds(10_800), now.plusSeconds(12_600), svc == null ? "CONSULT" : svc,
                            metadata("source", "generic", "locationId", locationId))
            );

            Instant from = request == null ? null : request.from();
            Instant to = request == null ? null : request.to();

            List<AppointmentModels.Slot> slots = generated.stream()
                    .filter(s -> inRange(s.startAt(), from, to))
                    .sorted(Comparator.comparing(AppointmentModels.Slot::startAt))
                    .toList();
            return AppointmentModels.AppointmentOutcome.ok(slots);
        }

        @Override
        public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> bookSlot(AppointmentModels.BookSlotRequest request, Map<String, Object> meta) {
            String key = firstKey(request == null ? null : request.keys());
            AppointmentModels.Appointment a = new AppointmentModels.Appointment(
                    "APPT-BOOK-" + safeHash(key),
                    deterministicBaseTime(key).plusSeconds(3600),
                    deterministicBaseTime(key).plusSeconds(5400),
                    request == null || request.serviceCode() == null ? "CONSULT" : request.serviceCode(),
                    "Врач (демо)",
                    "301",
                    "CONFIRMED",
                    mergeMetadata(
                            metadata("source", "generic", "slotId", request == null ? null : request.slotId()),
                            metadata("branchId", resolveBranchId(request == null ? null : request.context()), "requestedService", request == null ? null : request.serviceCode())
                    )
            );
            return AppointmentModels.AppointmentOutcome.ok(a);
        }

        @Override
        public AppointmentModels.AppointmentOutcome<Boolean> cancelAppointment(AppointmentModels.CancelAppointmentRequest request, Map<String, Object> meta) {
            if (request == null || request.appointmentId() == null || request.appointmentId().isBlank()) {
                return AppointmentModels.AppointmentOutcome.error("appointmentId обязателен для cancelAppointment");
            }
            return AppointmentModels.AppointmentOutcome.ok(Boolean.TRUE);
        }

        @Override
        public AppointmentModels.AppointmentOutcome<AppointmentModels.QueuePlan> buildQueuePlan(AppointmentModels.BuildQueuePlanRequest request, Map<String, Object> meta) {
            String apptId = request == null ? null : request.appointmentId();
            String segment = (request != null && request.context() != null && request.context().get("segment") != null)
                    ? String.valueOf(request.context().get("segment"))
                    : "DEFAULT";
            String branchId = resolveBranchId(request == null ? null : request.context());

            List<AppointmentModels.QueueStep> steps = List.of(
                    new AppointmentModels.QueueStep("REG", "ZONE-REG", null, "Регистрация клиента", metadataEntries(entry("order", 1), entry("branchId", branchId))),
                    new AppointmentModels.QueueStep("APPT", "ZONE-APPT", null, "Приём по записи", metadataEntries(entry("order", 2), entry("appointmentId", apptId)))
            );

            AppointmentModels.QueuePlan plan = new AppointmentModels.QueuePlan(
                    apptId == null ? "APPT-UNKNOWN" : apptId,
                    segment,
                    steps,
                    metadata("source", "generic", "branchId", branchId)
            );
            return AppointmentModels.AppointmentOutcome.ok(plan);
        }

        private static AppointmentModels.Appointment demoAppointment(String key, String serviceCode, String branchId) {
            Instant now = deterministicBaseTime(key);
            String apptId = "APPT-" + safeHash(key);
            return new AppointmentModels.Appointment(
                    apptId,
                    now.plusSeconds(1800),
                    now.plusSeconds(3600),
                    serviceCode,
                    "Врач (демо)",
                    "301",
                    "CONFIRMED",
                    mergeMetadata(
                            metadata("source", "generic", "clientKey", key),
                            metadata("branchId", branchId, "timeline", "nearest")
                    )
            );
        }

        private static AppointmentModels.Appointment appointmentWithOffset(String key,
                                                                           String serviceCode,
                                                                           String branchId,
                                                                           long fromOffsetSeconds,
                                                                           long toOffsetSeconds,
                                                                           String idSuffix,
                                                                           String room) {
            Instant base = deterministicBaseTime(key);
            return new AppointmentModels.Appointment(
                    idSuffix + "-" + safeHash(key),
                    base.plusSeconds(fromOffsetSeconds),
                    base.plusSeconds(toOffsetSeconds),
                    serviceCode,
                    "Врач (демо)",
                    room,
                    "CONFIRMED",
                    metadata("source", "generic", "branchId", branchId)
            );
        }

        private static String resolveServiceCode(Map<String, Object> context, String fallback) {
            if (context != null && context.get("serviceCode") != null) {
                String value = String.valueOf(context.get("serviceCode"));
                if (!value.isBlank()) {
                    return value;
                }
            }
            return fallback;
        }

        private static String resolveBranchId(Map<String, Object> context) {
            if (context != null && context.get("branchId") != null) {
                String value = String.valueOf(context.get("branchId"));
                if (!value.isBlank()) {
                    return value;
                }
            }
            return null;
        }

        private static boolean inRange(Instant value, Instant from, Instant to) {
            if (value == null) {
                return false;
            }
            boolean fromOk = from == null || !value.isBefore(from);
            boolean toOk = to == null || !value.isAfter(to);
            return fromOk && toOk;
        }

        private static Instant deterministicBaseTime(String key) {
            int seed = Math.abs(safeHash(key).hashCode());
            long offsetSeconds = seed % 86_400L;
            return Instant.parse("2026-01-01T09:00:00Z").plusSeconds(offsetSeconds);
        }

        private static Map<String, Object> metadata(String key1, String value1, String key2, String value2) {
            Map<String, Object> meta = new LinkedHashMap<>();
            if (key1 != null && value1 != null && !key1.isBlank() && !value1.isBlank()) {
                meta.put(key1, value1);
            }
            if (key2 != null && value2 != null && !key2.isBlank() && !value2.isBlank()) {
                meta.put(key2, value2);
            }
            return meta;
        }

        private static Map<String, Object> mergeMetadata(Map<String, Object> left, Map<String, Object> right) {
            Map<String, Object> out = new LinkedHashMap<>();
            if (left != null) {
                out.putAll(left);
            }
            if (right != null) {
                out.putAll(right);
            }
            return out;
        }

        @SafeVarargs
        private static Map<String, Object> metadataEntries(Map.Entry<String, Object>... entries) {
            Map<String, Object> out = new LinkedHashMap<>();
            if (entries == null) {
                return out;
            }
            for (Map.Entry<String, Object> e : entries) {
                if (e == null || e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) {
                    continue;
                }
                if (e.getValue() instanceof String str && str.isBlank()) {
                    continue;
                }
                out.put(e.getKey(), e.getValue());
            }
            return out;
        }

        private static Map.Entry<String, Object> entry(String key, Object value) {
            return new java.util.AbstractMap.SimpleEntry<>(key, value);
        }

        private static String firstKey(List<AppointmentModels.BookingKey> keys) {
            if (keys == null || keys.isEmpty() || keys.get(0) == null) {
                return null;
            }
            AppointmentModels.BookingKey k = keys.get(0);
            String t = k.type() == null ? "" : k.type();
            String v = k.value() == null ? "" : k.value();
            return t + ":" + v;
        }

        private static String safeHash(String s) {
            if (s == null || s.isBlank()) {
                return "0000";
            }
            int h = 0;
            for (char c : s.toCharArray()) {
                h = (h * 31) + c;
            }
            String hex = Integer.toHexString(h);
            return hex.length() > 8 ? hex.substring(0, 8) : hex;
        }
    }
}
