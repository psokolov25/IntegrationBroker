# AGENTS.md — Integration Broker

## 1) Source of truth (зафиксировано для разработки IB)

Дата/время фиксации: **2026-02-27T00:00:00Z (UTC)**.

### VisitManager
- Репозиторий: https://github.com/psokolov25/VisitManager
- Локальный путь: `/workspace/VisitManager`
- Ветка: `dev`
- Ревизия: `45b98889cbe1be241704f0b5470babbc0d716f86`

### DataBus
- Репозиторий: https://github.com/psokolov25/DataBus
- Локальный путь: `/workspace/DataBus`
- Ветка: `dev`
- Ревизия: `f8deeb21156d0a3435792ac7fd945b529fdb9db3`

---

## 2) VisitManager: API и доменные контракты, критичные для IB

> В кодовой базе контроллер называется `EntrypointController` (фактический класс), API base-path: `/entrypoint`.

### 2.1. Контроллеры
- `ru.aritmos.api.EntrypointController` (`@Controller("/entrypoint")`) — регистрация визитов, выбор услуг, параметры визита.
- `ru.aritmos.api.ServicePointController` (`@Controller("/servicepoint")`) — процесс обслуживания в точках.
- `ru.aritmos.api.ManagementController` (`@Controller("/managementinformation")`) — состояние отделений.

### 2.2. Таблица endpoint → назначение → DTO → заголовки/куки → типовые ошибки

#### EntrypointController (ключевые операции для IB)
| Endpoint | Назначение | DTO запроса | DTO ответа | Обязательные заголовки/куки | Типовые ошибки |
|---|---|---|---|---|---|
| `POST /entrypoint/branches/{branchId}/service-points/{servicePointId}/virtual-visits` | Создать виртуальный визит без печати | `List<String> serviceIds` | `Visit` | `Cookie: sid` опционально (идентификация сотрудника) | `404` branch/service not found, `409` duplicate, `500` |
| `POST /entrypoint/branches/{branchId}/entry-points/{entryPointId}/visits` | Создать визит (с талоном/без) | `List<String> serviceIds`, query `printTicket`, `segmentationRuleId` | `Visit` | нет обязательных HTTP-заголовков; контекст auth через security mode | `404`, `409`, `500` |
| `POST /entrypoint/branches/{branchId}/entry-points/{entryPointId}/visits/parameters` | Создать визит с параметрами клиента | `VisitParameters` | `Visit` | аналогично | `404`, `409`, `500` |
| `POST /entrypoint/branches/{branchId}/printers/{printerId}/visits` | Создать визит с печатью через выбранный принтер | `VisitParameters`/список услуг (по методу) + query флаги | `Visit` | `Cookie: sid` (где требуется) | `404`, `409`, `500` |
| `PUT /entrypoint/branches/{branchId}/visits/{visitId}` | Обновить map параметров визита | `Map<String,String>` | `Visit` | без специальных headers | `404`, `500` |
| `GET /entrypoint/branches/{branchId}/services/catalog` | Каталог услуг филиала | — | `List<Service>` | — | `404`, `500` |

#### ServicePointController (операционный цикл обслуживания)
| Endpoint | Назначение | DTO запроса | DTO ответа | Обязательные заголовки/куки | Типовые ошибки |
|---|---|---|---|---|---|
| `POST /servicepoint/branches/{branchId}/enter` | Вход сотрудника в режим обслуживания (по sid) | query параметры режима | `User` | `Cookie: sid` | `404`, `500` |
| `POST /servicepoint/branches/{branchId}/exit` | Выход сотрудника | query `isForced`, `reason` | `User` | `Cookie: sid` | `404`, `500` |
| `POST /servicepoint/branches/{branchId}/servicePoints/{servicePointId}/call` | Вызов следующего визита | query `isAutoCallEnabled` | `Optional<Visit>` | — | `401/404/500/207` |
| `PUT /servicepoint/branches/{branchId}/service-points/{servicePointId}/auto-call/start` | Включить авто-вызов | — | `Optional<ServicePoint>` | — | `207` already enabled, `404`, `500` |
| `PUT /servicepoint/branches/{branchId}/service-points/{servicePointId}/auto-call/cancel` | Выключить авто-вызов | — | `Optional<ServicePoint>` | — | `207` already disabled, `404`, `500` |
| `PUT /servicepoint/branches/{branchId}/servicePoints/{servicePointId}/postpone` | Отложить текущий визит | — | `Visit` | — | `404`, `500` |

