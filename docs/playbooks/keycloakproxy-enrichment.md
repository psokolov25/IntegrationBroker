# Playbook: KeycloakProxy enrichment

## Назначение

KeycloakProxy enrichment дополняет inbound-обработку контекстом пользователя:

- `meta.user` — профиль пользователя (без токенов);
- `meta.principal` — удобный идентификатор пользователя (обычно `username/login`);
- опционально автоподставляет `branchId`, если он отсутствует в сообщении и разрешено настройкой.

Enrichment предназначен для маршрутизации, сегментации и предметных сценариев (СУО/медицина/CRM) и не заменяет типизированные адаптеры.

## Требования безопасности

Строго запрещено:

- логировать `Authorization`, `Cookie`, `Set-Cookie`, `access_token`, `refresh_token`, `client_secret` и подобные секреты;
- сохранять сырые токены в PostgreSQL (outbox/DLQ) и в кэш.

Разрешено:

- использовать **хэш токена** (`SHA-256`) как ключ in-memory кэша (TTL).

## Рекомендуемый режим для закрытых контуров РФ

Используйте режим `USER_ID_HEADER`:

1) В inbound-сообщение передавайте идентификатор пользователя (например, логин) в:
   - поле `userId`, либо
   - заголовок `headers["x-user-id"]`.

2) В runtime-config включите enrichment:

```json
{
  "keycloakProxy": {
    "enabled": true,
    "critical": false,
    "connectorId": "keycloakProxy",
    "modes": ["USER_ID_HEADER"],
    "userIdHeaderName": "x-user-id",
    "userByIdPathTemplate": "/authorization/users/{userName}",
    "stripTokensFromResponse": true,
    "cacheTtlSeconds": 60
  }
}
```

3) Настройте REST-коннектор `keycloakProxy` (service-to-service авторизация):

```json
{
  "restConnectors": {
    "keycloakProxy": {
      "baseUrl": "http://keycloak-proxy:8080",
      "auth": {
        "type": "API_KEY_HEADER",
        "headerName": "X-Service-Token",
        "apiKey": "CHANGE_ME"
      }
    }
  }
}
```

## Режим по токену (BEARER_TOKEN)

Используйте только если:

- в вашем контуре KeycloakProxy действительно поддерживает получение профиля по Bearer-токену;
- вы осознанно контролируете передачу токена и риск PII.

В этом режиме:

- токен используется только для одного HTTP-вызова;
- в кэше хранится только `SHA-256(token)`.

## Отладка

- Метрики enrichment доступны в `GET /api/metrics/integration`:
  - `keycloakProxyCacheHits` / `keycloakProxyCacheMisses` / `keycloakProxyErrors`.

## Инварианты для разработчиков

- Enrichment не должен менять контракт `InboundEnvelope` кроме безопасной автоподстановки `branchId/userId`.
- Enrichment не должен «подменять» idempotency.
- Любые новые поля в `meta` должны документироваться и иметь русское описание.
