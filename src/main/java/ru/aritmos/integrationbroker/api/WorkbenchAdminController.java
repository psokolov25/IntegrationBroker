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
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.InboundDlqService;
import ru.aritmos.integrationbroker.core.MessagingOutboxService;
import ru.aritmos.integrationbroker.core.RestOutboxService;
import ru.aritmos.integrationbroker.templates.IntegrationTemplateService;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

@Secured("IB_ADMIN")
@Controller("/admin/workbench")
@Tag(name = "Integration Broker — Admin API (Workbench)", description = "Агрегированные данные для Workbench виджетов")
public class WorkbenchAdminController {

    private final RuntimeConfigStore runtimeConfigStore;
    private final InboundDlqService inboundDlqService;
    private final MessagingOutboxService messagingOutboxService;
    private final RestOutboxService restOutboxService;
    private final IntegrationTemplateService integrationTemplateService;

    public WorkbenchAdminController(RuntimeConfigStore runtimeConfigStore,
                                    InboundDlqService inboundDlqService,
                                    MessagingOutboxService messagingOutboxService,
                                    RestOutboxService restOutboxService,
                                    IntegrationTemplateService integrationTemplateService) {
        this.runtimeConfigStore = runtimeConfigStore;
        this.inboundDlqService = inboundDlqService;
        this.messagingOutboxService = messagingOutboxService;
        this.restOutboxService = restOutboxService;
        this.integrationTemplateService = integrationTemplateService;
    }

    @Get(uri = "/fallback-activations")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Виджет fallback-активаций CRM/Medical/Appointment")
    @ApiResponse(responseCode = "200", description = "Данные виджета", content = @Content(schema = @Schema(implementation = FallbackActivationsResponse.class)))
    public FallbackActivationsResponse fallbackActivations() {
        RuntimeConfigStore.RuntimeConfig cfg = runtimeConfigStore.getEffective();
        List<FallbackActivationItem> items = new ArrayList<>();

        RuntimeConfigStore.CrmConfig crm = cfg == null ? null : cfg.crm();
        if (crm != null && crm.enabled()) {
            boolean fallback = crm.profile() != RuntimeConfigStore.CrmProfile.GENERIC;
            items.add(new FallbackActivationItem("CRM", crm.profile().name(), "GENERIC", fallback,
                    fallback ? "Prerelease fallback на GENERIC" : "Native profile"));
        }

        RuntimeConfigStore.MedicalConfig medical = cfg == null ? null : cfg.medical();
        if (medical != null && medical.enabled()) {
            boolean fallback = medical.profile() != RuntimeConfigStore.MedicalProfile.FHIR_GENERIC;
            items.add(new FallbackActivationItem("MEDICAL", medical.profile().name(), "FHIR_GENERIC", fallback,
                    fallback ? "Prerelease fallback на FHIR_GENERIC" : "Native profile"));
        }

        RuntimeConfigStore.AppointmentConfig appointment = cfg == null ? null : cfg.appointment();
        if (appointment != null && appointment.enabled()) {
            boolean fallback = appointment.profile() != RuntimeConfigStore.AppointmentProfile.GENERIC
                    && appointment.profile() != RuntimeConfigStore.AppointmentProfile.CUSTOM_CONNECTOR;
            items.add(new FallbackActivationItem("APPOINTMENT", appointment.profile().name(), "GENERIC", fallback,
                    fallback ? "Prerelease fallback на GENERIC" : "Native profile"));
        }

        long activeFallbacks = items.stream().filter(FallbackActivationItem::fallbackActive).count();
        return new FallbackActivationsResponse(items, (int) activeFallbacks, items.size());
    }

