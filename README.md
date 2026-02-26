# Integration Broker

Integration Broker — универсальный интеграционный посредник между:

- СУО (в первую очередь **VisitManager**)
- **DeviceManager**
- внешними **CRM**
- **МИС/EMR/EHR**
- системами **предварительной записи**
- **шинами данных** и **брокерами сообщений**
- внешними **REST API**

Цель репозитория — стартовая, production-ready база для реального проекта (закрытые контуры, on‑prem, РФ).

## Что уже реализовано (итерация 1)

- **Единая входящая модель**: `InboundEnvelope`.
- **Публичный REST ingress**: `POST /api/inbound`.
- **FlowResolver** (базовая версия): выбор flow по `kind + type`.
- **GroovyFlowEngine**:
  - компиляция и кеширование;
  - binding: `input`, `meta`, `output`, `ctx`, `beans` + alias‑заглушки `rest/msg/crm/identity/medical/appointment`.
- **RuntimeConfigStore**:
  - локальная конфигурация из classpath;
  - удалённая конфигурация (SystemConfiguration) — как опция;
  - periodic refresh;
  - поддержка envelope-форматов `value/data/config/payload/settings`;
  - поддержка случая, когда `value` — JSON-строка.
- **Минимальные метрики**: `GET /api/metrics/integration`.
- **Quality-gate тест** (эвристика):
  - запрет `System.out/err`, `printStackTrace`, `@SuppressWarnings` в `src/main/java`;
  - обязательные Swagger-аннотации на контроллере.

## Что добавлено (итерация 2)

- **Idempotency** (PostgreSQL/Flyway):
  - таблица `ib_idempotency`;
  - статусы `IN_PROGRESS / COMPLETED / FAILED`;
  - решения `PROCESS / SKIP_COMPLETED / LOCKED`;
  - правило: `LOCKED` не является poison message.
- **Admin API (Idempotency)**:
  - `GET /admin/idempotency/{key}`
  - `GET /admin/idempotency?status=COMPLETED&limit=50`

## Что добавлено (итерация 3)

- **Inbound DLQ (PostgreSQL/Flyway)**:
  - таблица `ib_inbound_dlq`;
  - статусы `PENDING / REPLAYED / DEAD`;
  - хранение исходного payload для replay;
  - обязательная санитизация заголовков (Authorization/Cookie/Set-Cookie и т.п.).
- **Admin API (Inbound DLQ + replay)**:
  - `GET /admin/dlq/{id}`
  - `GET /admin/dlq?status=PENDING&limit=50`
  - `POST /admin/dlq/{id}/replay`
- **Метрики** (`GET /api/metrics/integration`): добавлены счётчики inbound DLQ.


## Что добавлено (итерация 4)

- **Outbox (PostgreSQL/Flyway)**:
  - `ib_messaging_outbox` — надёжная отправка сообщений во внешние брокеры;
  - `ib_rest_outbox` — надёжные REST-вызовы с `Idempotency-Key` и опциональной трактовкой части `4xx` как логического успеха;
  - статусы: `PENDING / SENDING / SENT / DEAD`;
  - retry/backoff (экспоненциальный, с верхней границей).
- **Outbox dispatcher** (Scheduled):
  - периодически выбирает due-записи (`next_attempt_at <= now`) и выполняет отправку;
  - конкурентная защита через атомарный переход `PENDING -> SENDING`.
- **MessagingProvider (расширяемый контракт)**:
  - реестр провайдеров по `providerId`;
  - провайдер `logging` как безопасная заглушка без внешнего брокера.
- **Admin API (Outbox + replay)**:
  - `GET /admin/outbox/messaging/{id}`
  - `GET /admin/outbox/messaging?status=PENDING&limit=50`
  - `POST /admin/outbox/messaging/{id}/replay?resetAttempts=true`
  - `GET /admin/outbox/rest/{id}`
  - `GET /admin/outbox/rest?status=PENDING&limit=50`
  - `POST /admin/outbox/rest/{id}/replay?resetAttempts=true`
- **Безопасность**:
  - заголовки в outbox **санитизируются** — `Authorization/Cookie/Set-Cookie` и похожие ключи не сохраняются в сыром виде.

### Почему Postgres, а не Redis для outbox/DLQ/idempotency

- **PostgreSQL** используется как source-of-truth для outbox/DLQ/idempotency, т.к. это долговременные эксплуатационные журналы, критичные для восстановления после аварий.
- **Redis** рекомендуется использовать **дополнительно** (опционально): кэш enrichment (KeycloakProxy) по хэшу токена, короткие TTL-блокировки/дедуп для разгрузки БД, быстрые лимитеры.

## Что добавлено (итерация 5)

