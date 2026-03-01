package ru.aritmos.integrationbroker.api;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.aritmos.integrationbroker.core.OutboundDryRunState;

/**
 * Admin API: runtime управление dry-run режимом outbound.
 */
@Secured("IB_ADMIN")
@Controller("/admin/outbound")
@Tag(name = "Integration Broker — Admin API (Outbound)", description = "Управление dry-run для outbound каналов")
public class OutboundAdminController {

    private final OutboundDryRunState dryRunState;

    public OutboundAdminController(OutboundDryRunState dryRunState) {
        this.dryRunState = dryRunState;
    }

    @Get(uri = "/dry-run")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Получить состояние outbound dry-run")
    @ApiResponse(responseCode = "200", description = "Состояние dry-run", content = @Content(schema = @Schema(implementation = DryRunStateResponse.class)))
    public DryRunStateResponse getDryRun() {
        return new DryRunStateResponse(
                dryRunState.configuredDefault(),
                dryRunState.overrideValue(),
                dryRunState.effective(dryRunState.configuredDefault())
        );
    }

    @Post(uri = "/dry-run")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Изменить состояние outbound dry-run")
    @ApiResponse(responseCode = "200", description = "Состояние обновлено", content = @Content(schema = @Schema(implementation = DryRunStateResponse.class)))
    public DryRunStateResponse setDryRun(@Body DryRunStateRequest request) {
        if (request != null && Boolean.TRUE.equals(request.reset())) {
            dryRunState.resetOverride();
        } else {
            dryRunState.setOverride(request == null ? null : request.enabled());
        }
        return getDryRun();
    }

    @Serdeable
    @Schema(name = "OutboundDryRunStateRequest", description = "Запрос на изменение dry-run outbound")
    record DryRunStateRequest(
            @Schema(description = "Новое значение dry-run override") Boolean enabled,
            @Schema(description = "Сбросить override к значению из application.yml") Boolean reset
    ) {
    }

    @Serdeable
    @Schema(name = "OutboundDryRunStateResponse", description = "Текущее состояние dry-run outbound")
    record DryRunStateResponse(
            @Schema(description = "Значение по умолчанию из application.yml") boolean configuredDefault,
            @Schema(description = "Текущее override-значение (null = нет override)") Boolean override,
            @Schema(description = "Эффективное значение dry-run") boolean effective
    ) {
    }
}
