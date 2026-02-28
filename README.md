# Integration Broker

Integration Broker (IB) — интеграционный посредник для оркестрации потоков между VisitManager, DataBus, CRM, МИС/EMR, системами предварительной записи и внешними API.

## 1. Назначение

IB решает задачу «склейки» систем в единый интеграционный контур:

- принимает входящие события и запросы;
- маршрутизирует и оркестрирует обработку через flow + Groovy;
- вызывает внешние REST/шины через типизированные адаптеры;
- обеспечивает эксплуатационную надёжность (idempotency, DLQ, outbox, replay);
- обеспечивает трассировку и наблюдаемость.

## 2. Архитектурные принципы

- **Core** (`src/main/java/.../core`) — idempotency, DLQ, outbox, dispatch/retry, корреляция.
- **Adapters** (`src/main/java/.../adapters`, `.../visitmanager`, `.../databus`, `.../crm`, `.../medical`, `.../appointment`) — интеграция с внешними системами.
- **Runtime** (`.../groovy`, `.../flow`) — выполнение бизнес-сценариев через Groovy/YAML.
- **API слой** (`.../api`) — ingress/admin/tooling endpoints.

> Важно: клиентская бизнес-логика размещается в flow/groovy, а не в Java-ядре.

## 3. Ключевые возможности

- Inbound ingress: `POST /api/inbound`.
- Flow routing (`kind + type`) и Groovy orchestration.
- Надёжность:
  - idempotency (PostgreSQL);
  - inbound DLQ;
  - messaging/rest outbox;
  - replay и backoff retry.
- Runtime connectors с auth-policy (NONE/BASIC/BEARER/API_KEY_HEADER).
- Security и RBAC (режимы безопасности + Keycloak сценарии).
- Groovy Tooling API:
  - validate;
  - emulate с трассировкой вызовов.

## 4. Быстрый старт

### 4.1. Сборка и тесты

```bash
./mvnw -q -DskipTests=false test
```

### 4.2. Запуск

```bash
./mvnw -q mn:run
```

### 4.3. Swagger UI

- `http://localhost:8088/swagger-ui/`

## 5. Примеры payload и сценариев

Примеры находятся в `src/main/resources/examples/`:

- `sample-inbound-visit.created.json`
- `sample-inbound-with-userid.json`
- `sample-inbound-identity.resolve.requested.json`
- `sample-system-config.json`
- `identity/*`
- `visionlabs/*`
- `crm/*`
- `medical/*`
- `appointment/*`
- `scenarios/*`

## 6. Документация

### 6.1. Playbooks

- `docs/playbooks/visitmanager-and-databus.md`
- `docs/playbooks/react-frontend-workbench.md`
- `docs/playbooks/auth-keycloak-and-access.md`
- `docs/playbooks/fail-fast-checkers.md`
- `docs/playbooks/crm-adapters.md`
- `docs/playbooks/medical-adapters.md`
- `docs/playbooks/appointment-adapters.md`
- `docs/playbooks/visionlabs-identity.md`
- `docs/playbooks/visionlabs-analytics-sources.md`
- `docs/playbooks/keycloakproxy-enrichment.md`
- `docs/playbooks/identity-adding-method.md`
- `docs/playbooks/codex-prompt-template.md`
- `docs/playbooks/documentation-toolkit.md`

### 6.2. Ролевые инструкции

- Пользователь: `docs/guides/user-guide.md`
- Внедренец: `docs/guides/implementation-guide.md`
- Поддержка: `docs/guides/support-runbook.md`
- Продажи/пресейл: `docs/guides/sales-guide.md`

### 6.3. План работ

- `docs/roadmap/implementation-backlog.md` — текущий пакет задач (30 задач, включая frontend).

### 6.4. Экспорт документации в PDF/DOCX

- Инструкция: `docs/guides/documentation-export-pdf-docx.md`
- Playbook по процессу: `docs/playbooks/documentation-toolkit.md`
- Скрипт: `scripts/export-docs.sh`
- Важно: PDF/DOCX формируются только локально и не добавляются в git.

Запуск:

```bash
./scripts/export-docs.sh
```

## 7. Безопасность и эксплуатация

- Не логировать секреты (`Authorization`, `Cookie`, `Set-Cookie`, `client_secret`, токены).
- Использовать корреляционные идентификаторы в каждом внешнем вызове.
- Ограничивать admin API role-based доступом.
- Проводить регулярный контроль DLQ/outbox/idempotency.

## 8. Frontend Workbench (старт реализации)

Frontend-контур описан в playbook и включён в ближайший пакет задач:

- каркас React + TypeScript;
- Keycloak login/logout;
- role-aware UI;
- monitoring/dashboard;
- runtime settings;
- tooling/emulation;
- replay операций.

Подробности: `docs/playbooks/react-frontend-workbench.md` и `docs/roadmap/implementation-backlog.md`.

## 9. Лицензирование и вклад

Перед внесением изменений:

1. Сверяйтесь с `AGENTS.md`.
2. Поддерживайте документацию на русском языке.
3. Для новых возможностей добавляйте playbook/guide + пример payload.
