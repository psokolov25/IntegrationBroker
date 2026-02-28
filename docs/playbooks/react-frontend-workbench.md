# Playbook: React Frontend Workbench для Integration Broker

## 1. Цель

Собрать frontend-приложение (React + TypeScript), которое даёт единое рабочее место для:

- управления flow-конфигурацией,
- IDE-подобного редактирования Groovy,
- запуска отладки/эмуляции сценариев,
- наблюдения за интеграционными вызовами (внутренние/внешние сервисы, outbox, DLQ),
- операционного мониторинга и администрирования настроек брокера.

> Важно: frontend не встраивает бизнес-логику клиента. Бизнес-правила остаются в Groovy/YAML flow.

## 2. Рекомендуемая архитектура UI

### 2.1. Модули

1. **Flow Catalog**
   - список flow,
   - фильтры по `kind/type/enabled`,
   - quick actions: validate, emulate, publish test event.

2. **Groovy IDE Workbench**
   - редактор (Monaco Editor),
   - lint/compile check,
   - подсветка alias (`crm`, `medical`, `appointment`, `visit`, `bus`, `branch`, `identity`, `rest`, `msg`),
   - просмотр debug-trace и вызовов alias.

3. **Service Emulator Panel**
   - настройка mock-ответов по ключу `alias.method`,
   - шаблоны mocks для типовых сценариев (VM/DataBus/CRM/MIS),
   - запуск dry-run без вызова реальных интеграций.

4. **Operations**
   - просмотр idempotency/outbox/DLQ,
   - replay сообщений,
   - диагностика ошибок выполнения flow.

5. **Settings Center**
   - управление runtime-настройками интеграций,
   - параметры авторизации коннекторов и профилей,
   - безопасное редактирование/версионирование конфигов,
   - dry-run проверка конфигурации перед применением.

6. **Monitoring & Observability**
   - health/status сервиса и зависимостей,
   - метрики по обработке сообщений,
   - метрики DLQ/outbox/idempotency,
   - аудит операций и ошибок.

### 2.2. Tech stack (рекомендация)

- React 18+
- TypeScript
- Vite
- TanStack Query
- Zustand/Redux Toolkit (state)
- Monaco Editor
- xterm.js (консоль debug trace, опционально)
- zod (валидация DTO в UI)
- Apache ECharts/Recharts (графики мониторинга)

## 3. Backend API для IDE/эмуляции

В IB добавлен tooling API:

- `POST /admin/groovy-tooling/validate`
  - request: `{ "script": "..." }`
  - response: `{ "valid": true|false, "errors": [] }`

- `POST /admin/groovy-tooling/emulate`
  - request:
    - `script` — Groovy-код,
    - `input` — входной payload/envelope,
    - `meta` — runtime meta,
    - `mocks` — карта mock-ответов по ключу `alias.method`.
  - response:
    - `success`,
    - `output`,
    - `calls` (история вызовов alias),
    - `debugMessages`,
    - `errors`.

Пример `mocks`:

```json
{
  "crm.findCustomerByPhone": {"success": true, "result": {"crmCustomerId": "CRM-1"}},
  "visit.createVisitRest": {"success": true, "body": {"id": "V-100"}}
}
```

## 4. IDE-возможности для Groovy

Минимальный MVP:

1. **Compile Check**: кнопка `Validate` → вызов `/admin/groovy-tooling/validate`.
2. **Debug Run**: кнопка `Emulate` → вызов `/admin/groovy-tooling/emulate`.
3. **Call Trace**: таблица `alias / method / args / response` из `calls`.
4. **Mock Profiles**: сохранение наборов `mocks` для регрессионных проверок.

Расширение (этап 2):

- брейкпоинты через lightweight-instrumentation (препроцессор скрипта),
- schema-aware подсказки для типовых payload,
- сравнение фактического `output` с ожидаемым snapshot.

## 5. Эмуляция внешних и внутренних служб

Эмуляция в workbench должна покрывать:

- внешние: CRM, MIS/medical, appointment, VisitManager, DataBus,
- внутренние: `rest`, `msg`, `branch`, `identity`.

Режимы:

1. **Local mock mode** (через `mocks` в `/emulate`) — быстрый unit-style прогон.
2. **Hybrid mode** — часть alias мокируется, часть ходит в реальные sandbox-среды.
3. **Record/replay mode** (roadmap) — запись реальных ответов и повтор в regression suite.

## 6. Центр настроек (полный контур)

Frontend должен покрывать **весь контур операционных настроек** брокера:

1. **Runtime config management**
   - просмотр активной конфигурации,
   - diff между текущей и новой версией,
   - валидация схемы и обязательных полей,
   - применение/rollback версии.