    @Get(uri = "/adapter-profiles/{adapterId}/last-errors")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Drill-down профиля адаптера: последние ошибки")
    @ApiResponse(responseCode = "200", description = "Список ошибок", content = @Content(schema = @Schema(implementation = AdapterProfileLastErrorsResponse.class)))
    public AdapterProfileLastErrorsResponse adapterProfileLastErrors(String adapterId, Integer limit) {
        int lim = limit == null ? 20 : Math.min(Math.max(1, limit), 100);
        List<AdapterErrorItem> items = new ArrayList<>();

        for (RestOutboxService.RestListItem row : restOutboxService.list(null, adapterId, lim * 3)) {
            RestOutboxService.RestRecord full = restOutboxService.get(row.id());
            if (full == null || (full.lastErrorCode() == null && full.lastErrorMessage() == null && full.lastHttpStatus() == null)) {
                continue;
            }
            items.add(new AdapterErrorItem(
                    "REST_OUTBOX",
                    adapterId,
                    row.id(),
                    row.status(),
                    full.lastErrorCode(),
                    full.lastErrorMessage(),
                    full.lastHttpStatus(),
                    row.updatedAt(),
                    row.path()
            ));
        }

        for (MessagingOutboxService.OutboxListItem row : messagingOutboxService.list(null, lim * 3)) {
            if (adapterId == null || adapterId.isBlank() || !adapterId.equals(row.provider())) {
                continue;
            }
            MessagingOutboxService.OutboxRecord full = messagingOutboxService.get(row.id());
            if (full == null || (full.lastErrorCode() == null && full.lastErrorMessage() == null)) {
                continue;
            }
            items.add(new AdapterErrorItem(
                    "MSG_OUTBOX",
                    row.provider(),
                    row.id(),
                    row.status(),
                    full.lastErrorCode(),
                    full.lastErrorMessage(),
                    null,
                    row.updatedAt(),
                    row.destination()
            ));
        }

        items.sort(Comparator.comparing(AdapterErrorItem::updatedAt, Comparator.nullsLast(String::compareTo)).reversed());
        if (items.size() > lim) {
            items = new ArrayList<>(items.subList(0, lim));
        }
        return new AdapterProfileLastErrorsResponse(adapterId, items);
    }