#### ManagementController
| Endpoint | Назначение | DTO запроса | DTO ответа | Обязательные заголовки/куки | Типовые ошибки |
|---|---|---|---|---|---|
| `GET /managementinformation/branches/{id}` | Снимок одного отделения | path id | `Branch` | — | `404`, `500` |
| `GET /managementinformation/branches` | Картина всех отделений | query `userName` (optional) | `Map<String, Branch>` | — | `404`, `500` |
| `GET /managementinformation/branches/tiny` | Упрощенная сводка отделений | — | `List<TinyClass>` | — | `404`, `500` |

### 2.3. Где смотреть OpenAPI/DTO в VisitManager
- OpenAPI файл: `/workspace/VisitManager/openapi.yml`
- AsyncAPI: `/workspace/VisitManager/src/main/resources/asyncapi/*`
- Основные DTO для IB:
  - `ru.aritmos.model.visit.Visit`
  - `ru.aritmos.model.VisitParameters`
  - `ru.aritmos.model.Service`
  - `ru.aritmos.model.Branch`
  - `ru.aritmos.model.TinyClass`

### 2.4. Авторизация/таймауты/корреляция (VM)
- Безопасность включаемая, режим через `visitmanager.security.mode` (default: `OPEN`), см. `src/main/resources/application.yml` в VM.
- Для части операций используется `Cookie sid`.
- Таймауты HTTP-клиента VM по умолчанию: connect ~2s, read ~3s (см. VM `micronaut.http.client.*`).
- В явном виде `correlationId` как обязательный header во всех endpoint VM не зафиксирован — **IB обязан добавлять его самостоятельно** (`X-Correlation-Id`, `X-Request-Id`) во внешние вызовы.

---

## 3) DataBus: API, заголовки и событийная модель

### 3.1. Базовые endpoint
Контроллер: `ru.aritmos.controller.DatabusController` с base-path `/databus`.

| Endpoint | Назначение | Важные headers | Тело |
|---|---|---|---|
| `POST /databus/events/types/{type}` | Публикация события + fan-out (Kafka/WebSocket) + опционально ретрансляция | `Service-Destination`, `Send-To-OtherBus`, `Send-Date` (RFC1123), `Service-Sender` | `Object body` |
| `POST /databus/events/types/{type}/route` | Публикация события + маршрутизация в список внешних шин | `Service-Destination`, `Send-Date`, `Service-Sender` | `RouteEvent { dataBusUrls[], body }` |
| `POST /databus/requests/{function}` | Прототип асинхронного request | `Service-Destination`, `Send-To-OtherBus`, `Send-Date`, `Service-Sender` | `Map<String,Object> params` |
| `POST /databus/responses` | Прототип response | `Service-Destination`, `Send-To-OtherBus`, `Send-Date`, `Service-Sender`, `Response-Status`, `Response-Message` | `Response` |

### 3.2. WebSocket
- Endpoint: `ws://<host>/ws/events/{topic}`
- Семантика:
  - topic `all` подписывает на broadcast.
  - Именованные topic получают только свой поток + `all`.

### 3.3. Модель события (минимальный конверт для IB)
Фактическая модель DB `Event`:
- `senderService`
- `eventDate`
- `eventType`
- `params`
- `body`

Канонический конверт IB (нормализация для скриптов/адаптеров):
- `type` ← `eventType`
- `source` ← `senderService`
- `timestamp` ← `eventDate`
- `correlationId` ← из IB envelope/headers
- `payload` ← `body`
- задел под `request/response` (`request`, `response`, `status`, `message`) для будущей совместимости.

### 3.4. Ограничения и нюансы
- `requests/responses` в DataBus присутствуют как прототип (не основной канал).
- Multi-bus поддерживается через `Send-To-OtherBus` и `RouteEvent.dataBusUrls`.
- Размер сообщений явно не ограничен в контроллере, но фактический лимит определяется транспортом (Kafka/WebSocket/прокси) — в IB ограничивать конфигом.

---

## 4) Как IB должен вызывать VM и DB

### 4.1. Рекомендованный паттерн вызовов
- Синхронно в VM: операции, где нужен немедленный результат для шага flow (создать/обновить визит, получить каталог услуг, состояние отделения).
- Асинхронно в DB: события о факте интеграции, уведомления внешних систем, fan-out/маршрутизация.

