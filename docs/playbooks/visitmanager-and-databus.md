# Создание визита в VisitManager и публикация событий в DataBus

Документ описывает, как Integration Broker должен использоваться в проекте, где:

- **правила сегментации и правила вызова/маршрутизации** реализуются **в VisitManager**;
- Integration Broker отвечает за:
  - получение события, требующего идентификации клиента;
  - обогащение данными (identity/CRM/МИС);
  - подготовку параметров визита (segment, clientId и др.);
  - **создание визита в VisitManager** (REST или событие `VISIT_CREATE` через DataBus).

> Важно: Integration Broker **не** строит план вызова, не выбирает окна/кабинеты и не выполняет сегментацию.
> Он лишь передаёт VisitManager нужные атрибуты визита.

## 1. Как определить отделение (branchId)

Для большинства интеграций критично понимать, **в каком отделении** произошло событие идентификации.
Например, если камера VisionLabs установлена в отделении, то после распознавания клиента нужно
создать визит **в этом отделении**.

Integration Broker предоставляет alias `branch` (см. `BranchResolverGroovyAdapter`):

```groovy
// input = InboundEnvelope

def br = branch.resolve(input)
meta.branchId = br.branchId
if (meta.branchId == null) {
  throw new RuntimeException('Не удалось определить отделение: ' + br.strategy)
}
```

Поддерживаемые варианты (в порядке приоритета):

1) `InboundEnvelope.branchId`
2) заголовок `x-branch-id` (настраивается)
3) заголовок `x-branch-prefix` + `prefixToBranchId`
4) `sourceMeta.cameraName` + regex-правила (извлекаем prefix или branchId)

Настройка выполняется в `runtime-config.branchResolution` (см. `examples/sample-system-config.json`).

## 2. Клиентские классы для VisitManager

Integration Broker экспортирует alias `visit`:

- `visit.servicesCatalog(branchId)` — получить каталог услуг отделения (для сопоставления)
- `visitManager.getServicesCatalog(target, branchId, headers, sourceMessageId, correlationId, idempotencyKey)` — Java API вариант каталога услуг с trace/meta заголовками
- `visit.matchServiceIdsByNames(branchId, names, allowContains)` — сопоставить внешние имена процедур со `serviceIds`
- `visit.createVisitRest(args, meta)` — создать визит через REST (с fallback в REST outbox при ошибке)
- `visit.createVirtualVisitRest(args, meta)` — создать виртуальный визит (service-point сценарий)
- `visit.createVisitOnPrinterRest(args, meta)` — создать визит через выбранный принтер (services/parameters)
- `visitManager.createVisitFromEvent(target, payload, headers, sourceMessageId, correlationId, idempotencyKey)` — Java API helper для прямого маппинга payload события `VISIT_CREATE` в REST-вызов VisitManager
- `visit.callNextVisitRest(args, meta)` — вызвать следующего посетителя на service point
- `visit.enterServicePointModeRest(args, meta)` — вход сотрудника в service-point режим (в т.ч. с `sid`)
- `visit.exitServicePointModeRest(args, meta)` — выход сотрудника из service-point режима (в т.ч. с `sid`)
- `visit.startAutoCallRest(args, meta)` — включение авто-вызова на service point
- `visit.cancelAutoCallRest(args, meta)` — выключение авто-вызова на service point
- `visit.postponeCurrentVisitRest(args, meta)` — отложить текущий визит на service point

Пример вызова создания визита:

```groovy

def visitArgs = [
  branchId: meta.branchId,
  entryPointId: (input.headers?.get('x-entry-point-id') ?: '1'),
  serviceIds: ['c3916e7f-7bea-4490-b9d1-0d4064adbe8b'],
  parameters: [
    clientId: 'CLIENT-001',
    segment: 'VIP',
    fullName: 'Иванов Иван'
  ],
  printTicket: false
]

def res = visit.createVisitRest(visitArgs, meta)
output.visitCreate = res
```

### Почему параметры визита — строки

В VisitManager модель `VisitParameters.parameters` — это `Map<String, String>`. Поэтому любые
дополнительные данные (segment, признаки, результаты аналитики) должны быть сериализованы в строки
(например, JSON-строка или `;`-список).

## 3. Публикация события VISIT_CREATE в DataBus

VisitManager поддерживает асинхронное создание визита через событие `VISIT_CREATE`.
В референсном VisitManager обработчик ожидает тело:

- `branchId`
- `entryPointId`
- `serviceIds`
- `parameters`
- `printTicket`
- `segmentationRuleId` (обычно не используется — VisitManager решает сам)

Integration Broker экспортирует alias `bus`:

```groovy

def body = [
  branchId: meta.branchId,
  entryPointId: '1',
  serviceIds: ['...'],
  parameters: [segment: 'VIP'],
  printTicket: false,
  segmentationRuleId: null
]

output.dataBusOutboxId = bus.publishEvent('VISIT_CREATE', 'visitmanager', body)
```

Для route-варианта (fan-out в несколько внешних шин) можно использовать `dataBus.publishEventRoute(...)` на Java API или `bus.publishEventRoute(...)` в Groovy.

Для унифицированной отправки канонического события создания визита есть Java helper `dataBus.publishVisitCreate(...)`: он собирает payload (`branchId`, `entryPointId`, `serviceIds`, `parameters`, `printTicket`, `segmentationRuleId`) и публикует событие типа `VISIT_CREATE`.
Для route-сценариев доступен `dataBus.publishVisitCreateRoute(...)`, который отправляет тот же канонический payload через `/events/types/{type}/route`.

```groovy
output.routeOutboxId = bus.publishEventRoute(
  'VISIT_CREATE',
  'visitmanager',
  true,
  ['http://bus-a:8080', 'http://bus-b:8080'],
  body,
  meta.messageId as String,
  meta.correlationId as String,
  meta.idempotencyKey as String
)
```


Для прототипных request/response сценариев DataBus alias `bus` также предоставляет упрощённые helper-методы. На Java API также доступны сокращения `dataBus.sendResponseOk(...)` и `dataBus.sendResponseError(...)` для типовых ответов без ручной расстановки статуса/message:

- `bus.sendRequest(function, destination, params)`
- `bus.sendRequest(function, destination, params, correlationId)`
- `bus.sendRequest(function, destination, sendToOtherBus, params, correlationId)`
- `bus.sendResponse(destination, status, message, response)`
- `bus.sendResponse(destination, status, message, response, correlationId)`

### Заголовки DataBus

DataBus требует заголовки (формируются автоматически внутри `bus.publishEvent(...)`):

- `Service-Destination`
- `Send-To-OtherBus`
- `Send-Date` (RFC1123)
- `Service-Sender`

## 4. Как сопоставлять медицинские процедуры с услугами VisitManager

Общий подход (в Groovy-flow):

1) получить из МИС список процедур (имя/код)
2) получить каталог услуг отделения из VisitManager
3) сопоставить процедуры с услугами по имени (при необходимости — по contains)
4) создать визит со списком `serviceIds`

Integration Broker даёт helper `visit.matchServiceIdsByNames(...)`, но заказчик может реализовать
своё сопоставление в Groovy (например, через словарь соответствий по кодам).

## 5. Пример сценария

См.:

- `src/main/resources/examples/scenarios/inbound-customer-identification-requested.json`
- flow `identify_customer_and_create_visit_demo` в `examples/sample-system-config.json`
