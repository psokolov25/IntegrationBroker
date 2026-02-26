package ru.aritmos.integrationbroker.visitmanager;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.core.FlowEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groovy-адаптер для взаимодействия с VisitManager.
 *
 * <p>Экспортируется в Groovy как переменная {@code visit}:
 * <pre>
 * {@code
 * def catalog = visit.servicesCatalog(branchId)
 * def res = visit.createVisitRest([
 *   branchId: branchId,
 *   entryPointId: '1',
 *   serviceIds: ['...'],
 *   parameters: [segment:'VIP'],
 *   printTicket: false
 * ], meta)
 * }
 * </pre>
 *
 * <p>Важно: IB не реализует сегментацию/правила вызова. Он лишь:
 * <ul>
 *   <li>передаёт в VisitManager параметры визита (segment и др.),</li>
 *   <li>и/или публикует событие VISIT_CREATE через DataBus (через alias {@code bus}).</li>
 * </ul>
 */
@Singleton
@FlowEngine.GroovyExecutable("visit")
public class VisitManagerGroovyAdapter {

    private final VisitManagerClient client;

    public VisitManagerGroovyAdapter(VisitManagerClient client) {
        this.client = client;
    }

    /**
     * Получить каталог услуг отделения.
     *
     * @param branchId идентификатор отделения
     * @return карта с полями: success/httpStatus/body/errorCode/errorMessage
     */
    public Map<String, Object> servicesCatalog(String branchId) {
        VisitManagerClient.CallResult r = client.getServicesCatalog(branchId);
        return toMap(r);
    }

    /**
     * Создать визит через REST API VisitManager.
     *
     * <p>Аргументы передаются как Map (удобно для Groovy):
     * <ul>
     *   <li>branchId (обязательно)</li>
     *   <li>entryPointId (опционально)</li>
     *   <li>serviceIds (обязательно, List)</li>
     *   <li>parameters (опционально, Map)</li>
     *   <li>printTicket (опционально, boolean)</li>
     *   <li>segmentationRuleId (опционально)</li>
     *   <li>headers (опционально, Map) — без секретов</li>
     * </ul>
     *
     * @param args параметры
     * @param meta служебные метаданные ядра (messageId/correlationId/idempotencyKey)
     * @return карта-результат (direct/outbox/error)
     */
    public Map<String, Object> createVisitRest(Object args, Map<String, Object> meta) {
        Map<String, Object> m = (args instanceof Map<?, ?> mm) ? (Map<String, Object>) mm : Map.of();

        String branchId = safeStr(m.get("branchId"));
        String entryPointId = safeStr(m.get("entryPointId"));
        boolean printTicket = Boolean.TRUE.equals(m.get("printTicket"));
        String segmentationRuleId = safeStr(m.get("segmentationRuleId"));

        List<String> serviceIds = toStringList(m.get("serviceIds"));
        Map<String, String> params = toStringMap(m.get("parameters"));
        Map<String, String> headers = toStringMap(m.get("headers"));

        String sourceMessageId = meta == null ? null : safeStr(meta.get("messageId"));
        String correlationId = meta == null ? null : safeStr(meta.get("correlationId"));
        String idempotencyKey = meta == null ? null : safeStr(meta.get("idempotencyKey"));

        VisitManagerClient.CallResult r = client.createVisitWithParametersRest(
                branchId,
                entryPointId,
                serviceIds,
                params,
                printTicket,
                segmentationRuleId,
                headers,
                sourceMessageId,
                correlationId,
                idempotencyKey
        );

        return toMap(r);
    }

    /**
     * Вспомогательная функция: сопоставить внешние названия процедур со списком serviceIds VisitManager.
     *
     * <p>Типовой сценарий: медицинская система отдаёт список процедур по имени, а VisitManager работает
     * с serviceIds. В flow можно:
     * <ol>
     *   <li>получить каталог услуг {@link #servicesCatalog(String)};</li>
     *   <li>выполнить сопоставление по нормализованному имени.</li>
     * </ol>
     *
     * @param branchId отделение
     * @param names список внешних имён процедур/услуг
     * @param allowContains если true — допускается match по contains (помогает при различиях формулировок)
     * @return список serviceIds, найденных по каталогу
     */
    public List<String> matchServiceIdsByNames(String branchId, List<String> names, boolean allowContains) {
        VisitManagerClient.CallResult r = client.getServicesCatalog(branchId);
        if (!"DIRECT".equals(r.mode()) || r.response() == null || !r.response().isArray()) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        if (names != null) {
            for (String n : names) {
                String t = normalizeName(n);
                if (t != null) {
                    normalized.add(t);
                }
            }
        }

        List<String> out = new ArrayList<>();
        for (var node : r.response()) {
            String id = node.hasNonNull("id") ? node.get("id").asText() : null;
            String nm = node.hasNonNull("name") ? node.get("name").asText() : null;
            String n2 = normalizeName(nm);
            if (id == null || n2 == null) {
                continue;
            }
            for (String target : normalized) {
                if (target.equals(n2) || (allowContains && (n2.contains(target) || target.contains(n2)))) {
                    out.add(id);
                    break;
                }
            }
        }
        return out;
    }

    private static Map<String, Object> toMap(VisitManagerClient.CallResult r) {
        if (r == null) {
            return Map.of("success", false, "errorCode", "NULL", "errorMessage", "Пустой результат");
        }
        boolean success = "DIRECT".equals(r.mode()) && r.httpStatus() >= 200 && r.httpStatus() < 300;
        Map<String, Object> out = new HashMap<>();
        out.put("success", success);
        out.put("mode", r.mode());
        out.put("httpStatus", r.httpStatus());
        out.put("outboxId", r.outboxId());
        out.put("errorCode", r.errorCode());
        out.put("errorMessage", r.errorMessage());
        out.put("body", r.response());
        return out;
    }

    private static String safeStr(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static List<String> toStringList(Object v) {
        if (v == null) {
            return List.of();
        }
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                String s = safeStr(o);
                if (s != null) {
                    out.add(s);
                }
            }
            return out;
        }
        return List.of(safeStr(v));
    }

    private static Map<String, String> toStringMap(Object v) {
        if (v == null) {
            return Map.of();
        }
        if (v instanceof Map<?, ?> map) {
            Map<String, String> out = new HashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                String k = String.valueOf(e.getKey());
                String val = String.valueOf(e.getValue());
                out.put(k, val);
            }
            return out;
        }
        return Map.of();
    }

    private static String normalizeName(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim().toLowerCase();
        if (t.isEmpty()) {
            return null;
        }
        // грубая нормализация: убираем двойные пробелы
        t = t.replaceAll("\\s+", " ");
        return t;
    }
}
