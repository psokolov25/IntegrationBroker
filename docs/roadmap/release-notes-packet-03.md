# Release Notes — Packet 03

## Что реализовано

- Надёжность outbound/admin:
  - фильтр `connectorId` для REST outbox списка;
  - массовая отмена queued REST outbox (`cancel-batch`).
- Надёжность и поддержка DLQ:
  - sanitized preview payload по `id`;
  - массовая пометка DLQ записей как ignored с reason.
- Idempotency tooling:
  - поиск по `externalMessageId`;
  - JSON export audit trail.
- Appointment custom connector:
  - дефолт `429/5xx -> ERROR_RETRYABLE`;
  - детализация `correlationId/requestId` в `details`.
- Runtime-config:
  - server-side валидация `method/path` для custom operations;
  - warning на write operations без `headersTemplate`.
- DataBus:
  - validator route-envelope;
  - unit coverage mandatory headers в route/request/response.
- Документация:
  - support runbook по деградациям `429/5xx`;
  - retry safety matrix в implementation guide.
- Тесты:
  - schema-smoke для `examples/appointment/*.json`;
  - regression suite для sanitizer.

## Checklist внедрения

- [x] Проверить права `IB_ADMIN` на новые admin endpoints.
- [ ] Согласовать политику batch-cancel outbox с поддержкой.
- [x] Подтвердить лимиты replay/cancel в production-профиле.
- [x] Убедиться, что dashboards учитывают новые admin-операции.
- [ ] Провести smoke на примерах appointment custom-client.

