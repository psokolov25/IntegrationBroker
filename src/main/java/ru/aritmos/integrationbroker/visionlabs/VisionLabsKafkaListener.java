package ru.aritmos.integrationbroker.visionlabs;

import io.micronaut.context.annotation.Requires;
import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.Topic;
import jakarta.inject.Singleton;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.SensitiveDataSanitizer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kafka inbound для результатов аналитики VisionLabs (callback type: luna-kafka).
 * <p>
 * В этом режиме агент публикует события в Kafka topic.
 * Integration Broker читает сообщения и нормализует их в InboundEnvelope.
 * <p>
 * Важно:
 * <ul>
 *   <li>этот listener включается только при integrationbroker.visionlabs.analytics.kafka.enabled=true;</li>
 *   <li>payload не логируется;</li>
 *   <li>события проходят через общий pipeline (idempotency/DLQ/outbox).</li>
 * </ul>
 */
@Singleton
@Requires(property = "integrationbroker.visionlabs.analytics.kafka.enabled", value = "true")
@KafkaListener(groupId = "${integrationbroker.visionlabs.analytics.kafka.group-id:integration-broker-visionlabs}")
public class VisionLabsKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(VisionLabsKafkaListener.class);

    private final RuntimeConfigStore configStore;
    private final VisionLabsAnalyticsIngressService ingressService;

    public VisionLabsKafkaListener(RuntimeConfigStore configStore, VisionLabsAnalyticsIngressService ingressService) {
        this.configStore = configStore;
        this.ingressService = ingressService;
    }

    @Topic("${integrationbroker.visionlabs.analytics.kafka.topic:luna.events}")
    void onMessage(ConsumerRecord<String, String> record) {
        if (record == null) {
            return;
        }
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        RuntimeConfigStore.VisionLabsAnalyticsConfig vcfg = cfg.visionLabsAnalytics();
        if (vcfg == null || !vcfg.enabled() || vcfg.kafka() == null || !vcfg.kafka().enabled()) {
            return;
        }
        try {
            Map<String, Object> sourceMeta = new LinkedHashMap<>();
            sourceMeta.put("topic", record.topic());
            sourceMeta.put("partition", record.partition());
            sourceMeta.put("offset", record.offset());
            if (record.key() != null) {
                sourceMeta.put("key", record.key());
            }
            ingressService.ingestJson("luna-kafka", record.value(), Map.of(), sourceMeta);
        } catch (Exception e) {
            log.warn("Не удалось обработать сообщение VisionLabs из Kafka: {}", SensitiveDataSanitizer.sanitizeText(e.getMessage()));
        }
    }
}
