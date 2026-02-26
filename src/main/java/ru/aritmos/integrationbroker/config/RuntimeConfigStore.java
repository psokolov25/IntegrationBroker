package ru.aritmos.integrationbroker.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Runtime-хранилище конфигурации Integration Broker.
 * <p>
 * Поддерживаются два режима:
 * <ol>
 *   <li>Локальная конфигурация (classpath), используемая как baseline.</li>
 *   <li>Удалённая конфигурация через SystemConfiguration (опционально), с periodic refresh и ETag.</li>
 * </ol>
 * <p>
 * Дополнительные требования:
 * <ul>
 *   <li>Поддержка «обёрток»: value/data/config/payload/settings.</li>
 *   <li>Поддержка случая, когда value приходит как JSON-строка.</li>
 *   <li>Безопасное поведение при ошибках (fallback на предыдущую effective-конфигурацию).</li>
 * </ul>
 */
@Singleton
public class RuntimeConfigStore {

    private static final Logger log = LoggerFactory.getLogger(RuntimeConfigStore.class);

    private final ResourceResolver resourceResolver;
    private final ObjectMapper objectMapper;
    private final HttpClient remoteClient;

    private final String localPath;
    private final boolean remoteEnabled;
    private final String remotePath;

    private final AtomicReference<RuntimeConfig> effective = new AtomicReference<>();
    private volatile String lastEtag;

    public RuntimeConfigStore(ResourceResolver resourceResolver,
                             ObjectMapper objectMapper,
                             @Client("${integrationbroker.remote-config.base-url:http://system-configuration:8080}") HttpClient remoteClient,
                             @Value("${integrationbroker.local-config.path:classpath:examples/sample-system-config.json}") String localPath,
                             @Value("${integrationbroker.remote-config.enabled:false}") boolean remoteEnabled,
                             @Value("${integrationbroker.remote-config.path:/configuration/config/system/integrationbroker}") String remotePath) {
        this.resourceResolver = resourceResolver;
        this.objectMapper = objectMapper;
        this.remoteClient = remoteClient;
        this.localPath = localPath;
        this.remoteEnabled = remoteEnabled;
        this.remotePath = remotePath;
    }

    /**
     * Инициализация effective-конфигурации.
     * <p>
     * Сначала загружается локальная конфигурация (baseline), затем (если включено) выполняется попытка
     * загрузить удалённую конфигурацию.
     */
    @PostConstruct
    void init() {
        RuntimeConfig local = loadLocal();
        effective.set(local);
        if (remoteEnabled) {
            refreshRemote();
        }
    }

    /**
     * Периодическое обновление удалённой конфигурации.
     * <p>
     * Важно: ошибка обновления не должна приводить к падению сервиса сама по себе.
     * Fail-fast по remote config — отдельное требование, будет добавлено отдельной итерацией (checker).
     */
    @Scheduled(fixedDelay = "${integrationbroker.remote-config.refresh-interval-sec:20}s")
    void scheduledRefresh() {
        if (!remoteEnabled) {
            return;
        }
        refreshRemote();
    }

