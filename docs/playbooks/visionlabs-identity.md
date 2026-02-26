# Playbook: интеграция identity с VisionLabs (распознавание лица и автотранспорта)

## Цель

Расширить слой **identity/customerIdentity** за счёт подключаемых решений компьютерного зрения:

- распознавание клиента-человека (лицо) → получение идентификатора лица;
- распознавание автотранспорта → получение номера автомобиля.

Ключевое требование: интеграция должна быть **расширяемой** — VisionLabs является примером, но архитектура позволяет
подключать и другие решения без изменений ядра.

## Что уже реализовано в Integration Broker

В проекте доступны два провайдера `IdentityProvider`:

1) `visionlabsFace` — распознавание лица (LUNA PLATFORM)
   - поддерживаемые `type`:
     - `faceId` (идентификатор уже известен)
     - `faceImageBase64` (нужно получить `faceId` из изображения)

2) `visionlabsCars` — распознавание автотранспорта/номера (LUNA CARS)
   - поддерживаемые `type`:
     - `vehiclePlate` (номер уже известен)
     - `vehicleImageBase64` (нужно распознать номер из изображения)

Оба провайдера:

- используют `runtime-config.restConnectors` для `baseUrl + auth`;
- **не сохраняют** изображения (base64) в результатах;
- не протаскивают секреты через сообщения и не пишут их в outbox/DLQ;
- возвращают результат в расширяемом `IdentityProfile` через `externalIds` и `attributes`.

## Почему так (важные инварианты)

1) **Vision-модули чаще всего дают “внешний идентификатор”**, а не “clientId”.
   - `clientId` может быть получен через CRM/МИС/внутренние справочники.
   - Поэтому IB хранит `vision.faceId` / `vision.vehiclePlate` в `externalIds`, чтобы другие источники могли
     использовать их как ключ поиска.

2) **Секреты нельзя хранить в журналах**.
   - Авторизация и креды должны жить в конфиге коннектора.
   - В outbox/DLQ сохраняются только санитизированные заголовки.

3) **Конкретные endpoint'ы зависят от поставки**.
   - Поэтому `identifyPath/recognizePath` и `response*Pointer` задаются конфигурацией провайдера.

## Конфигурация (пример)

### 1) Добавить REST-коннекторы

```json
"restConnectors": {
  "visionlabsLuna": {
    "baseUrl": "http://visionlabs-luna:8080",
    "auth": {
      "type": "API_KEY_HEADER",
      "headerName": "X-API-Key",
      "apiKey": "CHANGE_ME"
    }
  },
  "visionlabsCars": {
    "baseUrl": "http://visionlabs-cars:8080",
    "auth": {
      "type": "API_KEY_HEADER",
      "headerName": "X-API-Key",
      "apiKey": "CHANGE_ME"
    }
  }
}
```

### 2) Включить провайдеры identity

```json
"identity": {
  "enabled": true,
  "providers": {
    "visionlabsFace": {
      "enabled": true,
      "priority": 250,
      "connectorId": "visionlabsLuna",
      "identifyPath": "/api/identify",
      "responseIdPointer": "/faceId",
      "imageFieldName": "imageBase64",
      "timeoutMs": 3000,
      "extraBody": {
        "listId": "CHANGE_ME"
      }
    },
    "visionlabsCars": {
      "enabled": true,
      "priority": 240,
      "connectorId": "visionlabsCars",
      "recognizePath": "/api/recognize",
      "responsePlatePointer": "/plate",
      "imageFieldName": "imageBase64",
      "timeoutMs": 3000
    }
  }
}
```

## Пример запроса идентификации

### Лицо (изображение → faceId)

```json
{
  "attributes": [
    {"type": "faceImageBase64", "value": "<BASE64_IMAGE>"}
  ],
  "policy": {
    "stopOnFirstClientId": false,
    "stopOnAnyMatch": true
  }
}
```

### Автомобиль (изображение → номер)

```json
{
  "attributes": [
    {"type": "vehicleImageBase64", "value": "<BASE64_IMAGE>"}
  ]
}
```

## Как подключить другое решение (не VisionLabs)

1) Реализовать новый `IdentityProvider` (например, `ntechlabFace`, `ocrPlate`, `customVision`).
2) Использовать **те же типы** (`faceImageBase64`, `vehicleImageBase64`) либо свои типы, если это нужно заказчику.
3) Управлять конкуренцией провайдеров через `priority()`:
   - более высокий `priority` — вызывается раньше.
4) Не менять ядро `IdentityService`.
