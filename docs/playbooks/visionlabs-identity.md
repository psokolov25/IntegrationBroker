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


## Детализация: биометрическая идентификация в РФ (reference landscape)

Ниже — практическая матрица для внедрений в РФ. В каждом кейсе Integration Broker работает одинаково:

- принимает `attributes` из inbound;
- вызывает профильный `IdentityProvider`;
- возвращает унифицированный `IdentityProfile` (`clientId`, `externalIds`, `attributes`, `matches`).

### 1) VisionLabs (базовый/первый приоритет)

Поддерживается из коробки:

- `visionlabsFace` (`faceId`, `faceImageBase64`)
- `visionlabsCars` (`vehiclePlate`, `vehicleImageBase64`)

Рекомендуемый приоритет в mixed-ландшафте: **240–260** (выше generic OCR, но ниже hard business-ключей вроде contractNumber).

### 2) NtechLab (FindFace / face recognition)

Типовой профиль использования:

- входные типы: `faceImageBase64`, опционально `faceEmbedding`;
- выход в `externalIds`: `ntechlab.personId`, `ntechlab.watchlistId`;
- полезные `attributes`: `confidence`, `cameraId`, `matchType`.

Рекомендация: отдельный провайдер `ntechlabFace` с тем же контрактом, что и `visionlabsFace`, чтобы flow не менялся при переключении вендора.

### 3) 3DiVi (face SDK / on-prem biometric stacks)

Типовой профиль:

- вход: `faceImageBase64`;
- выход: `3divi.faceTemplateId` или `3divi.personId`;
- в `matches` хранить top-N кандидатов и score.

Рекомендация: использовать как on-prem fallback-провайдер при повышенных требованиях к локальной обработке.

### 4) Tevian (face analytics / identification)

Типовой профиль:

- вход: `faceImageBase64`;
- выход: `tevian.subjectId`;
- `attributes`: `liveness`, `quality`, `ageBand` (если доступно и разрешено политикой ПДн).

Рекомендация: выносить в отдельный провайдер `tevianFace` и включать policy-флаг, какие атрибуты разрешено возвращать downstream.

### 5) Smart Engines / OCR-стек для документов и номерных знаков

Для сценариев, где биометрия дополняется OCR:

- вход: `documentImageBase64` или `vehicleImageBase64`;
- выход: `externalIds.documentNumber` / `externalIds.vehiclePlate`;
- использовать как вторичный источник в той же identity-сессии.

Важно: OCR-провайдеры не заменяют face-биометрию, но повышают точность при мультифакторной идентификации.

## Рекомендуемая стратегия оркестрации нескольких провайдеров

1. Сначала deterministic-ключи (`contractNumber`, `phone`, `snils`, `policyNumber`) при наличии.
2. Затем face-провайдер с наивысшим качеством в контуре (обычно VisionLabs/NtechLab).
3. Далее on-prem fallback (например, 3DiVi/Tevian) по timeout/error основного.
4. В конце OCR/vehicle-провайдеры как дополнительный сигнал.

Для этого используйте:

- `priority()` провайдера;
- policy `stopOnFirstClientId` / `stopOnAnyMatch`;
- ограничение таймаутов на провайдер (обычно 1.5–3.0s на synchronous edge-сценарий).

## Минимальный стандарт нормализации для всех biometric-провайдеров

Чтобы Groovy-flow и downstream сервисы были независимы от вендора:

- `externalIds.vision.faceId` / `externalIds.ntechlab.personId` / ... — вендорные ключи;
- `attributes.biometric.modality` = `face` | `vehicle` | `document`;
- `attributes.biometric.confidence` = `0..1`;
- `attributes.biometric.provider` = `<providerId>`;
- `matches[]` содержит top-N кандидатов в едином формате (`id`, `score`, `meta`).

## Комплаенс и эксплуатация (РФ)

- Биометрические данные и изображения считаются чувствительными ПДн: не хранить raw base64 в outbox, DLQ, audit-log.
- Хранить в логах только correlation/request/idempotency + технический статус провайдера.
- Для интеграций с ГИС/ЕБС (если применимо в проекте) добавлять отдельный provider-адаптер, не смешивая его с коммерческими SDK в одном классе.
- Для аудита решений желательно сохранять только: `providerId`, `decision`, `confidenceBucket`, `processingMs`, без сырого изображения.

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
