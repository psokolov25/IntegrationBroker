package ru.aritmos.integrationbroker.api;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import ru.aritmos.integrationbroker.adapters.VisitManagerConflictMetrics;
import ru.aritmos.integrationbroker.core.InboundDlqService;
import ru.aritmos.integrationbroker.core.IdempotencyService;
import ru.aritmos.integrationbroker.core.InboundProcessingService;
import ru.aritmos.integrationbroker.core.CorrelationContext;
import ru.aritmos.integrationbroker.core.KeycloakProxyEnrichmentService;
import ru.aritmos.integrationbroker.core.MessagingOutboxService;
import ru.aritmos.integrationbroker.core.RestOutboxService;
import ru.aritmos.integrationbroker.core.SensitiveDataSanitizer;
import ru.aritmos.integrationbroker.model.InboundEnvelope;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Публичный REST ingress API Integration Broker.
 * <p>
 * Этот API предназначен для приёма событий/команд и запуска соответствующих flow.
 * Другие inbound-каналы (Kafka/RabbitMQ/NATS/JetStream) будут добавлены следующими итерациями,
 * но обязаны приводить данные к {@link InboundEnvelope}.
 */
@Controller("/api")
@Tag(name = "Integration Broker — публичный API", description = "Приём входящих событий/команд и получение результата выполнения flow")
public class InboundController {

    private final InboundProcessingService processingService;
    private final IdempotencyService idempotencyService;
    private final InboundDlqService inboundDlqService;
    private final KeycloakProxyEnrichmentService keycloakProxyEnrichmentService;
    private final MessagingOutboxService messagingOutboxService;
    private final RestOutboxService restOutboxService;
    private final VisitManagerConflictMetrics visitManagerConflictMetrics;
    private final ObjectMapper objectMapper;

    @Inject
    public InboundController(InboundProcessingService processingService,
                             IdempotencyService idempotencyService,
                             InboundDlqService inboundDlqService,
                             KeycloakProxyEnrichmentService keycloakProxyEnrichmentService,
                             MessagingOutboxService messagingOutboxService,
                             RestOutboxService restOutboxService,
                             VisitManagerConflictMetrics visitManagerConflictMetrics,
                             ObjectMapper objectMapper) {
        this.processingService = processingService;
        this.idempotencyService = idempotencyService;
        this.inboundDlqService = inboundDlqService;
        this.keycloakProxyEnrichmentService = keycloakProxyEnrichmentService;
        this.messagingOutboxService = messagingOutboxService;
        this.restOutboxService = restOutboxService;
        this.visitManagerConflictMetrics = visitManagerConflictMetrics;
        this.objectMapper = objectMapper;
    }

    @Post(uri = "/inbound", consumes = MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Принять входящее сообщение (событие или команда)",
            description = "Сообщение приводится к единому контракту InboundEnvelope, затем выбирается flow и выполняется Groovy-логика. " +
                    "Идемпотентность применяется до выполнения flow (PROCESS/SKIP_COMPLETED/LOCKED). " +
                    "Если включён KeycloakProxy enrichment, meta дополняется полями user/principal без хранения и логирования сырых токенов."
    )
    @ApiResponse(responseCode = "200", description = "Сообщение обработано или пропущено как уже обработанное", content = @Content(schema = @Schema(implementation = InboundResult.class)))
    @ApiResponse(responseCode = "202", description = "Сообщение уже обрабатывается (LOCKED). Это не poison message", content = @Content(schema = @Schema(implementation = InboundResult.class)))
    @ApiResponse(responseCode = "400", description = "Некорректный запрос или не найден flow")
    @ApiResponse(responseCode = "500", description = "Выполнение flow завершилось ошибкой. Если включён inbound DLQ, сообщение сохранено для replay", content = @Content(schema = @Schema(implementation = InboundResult.class)))
    public HttpResponse<InboundResult> inbound(@Body InboundEnvelope envelope) {
        try {
            InboundEnvelope normalized = normalizeCorrelation(envelope);
            InboundProcessingService.ProcessingResult res = processingService.process(normalized);
            InboundResult body = new InboundResult(res.outcome(), res.idempotencyKey(), res.output(), null, null, null);

            if ("LOCKED".equals(res.outcome())) {
                return HttpResponse.status(HttpStatus.ACCEPTED).body(body);
            }

            return HttpResponse.ok(body);
        } catch (InboundProcessingService.StoredInDlqException ex) {
            InboundResult body = new InboundResult(
                    "DLQ",
                    ex.idempotencyKey(),
                    Map.of("note", "Сообщение сохранено в inbound DLQ для последующего replay"),
                    ex.dlqId(),
                    ex.errorCode(),
                    ex.safeMessage()
            );
            return HttpResponse.serverError(body);
        } catch (IllegalArgumentException ex) {
            InboundResult body = new InboundResult(
                    "REJECTED",
                    null,
                    Map.of("note", "Запрос отклонён", "reason", SensitiveDataSanitizer.sanitizeText(ex.getMessage())),
                    null,
                    "BAD_REQUEST",
                    SensitiveDataSanitizer.sanitizeText(ex.getMessage())
            );
            return HttpResponse.badRequest(body);
        } catch (Exception ex) {
            InboundResult body = new InboundResult(
                    "FAILED",
                    null,
                    Map.of("note", "Обработка завершилась ошибкой"),
                    null,
                    "INTERNAL_ERROR",
                    SensitiveDataSanitizer.sanitizeText(ex.getMessage())
            );
            return HttpResponse.serverError(body);
        }
    }

