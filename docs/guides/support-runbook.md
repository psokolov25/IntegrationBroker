# Руководство поддержки (L1/L2/L3)

## Для кого

- **L1** — первая линия: приём инцидента, сбор данных.
- **L2** — вторая линия: анализ причин, replay, проверка настроек.
- **L3** — третья линия: исправление кода и сложных дефектов.

## Быстрый алгоритм при инциденте

1. Получите `correlationId` или `sourceMessageId`.
2. Проверьте метрики и свежие ошибки.
3. Проверьте запись в idempotency.
4. Проверьте, есть ли запись в DLQ/outbox.
5. Определите причину и выберите действие: replay или эскалация.

## Типовые ситуации

### 1) Быстро растёт DLQ

- Проверьте доступность внешних сервисов.
- Проверьте последние изменения конфигурации.
- Сделайте тестовый replay одной записи.

### 2) В outbox много `DEAD`

- Проверьте URL/путь/авторизацию коннектора.
- Проверьте лимиты повторов и задержки.
- После исправления запустите replay.

### 3) Много `LOCKED` в idempotency

- Проверьте зависшие процессы.
- Проверьте поток дублей от источника.
- Если нужно, передайте задачу в L3.

## Полезные API для поддержки

- `GET /api/metrics/integration`
- `GET /admin/idempotency?status=LOCKED&limit=100`
- `GET /admin/dlq?status=PENDING&limit=100`
- `GET /admin/outbox/messaging?status=DEAD&limit=100`
- `GET /admin/outbox/rest?status=DEAD&limit=100`

## Правила безопасности

- Не передавайте секреты в открытых каналах.
- Используйте только sanitized-выгрузки.
- Давайте доступ к admin API только нужным ролям.


## Playbook: деградации 429/5xx (внешние REST/appointment custom connector)

### Симптомы
- всплеск `PENDING/DEAD` в outbox;
- рост ошибок вида `ERROR_RETRYABLE`;
- увеличение времени обработки и количества replay.

### Что проверить за 5 минут
1. `GET /api/metrics/integration` — оценить масштаб и тип деградации.
2. `GET /admin/dlq?status=PENDING&limit=50` — есть ли единый тип падения.
3. `GET /admin/outbox/rest?status=DEAD&limit=50` — проверить повторяемость по connector/path.
4. Для appointment custom connector проверить `details.httpStatus`, `details.mappedOutcome`, `details.retriable`.

### Действия
1. Если это transient 429/5xx — увеличить окно ретраев и не запускать массовый replay write-операций без idempotency key.
2. Для read-операций (`get*`) допускается batch replay после восстановления поставщика.
3. Для write-операций (`book/cancel`) запускать replay только при наличии `Idempotency-Key`.
4. Если деградация длится > 15 минут — перевести appointment profile на fallback (`GENERIC`) до стабилизации.

### Критерий стабилизации
- `ERROR_RETRYABLE` снижается до фонового уровня;
- новые записи outbox переходят в `SENT`;
- replay последних `PENDING` завершается без роста `DEAD`.