    /**
     * @return актуальная effective-конфигурация (никогда не {@code null} после init)
     */
    public RuntimeConfig getEffective() {
        RuntimeConfig cfg = effective.get();
        if (cfg == null) {
            // На практике этого быть не должно: init всегда устанавливает baseline.
            return new RuntimeConfig(
                    "fallback",
                    List.of(),
                    new IdempotencyConfig(true, IdempotencyStrategy.AUTO, 60),
                    new InboundDlqConfig(true, 10, true),
                    new KeycloakProxyEnrichmentConfig(false,
                            false,
                            "keycloakProxy",
                            List.of(KeycloakProxyFetchMode.USER_ID_HEADER, KeycloakProxyFetchMode.BEARER_TOKEN),
                            "x-user-id",
                            "Authorization",
                            "/authorization/users/{userName}",
                            "/authentication/userInfo",
                            true,
                            60,
                            5000,
                            true,
                            List.of("branchId", "branch_id", "officeId")),
                    new MessagingOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50),
                    new RestOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50, "Idempotency-Key", "409"),
                    Map.of(),
                    CrmConfig.disabled(),
                    MedicalConfig.disabled(),
                    AppointmentConfig.disabled(),
                    IdentityConfig.defaultConfig(),
                    VisionLabsAnalyticsConfig.disabled(),
                    BranchResolutionConfig.defaultConfig(),
                    VisitManagerIntegrationConfig.disabled(),
                    DataBusIntegrationConfig.disabled()
            );
        }
        return cfg;
    }

    /**
     * @return включён ли режим удалённой конфигурации (SystemConfiguration)
     */
    public boolean isRemoteEnabled() {
        return remoteEnabled;
    }

    /**
     * Строгая проверка доступности удалённой конфигурации.
     * <p>
     * Метод предназначен для режима fail-fast: если remote-config включён и помечен как критичная зависимость,
     * то на старте сервиса можно потребовать успешную загрузку конфигурации.
     * <p>
     * Поведение:
     * <ul>
     *   <li>HTTP 304 трактуется как «доступно, изменений нет»;</li>
     *   <li>HTTP 200 требует непустого тела и корректного JSON;</li>
     *   <li>любая ошибка приводит к {@link IllegalStateException}.</li>
     * </ul>
     */
    public void assertRemoteAvailable() {
        if (!remoteEnabled) {
            throw new IllegalStateException("remote-config отключён (integrationbroker.remote-config.enabled=false)");
        }

        try {
            io.micronaut.http.MutableHttpRequest<?> req = HttpRequest.GET(remotePath);
            if (lastEtag != null && !lastEtag.isBlank()) {
                req = req.header(HttpHeaders.IF_NONE_MATCH, lastEtag);
            }

            HttpResponse<byte[]> resp = remoteClient.toBlocking().exchange(req, byte[].class);

            int sc = resp.getStatus().getCode();
            if (sc == 304) {
                return;
            }
            if (sc < 200 || sc >= 300) {
                throw new IllegalStateException("remote-config вернул неожиданный HTTP статус: " + sc);
            }

            String etag = resp.getHeaders().get(HttpHeaders.ETAG);
            if (etag != null && !etag.isBlank()) {
                lastEtag = etag;
            }

            byte[] body = resp.body();
            if (body == null || body.length == 0) {
                throw new IllegalStateException("remote-config вернул пустое тело");
            }

            JsonNode root = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
            JsonNode cfgNode = unwrapEnvelope(root);
            RuntimeConfig parsed = objectMapper.treeToValue(cfgNode, RuntimeConfig.class);
            effective.set(parsed.normalize());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось загрузить remote-config: " + e.getMessage(), e);
        }
    }

    private RuntimeConfig loadLocal() {
        Optional<InputStream> streamOpt = resourceResolver.getResourceAsStream(localPath);
        if (streamOpt.isEmpty() && localPath != null && !localPath.startsWith("classpath:")) {
            streamOpt = resourceResolver.getResourceAsStream("classpath:" + localPath);
        }
        if (streamOpt.isEmpty() && localPath != null && localPath.startsWith("classpath:")) {
            String stripped = localPath.substring("classpath:".length());
            if (stripped.startsWith("/")) {
                stripped = stripped.substring(1);
            }
            streamOpt = resourceResolver.getResourceAsStream(stripped);
        }
        if (streamOpt.isEmpty()) {
            throw new IllegalStateException("Не найден локальный конфиг Integration Broker по пути: " + localPath);
        }
        try (InputStream is = streamOpt.get()) {
            JsonNode root = objectMapper.readTree(is);
            JsonNode cfgNode = unwrapEnvelope(root);
            RuntimeConfig parsed = objectMapper.treeToValue(cfgNode, RuntimeConfig.class);
            return parsed.normalize();
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось загрузить локальную конфигурацию Integration Broker: " + e.getMessage(), e);
        }
    }

    private void refreshRemote() {
        try {
            io.micronaut.http.MutableHttpRequest<?> req = HttpRequest.GET(remotePath);
            if (lastEtag != null && !lastEtag.isBlank()) {
                req = req.header(HttpHeaders.IF_NONE_MATCH, lastEtag);
            }

            HttpResponse<byte[]> resp = remoteClient.toBlocking().exchange(req, byte[].class);

            if (resp.getStatus().getCode() == 304) {
                return;
            }

            String etag = resp.getHeaders().get(HttpHeaders.ETAG);
            if (etag != null && !etag.isBlank()) {
                lastEtag = etag;
            }

            byte[] body = resp.body();
            if (body == null || body.length == 0) {
                log.warn("Удалённая конфигурация Integration Broker вернула пустое тело. Оставляем предыдущую effective-конфигурацию.");
                return;
            }

            JsonNode root = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
            JsonNode cfgNode = unwrapEnvelope(root);
            RuntimeConfig parsed = objectMapper.treeToValue(cfgNode, RuntimeConfig.class);
            effective.set(parsed.normalize());
        } catch (Exception e) {
            // Важно: не логируем потенциально чувствительные данные.
            log.warn("Не удалось обновить удалённую конфигурацию Integration Broker (fallback на предыдущую): {}", e.getMessage());
        }
    }

    /**
     * Поддержка типовых «обёрток» конфигурации.
     * <p>
     * SystemConfiguration и внешние источники часто возвращают конфиг как:
     * <ul>
     *   <li>{"value": {...}} или {"value": "{...json...}"}</li>
     *   <li>{"data": {...}}</li>
     *   <li>{"config": {...}}</li>
     *   <li>{"payload": {...}}</li>
     *   <li>{"settings": {...}}</li>
     * </ul>
     *
     * @param root корневой JSON
     * @return JSON узел, который следует трактовать как «реальный конфиг»
     */
    private JsonNode unwrapEnvelope(JsonNode root) {
        if (root == null) {
            return objectMapper.createObjectNode();
        }

        JsonNode candidate = root;
        for (String key : new String[]{"value", "data", "config", "payload", "settings"}) {
            if (candidate.has(key) && !candidate.get(key).isNull()) {
                candidate = candidate.get(key);
                break;
            }
        }

        // Вариант: value приходит как JSON-строка.
        if (candidate.isTextual()) {
            try {
                return objectMapper.readTree(candidate.asText());
            } catch (Exception e) {
                // Если строка не JSON — возвращаем как есть.
                return candidate;
            }
        }

        return candidate;
    }

    /**
     * Effective-конфигурация Integration Broker.
     * <p>
     * Структура конфига специально сделана консервативной и расширяемой.
     */
    public record RuntimeConfig(
            String revision,
            List<FlowConfig> flows,
            IdempotencyConfig idempotency,
            InboundDlqConfig inboundDlq,
            KeycloakProxyEnrichmentConfig keycloakProxy,
            MessagingOutboxConfig messagingOutbox,
            RestOutboxConfig restOutbox,
            Map<String, RestConnectorConfig> restConnectors,
            CrmConfig crm,
            MedicalConfig medical,
            AppointmentConfig appointment,
            IdentityConfig identity,
            VisionLabsAnalyticsConfig visionLabsAnalytics,
            BranchResolutionConfig branchResolution,
            VisitManagerIntegrationConfig visitManager,
            DataBusIntegrationConfig dataBus
    ) {
        public RuntimeConfig normalize() {
            IdempotencyConfig idem = (idempotency == null)
                    ? new IdempotencyConfig(true, IdempotencyStrategy.AUTO, 60)
                    : idempotency;

            InboundDlqConfig dlq = (inboundDlq == null)
                    ? new InboundDlqConfig(true, 10, true)
                    : inboundDlq;

            KeycloakProxyEnrichmentConfig kc = (keycloakProxy == null)
                    ? new KeycloakProxyEnrichmentConfig(
                    false,
                    false,
                    "keycloakProxy",
                    List.of(KeycloakProxyFetchMode.USER_ID_HEADER, KeycloakProxyFetchMode.BEARER_TOKEN),
                    "x-user-id",
                    "Authorization",
                    "/authorization/users/{userName}",
                    "/authentication/userInfo",
                    true,
                    60,
                    5000,
                    true,
                    List.of("branchId", "branch_id", "officeId")
            )
                    : keycloakProxy;

            MessagingOutboxConfig msgOut = (messagingOutbox == null)
                    ? new MessagingOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50)
                    : messagingOutbox;

            RestOutboxConfig restOut = (restOutbox == null)
                    ? new RestOutboxConfig(false, "ON_FAILURE", 10, 5, 600, 50, "Idempotency-Key", "409")
                    : restOutbox;
            List<FlowConfig> fs = (flows == null) ? List.of() : flows;
            Map<String, RestConnectorConfig> connectors = (restConnectors == null) ? Map.of() : restConnectors;

            CrmConfig crmCfg = (crm == null)
                    ? CrmConfig.disabled()
                    : crm;

            
            MedicalConfig medicalCfg = (medical == null)
                    ? MedicalConfig.disabled()
                    : medical;

            AppointmentConfig appointmentCfg = (appointment == null)
                    ? AppointmentConfig.disabled()
                    : appointment;


            IdentityConfig identityCfg = (identity == null)
                    ? IdentityConfig.defaultConfig()
                    : identity;

            VisionLabsAnalyticsConfig vl = (visionLabsAnalytics == null)
                    ? VisionLabsAnalyticsConfig.disabled()
                    : visionLabsAnalytics;

            BranchResolutionConfig br = (branchResolution == null)
                    ? BranchResolutionConfig.defaultConfig()
                    : branchResolution;

            VisitManagerIntegrationConfig vm = (visitManager == null)
                    ? VisitManagerIntegrationConfig.disabled()
                    : visitManager;

            DataBusIntegrationConfig db = (dataBus == null)
                    ? DataBusIntegrationConfig.disabled()
                    : dataBus;

            return new RuntimeConfig(revision == null ? "unknown" : revision, fs, idem, dlq, kc, msgOut, restOut, connectors, crmCfg, medicalCfg, appointmentCfg, identityCfg, vl, br, vm, db);
        }

        /**
         * Индекс flow по ключу "KIND:TYPE".
         *
         * @return карта flow, пригодная для быстрого разрешения
         */
        public Map<String, FlowConfig> flowIndex() {
            return flows.stream()
                    .filter(f -> f != null && f.enabled())
                    .filter(f -> f.selector() != null && f.selector().kind() != null && f.selector().type() != null)
                    .collect(Collectors.toMap(
                            f -> f.selector().kind() + ":" + f.selector().type(),
                            f -> f,
                            (a, b) -> a
                    ));
        }
    }

    /**
     * Описание flow.
     */
    public record FlowConfig(
            String id,
            boolean enabled,
            Selector selector,
            Map<String, Object> metadata,
            String groovy
    ) {
    }

    /**
     * Минимальный селектор flow.
     * <p>
     * В следующих итерациях будет расширен условиями по segment/source/serviceCode/appointmentType и т.д.
     */
    public record Selector(
            String kind,
            String type
    ) {
    }

    /**
     * Настройки идемпотентности.
     */
    public record IdempotencyConfig(
            boolean enabled,
            IdempotencyStrategy strategy,
            int lockTtlSec
    ) {
    }

    /**
     * Настройки inbound DLQ.
     * <p>
     * Важно: DLQ является частью эксплуатационной надёжности.
     * По умолчанию включён (enabled=true), т.к. в закрытых контурах критично не терять сообщения.
     */
    public record InboundDlqConfig(
            boolean enabled,
            int maxAttempts,
            boolean sanitizeHeaders
    ) {
    }

    /**
     * Настройки enrichment пользователя через KeycloakProxy.
     * <p>
     * Цель enrichment:
     * <ul>
     *   <li>получить профиль пользователя и контекст (для маршрутизации/сегментации);</li>
     *   <li>при необходимости автоподставить branchId (если это разрешено настройками);</li>
     *   <li>не хранить и не логировать сырые токены (используется только хэш токена для кэша).</li>
     * </ul>
     */
    public record KeycloakProxyEnrichmentConfig(
            boolean enabled,
            boolean critical,
            String connectorId,
            List<KeycloakProxyFetchMode> modes,
            String userIdHeaderName,
            String tokenHeaderName,
            String userByIdPathTemplate,
            String userByTokenPath,
            boolean stripTokensFromResponse,
            int cacheTtlSeconds,
            int cacheMaxEntries,
            boolean autoBranchIdFromUser,
            List<String> branchIdAttributeKeys
    ) {
    }

    /**
     * Способы получения данных пользователя из KeycloakProxy.
     */
    public enum KeycloakProxyFetchMode {
        /**
         * Получение профиля по userId (в заголовке или поле envelope.userId).
         * <p>
         * Это предпочтительный режим для закрытых контуров РФ, т.к. исключает работу с токенами.
         */
        USER_ID_HEADER,

        /**
         * Получение профиля по access token (Bearer).
         * <p>
         * Важно: токен нельзя сохранять или логировать, допускается только краткоживущий in-memory кэш
         * по хэшу токена.
         */
        BEARER_TOKEN
    }

    /**
     * Настройки messaging outbox.
     * <p>
     * Данная часть конфига управляет тем, как система публикует сообщения во внешний брокер:
     * <ul>
     *   <li>ALWAYS — всегда через outbox (максимальная надёжность);</li>
     *   <li>ON_FAILURE — сначала прямой вызов провайдера, а при ошибке — запись в outbox.</li>
     * </ul>
     */
    public record MessagingOutboxConfig(
            boolean enabled,
            String mode,
            int maxAttempts,
            int baseDelaySec,
            int maxDelaySec,
            int batchSize
    ) {
    }

    /**
     * Настройки REST outbox.
     */
    public record RestOutboxConfig(
            boolean enabled,
            String mode,
            int maxAttempts,
            int baseDelaySec,
            int maxDelaySec,
            int batchSize,
            String idempotencyHeaderName,
            String treat4xxAsSuccess
    ) {
    }

    /**
     * Реестр REST-коннекторов.
     * <p>
     * Коннектор задаёт:
     * <ul>
     *   <li>базовый URL (baseUrl);</li>
     *   <li>тип авторизации (NONE/BASIC/BEARER/API_KEY_HEADER);</li>
     *   <li>параметры авторизации.</li>
     * </ul>
     * <p>
     * Важно: секреты хранятся только в конфигурации, но НЕ сохраняются в outbox-таблицах.
     */
    public record RestConnectorConfig(
            String baseUrl,
            RestConnectorAuth auth
    ) {
    }

    /**
     * Параметры авторизации REST-коннектора.
     * <p>
     * Важно: данные здесь считаются чувствительными. Их нельзя логировать и нельзя сохранять в outbox.
     */
    public record RestConnectorAuth(
            RestConnectorAuthType type,
            String headerName,
            String apiKey,
            String bearerToken,
            String basicUsername,
            String basicPassword
    ) {
    }

    /**
     * Тип авторизации REST-коннектора.
     */
    public enum RestConnectorAuthType {
        NONE,
        BASIC,
        BEARER,
        API_KEY_HEADER
    }

    /**
     * Настройки CRM слоя.
     * <p>
     * CRM слой предоставляет типизированные операции с внешними CRM.
     * Важно: идентификация (identity) является самостоятельным слоем; CRM может быть одним из backend-источников.
     */
    public record CrmConfig(
            boolean enabled,
            CrmProfile profile,
            String connectorId,
            Map<String, Object> settings
    ) {
        /**
         * @return безопасный дефолт (CRM выключен).
         */
        public static CrmConfig disabled() {
            return new CrmConfig(false, CrmProfile.GENERIC, "crmGeneric", Map.of());
        }
    }

    /**
     * Профили CRM (тип интеграции).
     */
    public enum CrmProfile {
        BITRIX24,
        AMOCRM,
        RETAILCRM,
        MEGAPLAN,
        GENERIC
    }


    /**
     * Настройки медицинского слоя (Medical/MIS/EMR/EHR).
     * <p>
     * Слой используется для медицинских сценариев (precheck, профосмотры, построение маршрута).
     * Интеграция реализуется через типизированные клиенты, выбираемые по профилю.
     * <p>
     * Важно: слой может быть выключен, если у заказчика нет медицинской интеграции.
     */
    public record MedicalConfig(
            boolean enabled,
            MedicalProfile profile,
            String connectorId,
            Map<String, Object> settings
    ) {
        /**
         *  безопасный дефолт (medical выключен).
         */
        public static MedicalConfig disabled() {
            return new MedicalConfig(false, MedicalProfile.FHIR_GENERIC, "medicalGeneric", Map.of());
        }
    }

    /**
     * Профили медицинской интеграции.
     * <p>
     * Названия намеренно «архитектурные», а не конкретные продукты: это облегчает замену вендора.
     */
    public enum MedicalProfile {
        /** EMIAS-подобная интеграция (региональная МИС, похожие контракты). */
        EMIAS_LIKE,
        /** MEDESK-подобная интеграция (частные клиники, SaaS-подобные контракты). */
        MEDESK_LIKE,
        /** FHIR/generic профиль (обобщённая интеграция, включая FHIR-сервера). */
        FHIR_GENERIC
    }


    /**
     * Настройки слоя предварительной записи (appointment/booking/schedule).
     * <p>
     * Слой используется для сценариев предварительной записи, в том числе медицинских:
     * получение ближайшей записи, доступных слотов, бронирование/отмена, построение первичного queue plan.
     * <p>
     * Важно: слой может быть выключен, если у заказчика нет предварительной записи.
     */
    public record AppointmentConfig(
            boolean enabled,
            AppointmentProfile profile,
            String connectorId,
            Map<String, Object> settings
    ) {
        /**
         * @return безопасный дефолт (appointment выключен).
         */
        public static AppointmentConfig disabled() {
            return new AppointmentConfig(false, AppointmentProfile.GENERIC, "appointmentGeneric", Map.of());
        }
    }

    /**
     * Профили интеграции предварительной записи.
     * <p>
     * Названия являются архитектурными: это облегчает замену вендора и поддержку разных поставщиков.
     */
    public enum AppointmentProfile {
        /** EMIAS-подобная предварительная запись (медицинские сценарии). */
        EMIAS_APPOINTMENT,
        /** MEDTOCHKA-подобная интеграция (частные клиники, SaaS-подобные контракты). */
        MEDTOCHKA_LIKE,
        /** PRODOCTOROV-подобная интеграция (маркетплейс/агрегатор записей). */
        PRODOCTOROV_LIKE,
        /** YCLIENTS-подобная интеграция (сервисы записи для бизнеса). */
        YCLIENTS_LIKE,
        /** НАПОПРАВКУ-подобная интеграция (маркетплейс/агрегатор). */
        NAPOPRAVKU_LIKE,
        /** Обобщённый профиль (детерминированная заглушка для разработки). */
        GENERIC
    }

    /**
     * Настройки слоя идентификации клиента (identity/customerIdentity).
     * <p>
     * Важно: слой identity является самостоятельным и не должен сводиться только к CRM.
     * CRM может быть одним из backend-источников, но контракт identity — независим.
     */
    public record IdentityConfig(
            boolean enabled,
            Map<String, String> segmentAliases,
            List<String> segmentPriority,
            Map<String, Object> providers
    ) {
        /**
         * Дефолтная конфигурация identity.
         * <p>
         * По умолчанию слой включён, но без провайдеров. Это безопасно: сервис не «выдумывает» идентичности.
         */
        public static IdentityConfig defaultConfig() {
            return new IdentityConfig(
                    true,
                    Map.of(),
                    List.of(
                            "VIP",
                            "PREMIUM",
                            "CORPORATE",
                            "LOW_MOBILITY",
                            "PREBOOKED_MEDICAL",
                            "FAST_TRACK",
                            "MULTI_STAGE_EXAM",
                            "DEFAULT"
                    ),
                    Map.of()
            );
        }
    }

    /**
     * Настройки источников событий/результатов аналитики VisionLabs (LUNA PLATFORM).
     * <p>
     * На уровне LUNA Video Manager / Agent поддерживаются разные механизмы доставки результатов аналитики:
     * <ul>
     *   <li>http — отправка в сторонний сервис;</li>
     *   <li>luna-ws-notification — WebSocket уведомления;</li>
     *   <li>luna-event — сохранение в Events с последующей выборкой через API;</li>
     *   <li>luna-kafka — публикация событий в Kafka.</li>
     * </ul>
     * <p>
     * Важно: данный раздел описывает только приём событий и базовые параметры.
     * Конкретные URL/топики/секреты задаются консервативно и не должны логироваться.
     */
    public record VisionLabsAnalyticsConfig(
            boolean enabled,
            String inboundTypePrefix,
            VisionLabsHttpCallbackConfig http,
            VisionLabsWsConfig ws,
            VisionLabsEventsConfig events,
            VisionLabsKafkaConfig kafka
    ) {
        /**
         * @return безопасная конфигурация, в которой приём аналитики выключен.
         */
        public static VisionLabsAnalyticsConfig disabled() {
            return new VisionLabsAnalyticsConfig(
                    false,
                    "visionlabs.analytics.",
                    new VisionLabsHttpCallbackConfig(false, null, null),
                    new VisionLabsWsConfig(false, "token", null),
                    new VisionLabsEventsConfig(false, "visionlabsEvents", "/events", List.of(), 10,
                            "/events", "/id",
                            "stream_id", "after_id", "limit", 100),
                    new VisionLabsKafkaConfig(false, "luna.events", "integration-broker-visionlabs")
            );
        }
    }

    /**
     * HTTP callback (callback type: http).
     * <p>
     * Shared-secret передаётся через заголовок. Важно: значение секрета нельзя логировать.
     */
    public record VisionLabsHttpCallbackConfig(
            boolean enabled,
            String sharedSecretHeaderName,
            String sharedSecret
    ) {
    }

    /**
     * WebSocket уведомления (callback type: luna-ws-notification).
     * <p>
     * Shared-secret передаётся как query-param при подключении.
     */
    public record VisionLabsWsConfig(
            boolean enabled,
            String sharedSecretQueryParam,
            String sharedSecret
    ) {
    }

    /**
     * Events poller (callback type: luna-event).
     * <p>
     * События сохраняются в Events и затем выбираются через API ("get general events")
     * с фильтром по stream_id.
     */
    public record VisionLabsEventsConfig(
            boolean enabled,
            String connectorId,
            String path,
            List<String> streamIds,
            int pollIntervalSec,
            String listJsonPointer,
            String idJsonPointer,
            String streamIdParam,
            String afterIdParam,
            String limitParam,
            int limit
    ) {
    }

    /**
     * Kafka inbound (callback type: luna-kafka).
     * <p>
     * Здесь задаются только логические параметры (topic/group). Транспортные параметры Kafka
     * (bootstrap servers, security, SASL и т.п.) настраиваются через application.yml и окружение.
     */
    public record VisionLabsKafkaConfig(
            boolean enabled,
            String topic,
            String groupId
    ) {
    }