2. **Integration connectors**
   - редактирование `restConnectors` (baseUrl, timeout, retry, auth policy),
   - настройка messaging providers,
   - режимы fail-fast/startup-checks по зависимостям.

3. **Security settings**
   - режимы `OPEN / KEYCLOAK_OPTIONAL / KEYCLOAK_REQUIRED`,
   - allow-list для anonymous endpoint,
   - mapping ролей и политик доступа.

4. **Flow governance**
   - enable/disable flow,
   - pin версий скриптов,
   - обязательные pre-check (validate + emulate) перед публикацией.

5. **Change management**
   - журнал изменений настроек,
   - кто/когда/что изменил,
   - commit message + ticket reference.

## 7. Мониторинг и эксплуатация (полный набор)

### 7.1. Dashboard-виджеты

- **Ingress overview**: входящие события, success/error rate, latency p50/p95.
- **Idempotency**: `PROCESS/SKIP_COMPLETED/LOCKED` по периодам.
- **DLQ**: `PENDING/REPLAYED/DEAD`, возраст oldest message, replay success rate.
- **Outbox (REST + Messaging)**: `PENDING/SENDING/SENT/DEAD`, retries, backoff saturation.
- **Dependency health**: remote-config, rest-connectors, messaging providers.
- **Security events**: 401/403, ошибка токенов, denied by role.

### 7.2. Операционные действия из UI

- replay для DLQ/outbox,
- ручной retry с reset attempts,
- drill-down до карточки сообщения/ошибки,
- export инцидента (sanitized) для анализа.

### 7.3. Алертинг (UI + интеграция)

- пороги для DLQ growth и outbox backlog,
- SLA-alert по времени обработки,
- всплеск 5xx/4xx,
- недоступность критичных коннекторов,
- уведомления в внешние каналы (email/chat/webhook) через отдельный integration layer.

## 8. План реализации (интенсивный)

Каждая итерация: **1 фича VM + 1 фича DataBus + 1-2 фичи внешних адаптеров + 1 фича UI/workbench**.

Пример спринта:

- VM: новый event-mapping helper для service-point.
- DataBus: канонический publisher для очередного доменного события.
- External adapters: compact helper в CRM/Medical/Appointment.
- UI: расширение эмулятора mocks + визуализация call trace.

Для frontend отдельно рекомендуется ротация задач:

- Iteration UI-1: IDE validate/emulate + trace viewer,
- Iteration UI-2: Settings Center (runtime config + connector auth),
- Iteration UI-3: Monitoring dashboard + replay actions,
- Iteration UI-4: Security/RBAC management + audit timeline.

## 9. Референс-ориентир

Можно использовать подходы из `DeviceTypesConverter` как ориентир по UX/структуре решений,
но архитектуру и компоненты нужно адаптировать под Integration Broker, расширяя под сценарии
flow orchestration, Groovy tooling и интеграционные эмуляции.

## 10. Security и доступ (Keycloak)

Frontend Workbench должен поддерживать режимы безопасности брокера:

- Keycloak login (OIDC code + PKCE),
- role-aware UI (`IB_ADMIN`, `IB_OPERATOR`, `IB_FLOW_EDITOR`, `IB_API_CLIENT`, `IB_READONLY`),
- fallback для anonymous-mode, если включён на стороне брокера.

Для регламента backend-side авторизации/аутентификации см.
`docs/playbooks/auth-keycloak-and-access.md`.


## 11. Формы для статических и динамических параметров

Frontend Workbench должен поддерживать генерацию и редактирование форм двух типов:

1. **Статические формы**
   - фиксированный набор полей для сценариев с предсказуемым контрактом,
   - строгая валидация (`required`, типы, диапазоны),
   - версионирование шаблонов форм вместе с flow/config.

2. **Динамические формы**
   - сборка UI из JSON schema / metadata descriptor,
   - условные поля (show/hide по значениям),
   - runtime-подгрузка справочников/options из адаптеров/коннекторов,
   - поддержка пользовательских key/value параметров для event payload и service calls.

### 11.1. Где применять

- настройки коннекторов (`restConnectors`, messaging providers, auth policies),
- параметры запуска эмуляции (`input/meta/mocks`),
- конфигурация flow steps и их параметров,
- операционные действия (replay/retry с дополнительными параметрами).

### 11.2. Технические требования

- единый Form Engine (React hooks + schema resolver),
- live-validation и preview итогового JSON payload,
- безопасная работа с secret-полями (masked UI + no raw logging),
- режим импорт/экспорт шаблонов форм для повторного использования.

