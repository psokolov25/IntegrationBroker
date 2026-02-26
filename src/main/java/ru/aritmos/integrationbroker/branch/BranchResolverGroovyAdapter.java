package ru.aritmos.integrationbroker.branch;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.FlowEngine;
import ru.aritmos.integrationbroker.model.InboundEnvelope;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Инструмент определения отделения (branchId) для входящих событий.
 *
 * <p>Назначение:
 * <ul>
 *   <li>помочь понять, к какому отделению относится событие идентификации клиента;</li>
 *   <li>дать Groovy-flow удобную функцию {@code branch.resolve(input)};</li>
 *   <li>не внедрять правила вызова/сегментации: это зона ответственности VisitManager.</li>
 * </ul>
 *
 * <p>Типовые источники branch-контекста:
 * <ul>
 *   <li>{@link InboundEnvelope#branchId()} — если inbound уже знает отделение;</li>
 *   <li>заголовок {@code x-branch-id} или другой, заданный конфигом;</li>
 *   <li>префикс отделения ({@code x-branch-prefix}) с сопоставлением на branchId;</li>
 *   <li>имя камеры VisionLabs ({@code sourceMeta.cameraName}) с regex-извлечением.</li>
 * </ul>
 */
@Singleton
@FlowEngine.GroovyExecutable("branch")
public class BranchResolverGroovyAdapter {

    private final RuntimeConfigStore configStore;

    public BranchResolverGroovyAdapter(RuntimeConfigStore configStore) {
        this.configStore = configStore;
    }

    /**
     * Определить branchId для входящего сообщения.
     *
     * <p>Возвращает карту, чтобы удобно использовать из Groovy:
     * <pre>
     * {@code
     * def br = branch.resolve(input)
     * if (br.branchId == null) {
     *   throw new RuntimeException('Не удалось определить отделение')
     * }
     * }
     * </pre>
     *
     * @param input входящий envelope
     * @return карта с полями: branchId, strategy, details
     */
    public Map<String, Object> resolve(InboundEnvelope input) {
        RuntimeConfigStore.RuntimeConfig eff = configStore.getEffective();
        RuntimeConfigStore.BranchResolutionConfig cfg = (eff == null) ? null : eff.branchResolution();
        if (cfg == null || !cfg.enabled()) {
            return Map.of(
                    "branchId", safeTrim(input == null ? null : input.branchId()),
                    "strategy", "DISABLED",
                    "details", "Резолвинг отключён конфигурацией"
            );
        }

        String direct = safeTrim(input == null ? null : input.branchId());
        if (direct != null) {
            return Map.of("branchId", direct, "strategy", "ENVELOPE", "details", "branchId задан во входящем envelope");
        }

        String byHeader = safeTrim(input == null ? null : input.header(cfg.branchIdHeaderName()));
        if (byHeader != null) {
            return Map.of("branchId", byHeader, "strategy", "HEADER_BRANCH_ID", "details", "branchId извлечён из заголовка " + cfg.branchIdHeaderName());
        }

        String prefix = safeTrim(input == null ? null : input.header(cfg.branchPrefixHeaderName()));
        if (prefix != null) {
            String mapped = safeTrim(cfg.prefixToBranchId() == null ? null : cfg.prefixToBranchId().get(prefix));
            if (mapped != null) {
                return Map.of("branchId", mapped, "strategy", "HEADER_PREFIX", "details", "branchId сопоставлен по префиксу " + prefix);
            }
            return Map.of("branchId", null, "strategy", "HEADER_PREFIX_NOT_MAPPED", "details", "Префикс задан, но отсутствует в prefixToBranchId: " + prefix);
        }

        String cameraName = readCameraName(input);
        if (cameraName != null && cfg.cameraNameRules() != null) {
            for (RuntimeConfigStore.CameraNameRule r : cfg.cameraNameRules()) {
                if (r == null || r.regex() == null || r.regex().isBlank()) {
                    continue;
                }
                Pattern p = Pattern.compile(r.regex());
                Matcher m = p.matcher(cameraName);
                if (!m.matches()) {
                    continue;
                }
                int g = Math.max(0, r.group());
                String extracted;
                try {
                    extracted = safeTrim(m.group(g));
                } catch (Exception ex) {
                    extracted = null;
                }
                if (extracted == null) {
                    continue;
                }

                if (r.mode() == RuntimeConfigStore.CameraNameRuleMode.BRANCH_ID) {
                    return Map.of("branchId", extracted, "strategy", "CAMERA_NAME_BRANCH_ID", "details", "branchId извлечён из cameraName по правилу: " + safeTrim(r.name()));
                }
                if (r.mode() == RuntimeConfigStore.CameraNameRuleMode.BRANCH_PREFIX) {
                    String mapped = safeTrim(cfg.prefixToBranchId() == null ? null : cfg.prefixToBranchId().get(extracted));
                    if (mapped != null) {
                        return Map.of("branchId", mapped, "strategy", "CAMERA_NAME_PREFIX", "details", "branchId сопоставлен по cameraName-префиксу " + extracted + " (" + safeTrim(r.name()) + ")");
                    }
                    return Map.of("branchId", null, "strategy", "CAMERA_NAME_PREFIX_NOT_MAPPED", "details", "Префикс из cameraName не найден в prefixToBranchId: " + extracted);
                }
            }
        }

        Map<String, Object> details = new HashMap<>();
        details.put("cameraName", cameraName);
        details.put("branchIdHeaderName", cfg.branchIdHeaderName());
        details.put("branchPrefixHeaderName", cfg.branchPrefixHeaderName());
        details.put("rules", cfg.cameraNameRules() == null ? 0 : cfg.cameraNameRules().size());

        return Map.of(
                "branchId", null,
                "strategy", "UNRESOLVED",
                "details", details
        );
    }

    private static String readCameraName(InboundEnvelope input) {
        if (input == null || input.sourceMeta() == null) {
            return null;
        }
        Object v = input.sourceMeta().get("cameraName");
        if (v == null) {
            v = input.sourceMeta().get("camera_name");
        }
        return safeTrim(v == null ? null : String.valueOf(v));
    }

    private static String safeTrim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
