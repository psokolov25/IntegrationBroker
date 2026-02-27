package ru.aritmos.integrationbroker.medical;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;

/**
 * Набор реализаций медицинских клиентов.
 * <p>
 * В этой итерации реальных интеграций нет: для EMIAS/MEDESK/FHIR даём каркасы "NOT_IMPLEMENTED",
 * а для GENERIC — детерминированную заглушку, удобную для разработки flow и тестов.
 * <p>
 * В следующих итерациях сюда добавляются реальные реализации (через restConnectors и typed mapping).
 */
@Singleton
public class MedicalClients {

    private final MedicalClient emiasLike;
    private final MedicalClient medeskLike;
    private final MedicalClient fhirGeneric;

    public MedicalClients(ObjectMapper objectMapper) {
        // objectMapper оставлен на будущее (для маппинга FHIR и адаптеров), но сейчас не используется.
        this.emiasLike = new NotImplementedMedicalClient("EMIAS_LIKE");
        this.medeskLike = new NotImplementedMedicalClient("MEDESK_LIKE");
        this.fhirGeneric = new GenericMedicalClient();
    }

    public MedicalClient emiasLike() {
        return emiasLike;
    }

    public MedicalClient medeskLike() {
        return medeskLike;
    }

    public MedicalClient fhirGeneric() {
        return fhirGeneric;
    }

    /**
     * Заглушка для профилей, которые ещё не реализованы.
     */
    private static final class NotImplementedMedicalClient implements MedicalClient {
        private final String profile;

        private NotImplementedMedicalClient(String profile) {
            this.profile = profile;
        }

        @Override
        public String profileId() {
            return profile;
        }

        @Override
        public MedicalModels.MedicalOutcome<MedicalModels.Patient> getPatient(MedicalModels.GetPatientRequest request, java.util.Map<String, Object> meta) {
            return MedicalModels.MedicalOutcome.notImplemented(profile, "getPatient");
        }

        @Override
        public MedicalModels.MedicalOutcome<java.util.List<MedicalModels.UpcomingService>> getUpcomingServices(MedicalModels.UpcomingServicesRequest request, java.util.Map<String, Object> meta) {
            return MedicalModels.MedicalOutcome.notImplemented(profile, "getUpcomingServices");
        }

        @Override
        public MedicalModels.MedicalOutcome<MedicalModels.MedicalRoutingContext> buildRoutingContext(MedicalModels.BuildRoutingContextRequest request, java.util.Map<String, Object> meta) {
            return MedicalModels.MedicalOutcome.notImplemented(profile, "buildRoutingContext");
        }
    }

    /**
     * Детерминированный "generic" клиент для разработки.
     * <p>
     * Цель: дать предсказуемые данные без внешних зависимостей.
     */
    private static final class GenericMedicalClient implements MedicalClient {

        @Override
        public String profileId() {
            return "FHIR_GENERIC";
        }

        @Override
        public MedicalModels.MedicalOutcome<MedicalModels.Patient> getPatient(MedicalModels.GetPatientRequest request, java.util.Map<String, Object> meta) {
            String key = firstKey(request);
            String pid = "P-" + safeHash(key);
            MedicalModels.Patient p = new MedicalModels.Patient(
                    pid,
                    "Пациент " + (key == null ? "Неизвестный" : key),
                    "1980-01-01",
                    java.util.Map.of("fhirId", pid),
                    java.util.Map.of("source", "generic")
            );
            return MedicalModels.MedicalOutcome.ok(p, java.util.Map.of("profile", profileId()));
        }