- **REST-коннекторы (typed preset) для безопасной авторизации**:
  - `restConnectors` в runtime-config: `baseUrl` + `auth` (NONE/BASIC/BEARER/API_KEY_HEADER);
  - секреты хранятся только в конфигурации и **не сохраняются в outbox**.
- **REST outbox через коннекторы**:
  - в `ib_rest_outbox` добавлены `connector_id` и `path`;
  - итоговый URL и auth-заголовки собираются **в момент отправки** по runtime-config.
- **Groovy ctx/rest alias**:
  - `ctx.restCallConnector(connectorId, method, path, headers, body)` (рекомендуемый вариант).

## Что добавлено (итерация 6)

- **KeycloakProxy enrichment (опционально)**:
  - режимы получения профиля: `USER_ID_HEADER` (предпочтительно) и `BEARER_TOKEN`;
  - краткоживущий in-memory TTL-кэш;
  - ключ кэша для token-mode — только `SHA-256(token)`;
  - из ответа удаляются поля `access_token/refresh_token` и подобные (если включено `stripTokensFromResponse`);
  - в Groovy-binding доступны переменные `user` и `principal`.

### Конфигурация KeycloakProxy enrichment

Секция `keycloakProxy` добавлена в runtime-config (см. `src/main/resources/examples/sample-system-config.json`).
Основной путь для закрытых контуров РФ — передавать `userId` через заголовок (например `x-user-id`) и включить режим `USER_ID_HEADER`.

## Что добавлено (итерация 7)

- **Fail-fast проверки зависимостей на старте** (настраиваемые):
  - remote-config (SystemConfiguration);
  - REST-коннекторы (`restConnectors[*]`);
  - messaging providers (по списку requiredProviderIds).
- Настройки: `integrationbroker.startup-checks.*`.
- Регламент: `docs/playbooks/fail-fast-checkers.md`.

## Что добавлено (итерация 8)

- **Расширяемый identity layer (customerIdentity)**:
  - контракты `IdentityRequest / IdentityAttribute / IdentityProfile / IdentityResolution`;
  - цепочка провайдеров `IdentityProvider` + реестр `IdentityProviderRegistry`;
  - агрегация результата и нормализация сегмента (`SegmentNormalizer`);
  - демо-провайдер `StaticIdentityProvider` (для примеров без внешних систем).
- **Интеграция с решениями компьютерного зрения (как расширяемая модель)**:
  - `visionlabsFace` (распознавание лица, LUNA PLATFORM): `faceId`, `faceImageBase64`;
  - `visionlabsCars` (распознавание автотранспорта, LUNA CARS): `vehiclePlate`, `vehicleImageBase64`;
  - настройки и регламент: `docs/playbooks/visionlabs-identity.md`.
- **Публичный API**:
  - `POST /api/identity/resolve`.
- **Доступ из Groovy-flow**:
  - `identity.resolve(request)`.
- Регламент добавления нового способа идентификации:
  - `docs/playbooks/identity-adding-method.md`.

## Что добавлено (итерация 8.1)

- **Источники событий аналитики VisionLabs (LUNA PLATFORM)**, соответствующие callback types:
  - `http` — `POST /api/visionlabs/callback`;
  - `luna-ws-notification` — `ws://<host>:<port>/ws/visionlabs/notifications`;
  - `luna-event` — poller `VisionLabsEventsPoller` (выборка событий из Events через API + checkpoint в PostgreSQL);
  - `luna-kafka` — Kafka listener `VisionLabsKafkaListener`.
- Конфигурация в runtime-config: `visionLabsAnalytics` (по умолчанию выключена).
- Регламент настройки: `docs/playbooks/visionlabs-analytics-sources.md`.

## Что добавлено (итерация 9)

- **Typed CRM слой** (`ru.aritmos.integrationbroker.crm`):
  - контракты и модели операций (`CrmModels`);
  - интерфейс `CrmClient` и реестр `CrmClientRegistry`;
  - сервис `CrmService` (проверка `runtime-config.crm.enabled`, выбор активного профиля);
  - профили: `BITRIX24 / AMOCRM / RETAILCRM / MEGAPLAN / GENERIC`;
  - реализация `GENERIC` (stub) для демо/тестов без внешних зависимостей;
  - каркасы клиентов для Bitrix24/amoCRM/RetailCRM/Мегаплан (возвращают NOT_IMPLEMENTED).
- **Доступ из Groovy-flow**:
  - alias `crm` экспортируется через `@GroovyExecutable("crm")`;
  - пример flow: `crm.findCustomer.requested` (см. `sample-system-config.json`).
- **Playbook**:
  - `docs/playbooks/crm-adapters.md`.


## Что добавлено (итерация 10)

