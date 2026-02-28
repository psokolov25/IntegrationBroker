package ru.aritmos.integrationbroker.api;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.aritmos.integrationbroker.identity.IdentityModels;
import ru.aritmos.integrationbroker.identity.IdentityService;

import java.util.Map;

/**
 * Публичный API слоя идентификации клиента.
 * <p>
 * Это отдельный слой (identity/customerIdentity), который может использовать CRM/МИС как backend-источники,
 * но не сводится к ним.
 */
@Controller("/api/identity")
@Tag(name = "Идентификация клиента", description = "Публичный API для идентификации клиента по расширяемой модели type+value.")
public class IdentityController {

    private final IdentityService identityService;

    public IdentityController(IdentityService identityService) {
        this.identityService = identityService;
    }

    /**
     * Разрешить (идентифицировать) клиента по одному или нескольким идентификаторам.
     */
    @Post(uri = "/resolve", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Идентифицировать клиента",
            description = "Выполняет идентификацию по набору идентификаторов (type+value). Поддерживает приоритет и fallback, а также агрегацию результата из нескольких источников."
    )
    @ApiResponse(responseCode = "200", description = "Результат идентификации (профиль + диагностика)", content = @Content(schema = @Schema(implementation = IdentityModels.IdentityResolution.class)))
    @ApiResponse(responseCode = "400", description = "Ошибка запроса (например, отсутствуют attributes)")
    public HttpResponse<IdentityModels.IdentityResolution> resolve(@Body @Valid IdentityModels.IdentityRequest request) {
        // На публичном endpoint нет понятия «meta ядра». При необходимости клиент может передать контекст в request.context.
        IdentityModels.IdentityResolution res = identityService.resolve(request, Map.of("channel", "IDENTITY_API"));
        return HttpResponse.ok(res);
    }
}
