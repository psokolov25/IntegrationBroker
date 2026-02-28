package ru.aritmos.integrationbroker.api;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.aritmos.integrationbroker.groovy.GroovyToolingService;

import java.util.Map;

@Secured({"IB_FLOW_EDITOR", "IB_ADMIN"})
@Controller("/admin/groovy-tooling")
@Tag(name = "Groovy Tooling", description = "Валидация, отладка и эмуляция Groovy-скриптов для frontend IDE")
public class GroovyToolingController {

    private final GroovyToolingService toolingService;

    public GroovyToolingController(GroovyToolingService toolingService) {
        this.toolingService = toolingService;
    }

    @Post("/validate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Проверить Groovy-скрипт на компиляцию")
    public GroovyToolingService.ValidationResult validate(@Body ScriptRequest request) {
        return toolingService.validate(request == null ? null : request.script());
    }

    @Post("/emulate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Эмулировать выполнение Groovy-скрипта с mock-алиасами")
    public GroovyToolingService.EmulationResult emulate(@Body EmulationRequest request) {
        if (request == null) {
            return GroovyToolingService.EmulationResult.failed(java.util.List.of("Request is empty"));
        }
        return toolingService.emulate(request.script(), request.input(), request.meta(), request.mocks());
    }

    public record ScriptRequest(String script) {
    }

    public record EmulationRequest(String script,
                                   Map<String, Object> input,
                                   Map<String, Object> meta,
                                   Map<String, Object> mocks) {
    }
}
