# Playbook: Авторизация/аутентификация Integration Broker (Keycloak + роли + optional anonymous)

## 1. Цель

Реализовать единый контур безопасности для Integration Broker:

1. **Исходящая авторизация** IB → внешние/внутренние службы (уже поддерживается через `restConnectors[*].auth`, расширяемо).
2. **Входящая авторизация** внешние службы → IB REST API (через Keycloak/OIDC).
3. **Гибкий режим доступа**: защищённый режим по умолчанию + возможность оставить отдельные API/контуры анонимными.
4. **RBAC**: разграничение прав по ролям (админ API, runtime API, tooling API, metrics/read-only).

## 2. Контуры безопасности

## 2.1. Outbound (IB вызывает другие сервисы)

Для вызовов IB уже используется `runtime-config.restConnectors`:

- `auth.type = NONE | BASIC | BEARER | API_KEY_HEADER`,
- секреты хранятся только в runtime-config/секрет-хранилище,
- в outbox сохраняются только санитизированные заголовки (без секретов).

Рекомендации развития:

- добавить поддержку `OAUTH2_CLIENT_CREDENTIALS` (token endpoint, client_id/client_secret, cache TTL),
- добавить per-connector policy: `requiredAuth=true/false`,
- добавить startup-check проверку доступности token endpoint для критичных коннекторов.

## 2.2. Inbound (внешние службы вызывают API брокера)

Целевой режим:

- Keycloak как OIDC provider для API брокера,
- JWT access token в `Authorization: Bearer ...`,
- mapping `realm/client roles` -> роли приложения.

При этом:

- возможен **anonymous access** для выбранных endpoint (например, публичный ingestion в закрытом контуре),
- остальные endpoint требуют аутентификацию и роль.

## 3. Модель ролей (RBAC)

Базовые роли:

- `IB_ADMIN` — полный доступ (admin API, replay DLQ/outbox, idempotency admin).
- `IB_OPERATOR` — операционные действия без критичных admin-настроек.
- `IB_FLOW_EDITOR` — tooling API, validate/emulate, управление flow-конфигами.
- `IB_API_CLIENT` — доступ к `POST /api/inbound` и профильным API.
- `IB_READONLY` — read-only метрики/просмотр статусов.

Рекомендуемая матрица (минимум):

- `/admin/**` -> `IB_ADMIN` (или subset `IB_OPERATOR` для read-only и replay по политике).
- `/admin/groovy-tooling/**` -> `IB_FLOW_EDITOR` или `IB_ADMIN`.
- `/api/inbound` -> `IB_API_CLIENT` (или anonymous при флаге открытого режима).
- `/api/metrics/**` -> `IB_READONLY`/`IB_OPERATOR`/`IB_ADMIN`.

## 4. Конфигурация безопасности (roadmap)

Пример целевого блока в `application.yml`:

```yaml
integrationbroker:
  security:
    mode: KEYCLOAK_OPTIONAL # KEYCLOAK_REQUIRED | OPEN
    anonymous:
      enabled: true
      allow-paths:
        - /api/inbound
        - /health
        - /swagger-ui/**
    rbac:
      admin-role: IB_ADMIN
      operator-role: IB_OPERATOR
      flow-editor-role: IB_FLOW_EDITOR
      api-client-role: IB_API_CLIENT
      readonly-role: IB_READONLY
```

И связанный OIDC block (Micronaut Security + Keycloak):

```yaml
micronaut:
  security:
    enabled: true
    authentication: bearer
    token:
      jwt:
        signatures:
          jwks:
            keycloak:
              url: ${KEYCLOAK_JWKS_URL:}
```

## 5. Режимы работы

1. **OPEN**
   - полная анонимность (dev/lab).
2. **KEYCLOAK_OPTIONAL**
   - часть endpoint open,
   - часть endpoint по JWT + роли.
3. **KEYCLOAK_REQUIRED**
   - весь API защищён, кроме явно разрешённых технических endpoint (health/actuator при необходимости).

## 6. Требования к frontend (React Workbench)

Frontend должен поддерживать:

- login через Keycloak (OIDC code flow + PKCE),
- хранение токена только в безопасном клиентском контексте (без утечки в логи),
- role-aware UI (скрытие/блокировка admin/tooling действий),
- graceful fallback для anonymous-mode (если включён).

## 7. План реализации (итерации)

Каждая итерация: **1 VM feature + 1 DataBus feature + 1-2 adapter features + 1 security/workbench feature**.

### Итерация A

- добавить typed security config (`integrationbroker.security.*`),
- включить Keycloak JWT validation,
- ввести минимальную ролевую матрицу для `/admin/**` и `/admin/groovy-tooling/**`.

### Итерация B

- добавить optional anonymous policy (`allow-paths`),
- интеграционные тесты: anonymous/open vs protected endpoints,
- аудит логов на отсутствие token leakage.

### Итерация C

- расширить outbound auth до `OAUTH2_CLIENT_CREDENTIALS`,
- токен-кеш + retry/backoff,
- fail-fast checks для auth-критичных коннекторов.

### Итерация D

- role-mapping из Keycloak groups/roles,
- расширенные permissions для admin действий (DLQ replay, outbox replay, config updates),
- UI role matrix в React Workbench.

## 8. Нефункциональные требования

- Нельзя логировать сырой `Authorization`, `Cookie`, refresh/access token.
- Все security-ошибки должны возвращать нормализованные ответы (`401/403`) без утечки деталей.
- Все изменения безопасности сопровождаются unit/integration тестами и обновлением playbooks.
