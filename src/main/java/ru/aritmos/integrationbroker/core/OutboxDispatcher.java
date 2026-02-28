package ru.aritmos.integrationbroker.core;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Диспетчер outbox.
 * <p>
 * Периодически выбирает записи со статусом {@code PENDING} и сроком {@code next_attempt_at <= now},
 * пытается отправить и переводит запись в:
 * <ul>
 *   <li>{@code SENT} — при успехе;</li>
 *   <li>{@code PENDING} + планирование следующей попытки — при временной ошибке;</li>
 *   <li>{@code DEAD} — при исчерпании попыток.</li>
 * </ul>
 * <p>
 * Важно: конкурентная обработка защищена переводом {@code PENDING -> SENDING} через атомарный UPDATE.
 */
@Singleton
@Requires(property = "integrationbroker.dispatcher.enabled", notEquals = "false")
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final RuntimeConfigStore configStore;
    private final MessagingOutboxService messagingOutboxService;
    private final RestOutboxService restOutboxService;
    private final MessagingProviderRegistry providerRegistry;
    @Value("${integrationbroker.outbound.dry-run:false}")
    protected boolean outboundDryRun;

    public OutboxDispatcher(RuntimeConfigStore configStore,
                            MessagingOutboxService messagingOutboxService,
                            RestOutboxService restOutboxService,
                            MessagingProviderRegistry providerRegistry) {
        this.configStore = configStore;
        this.messagingOutboxService = messagingOutboxService;
        this.restOutboxService = restOutboxService;
        this.providerRegistry = providerRegistry;
    }

    /**
     * Диспетчеризация messaging outbox.
     */
    @Scheduled(fixedDelay = "${integrationbroker.dispatcher.fixed-delay:2s}")
    public void dispatchMessaging() {
        if (outboundDryRun) {
            return;
        }
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        RuntimeConfigStore.MessagingOutboxConfig oc = cfg.messagingOutbox();
        if (oc == null || !oc.enabled()) {
            return;
        }

        int batch = Math.max(1, oc.batchSize());
        List<MessagingOutboxService.OutboxRecord> due = messagingOutboxService.pickDue(batch);
        for (MessagingOutboxService.OutboxRecord r : due) {
            if (!messagingOutboxService.markSending(r.id())) {
                continue;
            }

            try {
                Map<String, String> headers = messagingOutboxService.parseHeaders(r.headersJson());
                MessagingProvider provider = providerRegistry.get(r.provider());
                MessagingProvider.SendResult sr = provider.send(
                        new MessagingProvider.OutboundMessage(
                                r.destination(),
                                r.messageKey(),
                                headers,
                                r.payloadJson(),
                                r.correlationId(),
                                r.sourceMessageId(),
                                r.idempotencyKey()
                        )
                );

                if (sr.success()) {
                    messagingOutboxService.markSent(r.id());
                    continue;
                }

                onMessagingFailure(r, oc, sr.errorCode(), sr.errorMessage());
            } catch (Exception e) {
                onMessagingFailure(r, oc, "DISPATCH_ERROR", e.getMessage());
            }
        }
    }

    private void onMessagingFailure(MessagingOutboxService.OutboxRecord r,
                                    RuntimeConfigStore.MessagingOutboxConfig oc,
                                    String errorCode,
                                    String errorMessage) {
        int nextAttempts = r.attempts() + 1;
        int maxAttempts = Math.max(1, oc.maxAttempts());
        boolean dead = nextAttempts >= maxAttempts;
        Instant next = computeNextAttempt(oc.baseDelaySec(), oc.maxDelaySec(), nextAttempts);

        messagingOutboxService.markFailed(r.id(), nextAttempts, maxAttempts, next, errorCode, errorMessage, dead);

        if (dead) {
            log.warn("[OUTBOX][MSG] запись переведена в DEAD id={} provider={} destination={} attempts={}/{} errorCode={}",
                    r.id(), r.provider(), r.destination(), nextAttempts, maxAttempts, SensitiveDataSanitizer.sanitizeText(errorCode));
        }
    }

    /**
     * Диспетчеризация REST outbox.
     */
    @Scheduled(fixedDelay = "${integrationbroker.dispatcher.fixed-delay:2s}")
    public void dispatchRest() {
        if (outboundDryRun) {
            return;
        }
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        RuntimeConfigStore.RestOutboxConfig oc = cfg.restOutbox();
        if (oc == null || !oc.enabled()) {
            return;
        }

        int batch = Math.max(1, oc.batchSize());
        List<RestOutboxService.RestRecord> due = restOutboxService.pickDue(batch);
        for (RestOutboxService.RestRecord r : due) {
            if (!restOutboxService.markSending(r.id())) {
                continue;
            }

            try {
                RestOutboundSender.Result rr = restOutboxService.sendOnce(r, oc.idempotencyHeaderName(), cfg);
                if (rr.success()) {
                    restOutboxService.markSent(r.id(), rr.httpStatus());
                    continue;
                }
                onRestFailure(r, oc, rr.errorCode(), rr.errorMessage(), rr.httpStatus());
            } catch (Exception e) {
                onRestFailure(r, oc, "DISPATCH_ERROR", e.getMessage(), -1);
            }
        }
    }

    private void onRestFailure(RestOutboxService.RestRecord r,
                               RuntimeConfigStore.RestOutboxConfig oc,
                               String errorCode,
                               String errorMessage,
                               int httpStatus) {
        int nextAttempts = r.attempts() + 1;
        int maxAttempts = Math.max(1, oc.maxAttempts());
        boolean dead = nextAttempts >= maxAttempts;
        Instant next = computeNextAttempt(oc.baseDelaySec(), oc.maxDelaySec(), nextAttempts);

        restOutboxService.markFailed(r.id(), nextAttempts, maxAttempts, next, errorCode, errorMessage, httpStatus, dead);

        if (dead) {
            log.warn("[OUTBOX][REST] запись переведена в DEAD id={} method={} url={} attempts={}/{} httpStatus={} errorCode={}",
                    r.id(), r.httpMethod(), r.url(), nextAttempts, maxAttempts, httpStatus, SensitiveDataSanitizer.sanitizeText(errorCode));
        }
    }

    private static Instant computeNextAttempt(int baseDelaySec, int maxDelaySec, int attempts) {
        int base = Math.max(1, baseDelaySec);
        int max = Math.max(base, maxDelaySec);

        // Экспоненциальный рост: base * 2^(attempts-1)
        long delay = (long) base * (1L << Math.min(20, Math.max(0, attempts - 1)));
        if (delay > max) {
            delay = max;
        }
        return Instant.now().plus(delay, ChronoUnit.SECONDS);
    }
}
