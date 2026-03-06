# Playbook: интеграция предварительной записи (appointment/booking)

## Назначение
Слой **appointment** используется для сценариев предварительной записи (включая медицинские):

- получение списка записей клиента/пациента;
- получение ближайшей актуальной записи;
- получение доступных слотов;
- бронирование/отмена записи;
- построение первичного **queue plan** для СУО и/или команд для DeviceManager.

Ключевой принцип: слой должен быть **расширяемым** и **отключаемым**.

## Контракты
Основные контракты находятся в пакете:

- `ru.aritmos.integrationbroker.appointment.AppointmentModels`

Ключи для поиска записи передаются как набор `type + value`:

- `BookingKey(type, value, attributes)`

Тип ключа не ограничивается фиксированным списком. Примеры:

- `clientId`, `patientId`
- `phone`, `email`
- `policyNumber`, `contractNumber`
- `requestId`, `externalBookingId`
- `ticketNumber`

## Конфигурация
Секция runtime-config:

```json
"appointment": {
  "enabled": true,
  "profile": "GENERIC",
  "connectorId": "appointmentGeneric",
  "settings": {}
}
```

- `enabled` — включает/выключает слой.
- `profile` — архитектурный профиль интеграции.
- `connectorId` — ссылка на `restConnectors.<id>`.
- `settings` — расширяемые параметры конкретного вендора (URL path, схемы, поля, особенности статусов и т.п.).

## Профили
Поддержаны профили (архитектурные):

- `EMIAS_APPOINTMENT`
- `MEDTOCHKA_LIKE`
- `PRODOCTOROV_LIKE`
- `YCLIENTS_LIKE`
- `NAPOPRAVKU_LIKE`
- `CUSTOM_CONNECTOR`
- `GENERIC`

В текущей версии:
- `GENERIC` — детерминированная заглушка (для разработки flow и демо).
- `CUSTOM_CONNECTOR` — каркас под визуально-конфигурируемый клиент (`restConnectors` + шаблоны).
- остальные — `NOT_IMPLEMENTED` (каркас, чтобы контракт был стабилен).

Особенности `GENERIC` профиля в текущей реализации:
- `getAppointments` возвращает несколько демо-записей и применяет фильтр периода `from/to`, если он передан.
- `getNearestAppointment` выбирает ближайшую `CONFIRMED` запись.
- `getAvailableSlots` возвращает 3 демо-слота и также учитывает фильтр `from/to`.
- `context.branchId` и `context.serviceCode` используются для enrichment демо-ответов.

## Использование в Groovy-flow
Alias доступен как `appointment`.

Для кратких flow есть helper `appointment.getAppointmentsByKeys(keys, meta)` для быстрого получения списка записей без ручной сборки request.
Также доступен helper `appointment.buildQueuePlanSimple(appointmentId, keys, context, meta)` для ускоренной сборки queue plan.
Для быстрых операций записи доступны `appointment.bookSlotSimple(slotId, serviceCode, keys, context, meta)` и `appointment.cancelAppointmentSimple(appointmentId, reason, context, meta)`.
Для краткого запроса слотов есть `appointment.getAvailableSlotsSimple(serviceCode, locationId, from, to, context, meta)` (поля `from/to` передаются как `Instant`).
Для сценария nearest-by-client доступен helper `appointment.getNearestAppointmentSimple(clientId, branchId, meta)`.
Для списка записей по клиенту есть helper `appointment.getAppointmentsByClientId(clientId, context, meta)`.
Для списка записей по клиенту за период доступен `appointment.getAppointmentsByClientIdAndPeriod(clientId, from, to, context, meta)`.

Пример: получить ближайшую запись и построить план обслуживания:

```groovy
// clientId может приходить из identity/CRM/МИС.
def nearest = appointment.getNearestAppointment([
  keys: [[type:'clientId', value: 'CLIENT-001']],
  context: [branchId: meta.branchId]
], meta)

if (nearest.success() && nearest.result() != null) {
  def plan = appointment.buildQueuePlan([
    appointmentId: nearest.result().appointmentId(),
    keys: [[type:'clientId', value: 'CLIENT-001']],
    context: [branchId: meta.branchId, segment: (meta.segment ?: 'DEFAULT')]
  ], meta)
  output.queuePlan = plan.result()
}
```

