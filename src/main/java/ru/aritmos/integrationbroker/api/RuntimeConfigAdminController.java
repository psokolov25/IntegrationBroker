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
        return new RuntimeConfigResponse(cfg, runtimeConfigStore.isRemoteEnabled());
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
        return new DryRunResponse(true, warnings, normalized.revision());
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
}
