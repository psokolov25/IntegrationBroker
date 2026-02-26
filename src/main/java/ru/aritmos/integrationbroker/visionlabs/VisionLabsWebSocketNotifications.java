package ru.aritmos.integrationbroker.visionlabs;

import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.SensitiveDataSanitizer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * WebSocket endpoint для получения уведомлений от VisionLabs (callback type: luna-ws-notification).
 * <p>
 * Важно: это <b>серверная</b> сторона WebSocket — внешний агент/сервис подключается к IB и отправляет сообщения.
 * <p>
 * Для безопасности поддерживается опциональный shared-secret через query-param (например, token=...).
 * Значение не логируется.
 */
@Singleton
@ServerWebSocket("/ws/visionlabs/notifications")
public class VisionLabsWebSocketNotifications {

    private static final Logger log = LoggerFactory.getLogger(VisionLabsWebSocketNotifications.class);

    private final RuntimeConfigStore configStore;
    private final VisionLabsAnalyticsIngressService ingressService;
    private final ExecutorService executor;

    public VisionLabsWebSocketNotifications(RuntimeConfigStore configStore,
                                            VisionLabsAnalyticsIngressService ingressService,
                                            @Named(TaskExecutors.IO) ExecutorService executor) {
        this.configStore = configStore;
        this.ingressService = ingressService;
        this.executor = executor;
    }

    @OnOpen
    void onOpen(WebSocketSession session) {
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        RuntimeConfigStore.VisionLabsAnalyticsConfig vcfg = cfg.visionLabsAnalytics();
        if (vcfg == null || !vcfg.enabled() || vcfg.ws() == null || !vcfg.ws().enabled()) {
            session.close();
            return;
        }
        if (!checkSharedSecret(vcfg.ws(), session)) {
            session.close();
        }
    }

    @OnMessage
    void onMessage(String message, WebSocketSession session) {
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        RuntimeConfigStore.VisionLabsAnalyticsConfig vcfg = cfg.visionLabsAnalytics();
        if (vcfg == null || !vcfg.enabled() || vcfg.ws() == null || !vcfg.ws().enabled()) {
            return;
        }

        // Обработка выносится в IO executor, чтобы не блокировать websocket event-loop.
        executor.submit(() -> {
            try {
                Map<String, Object> sourceMeta = new LinkedHashMap<>();
                sourceMeta.put("wsSessionId", session.getId());
                ingressService.ingestJson("luna-ws-notification", message, Map.of(), sourceMeta);
            } catch (Exception e) {
                // Важно: не логируем payload.
                log.warn("Не удалось обработать сообщение VisionLabs через WebSocket: {}", SensitiveDataSanitizer.sanitizeText(e.getMessage()));
            }
        });
    }

    private boolean checkSharedSecret(RuntimeConfigStore.VisionLabsWsConfig ws, WebSocketSession session) {
        if (ws == null) {
            return true;
        }
        String param = ws.sharedSecretQueryParam();
        String secret = ws.sharedSecret();
        if (param == null || param.isBlank() || secret == null || secret.isBlank()) {
            return true;
        }

        try {
            Object raw = session.getRequestParameters().get(param);
            if (raw == null) {
                return false;
            }
            if (raw instanceof String s) {
                return secret.equals(s);
            }
            if (raw instanceof java.util.List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                return first != null && secret.equals(String.valueOf(first));
            }
            return secret.equals(String.valueOf(raw));
        } catch (Exception e) {
            // На разных версиях Micronaut тип requestParameters может отличаться, поэтому fallback на «нет доступа».
            return false;
        }
    }
}