    private InboundEnvelope normalizeCorrelation(InboundEnvelope envelope) {
        CorrelationContext context = CorrelationContext.fromInbound(envelope);
        Map<String, String> headers = envelope.headers() == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(envelope.headers());
        headers.putIfAbsent("X-Correlation-Id", context.correlationId());
        headers.putIfAbsent("X-Request-Id", context.requestId());

        String messageId = envelope.messageId();
        if (messageId == null || messageId.isBlank()) {
            messageId = context.requestId();
        }

        return new InboundEnvelope(
                envelope.kind(),
                envelope.type(),
                envelope.payload(),
                headers,
                messageId,
                context.correlationId(),
                envelope.branchId(),
                envelope.userId(),
                envelope.sourceMeta()
        );
    }

    @Get(uri = "/metrics/integration")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Получить минимальные эксплуатационные метрики Integration Broker",
            description = "Метрики включают идемпотентность, inbound DLQ и outbox/rest-outbox. " +
                    "Значения предназначены для быстрой диагностики без доступа к БД."
    )
    @ApiResponse(responseCode = "200", description = "Метрики", content = @Content(schema = @Schema(implementation = IntegrationMetrics.class)))
    public IntegrationMetrics metrics() {
        long inProgress = idempotencyService.countByStatus(IdempotencyService.Status.IN_PROGRESS);
        long completed = idempotencyService.countByStatus(IdempotencyService.Status.COMPLETED);
        long failed = idempotencyService.countByStatus(IdempotencyService.Status.FAILED);

        long dlqPending = inboundDlqService.countByStatus(InboundDlqService.Status.PENDING);
        long dlqReplayed = inboundDlqService.countByStatus(InboundDlqService.Status.REPLAYED);
        long dlqDead = inboundDlqService.countByStatus(InboundDlqService.Status.DEAD);

        long msgPending = messagingOutboxService.countByStatus(MessagingOutboxService.Status.PENDING);
        long msgSent = messagingOutboxService.countByStatus(MessagingOutboxService.Status.SENT);
        long msgDead = messagingOutboxService.countByStatus(MessagingOutboxService.Status.DEAD);

        long restPending = restOutboxService.countByStatus(RestOutboxService.Status.PENDING);
        long restSent = restOutboxService.countByStatus(RestOutboxService.Status.SENT);
        long restDead = restOutboxService.countByStatus(RestOutboxService.Status.DEAD);

        long kcHits = keycloakProxyEnrichmentService.cacheHits();
        long kcMiss = keycloakProxyEnrichmentService.cacheMisses();
        long kcErr = keycloakProxyEnrichmentService.errors();

        long vmConflicts409 = visitManagerConflictMetrics.conflicts409();

        return new IntegrationMetrics(inProgress, completed, failed,
                dlqPending, dlqReplayed, dlqDead,
                msgPending, msgSent, msgDead,
                restPending, restSent, restDead,
                kcHits, kcMiss, kcErr,
                vmConflicts409);
    }

    /**
     * Результат выполнения inbound-обработки.
     */
    @Serdeable
    @Schema(name = "InboundResult", description = "Результат обработки входящего сообщения")
    public record InboundResult(
            @Schema(description = "Итог: PROCESSED / SKIP_COMPLETED / LOCKED")
            String outcome,
            @Schema(description = "Ключ идемпотентности (SHA-256)")
            String idempotencyKey,
            @Schema(description = "Результат выполнения flow (output)")
            Map<String, Object> output,
            @Schema(description = "Идентификатор записи inbound DLQ (если outcome=DLQ)")
            Long dlqId,
            @Schema(description = "Код ошибки (санитизированный)")
            String errorCode,
            @Schema(description = "Сообщение ошибки (санитизированное и укороченное)")
            String errorMessage
    ) {
    }

    /**
     * Минимальные эксплуатационные метрики.
     */
    @Serdeable
    @Schema(name = "IntegrationMetrics", description = "Минимальные метрики по состоянию служебных подсистем")
    public record IntegrationMetrics(
            @Schema(description = "Количество сообщений в статусе IN_PROGRESS")
            long idempotencyInProgress,
            @Schema(description = "Количество сообщений в статусе COMPLETED")
            long idempotencyCompleted,
            @Schema(description = "Количество сообщений в статусе FAILED")
            long idempotencyFailed,
            @Schema(description = "Inbound DLQ: ожидают replay")
            long inboundDlqPending,
            @Schema(description = "Inbound DLQ: успешно переиграны")
            long inboundDlqReplayed,
            @Schema(description = "Inbound DLQ: признаны DEAD")
            long inboundDlqDead,

            @Schema(description = "Messaging outbox: ожидают отправки")
            long messagingOutboxPending,
            @Schema(description = "Messaging outbox: отправлены")
            long messagingOutboxSent,
            @Schema(description = "Messaging outbox: признаны DEAD")
            long messagingOutboxDead,

            @Schema(description = "REST outbox: ожидают отправки")
            long restOutboxPending,
            @Schema(description = "REST outbox: отправлены")
            long restOutboxSent,
            @Schema(description = "REST outbox: признаны DEAD")
            long restOutboxDead,
            @Schema(description = "KeycloakProxy enrichment: попадания в кэш")
            long keycloakProxyCacheHits,
            @Schema(description = "KeycloakProxy enrichment: промахи кэша")
            long keycloakProxyCacheMisses,
            @Schema(description = "KeycloakProxy enrichment: ошибки")
            long keycloakProxyErrors,
            @Schema(description = "VisitManager: количество конфликтов HTTP 409")
            long visitManagerConflicts409
    ) {
    }
}

