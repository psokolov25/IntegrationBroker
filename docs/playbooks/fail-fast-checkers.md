# Playbook: fail-fast проверки зависимостей

## Цель

Fail-fast проверки нужны, чтобы **на старте** обнаруживать недоступность критичных внешних компонентов и (при необходимости) **останавливать запуск сервиса**, а не получать «тихие» ошибки в рантайме.

В Integration Broker проверки реализованы как listener на событие запуска приложения и управляются настройками `integrationbroker.startup-checks.*`.

## Ключевые принципы

1. **Проверки настраиваемые**: включаются и настраиваются отдельно для разных типов зависимостей.
2. **Критичность отделена от fail-fast**:
   - `critical=true` означает, что зависимость важна для корректной работы.
   - `fail-fast=true` означает, что при недоступности критичной зависимости сервис должен **падать** на старте.
3. **Некритичные зависимости не валят сервис**: ошибка логируется, но запуск продолжается.
4. **Никаких секретов в логах**: токены/пароли/ключи не логируются.

## Где находится реализация

- `ru.aritmos.integrationbroker.checks.StartupChecksRunner` — исполняет проверки на старте
- `RemoteConfigChecker` — проверка доступности SystemConfiguration (remote-config)
- `RestConnectorsChecker` — проверка health endpoint для `restConnectors`
- `MessagingProvidersChecker` — проверка наличия и health-check провайдеров сообщений

## Настройка (application.yml)

Пример базовой конфигурации:

```yaml
integrationbroker:
  startup-checks:
    enabled: true

    remote-config:
      enabled: ${integrationbroker.remote-config.enabled:false}
      critical: true
      fail-fast: true

    rest-connectors:
      enabled: false
      critical: false
      fail-fast: false
      health-path: /health
      timeout-ms: 1500
      expected-status: [200, 204]

    messaging-providers:
      enabled: false
      critical: true
      fail-fast: true
      required-provider-ids: []
```

## Remote config (SystemConfiguration)

### Когда включать

- Если сервис **не может работать** без актуальной конфигурации flow.

### Рекомендуемая политика

- `critical=true`
- `fail-fast=true`

Тогда при недоступности SystemConfiguration сервис **не стартует**.

## REST-коннекторы

### Когда включать

- Если у всех коннекторов есть согласованный health endpoint (`/health` или другой).

### На что обратить внимание

- Некоторые системы требуют авторизацию даже для health — тогда в `restConnectors[*].auth` настраивается service-to-service доступ.
- Проверка не должна использовать пользовательские токены.

## Messaging providers

### Когда включать

- Когда вы используете конкретные провайдеры и хотите падать, если они не зарегистрированы или недоступны.

### Как задать обязательные провайдеры

```yaml
integrationbroker:
  startup-checks:
    messaging-providers:
      enabled: true
      critical: true
      fail-fast: true
      required-provider-ids: ["kafka"]
```

## Тестовый профиль

В `application-test.yml` проверки отключены:

```yaml
integrationbroker:
  startup-checks:
    enabled: false
```

Это сделано специально, чтобы unit/integration тесты не зависели от доступности внешних систем.
