package ru.aritmos.integrationbroker.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Сервис идентификации клиента.
 * <p>
 * Реализует цепочку/реестр провайдеров ({@link IdentityProvider}) и агрегацию результата.
 * <p>
 * Основные свойства:
 * <ul>
 *   <li>расширяемая модель идентификаторов: {@code type + value + attributes};</li>
 *   <li>комбинация нескольких способов в одном запросе;</li>
 *   <li>приоритет и fallback между способами;</li>
 *   <li>агрегация профиля из нескольких источников;</li>
 *   <li>нормализация сегмента и вычисление priorityWeight.</li>
 * </ul>
 */
@Singleton
public class IdentityService {

    private final RuntimeConfigStore configStore;
    private final IdentityProviderRegistry registry;
    private final ObjectMapper objectMapper;

    public IdentityService(RuntimeConfigStore configStore,
                           IdentityProviderRegistry registry,
                           ObjectMapper objectMapper) {
        this.configStore = configStore;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /**
     * Выполнить идентификацию.
     *
     * @param request запрос
     * @param meta метаданные ядра (branchId/userId/channel и т.д.)
     * @return результат идентификации
     */
    public IdentityModels.IdentityResolution resolve(IdentityModels.IdentityRequest request, Map<String, Object> meta) {
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        if (cfg.identity() == null || !cfg.identity().enabled()) {
            throw new IllegalStateException("Слой identity отключён настройкой runtime-config.identity.enabled=false");
        }

        IdentityModels.IdentityRequest req = normalizeRequest(request);
        SegmentNormalizer segmentNormalizer = new SegmentNormalizer(cfg.identity());

        List<IdentityModels.IdentityEvidence> evidences = new ArrayList<>();
        IdentityModels.IdentityProfile aggregated = null;
        boolean anyMatch = false;

        List<IdentityModels.IdentityAttribute> attrs = orderAttributes(req.attributes(), req.policy());

        for (IdentityModels.IdentityAttribute attr : attrs) {
            if (attr == null || isBlank(attr.type()) || isBlank(attr.value())) {
                continue;
            }

            String type = attr.type().trim();
            List<IdentityProvider> providers = registry.providersForType(type).stream()
                    .filter(p -> isProviderEnabled(cfg, p.id()))
                    .toList();
            if (providers.isEmpty()) {
                evidences.add(new IdentityModels.IdentityEvidence(
                        "registry",
                        type,
                        safeValuePreview(attr.value()),
                        "NO_MATCH",
                        Map.of("reason", "Нет провайдера, поддерживающего данный type")
                ));
                continue;
            }

            boolean matchedForAttr = false;
            for (IdentityProvider provider : providers) {
                try {
                    IdentityProvider.ProviderContext ctx = new IdentityProvider.ProviderContext(cfg, meta == null ? Map.of() : meta);
                    var resOpt = provider.resolve(attr, req, ctx);
                    if (resOpt.isEmpty()) {
                        evidences.add(new IdentityModels.IdentityEvidence(
                                provider.id(),
                                type,
                                safeValuePreview(attr.value()),
                                "NO_MATCH",
                                Map.of()
                        ));
                        continue;
                    }

                    IdentityModels.IdentityProfile prof = normalizeProfile(resOpt.get(), segmentNormalizer);
                    aggregated = mergeProfiles(aggregated, prof, segmentNormalizer);
                    matchedForAttr = true;
                    anyMatch = true;
                    evidences.add(new IdentityModels.IdentityEvidence(
                            provider.id(),
                            type,
                            safeValuePreview(attr.value()),
                            "MATCH",
                            Map.of("clientId", prof.clientId() == null ? "" : prof.clientId())
                    ));

                    if (req.policy().stopOnAnyMatch()) {
                        break;
                    }
                    if (req.policy().stopOnFirstClientId() && aggregated != null && !isBlank(aggregated.clientId())) {
                        break;
                    }
                } catch (Exception e) {
                    evidences.add(new IdentityModels.IdentityEvidence(
                            provider.id(),
                            type,
                            safeValuePreview(attr.value()),
                            "ERROR",
                            Map.of("error", sanitizeError(e.getMessage()))
                    ));
                }
            }

            if (req.policy().stopOnAnyMatch() && matchedForAttr) {
                break;
            }
            if (req.policy().stopOnFirstClientId() && aggregated != null && !isBlank(aggregated.clientId())) {
                break;
            }
        }

        IdentityModels.IdentityProfile finalProfile = aggregated;
        if (finalProfile == null) {
            // Безопасный дефолт: не «выдумываем» clientId, но возвращаем нормализованный каркас профиля.
            String seg = segmentNormalizer.normalize("DEFAULT");
            finalProfile = new IdentityModels.IdentityProfile(
                    null,
                    Map.of(),
                    null,
                    seg,
                    segmentNormalizer.weightForSegment(seg),
                    List.of(),
                    Map.of(),
                    Map.of("identityResolved", false)
            );
        }

        Map<String, Object> diagnostics = new HashMap<>();
        diagnostics.put("resolved", anyMatch);
        diagnostics.put("providersUsed", evidences.stream().map(IdentityModels.IdentityEvidence::providerId).distinct().toList());

        return new IdentityModels.IdentityResolution(finalProfile, evidences, diagnostics);
    }

    /**
     * Проверить, включён ли провайдер в runtime-config.
     * <p>
     * Политика:
     * <ul>
     *   <li>если секция identity.providers отсутствует — считаем провайдера включённым (безопасная совместимость);</li>
     *   <li>если для провайдера задано поле enabled=false — провайдер не вызывается вообще;</li>
     *   <li>в любых нештатных случаях (не Map, нет поля enabled) — считаем провайдера включённым.</li>
     * </ul>
     * <p>
     * Важно: этот флаг относится к провайдеру как к источнику.
     * Он не отменяет поддержку конкретных типов, которая определяется {@link IdentityProvider#supportsType(String)}.
     */
    private boolean isProviderEnabled(RuntimeConfigStore.RuntimeConfig cfg, String providerId) {
        if (cfg == null || cfg.identity() == null || cfg.identity().providers() == null) {
            return true;
        }
        Object raw = cfg.identity().providers().get(providerId);
        if (!(raw instanceof Map<?, ?> m)) {
            return true;
        }
        Object enabled = m.get("enabled");
        if (enabled instanceof Boolean b) {
            return b;
        }
        return true;
    }

    /**
     * Преобразовать вход (часто приходит как Map из Groovy/JSON) в типизированный запрос.
     */
    public IdentityModels.IdentityRequest convertToRequest(Object raw) {
        if (raw == null) {
            return new IdentityModels.IdentityRequest(List.of(), Map.of(), IdentityModels.IdentityResolutionPolicy.defaultPolicy());
        }
        if (raw instanceof IdentityModels.IdentityRequest r) {
            return r;
        }
        try {
            return objectMapper.convertValue(raw, IdentityModels.IdentityRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Некорректный формат запроса идентификации: ожидается JSON/Map с полями attributes/context/policy");
        }
    }

    private IdentityModels.IdentityRequest normalizeRequest(IdentityModels.IdentityRequest request) {
        if (request == null) {
            return new IdentityModels.IdentityRequest(List.of(), Map.of(), IdentityModels.IdentityResolutionPolicy.defaultPolicy());
        }
        List<IdentityModels.IdentityAttribute> attrs = request.attributes() == null ? List.of() : request.attributes();
        Map<String, Object> ctx = request.context() == null ? Map.of() : request.context();
        IdentityModels.IdentityResolutionPolicy pol = request.policy() == null ? IdentityModels.IdentityResolutionPolicy.defaultPolicy() : request.policy();
        return new IdentityModels.IdentityRequest(attrs, ctx, pol);
    }

    private List<IdentityModels.IdentityAttribute> orderAttributes(List<IdentityModels.IdentityAttribute> attrs,
                                                                  IdentityModels.IdentityResolutionPolicy policy) {
        if (attrs == null || attrs.isEmpty()) {
            throw new IllegalArgumentException("Не задан ни один идентификатор (attributes пуст)");
        }
        if (policy == null || policy.preferredTypes() == null || policy.preferredTypes().isEmpty()) {
            return attrs;
        }

        List<String> prio = policy.preferredTypes().stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .toList();

        List<IdentityModels.IdentityAttribute> head = new ArrayList<>();
        List<IdentityModels.IdentityAttribute> tail = new ArrayList<>();
        for (IdentityModels.IdentityAttribute a : attrs) {
            if (a == null || isBlank(a.type())) {
                tail.add(a);
                continue;
            }
            String t = a.type().trim().toLowerCase(Locale.ROOT);
            if (prio.contains(t)) {
                head.add(a);
            } else {
                tail.add(a);
            }
        }

        List<IdentityModels.IdentityAttribute> res = new ArrayList<>(head.size() + tail.size());
        res.addAll(head);
        res.addAll(tail);
        return res;
    }

    private IdentityModels.IdentityProfile normalizeProfile(IdentityModels.IdentityProfile p, SegmentNormalizer normalizer) {
        if (p == null) {
            return null;
        }
        String seg = normalizer.normalize(p.segment());
        Integer weight = p.priorityWeight();
        if (weight == null) {
            weight = normalizer.weightForSegment(seg);
        }
        return new IdentityModels.IdentityProfile(
                blankToNull(p.clientId()),
                p.externalIds() == null ? Map.of() : p.externalIds(),
                blankToNull(p.fullName()),
                seg,
                weight,
                p.serviceHints() == null ? List.of() : p.serviceHints(),
                p.branchAffinity() == null ? Map.of() : p.branchAffinity(),
                p.attributes() == null ? Map.of() : p.attributes()
        );
    }

    private IdentityModels.IdentityProfile mergeProfiles(IdentityModels.IdentityProfile a,
                                                        IdentityModels.IdentityProfile b,
                                                        SegmentNormalizer normalizer) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }

        String clientId = firstNonBlank(a.clientId(), b.clientId());

        Map<String, String> externalIds = new LinkedHashMap<>();
        if (a.externalIds() != null) {
            externalIds.putAll(a.externalIds());
        }
        if (b.externalIds() != null) {
            externalIds.putAll(b.externalIds());
        }

        String fullName = firstNonBlank(a.fullName(), b.fullName());

        String segment = normalizer.chooseBest(List.of(a.segment(), b.segment()));

        Integer weight = maxInt(a.priorityWeight(), b.priorityWeight());
        if (weight == null) {
            weight = normalizer.weightForSegment(segment);
        }

        List<String> hints = new ArrayList<>();
        if (a.serviceHints() != null) {
            hints.addAll(a.serviceHints());
        }
        if (b.serviceHints() != null) {
            for (String s : b.serviceHints()) {
                if (s != null && !hints.contains(s)) {
                    hints.add(s);
                }
            }
        }

        Map<String, Object> branchAffinity = new LinkedHashMap<>();
        if (a.branchAffinity() != null) {
            branchAffinity.putAll(a.branchAffinity());
        }
        if (b.branchAffinity() != null) {
            branchAffinity.putAll(b.branchAffinity());
        }

        Map<String, Object> attrs = new LinkedHashMap<>();
        if (a.attributes() != null) {
            attrs.putAll(a.attributes());
        }
        if (b.attributes() != null) {
            attrs.putAll(b.attributes());
        }

        return new IdentityModels.IdentityProfile(
                clientId,
                Map.copyOf(externalIds),
                fullName,
                segment,
                weight,
                List.copyOf(hints),
                Map.copyOf(branchAffinity),
                Map.copyOf(attrs)
        );
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstNonBlank(String a, String b) {
        if (!isBlank(a)) {
            return a;
        }
        return isBlank(b) ? null : b;
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s;
    }

    private static Integer maxInt(Integer a, Integer b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return Math.max(a, b);
    }

    private static String sanitizeError(String msg) {
        if (msg == null) {
            return "";
        }
        // Минимальная санитизация: исключаем прямую утечку «Authorization=Bearer ...».
        return msg.replaceAll("(?i)authorization\\s*=?\\s*bearer\\s+\\S+", "Authorization=Bearer ***");
    }

    private static String safeValuePreview(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        if (v.length() <= 8) {
            return v;
        }
        return v.substring(0, 4) + "…" + v.substring(v.length() - 2);
    }
}