/**
 * Admin API: управление и наблюдение за идемпотентностью.
 * <p>
 * В дальнейшем Admin API будет расширен разделами outbox/rest-outbox/DLQ/replay.
 */
@Secured("IB_ADMIN")
@Controller("/admin/idempotency")
@Tag(name = "Integration Broker — Admin API (Idempotency)", description = "Просмотр записей идемпотентности и диагностика повторных доставок")
class AdminIdempotencyController {

    private final IdempotencyService idempotencyService;

    @Inject
    AdminIdempotencyController(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Get(uri = "/{key}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Получить запись идемпотентности по ключу",
            description = "Используется для диагностики повторной доставки и статуса обработки."
    )
    @ApiResponse(responseCode = "200", description = "Запись найдена", content = @Content(schema = @Schema(implementation = IdempotencyService.IdempotencyRecord.class)))
    @ApiResponse(responseCode = "404", description = "Запись не найдена")
    public HttpResponse<IdempotencyService.IdempotencyRecord> getByKey(
            @Parameter(description = "Ключ идемпотентности (SHA-256)")
            @PathVariable("key") String key) {

        IdempotencyService.IdempotencyRecord rec = idempotencyService.get(key);
        if (rec == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(rec);
    }

    @Get
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Список записей идемпотентности",
            description = "Фильтрация по статусу опциональна. Лимит ограничен для эксплуатационной безопасности."
    )
    @ApiResponse(responseCode = "200", description = "Список", content = @Content(schema = @Schema(implementation = IdempotencyListResponse.class)))
    public IdempotencyListResponse list(
            @Parameter(description = "Фильтр статуса (IN_PROGRESS/COMPLETED/FAILED)") String status,
            @Parameter(description = "Лимит (1..200)") Integer limit) {

        int lim = (limit == null) ? 50 : limit;
        List<IdempotencyService.IdempotencyRecord> items = idempotencyService.list(status, lim);
        return new IdempotencyListResponse(items);
    }

    @Post(uri = "/{key}/unlock", consumes = MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Ручная разморозка LOCKED-записи идемпотентности",
            description = "Переводит запись из IN_PROGRESS в FAILED c аудитом причины ручной разморозки. " +
                    "Используется при зависших обработках и не изменяет payload/result записи."
    )
    @ApiResponse(responseCode = "200", description = "Запись разморожена")
    @ApiResponse(responseCode = "404", description = "Запись не найдена или не находится в IN_PROGRESS")
    public HttpResponse<UnlockResponse> unlock(
            @PathVariable("key") String key,
            @Body UnlockRequest request) {

        String actor = request == null ? null : request.actor();
        String reason = request == null ? null : request.reason();
        boolean unlocked = idempotencyService.manualUnlock(key, actor, reason);
        if (!unlocked) {
            return HttpResponse.notFound(new UnlockResponse(false, key, "NOT_FOUND_OR_NOT_IN_PROGRESS"));
        }
        return HttpResponse.ok(new UnlockResponse(true, key, "UNLOCKED"));
    }

    @Serdeable
    @Schema(name = "IdempotencyListResponse", description = "Ответ со списком записей идемпотентности")
    record IdempotencyListResponse(
            @Schema(description = "Список записей")
            List<IdempotencyService.IdempotencyRecord> items
    ) {
    }

    @Serdeable
    @Schema(name = "IdempotencyUnlockRequest", description = "Запрос на ручную разморозку idempotency-ключа")
    record UnlockRequest(
            @Schema(description = "Кто выполнил разморозку")
            String actor,
            @Schema(description = "Причина разморозки")
            String reason
    ) {
    }

    @Serdeable
    @Schema(name = "IdempotencyUnlockResponse", description = "Результат ручной разморозки")
    record UnlockResponse(
            @Schema(description = "Успешно ли разморожено")
            boolean unlocked,
            @Schema(description = "Ключ идемпотентности")
            String idempotencyKey,
            @Schema(description = "Код результата")
            String code
    ) {
    }
}

/**
 * Admin API: inbound DLQ и replay.
 */
@Secured("IB_ADMIN")
@Controller("/admin/dlq")
@Tag(name = "Integration Broker — Admin API (Inbound DLQ)", description = "Просмотр сообщений в inbound DLQ и выполнение replay")
class AdminInboundDlqController {

