# Playbook: добавление нового способа идентификации клиента

## Цель

Слой **identity/customerIdentity** предназначен для идентификации клиента по расширяемой модели:

- идентификатор = `type + value`
- дополнительные параметры = `attributes`

Ключевое требование: **ядро Integration Broker не должно знать о конкретных способах идентификации**.
Новый способ добавляется через новый провайдер `IdentityProvider` и (при необходимости) конфигурацию.

## Что уже есть в проекте

- Контракты: `ru.aritmos.integrationbroker.identity.IdentityModels`
- Интерфейс провайдера: `ru.aritmos.integrationbroker.identity.IdentityProvider`
- Реестр провайдеров: `ru.aritmos.integrationbroker.identity.IdentityProviderRegistry`
- Сервис оркестрации/агрегации: `ru.aritmos.integrationbroker.identity.IdentityService`
- Демо-провайдер: `StaticIdentityProvider` (для примеров, не для production)

Дополнительно:

- Провайдеры компьютерного зрения (пример интеграции с VisionLabs):
  - `visionlabsFace` (типы: `faceId`, `faceImageBase64`)
  - `visionlabsCars` (типы: `vehiclePlate`, `vehicleImageBase64`)
  - регламент настройки: `docs/playbooks/visionlabs-identity.md`

## Общий алгоритм (коротко)

1. Клиент (или flow) формирует `IdentityRequest` с набором `attributes`.
2. `IdentityService` выбирает провайдеров по `attribute.type` и вызывает их по приоритету.
3. Результаты агрегируются в `IdentityProfile`.
4. Сегмент нормализуется и вычисляется `priorityWeight` (если не пришёл от источника).

## Шаг 1. Создать новый провайдер

Создайте класс и реализуйте интерфейс `IdentityProvider`.

Минимальные правила:

- **Не логировать** чувствительные значения идентификаторов, если они могут являться PII.
- Не хранить токены/секреты в результатах.
- Любые секреты должны читаться из конфигурации коннекторов, а не передаваться через сообщения.

Пример каркаса:

```java
@Singleton
public class ContractNumberIdentityProvider implements IdentityProvider {

  @Override
  public String id() {
    return "contractNumber";
  }

  @Override
  public int priority() {
    return 200;
  }

  @Override
  public boolean supportsType(String type) {
    return "contractNumber".equalsIgnoreCase(type);
  }

  @Override
  public Optional<IdentityModels.IdentityProfile> resolve(IdentityModels.IdentityAttribute attribute,
                                                         IdentityModels.IdentityRequest request,
                                                         ProviderContext ctx) {
    // 1) attribute.value = номер договора
    // 2) выполнить поиск в источнике (CRM/справочник/шина)
    // 3) вернуть нормализованный профиль
    return Optional.empty();
  }
}
```

## Шаг 2. Конфигурация провайдера (опционально)

Если провайдеру нужны параметры (URL, креды, таймауты и т.д.), рекомендуемый подход:

- хранить конфиг в `runtime-config.restConnectors` (или в отдельной секции runtime-config)
- **не писать секреты** в outbox/DLQ

Если вам нужен провайдерный конфиг именно в `runtime-config.identity.providers`, используйте схему:

```json
"identity": {
  "providers": {
    "<providerId>": {
      "enabled": true,
      "...": "..."
    }
  }
}
```

И внутри провайдера извлекайте свой кусок:

- `ctx.cfg().identity().providers().get("<providerId>")`

## Шаг 3. Добавить пример

1. Добавьте пример запроса:
   - `src/main/resources/examples/identity/...`
2. Добавьте пример flow, который использует `identity.resolve(...)`:
   - `src/main/resources/examples/sample-system-config.json`

## Шаг 4. Проверка

- Юнит-тест провайдера (минимум: позитивный кейс + негативный кейс)
- Проверка, что результат не содержит секретов
- Проверка нормализации сегмента

Команда:

```bash
./mvnw -q test
```

## Пример вызова из Groovy-flow

```groovy
// request как Map (удобно в Groovy)

def req = [
  attributes: [
    [type: 'contractNumber', value: 'CN-001'],
    [type: 'phone', value: '+79990000001']
  ],
  policy: [
    stopOnFirstClientId: true,
    stopOnAnyMatch: false
  ]
]

def res = identity.resolve(req)
output.clientId = res.profile.clientId
output.segment = res.profile.segment
return output
```