## Кастомизированный client для внешних коннекторов (визуальная сборка запросов)
Для проектов, где интегратор не хочет писать Java-клиент сразу, допускается сценарий **customized client** на базе `restConnectors` + Groovy.

### Цель
- визуально собирать HTTP-запросы к внешним службам через Runtime Config / Workbench;
- переиспользовать единый auth/retry/circuit-breaker слой `restConnectors`;
- постепенно перейти от low-code схемы к типизированному `AppointmentClient`, когда контракт стабилизирован.

### Рекомендованный шаблон
1) Создать `restConnectors.<id>` для внешней системы (baseUrl, auth, timeouts, retryPolicy).
2) В `appointment.settings` описать map шаблонов операций (`getAppointments`, `getNearestAppointment`, `getAvailableSlots`, `bookSlot`, `cancelAppointment`).
3) В flow использовать один Groovy-скрипт-трансформер:
   - вход `AppointmentModels.*Request` -> map запроса в формат внешнего API;
   - выход внешнего ответа -> `AppointmentModels.AppointmentOutcome`.
4) После стабилизации вынести трансформер в отдельный Java `AppointmentClient` профиль.

Пример структуры `settings` (минимум для визуального конфигурирования):

```json
"appointment": {
  "enabled": true,
  "profile": "CUSTOM_CONNECTOR",
  "connectorId": "appointmentVendorA",
  "settings": {
    "customClient": {
      "enabled": true,
      "operations": {
        "getAppointments": {
          "method": "POST",
          "path": "/api/v1/appointments/search",
          "requestTemplate": {
            "client": "${keys.clientId}",
            "from": "${from}",
            "to": "${to}",
            "branch": "${context.branchId}"
          }
        },
        "bookSlot": {
          "method": "POST",
          "path": "/api/v1/appointments/book",
          "requestTemplate": {
            "slot": "${slotId}",
            "service": "${serviceCode}",
            "client": "${keys.clientId}"
          }
        }
      }
    }
  }
}
```

### Визуальная формировка запросов (Workbench)
- В Runtime Config UI хранить шаблоны request/response в `appointment.settings.customClient.operations`.
- Для каждого operation задавать:
  - `method`, `path`, `queryTemplate`, `headersTemplate`, `requestTemplate`;
  - `responseMapping` (где взять `appointmentId`, `startAt`, `status`, `slots[]` и т.д.).
- Перед сохранением обязательно запускать dry-run и проверять diff.

### Функционал GUI для кастомизации адаптеров и коннекторов (расширенный минимум)
Чтобы интеграторы могли работать без Java-разработки на ранних этапах проекта, в Workbench рекомендуется явно поддержать следующий GUI-функционал:

1) **Каталог внешних систем**
   - визуальное создание карточки интеграции: `systemId`, `displayName`, `owner`, `criticality`, `environment`;
   - привязка к `connectorId` и профилю адаптера (`appointment/crm/medical/...`);
   - быстрый переход к health, журналу ошибок и последним outbound-вызовам.

2) **Конструктор REST-коннектора**
   - редактирование `baseUrl`, auth-mode, timeout/retry/circuit-breaker;
   - переключение mock/real endpoint для поэтапного запуска;
   - шаблоны заголовков по умолчанию (`X-Correlation-Id`, `X-Request-Id`, `Idempotency-Key`).

3) **Конструктор операций адаптера**
   - визуальная сборка операций уровня домена (`getNearestAppointment`, `bookSlot`, `cancelAppointment`);
   - map полей `AppointmentModels.*Request` -> vendor payload;
   - map vendor response/errors -> `AppointmentOutcome` с подсветкой обязательных полей.

4) **Валидация и безопасная публикация**
   - pre-save проверки: обязательные поля, конфликт route/path, наличие `responseMapping`;
   - dry-run с эталонными payload и сравнением diff до/после изменения;
   - публикация через версионность (`draft` -> `published`) с возможностью rollback на предыдущую ревизию.

5) **Наблюдаемость и поддержка эксплуатации**
   - timeline вызова: request-template -> финальный request -> response -> mapped outcome;
   - поиск по `correlationId/requestId/vendorTraceId`;
   - экспорт проблемного кейса в JSON для передачи в команду внешней системы.

