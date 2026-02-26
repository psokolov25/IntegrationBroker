package ru.aritmos.integrationbroker.appointment;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;

import java.time.Instant;
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
    private final AppointmentClient generic;

    public AppointmentClients(ObjectMapper objectMapper) {
        // objectMapper оставлен на будущее (маппинг под реальные API), но сейчас не используется.
        this.emiasAppointment = new NotImplementedAppointmentClient("EMIAS_APPOINTMENT");
        this.medtochkaLike = new NotImplementedAppointmentClient("MEDTOCHKA_LIKE");
        this.prodoctorovLike = new NotImplementedAppointmentClient("PRODOCTOROV_LIKE");
        this.yclientsLike = new NotImplementedAppointmentClient("YCLIENTS_LIKE");
        this.napopravkuLike = new NotImplementedAppointmentClient("NAPOPRAVKU_LIKE");
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
            AppointmentModels.Appointment a = demoAppointment(firstKey(request == null ? null : request.keys()));
            return AppointmentModels.AppointmentOutcome.ok(List.of(a));
        }

        @Override
        public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> getNearestAppointment(AppointmentModels.GetNearestAppointmentRequest request, Map<String, Object> meta) {
            return AppointmentModels.AppointmentOutcome.ok(demoAppointment(firstKey(request == null ? null : request.keys())));
        }

        @Override
        public AppointmentModels.AppointmentOutcome<List<AppointmentModels.Slot>> getAvailableSlots(AppointmentModels.GetAvailableSlotsRequest request, Map<String, Object> meta) {
            Instant now = Instant.now();
            String svc = request == null ? null : request.serviceCode();
            List<AppointmentModels.Slot> slots = List.of(
                    new AppointmentModels.Slot("SLOT-001", now.plusSeconds(3600), now.plusSeconds(5400), svc == null ? "CONSULT" : svc, Map.of("source", "generic")),
                    new AppointmentModels.Slot("SLOT-002", now.plusSeconds(7200), now.plusSeconds(9000), svc == null ? "CONSULT" : svc, Map.of("source", "generic"))
            );
            return AppointmentModels.AppointmentOutcome.ok(slots);
        }

        @Override
        public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> bookSlot(AppointmentModels.BookSlotRequest request, Map<String, Object> meta) {
            String key = firstKey(request == null ? null : request.keys());
            AppointmentModels.Appointment a = new AppointmentModels.Appointment(
                    "APPT-BOOK-" + safeHash(key),
                    Instant.now().plusSeconds(3600),
                    Instant.now().plusSeconds(5400),
                    request == null || request.serviceCode() == null ? "CONSULT" : request.serviceCode(),
                    "Врач (демо)",
                    "301",
                    "CONFIRMED",
                    Map.of(
                            "slotId", request == null ? null : request.slotId(),
                            "source", "generic"
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

            List<AppointmentModels.QueueStep> steps = List.of(
                    new AppointmentModels.QueueStep("REG", "ZONE-REG", null, "Регистрация клиента", Map.of("order", 1)),
                    new AppointmentModels.QueueStep("APPT", "ZONE-APPT", null, "Приём по записи", Map.of("order", 2, "appointmentId", apptId))
            );

            AppointmentModels.QueuePlan plan = new AppointmentModels.QueuePlan(
                    apptId == null ? "APPT-UNKNOWN" : apptId,
                    segment,
                    steps,
                    Map.of("source", "generic")
            );
            return AppointmentModels.AppointmentOutcome.ok(plan);
        }

        private static AppointmentModels.Appointment demoAppointment(String key) {
            Instant now = Instant.now();
            String apptId = "APPT-" + safeHash(key);
            return new AppointmentModels.Appointment(
                    apptId,
                    now.plusSeconds(1800),
                    now.plusSeconds(3600),
                    "CONSULT",
                    "Врач (демо)",
                    "301",
                    "CONFIRMED",
                    Map.of(
                            "clientKey", key,
                            "source", "generic"
                    )
            );
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
