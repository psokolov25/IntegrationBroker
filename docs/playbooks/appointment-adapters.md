# Playbook: интеграция предварительной записи (appointment/booking)

## Назначение
Слой **appointment** используется для сценариев предварительной записи (включая медицинские):

- получение списка записей клиента/пациента;
- получение ближайшей актуальной записи;
- получение доступных слотов;
- бронирование/отмена записи;
- построение первичного **queue plan** для СУО и/или команд для DeviceManager.

Ключевой принцип: слой должен быть **расширяемым** и **отключаемым**.

## Контракты
Основные контракты находятся в пакете:

- `ru.aritmos.integrationbroker.appointment.AppointmentModels`

Ключи для поиска записи передаются как набор `type + value`:

- `BookingKey(type, value, attributes)`

Тип ключа не ограничивается фиксированным списком. Примеры:

- `clientId`, `patientId`
- `phone`, `email`
- `policyNumber`, `contractNumber`
- `requestId`, `externalBookingId`
- `ticketNumber`

## Конфигурация
Секция runtime-config:

```json
"appointment": {
  "enabled": true,
  "profile": "GENERIC",
  "connectorId": "appointmentGeneric",
  "settings": {}
}
```

- `enabled` — включает/выключает слой.
- `profile` — архитектурный профиль интеграции.
- `connectorId` — ссылка на `restConnectors.<id>`.
- `settings` — расширяемые параметры конкретного вендора (URL path, схемы, поля, особенности статусов и т.п.).

## Профили
Поддержаны профили (архитектурные):

- `EMIAS_APPOINTMENT`
- `MEDTOCHKA_LIKE`
- `PRODOCTOROV_LIKE`
- `YCLIENTS_LIKE`
- `NAPOPRAVKU_LIKE`
- `GENERIC`

В текущей версии:
- `GENERIC` — детерминированная заглушка (для разработки flow и демо).
- остальные — `NOT_IMPLEMENTED` (каркас, чтобы контракт был стабилен).

## Использование в Groovy-flow
Alias доступен как `appointment`.

Пример: получить ближайшую запись и построить план обслуживания:

```groovy
// clientId может приходить из identity/CRM/МИС.
def nearest = appointment.getNearestAppointment([
  keys: [[type:'clientId', value: 'CLIENT-001']],
  context: [branchId: meta.branchId]
], meta)

if (nearest.success() && nearest.result() != null) {
  def plan = appointment.buildQueuePlan([
    appointmentId: nearest.result().appointmentId(),
    keys: [[type:'clientId', value: 'CLIENT-001']],
    context: [branchId: meta.branchId, segment: (meta.segment ?: 'DEFAULT')]
  ], meta)
  output.queuePlan = plan.result()
}
```

## Как добавить новую систему записи
1) Добавить новый профиль в `RuntimeConfigStore.AppointmentProfile`.
2) Реализовать `AppointmentClient` под этот профиль.
3) Зарегистрировать реализацию в `AppointmentClients`.
4) Описать параметры `settings` (какие path/поля/правила статусов нужны).
5) Добавить пример `examples/appointment/*` и demo-flow в `examples/sample-system-config.json`.

## Безопасность
- Секреты авторизации для внешних систем задаются **только** в `restConnectors.*.auth`.
- Секреты нельзя логировать.
- Секреты нельзя сохранять в outbox/DLQ.