### Ограничения
- Это **переходный** путь для ускорения внедрения; production-критичные интеграции лучше переводить в типизированный Java-клиент.
- Шаблоны не должны содержать секреты: секреты только в `restConnectors.<id>.auth`.
- Любые визуальные шаблоны должны быть идемпотентны и трассируемы через `correlationId`.

### GUI user-journey (рекомендованный сценарий работы интегратора)
1) Создать внешнюю систему в каталоге и привязать `connectorId`.
2) Настроить auth/retry/timeout в коннекторе и пройти health-check.
3) Собрать операции адаптера через визуальные шаблоны request/response.
4) Выполнить dry-run на 3 кейсах: happy-path, empty result, retryable error.
5) Проверить trace и корректность маппинга в `AppointmentOutcome`.
6) Опубликовать версию в `PUBLISHED`, зафиксировать `revision` и комментарий.
7) Наблюдать первые вызовы в timeline, при отклонениях сделать rollback.

### Матрица обязательного GUI-функционала (DoD для кастомного коннектора)
| Блок GUI | Обязательные элементы | Критерий готовности |
|---|---|---|
| Каталог внешней системы | `systemId`, owner, environment, criticality | карточка сохранена и связана с `connectorId` |
| Коннектор | auth-policy, timeout, retry, circuit-breaker, default headers | dry-run/health-check успешен |
| Операции | requestTemplate, responseMapping, errorMapping | каждая операция валидируется без ошибок схемы |
| Публикация | draft/published, diff, rollback | есть опубликованная версия и откат до предыдущей |
| Эксплуатация | trace-view, correlation search, incident export | инцидент экспортируется в sanitized JSON |

### RBAC для GUI-кастомизации (минимум)
- `IB_ADMIN`: полный доступ (create/update/publish/rollback).
- `IB_OPERATOR`: редактирование и dry-run без publish/rollback.
- `IB_AUDITOR`/read-only: просмотр конфигураций, ревизий и trace без изменений.

## Реализация кастомных REST API для внешних служб клиентов
Ниже — практический blueprint для команд внедрения, когда нужно интегрировать нестандартный REST API внешней службы (страховая, CRM, колл-центр, кастомный booking backend) без немедленной разработки полноценного Java-клиента.

### 1) Канонический контракт IB -> внешний REST
Рекомендуется всегда разделять:
- **внутренний контракт IB** (`AppointmentModels.*Request/*Outcome`);
- **внешний контракт вендора** (vendor DTO, path/query/body/headers).

Минимальная схема операции:
1) получить `AppointmentModels.*Request`;
2) собрать `RestOutboundRequest` по шаблонам из `appointment.settings.customClient.operations.<operation>`;
3) отправить через `restConnectors.<connectorId>`;
4) промаппить ответ в `AppointmentOutcome` (`OK/ERROR/NOT_IMPLEMENTED`);
5) приложить `details` без секретов (status, code, retriable, vendorTraceId).

### 2) Рекомендуемая структура operation-шаблона
Для каждой операции (`getAppointments`, `bookSlot`, и т.д.) хранить:

```json
{
  "method": "POST",
  "path": "/api/v2/appointments/search",
  "queryTemplate": {
    "branch": "${context.branchId}",
    "limit": "${context.limit:50}"
  },
  "headersTemplate": {
    "X-Correlation-Id": "${meta.correlationId}",
    "X-Request-Id": "${meta.requestId}",
    "X-Client-Channel": "${context.channel:web}"
  },
  "requestTemplate": {
    "client": {
      "id": "${keys.clientId}",
      "phone": "${keys.phone}"
    },
    "period": {
      "from": "${from}",
      "to": "${to}"
    },
    "service": "${context.serviceCode}"
  },
  "responseMapping": {
    "itemsPath": "$.data.items[*]",
    "appointmentId": "$.id",
    "startAt": "$.start",
    "endAt": "$.end",
    "serviceCode": "$.service.code",
    "specialistName": "$.doctor.name",
    "room": "$.cabinet",
    "status": "$.status"
  },
  "errorMapping": {
    "400": "ERROR",
    "404": "OK_EMPTY",
    "409": "ERROR_CONFLICT",
    "429": "ERROR_RETRYABLE",
    "500": "ERROR_RETRYABLE"
  }
}
```

