package ru.aritmos.integrationbroker.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.model.InboundEnvelope;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Основной сервис обработки inbound-сообщений.
 * <p>
 * На текущей итерации реализованы:
 * <ul>
 *   <li>получение effective-конфига;</li>
 *   <li>разрешение flow;</li>
 *   <li>идемпотентность (PROCESS/SKIP_COMPLETED/LOCKED);</li>
 *   <li>enrichment пользователя через KeycloakProxy (опционально);</li>
 *   <li>выполнение Groovy flow.</li>
 * </ul>
 * <p>
 * Следующими итерациями будут добавлены: enrichment, DLQ, outbox, адаптеры, сегментация и предметные сценарии.
 */
@Singleton
public class InboundProcessingService {

    private final RuntimeConfigStore configStore;
    private final FlowEngine.FlowResolver flowResolver;
    private final FlowEngine.GroovyFlowEngine groovyFlowEngine;
    private final IdempotencyService idempotencyService;
    private final InboundDlqService inboundDlqService;
    private final KeycloakProxyEnrichmentService keycloakProxyEnrichmentService;
    private final ObjectMapper objectMapper;

    public InboundProcessingService(RuntimeConfigStore configStore,
                                   FlowEngine.ConfigBasedFlowResolver flowResolver,
                                   FlowEngine.GroovyFlowEngine groovyFlowEngine,
                                   IdempotencyService idempotencyService,
                                   InboundDlqService inboundDlqService,
                                   KeycloakProxyEnrichmentService keycloakProxyEnrichmentService,
                                   ObjectMapper objectMapper) {
        this.configStore = configStore;
        this.flowResolver = flowResolver;
        this.groovyFlowEngine = groovyFlowEngine;
        this.idempotencyService = idempotencyService;
        this.inboundDlqService = inboundDlqService;
        this.keycloakProxyEnrichmentService = keycloakProxyEnrichmentService;
        this.objectMapper = objectMapper;
    }

    /**
     * Результат обработки.
     */
    public record ProcessingResult(
            String outcome,
            String idempotencyKey,
            Map<String, Object> output
    ) {
    }

    /**
     * Исключение, сигнализирующее о том, что сообщение сохранено в inbound DLQ.
     * <p>
     * Используется REST ingress слоем для формирования корректного ответа клиенту.
     * Важно: само исключение не должно содержать сырых секретов/токенов.
     */
    public static final class StoredInDlqException extends RuntimeException {

        private final long dlqId;
        private final String idempotencyKey;
        private final String errorCode;
        private final String safeMessage;

        public StoredInDlqException(long dlqId, String idempotencyKey, String errorCode, String safeMessage) {
            super("Сообщение помещено в DLQ");
            this.dlqId = dlqId;
            this.idempotencyKey = idempotencyKey;
            this.errorCode = errorCode;
            this.safeMessage = safeMessage;
        }

        public long dlqId() {
            return dlqId;
        }

        public String idempotencyKey() {
            return idempotencyKey;
        }

        public String errorCode() {
            return errorCode;
        }

        public String safeMessage() {
            return safeMessage;
        }
    }

    /**
     * Обработать inbound-сообщение.
     *
     * @param envelope входящий конверт
     * @return результат обработки
     */
    public ProcessingResult process(InboundEnvelope envelope) {
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();

        Optional<RuntimeConfigStore.FlowConfig> flowOpt = flowResolver.resolve(envelope, cfg);
        if (flowOpt.isEmpty()) {
            throw new IllegalArgumentException("Не найден flow для kind=" + envelope.kind() + ", type=" + envelope.type());
        }

        RuntimeConfigStore.FlowConfig flow = flowOpt.get();

        // Идемпотентность применяется на входе, до исполнения flow.
        IdempotencyService.IdempotencyDecision decision = idempotencyService.decide(envelope, cfg.idempotency());

        if (decision.decision() == IdempotencyService.Decision.SKIP_COMPLETED) {
            Map<String, Object> out = new HashMap<>();
            if (decision.existingResultJson() != null) {
                Object restored = tryParseJsonToObject(decision.existingResultJson());
                if (restored instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        if (e.getKey() != null) {
                            out.put(String.valueOf(e.getKey()), e.getValue());
                        }
                    }
                } else {
                    out.put("resultJson", decision.existingResultJson());
                }
            }
            out.put("note", "Сообщение уже обработано ранее (SKIP_COMPLETED)");
            return new ProcessingResult("SKIP_COMPLETED", decision.idemKey(), out);
        }

        if (decision.decision() == IdempotencyService.Decision.LOCKED) {
            return new ProcessingResult("LOCKED", decision.idemKey(), Map.of("note", "Сообщение уже обрабатывается (LOCKED не является poison message)"));
        }

        Map<String, Object> meta = buildMeta(envelope, cfg, decision.idemKey());

        // Enrichment пользователя/контекста через KeycloakProxy.
        // Важно: нельзя логировать и сохранять сырой токен. В кэше используется только хэш токена.
        InboundEnvelope enriched = keycloakProxyEnrichmentService.enrichIfEnabled(envelope, cfg, meta);

        try {
            Map<String, Object> output = groovyFlowEngine.execute(enriched, flow, meta);
            idempotencyService.markCompleted(decision.idemKey(), output);
            return new ProcessingResult("PROCESSED", decision.idemKey(), output);
        } catch (Exception e) {
            String code = "FLOW_EXECUTION_ERROR";
            String safeMessage = SensitiveDataSanitizer.sanitizeText(e.getMessage());
            idempotencyService.markFailed(decision.idemKey(), code, safeMessage);

            // Важно: при replay из DLQ не создаём новую DLQ-запись.
            if (cfg.inboundDlq() != null && cfg.inboundDlq().enabled() && !isDlqReplay(envelope)) {
                long dlqId = inboundDlqService.put(
                        envelope,
                        decision.idemKey(),
                        code,
                        safeMessage,
                        cfg.inboundDlq().maxAttempts(),
                        cfg.inboundDlq().sanitizeHeaders()
                );
                if (dlqId > 0) {
                    throw new StoredInDlqException(dlqId, decision.idemKey(), code, safeMessage);
                }
            }
            throw e;
        }
    }

    private boolean isDlqReplay(InboundEnvelope envelope) {
        if (envelope == null || envelope.sourceMeta() == null) {
            return false;
        }
        Object v = envelope.sourceMeta().get("dlqReplayId");
        return v != null;
    }

    private Map<String, Object> buildMeta(InboundEnvelope envelope, RuntimeConfigStore.RuntimeConfig cfg, String idempotencyKey) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("revision", cfg.revision());
        meta.put("kind", String.valueOf(envelope.kind()));
        meta.put("type", envelope.type());
        meta.put("messageId", envelope.messageId());
        meta.put("correlationId", envelope.correlationId());
        meta.put("branchId", envelope.branchId());
        meta.put("userId", envelope.userId());
        if (idempotencyKey != null) {
            meta.put("idempotencyKey", idempotencyKey);
        }
        return meta;
    }

    private Object tryParseJsonToObject(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return json;
        }
    }
}