    private final InboundDlqService inboundDlqService;
    private final InboundProcessingService processingService;
    private final ObjectMapper objectMapper;

    @Inject
    AdminInboundDlqController(InboundDlqService inboundDlqService,
                              InboundProcessingService processingService,
                              ObjectMapper objectMapper) {
        this.inboundDlqService = inboundDlqService;
        this.processingService = processingService;
        this.objectMapper = objectMapper;
    }

    @Get(uri = "/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Получить запись inbound DLQ",
            description = "Возвращает DLQ-запись и исходные данные (payload/headers/sourceMeta) для диагностики и подготовки replay. " +
                    "Важно: заголовки уже санитизированы (без токенов/секретов)."
    )
    @ApiResponse(responseCode = "200", description = "Запись найдена", content = @Content(schema = @Schema(implementation = DlqGetResponse.class)))
    @ApiResponse(responseCode = "404", description = "Запись не найдена")
    public HttpResponse<DlqGetResponse> get(@Parameter(description = "ID записи DLQ") @PathVariable("id") long id) {
        InboundDlqService.DlqFull full = inboundDlqService.getFull(id);
        if (full == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(new DlqGetResponse(full.record(), full.headers(), full.payload(), full.sourceMeta(), full.replayResultJson()));
    }

    @Get
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Список inbound DLQ",
            description = "Список DLQ-записей с опциональной фильтрацией по статусу. Payload в список не включается."
    )
    @ApiResponse(responseCode = "200", description = "Список", content = @Content(schema = @Schema(implementation = DlqListResponse.class)))
    public DlqListResponse list(
            @Parameter(description = "Фильтр статуса (PENDING/REPLAYED/DEAD)") String status,
            @Parameter(description = "Фильтр по типу сообщения") String type,
            @Parameter(description = "Фильтр по источнику (source/sourceSystem/system)") String source,
            @Parameter(description = "Фильтр по branchId") String branchId,
            @Parameter(description = "Лимит (1..200)") Integer limit) {
        int lim = (limit == null) ? 50 : limit;
        return new DlqListResponse(inboundDlqService.list(status, type, source, branchId, lim));
    }

