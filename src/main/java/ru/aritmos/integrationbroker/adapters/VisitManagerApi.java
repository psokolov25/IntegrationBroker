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
     * Получить состояние отделения (ManagementController).
     */
    Map<String, Object> getBranchState(String target, String branchId,
                                       Map<String, String> headers,
                                       String sourceMessageId,
                                       String correlationId,
                                       String idempotencyKey);

    /**
     * Получить карту всех отделений (ManagementController).
     */
    Map<String, Object> getBranchesState(String target,
                                         String userName,
                                         Map<String, String> headers,
                                         String sourceMessageId,
                                         String correlationId,
                                         String idempotencyKey);

    /**
     * Получить упрощённую сводку отделений (ManagementController).
     */
    Map<String, Object> getBranchesTiny(String target,
                                        Map<String, String> headers,
                                        String sourceMessageId,
                                        String correlationId,
                                        String idempotencyKey);

    /**
     * Вызвать следующего посетителя в сервисной точке (ServicePointController).
     */
    Map<String, Object> callNextVisit(String target,
                                      String branchId,
                                      String servicePointId,
                                      boolean autoCallEnabled,
                                      Map<String, String> headers,
                                      String sourceMessageId,
                                      String correlationId,
                                      String idempotencyKey);

    /**
     * Отложить текущий визит в сервисной точке (ServicePointController).
     */
    Map<String, Object> postponeCurrentVisit(String target,
                                             String branchId,
                                             String servicePointId,
                                             Map<String, String> headers,
                                             String sourceMessageId,
                                             String correlationId,
                                             String idempotencyKey);


    /**
     * Вход сотрудника в режим обслуживания (ServicePointController /enter).
     */
    Map<String, Object> enterServicePointMode(String target,
                                              String branchId,
                                              Map<String, Object> query,
                                              Map<String, String> headers,
                                              String sourceMessageId,
                                              String correlationId,
                                              String idempotencyKey);

    /**
     * Выход сотрудника из режима обслуживания (ServicePointController /exit).
     */
    Map<String, Object> exitServicePointMode(String target,
                                             String branchId,
                                             Map<String, Object> query,
                                             Map<String, String> headers,
                                             String sourceMessageId,
                                             String correlationId,
                                             String idempotencyKey);

    /**
     * Включить авто-вызов в сервисной точке (ServicePointController).
     */
    Map<String, Object> startAutoCall(String target,
                                      String branchId,
                                      String servicePointId,
                                      Map<String, String> headers,
                                      String sourceMessageId,
                                      String correlationId,
                                      String idempotencyKey);

    /**
     * Выключить авто-вызов в сервисной точке (ServicePointController).
     */
    Map<String, Object> cancelAutoCall(String target,
                                       String branchId,
                                       String servicePointId,
                                       Map<String, String> headers,
                                       String sourceMessageId,
                                       String correlationId,
                                       String idempotencyKey);


    /**
     * Создать виртуальный визит (без печати) в сервисной точке.
     */
    Map<String, Object> createVirtualVisit(String target,
                                           String branchId,
                                           String servicePointId,
                                           List<String> serviceIds,
                                           Map<String, String> headers,
                                           String sourceMessageId,
                                           String correlationId,
                                           String idempotencyKey);

    /**
     * Создать визит через конкретный принтер по списку услуг.
     */
    Map<String, Object> createVisitOnPrinterWithServices(String target,
                                                         String branchId,
                                                         String printerId,
                                                         List<String> serviceIds,
                                                         boolean printTicket,
                                                         String segmentationRuleId,
                                                         Map<String, String> headers,
                                                         String sourceMessageId,
                                                         String correlationId,
                                                         String idempotencyKey);

    /**
     * Создать визит через конкретный принтер с параметрами.
     */
    Map<String, Object> createVisitOnPrinterWithParameters(String target,
                                                           String branchId,
                                                           String printerId,
                                                           List<String> serviceIds,
                                                           Map<String, String> parameters,
                                                           boolean printTicket,
                                                           String segmentationRuleId,
                                                           Map<String, String> headers,
                                                           String sourceMessageId,
                                                           String correlationId,
                                                           String idempotencyKey);

    /**
     * Упрощённый createVirtualVisit (target=default, без source/idempotency).
     */
    default Map<String, Object> createVirtualVisit(String branchId,
                                                   String servicePointId,
                                                   List<String> serviceIds,
                                                   String correlationId) {
        return createVirtualVisit("default", branchId, servicePointId, serviceIds, Map.of(), null, correlationId, null);
    }

    /**
     * Упрощённый getBranchState.
     */
    default Map<String, Object> getBranchState(String target, String branchId, String correlationId) {
        return getBranchState(target, branchId, Map.of(), null, correlationId, null);
    }

    /**
     * Упрощённый getBranchesState.
     */
    default Map<String, Object> getBranchesState(String target, String userName, String correlationId) {
        return getBranchesState(target, userName, Map.of(), null, correlationId, null);
    }

    /**
     * Упрощённый getBranchesTiny.
     */
    default Map<String, Object> getBranchesTiny(String target, String correlationId) {
        return getBranchesTiny(target, Map.of(), null, correlationId, null);
    }

    /**
     * Упрощённый callNextVisit.
     */
    default Map<String, Object> callNextVisit(String target,
                                              String branchId,
                                              String servicePointId,
                                              boolean autoCallEnabled,
                                              String correlationId) {
        return callNextVisit(target, branchId, servicePointId, autoCallEnabled, Map.of(), null, correlationId, null);
    }

    /**
     * Упрощённый postponeCurrentVisit.
     */
    default Map<String, Object> postponeCurrentVisit(String target,
                                                     String branchId,
                                                     String servicePointId,
                                                     String correlationId) {
        return postponeCurrentVisit(target, branchId, servicePointId, Map.of(), null, correlationId, null);
    }

    /**
     * Упрощённый enterServicePointMode.
     */
    default Map<String, Object> enterServicePointMode(String target,
                                                      String branchId,
                                                      Map<String, Object> query,
                                                      String correlationId) {
        return enterServicePointMode(target, branchId, query, Map.of(), null, correlationId, null);
    }

    /**
     * Упрощённый exitServicePointMode.
     */
    default Map<String, Object> exitServicePointMode(String target,
                                                     String branchId,
                                                     Map<String, Object> query,
                                                     String correlationId) {
        return exitServicePointMode(target, branchId, query, Map.of(), null, correlationId, null);
    }

    /**
     * Упрощённый startAutoCall.
     */
    default Map<String, Object> startAutoCall(String target,
                                              String branchId,
                                              String servicePointId,
                                              String correlationId) {
        return startAutoCall(target, branchId, servicePointId, Map.of(), null, correlationId, null);
    }

    /**
     * Упрощённый cancelAutoCall.
     */
    default Map<String, Object> cancelAutoCall(String target,
                                               String branchId,
                                               String servicePointId,
                                               String correlationId) {
        return cancelAutoCall(target, branchId, servicePointId, Map.of(), null, correlationId, null);
    }


    private static Map<String, String> sidHeaders(String sid) {
        return (sid == null || sid.isBlank()) ? Map.of() : Map.of("Cookie", "sid=" + sid);
    }

    private static Map<String, String> sidAndHeaders(String sid, Map<String, String> headers) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (headers != null && !headers.isEmpty()) {
            out.putAll(headers);
        }
        Map<String, String> sidH = sidHeaders(sid);
        if (sidH.isEmpty()) {
            return out;
        }
        String cookie = sidH.get("Cookie");

        String cookieKey = "Cookie";
        String existing = null;
        for (Map.Entry<String, String> e : out.entrySet()) {
            if (e.getKey() != null && "cookie".equalsIgnoreCase(e.getKey())) {
                cookieKey = e.getKey();
                existing = e.getValue();
                break;
            }
        }

        if (existing == null || existing.isBlank()) {
            out.put(cookieKey, cookie);
        } else if (!containsSidCookie(existing)) {
            out.put(cookieKey, existing + "; " + cookie);
        }
        return out;
    }

    private static boolean containsSidCookie(String cookieHeaderValue) {
        if (cookieHeaderValue == null || cookieHeaderValue.isBlank()) {
            return false;
        }
        String[] parts = cookieHeaderValue.split(";");
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String token = part.trim();
            if (token.regionMatches(true, 0, "sid=", 0, 4)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Вход сотрудника в режим обслуживания с cookie sid.
     */
    default Map<String, Object> enterServicePointModeWithSid(String target,
                                                             String branchId,
                                                             Map<String, Object> query,
                                                             String sid,
                                                             String correlationId) {
        Map<String, String> headers = sidHeaders(sid);
        return enterServicePointMode(target, branchId, query, headers, null, correlationId, null);
    }

    /**
     * Выход сотрудника из режима обслуживания с cookie sid.
     */
    default Map<String, Object> exitServicePointModeWithSid(String target,
                                                            String branchId,
                                                            Map<String, Object> query,
                                                            String sid,
                                                            String correlationId) {
        Map<String, String> headers = sidHeaders(sid);
        return exitServicePointMode(target, branchId, query, headers, null, correlationId, null);
    }

    /**
     * Типовой enter по mode + autoCall c cookie sid.
     */
    default Map<String, Object> enterServicePointModeWithSid(String target,
                                                             String branchId,
                                                             String mode,
                                                             Boolean autoCallEnabled,
                                                             String sid,
                                                             String correlationId) {
        Map<String, Object> query = new java.util.HashMap<>();
        if (mode != null && !mode.isBlank()) {
            query.put("mode", mode);
        }
        if (autoCallEnabled != null) {
            query.put("isAutoCallEnabled", autoCallEnabled);
        }
        return enterServicePointModeWithSid(target, branchId, query, sid, correlationId);
    }

    /**
     * Типовой forced-exit c reason и cookie sid.
     */
    default Map<String, Object> exitServicePointModeWithSid(String target,
                                                            String branchId,
                                                            Boolean forced,
                                                            String reason,
                                                            String sid,
                                                            String correlationId) {
        Map<String, Object> query = new java.util.HashMap<>();
        if (forced != null) {
            query.put("isForced", forced);
        }
        if (reason != null && !reason.isBlank()) {
            query.put("reason", reason);
        }
        return exitServicePointModeWithSid(target, branchId, query, sid, correlationId);
    }

    /**
     * Вызвать следующего посетителя с cookie sid.
     */
    default Map<String, Object> callNextVisitWithSid(String target,
                                                     String branchId,
                                                     String servicePointId,
                                                     boolean autoCallEnabled,
                                                     String sid,
                                                     String correlationId) {
        Map<String, String> headers = sidHeaders(sid);
        return callNextVisit(target, branchId, servicePointId, autoCallEnabled, headers, null, correlationId, null);
    }

    /**
     * Создать виртуальный визит с cookie sid.
     */
    default Map<String, Object> createVirtualVisitWithSid(String target,
                                                          String branchId,
                                                          String servicePointId,
                                                          List<String> serviceIds,
                                                          String sid,
                                                          String correlationId) {
        Map<String, String> headers = sidHeaders(sid);
        return createVirtualVisit(target, branchId, servicePointId, serviceIds, headers, null, correlationId, null);
    }

    /**
     * Создать визит на принтере по услугам с cookie sid.
     */
    default Map<String, Object> createVisitOnPrinterWithServicesAndSid(String target,
                                                                       String branchId,
                                                                       String printerId,
                                                                       List<String> serviceIds,
                                                                       boolean printTicket,
                                                                       String segmentationRuleId,
                                                                       String sid,
                                                                       String correlationId) {
        Map<String, String> headers = sidHeaders(sid);
        return createVisitOnPrinterWithServices(target,
                branchId,
                printerId,
                serviceIds,
                printTicket,
                segmentationRuleId,
                headers,
                null,
                correlationId,
                null);
    }


    /**
     * Отложить текущий визит с cookie sid.
     */
    default Map<String, Object> postponeCurrentVisitWithSid(String target,
                                                            String branchId,
                                                            String servicePointId,
                                                            String sid,
                                                            String correlationId) {
        return postponeCurrentVisit(target, branchId, servicePointId, sidHeaders(sid), null, correlationId, null);
    }

    /**
     * Включить авто-вызов с cookie sid.
     */
    default Map<String, Object> startAutoCallWithSid(String target,
                                                     String branchId,
                                                     String servicePointId,
                                                     String sid,
                                                     String correlationId) {
        return startAutoCall(target, branchId, servicePointId, sidHeaders(sid), null, correlationId, null);
    }

    /**
     * Выключить авто-вызов с cookie sid.
     */
    default Map<String, Object> cancelAutoCallWithSid(String target,
                                                      String branchId,
                                                      String servicePointId,
                                                      String sid,
                                                      String correlationId) {
        return cancelAutoCall(target, branchId, servicePointId, sidHeaders(sid), null, correlationId, null);
    }

    /**
     * Создать визит на принтере c параметрами и cookie sid.
     */
    default Map<String, Object> createVisitOnPrinterWithParametersAndSid(String target,
                                                                         String branchId,
                                                                         String printerId,
                                                                         List<String> serviceIds,
                                                                         Map<String, String> parameters,
                                                                         boolean printTicket,
                                                                         String segmentationRuleId,
                                                                         String sid,
                                                                         String correlationId) {
        return createVisitOnPrinterWithParameters(target,
                branchId,
                printerId,
                serviceIds,
                parameters,
                printTicket,
                segmentationRuleId,
                sidHeaders(sid),
                null,
                correlationId,
                null);
    }


    /**
     * Вход сотрудника в режим обслуживания с cookie sid и дополнительными заголовками.
     */
    default Map<String, Object> enterServicePointModeWithSidAndHeaders(String target,
                                                                        String branchId,
                                                                        Map<String, Object> query,
                                                                        String sid,
                                                                        Map<String, String> headers,
                                                                        String correlationId) {
        return enterServicePointMode(target, branchId, query, sidAndHeaders(sid, headers), null, correlationId, null);
    }

    /**
     * Выход сотрудника из режима обслуживания с cookie sid и дополнительными заголовками.
     */
    default Map<String, Object> exitServicePointModeWithSidAndHeaders(String target,
                                                                       String branchId,
                                                                       Map<String, Object> query,
                                                                       String sid,
                                                                       Map<String, String> headers,
                                                                       String correlationId) {
        return exitServicePointMode(target, branchId, query, sidAndHeaders(sid, headers), null, correlationId, null);
    }

    /**
     * Вызов следующего посетителя с cookie sid и дополнительными заголовками.
     */
    default Map<String, Object> callNextVisitWithSidAndHeaders(String target,
                                                               String branchId,
                                                               String servicePointId,
                                                               boolean autoCallEnabled,
                                                               String sid,
                                                               Map<String, String> headers,
                                                               String correlationId) {
        return callNextVisit(target, branchId, servicePointId, autoCallEnabled, sidAndHeaders(sid, headers), null, correlationId, null);
    }

    /**
     * Виртуальный визит с cookie sid и дополнительными заголовками.
     */
    default Map<String, Object> createVirtualVisitWithSidAndHeaders(String target,
                                                                    String branchId,
                                                                    String servicePointId,
                                                                    List<String> serviceIds,
                                                                    String sid,
                                                                    Map<String, String> headers,
                                                                    String correlationId) {
        return createVirtualVisit(target, branchId, servicePointId, serviceIds, sidAndHeaders(sid, headers), null, correlationId, null);
    }

    /**
     * Визит на принтере (services) с cookie sid и дополнительными заголовками.
     */
    default Map<String, Object> createVisitOnPrinterWithServicesAndSidAndHeaders(String target,
                                                                                  String branchId,
                                                                                  String printerId,
                                                                                  List<String> serviceIds,
                                                                                  boolean printTicket,
                                                                                  String segmentationRuleId,
                                                                                  String sid,
                                                                                  Map<String, String> headers,
                                                                                  String correlationId) {
        return createVisitOnPrinterWithServices(target,
                branchId,
                printerId,
                serviceIds,
                printTicket,
                segmentationRuleId,
                sidAndHeaders(sid, headers),
                null,
                correlationId,
                null);
    }

    /**
     * Визит на принтере (parameters) с cookie sid и дополнительными заголовками.
     */
    default Map<String, Object> createVisitOnPrinterWithParametersAndSidAndHeaders(String target,
                                                                                    String branchId,
                                                                                    String printerId,
                                                                                    List<String> serviceIds,
                                                                                    Map<String, String> parameters,
                                                                                    boolean printTicket,
                                                                                    String segmentationRuleId,
                                                                                    String sid,
                                                                                    Map<String, String> headers,
                                                                                    String correlationId) {
        return createVisitOnPrinterWithParameters(target,
                branchId,
                printerId,
                serviceIds,
                parameters,
                printTicket,
                segmentationRuleId,
                sidAndHeaders(sid, headers),
                null,
                correlationId,
                null);
    }


    /**
     * Отложить визит с sid и дополнительными заголовками.
     */
    default Map<String, Object> postponeCurrentVisitWithSidAndHeaders(String target,
                                                                      String branchId,
                                                                      String servicePointId,
                                                                      String sid,
                                                                      Map<String, String> headers,
                                                                      String correlationId) {
        return postponeCurrentVisit(target, branchId, servicePointId, sidAndHeaders(sid, headers), null, correlationId, null);
    }

    /**
     * Включить авто-вызов с sid и дополнительными заголовками.
     */
    default Map<String, Object> startAutoCallWithSidAndHeaders(String target,
                                                               String branchId,
                                                               String servicePointId,
                                                               String sid,
                                                               Map<String, String> headers,
                                                               String correlationId) {
        return startAutoCall(target, branchId, servicePointId, sidAndHeaders(sid, headers), null, correlationId, null);
    }

    /**
     * Выключить авто-вызов с sid и дополнительными заголовками.
     */
    default Map<String, Object> cancelAutoCallWithSidAndHeaders(String target,
                                                                String branchId,
                                                                String servicePointId,
                                                                String sid,
                                                                Map<String, String> headers,
                                                                String correlationId) {
        return cancelAutoCall(target, branchId, servicePointId, sidAndHeaders(sid, headers), null, correlationId, null);
    }

    /**
     * Получить состояние отделения с дополнительными заголовками.
     */
    default Map<String, Object> getBranchStateWithHeaders(String target,
                                                          String branchId,
                                                          Map<String, String> headers,
                                                          String correlationId) {
        return getBranchState(target, branchId, headers, null, correlationId, null);
    }

    /**
     * Получить карту отделений с дополнительными заголовками.
     */
    default Map<String, Object> getBranchesStateWithHeaders(String target,
                                                            String userName,
                                                            Map<String, String> headers,
                                                            String correlationId) {
        return getBranchesState(target, userName, headers, null, correlationId, null);
    }

    /**
     * Получить tiny-карту отделений с дополнительными заголовками.
     */
    default Map<String, Object> getBranchesTinyWithHeaders(String target,
                                                           Map<String, String> headers,
                                                           String correlationId) {
        return getBranchesTiny(target, headers, null, correlationId, null);
    }


    /**
     * Вход в сервисную точку с sid+headers и target=default.
     */
    default Map<String, Object> enterServicePointModeWithSidAndHeaders(String branchId,
                                                                        Map<String, Object> query,
                                                                        String sid,
                                                                        Map<String, String> headers,
                                                                        String correlationId) {
        return enterServicePointModeWithSidAndHeaders("default", branchId, query, sid, headers, correlationId);
    }

    /**
     * Выход из сервисной точки с sid+headers и target=default.
     */
    default Map<String, Object> exitServicePointModeWithSidAndHeaders(String branchId,
                                                                       Map<String, Object> query,
                                                                       String sid,
                                                                       Map<String, String> headers,
                                                                       String correlationId) {
        return exitServicePointModeWithSidAndHeaders("default", branchId, query, sid, headers, correlationId);
    }

    /**
     * Вызов следующего клиента с sid+headers и target=default.
     */
    default Map<String, Object> callNextVisitWithSidAndHeaders(String branchId,
                                                               String servicePointId,
                                                               boolean autoCallEnabled,
                                                               String sid,
                                                               Map<String, String> headers,
                                                               String correlationId) {
        return callNextVisitWithSidAndHeaders("default", branchId, servicePointId, autoCallEnabled, sid, headers, correlationId);
    }

    /**
     * Виртуальный визит с sid+headers и target=default.
     */
    default Map<String, Object> createVirtualVisitWithSidAndHeaders(String branchId,
                                                                    String servicePointId,
                                                                    List<String> serviceIds,
                                                                    String sid,
                                                                    Map<String, String> headers,
                                                                    String correlationId) {
        return createVirtualVisitWithSidAndHeaders("default", branchId, servicePointId, serviceIds, sid, headers, correlationId);
    }

    /**
     * Принтерный визит (services) с sid+headers и target=default.
     */
    default Map<String, Object> createVisitOnPrinterWithServicesAndSidAndHeaders(String branchId,
                                                                                  String printerId,
                                                                                  List<String> serviceIds,
                                                                                  boolean printTicket,
                                                                                  String segmentationRuleId,
                                                                                  String sid,
                                                                                  Map<String, String> headers,
                                                                                  String correlationId) {
        return createVisitOnPrinterWithServicesAndSidAndHeaders("default", branchId, printerId, serviceIds, printTicket,
                segmentationRuleId, sid, headers, correlationId);
    }

    /**
     * Принтерный визит (parameters) с sid+headers и target=default.
     */
    default Map<String, Object> createVisitOnPrinterWithParametersAndSidAndHeaders(String branchId,
                                                                                    String printerId,
                                                                                    List<String> serviceIds,
                                                                                    Map<String, String> parameters,
                                                                                    boolean printTicket,
                                                                                    String segmentationRuleId,
                                                                                    String sid,
                                                                                    Map<String, String> headers,
                                                                                    String correlationId) {
        return createVisitOnPrinterWithParametersAndSidAndHeaders("default", branchId, printerId, serviceIds,
                parameters, printTicket, segmentationRuleId, sid, headers, correlationId);
    }

    /**
     * Включить авто-вызов с sid+headers и target=default.
     */
    default Map<String, Object> startAutoCallWithSidAndHeaders(String branchId,
                                                               String servicePointId,
                                                               String sid,
                                                               Map<String, String> headers,
                                                               String correlationId) {
        return startAutoCallWithSidAndHeaders("default", branchId, servicePointId, sid, headers, correlationId);
    }

    /**
     * Выключить авто-вызов с sid+headers и target=default.
     */
    default Map<String, Object> cancelAutoCallWithSidAndHeaders(String branchId,
                                                                String servicePointId,
                                                                String sid,
                                                                Map<String, String> headers,
                                                                String correlationId) {
        return cancelAutoCallWithSidAndHeaders("default", branchId, servicePointId, sid, headers, correlationId);
    }

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
