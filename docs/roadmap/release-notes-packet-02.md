# Release Notes — Пакет 02

Дата: 2026-03-05

## Что вошло

- Локализация Workbench для Replay/Integrations/Runtime (включая fallback-тексты и пустые состояния).
- Batch replay DLQ из Workbench через `/admin/dlq/replay-batch`, с подтверждением и валидацией фильтров.
- Визуализация dry-run override и управление им через UI.
- Отображение retry policy и circuit-breaker по REST-коннекторам.
- Фильтры и пагинация для DLQ/outbox.
- Метрики replay (ok/failed/locked/dead) на dashboard.
- Health-check интеграций (VM/DataBus) с ручным trigger из UI.
- Копирование correlationId и просмотр replay payload.
- Read-only экран ревизий runtime-config.
- Server-side guard для лимита batch replay + UI hint при clamp.
- Audit событие для batch replay (структурированный лог `DLQ_REPLAY_BATCH`).

## Операционные изменения

- Добавлен конфиг `integrationbroker.admin.dlq.replay-batch.max-limit` (default: `100`).
- Ответ `/admin/dlq/replay-batch` расширен полями:
  - `requestedLimit`
  - `appliedLimit`
  - `limitClamped`

## Совместимость

- Изменения обратно совместимы для клиентов, которые читают только ранее существующие поля ответа replay-batch.
- Для UI Workbench рекомендуется обновление до версии, поддерживающей поля clamp.
