package ru.aritmos.integrationbroker.adapters;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.core.FlowEngine;
import ru.aritmos.integrationbroker.visitmanager.VisitManagerClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Реализация {@link VisitManagerApi} поверх типизированного HTTP-клиента VisitManager.
 */
@Singleton
@FlowEngine.GroovyExecutable("visitManager")
public class VisitManagerApiImpl implements VisitManagerApi {

    private final VisitManagerClient client;
    private final VisitManagerConflictMetrics conflictMetrics;

    public VisitManagerApiImpl(VisitManagerClient client) {
        this(client, null);
    }

    public VisitManagerApiImpl(VisitManagerClient client, VisitManagerConflictMetrics conflictMetrics) {
        this.client = client;
        this.conflictMetrics = conflictMetrics;
    }

    @Override
    public Map<String, Object> createVisit(String target,
                                           String branchId,
                                           String entryPointId,
                                           List<String> serviceIds,
                                           boolean printTicket,
                                           String segmentationRuleId,
                                           Map<String, String> headers,
                                           String sourceMessageId,
                                           String correlationId,
                                           String idempotencyKey) {
        if (isBlank(branchId)) {
            return invalidArgument("branchId");
        }
        List<String> normalizedServiceIds = normalizeServiceIds(serviceIds);
        if (normalizedServiceIds.isEmpty()) {
            return invalidArgument("serviceIds");
        }
        VisitManagerClient.CallResult r = client.createVisitRest(
                branchId,
                entryPointId,
                normalizedServiceIds,
                printTicket,
                segmentationRuleId,
                normalizeHeaders(headers),
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }

    @Override
    public Map<String, Object> createVisitWithParameters(String target,
                                                         String branchId,
                                                         String entryPointId,
                                                         List<String> serviceIds,
                                                         Map<String, String> parameters,
                                                         boolean printTicket,
                                                         String segmentationRuleId,
                                                         Map<String, String> headers,
                                                         String sourceMessageId,
                                                         String correlationId,
                                                         String idempotencyKey) {
        if (isBlank(branchId)) {
            return invalidArgument("branchId");
        }
        List<String> normalizedServiceIds = normalizeServiceIds(serviceIds);
        if (normalizedServiceIds.isEmpty()) {
            return invalidArgument("serviceIds");
        }
        // target будет использоваться при маршрутизации multi-target (S6).
        VisitManagerClient.CallResult r = client.createVisitWithParametersRest(
                branchId,
                entryPointId,
                normalizedServiceIds,
                normalizeParameters(parameters),
                printTicket,
                segmentationRuleId,
                normalizeHeaders(headers),
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }

    @Override
    public Map<String, Object> updateVisitParameters(String target,
                                                     String branchId,
                                                     String visitId,
                                                     Map<String, String> parameters,
                                                     Map<String, String> headers,
                                                     String sourceMessageId,
                                                     String correlationId,
                                                     String idempotencyKey) {
        if (isBlank(branchId)) {
            return invalidArgument("branchId");
        }
        if (isBlank(visitId)) {
            return invalidArgument("visitId");
        }
        VisitManagerClient.CallResult r = client.updateVisitParametersRest(
                branchId,
                visitId,
                normalizeParameters(parameters),
                normalizeHeaders(headers),
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }


    @Override
    public Map<String, Object> getServicesCatalog(String target, String branchId) {
        return getServicesCatalog(target, branchId, Map.of(), null, null, null);
    }

    @Override
    public Map<String, Object> getServicesCatalog(String target,
                                                  String branchId,
                                                  Map<String, String> headers,
                                                  String sourceMessageId,
                                                  String correlationId,
                                                  String idempotencyKey) {
        if (isBlank(branchId)) {
            return invalidArgument("branchId");
        }
        VisitManagerClient.CallResult r = client.callRestEndpoint(
                "GET",
                "/entrypoint/branches/" + urlEncodePathSegment(branchId) + "/services/catalog",
                null,
                normalizeHeaders(headers),
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }


    @Override
    public Map<String, Object> getBranchState(String target,
                                              String branchId,
                                              Map<String, String> headers,
                                              String sourceMessageId,
                                              String correlationId,
                                              String idempotencyKey) {
        if (isBlank(branchId)) {
            return invalidArgument("branchId");
        }
        VisitManagerClient.CallResult r = client.callRestEndpoint(
                "GET",
                "/managementinformation/branches/" + urlEncodePathSegment(branchId),
                null,
                normalizeHeaders(headers),
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }

    @Override
    public Map<String, Object> getBranchesState(String target,
                                                String userName,
                                                Map<String, String> headers,
                                                String sourceMessageId,
                                                String correlationId,
                                                String idempotencyKey) {
        String path = "/managementinformation/branches";
        if (userName != null && !userName.isBlank()) {
            path = path + "?userName=" + urlEncodeQueryParam(userName);
        }
        VisitManagerClient.CallResult r = client.callRestEndpoint(
                "GET",
                path,
                null,
                normalizeHeaders(headers),
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }

    @Override
    public Map<String, Object> getBranchesTiny(String target,
                                               Map<String, String> headers,
                                               String sourceMessageId,
                                               String correlationId,
                                               String idempotencyKey) {
        VisitManagerClient.CallResult r = client.callRestEndpoint(
                "GET",
                "/managementinformation/branches/tiny",
                null,
                normalizeHeaders(headers),
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }

    @Override
    public Map<String, Object> callNextVisit(String target,
                                             String branchId,
                                             String servicePointId,
                                             boolean autoCallEnabled,
                                             Map<String, String> headers,
                                             String sourceMessageId,
                                             String correlationId,
                                             String idempotencyKey) {
        if (isBlank(branchId)) {
            return invalidArgument("branchId");
        }
        if (isBlank(servicePointId)) {
            return invalidArgument("servicePointId");
        }
        VisitManagerClient.CallResult r = client.callRestEndpoint(
                "POST",
                "/servicepoint/branches/" + urlEncodePathSegment(branchId) + "/servicePoints/"
                        + urlEncodePathSegment(servicePointId)
                        + "/call?isAutoCallEnabled=" + autoCallEnabled,
                null,
                normalizeHeaders(headers),
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }

    @Override
    public Map<String, Object> postponeCurrentVisit(String target,
                                                    String branchId,
                                                    String servicePointId,
                                                    Map<String, String> headers,
                                                    String sourceMessageId,
                                                    String correlationId,
                                                    String idempotencyKey) {
        if (isBlank(branchId)) {
            return invalidArgument("branchId");
        }
        if (isBlank(servicePointId)) {
            return invalidArgument("servicePointId");
        }
        VisitManagerClient.CallResult r = client.callRestEndpoint(
                "PUT",
                "/servicepoint/branches/" + urlEncodePathSegment(branchId) + "/servicePoints/"
                        + urlEncodePathSegment(servicePointId) + "/postpone",
                null,
                normalizeHeaders(headers),
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }


    @Override
    public Map<String, Object> enterServicePointMode(String target,
                                                      String branchId,
                                                      Map<String, Object> query,
                                                      Map<String, String> headers,
                                                      String sourceMessageId,
                                                      String correlationId,
                                                      String idempotencyKey) {
        if (isBlank(branchId)) {
            return invalidArgument("branchId");
        }
        String path = withQuery("/servicepoint/branches/" + urlEncodePathSegment(branchId) + "/enter", query);
        VisitManagerClient.CallResult r = client.callRestEndpoint(
                "POST",
                path,
                null,
                normalizeHeaders(headers),
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }

    @Override
    public Map<String, Object> exitServicePointMode(String target,
                                                     String branchId,
                                                     Map<String, Object> query,
                                                     Map<String, String> headers,
                                                     String sourceMessageId,
                                                     String correlationId,
                                                     String idempotencyKey) {
        if (isBlank(branchId)) {
            return invalidArgument("branchId");
        }
        String path = withQuery("/servicepoint/branches/" + urlEncodePathSegment(branchId) + "/exit", query);
        VisitManagerClient.CallResult r = client.callRestEndpoint(
                "POST",
                path,
                null,
                normalizeHeaders(headers),
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }

    @Override
    public Map<String, Object> startAutoCall(String target,
                                             String branchId,
                                             String servicePointId,
                                             Map<String, String> headers,
                                             String sourceMessageId,
                                             String correlationId,
                                             String idempotencyKey) {
        if (isBlank(branchId)) {
            return invalidArgument("branchId");
        }
        if (isBlank(servicePointId)) {
            return invalidArgument("servicePointId");
        }
        VisitManagerClient.CallResult r = client.callRestEndpoint(
                "PUT",
                "/servicepoint/branches/" + urlEncodePathSegment(branchId) + "/service-points/"
                        + urlEncodePathSegment(servicePointId) + "/auto-call/start",
                null,
                normalizeHeaders(headers),
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }

    @Override
    public Map<String, Object> cancelAutoCall(String target,
                                              String branchId,
                                              String servicePointId,
                                              Map<String, String> headers,
                                              String sourceMessageId,
                                              String correlationId,
                                              String idempotencyKey) {
        if (isBlank(branchId)) {
            return invalidArgument("branchId");
        }
        if (isBlank(servicePointId)) {
            return invalidArgument("servicePointId");
        }
        VisitManagerClient.CallResult r = client.callRestEndpoint(
                "PUT",
                "/servicepoint/branches/" + urlEncodePathSegment(branchId) + "/service-points/"
                        + urlEncodePathSegment(servicePointId) + "/auto-call/cancel",
                null,
                normalizeHeaders(headers),
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }


    @Override
    public Map<String, Object> createVirtualVisit(String target,
                                                   String branchId,
                                                   String servicePointId,
                                                   List<String> serviceIds,
                                                   Map<String, String> headers,
                                                   String sourceMessageId,
                                                   String correlationId,
                                                   String idempotencyKey) {
        if (isBlank(branchId)) {
            return invalidArgument("branchId");
        }
        if (isBlank(servicePointId)) {
            return invalidArgument("servicePointId");
        }
        List<String> normalizedServiceIds = normalizeServiceIds(serviceIds);
        if (normalizedServiceIds.isEmpty()) {
            return invalidArgument("serviceIds");
        }
        VisitManagerClient.CallResult r = client.callRestEndpoint(
                "POST",
                "/entrypoint/branches/" + urlEncodePathSegment(branchId)
                        + "/service-points/" + urlEncodePathSegment(servicePointId)
                        + "/virtual-visits",
                normalizedServiceIds,
                normalizeHeaders(headers),
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }

    @Override
    public Map<String, Object> createVisitOnPrinterWithServices(String target,
                                                                 String branchId,
                                                                 String printerId,
                                                                 List<String> serviceIds,
                                                                 boolean printTicket,
                                                                 String segmentationRuleId,
                                                                 Map<String, String> headers,
                                                                 String sourceMessageId,
                                                                 String correlationId,
                                                                 String idempotencyKey) {
        if (isBlank(branchId)) {
            return invalidArgument("branchId");
        }
        if (isBlank(printerId)) {
            return invalidArgument("printerId");
        }
        List<String> normalizedServiceIds = normalizeServiceIds(serviceIds);
        if (normalizedServiceIds.isEmpty()) {
            return invalidArgument("serviceIds");
        }

        String path = "/entrypoint/branches/" + urlEncodePathSegment(branchId)
                + "/printers/" + urlEncodePathSegment(printerId)
                + "/visits";
        Map<String, Object> query = new HashMap<>();
        query.put("printTicket", printTicket);
        if (!isBlank(segmentationRuleId)) {
            query.put("segmentationRuleId", segmentationRuleId);
        }

        VisitManagerClient.CallResult r = client.callRestEndpoint(
                "POST",
                withQuery(path, query),
                normalizedServiceIds,
                normalizeHeaders(headers),
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }

    @Override
    public Map<String, Object> createVisitOnPrinterWithParameters(String target,
                                                                   String branchId,
                                                                   String printerId,
                                                                   List<String> serviceIds,
                                                                   Map<String, String> parameters,
                                                                   boolean printTicket,
                                                                   String segmentationRuleId,
                                                                   Map<String, String> headers,
                                                                   String sourceMessageId,
                                                                   String correlationId,
                                                                   String idempotencyKey) {
        if (isBlank(branchId)) {
            return invalidArgument("branchId");
        }
        if (isBlank(printerId)) {
            return invalidArgument("printerId");
        }
        String path = "/entrypoint/branches/" + urlEncodePathSegment(branchId)
                + "/printers/" + urlEncodePathSegment(printerId)
                + "/visits";
        Map<String, Object> query = new HashMap<>();
        query.put("printTicket", printTicket);
        if (!isBlank(segmentationRuleId)) {
            query.put("segmentationRuleId", segmentationRuleId);
        }

        List<String> normalizedServiceIds = normalizeServiceIds(serviceIds);
        if (normalizedServiceIds.isEmpty()) {
            return invalidArgument("serviceIds");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("serviceIds", normalizedServiceIds);
        body.put("parameters", normalizeParameters(parameters));

        VisitManagerClient.CallResult r = client.callRestEndpoint(
                "POST",
                withQuery(path, query),
                body,
                normalizeHeaders(headers),
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }

    @Override
    public Map<String, Object> callEndpoint(String target,
                                            String method,
                                            String path,
                                            Object body,
                                            Map<String, String> headers,
                                            String sourceMessageId,
                                            String correlationId,
                                            String idempotencyKey) {
        if (isBlank(method)) {
            return invalidArgument("method");
        }
        if (isBlank(path)) {
            return invalidArgument("path");
        }
        String normalizedMethod = normalizeMethod(method);
        if (normalizedMethod == null) {
            return invalidArgument("method");
        }
        VisitManagerClient.CallResult r = client.callRestEndpoint(
                normalizedMethod,
                path.trim(),
                body,
                normalizeHeaders(headers),
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }


    private static String normalizeMethod(String method) {
        if (method == null) {
            return null;
        }
        String m = method.trim().toUpperCase();
        if (m.isEmpty()) {
            return null;
        }
        return switch (m) {
            case "GET", "POST", "PUT", "PATCH", "DELETE" -> m;
            default -> null;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Map<String, Object> invalidArgument(String field) {
        return toResult(VisitManagerClient.CallResult.error("INVALID_ARGUMENT", "Отсутствует обязательный параметр: " + field));
    }

    private static String urlEncodePathSegment(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String urlEncodeQueryParam(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String withQuery(String path, Map<String, Object> query) {
        if (query == null || query.isEmpty()) {
            return path;
        }
        StringJoiner sj = new StringJoiner("&");
        var keys = new ArrayList<String>();
        for (String k : query.keySet()) {
            if (k != null && !k.isBlank()) {
                keys.add(k);
            }
        }
        keys.sort(String::compareTo);
        for (String k : keys) {
            String v = normalizeQueryValue(query.get(k));
            if (v == null) {
                continue;
            }
            sj.add(urlEncodeQueryParam(k) + "=" + urlEncodeQueryParam(v));
        }
        String q = sj.toString();
        if (q.isBlank()) {
            return path;
        }
        return path + "?" + q;
    }

    private static String normalizeQueryValue(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof CharSequence cs) {
            String t = cs.toString().trim();
            return t.isEmpty() ? null : t;
        }
        return String.valueOf(v);
    }

    private static Map<String, String> normalizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String k = e.getKey().trim();
            String v = e.getValue().trim();
            if (k.isEmpty() || v.isEmpty()) {
                continue;
            }
            out.put(k, v);
        }
        return out;
    }


    private static Map<String, String> normalizeParameters(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : parameters.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String k = e.getKey().trim();
            String v = e.getValue().trim();
            if (k.isEmpty() || v.isEmpty()) {
                continue;
            }
            out.put(k, v);
        }
        return out;
    }

    private static List<String> normalizeServiceIds(List<String> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return List.of();
        }
        var out = new java.util.LinkedHashSet<String>();
        for (String s : serviceIds) {
            if (s == null) {
                continue;
            }
            String t = s.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return new ArrayList<>(out);
    }

    private Map<String, Object> toResult(VisitManagerClient.CallResult r) {
        maybeTrackConflict(r);

        Map<String, Object> out = new HashMap<>();
        out.put("mode", r.mode());
        out.put("httpStatus", r.httpStatus());
        out.put("outboxId", r.outboxId());
        out.put("errorCode", r.errorCode());
        out.put("errorMessage", r.errorMessage());
        out.put("body", r.response());

        String status = resolveEnvelopeStatus(r);
        Map<String, Object> error = resolveEnvelopeError(r);
        out.put("status", status);
        out.put("headers", Map.of());
        out.put("error", error);
        return out;
    }

    private void maybeTrackConflict(VisitManagerClient.CallResult r) {
        if (conflictMetrics == null || r == null) {
            return;
        }
        if (r.httpStatus() == 409 || "HTTP_409".equals(r.errorCode())) {
            conflictMetrics.increment409();
        }
    }

    private static String resolveEnvelopeStatus(VisitManagerClient.CallResult r) {
        if (r == null) {
            return "ERROR";
        }
        if ("DIRECT".equals(r.mode()) && r.httpStatus() >= 200 && r.httpStatus() < 300) {
            return "SUCCESS";
        }
        if ("OUTBOX_ENQUEUED".equals(r.mode())) {
            return "ACCEPTED";
        }
        return "ERROR";
    }

    private static Map<String, Object> resolveEnvelopeError(VisitManagerClient.CallResult r) {
        if (r == null) {
            return Map.of("code", "UNKNOWN", "domainCode", "VM_CALL_FAILED", "message", "Unknown VisitManager call result", "httpStatus", 0);
        }

        String errorCode = r.errorCode();
        String errorMessage = r.errorMessage();
        int httpStatus = r.httpStatus();

        if ((errorCode == null || errorCode.isBlank()) && httpStatus >= 200 && httpStatus < 300) {
            return null;
        }
        if ((errorCode == null || errorCode.isBlank()) && "OUTBOX_ENQUEUED".equals(r.mode())) {
            return null;
        }

        String domainCode = mapVisitManagerDomainCode(errorCode, httpStatus);
        return Map.of(
                "code", errorCode == null ? "UNKNOWN" : errorCode,
                "domainCode", domainCode,
                "message", errorMessage == null ? "" : errorMessage,
                "httpStatus", httpStatus
        );
    }

    private static String mapVisitManagerDomainCode(String errorCode, int httpStatus) {
        if ("INVALID_ARGUMENT".equals(errorCode)) {
            return "IB_INVALID_ARGUMENT";
        }
        if (httpStatus == 404 || "HTTP_404".equals(errorCode)) {
            return "VM_NOT_FOUND";
        }
        if (httpStatus == 409 || "HTTP_409".equals(errorCode)) {
            return "VM_CONFLICT";
        }
        if (httpStatus >= 500 || "HTTP_500".equals(errorCode)) {
            return "VM_INTERNAL_ERROR";
        }
        if (httpStatus >= 400) {
            return "VM_CLIENT_ERROR";
        }
        return "VM_CALL_FAILED";
    }

}