    @Post(uri = "/{id}/replay")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Выполнить replay сообщения из DLQ",
            description = "Переигрывает обработку сохранённого сообщения. Идемпотентность применяется стандартно. " +
                    "LOCKED не считается poison message и не приводит к переводу записи в DEAD."
    )
    @ApiResponse(responseCode = "200", description = "Replay выполнен (PROCESSED или SKIP_COMPLETED)", content = @Content(schema = @Schema(implementation = DlqReplayResponse.class)))
    @ApiResponse(responseCode = "202", description = "Replay не выполнен из-за LOCKED (сообщение обрабатывается другим воркером)", content = @Content(schema = @Schema(implementation = DlqReplayResponse.class)))
    @ApiResponse(responseCode = "404", description = "Запись не найдена")
    @ApiResponse(responseCode = "409", description = "Запись в статусе DEAD (лимит попыток исчерпан)")
    @ApiResponse(responseCode = "500", description = "Replay завершился ошибкой (attempts увеличен)", content = @Content(schema = @Schema(implementation = DlqReplayResponse.class)))
    public HttpResponse<DlqReplayResponse> replay(@Parameter(description = "ID записи DLQ") @PathVariable("id") long id) {
        DlqReplayResponse response = replayOne(id);
        if ("NOT_FOUND".equals(response.outcome())) {
            return HttpResponse.notFound();
        }
        if ("DEAD".equals(response.outcome())) {
            return HttpResponse.status(HttpStatus.CONFLICT).body(response);
        }
        if ("LOCKED".equals(response.outcome())) {
            return HttpResponse.status(HttpStatus.ACCEPTED).body(response);
        }
        if ("FAILED".equals(response.outcome())) {
            return HttpResponse.serverError(response);
        }
        return HttpResponse.ok(response);
    }

