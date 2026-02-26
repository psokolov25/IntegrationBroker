package ru.aritmos.integrationbroker.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Провайдер идентификации/обогащения по данным распознавания автотранспорта (VisionLabs LUNA CARS).
 * <p>
 * Назначение:
 * <ul>
 *   <li>принять либо готовый номер автомобиля (vehiclePlate), либо изображение автомобиля (vehicleImageBase64);</li>
 *   <li>при необходимости вызвать внешнюю систему распознавания для получения номера (plate);</li>
 *   <li>вернуть нормализованный профиль, содержащий внешний идентификатор (externalIds) и атрибуты.</li>
 * </ul>
 * <p>
 * Важно: номер автомобиля может считаться чувствительным атрибутом.
 * Провайдер не логирует значение и не сохраняет исходное изображение.
 */
@Singleton
public class VisionLabsCarsIdentityProvider implements IdentityProvider {

    /**
     * Тип идентификатора: готовый номер автомобиля.
     */
    public static final String TYPE_VEHICLE_PLATE = "vehiclePlate";

    /**
     * Тип идентификатора: изображение автомобиля/номера в base64.
     */
    public static final String TYPE_VEHICLE_IMAGE_BASE64 = "vehicleImageBase64";

    private final RuntimeConfigStore configStore;
    private final ObjectMapper objectMapper;
    private final VisionLabsClient client;

    private final AtomicReference<String> cachedRevision = new AtomicReference<>();
    private final AtomicReference<ProviderCfg> cachedCfg = new AtomicReference<>(ProviderCfg.disabled());

    public VisionLabsCarsIdentityProvider(RuntimeConfigStore configStore,
                                          ObjectMapper objectMapper,
                                          VisionLabsClient client) {
        this.configStore = configStore;
        this.objectMapper = objectMapper;
        this.client = client;
    }

    @Override
    public String id() {
        return "visionlabsCars";
    }

    @Override
    public int priority() {
        refreshIfNeeded(configStore.getEffective());
        return cachedCfg.get().priority;
    }

    @Override
    public boolean supportsType(String type) {
        refreshIfNeeded(configStore.getEffective());
        ProviderCfg cfg = cachedCfg.get();
        if (!cfg.enabled) {
            return false;
        }
        if (type == null || type.isBlank()) {
            return false;
        }
        String t = type.trim().toLowerCase(Locale.ROOT);
        return TYPE_VEHICLE_PLATE.equalsIgnoreCase(t) || TYPE_VEHICLE_IMAGE_BASE64.equalsIgnoreCase(t);
    }