### 3) Нормализация статусов и ошибок
Чтобы flow не зависел от конкретного вендора:
- `2xx` -> `success=true`, `code=OK`;
- `404` для search/read-операций -> `success=true`, пустой результат;
- `409` -> `success=false`, `code=ERROR`, `details.reason=CONFLICT`;
- `429/5xx` -> `success=false`, `code=ERROR`, `details.retriable=true`.

Важно: итоговое решение о retry принимает policy коннектора и оркестрация, а не шаблон сам по себе.

### 4) Политика заголовков и трассировки
Во все исходящие вызовы кастомного REST API обязательно прокидывать:
- `X-Correlation-Id`
- `X-Request-Id`
- при наличии: `Idempotency-Key`

Если внешний API возвращает `requestId/traceId`, сохранять его в `AppointmentOutcome.details.vendorTraceId`.

### 5) Визуальная валидация в Workbench
Перед публикацией конфигурации:
1) собрать request из шаблона в режиме dry-run;
2) проверить, что `path/query/headers/body` соответствуют API-документации вендора;
3) убедиться, что секреты не попали в шаблон;
4) прогнать минимум 3 кейса:
   - happy-path;
   - пустой ответ (`404`/`items=[]`);
   - временная ошибка (`429`/`500`).

### 6) Минимальный чеклист production-ready
- есть `connectorId` и валидная auth-конфигурация в `restConnectors`;
- определены `timeout/retry/circuit-breaker`;
- есть `responseMapping` и `errorMapping` для всех используемых операций;
- есть логическая идемпотентность (напр. `bookSlot` по внешнему requestId);
- есть пример payload в `src/main/resources/examples/appointment/*`.


## Паттерн реализации Custom REST клиента (по этапам)
Чтобы снизить риски при внедрении, рекомендован поэтапный rollout:

### Этап A — Discovery
- Зафиксировать операции вендора: search, nearest, slots, book, cancel.
- Зафиксировать SLA/ошибки API (429, 5xx, таймауты).
- Согласовать поля, обязательные для S1 baseline: `appointmentId`, `startAt`, `status`, `serviceCode`.

### Этап B — Visual Prototype
- Собрать шаблоны в `appointment.settings.customClient.operations`.
- Проверить dry-run на реальных payload.
- Подключить минимальный `errorMapping`.

### Этап C — Pilot
- Ограничить профиль на один филиал/tenant.
- Включить расширенное логирование без секретов.
- Замерить: latency, error-rate, retry-rate.

### Этап D — Production
- Уточнить `timeout/retry/circuit-breaker` по фактическим метрикам.
- Добавить алерты на деградацию (`429`, рост `5xx`, рост timeout).
- Подготовить план отката на `GENERIC`/fallback flow.

## Versioning и совместимость шаблонов
Рекомендуется в `appointment.settings.customClient` хранить:

```json
{
  "version": "1.0",
  "schema": "appointment-custom-client.v1",
  "operations": { }
}
```

Правила:
- Breaking changes (переименование обязательных полей, изменение формата дат) — только с bump major.
- Non-breaking changes (добавление optional полей) — minor.
- Для каждого изменения версии — migration note в release notes.

## Observability для кастомных REST интеграций
Минимальный набор операционных полей в логах/метриках:
- `operation` (`getAppointments/bookSlot/...`)
- `connectorId`
- `correlationId`
- `requestId`
- `httpStatus`
- `durationMs`
- `retriable`
- `vendorTraceId` (если доступен)

Важно: request/response body логировать только в sanitized виде.

## Anti-patterns (чего избегать)
- Хранить секреты в `requestTemplate/headersTemplate`.
- Смешивать бизнес-правила клиента в Java-ядре вместо flow/groovy.
- Делать «магический» retry в шаблоне без учёта idempotency.
- Привязывать flow к vendor-специфичным статусам без нормализации.

## Definition of Done для кастомного клиента
Перед приёмкой убедиться, что:
1) Конфиг проходит dry-run validation.
2) Есть happy-path + empty + retryable сценарии в тестовых примерах.
3) Ошибки нормализуются в `AppointmentOutcome`.
4) Корреляция (`X-Correlation-Id`, `X-Request-Id`) прокидывается всегда.
5) Документация обновлена (playbook + пример конфигурации + runbook заметка).


