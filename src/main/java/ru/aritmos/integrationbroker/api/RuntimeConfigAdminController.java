package ru.aritmos.integrationbroker.api;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin API: просмотр/валидация/сохранение runtime-конфигурации.
 */
@Secured("IB_ADMIN")
@Controller("/admin/runtime-config")
@Tag(name = "Integration Broker — Admin API (Runtime Config)", description = "Управление runtime-конфигурацией и аудит изменений")
public class RuntimeConfigAdminController {

    private final RuntimeConfigStore runtimeConfigStore;

    public RuntimeConfigAdminController(RuntimeConfigStore runtimeConfigStore) {
        this.runtimeConfigStore = runtimeConfigStore;
    }

    @Get
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Получить текущую runtime-конфигурацию")
    @ApiResponse(responseCode = "200", description = "Конфигурация возвращена", content = @Content(schema = @Schema(implementation = RuntimeConfigResponse.class)))
    public RuntimeConfigResponse getCurrent() {
        RuntimeConfigStore.RuntimeConfig cfg = runtimeConfigStore.getEffective();
        return new RuntimeConfigResponse(sanitizeForAdmin(cfg), runtimeConfigStore.isRemoteEnabled());
    }


    private static RuntimeConfigStore.RuntimeConfig sanitizeForAdmin(RuntimeConfigStore.RuntimeConfig cfg) {
        if (cfg == null || cfg.restConnectors() == null || cfg.restConnectors().isEmpty()) {
            return cfg;
        }
        java.util.Map<String, RuntimeConfigStore.RestConnectorConfig> sanitizedConnectors = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, RuntimeConfigStore.RestConnectorConfig> e : cfg.restConnectors().entrySet()) {
            RuntimeConfigStore.RestConnectorConfig connector = e.getValue();
            if (connector == null) {
                sanitizedConnectors.put(e.getKey(), null);
                continue;
            }
            RuntimeConfigStore.RestConnectorAuth auth = connector.auth();
            RuntimeConfigStore.RestConnectorAuth sanitizedAuth = auth == null ? null : new RuntimeConfigStore.RestConnectorAuth(
                    auth.type(),
                    auth.headerName(),
                    mask(auth.apiKey()),
                    mask(auth.bearerToken()),
                    auth.basicUsername(),
                    mask(auth.basicPassword()),
                    auth.oauth2TokenUrl(),
                    auth.oauth2ClientId(),
                    mask(auth.oauth2ClientSecret()),
                    auth.oauth2Scope(),
                    auth.oauth2Audience()
            );
            sanitizedConnectors.put(e.getKey(), new RuntimeConfigStore.RestConnectorConfig(
                    connector.baseUrl(),
                    sanitizedAuth,
                    connector.retryPolicy(),
                    connector.circuitBreaker()
            ));
        }
        return new RuntimeConfigStore.RuntimeConfig(
                cfg.revision(),
                cfg.flows(),
                cfg.idempotency(),
                cfg.inboundDlq(),
                cfg.keycloakProxy(),
                cfg.messagingOutbox(),
                cfg.restOutbox(),
                java.util.Map.copyOf(sanitizedConnectors),
                cfg.crm(),
                cfg.medical(),
                cfg.appointment(),
                cfg.identity(),
                cfg.visionLabsAnalytics(),
                cfg.branchResolution(),
                cfg.visitManager(),
                cfg.dataBus()
        );
    }

    private static String mask(String value) {
        return value == null || value.isBlank() ? value : "***";
    }

    @Post(uri = "/dry-run")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Dry-run валидация runtime-конфигурации")
    @ApiResponse(responseCode = "200", description = "Результат dry-run", content = @Content(schema = @Schema(implementation = DryRunResponse.class)))
    public DryRunResponse dryRun(@Body RuntimeConfigStore.RuntimeConfig payload) {
        RuntimeConfigStore.RuntimeConfig normalized = (payload == null ? runtimeConfigStore.getEffective() : payload).normalize();
        List<String> warnings = new ArrayList<>();
        if (normalized.revision() == null || normalized.revision().isBlank() || "unknown".equalsIgnoreCase(normalized.revision())) {
            warnings.add("Рекомендуется явно задавать revision для runtime-конфигурации");
        }
        if (normalized.flows() == null || normalized.flows().isEmpty()) {
            warnings.add("Конфигурация не содержит flow-описаний");
        }
        if (normalized.restConnectors() == null || normalized.restConnectors().isEmpty()) {
            warnings.add("Не задано ни одного rest-connector");
        }
        List<String> validationErrors = new ArrayList<>();
        validationErrors.addAll(RuntimeConfigStore.validateAppointmentCustomOperations(normalized));
        validationErrors.addAll(RuntimeConfigStore.validateSubprofiles(normalized));
        for (String err : validationErrors) {
            warnings.add("ERROR: " + err);
        }
        warnings.addAll(RuntimeConfigStore.collectAppointmentCustomOperationWarnings(normalized));
        warnings.addAll(collectFallbackWarnings(normalized));
        return new DryRunResponse(validationErrors.isEmpty(), warnings, normalized.revision());
    }

    @Put
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Сохранить runtime-конфигурацию вручную")
    @ApiResponse(responseCode = "200", description = "Конфигурация сохранена", content = @Content(schema = @Schema(implementation = SaveResponse.class)))
    public SaveResponse save(@Body RuntimeConfigStore.RuntimeConfig config,
                             @Parameter(description = "Инициатор изменения") String actor,
                             @Parameter(description = "Причина изменения") String reason) {
        RuntimeConfigStore.RuntimeConfig applied = runtimeConfigStore.applyManual(config, actor, reason);
        return new SaveResponse(true, applied.revision());
    }

    @Get(uri = "/audit")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Получить журнал изменений runtime-конфигурации")
    @ApiResponse(responseCode = "200", description = "Журнал возвращён", content = @Content(schema = @Schema(implementation = RuntimeConfigAuditResponse.class)))
    public RuntimeConfigAuditResponse audit(@Parameter(description = "Лимит записей (1..500)") Integer limit) {
        int lim = limit == null ? 100 : limit;
        return new RuntimeConfigAuditResponse(runtimeConfigStore.getAuditTrail(lim));
    }

    @Get(uri = "/export")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Экспорт effective runtime-конфигурации")
    @ApiResponse(responseCode = "200", description = "Конфигурация экспортирована", content = @Content(schema = @Schema(implementation = RuntimeConfigExportResponse.class)))
    public RuntimeConfigExportResponse export(@Parameter(description = "Маскировать секреты") Boolean redactSecrets) {
        RuntimeConfigStore.RuntimeConfig effective = runtimeConfigStore.getEffective();
        boolean redact = redactSecrets == null || redactSecrets;
        RuntimeConfigStore.RuntimeConfig exported = redact ? sanitizeForAdmin(effective) : effective;
        return new RuntimeConfigExportResponse(exported, redact, effective == null ? null : effective.revision());
    }

    @Get(uri = "/connectors/policy-snapshot")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Получить snapshot effective retry/jitter/circuit-breaker по REST-коннекторам")
    @ApiResponse(responseCode = "200", description = "Snapshot возвращён", content = @Content(schema = @Schema(implementation = ConnectorPolicySnapshotResponse.class)))
    public ConnectorPolicySnapshotResponse connectorPolicySnapshot() {
        RuntimeConfigStore.RuntimeConfig cfg = runtimeConfigStore.getEffective();
        RuntimeConfigStore.RestOutboxConfig global = cfg == null ? null : cfg.restOutbox();
        java.util.Map<String, RuntimeConfigStore.RestConnectorConfig> connectors = cfg == null || cfg.restConnectors() == null
                ? java.util.Map.of()
                : cfg.restConnectors();
        List<ConnectorPolicySnapshotItem> items = new ArrayList<>();
        for (java.util.Map.Entry<String, RuntimeConfigStore.RestConnectorConfig> e : connectors.entrySet()) {
            items.add(toPolicySnapshotItem(e.getKey(), e.getValue(), global));
        }
        return new ConnectorPolicySnapshotResponse(items);
    }



    @Get(uri = "/connectors/policy-diff")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Получить diff retry/circuit policy: effective vs baseline")
    @ApiResponse(responseCode = "200", description = "Diff возвращён", content = @Content(schema = @Schema(implementation = ConnectorPolicyDiffResponse.class)))
    public ConnectorPolicyDiffResponse connectorPolicyDiff() {
        RuntimeConfigStore.RuntimeConfig effective = runtimeConfigStore.getEffective();
        RuntimeConfigStore.RuntimeConfig baseline = runtimeConfigStore.getBaseline();
        RuntimeConfigStore.RestOutboxConfig effectiveGlobal = effective == null ? null : effective.restOutbox();
        RuntimeConfigStore.RestOutboxConfig baselineGlobal = baseline == null ? null : baseline.restOutbox();

        java.util.Map<String, RuntimeConfigStore.RestConnectorConfig> effectiveConnectors = effective == null || effective.restConnectors() == null
                ? java.util.Map.of()
                : effective.restConnectors();
        java.util.Map<String, RuntimeConfigStore.RestConnectorConfig> baselineConnectors = baseline == null || baseline.restConnectors() == null
                ? java.util.Map.of()
                : baseline.restConnectors();

        java.util.Set<String> connectorIds = new java.util.TreeSet<>();
        connectorIds.addAll(effectiveConnectors.keySet());
        connectorIds.addAll(baselineConnectors.keySet());

        List<ConnectorPolicyDiffItem> items = new ArrayList<>();
        for (String connectorId : connectorIds) {
            RuntimeConfigStore.RestConnectorConfig effectiveConnector = effectiveConnectors.get(connectorId);
            RuntimeConfigStore.RestConnectorConfig baselineConnector = baselineConnectors.get(connectorId);

            ConnectorPolicySnapshotItem effectiveItem = toPolicySnapshotItem(connectorId, effectiveConnector, effectiveGlobal);
            ConnectorPolicySnapshotItem baselineItem = toPolicySnapshotItem(connectorId, baselineConnector, baselineGlobal);

            boolean changed = effectiveItem.retryMaxAttempts() != baselineItem.retryMaxAttempts()
                    || effectiveItem.retryBaseDelaySec() != baselineItem.retryBaseDelaySec()
                    || effectiveItem.retryMaxDelaySec() != baselineItem.retryMaxDelaySec()
                    || effectiveItem.jitterPercent() != baselineItem.jitterPercent()
                    || effectiveItem.circuitBreakerEnabled() != baselineItem.circuitBreakerEnabled()
                    || !java.util.Objects.equals(effectiveItem.circuitBreakerFailureThreshold(), baselineItem.circuitBreakerFailureThreshold())
                    || !java.util.Objects.equals(effectiveItem.circuitBreakerOpenTimeoutSec(), baselineItem.circuitBreakerOpenTimeoutSec())
                    || !java.util.Objects.equals(effectiveItem.circuitBreakerHalfOpenMaxProbes(), baselineItem.circuitBreakerHalfOpenMaxProbes());

            items.add(new ConnectorPolicyDiffItem(connectorId, changed, baselineItem, effectiveItem));
        }
        return new ConnectorPolicyDiffResponse(items);
    }

    private static ConnectorPolicySnapshotItem toPolicySnapshotItem(String connectorId,
                                                                    RuntimeConfigStore.RestConnectorConfig connector,
                                                                    RuntimeConfigStore.RestOutboxConfig global) {
        RuntimeConfigStore.RetryPolicy retry = connector == null ? null : connector.retryPolicy();
        RuntimeConfigStore.CircuitBreakerPolicy cb = connector == null ? null : connector.circuitBreaker();
        int maxAttempts = retry != null && retry.maxAttempts() != null ? retry.maxAttempts() : (global == null ? 10 : global.maxAttempts());
        int baseDelaySec = retry != null && retry.baseDelaySec() != null ? retry.baseDelaySec() : (global == null ? 5 : global.baseDelaySec());
        int maxDelaySec = retry != null && retry.maxDelaySec() != null ? retry.maxDelaySec() : (global == null ? 600 : global.maxDelaySec());
        return new ConnectorPolicySnapshotItem(
                connectorId,
                connector == null ? null : connector.baseUrl(),
                maxAttempts,
                baseDelaySec,
                maxDelaySec,
                20,
                cb != null && cb.enabled(),
                cb == null ? null : cb.failureThreshold(),
                cb == null ? null : cb.openTimeoutSec(),
                cb == null ? null : cb.halfOpenMaxProbes()
        );
    }
    @Serdeable
    @Schema(name = "RuntimeConfigResponse", description = "Текущая runtime-конфигурация")
    record RuntimeConfigResponse(
            @Schema(description = "Effective runtime-config") RuntimeConfigStore.RuntimeConfig config,
            @Schema(description = "Включен ли remote-config") boolean remoteEnabled
    ) {
    }

    @Serdeable
    @Schema(name = "RuntimeConfigDryRunResponse", description = "Результат dry-run runtime-конфигурации")
    record DryRunResponse(
            @Schema(description = "Успешность синтаксической проверки") boolean ok,
            @Schema(description = "Предупреждения") List<String> warnings,
            @Schema(description = "Revision после normalize") String revision
    ) {
    }

    @Serdeable
    @Schema(name = "RuntimeConfigSaveResponse", description = "Результат сохранения runtime-конфигурации")
    record SaveResponse(
            @Schema(description = "Сохранено ли изменение") boolean saved,
            @Schema(description = "Текущий revision") String revision
    ) {
    }

    @Serdeable
    @Schema(name = "RuntimeConfigAuditResponse", description = "Журнал изменений runtime-конфигурации")
    record RuntimeConfigAuditResponse(
            @Schema(description = "Список изменений") List<RuntimeConfigStore.RuntimeConfigAuditEntry> items
    ) {
    }

    @Serdeable
    @Schema(name = "RuntimeConfigExportResponse", description = "Экспорт effective runtime-конфигурации")
    record RuntimeConfigExportResponse(
            @Schema(description = "Экспортированная конфигурация") RuntimeConfigStore.RuntimeConfig config,
            @Schema(description = "Применена ли маскировка секретов") boolean redacted,
            @Schema(description = "Revision effective-конфигурации") String revision
    ) {
    }


    @Serdeable
    @Schema(name = "ConnectorPolicySnapshotResponse", description = "Effective snapshot retry/jitter/circuit-breaker по коннекторам")
    record ConnectorPolicySnapshotResponse(
            @Schema(description = "Список effective политик") List<ConnectorPolicySnapshotItem> items
    ) {
    }

    @Serdeable
    @Schema(name = "ConnectorPolicyDiffResponse", description = "Diff политик effective vs baseline по REST-коннекторам")
    record ConnectorPolicyDiffResponse(
            @Schema(description = "Список отличий по коннекторам") List<ConnectorPolicyDiffItem> items
    ) {
    }

    @Serdeable
    @Schema(name = "ConnectorPolicyDiffItem", description = "Изменения политики конкретного REST-коннектора")
    record ConnectorPolicyDiffItem(
            @Schema(description = "Идентификатор коннектора") String connectorId,
            @Schema(description = "Есть ли изменение относительно baseline") boolean changed,
            @Schema(description = "Политика baseline") ConnectorPolicySnapshotItem baseline,
            @Schema(description = "Эффективная политика runtime") ConnectorPolicySnapshotItem effective
    ) {
    }

    @Serdeable
    @Schema(name = "ConnectorPolicySnapshotItem", description = "Effective политика конкретного REST-коннектора")
    record ConnectorPolicySnapshotItem(
            @Schema(description = "Идентификатор коннектора") String connectorId,
            @Schema(description = "Базовый URL") String baseUrl,
            @Schema(description = "Максимум попыток") int retryMaxAttempts,
            @Schema(description = "Базовая задержка, сек") int retryBaseDelaySec,
            @Schema(description = "Максимальная задержка, сек") int retryMaxDelaySec,
            @Schema(description = "Jitter в процентах (фиксированный runtime-паттерн)") int jitterPercent,
            @Schema(description = "Включен ли circuit-breaker") boolean circuitBreakerEnabled,
            @Schema(description = "Порог ошибок для открытия") Integer circuitBreakerFailureThreshold,
            @Schema(description = "Время открытого состояния, сек") Integer circuitBreakerOpenTimeoutSec,
            @Schema(description = "Максимум half-open probes") Integer circuitBreakerHalfOpenMaxProbes
    ) {
    }

    private static List<String> collectFallbackWarnings(RuntimeConfigStore.RuntimeConfig cfg) {
        List<String> warnings = new ArrayList<>();
        RuntimeConfigStore.CrmConfig crm = cfg == null ? null : cfg.crm();
        if (crm != null && crm.enabled() && crm.profile() != RuntimeConfigStore.CrmProfile.GENERIC) {
            warnings.add("CRM profile " + crm.profile() + " в prerelease работает через fallback на GENERIC до включения реального коннектора");
        }
        RuntimeConfigStore.MedicalConfig medical = cfg == null ? null : cfg.medical();
        if (medical != null && medical.enabled() && medical.profile() != RuntimeConfigStore.MedicalProfile.FHIR_GENERIC) {
            warnings.add("Medical profile " + medical.profile() + " в prerelease работает через fallback на FHIR_GENERIC");
        }
        RuntimeConfigStore.AppointmentConfig appointment = cfg == null ? null : cfg.appointment();
        if (appointment != null && appointment.enabled()
                && appointment.profile() != RuntimeConfigStore.AppointmentProfile.GENERIC
                && appointment.profile() != RuntimeConfigStore.AppointmentProfile.CUSTOM_CONNECTOR) {
            warnings.add("Appointment profile " + appointment.profile() + " в prerelease работает через fallback на GENERIC");
        }
        return warnings;
    }

}
