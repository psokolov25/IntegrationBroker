package ru.aritmos.integrationbroker.identity;

import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Нормализация сегмента клиента.
 * <p>
 * В реальных интеграциях сегмент может приходить из разных источников (CRM, МИС, внешняя шина)
 * и иметь разные названия. Данный компонент:
 * <ul>
 *   <li>приводит сегмент к единому виду (aliases -> normalized);</li>
 *   <li>позволяет выбрать «лучший» сегмент по приоритету;</li>
 *   <li>вычисляет числовой вес приоритета, если он не задан источником.</li>
 * </ul>
 */
public class SegmentNormalizer {

    private final RuntimeConfigStore.IdentityConfig cfg;

    public SegmentNormalizer(RuntimeConfigStore.IdentityConfig cfg) {
        this.cfg = cfg == null ? RuntimeConfigStore.IdentityConfig.defaultConfig() : cfg;
    }

    /**
     * Нормализовать сегмент.
     *
     * @param raw исходный сегмент
     * @return нормализованный сегмент или DEFAULT
     */
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "DEFAULT";
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        String aliased = cfg.segmentAliases() == null ? null : cfg.segmentAliases().get(s);
        return (aliased == null || aliased.isBlank()) ? s : aliased.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Выбрать «лучший» сегмент из набора.
     *
     * @param segments сегменты (сырые или нормализованные)
     * @return итоговый сегмент
     */
    public String chooseBest(List<String> segments) {
        if (segments == null || segments.isEmpty()) {
            return "DEFAULT";
        }

        List<String> prio = (cfg.segmentPriority() == null || cfg.segmentPriority().isEmpty())
                ? RuntimeConfigStore.IdentityConfig.defaultConfig().segmentPriority()
                : cfg.segmentPriority();

        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < prio.size(); i++) {
            rank.put(prio.get(i).toUpperCase(Locale.ROOT), i);
        }

        String best = null;
        int bestRank = Integer.MAX_VALUE;
        for (String s : segments) {
            String n = normalize(s);
            int r = rank.getOrDefault(n, Integer.MAX_VALUE - 1);
            if (best == null || r < bestRank) {
                best = n;
                bestRank = r;
            }
        }
        return best == null ? "DEFAULT" : best;
    }

    /**
     * Получить вес приоритета для сегмента.
     *
     * @param segment нормализованный сегмент
     * @return вес приоритета
     */
    public int weightForSegment(String segment) {
        String s = normalize(segment);
        return switch (s) {
            case "VIP" -> 100;
            case "PREMIUM" -> 80;
            case "CORPORATE" -> 70;
            case "FAST_TRACK" -> 65;
            case "LOW_MOBILITY" -> 60;
            case "PREBOOKED_MEDICAL" -> 60;
            case "MULTI_STAGE_EXAM" -> 55;
            default -> 10;
        };
    }
}