        @Override
        public MedicalModels.MedicalOutcome<java.util.List<MedicalModels.UpcomingService>> getUpcomingServices(MedicalModels.UpcomingServicesRequest request, java.util.Map<String, Object> meta) {
            String pid = (request != null && request.patientId() != null && !request.patientId().isBlank())
                    ? request.patientId()
                    : "P-" + safeHash(firstKeyFromKeys(request == null ? null : request.keys()));

            String startBase = deterministicStartAt(pid);
            java.util.List<MedicalModels.UpcomingService> list = java.util.List.of(
                    new MedicalModels.UpcomingService("LAB", "Анализ крови", "Лаборатория", "101", startBase, java.util.Map.of("patientId", pid)),
                    new MedicalModels.UpcomingService("THER", "Осмотр терапевта", "Терапия", "202", shiftIso(startBase, 1800), java.util.Map.of("patientId", pid))
            );
            return MedicalModels.MedicalOutcome.ok(list, java.util.Map.of("profile", profileId(), "count", list.size()));
        }

        @Override
        public MedicalModels.MedicalOutcome<MedicalModels.MedicalRoutingContext> buildRoutingContext(MedicalModels.BuildRoutingContextRequest request, java.util.Map<String, Object> meta) {
            MedicalModels.Patient patient;
            if (request != null && request.patientId() != null && !request.patientId().isBlank()) {
                patient = new MedicalModels.Patient(request.patientId(), "Пациент " + request.patientId(), "1980-01-01", java.util.Map.of("fhirId", request.patientId()), java.util.Map.of("source", "generic"));
            } else {
                patient = getPatient(new MedicalModels.GetPatientRequest(request == null ? java.util.List.of() : request.keys(), request == null ? java.util.Map.of() : request.context()), meta).result();
            }

            java.util.List<MedicalModels.UpcomingService> services = getUpcomingServices(
                    new MedicalModels.UpcomingServicesRequest(patient.patientId(), request == null ? java.util.List.of() : request.keys(), request == null ? java.util.Map.of() : request.context()),
                    meta
            ).result();

            java.util.Map<String, Object> hints = mapWithoutNulls(
                    "routeType", "MULTI_STAGE_EXAM",
                    "recommendedStart", services.isEmpty() ? null : services.get(0).department(),
                    "profile", profileId()
            );

            return MedicalModels.MedicalOutcome.ok(new MedicalModels.MedicalRoutingContext(patient, services, hints), hints);
        }

        private static String deterministicStartAt(String seed) {
            int h = Math.abs(safeHash(seed).hashCode());
            java.time.Instant base = java.time.Instant.parse("2026-01-10T08:00:00Z").plusSeconds(h % 7200L);
            return base.toString();
        }

        private static String shiftIso(String isoInstant, long seconds) {
            if (isoInstant == null || isoInstant.isBlank()) {
                return null;
            }
            return java.time.Instant.parse(isoInstant).plusSeconds(seconds).toString();
        }

        private static java.util.Map<String, Object> mapWithoutNulls(String k1, Object v1,
                                                                     String k2, Object v2,
                                                                     String k3, Object v3) {
            java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
            putIfPresent(out, k1, v1);
            putIfPresent(out, k2, v2);
            putIfPresent(out, k3, v3);
            return out;
        }

        private static void putIfPresent(java.util.Map<String, Object> out, String key, Object value) {
            if (key == null || key.isBlank() || value == null) {
                return;
            }
            out.put(key, value);
        }

        private static String firstKey(MedicalModels.GetPatientRequest request) {
            return firstKeyFromKeys(request == null ? null : request.keys());
        }

        private static String firstKeyFromKeys(java.util.List<MedicalModels.PatientKey> keys) {
            if (keys == null || keys.isEmpty() || keys.get(0) == null) {
                return null;
            }
            MedicalModels.PatientKey k = keys.get(0);
            String t = k.type() == null ? "" : k.type();
            String v = k.value() == null ? "" : k.value();
            return t + ":" + v;
        }

        private static String safeHash(String s) {
            if (s == null || s.isBlank()) {
                return "0000";
            }
            // Дешёвый детерминированный хеш для демо (не криптография).
            int h = 0;
            for (char c : s.toCharArray()) {
                h = (h * 31) + c;
            }
            String hex = Integer.toHexString(h);
            return hex.length() > 8 ? hex.substring(0, 8) : hex;
        }
    }
}
