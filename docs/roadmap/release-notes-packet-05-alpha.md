# Release notes — Packet 05 alpha

## Что вошло

- Добавлен admin export effective runtime-config с опциональным `redactSecrets`.
- Добавлен batch replay messaging outbox по `correlationId` с dry-run режимом.
- Усилена санитизация debug-текстов: маскирование `Cookie sid`.
- Обновлены playbooks для CRM/Medical fallback-поведения в prerelease.
- Добавлен отдельный rollout-checklist для переключения с fallback на real connector.
- Добавлены примеры fallback-flow для CRM/Medical/Appointment.

## Операционные заметки

- Для расследования fallback-кейсов используйте фильтрацию по `correlationId`.
- Перед сменой профиля всегда выполняйте dry-run и фиксируйте rollout-окно.
- Replay в период стабилизации — только для безопасных/idempotent операций.