    @Post(uri = "/replay-batch")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Batch replay сообщений из DLQ",
            description = "Выполняет переигрывание нескольких DLQ-записей с фильтрацией по status/type/source/branch. " +
                    "По умолчанию обрабатываются PENDING-записи."
    )
    @ApiResponse(responseCode = "200", description = "Batch replay выполнен", content = @Content(schema = @Schema(implementation = DlqReplayBatchResponse.class)))
    public DlqReplayBatchResponse replayBatch(@Body DlqReplayBatchRequest request) {
        int lim = (request == null || request.limit() == null) ? 50 : request.limit();
        String status = (request == null || request.status() == null || request.status().isBlank()) ? InboundDlqService.Status.PENDING.name() : request.status();
        String type = request == null ? null : request.type();
        String source = request == null ? null : request.source();
        String branchId = request == null ? null : request.branchId();

        List<InboundDlqService.DlqRecord> records = inboundDlqService.list(status, type, source, branchId, lim);
        List<DlqReplayResponse> items = new ArrayList<>();
        int ok = 0;
        int locked = 0;
        int failed = 0;
        int dead = 0;

        for (InboundDlqService.DlqRecord rec : records) {
            DlqReplayResponse res = replayOne(rec.id());
            items.add(res);
            if ("PROCESSED".equals(res.outcome()) || "SKIP_COMPLETED".equals(res.outcome()) || "REPLAYED".equals(res.outcome())) {
                ok++;
            } else if ("LOCKED".equals(res.outcome())) {
                locked++;
            } else if ("DEAD".equals(res.outcome())) {
                dead++;
            } else if ("FAILED".equals(res.outcome())) {
                failed++;
            }
        }

        return new DlqReplayBatchResponse(records.size(), ok, locked, failed, dead, items);
    }

    private DlqReplayResponse replayOne(long id) {
        InboundDlqService.DlqFull full = inboundDlqService.getFull(id);
        if (full == null) {
            return new DlqReplayResponse("NOT_FOUND", id, 0, 0, null, "DLQ_NOT_FOUND", "Запись не найдена");
        }

        if (InboundDlqService.Status.DEAD.name().equals(full.record().status())) {
            return new DlqReplayResponse("DEAD", id, full.record().attempts(), full.record().maxAttempts(), null, "DLQ_DEAD", "Лимит попыток исчерпан");
        }

        InboundEnvelope env = new InboundEnvelope(
                InboundEnvelope.Kind.valueOf(full.record().kind()),
                full.record().type(),
                full.payload(),
                full.headers(),
                full.record().messageId(),
                full.record().correlationId(),
                full.record().branchId(),
                full.record().userId(),
                mergeReplayMeta(full.sourceMeta(), id)
        );

        try {
            InboundProcessingService.ProcessingResult res = processingService.process(env);
            if ("LOCKED".equals(res.outcome())) {
                return new DlqReplayResponse("LOCKED", id, full.record().attempts(), full.record().maxAttempts(), res.output(), null, null);
            }

            String replayJson = objectMapper.writeValueAsString(res);
            inboundDlqService.markReplayed(id, replayJson);
            return new DlqReplayResponse(res.outcome(), id, full.record().attempts(), full.record().maxAttempts(), res.output(), null, null);
        } catch (Exception ex) {
            String safeMsg = SensitiveDataSanitizer.sanitizeText(ex.getMessage());
            inboundDlqService.markReplayFailed(id, "REPLAY_FAILED", safeMsg);
            return new DlqReplayResponse("FAILED", id, full.record().attempts() + 1, full.record().maxAttempts(), null, "REPLAY_FAILED", safeMsg);
        }
    }

    private Map<String, Object> mergeReplayMeta(Map<String, Object> sourceMeta, long dlqId) {
        if (sourceMeta == null || sourceMeta.isEmpty()) {
            return Map.of("dlqReplayId", dlqId);
        }
        // Важно: сохраняем исходные метаданные источника, добавляя маркер replay.
        sourceMeta.put("dlqReplayId", dlqId);
        return sourceMeta;
    }

    @Serdeable
    @Schema(name = "DlqGetResponse", description = "Полная запись inbound DLQ (для диагностики и replay)")
    record DlqGetResponse(
            @Schema(description = "Краткая форма записи") InboundDlqService.DlqRecord record,
            @Schema(description = "Санитизированные заголовки") Map<String, String> headers,
            @Schema(description = "Исходный payload") Object payload,
            @Schema(description = "Метаданные источника") Map<String, Object> sourceMeta,
            @Schema(description = "Сохранённый результат replay (если был)") String replayResultJson
    ) {
    }

    @Serdeable
    @Schema(name = "DlqListResponse", description = "Список записей inbound DLQ")
    record DlqListResponse(
            @Schema(description = "Элементы") List<InboundDlqService.DlqRecord> items
    ) {
    }

    @Serdeable
    @Schema(name = "DlqReplayResponse", description = "Результат replay DLQ-сообщения")
    record DlqReplayResponse(
            @Schema(description = "Итог: PROCESSED / SKIP_COMPLETED / LOCKED / FAILED / DEAD") String outcome,
            @Schema(description = "ID записи DLQ") long dlqId,
            @Schema(description = "Текущее количество попыток") int attempts,
            @Schema(description = "Максимальное количество попыток") int maxAttempts,
            @Schema(description = "Результат выполнения (если есть)") Map<String, Object> output,
            @Schema(description = "Код ошибки (если есть)") String errorCode,
            @Schema(description = "Сообщение ошибки (если есть)") String errorMessage
    ) {
    }

    @Serdeable
    @Schema(name = "DlqReplayBatchRequest", description = "Параметры пакетного replay inbound DLQ")
    record DlqReplayBatchRequest(
            @Schema(description = "Фильтр статуса (по умолчанию PENDING)") String status,
            @Schema(description = "Фильтр по type") String type,
            @Schema(description = "Фильтр по source/sourceSystem/system") String source,
            @Schema(description = "Фильтр по branchId") String branchId,
            @Schema(description = "Лимит записей (1..200)") Integer limit
    ) {
    }

    @Serdeable
    @Schema(name = "DlqReplayBatchResponse", description = "Результат пакетного replay inbound DLQ")
    record DlqReplayBatchResponse(
            @Schema(description = "Количество выбранных записей") int selected,
            @Schema(description = "Успешно replayed (PROCESSED/SKIP_COMPLETED)") int success,
            @Schema(description = "Количество LOCKED") int locked,
            @Schema(description = "Количество FAILED") int failed,
            @Schema(description = "Количество DEAD") int dead,
            @Schema(description = "Результаты по каждой записи") List<DlqReplayResponse> items
    ) {
    }
}


