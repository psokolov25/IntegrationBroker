package ru.aritmos.integrationbroker.adapters;

import java.util.List;
import java.util.Map;

/**
 * Высокоуровневый API для вызова VisitManager из Groovy и Java orchestration-слоя.
 *
 * <p>Контракт опирается на зафиксированные endpoint'ы VisitManager:
 * создание визитов, создание визитов с параметрами и обновление параметров визита.
 */
public interface VisitManagerApi {

    /**
     * Создать визит по списку услуг.
     *
     * @param branchId отделение
     * @param entryPointId точка регистрации
     * @param serviceIds идентификаторы услуг
     * @param printTicket печатать талон
     * @param segmentationRuleId опциональное правило сегментации
     * @param headers дополнительные заголовки (без секретов)
     * @param sourceMessageId id входного сообщения
     * @param correlationId correlation id
     * @param idempotencyKey idempotency key
     * @return нормализованный результат вызова
     */
    default Map<String, Object> createVisit(String branchId,
                                    String entryPointId,
                                    List<String> serviceIds,
                                    boolean printTicket,
                                    String segmentationRuleId,
                                    Map<String, String> headers,
                                    String sourceMessageId,
                                    String correlationId,
                                    String idempotencyKey) {
        return createVisit("default", branchId, entryPointId, serviceIds, printTicket, segmentationRuleId,
                headers, sourceMessageId, correlationId, idempotencyKey);
    }


    /**
     * Создать визит по списку услуг в выбранном target-контуре.
     *
     * <p>На текущем этапе target используется как расширяемый параметр для S6 (multi-target).
     */
    Map<String, Object> createVisit(String target,
                                    String branchId,
                                    String entryPointId,
                                    List<String> serviceIds,
                                    boolean printTicket,
                                    String segmentationRuleId,
                                    Map<String, String> headers,
                                    String sourceMessageId,
                                    String correlationId,
                                    String idempotencyKey);

    /**
     * Создать визит с параметрами клиента.
     */
    Map<String, Object> createVisitWithParameters(String target,
                                                  String branchId,
                                                  String entryPointId,
                                                  List<String> serviceIds,
                                                  Map<String, String> parameters,
                                                  boolean printTicket,
                                                  String segmentationRuleId,
                                                  Map<String, String> headers,
                                                  String sourceMessageId,
                                                  String correlationId,
                                                  String idempotencyKey);

    /**
     * Обновить параметры существующего визита.
     */
    Map<String, Object> updateVisitParameters(String target,
                                              String branchId,
                                              String visitId,
                                              Map<String, String> parameters,
                                              Map<String, String> headers,
                                              String sourceMessageId,
                                              String correlationId,
                                              String idempotencyKey);

    /**
     * Получить каталог услуг отделения VisitManager.
     */
    Map<String, Object> getServicesCatalog(String target, String branchId);

    /**
     * Универсальный прокси-вызов REST endpoint VisitManager через типизированный посредник.
     *
     * <p>Используется в сценариях, где flow требуется вызвать endpoint, для которого ещё не
     * выделен отдельный typed-метод API.
     */
    Map<String, Object> callEndpoint(String target,
                                     String method,
                                     String path,
                                     Object body,
                                     Map<String, String> headers,
                                     String sourceMessageId,
                                     String correlationId,
                                     String idempotencyKey);
}