- **Typed Medical/MIS слой** (`ru.aritmos.integrationbroker.medical`):
  - контракты `MedicalModels` (пациент, предстоящие услуги, routing context);
  - интерфейс `MedicalClient` и реестр `MedicalClientRegistry`;
  - сервис `MedicalService` (проверка `runtime-config.medical.enabled`, выбор профиля);
  - профили: `EMIAS_LIKE / MEDESK_LIKE / FHIR_GENERIC`.
- **Демо-реализация**:
  - `FHIR_GENERIC` — детерминированный stub для разработки и тестов без внешних систем;
  - `EMIAS_LIKE` и `MEDESK_LIKE` — каркасы, возвращающие `NOT_IMPLEMENTED`.
- **Доступ из Groovy-flow**:
  - alias `medical` экспортируется через `@GroovyExecutable("medical")`;
  - пример flow: `medical.precheck.requested` (см. `sample-system-config.json`).
- **Playbook**:
  - `docs/playbooks/medical-adapters.md`.

## Итерация 11: Appointment/Booking слой

- **Typed appointment слой** (`ru.aritmos.integrationbroker.appointment`):
  - контракты `AppointmentModels` (записи, слоты, бронирование/отмена);
  - интерфейс `AppointmentClient` и реестр `AppointmentClientRegistry`;
  - сервис `AppointmentService` (проверка `runtime-config.appointment.enabled`, выбор профиля);
  - профили: `EMIAS_APPOINTMENT / MEDTOCHKA_LIKE / PRODOCTOROV_LIKE / YCLIENTS_LIKE / NAPOPRAVKU_LIKE / GENERIC`.
- **Демо-реализация**:
  - `GENERIC` — детерминированный stub для разработки и тестов без внешних систем;
  - остальные — каркасы, возвращающие `NOT_IMPLEMENTED`.
- **Доступ из Groovy-flow**:
  - alias `appointment` экспортируется через `@GroovyExecutable("appointment")`;
  - пример flow: `prebooked.patient.arrived` (см. `sample-system-config.json`).
- **Playbook**:
  - `docs/playbooks/appointment-adapters.md`.

## Итерация 12: Создание визита в VisitManager и события DataBus

- **Интеграция с VisitManager**:
  - alias `visit` (клиентские классы для каталога услуг и создания визита по параметрам);
  - создание визита через REST `/entrypoint/.../visits/parameters` с fallback в REST outbox при ошибке.
- **Интеграция с DataBus**:
  - alias `bus` для публикации событий в DataBus (в т.ч. `VISIT_CREATE` для VisitManager).
- **Определение отделения**:
  - alias `branch` для восстановления `branchId` по `branchId/prefix/cameraName`.
- **Регламент**:
  - `docs/playbooks/visitmanager-and-databus.md`.


## Быстрый старт

1) Сборка и тесты:

```bash
./mvnw -q -DskipTests=false test
```

2) Запуск:

```bash
./mvnw -q mn:run
```

3) Swagger UI:

- `http://localhost:8088/swagger-ui/`

## Примеры

Примеры лежат в `src/main/resources/examples/`:

- `sample-inbound-visit.created.json` — пример входящего события
- `sample-inbound-with-userid.json` — пример входящего события с userId в заголовке
- `sample-inbound-identity.resolve.requested.json` — пример входящего события для демо-идентификации
- `sample-system-config.json` — пример конфигурации flow
- `identity/identity-request-multi.json` — пример запроса к Identity API (несколько идентификаторов)
- `identity/identity-request-face.json` — пример запроса идентификации по изображению лица (base64)
- `identity/identity-request-vehicle.json` — пример запроса идентификации по изображению автомобиля/номера (base64)
- `visionlabs/analytics-callback-sample.json` — пример payload события аналитики
- `crm/crm-findCustomer-request.json` — пример запроса поиска клиента в CRM
- `medical/medical-precheck-request.json` — пример запроса на медицинский precheck (пациент + контекст)
- `appointment/prebooked-patient-arrived.json` — пример события "клиент пришёл по записи" (общий формат)
- `scenarios/inbound-customer-identification-requested.json` — пример полного InboundEnvelope для идентификации и создания визита

## Следующие итерации (план)

- Уточнение typed клиентов CRM/МИС/appointment под реальные API заказчика.
- Расширение набора inbound-каналов (Kafka/Rabbit/NATS/JetStream) и провайдеров MessagingProvider.
- Сквозные сценарии VisitManager <-> Integration Broker <-> DeviceManager (если заказчику требуется отображение/озвучка через DeviceManager).

## Важные правила

- Документация к коду и API — **строго на русском**.
- Запрещено логировать токены, Authorization/Cookie/Set-Cookie, client_secret и прочие секреты.
- Groovy — только orchestration/routing. Интеграционная логика — в типизированных Java-адаптерах.

См. также `AGENTS.md` и `Agents.md`.
# IntegrationBroker