    @Override
    public Optional<IdentityModels.IdentityProfile> resolve(IdentityModels.IdentityAttribute attribute,
                                                           IdentityModels.IdentityRequest request,
                                                           ProviderContext ctx) {
        if (attribute == null || ctx == null || ctx.cfg() == null) {
            return Optional.empty();
        }
        RuntimeConfigStore.RuntimeConfig cfg = ctx.cfg();
        refreshIfNeeded(cfg);

        ProviderCfg pc = cachedCfg.get();
        if (!pc.enabled) {
            return Optional.empty();
        }

        String type = attribute.type();
        String value = attribute.value();
        if (type == null || type.isBlank() || value == null || value.isBlank()) {
            return Optional.empty();
        }

        if (TYPE_VEHICLE_PLATE.equalsIgnoreCase(type.trim())) {
            return Optional.of(profileFromPlate(value.trim(), pc));
        }

        if (!TYPE_VEHICLE_IMAGE_BASE64.equalsIgnoreCase(type.trim())) {
            return Optional.empty();
        }

        RuntimeConfigStore.RestConnectorConfig connector = cfg.restConnectors() == null ? null : cfg.restConnectors().get(pc.connectorId);
        if (connector == null) {
            return Optional.empty();
        }

        Map<String, Object> body = pc.buildRequestBody(value, attribute.attributes());
        VisionLabsClient.Response resp = client.postJson(connector, pc.recognizePath, body, pc.timeoutMs);

        if (resp.httpStatus() == 404 || resp.httpStatus() == 204) {
            return Optional.empty();
        }
        if (resp.httpStatus() < 200 || resp.httpStatus() >= 300) {
            throw new IllegalStateException("VisionLabs(CARS) вернул HTTP статус " + resp.httpStatus());
        }

        String plate = extractStringByPointer(resp.json(), pc.responsePlatePointer);
        if (plate == null || plate.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(profileFromPlate(plate.trim(), pc));
    }

    private IdentityModels.IdentityProfile profileFromPlate(String plate, ProviderCfg pc) {
        return new IdentityModels.IdentityProfile(
                null,
                Map.of(
                        "vision.vehiclePlate", plate,
                        "vision.vendor", "VisionLabs",
                        "vision.product", "LUNA_CARS"
                ),
                null,
                "DEFAULT",
                null,
                java.util.List.of(),
                Map.of(),
                Map.of(
                        "vehiclePlate", plate,
                        "visionProviderId", id(),
                        "visionConnectorId", pc.connectorId
                )
        );
    }

    private void refreshIfNeeded(RuntimeConfigStore.RuntimeConfig cfg) {
        if (cfg == null) {
            return;
        }
        String rev = cfg.revision();
        String prev = cachedRevision.get();
        if (rev != null && rev.equals(prev)) {
            return;
        }

        ProviderCfg pc = parseCfg(cfg);
        cachedCfg.set(pc);
        cachedRevision.set(rev);
    }

    private ProviderCfg parseCfg(RuntimeConfigStore.RuntimeConfig cfg) {
        Map<String, Object> providers = cfg.identity() == null ? Map.of() : cfg.identity().providers();
        Object raw = providers == null ? null : providers.get(id());
        if (raw == null) {
            return ProviderCfg.disabled();
        }
        try {
            ProviderCfg pc = objectMapper.convertValue(raw, ProviderCfg.class);
            return pc == null ? ProviderCfg.disabled() : pc.normalize();
        } catch (Exception e) {
            return ProviderCfg.disabled();
        }
    }

    private static String extractStringByPointer(JsonNode json, String jsonPointer) {
        if (json == null) {
            return null;
        }
        String ptr = (jsonPointer == null || jsonPointer.isBlank()) ? "/plate" : jsonPointer.trim();
        if (!ptr.startsWith("/")) {
            ptr = "/" + ptr;
        }
        JsonNode n = json.at(ptr);
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        if (n.isTextual()) {
            return n.asText();
        }
        if (n.isNumber()) {
            return n.asText();
        }
        return null;
    }

    /**
     * Конфиг провайдера VisionLabs Cars.
     */
    private static final class ProviderCfg {
        public boolean enabled;
        public int priority;

        public String connectorId;
        public String recognizePath;
        public String responsePlatePointer;
        public String imageFieldName;
        public Map<String, Object> extraBody;
        public int timeoutMs;

        public ProviderCfg() {
        }

        static ProviderCfg disabled() {
            ProviderCfg c = new ProviderCfg();
            c.enabled = false;
            c.priority = 240;
            c.connectorId = "visionlabsCars";
            c.recognizePath = "/api/recognize";
            c.responsePlatePointer = "/plate";
            c.imageFieldName = "imageBase64";
            c.extraBody = Map.of();
            c.timeoutMs = 3000;
            return c;
        }

        ProviderCfg normalize() {
            if (priority <= 0) {
                priority = 240;
            }
            if (connectorId == null || connectorId.isBlank()) {
                connectorId = "visionlabsCars";
            }
            if (recognizePath == null || recognizePath.isBlank()) {
                recognizePath = "/api/recognize";
            }
            if (imageFieldName == null || imageFieldName.isBlank()) {
                imageFieldName = "imageBase64";
            }
            if (responsePlatePointer == null || responsePlatePointer.isBlank()) {
                responsePlatePointer = "/plate";
            }
            if (extraBody == null) {
                extraBody = Map.of();
            }
            if (timeoutMs <= 0) {
                timeoutMs = 3000;
            }
            return this;
        }

        Map<String, Object> buildRequestBody(String imageBase64, Map<String, Object> attributeAttrs) {
            java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
            if (extraBody != null) {
                m.putAll(extraBody);
            }
            if (attributeAttrs != null && !attributeAttrs.isEmpty()) {
                for (Map.Entry<String, Object> e : attributeAttrs.entrySet()) {
                    if (e.getKey() == null) {
                        continue;
                    }
                    if (e.getKey().equals(imageFieldName)) {
                        continue;
                    }
                    m.put(e.getKey(), e.getValue());
                }
            }
            m.put(imageFieldName, imageBase64);
            return Map.copyOf(m);
        }
    }
}
