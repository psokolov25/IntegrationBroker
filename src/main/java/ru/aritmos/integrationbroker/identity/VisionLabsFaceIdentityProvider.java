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
 * Провайдер идентификации/обогащения по данным распознавания лица (VisionLabs LUNA PLATFORM).
 * <p>
 * Назначение провайдера:
 * <ul>
 *   <li>принять либо готовый идентификатор лица (faceId), либо изображение (faceImageBase64);</li>
 *   <li>при необходимости вызвать внешнюю систему распознавания для получения faceId;</li>
 *   <li>вернуть нормализованный профиль, содержащий внешний идентификатор (externalIds) и атрибуты.</li>
 * </ul>
 * <p>
 * Важно:
 * <ul>
 *   <li>провайдер сам по себе не обязан выдавать clientId — он может быть получен в других источниках (CRM/МИС);
 *   <li>секреты берутся из {@code runtime-config.restConnectors} и не сохраняются в outbox/DLQ;</li>
 *   <li>base64-изображение не логируется и не сохраняется в результатах.</li>
 * </ul>
 */
@Singleton
public class VisionLabsFaceIdentityProvider implements IdentityProvider {

    /**
     * Тип идентификатора: готовый идентификатор лица.
     */
    public static final String TYPE_FACE_ID = "faceId";

    /**
     * Тип идентификатора: изображение лица в base64.
     */
    public static final String TYPE_FACE_IMAGE_BASE64 = "faceImageBase64";

    private final RuntimeConfigStore configStore;
    private final ObjectMapper objectMapper;
    private final VisionLabsClient client;

    private final AtomicReference<String> cachedRevision = new AtomicReference<>();
    private final AtomicReference<ProviderCfg> cachedCfg = new AtomicReference<>(ProviderCfg.disabled());

    public VisionLabsFaceIdentityProvider(RuntimeConfigStore configStore,
                                          ObjectMapper objectMapper,
                                          VisionLabsClient client) {
        this.configStore = configStore;
        this.objectMapper = objectMapper;
        this.client = client;
    }

    @Override
    public String id() {
        return "visionlabsFace";
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
        return TYPE_FACE_ID.equalsIgnoreCase(t) || TYPE_FACE_IMAGE_BASE64.equalsIgnoreCase(t);
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

        if (TYPE_FACE_ID.equalsIgnoreCase(type.trim())) {
            return Optional.of(profileFromFaceId(value.trim(), pc));
        }

        if (!TYPE_FACE_IMAGE_BASE64.equalsIgnoreCase(type.trim())) {
            return Optional.empty();
        }

        // Запрос в VisionLabs для получения faceId по изображению.
        RuntimeConfigStore.RestConnectorConfig connector = cfg.restConnectors() == null ? null : cfg.restConnectors().get(pc.connectorId);
        if (connector == null) {
            return Optional.empty();
        }

        Map<String, Object> body = pc.buildRequestBody(value, attribute.attributes());
        VisionLabsClient.Response resp = client.postJson(connector, pc.identifyPath, body, pc.timeoutMs);

        // Типовая политика: 404/204 трактуем как «нет совпадения», остальные не-2xx — как ошибка.
        if (resp.httpStatus() == 404 || resp.httpStatus() == 204) {
            return Optional.empty();
        }
        if (resp.httpStatus() < 200 || resp.httpStatus() >= 300) {
            throw new IllegalStateException("VisionLabs(LUNA) вернул HTTP статус " + resp.httpStatus());
        }

        String faceId = extractStringByPointer(resp.json(), pc.responseIdPointer);
        if (faceId == null || faceId.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(profileFromFaceId(faceId.trim(), pc));
    }

    private IdentityModels.IdentityProfile profileFromFaceId(String faceId, ProviderCfg pc) {
        return new IdentityModels.IdentityProfile(
                null,
                Map.of(
                        "vision.faceId", faceId,
                        "vision.vendor", "VisionLabs",
                        "vision.product", "LUNA_PLATFORM"
                ),
                null,
                "DEFAULT",
                null,
                java.util.List.of(),
                Map.of(),
                Map.of(
                        "faceId", faceId,
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
        String ptr = (jsonPointer == null || jsonPointer.isBlank()) ? "/faceId" : jsonPointer.trim();
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
        // На всякий случай: поддержка числовых id.
        if (n.isNumber()) {
            return n.asText();
        }
        return null;
    }

    /**
     * Конфиг провайдера VisionLabs Face.
     */
    private static final class ProviderCfg {
        public boolean enabled;
        public int priority;

        /**
         * Идентификатор REST-коннектора в runtime-config.restConnectors.
         */
        public String connectorId;

        /**
         * Путь endpoint'а идентификации по изображению.
         */
        public String identifyPath;

        /**
         * JSONPointer до поля faceId в ответе.
         */
        public String responseIdPointer;

        /**
         * Поле в JSON тела запроса, куда будет записан base64.
         */
        public String imageFieldName;

        /**
         * Дополнительные поля запроса (например, listId и т.п.).
         */
        public Map<String, Object> extraBody;

        /**
         * Таймаут запроса.
         */
        public int timeoutMs;

        public ProviderCfg() {
        }

        static ProviderCfg disabled() {
            ProviderCfg c = new ProviderCfg();
            c.enabled = false;
            c.priority = 250;
            c.connectorId = "visionlabsLuna";
            c.identifyPath = "/api/identify";
            c.responseIdPointer = "/faceId";
            c.imageFieldName = "imageBase64";
            c.extraBody = Map.of();
            c.timeoutMs = 3000;
            return c;
        }

        ProviderCfg normalize() {
            if (priority <= 0) {
                priority = 250;
            }
            if (connectorId == null || connectorId.isBlank()) {
                connectorId = "visionlabsLuna";
            }
            if (identifyPath == null || identifyPath.isBlank()) {
                identifyPath = "/api/identify";
            }
            if (imageFieldName == null || imageFieldName.isBlank()) {
                imageFieldName = "imageBase64";
            }
            if (responseIdPointer == null || responseIdPointer.isBlank()) {
                responseIdPointer = "/faceId";
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
            // Атрибуты идентификатора могут дополнять тело запроса (но не должны содержать секреты).
            if (attributeAttrs != null && !attributeAttrs.isEmpty()) {
                for (Map.Entry<String, Object> e : attributeAttrs.entrySet()) {
                    if (e.getKey() == null) {
                        continue;
                    }
                    // Ограничение: не перезаписываем поле изображения.
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
