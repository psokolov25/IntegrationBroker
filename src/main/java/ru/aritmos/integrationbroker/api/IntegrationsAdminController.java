package ru.aritmos.integrationbroker.api;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.aritmos.integrationbroker.core.IntegrationsHealthService;

import java.util.List;

/**
 * Admin API: health-check интеграций Workbench (VisitManager/DataBus).
 */
@Secured("IB_ADMIN")
@Controller("/admin/integrations")
@Tag(name = "Integration Broker — Admin API (Integrations)", description = "Health-check интеграций")
public class IntegrationsAdminController {

    private final IntegrationsHealthService integrationsHealthService;

    public IntegrationsAdminController(IntegrationsHealthService integrationsHealthService) {
        this.integrationsHealthService = integrationsHealthService;
    }

    @Get(uri = "/health")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Получить health интеграций", description = "Возвращает статус и latency для VisitManager/DataBus")
    @ApiResponse(responseCode = "200", description = "Состояние интеграций", content = @Content(schema = @Schema(implementation = IntegrationsHealthResponse.class)))
    public IntegrationsHealthResponse health() {
        return new IntegrationsHealthResponse(integrationsHealthService.health());
    }

    @Serdeable
    @Schema(name = "IntegrationsHealthResponse", description = "Список статусов интеграций")
    public record IntegrationsHealthResponse(List<IntegrationsHealthService.IntegrationHealthRow> items) {
    }
}