/**
 * Настройки определения отделения (branchId) для входящих событий.
 *
 * <p>Задача Integration Broker — помочь восстановить контекст отделения (для последующего
 * создания визита в VisitManager или отправки события на DataBus), но не внедрять прикладные
 * правила вызова/сегментации: это зона ответственности VisitManager.
 *
 * <p>Алгоритм резолвинга (в порядке приоритета):
 * <ol>
 *   <li>использовать {@code InboundEnvelope.branchId}, если он задан;</li>
 *   <li>попытаться взять branchId из заголовка {@code branchIdHeaderName};</li>
 *   <li>если задан префикс (заголовок {@code branchPrefixHeaderName}) — сопоставить по карте {@code prefixToBranchId};</li>
 *   <li>если задано имя камеры (в {@code sourceMeta.cameraName}) — применить regex-правила {@code cameraNameRules}.</li>
 * </ol>
 */
public record BranchResolutionConfig(
        boolean enabled,
        String branchIdHeaderName,
        String branchPrefixHeaderName,
        Map<String, String> prefixToBranchId,
        List<CameraNameRule> cameraNameRules
) {
    /**
     * @return безопасная конфигурация по умолчанию (резолвинг включён, но правила пустые).
     */
    public static BranchResolutionConfig defaultConfig() {
        return new BranchResolutionConfig(
                true,
                "x-branch-id",
                "x-branch-prefix",
                Map.of(),
                List.of()
        );
    }
}