    @Post(uri = "/template-set/import-preview")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Мастер импорта template set: preview конфликтов")
    @ApiResponse(responseCode = "200", description = "Preview подготовлен", content = @Content(schema = @Schema(implementation = TemplateSetImportPreviewResponse.class)))
    public TemplateSetImportPreviewResponse templateSetImportPreview(@Body TemplateSetImportPreviewRequest request) {
        DecodedArchive decoded = decodeBase64(request == null ? null : request.archiveBase64());
        if (!decoded.valid()) {
            return new TemplateSetImportPreviewResponse(
                    false,
                    null,
                    request == null ? null : request.mergeStrategy(),
                    List.of(),
                    List.of("archiveBase64 is not a valid Base64 payload"),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }
        IntegrationTemplateService.ImportDryRunResult r = integrationTemplateService.importDryRun(
                decoded.bytes(),
                request == null ? null : request.mergeStrategy(),
                request == null ? null : request.branchIdHint()
        );
        return new TemplateSetImportPreviewResponse(
                r.ok(),
                r.format(),
                r.mergeStrategy(),
                r.warnings(),
                r.errors(),
                r.changedArtifacts(),
                r.conflicts(),
                r.scriptsChecked()
        );
    }

    @Get(uri = "/incidents/by-correlation")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Быстрый фильтр инцидентов по correlationId")
    @ApiResponse(responseCode = "200", description = "Инциденты", content = @Content(schema = @Schema(implementation = CorrelationIncidentsResponse.class)))
    public CorrelationIncidentsResponse incidentsByCorrelation(String correlationId, Integer limit) {
        int lim = limit == null ? 50 : Math.min(Math.max(1, limit), 200);
        String corr = correlationId == null ? null : correlationId.trim();
        if (corr == null || corr.isBlank()) {
            return new CorrelationIncidentsResponse(correlationId, List.of());
        }

        List<CorrelationIncidentItem> out = new ArrayList<>();

        for (InboundDlqService.DlqRecord d : inboundDlqService.list(null, null, null, null, null, corr, lim)) {
            out.add(new CorrelationIncidentItem("DLQ", d.id(), d.status(), d.errorCode(), d.errorMessage(), d.updatedAt()));
        }
        for (MessagingOutboxService.OutboxListItem m : messagingOutboxService.listByCorrelation(corr, lim)) {
            MessagingOutboxService.OutboxRecord full = messagingOutboxService.get(m.id());
            out.add(new CorrelationIncidentItem("MSG_OUTBOX", m.id(), m.status(),
                    full == null ? null : full.lastErrorCode(),
                    full == null ? null : full.lastErrorMessage(),
                    m.updatedAt()));
        }
        for (RestOutboxService.RestListItem r : restOutboxService.listByCorrelation(corr, lim)) {
            RestOutboxService.RestRecord full = restOutboxService.get(r.id());
            out.add(new CorrelationIncidentItem("REST_OUTBOX", r.id(), r.status(),
                    full == null ? null : full.lastErrorCode(),
                    full == null ? null : full.lastErrorMessage(),
                    r.updatedAt()));
        }

        out.sort(Comparator.comparing(CorrelationIncidentItem::updatedAt, Comparator.nullsLast(String::compareTo)).reversed());
        if (out.size() > lim) {
            out = new ArrayList<>(out.subList(0, lim));
        }
        return new CorrelationIncidentsResponse(corr, out);
    }

    @Get(uri = "/runtime-audit/recent")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Таблица последних runtime-audit изменений с diff viewer")
    @ApiResponse(responseCode = "200", description = "Runtime audit", content = @Content(schema = @Schema(implementation = RuntimeAuditWorkbenchResponse.class)))
    public RuntimeAuditWorkbenchResponse runtimeAuditRecent(Integer limit) {
        int lim = limit == null ? 50 : Math.min(Math.max(1, limit), 500);
        List<RuntimeAuditWorkbenchItem> items = new ArrayList<>();
        for (RuntimeConfigStore.RuntimeConfigAuditEntry a : runtimeConfigStore.getAuditTrail(lim)) {
            List<String> sections = new ArrayList<>();
            if (a.changedSections() != null && !a.changedSections().isBlank()) {
                for (String part : a.changedSections().split(",")) {
                    String s = part.trim();
                    if (!s.isBlank()) {
                        sections.add(s);
                    }
                }
            }
            items.add(new RuntimeAuditWorkbenchItem(a.changedAt(), a.actor(), a.source(), a.fromRevision(), a.toRevision(), a.note(), sections));
        }
        return new RuntimeAuditWorkbenchResponse(items);
    }

    private static DecodedArchive decodeBase64(String archiveBase64) {
        if (archiveBase64 == null || archiveBase64.isBlank()) {
            return new DecodedArchive(new byte[0], true);
        }
        try {
            return new DecodedArchive(Base64.getDecoder().decode(archiveBase64), true);
        } catch (IllegalArgumentException e) {
            return new DecodedArchive(new byte[0], false);
        }
    }

    private record DecodedArchive(byte[] bytes, boolean valid) {
    }

    @Serdeable
    public record FallbackActivationItem(String domain,
                                         String requestedProfile,
                                         String executionProfile,
                                         boolean fallbackActive,
                                         String note) {
    }

    @Serdeable
    public record FallbackActivationsResponse(List<FallbackActivationItem> items,
                                              int activeFallbacks,
                                              int totalEnabledDomains) {
    }

    @Serdeable
    public record AdapterErrorItem(String source,
                                   String adapterId,
                                   long refId,
                                   String status,
                                   String errorCode,
                                   String errorMessage,
                                   Integer httpStatus,
                                   String updatedAt,
                                   String location) {
    }

    @Serdeable
    public record AdapterProfileLastErrorsResponse(String adapterId,
                                                   List<AdapterErrorItem> items) {
    }

    @Serdeable
    public record TemplateSetImportPreviewRequest(String archiveBase64,
                                                  String mergeStrategy,
                                                  String branchIdHint) {
    }

    @Serdeable
    public record TemplateSetImportPreviewResponse(boolean ok,
                                                   String format,
                                                   String mergeStrategy,
                                                   List<String> warnings,
                                                   List<String> errors,
                                                   List<String> changedArtifacts,
                                                   List<String> conflicts,
                                                   List<String> scriptsChecked) {
    }

    @Serdeable
    public record CorrelationIncidentItem(String source,
                                          long refId,
                                          String status,
                                          String errorCode,
                                          String errorMessage,
                                          String updatedAt) {
    }

    @Serdeable
    public record CorrelationIncidentsResponse(String correlationId,
                                               List<CorrelationIncidentItem> items) {
    }

    @Serdeable
    public record RuntimeAuditWorkbenchItem(String changedAt,
                                            String actor,
                                            String source,
                                            String fromRevision,
                                            String toRevision,
                                            String note,
                                            List<String> changedSections) {
    }

    @Serdeable
    public record RuntimeAuditWorkbenchResponse(List<RuntimeAuditWorkbenchItem> items) {
    }
}
