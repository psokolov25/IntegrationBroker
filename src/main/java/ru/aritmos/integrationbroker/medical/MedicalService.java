package ru.aritmos.integrationbroker.medical;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.util.List;
import java.util.Map;

/**
 * Сервис медицинского слоя, который выбирает активный профиль и делегирует в соответствующий {@link MedicalClient}.
 * <p>
 * Важно: медицинский слой должен быть отключаемым. В закрытых контурах часто часть интеграций отсутствует,
 * но при этом IB должен продолжать работать (если сценарий не требует medical).
 */
@Singleton
public class MedicalService {

    private final RuntimeConfigStore configStore;
    private final MedicalClientRegistry registry;

    public MedicalService(RuntimeConfigStore configStore, MedicalClientRegistry registry) {
        this.configStore = configStore;
        this.registry = registry;
    }

    public MedicalModels.MedicalOutcome<MedicalModels.Patient> getPatient(MedicalModels.GetPatientRequest request, Map<String, Object> meta) {
        RuntimeConfigStore.MedicalConfig cfg = configStore.getEffective().medical();
        if (cfg == null || !cfg.enabled()) {
            return MedicalModels.MedicalOutcome.disabled("Медицинский слой выключен конфигурацией");
        }
        MedicalClient c = registry.get(cfg.profile());
        return c.getPatient(request, safeMeta(meta));
    }

    public MedicalModels.MedicalOutcome<List<MedicalModels.UpcomingService>> getUpcomingServices(MedicalModels.UpcomingServicesRequest request, Map<String, Object> meta) {
        RuntimeConfigStore.MedicalConfig cfg = configStore.getEffective().medical();
        if (cfg == null || !cfg.enabled()) {
            return MedicalModels.MedicalOutcome.disabled("Медицинский слой выключен конфигурацией");
        }
        MedicalClient c = registry.get(cfg.profile());
        return c.getUpcomingServices(request, safeMeta(meta));
    }

    public MedicalModels.MedicalOutcome<MedicalModels.MedicalRoutingContext> buildRoutingContext(MedicalModels.BuildRoutingContextRequest request, Map<String, Object> meta) {
        RuntimeConfigStore.MedicalConfig cfg = configStore.getEffective().medical();
        if (cfg == null || !cfg.enabled()) {
            return MedicalModels.MedicalOutcome.disabled("Медицинский слой выключен конфигурацией");
        }
        MedicalClient c = registry.get(cfg.profile());
        return c.buildRoutingContext(request, safeMeta(meta));
    }

    private Map<String, Object> safeMeta(Map<String, Object> meta) {
        return meta == null ? Map.of() : meta;
    }
}
