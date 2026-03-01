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
6. `DONE` Добавить пер-connector retry policy (override global backoff).
7. `DONE` Реализовать `circuit-breaker` для проблемных REST-коннекторов.
8. `DONE` Добавить журнал смены runtime-конфигурации (кто/когда/что изменил).
9. `DONE` Реализовать dry-run режим для outbound (без фактической отправки).
10. `DONE` Поддержать batch replay для DLQ с фильтрацией по type/source/branch.

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
23. `DONE` Реализовать страницу управления runtime-config с dry-run перед сохранением.
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

## 5) Пакет 02 (следующие 30 задач)

31. `DONE` Убрать оставшиеся hardcoded-строки в Replay/Integrations и вынести в i18n-словари.
32. `DONE` Локализовать пустое состояние аудита runtime-config.
33. `DONE` Реализовать batch replay DLQ в Workbench через `/admin/dlq/replay-batch`.
34. `DONE` Локализовать контент инцидента (preview/markdown export labels).
35. `DONE` Добавить UI-индикатор состояния outbound dry-run override.
36. `DONE` Добавить управление outbound dry-run override из Workbench.
37. `DONE` Показать effective retry policy по REST-коннекторам в UI.
38. `DONE` Показать circuit-breaker состояние REST-коннекторов в UI.
39. `DONE` Добавить фильтры списка DLQ по `type/source/branch` в UI-листинге.
40. `DONE` Добавить пагинацию для DLQ/outbox списков.
41. `DONE` Добавить автorefresh режим для мониторинга (toggle + interval).
42. `DONE` Добавить экспорт audit runtime-config в JSON.
43. `DONE` Добавить подтверждение перед batch replay (confirm dialog).
44. `DONE` Добавить dry-run предпросмотр diff конфигурации до сохранения.
45. `DONE` Добавить UI-валидацию формата фильтра batch replay.
46. `DONE` Добавить метрики replay (ok/failed/locked/dead) на dashboard.
47. `TODO` Добавить E2E smoke для маршрутов Workbench.
48. `TODO` Добавить unit-тесты для workbenchApi replay-batch.
49. `TODO` Добавить unit-тесты для локализации runtime/replay screen.
50. `DONE` Добавить fallback-тексты для неизвестных исходов replay.
51. `DONE` Добавить серверный endpoint health-check интеграций с деталями VM/DataBus.
52. `DONE` Добавить ручной trigger health-check из UI с timestamp последней проверки.
53. `DONE` Добавить фильтр по статусам интеграций (UP/DEGRADED/DOWN).
54. `DONE` Добавить поддержку копирования correlationId из replay таблиц.
55. `DONE` Добавить UI для просмотра replay result payload.
56. `DONE` Добавить API client wrapper для `OutboundAdminController`.
57. `TODO` Добавить read-only экран runtime-config revisions.
58. `TODO` Добавить server-side limit guard и UI hint для больших batch replay.
59. `TODO` Добавить audit события для replay batch запросов.
60. `TODO` Подготовить release notes по пакету 02 и обновить README ссылками.
