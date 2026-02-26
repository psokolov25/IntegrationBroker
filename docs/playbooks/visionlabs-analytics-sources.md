# VisionLabs (LUNA PLATFORM): подключение источников событий аналитики

Данный регламент описывает, как подключить Integration Broker к результатам видеоаналитики VisionLabs (LUNA PLATFORM) через разные механизмы доставки (callback types):

* `http`
* `luna-ws-notification`
* `luna-event`
* `luna-kafka`

Основание: VisionLabs допускает отправку/сохранение результатов аналитики через механизм Callback'ов с указанными типами.
См. документацию: https://docs.visionlabs.ru/luna/v.5.137.0/lp-distribution/administrator-manual/additional-information/

## Общие принципы Integration Broker

1. **Все события аналитики нормализуются в `InboundEnvelope`** и проходят общий pipeline (idempotency → enrichment → flow → DLQ/outbox).
2. **Секреты и токены запрещено сохранять и логировать.**
   * Заголовки `Authorization/Cookie/Set-Cookie/...` санитизируются (`***`).
   * Для проверки источника используется shared-secret (header/query-param), но его значение не логируется.
3. **Идентификатор сообщения (`messageId`) должен быть устойчивым.**
   * Если в payload есть `event_id/id` — используем его.
   * Иначе используется хэш payload (стабилен при повторной доставке).

## 1) HTTP callback (`http`)

### Когда использовать

* Самый простой и типовой путь: агент делает HTTP POST на указанный URL.

### Endpoint в Integration Broker

* `POST /api/visionlabs/callback`

### Безопасность

Рекомендуется включить shared-secret:

* `visionLabsAnalytics.http.sharedSecretHeaderName` (например, `X-VisionLabs-Token`)
* `visionLabsAnalytics.http.sharedSecret` — значение секрета

### Конфигурация (пример)

См. `src/main/resources/examples/sample-system-config.json`, секция `visionLabsAnalytics.http`.

## 2) WebSocket уведомления (`luna-ws-notification`)

### Когда использовать

* Когда требуется доставка событий почти в реальном времени по WebSocket.

### Endpoint в Integration Broker

* `ws://<host>:<port>/ws/visionlabs/notifications?token=...`

### Важные условия VisionLabs

Если используются WebSocket'ы, то в VisionLabs должен быть включён сервис Sender и должно быть установлено соединение (ws handshake для general events).

### Безопасность

* Рекомендуется использовать query-param `token` как shared-secret.
* Значение `token` не логируется.

## 3) Сохранение в Events (`luna-event`)

### Когда использовать

* Когда события сохраняются в Events, а интеграции удобнее/надёжнее забирать их pull-моделью.

### Как работает в Integration Broker

* Включается poller `VisionLabsEventsPoller`.
* Poller ходит в API ("get general events") с фильтром по `stream_id` и забирает новые события.
* Последний обработанный `event_id` хранится в PostgreSQL в таблице `ib_visionlabs_events_checkpoint`.

### Требования

* Должен быть настроен REST-коннектор `restConnectors.visionlabsEvents` (baseUrl + auth).
* Должны быть указаны `streamIds`.

### Конфигурация (пример)

См. `src/main/resources/examples/sample-system-config.json`, секция `visionLabsAnalytics.events`.

> Внимание: конкретный путь и параметры запроса к Events могут отличаться в поставке/инсталляции.
> Поэтому `path/streamIdParam/afterIdParam/listJsonPointer/idJsonPointer` сделаны конфигурируемыми.

## 4) Kafka (`luna-kafka`)

### Когда использовать

* Для массовых событий, высокой пропускной способности и интеграции с Kafka-first инфраструктурой.

### Как работает в Integration Broker

* Включается Kafka listener `VisionLabsKafkaListener`.
* Сообщения из topic нормализуются и обрабатываются общим pipeline.

### Конфигурация

Runtime-config:

* `visionLabsAnalytics.kafka.enabled=true`
* `visionLabsAnalytics.kafka.topic`
* `visionLabsAnalytics.kafka.groupId`

Transport-конфигурация Kafka задаётся в `application.yml` / окружении (bootstrap servers, security/SASL и т.п.).

Параметры настройки callback'а `luna-kafka` со стороны VisionLabs включают `servers`, `topic`, `protocol`, `username`, `password` (SASL).

## Проверка интеграции

1. Включите `visionLabsAnalytics.enabled=true`.
2. Выберите один механизм (http/ws/events/kafka) и включите только его.
3. Настройте flow по типам `visionlabs.analytics.*`.
4. Отправьте тестовое событие (пример в `src/main/resources/examples/visionlabs/analytics-callback-sample.json`).

## Инварианты, которые нельзя нарушать

* Нельзя сохранять/логировать токены, cookies и секреты.
* Нельзя переносить изображения (base64) в DLQ/outbox — используйте внешнее хранилище.
* `LOCKED` (идемпотентность) не считается poison message.
