# Roadmap реализации Integration Broker (пакет 01)

Документ фиксирует **первый пакет работ (30 задач)** для последовательной реализации по playbook'ам и AGENTS.

## Статусы

- `TODO` — задача не начата.
- `IN_PROGRESS` — в работе.
- `DONE` — завершена и влита в основной поток.

## 1) Платформенный контур (core + adapters)

1. `DONE` Ввести единый `CorrelationContext` для всех входящих каналов (REST, Webhook, polling).
2. `DONE` Добавить унифицированный генератор `X-Correlation-Id` и `X-Request-Id` для исходящих HTTP-вызовов.
3. `DONE` Добавить валидацию idempotency key по шаблону `source:flow:externalId`.
4. `DONE` Расширить `IdempotencyService` меткой причины `SKIPPED_REASON` (duplicate/locked/expired).
5. `DONE` Реализовать endpoint ручной разморозки `LOCKED` записей с аудитом.
6. `TODO` Добавить пер-connector retry policy (override global backoff).
7. `TODO` Реализовать `circuit-breaker` для проблемных REST-коннекторов.
8. `TODO` Добавить журнал смены runtime-конфигурации (кто/когда/что изменил).
9. `TODO` Реализовать dry-run режим для outbound (без фактической отправки).
10. `TODO` Поддержать batch replay для DLQ с фильтрацией по type/source/branch.

## 2) VisitManager и DataBus (контракты по AGENTS)

11. `DONE` Нормализовать VM-ответы в единый envelope `{status, headers, body, error}`.
12. `DONE` Добавить в `VisitManagerApiImpl` явную поддержку `sid` cookie для servicepoint-сценариев.
13. `DONE` Реализовать трассировку конфликтов `409` из VM в отдельную метрику.
14. `DONE` Ввести карту ошибок VM (`404/409/500`) -> кодов интеграционного домена IB.
15. `DONE` Укрепить `DataBusApiImpl` обязательными заголовками `Service-Sender` и `Send-Date`.
16. `DONE` Добавить режим multi-bus fan-out с частичным успехом и итоговым отчётом.
17. `DONE` Ввести лимит размера payload для DataBus на уровне IB-конфига.
18. `DONE` Добавить автообогащение DataBus-события параметрами `correlationId/requestId/idempotencyKey`.

## 3) Frontend Workbench (React) — старт реализации

19. `DONE` Создать каркас frontend-приложения (`apps/workbench`) на React + TypeScript.
20. `DONE` Реализовать login/logout через Keycloak (OIDC PKCE) с хранением сессии в memory.
21. `DONE` Добавить role-aware layout (admin/operator/auditor/support).
22. `DONE` Реализовать страницу мониторинга ingress/idempotency/DLQ/outbox.
23. `IN_PROGRESS` Реализовать страницу управления runtime-config с dry-run перед сохранением.
24. `DONE` Реализовать UI для replay DLQ/outbox (single + batch).
25. `DONE` Добавить экран Groovy Tooling (validate + emulate + trace).
26. `DONE` Добавить страницу интеграций (VM/DataBus/CRM/МИС) и health-check статусы.
27. `DONE` Внедрить экспорт инцидента в sanitized JSON/Markdown.

## 4) Документация и enablement

28. `DONE` Подготовить ролевые инструкции: пользователь, внедренец, поддержка, продажник (в простой, понятной форме).
29. `DONE` Добавить стандартный процесс локального экспорта документации из Markdown (bundle/manifest/HTML, без хранения бинарных файлов в git).
30. `DONE` Перестроить `README.md` по стандартной структуре: обзор, архитектура, запуск, эксплуатация, ссылки.

---

> Рекомендуемый темп: брать по 20–30 задач на релизный цикл, фиксируя статус и ссылку на PR для каждой задачи.
