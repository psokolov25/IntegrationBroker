package ru.aritmos.integrationbroker.appointment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.CorrelationContext;
import ru.aritmos.integrationbroker.core.OAuth2ClientCredentialsService;
import ru.aritmos.integrationbroker.core.SensitiveDataSanitizer;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Supplier;

/**
 * CUSTOM_CONNECTOR реализация для appointment через runtime-config шаблоны.
 */
final class AppointmentCustomConnectorClient implements AppointmentClient {

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final Supplier<RuntimeConfigStore.RuntimeConfig> configSupplier;
    private final ObjectMapper objectMapper;
    private final OAuth2ClientCredentialsService oauth2Service;
    private final HttpClient httpClient;

    AppointmentCustomConnectorClient(Supplier<RuntimeConfigStore.RuntimeConfig> configSupplier,
                                     ObjectMapper objectMapper,
                                     OAuth2ClientCredentialsService oauth2Service) {
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.oauth2Service = oauth2Service;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Override
    public AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> getAppointments(AppointmentModels.GetAppointmentsRequest request, Map<String, Object> meta) {
        VendorCallResult call = execute("getAppointments", request == null ? null : request.context(), request == null ? null : request.keys(),
                request == null ? null : request.from(), request == null ? null : request.to(), meta, null);
        if (!call.success()) {
            return AppointmentModels.AppointmentOutcome.error(call.message());
        }
        List<AppointmentModels.Appointment> mapped = mapAppointments(call.body(), call.operation());
        return new AppointmentModels.AppointmentOutcome<>(true, "OK", "", mapped, call.details());
    }

    @Override
    public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> getNearestAppointment(AppointmentModels.GetNearestAppointmentRequest request, Map<String, Object> meta) {
        AppointmentModels.GetAppointmentsRequest req = new AppointmentModels.GetAppointmentsRequest(
                request == null ? List.of() : request.keys(),
                null,
                null,
                request == null ? Map.of() : request.context()
        );
        AppointmentModels.AppointmentOutcome<List<AppointmentModels.Appointment>> list = getAppointments(req, meta);
        if (!list.success()) {
            return AppointmentModels.AppointmentOutcome.error(list.message());
        }
        AppointmentModels.Appointment nearest = list.result().stream()
                .filter(a -> a != null && a.startAt() != null)
                .filter(a -> "CONFIRMED".equalsIgnoreCase(a.status()) || "BOOKED".equalsIgnoreCase(a.status()) || a.status() == null)
                .min(Comparator.comparing(AppointmentModels.Appointment::startAt))
                .orElse(null);
        return AppointmentModels.AppointmentOutcome.ok(nearest);
    }

    @Override
    public AppointmentModels.AppointmentOutcome<List<AppointmentModels.Slot>> getAvailableSlots(AppointmentModels.GetAvailableSlotsRequest request, Map<String, Object> meta) {
        Map<String, Object> extra = new LinkedHashMap<>();
        if (request != null) {
            extra.put("serviceCode", request.serviceCode());
            extra.put("locationId", request.locationId());
        }
        VendorCallResult call = execute("getAvailableSlots", request == null ? null : request.context(), List.of(),
                request == null ? null : request.from(), request == null ? null : request.to(), meta, extra);
        if (!call.success()) {
            return AppointmentModels.AppointmentOutcome.error(call.message());
        }
        List<AppointmentModels.Slot> mapped = mapSlots(call.body(), call.operation());
        return new AppointmentModels.AppointmentOutcome<>(true, "OK", "", mapped, call.details());
    }

    @Override
    public AppointmentModels.AppointmentOutcome<AppointmentModels.Appointment> bookSlot(AppointmentModels.BookSlotRequest request, Map<String, Object> meta) {
        Map<String, Object> extra = new LinkedHashMap<>();
        if (request != null) {
            extra.put("slotId", request.slotId());
            extra.put("serviceCode", request.serviceCode());
        }
        VendorCallResult call = execute("bookSlot", request == null ? null : request.context(), request == null ? null : request.keys(),
                null, null, meta, extra);
        if (!call.success()) {
            return AppointmentModels.AppointmentOutcome.error(call.message());
        }
        AppointmentModels.Appointment first = mapSingleAppointment(call.body(), call.operation());
        return new AppointmentModels.AppointmentOutcome<>(true, "OK", "", first, call.details());
    }

    @Override
    public AppointmentModels.AppointmentOutcome<Boolean> cancelAppointment(AppointmentModels.CancelAppointmentRequest request, Map<String, Object> meta) {
        Map<String, Object> extra = new LinkedHashMap<>();
        if (request != null) {
            extra.put("appointmentId", request.appointmentId());
            extra.put("reason", request.reason());
        }
        VendorCallResult call = execute("cancelAppointment", request == null ? null : request.context(), List.of(),
                null, null, meta, extra);
        if (!call.success()) {
            return AppointmentModels.AppointmentOutcome.error(call.message());
        }
        return new AppointmentModels.AppointmentOutcome<>(true, "OK", "", Boolean.TRUE, call.details());
    }

    @Override
    public AppointmentModels.AppointmentOutcome<AppointmentModels.QueuePlan> buildQueuePlan(AppointmentModels.BuildQueuePlanRequest request, Map<String, Object> meta) {
        Map<String, Object> extra = new LinkedHashMap<>();
        if (request != null) {
            extra.put("appointmentId", request.appointmentId());
        }
        VendorCallResult call = execute("buildQueuePlan", request == null ? null : request.context(), request == null ? null : request.keys(),
                null, null, meta, extra);
        if (!call.success()) {
            return AppointmentModels.AppointmentOutcome.error(call.message());
        }
        // Для baseline: если внешняя операция возвращает запись — строим минимальный план.
        AppointmentModels.Appointment appt = mapSingleAppointment(call.body(), call.operation());
        String appointmentId = request == null ? null : request.appointmentId();
        if (appt != null && appt.appointmentId() != null) {
            appointmentId = appt.appointmentId();
        }
        String segment = request != null && request.context() != null && request.context().get("segment") != null
                ? String.valueOf(request.context().get("segment"))
                : "DEFAULT";
        AppointmentModels.QueuePlan plan = new AppointmentModels.QueuePlan(
                appointmentId,
                segment,
                List.of(),
                Map.of("source", "customConnector")
        );
        return new AppointmentModels.AppointmentOutcome<>(true, "OK", "", plan, call.details());
    }

    private VendorCallResult execute(String operationName,
                                     Map<String, Object> context,
                                     List<AppointmentModels.BookingKey> keys,
                                     Instant from,
                                     Instant to,
                                     Map<String, Object> meta,
                                     Map<String, Object> extraVars) {
        RuntimeConfigStore.RuntimeConfig cfg = configSupplier.get();
        RuntimeConfigStore.AppointmentConfig ac = cfg == null ? null : cfg.appointment();
        if (ac == null || ac.profile() != RuntimeConfigStore.AppointmentProfile.CUSTOM_CONNECTOR) {
            return VendorCallResult.error("Профиль appointment не настроен на CUSTOM_CONNECTOR", Map.of());
        }
        RuntimeConfigStore.RestConnectorConfig connector = cfg.restConnectors() == null ? null : cfg.restConnectors().get(ac.connectorId());
        if (connector == null || isBlank(connector.baseUrl())) {
            return VendorCallResult.error("Не найден restConnectors." + ac.connectorId(), Map.of());
        }

        Map<String, Object> customClient = asMap(ac.settings() == null ? null : ac.settings().get("customClient"));
        if (!asBoolean(customClient.get("enabled"), true)) {
            return VendorCallResult.error("appointment.settings.customClient.enabled=false", Map.of());
        }
        Map<String, Object> operations = asMap(customClient.get("operations"));
        Map<String, Object> operation = asMap(operations.get(operationName));
        if (operation.isEmpty()) {
            return VendorCallResult.error("Операция customClient не настроена: " + operationName, Map.of());
        }

        CorrelationContext cc = CorrelationContext.resolve(metaString(meta, "correlationId"), metaString(meta, "requestId"));
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("from", from == null ? null : from.toString());
        vars.put("to", to == null ? null : to.toString());
        vars.put("context", context == null ? Map.of() : context);
        vars.put("meta", mergeMeta(meta, cc));
        vars.put("keys", toKeysMap(keys));
        if (extraVars != null) {
            vars.putAll(extraVars);
        }

        String path = substituteString(asString(operation.get("path"), ""), vars);
        String url = buildUrl(connector.baseUrl(), path, materializeStringMap(operation.get("queryTemplate"), vars));

        Map<String, String> headers = materializeStringMap(operation.get("headersTemplate"), vars);
        headers.putIfAbsent("X-Correlation-Id", cc.correlationId());
        headers.putIfAbsent("X-Request-Id", cc.requestId());
        headers.putIfAbsent("Accept", "application/json");
        applyAuth(headers, connector.auth());

        Object requestBody = materializeObject(operation.get("requestTemplate"), vars);
        String bodyJson;
        try {
            bodyJson = requestBody == null ? "{}" : objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            return VendorCallResult.error("Ошибка сериализации requestTemplate: " + safe(e.getMessage()), Map.of("operation", operationName));
        }

        String method = asString(operation.get("method"), "POST").toUpperCase();
        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10));
            headers.forEach(rb::header);
            if ("GET".equals(method) || "DELETE".equals(method)) {
                rb.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                rb.method(method, HttpRequest.BodyPublishers.ofString(bodyJson));
                if (!headers.containsKey("Content-Type")) {
                    rb.header("Content-Type", "application/json");
                }
            }
            HttpResponse<String> resp = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = resp.statusCode();
            if (status >= 200 && status < 300) {
                JsonNode body = parseJson(resp.body());
                return VendorCallResult.ok(body, operation, Map.of("httpStatus", status, "operation", operationName));
            }
            String mapped = mapHttpError(operation, status);
            if ("OK_EMPTY".equals(mapped)) {
                return VendorCallResult.ok(parseJson("{\"data\":{\"items\":[]}}"), operation, Map.of("httpStatus", status, "operation", operationName, "mappedOutcome", mapped));
            }
            boolean retriable = "ERROR_RETRYABLE".equals(mapped);
            return VendorCallResult.error("operation=" + operationName + ", status=" + status + ", outcome=" + mapped,
                    Map.of("httpStatus", status, "mappedOutcome", mapped, "retriable", retriable));
        } catch (Exception ex) {
            return VendorCallResult.error("Ошибка вызова custom connector: " + safe(ex.getMessage()), Map.of("operation", operationName));
        }
    }

    private List<AppointmentModels.Appointment> mapAppointments(JsonNode body, Map<String, Object> operation) {
        if (body == null) {
            return List.of();
        }
        Map<String, Object> responseMapping = asMap(operation.get("responseMapping"));
        String itemsPath = asString(responseMapping.get("itemsPath"), "$.data.items[*]");
        List<JsonNode> items = resolveItems(body, itemsPath);
        List<AppointmentModels.Appointment> out = new ArrayList<>();
        for (JsonNode it : items) {
            String id = readByPath(it, asString(responseMapping.get("appointmentId"), "$.id"));
            Instant startAt = parseInstant(readByPath(it, asString(responseMapping.get("startAt"), "$.start")));
            Instant endAt = parseInstant(readByPath(it, asString(responseMapping.get("endAt"), "$.end")));
            String serviceCode = readByPath(it, asString(responseMapping.get("serviceCode"), "$.service.code"));
            String doctor = readByPath(it, asString(responseMapping.get("specialistName"), "$.doctor.name"));
            String room = readByPath(it, asString(responseMapping.get("room"), "$.cabinet"));
            String status = readByPath(it, asString(responseMapping.get("status"), "$.status"));
            out.add(new AppointmentModels.Appointment(id, startAt, endAt, serviceCode, doctor, room, status,
                    Map.of("source", "customConnector")));
        }
        return out;
    }

    private AppointmentModels.Appointment mapSingleAppointment(JsonNode body, Map<String, Object> operation) {
        List<AppointmentModels.Appointment> fromList = mapAppointments(body, operation);
        if (!fromList.isEmpty()) {
            return fromList.get(0);
        }
        if (body == null || body.isArray()) {
            return null;
        }
        Map<String, Object> responseMapping = asMap(operation.get("responseMapping"));
        String id = readByPath(body, asString(responseMapping.get("appointmentId"), "$.id"));
        Instant startAt = parseInstant(readByPath(body, asString(responseMapping.get("startAt"), "$.start")));
        Instant endAt = parseInstant(readByPath(body, asString(responseMapping.get("endAt"), "$.end")));
        String serviceCode = readByPath(body, asString(responseMapping.get("serviceCode"), "$.service.code"));
        String doctor = readByPath(body, asString(responseMapping.get("specialistName"), "$.doctor.name"));
        String room = readByPath(body, asString(responseMapping.get("room"), "$.cabinet"));
        String status = readByPath(body, asString(responseMapping.get("status"), "$.status"));
        if (id == null && startAt == null && endAt == null && serviceCode == null && doctor == null && room == null && status == null) {
            return null;
        }
        return new AppointmentModels.Appointment(id, startAt, endAt, serviceCode, doctor, room, status, Map.of("source", "customConnector"));
    }

    private List<AppointmentModels.Slot> mapSlots(JsonNode body, Map<String, Object> operation) {
        if (body == null) {
            return List.of();
        }
        Map<String, Object> responseMapping = asMap(operation.get("responseMapping"));
        String itemsPath = asString(responseMapping.get("itemsPath"), "$.data.items[*]");
        List<JsonNode> items = resolveItems(body, itemsPath);
        List<AppointmentModels.Slot> out = new ArrayList<>();
        for (JsonNode it : items) {
            String slotId = readByPath(it, asString(responseMapping.get("slotId"), "$.id"));
            Instant startAt = parseInstant(readByPath(it, asString(responseMapping.get("startAt"), "$.start")));
            Instant endAt = parseInstant(readByPath(it, asString(responseMapping.get("endAt"), "$.end")));
            String serviceCode = readByPath(it, asString(responseMapping.get("serviceCode"), "$.service.code"));
            out.add(new AppointmentModels.Slot(slotId, startAt, endAt, serviceCode, Map.of("source", "customConnector")));
        }
        return out;
    }

    private void applyAuth(Map<String, String> headers, RuntimeConfigStore.RestConnectorAuth auth) {
        if (auth == null || auth.type() == null) {
            return;
        }
        switch (auth.type()) {
            case NONE -> {
            }
            case BEARER -> {
                if (!isBlank(auth.bearerToken())) {
                    headers.put("Authorization", "Bearer " + auth.bearerToken().trim());
                }
            }
            case BASIC -> {
                if (!isBlank(auth.basicUsername()) && auth.basicPassword() != null) {
                    String token = Base64.getEncoder().encodeToString((auth.basicUsername() + ":" + auth.basicPassword()).getBytes(StandardCharsets.UTF_8));
                    headers.put("Authorization", "Basic " + token);
                }
            }
            case API_KEY_HEADER -> {
                String name = isBlank(auth.headerName()) ? "X-API-Key" : auth.headerName().trim();
                if (!isBlank(auth.apiKey())) {
                    headers.put(name, auth.apiKey().trim());
                }
            }
            case OAUTH2_CLIENT_CREDENTIALS -> {
                if (oauth2Service != null) {
                    String token = oauth2Service.resolveAccessToken(auth);
                    if (!isBlank(token)) {
                        headers.put("Authorization", "Bearer " + token);
                    }
                }
            }
        }
    }

    private static List<JsonNode> resolveItems(JsonNode body, String itemsPath) {
        String p = itemsPath == null ? "" : itemsPath.trim();
        if (p.startsWith("$.") && p.endsWith("[*]")) {
            String fieldPath = p.substring(2, p.length() - 3);
            JsonNode cursor = body;
            for (String seg : fieldPath.split("\\.")) {
                if (seg.isBlank()) {
                    continue;
                }
                cursor = cursor == null ? null : cursor.path(seg);
            }
            if (cursor != null && cursor.isArray()) {
                List<JsonNode> out = new ArrayList<>();
                cursor.forEach(out::add);
                return out;
            }
        }
        return List.of();
    }

    private static String readByPath(JsonNode node, String jsonPath) {
        if (node == null || jsonPath == null || !jsonPath.startsWith("$.")) {
            return null;
        }
        String p = jsonPath.substring(2);
        JsonNode cursor = node;
        for (String seg : p.split("\\.")) {
            if (seg.isBlank()) {
                continue;
            }
            cursor = cursor.path(seg);
        }
        if (cursor.isMissingNode() || cursor.isNull()) {
            return null;
        }
        return cursor.isValueNode() ? cursor.asText() : cursor.toString();
    }

    private static Instant parseInstant(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignore) {
            return null;
        }
    }

    private JsonNode parseJson(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> mergeMeta(Map<String, Object> meta, CorrelationContext cc) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (meta != null) {
            out.putAll(meta);
        }
        out.putIfAbsent("correlationId", cc.correlationId());
        out.putIfAbsent("requestId", cc.requestId());
        return out;
    }

    private static Map<String, String> toKeysMap(List<AppointmentModels.BookingKey> keys) {
        Map<String, String> out = new LinkedHashMap<>();
        if (keys == null) {
            return out;
        }
        for (AppointmentModels.BookingKey k : keys) {
            if (k == null || isBlank(k.type()) || isBlank(k.value())) {
                continue;
            }
            out.putIfAbsent(k.type(), k.value());
        }
        return out;
    }

    private static Object materializeObject(Object template, Map<String, Object> vars) {
        if (template == null) {
            return null;
        }
        if (template instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                out.put(String.valueOf(e.getKey()), materializeObject(e.getValue(), vars));
            }
            return out;
        }
        if (template instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(materializeObject(item, vars));
            }
            return out;
        }
        if (template instanceof String s) {
            return resolveStringOrValue(s, vars);
        }
        return template;
    }

    private static Map<String, String> materializeStringMap(Object template, Map<String, Object> vars) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!(template instanceof Map<?, ?> m)) {
            return out;
        }
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            Object mat = materializeObject(e.getValue(), vars);
            String v = mat == null ? "" : String.valueOf(mat);
            if (!v.isBlank() && !"null".equals(v)) {
                out.put(String.valueOf(e.getKey()), v);
            }
        }
        return out;
    }

    private static Object resolveStringOrValue(String raw, Map<String, Object> vars) {
        if (raw == null) {
            return null;
        }
        Matcher m = TEMPLATE_PATTERN.matcher(raw.trim());
        if (m.matches()) {
            return resolveToken(m.group(1), vars);
        }
        return substituteString(raw, vars);
    }

    private static String substituteString(String raw, Map<String, Object> vars) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String out = raw;
        Matcher matcher = TEMPLATE_PATTERN.matcher(raw);
        while (matcher.find()) {
            Object resolved = resolveToken(matcher.group(1), vars);
            out = out.replace("${" + matcher.group(1) + "}", resolved == null ? "" : String.valueOf(resolved));
        }
        return out;
    }

    private static Object resolveToken(String token, Map<String, Object> vars) {
        if (token == null) {
            return "";
        }
        String key = token;
        String def = null;
        int idx = token.indexOf(':');
        if (idx >= 0) {
            key = token.substring(0, idx);
            def = token.substring(idx + 1);
        }
        Object found = flatten(vars).get(key);
        if (found == null || String.valueOf(found).isBlank()) {
            return def == null ? "" : def;
        }
        return found;
    }

    private static Map<String, Object> flatten(Map<String, Object> src) {
        Map<String, Object> out = new LinkedHashMap<>();
        flattenRec("", src, out);
        return out;
    }

    private static void flattenRec(String prefix, Object value, Map<String, Object> out) {
        if (value instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                String next = prefix.isBlank() ? String.valueOf(e.getKey()) : prefix + "." + e.getKey();
                flattenRec(next, e.getValue(), out);
            }
            return;
        }
        out.put(prefix, value == null ? "" : value);
    }

    private static String mapHttpError(Map<String, Object> operation, int status) {
        Map<String, Object> mapping = asMap(operation.get("errorMapping"));
        return asString(mapping.get(String.valueOf(status)), "ERROR");
    }

    private static String buildUrl(String baseUrl, String path, Map<String, String> query) {
        String base = trimEndSlash(baseUrl == null ? "" : baseUrl.trim());
        String p = path == null ? "" : path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        StringBuilder sb = new StringBuilder(base).append(p);
        if (query != null && !query.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, String> e : query.entrySet()) {
                if (isBlank(e.getKey()) || isBlank(e.getValue())) {
                    continue;
                }
                sb.append(first ? '?' : '&');
                first = false;
                sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
                sb.append('=');
                sb.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            }
        }
        return sb.toString();
    }

    private static String trimEndSlash(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String metaString(Map<String, Object> meta, String key) {
        if (meta == null) {
            return null;
        }
        Object v = meta.get(key);
        return v == null ? null : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static String asString(Object value, String def) {
        if (value == null) {
            return def;
        }
        String s = String.valueOf(value);
        return s.isBlank() ? def : s;
    }

    private static boolean asBoolean(Object value, boolean def) {
        if (value == null) {
            return def;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String safe(String s) {
        return SensitiveDataSanitizer.sanitizeText(s == null ? "" : s);
    }

    private record VendorCallResult(boolean success,
                                    String message,
                                    JsonNode body,
                                    Map<String, Object> operation,
                                    Map<String, Object> details) {
        static VendorCallResult ok(JsonNode body, Map<String, Object> operation, Map<String, Object> details) {
            return new VendorCallResult(true, "", body, operation, details);
        }

        static VendorCallResult error(String message, Map<String, Object> details) {
            return new VendorCallResult(false, message, null, Map.of(), details == null ? Map.of() : details);
        }
    }
}