### 4.2. Надежность
- Идемпотентность inbound: ключ `(sourceSystem, externalMessageId|hash(payload), flow)`.
- Корреляция: всегда формировать и прокидывать `X-Correlation-Id` и `X-Request-Id`; для DB дублировать в `params`/payload envelope.
- Ретраи: экспоненциальный backoff для VM/DB; повторяем только безопасные операции или операции с idempotency key.
- Необрабатываемые сообщения: DLQ/parking-lot + reprocess endpoint.

### 4.3. Актуальные контуры wrapper API в IB (для Groovy/Java orchestration)
```java
public interface VisitManagerApi {
  Map<String,Object> createVisit(String target,
                                 String branchId,
                                 String entryPointId,
                                 List<String> serviceIds,
                                 boolean printTicket,
                                 String segmentationRuleId,
                                 Map<String,String> headers,
                                 String sourceMessageId,
                                 String correlationId,
                                 String idempotencyKey);

  Map<String,Object> createVisitWithParameters(String target,
                                               String branchId,
                                               String entryPointId,
                                               List<String> serviceIds,
                                               Map<String,String> parameters,
                                               boolean printTicket,
                                               String segmentationRuleId,
                                               Map<String,String> headers,
                                               String sourceMessageId,
                                               String correlationId,
                                               String idempotencyKey);

  Map<String,Object> updateVisitParameters(String target,
                                           String branchId,
                                           String visitId,
                                           Map<String,String> parameters,
                                           Map<String,String> headers,
                                           String sourceMessageId,
                                           String correlationId,
                                           String idempotencyKey);
}

public interface DataBusApi {
  Map<String,Object> publishEvent(String target,
                                  String type,
                                  String destination,
                                  Object payload,
                                  Boolean sendToOtherBus,
                                  String sourceMessageId,
                                  String correlationId,
                                  String idempotencyKey);

  Map<String,Object> publishEventRoute(String target,
                                       String destination,
                                       String type,
                                       List<String> dataBusUrls,
                                       Object payload,
                                       String sourceMessageId,
                                       String correlationId,
                                       String idempotencyKey);

  Map<String,Object> sendRequest(String target,
                                 String destination,
                                 String function,
                                 Map<String,Object> params,
                                 Boolean sendToOtherBus,
                                 String sourceMessageId,
                                 String correlationId,
                                 String idempotencyKey);

  Map<String,Object> sendResponse(String target,
                                  String destination,
                                  Integer status,
                                  String message,
                                  Object response,
                                  Boolean sendToOtherBus,
                                  String sourceMessageId,
                                  String correlationId,
                                  String idempotencyKey);
}
```

Groovy usage (пример):
```groovy
def visitRes = visitManager.createVisit(
  targetId,
  branchId,
  entryPointId,
  services,
  true,
  null,
  [:],
  ctx.sourceMessageId,
  ctx.correlationId,
  ctx.idempotencyKey
)

dataBus.publishEvent(targetId, "crm,display", "visit.created",
  [visitId: visitRes.body?.id, ticket: visitRes.body?.ticket],
  ctx.correlationId)
return [status: "OK", visitId: visitRes.body?.id]
```

---

## 5) Процедура обновления AGENTS.md при изменениях VM/DB

1. `git -C /workspace/VisitManager pull --ff-only` и `git -C /workspace/DataBus pull --ff-only`.
2. Снять новые `branch`/`rev-parse HEAD` и обновить секцию Source of truth.
3. Сравнить:
   - VM: `openapi.yml`, контроллеры `EntrypointController`, `ServicePointController`, `ManagementController`, ключевые DTO.
   - DB: `DatabusController`, `Event/Request/Response/RouteEvent`, WebSocket endpoint.
4. Актуализировать таблицы endpoint/DTO/headers/errors.
5. Проверить, что обертки `VisitManagerApi`/`DataBusApi` и примеры Groovy не расходятся с контрактами.
6. Прогнать тесты IB и зафиксировать изменения в PR.

---

## 6) Ограничение области Integration Broker (S1 baseline)

IB остается только интеграционным посредником:
- `core/` — оркестрация flow, идемпотентность, DLQ/outbox, корреляция.
- `adapters/` — коннекторы VM/DB/CRM/MIS/Prebooking/Biometrics/Bus/HTTP.
- `runtime/` — Groovy runtime (validate, dry-run, sandbox context, timeout).
- `examples/` — примеры YAML flow + Groovy scripts для внедрения.

Правило: бизнес-логика клиента (очередь/просрочка/исключения/сегменты) — только в Groovy и YAML flows, не в Java-коде.
