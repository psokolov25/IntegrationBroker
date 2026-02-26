# Playbook: CRM адаптеры (typed слой)

Этот регламент описывает, как в Integration Broker устроен слой **crm** и как добавлять/подключать конкретные CRM (Bitrix24/amoCRM/RetailCRM/Мегаплан/и др.).

## 1. Назначение слоя crm

Слой **crm** предназначен для типизированных операций с CRM:

- поиск клиента
- создание/обновление карточки клиента
- создание лида
- создание задачи
- добавление заметки
- создание сервисного обращения
- комплексная операция: «синхронизировать клиента и создать обращение»

Ключевой принцип: **crm не заменяет identity**.

- `identity` — слой идентификации клиента, который агрегирует результат из нескольких источников.
- `crm` — один из возможных backend-источников и/или канал фиксации кейсов/задач.

## 2. Конфигурация runtime-config

Секция `crm` в `RuntimeConfig`:

- `enabled` — включить/выключить слой
- `profile` — профиль CRM (тип интеграции)
- `connectorId` — id REST-коннектора из `restConnectors`
- `settings` — произвольные параметры конкретной CRM (не ограничены фиксированным набором)

Пример (каркас):

```json
{
  "crm": {
    "enabled": true,
    "profile": "GENERIC",
    "connectorId": "crmGeneric",
    "settings": {
      "note": "здесь будут настройки конкретного API"
    }
  }
}
```

## 3. Безопасность: секреты и логирование

- Секреты (API ключи, bearer tokens, basic auth) задаются **только** в `restConnectors`.
- В outbox/DLQ **не сохраняются** секреты, токены, заголовки `Authorization/Cookie/Set-Cookie`.
- Не допускается логирование токенов и «сырых» персональных данных.

## 4. Как добавить новую CRM (новый профиль)

### Шаг 1. Реализовать `CrmClient`

Создайте бин (Micronaut `@Singleton`) и реализуйте интерфейс:

- `ru.aritmos.integrationbroker.crm.CrmClient`

Обязательно:

- вернуть корректный `profile()`
- использовать `cfg.crm().settings()` для параметров
- использовать `cfg.restConnectors()` и `cfg.crm().connectorId()` для сетевых вызовов

### Шаг 2. Зарегистрировать бин

Если бин есть в classpath и помечен `@Singleton`, он автоматически попадёт в `CrmClientRegistry`.

### Шаг 3. Подключить в конфиге

Установите:

- `crm.enabled=true`
- `crm.profile=<ВАШ_ПРОФИЛЬ>`

## 5. Как использовать CRM из Groovy-flow

В Groovy доступен alias `crm`.

Пример:

```groovy
// meta желательно передавать вторым аргументом, если оно нужно CRM для контекста.

def res = crm.findCustomer([
  keys: [[type: 'phone', value: '+79990000001']]
], meta)

if (res.success()) {
  output.crmCustomerId = res.result().crmCustomerId()
  output.segmentAlias = res.result().segmentAlias()
} else {
  output.crmError = res.errorCode()
}

return output
```

## 6. Definition of Done для CRM адаптера

- Реализованы необходимые операции (минимум: `findCustomer`, `createServiceCase`)
- Отсутствуют `System.out/System.err/printStackTrace/@SuppressWarnings` в `src/main/java`
- JavaDoc и пояснения **на русском языке**
- Отсутствует логирование секретов и токенов
- Добавлены примеры (`src/main/resources/examples/crm/*`)
- Добавлен/обновлён playbook
