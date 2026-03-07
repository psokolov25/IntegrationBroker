package ru.aritmos.integrationbroker.api;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.aritmos.integrationbroker.templates.IntegrationTemplateService;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Secured("IB_ADMIN")
@Controller("/admin/templates")
@Tag(name = "Integration Broker — Admin API (Templates)", description = "Import/Export шаблонов интеграционной ветки (*.ibt/*.ibts)")
public class TemplatesAdminController {

    private static final int MAX_AUDIT_ITEMS = 500;

    private final IntegrationTemplateService service;
    private final List<TemplateAuditItem> audit = new CopyOnWriteArrayList<>();

    public TemplatesAdminController(IntegrationTemplateService service) {
        this.service = service;
    }

    @Post(uri = "/export")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Экспорт шаблона ветки", description = "Упаковывает runtime/flow/groovy артефакты в ibt/ibts архив")
    @ApiResponse(responseCode = "200", description = "Архив подготовлен")
    public HttpResponse<byte[]> export(@Body TemplateExportRequest request,
                                       @Header("X-Correlation-Id") String correlationId,
                                       @Header("X-Request-Id") String requestId) {
        String normalizedCorrelationId = normalizeId(correlationId);
        String normalizedRequestId = normalizeId(requestId);
        String branchId = request == null ? null : request.branchId();
        boolean templateSet = request != null && Boolean.TRUE.equals(request.templateSet());

        IntegrationTemplateService.ExportResult result = service.exportTemplate(
                branchId,
                request == null ? null : request.solution(),
                request == null ? null : request.customerGroup(),
                request == null ? null : request.templateVersion(),
                templateSet,
                request == null ? null : request.customerOverrides()
        );

        appendAudit(new TemplateAuditItem("export", normalizedCorrelationId, normalizedRequestId, branchId, result.filename()));
        return HttpResponse.ok(result.bytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
                .header("X-Correlation-Id", normalizedCorrelationId)
                .header("X-Request-Id", normalizedRequestId);
    }

    @Post(uri = "/import-dry-run")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Dry-run импорт шаблона", description = "Проверка версии, целостности YAML/Groovy и merge strategy без применения")
    @ApiResponse(responseCode = "200", description = "Dry-run завершён", content = @Content(schema = @Schema(implementation = TemplateImportDryRunResponse.class)))
    public HttpResponse<TemplateImportDryRunResponse> importDryRun(@Body TemplateImportDryRunRequest request,
                                                     @Header("X-Correlation-Id") String correlationId,
                                                     @Header("X-Request-Id") String requestId) {
        String normalizedCorrelationId = normalizeId(correlationId);
        String normalizedRequestId = normalizeId(requestId);

        DecodedArchive decoded = decodeArchive(request == null ? null : request.archiveBase64());
        IntegrationTemplateService.ImportDryRunResult result;
        if (decoded.error != null) {
            result = new IntegrationTemplateService.ImportDryRunResult(
                    false,
                    "UNKNOWN",
                    request == null ? "merge" : request.mergeStrategy(),
                    List.of(),
                    List.of(decoded.error),
                    List.of(),
                    List.of(),
                    List.of(),
                    Map.of()
            );
            appendAudit(new TemplateAuditItem(
                    "import-dry-run",
                    normalizedCorrelationId,
                    normalizedRequestId,
                    request == null ? "" : request.branchIdHint(),
                    "BAD_REQUEST: invalid_base64"
            ));
            TemplateImportDryRunResponse payload = new TemplateImportDryRunResponse(
                    false, "UNKNOWN", result.mergeStrategy(), result.warnings(), result.errors(),
                    result.scriptsChecked(), result.changedArtifacts(), result.conflicts(), normalizedCorrelationId, normalizedRequestId
            );
            return HttpResponse.badRequest(payload)
                    .header("X-Correlation-Id", normalizedCorrelationId)
                    .header("X-Request-Id", normalizedRequestId);
        }

        result = service.importDryRun(
                decoded.bytes,
                request == null ? null : request.mergeStrategy(),
                request == null ? null : request.branchIdHint()
        );

        appendAudit(new TemplateAuditItem(
                "import-dry-run",
                normalizedCorrelationId,
                normalizedRequestId,
                request == null ? "" : request.branchIdHint(),
                result.format()
        ));

        TemplateImportDryRunResponse payload = new TemplateImportDryRunResponse(
                result.ok(), result.format(), result.mergeStrategy(), result.warnings(), result.errors(),
                result.scriptsChecked(), result.changedArtifacts(), result.conflicts(), normalizedCorrelationId, normalizedRequestId
        );
        return HttpResponse.ok(payload)
                .header("X-Correlation-Id", normalizedCorrelationId)
                .header("X-Request-Id", normalizedRequestId);
    }

    @Get(uri = "/audit")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Получить audit import/export операций")
    public TemplateAuditResponse audit(Integer limit) {
        int lim = limit == null ? 100 : Math.max(1, Math.min(limit, MAX_AUDIT_ITEMS));
        int size = audit.size();
        int from = Math.max(0, size - lim);
        return new TemplateAuditResponse(List.copyOf(audit.subList(from, size)));
    }

    private static String normalizeId(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return value;
    }

    private void appendAudit(TemplateAuditItem item) {
        audit.add(item);
        while (audit.size() > MAX_AUDIT_ITEMS) {
            audit.remove(0);
        }
    }

    private static DecodedArchive decodeArchive(String archiveBase64) {
        if (archiveBase64 == null || archiveBase64.isBlank()) {
            return new DecodedArchive(null, null);
        }
        try {
            return new DecodedArchive(Base64.getDecoder().decode(archiveBase64), null);
        } catch (IllegalArgumentException e) {
            return new DecodedArchive(null, "Invalid base64 archive payload");
        }
    }

    private record DecodedArchive(byte[] bytes, String error) {}

    @Serdeable
    @Schema(name = "TemplateExportRequest")
    public record TemplateExportRequest(String branchId,
                                        String solution,
                                        String customerGroup,
                                        String templateVersion,
                                        Boolean templateSet,
                                        Map<String, Object> customerOverrides) {
    }

    @Serdeable
    @Schema(name = "TemplateImportDryRunRequest")
    public record TemplateImportDryRunRequest(String archiveBase64,
                                              String mergeStrategy,
                                              String branchIdHint) {
    }

    @Serdeable
    @Schema(name = "TemplateImportDryRunResponse")
    public record TemplateImportDryRunResponse(boolean ok,
                                               String format,
                                               String mergeStrategy,
                                               List<String> warnings,
                                               List<String> errors,
                                               List<String> scriptsChecked,
                                               List<String> changedArtifacts,
                                               List<String> conflicts,
                                               String correlationId,
                                               String requestId) {
    }

    @Serdeable
    public record TemplateAuditItem(String operation,
                                    String correlationId,
                                    String requestId,
                                    String branchId,
                                    String details) {
    }

    @Serdeable
    public record TemplateAuditResponse(List<TemplateAuditItem> items) {
    }
}
