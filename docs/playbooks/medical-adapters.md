# Playbook: Medical/MIS/EMR adapters

## Назначение

Данный playbook описывает, как в **Integration Broker** подключать медицинские источники (МИС/EMR/EHR) в виде типизированного слоя `medical`.

Слой `medical` используется для:

- получения карточки пациента;
- получения предстоящих медицинских услуг/этапов;
- построения `routing context` для СУО (VisitManager/Integration Broker), включая маршруты профосмотров.

Важно: медицинский слой **не заменяет** слой `identity`. Обычно поток выглядит так:

1) `identity` получает `clientId` и базовый профиль (ФИО/сегмент/приоритет).
2) `medical` получает медицинский контекст (patientId, услуги, этапы) и строит подсказки для маршрутизации.

## Архитектурные инварианты

1) **Секреты не должны попадать в payload/headers сообщений**.
   - Авторизация и сетевые параметры медицинских систем хранятся только в runtime-config через `restConnectors`.

2) **Запрещено логировать токены и лишние ПДн**.
   - Любые `Authorization`, `Cookie`, `access_token`, `refresh_token` должны быть санитизированы.

3) `medical` — это **типизированный слой**.
   - Groovy используется только для orchestration/routing.

## Конфигурация

В `system-config` добавляются секции:

### 1) restConnectors

```json
{
  "restConnectors": {
    "medicalGeneric": {
      "baseUrl": "http://medical.example.local",
      "auth": {
        "type": "BEARER",
        "bearerToken": "CHANGE_ME"
      }
    }
  }
}
```

### 2) medical

```json
{
  "medical": {
    "enabled": true,
    "profile": "FHIR_GENERIC",
    "connectorId": "medicalGeneric",
    "settings": {
      "note": "Расширяемые параметры для конкретной МИС"
    }
  }
}
```

- `profile` определяет тип интеграции:
  - `EMIAS_LIKE` — региональная МИС (контракты EMIAS-подобного типа)
  - `MEDESK_LIKE` — частные клиники / SaaS-подобные контракты
  - `FHIR_GENERIC` — универсальный профиль, включая FHIR-сервера

## Использование в Groovy-flow

Алиас `medical` доступен в Groovy, если в проекте подключён бин `MedicalGroovyAdapter`.

Для коротких flow можно использовать helper `medical.getPatientByKeys(keys, meta)` без ручной сборки полного request.
Для SNILS-first сценариев есть `medical.getPatientBySnils(snils, meta)` с автосборкой ключа `snils`.
Для patientId-first сценариев доступен helper `medical.getPatientByPatientId(patientId, meta)`.
Также доступен helper `medical.getUpcomingServicesByPatient(patientId, meta)` для запроса по известному patientId.
Для patient+branch сценария есть helper `medical.getUpcomingServicesByPatientAndBranch(patientId, branchId, meta)`.
Для key-only сценариев есть `medical.getUpcomingServicesByKeys(keys, meta)`.
Для сокращённого формирования medical context можно использовать `medical.buildRoutingContextSimple(patientId, keys, context, meta)`.
Для patientId-only сценария есть `medical.buildRoutingContextByPatientId(patientId, context, meta)`.

Пример precheck:

```groovy
// payload: examples/medical/medical-precheck-request.json

def res = medical.buildRoutingContext(input.payload, meta)

if (res.success()) {
  output.patientId = res.result().patient().patientId()
  output.upcoming = res.result().upcomingServices()
  output.hints = res.result().routingHints()
} else {
  output.error = res.errorCode()
  output.message = res.message()
}

return output
```

## Как добавить новую медицинскую интеграцию

1) Реализуйте `MedicalClient`:
   - `getPatient(...)`
   - `getUpcomingServices(...)`
   - `buildRoutingContext(...)`

2) Зарегистрируйте реализацию в `MedicalClients`.

3) Добавьте профиль (если нужен новый) в `RuntimeConfigStore.MedicalProfile`.

4) Добавьте в `docs/playbooks` описание конкретного адаптера и минимальные примеры payload.

## Эксплуатационные рекомендации

- Для надёжности используйте **REST outbox** для внешних вызовов (если адаптер делает внешние REST-запросы).
- Для закрытых контуров используйте service-to-service авторизацию через `restConnectors`.
- Не храните изображения/сканы документов в IB дольше необходимого, не кладите их в DLQ/outbox.