/**
 * Admin API: messaging outbox и replay.
 * <p>
 * Назначение: эксплуатационная диагностика, ручная переотправка, контроль статусов PENDING/SENT/DEAD.
 * <p>
 * Важно:
 * <ul>
 *   <li>outbox хранится в PostgreSQL как долговременный журнал;</li>
 *   <li>заголовки санитизируются (Authorization/Cookie и т.п. не сохраняются);</li>
 *   <li>replay переводит запись в PENDING и назначает ближайшую попытку.</li>
 * </ul>
 */
@Secured("IB_ADMIN")
@Controller("/admin/outbox/messaging")
@Tag(name = "Integration Broker — Admin API (Messaging Outbox)", description = "Просмотр записей messaging outbox и ручной replay")
class AdminMessagingOutboxController {

    private final MessagingOutboxService messagingOutboxService;

    @Inject
    AdminMessagingOutboxController(MessagingOutboxService messagingOutboxService) {
        this.messagingOutboxService = messagingOutboxService;
    }

    @Get(uri = "/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Получить запись messaging outbox",
            description = "Возвращает полную запись, включая payload_json и headers_json (заголовки уже санитизированы)."
    )
    @ApiResponse(responseCode = "200", description = "Запись найдена", content = @Content(schema = @Schema(implementation = MessagingOutboxService.OutboxRecord.class)))
    @ApiResponse(responseCode = "404", description = "Запись не найдена")
    public HttpResponse<MessagingOutboxService.OutboxRecord> get(@Parameter(description = "ID записи outbox") @PathVariable("id") long id) {
        MessagingOutboxService.OutboxRecord rec = messagingOutboxService.get(id);
        if (rec == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(rec);
    }

    @Get
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Список записей messaging outbox",
            description = "Список возвращается без payload_json для уменьшения риска утечек и нагрузки. Фильтр по статусу опционален."
    )
    @ApiResponse(responseCode = "200", description = "Список", content = @Content(schema = @Schema(implementation = MessagingOutboxListResponse.class)))
    public MessagingOutboxListResponse list(
            @Parameter(description = "Фильтр статуса (PENDING/SENDING/SENT/DEAD)") String status,
            @Parameter(description = "Лимит (1..200)") Integer limit) {
        int lim = (limit == null) ? 50 : limit;
        return new MessagingOutboxListResponse(messagingOutboxService.list(status, lim));
    }