## Практические шаблоны для flow/groovy (custom REST)
Ниже — минимальные заготовки, чтобы быстрее запускать интеграции на `CUSTOM_CONNECTOR`.

### Groovy: сборка payload для `getAppointments`
```groovy
// input: request (AppointmentModels.GetAppointmentsRequest), meta
Map<String, Object> keysByType = [:]
(request.keys ?: []).each { k ->
  if (k?.type && k?.value && !keysByType.containsKey(k.type)) {
    keysByType[k.type] = k.value
  }
}

return [
  client : [
    id    : keysByType.clientId,
    phone : keysByType.phone
  ],
  period : [
    from  : request.from?.toString(),
    to    : request.to?.toString()
  ],
  branch : request.context?.branchId,
  service: request.context?.serviceCode,
  trace  : [
    correlationId: meta.correlationId,
    requestId    : meta.requestId
  ]
]
```

### Groovy: нормализация vendor-response -> AppointmentOutcome
```groovy
// input: vendorResponse map

def items = (vendorResponse?.data?.items ?: [])
def result = items.collect { i ->
  [
    appointmentId : i.id,
    startAt       : i.start,
    endAt         : i.end,
    serviceCode   : i.service?.code,
    specialistName: i.doctor?.name,
    room          : i.cabinet,
    status        : i.status,
    attributes    : [source: 'customConnector', vendorTraceId: vendorResponse?.traceId]
  ]
}

return [
  success: true,
  code: 'OK',
  message: '',
  result: result,
  details: [:]
]
```

## Матрица маппинга полей (IB <-> Vendor)
Рекомендуется вести таблицу соответствий для каждой внешней службы:

| IB поле | Vendor поле | Обязательность | Примечание |
|---|---|---|---|
| `keys.clientId` | `client.id` | high | Основной ключ клиента |
| `keys.phone` | `client.phone` | medium | fallback-идентификатор |
| `from` | `period.from` | medium | ISO-8601 UTC |
| `to` | `period.to` | medium | ISO-8601 UTC |
| `context.branchId` | `branch` | high | Для филиальной маршрутизации |
| `context.serviceCode` | `service` | medium | Фильтр/тип приёма |
| `meta.correlationId` | `X-Correlation-Id` | high | Трассировка межсервисных вызовов |
| `meta.requestId` | `X-Request-Id` | high | Запросный идентификатор |

## Runbook: rollback и восстановление
Если custom REST интеграция деградировала:
1) переключить `appointment.profile` на fallback (`GENERIC` или ранее стабильный профиль);
2) оставить `connectorId` без изменений, но отключить `customClient.enabled`;
3) зафиксировать окно инцидента и top-3 причины (timeout/429/5xx);
4) выполнить replay только для безопасных операций (`get*`); `bookSlot/cancel` — только с idempotency ключами;
5) после стабилизации вернуть профиль и включить pilot-режим с ограниченным трафиком.

## Чеклист ревью PR для custom-клиента
- [ ] В PR есть пример `appointment.settings.customClient.operations`.
- [ ] Показано, как формируются `X-Correlation-Id` и `X-Request-Id`.
- [ ] Ошибки 429/5xx помечаются как retriable.
- [ ] Нет секретов в шаблонах/логах/примерных payload.
- [ ] Добавлены/обновлены примеры в `src/main/resources/examples/appointment/*`.

Примеры для стартовой реализации лежат в:
- `src/main/resources/examples/appointment/appointment-custom-client-settings.json`
- `src/main/resources/examples/appointment/appointment-custom-client-response-happy.json`
- `src/main/resources/examples/appointment/appointment-custom-client-response-empty.json`
- `src/main/resources/examples/appointment/appointment-custom-client-response-retryable.json`

## Как добавить новую систему записи
1) Добавить новый профиль в `RuntimeConfigStore.AppointmentProfile`.
2) Реализовать `AppointmentClient` под этот профиль.
3) Зарегистрировать реализацию в `AppointmentClients`.
4) Описать параметры `settings` (какие path/поля/правила статусов нужны).
5) Добавить пример `examples/appointment/*` и demo-flow в `examples/sample-system-config.json`.

## Безопасность
- Секреты авторизации для внешних систем задаются **только** в `restConnectors.*.auth`.
- Секреты нельзя логировать.
- Секреты нельзя сохранять в outbox/DLQ.