/**
 * Правило извлечения branchId или branchPrefix из имени камеры.
 *
 * <p>Пример: если камера именуется "TVR_CAM_01", можно извлечь префикс "TVR" и затем
 * сопоставить его с branchId по карте {@code prefixToBranchId}.
 */
public record CameraNameRule(
        String name,
        String regex,
        int group,
        CameraNameRuleMode mode
) {
}

/**
 * Режим интерпретации regex-группы из имени камеры.
 */
public enum CameraNameRuleMode {
    BRANCH_ID,
    BRANCH_PREFIX
}

/**
 * Настройки интеграции с VisitManager.
 *
 * <p>Integration Broker использует эти настройки для формирования запросов к VisitManager
 * (например, создание визита по параметрам и получение каталога услуг отделения).
 */
public record VisitManagerIntegrationConfig(
        boolean enabled,
        String connectorId,
        String createVisitPathTemplate,
        String servicesCatalogPathTemplate,
        String defaultEntryPointId,
        String entryPointIdHeaderName
) {
    /**
     * @return конфигурация "выключено" с безопасными дефолтами путей.
     */
    public static VisitManagerIntegrationConfig disabled() {
        return new VisitManagerIntegrationConfig(
                false,
                "visitmanager",
                "/entrypoint/branches/{branchId}/entry-points/{entryPointId}/visits/parameters?printTicket={printTicket}",
                "/entrypoint/branches/{branchId}/services/catalog",
                "1",
                "x-entry-point-id"
        );
    }
}

/**
 * Настройки интеграции с DataBus (публикация событий).
 *
 * <p>DataBus в референсе принимает события через REST:
 * {@code POST /databus/events/types/{type}} с обязательными заголовками
 * Service-Destination/Send-To-OtherBus/Send-Date/Service-Sender.
 */
public record DataBusIntegrationConfig(
        boolean enabled,
        String connectorId,
        String publishEventPathTemplate,
        String destinationHeaderName,
        String sendToOtherBusHeaderName,
        String sendDateHeaderName,
        String senderHeaderName,
        String defaultSenderServiceName,
        boolean defaultSendToOtherBus
) {
    /**
     * @return конфигурация "выключено" с дефолтными именами заголовков из DataBus.
     */
    public static DataBusIntegrationConfig disabled() {
        return new DataBusIntegrationConfig(
                false,
                "databus",
                "/databus/events/types/{type}",
                "Service-Destination",
                "Send-To-OtherBus",
                "Send-Date",
                "Service-Sender",
                "integration-broker",
                false
        );
    }
}
/**
     * Стратегия расчёта ключа идемпотентности.
     */
    public enum IdempotencyStrategy {
        MESSAGE_ID,
        CORRELATION_ID,
        PAYLOAD_HASH,
        AUTO
    }
}