    @Post(uri = "/{id}/replay")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Поставить запись messaging outbox на повторную отправку",
            description = "Переводит запись в PENDING и назначает ближайшую попытку. При resetAttempts=true сбрасывает счётчик attempts."
    )
    @ApiResponse(responseCode = "200", description = "Replay поставлен в очередь", content = @Content(schema = @Schema(implementation = AdminMessagingOutboxController.OutboxReplayResponse.class)))
    @ApiResponse(responseCode = "404", description = "Запись не найдена")
    public HttpResponse<OutboxReplayResponse> replay(
            @Parameter(description = "ID записи outbox") @PathVariable("id") long id,
            @Parameter(description = "Сбросить attempts в 0") Boolean resetAttempts) {
        boolean reset = resetAttempts != null && resetAttempts;
        boolean ok = messagingOutboxService.replay(id, reset);
        if (!ok) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(new OutboxReplayResponse(id, reset, "PENDING"));
    }

    @Serdeable
    @Schema(name = "MessagingOutboxListResponse", description = "Ответ со списком записей messaging outbox (без payload)")
    record MessagingOutboxListResponse(
            @Schema(description = "Элементы списка") List<MessagingOutboxService.OutboxListItem> items
    ) {
    }

    @Serdeable
    @Schema(name = "OutboxReplayResponse", description = "Результат постановки записи outbox на replay")
    record OutboxReplayResponse(
            @Schema(description = "ID записи outbox") long id,
            @Schema(description = "Сброшены ли attempts") boolean resetAttempts,
            @Schema(description = "Статус после операции") String status
    ) {
    }
}

/**
 * Admin API: REST outbox и replay.
 */
@Secured("IB_ADMIN")
@Controller("/admin/outbox/rest")
@Tag(name = "Integration Broker — Admin API (REST Outbox)", description = "Просмотр записей REST outbox и ручной replay")
class AdminRestOutboxController {

    private final RestOutboxService restOutboxService;

    @Inject
    AdminRestOutboxController(RestOutboxService restOutboxService) {
        this.restOutboxService = restOutboxService;
    }

    @Get(uri = "/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Получить запись REST outbox",
            description = "Возвращает полную запись, включая body_json и headers_json (заголовки уже санитизированы)."
    )
    @ApiResponse(responseCode = "200", description = "Запись найдена", content = @Content(schema = @Schema(implementation = RestOutboxService.RestRecord.class)))
    @ApiResponse(responseCode = "404", description = "Запись не найдена")
    public HttpResponse<RestOutboxService.RestRecord> get(@Parameter(description = "ID записи REST outbox") @PathVariable("id") long id) {
        RestOutboxService.RestRecord rec = restOutboxService.get(id);
        if (rec == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(rec);
    }

    @Get
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Список записей REST outbox",
            description = "Список возвращается без body_json для уменьшения риска утечек и нагрузки. Фильтр по статусу опционален."
    )
    @ApiResponse(responseCode = "200", description = "Список", content = @Content(schema = @Schema(implementation = RestOutboxListResponse.class)))
    public RestOutboxListResponse list(
            @Parameter(description = "Фильтр статуса (PENDING/SENDING/SENT/DEAD)") String status,
            @Parameter(description = "Лимит (1..200)") Integer limit) {
        int lim = (limit == null) ? 50 : limit;
        return new RestOutboxListResponse(restOutboxService.list(status, lim));
    }

    @Post(uri = "/{id}/replay")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Поставить запись REST outbox на повторную отправку",
            description = "Переводит запись в PENDING и назначает ближайшую попытку. При resetAttempts=true сбрасывает счётчик attempts."
    )
    @ApiResponse(responseCode = "200", description = "Replay поставлен в очередь", content = @Content(schema = @Schema(implementation = AdminMessagingOutboxController.OutboxReplayResponse.class)))
    @ApiResponse(responseCode = "404", description = "Запись не найдена")
    public HttpResponse<AdminMessagingOutboxController.OutboxReplayResponse> replay(
            @Parameter(description = "ID записи REST outbox") @PathVariable("id") long id,
            @Parameter(description = "Сбросить attempts в 0") Boolean resetAttempts) {
        boolean reset = resetAttempts != null && resetAttempts;
        boolean ok = restOutboxService.replay(id, reset);
        if (!ok) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(new AdminMessagingOutboxController.OutboxReplayResponse(id, reset, "PENDING"));
    }

    @Serdeable
    @Schema(name = "RestOutboxListResponse", description = "Ответ со списком записей REST outbox (без body)")
    record RestOutboxListResponse(
            @Schema(description = "Элементы списка") List<RestOutboxService.RestListItem> items
    ) {
    }
}
