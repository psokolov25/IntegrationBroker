package ru.aritmos.integrationbroker.adapters;

import jakarta.inject.Singleton;
import ru.aritmos.integrationbroker.core.FlowEngine;
import ru.aritmos.integrationbroker.visitmanager.VisitManagerClient;

import java.util.List;
import java.util.Map;

/**
 * Реализация {@link VisitManagerApi} поверх типизированного HTTP-клиента VisitManager.
 */
@Singleton
@FlowEngine.GroovyExecutable("visitManager")
public class VisitManagerApiImpl implements VisitManagerApi {

    private final VisitManagerClient client;

    public VisitManagerApiImpl(VisitManagerClient client) {
        this.client = client;
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
        return createVisitWithParameters(target, branchId, entryPointId, serviceIds, Map.of(), printTicket, segmentationRuleId,
                headers, sourceMessageId, correlationId, idempotencyKey);
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
        // target будет использоваться при маршрутизации multi-target (S6).
        VisitManagerClient.CallResult r = client.createVisitWithParametersRest(
                branchId,
                entryPointId,
                serviceIds,
                parameters,
                printTicket,
                segmentationRuleId,
                headers,
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
        VisitManagerClient.CallResult r = client.updateVisitParametersRest(
                branchId,
                visitId,
                parameters,
                headers,
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }


    @Override
    public Map<String, Object> getServicesCatalog(String target, String branchId) {
        VisitManagerClient.CallResult r = client.getServicesCatalog(branchId);
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
        VisitManagerClient.CallResult r = client.callRestEndpoint(
                method,
                path,
                body,
                headers,
                sourceMessageId,
                correlationId,
                idempotencyKey
        );
        return toResult(r);
    }

    private static Map<String, Object> toResult(VisitManagerClient.CallResult r) {
        return Map.of(
                "mode", r.mode(),
                "httpStatus", r.httpStatus(),
                "outboxId", r.outboxId(),
                "errorCode", r.errorCode(),
                "errorMessage", r.errorMessage(),
                "body", r.response()
        );
    }

}
