package ru.aritmos.integrationbroker.visionlabs;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import ru.aritmos.integrationbroker.api.InboundController;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.InboundProcessingService;
import ru.aritmos.integrationbroker.core.SensitiveDataSanitizer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP callback endpoint для результатов аналитики VisionLabs (LUNA PLATFORM).
 * <p>
 * Соответствует callback type: <b>http</b> (агент отправляет результат обработки на заранее настроенный сервер).
 * <p>
 * Важно:
 * <ul>
 *   <li>заголовки санитизируются (Authorization/Cookie и т.п. → "***");</li>
 *   <li>поддерживается опциональная проверка shared-secret (header);</li>
 *   <li>payload не логируется.</li>
 * </ul>
 */
@Controller("/api/visionlabs")
@Tag(name = "VisionLabs — источники аналитики (HTTP)", description = "Приём результатов аналитики VisionLabs через HTTP callback")
public class VisionLabsHttpCallbackController {

    private final RuntimeConfigStore configStore;
    private final VisionLabsAnalyticsIngressService ingressService;

    @Inject
    public VisionLabsHttpCallbackController(RuntimeConfigStore configStore,
                                            VisionLabsAnalyticsIngressService ingressService) {
        this.configStore = configStore;
        this.ingressService = ingressService;
    }

    @Post(uri = "/callback", consumes = MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Принять результат аналитики VisionLabs (HTTP callback)",
            description = "Endpoint для callback type=http. Тело запроса должно быть JSON. " +
                    "Сообщение нормализуется в InboundEnvelope и обрабатывается обычным pipeline (idempotency/enrichment/flow/DLQ/outbox). " +
                    "Секреты и токены не сохраняются и не логируются."
    )
    @ApiResponse(responseCode = "200", description = "Сообщение обработано или пропущено как уже обработанное", content = @Content(schema = @Schema(implementation = InboundController.InboundResult.class)))
    @ApiResponse(responseCode = "202", description = "Сообщение уже обрабатывается (LOCKED) — не poison", content = @Content(schema = @Schema(implementation = InboundController.InboundResult.class)))
    @ApiResponse(responseCode = "401", description = "Не пройдена проверка shared-secret")
    @ApiResponse(responseCode = "400", description = "Некорректный JSON или отключён приём аналитики")
    @ApiResponse(responseCode = "500", description = "Ошибка выполнения flow, при включённом DLQ сообщение сохранено")
    public HttpResponse<InboundController.InboundResult> callback(@Body String body, HttpRequest<?> request) {
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        RuntimeConfigStore.VisionLabsAnalyticsConfig vcfg = cfg.visionLabsAnalytics();
        if (vcfg == null || !vcfg.enabled() || vcfg.http() == null || !vcfg.http().enabled()) {
            return HttpResponse.badRequest(new InboundController.InboundResult(
                    "REJECTED",
                    null,
                    Map.of("note", "Приём VisionLabs аналитики отключён"),
                    null,
                    "DISABLED",
                    "visionLabsAnalytics.enabled=false"
            ));
        }

        if (!checkSharedSecret(vcfg.http(), request)) {
            return HttpResponse.status(HttpStatus.UNAUTHORIZED).body(new InboundController.InboundResult(
                    "REJECTED",
                    null,
                    Map.of("note", "Не пройдена проверка shared-secret"),
                    null,
                    "UNAUTHORIZED",
                    "Некорректный или отсутствующий shared-secret"
            ));
        }

        Map<String, String> headers = toSanitizedHeaders(request.getHeaders());
        Map<String, Object> sourceMeta = new LinkedHashMap<>();
        java.net.InetSocketAddress addr = request.getRemoteAddress();
        if (addr != null) {
            sourceMeta.put("remote", addr.getHostString() + ":" + addr.getPort());
        }

        try {
            InboundProcessingService.ProcessingResult res = ingressService.ingestJson(
                    "http",
                    body,
                    headers,
                    sourceMeta
            );

            InboundController.InboundResult out = new InboundController.InboundResult(res.outcome(), res.idempotencyKey(), res.output(), null, null, null);
            if ("LOCKED".equals(res.outcome())) {
                return HttpResponse.status(HttpStatus.ACCEPTED).body(out);
            }
            return HttpResponse.ok(out);
        } catch (InboundProcessingService.StoredInDlqException ex) {
            InboundController.InboundResult out = new InboundController.InboundResult(
                    "DLQ",
                    ex.idempotencyKey(),
                    Map.of("note", "Сообщение сохранено в inbound DLQ для последующего replay"),
                    ex.dlqId(),
                    ex.errorCode(),
                    ex.safeMessage()
            );
            return HttpResponse.serverError(out);
        } catch (IllegalArgumentException ex) {
            String msg = SensitiveDataSanitizer.sanitizeText(ex.getMessage());
            return HttpResponse.badRequest(new InboundController.InboundResult(
                    "REJECTED",
                    null,
                    Map.of("note", "Запрос отклонён", "reason", msg),
                    null,
                    "BAD_REQUEST",
                    msg
            ));
        } catch (Exception ex) {
            String msg = SensitiveDataSanitizer.sanitizeText(ex.getMessage());
            return HttpResponse.serverError(new InboundController.InboundResult(
                    "FAILED",
                    null,
                    Map.of("note", "Обработка завершилась ошибкой"),
                    null,
                    "INTERNAL_ERROR",
                    msg
            ));
        }
    }

    private boolean checkSharedSecret(RuntimeConfigStore.VisionLabsHttpCallbackConfig http, HttpRequest<?> req) {
        if (http == null) {
            return true;
        }
        String headerName = http.sharedSecretHeaderName();
        String secret = http.sharedSecret();
        if (headerName == null || headerName.isBlank() || secret == null || secret.isBlank()) {
            return true;
        }
        String provided = req.getHeaders().get(headerName);
        return secret.equals(provided);
    }

    private Map<String, String> toSanitizedHeaders(HttpHeaders headers) {
        Map<String, String> raw = new LinkedHashMap<>();
        if (headers != null) {
            for (String name : headers.names()) {
                String v = headers.get(name);
                if (v != null) {
                    raw.put(name, v);
                }
            }
        }
        return SensitiveDataSanitizer.sanitizeHeaders(raw);
    }

    /**
     * Технический ответ для быстрых интеграционных проверок.
     */
    @Serdeable
    @Schema(name = "VisionLabsCallbackAck", description = "Техническое подтверждение приёма callback")
    public record CallbackAck(
            @Schema(description = "Статус")
            String status
    ) {
    }
}
