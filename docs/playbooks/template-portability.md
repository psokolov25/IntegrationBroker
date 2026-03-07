# Playbook: переносимость шаблонов Integration Branch (`*.ibt` / `*.ibts`)

## Цель

Упростить перенос интеграционного решения между стендами/заказчиками через архивы шаблонов:

- `*.ibt` — один шаблон ветки;
- `*.ibts` — набор шаблонов (template set).

## Формат архива (v1.0.0)

Архив является ZIP-контейнером с человеко-читаемыми артефактами:

- `manifest.yml` — метаданные формата, версии, checksums;
- `runtime/runtime-config.yml` — runtime-конфигурация;
- `flows/<flowId>/flow.yml` — описание flow;
- `flows/<flowId>/script.groovy` — Groovy orchestration-код;
- `overrides/customer-overrides.yml` — слой кастомизации per-customer.

Для `*.ibts` используется корень `templates/<branchId>/...`.

## Git-friendly правила

- YAML и Groovy хранятся отдельными файлами (без inline JSON-строк со скриптами).
- Поля и файлы экспортируются в детерминированном порядке.
- Line endings нормализуются к `\n`.
- В манифесте пишутся SHA-256 checksums для контроля целостности.

## Версионирование

`manifest.yml` содержит:

- `format`: `ibt` или `ibts`;
- `formatVersion`: версия формата архива (`1.0.0`);
- `templateVersion`: версия конкретного шаблона (SemVer);
- `compatibilityRange`: диапазон совместимости по формату.

## Admin API

### Экспорт

`POST /admin/templates/export`

Тело запроса:

```json
{
  "branchId": "branch-77",
  "solution": "retail",
  "customerGroup": "group-a",
  "templateVersion": "1.2.3",
  "templateSet": false
}
```

Ответ: бинарный архив (`application/octet-stream`) + `Content-Disposition` с именем `*.ibt`/`*.ibts`.

### Import dry-run

`POST /admin/templates/import-dry-run` (200 при валидном запросе, 400 при невалидном base64)

Тело запроса:

```json
{
  "archiveBase64": "<base64 zip>",
  "mergeStrategy": "merge",
  "branchIdHint": "branch-77"
}
```

Проверки dry-run:

1. Совместимость `formatVersion`.
2. Валидность `format`: только `ibt`/`ibts`.
3. Проверка структуры архива: один `manifest.yml`, корректный root (`ibt` в корне, `ibts` в `templates/<branchId>/`).
4. Целостность по checksums (включая проверку, что все файлы описаны в `manifest.yml`).
5. Schema-check YAML (parse + структура).
6. Compile-check Groovy скриптов.
7. Валидация `mergeStrategy`: `replace|merge|keep-local`.
8. Валидация base64-контейнера архива (`Invalid base64 archive payload`).

### Аудит операций

`GET /admin/templates/audit?limit=100`

Каждая запись содержит:

- operation (`export` / `import-dry-run`),
- `correlationId`,
- `requestId`,
- `branchId`,
- details.

## Стратегии merge

Поддерживаются стратегии:

- `replace` — целиком заменять локальные артефакты шаблона;
- `merge` — объединять по ключам/файлам;
- `keep-local` — сохранять локальные версии, импортировать только отсутствующие.

На текущем этапе dry-run также возвращает список конфликтующих файлов (по текущему baseline).

### Overrides layer

`customer-overrides.yml` хранит параметризацию «базовое решение -> кастомизация заказчика».
При импорте слой учитывается как отдельный артефакт и участвует в conflict report.

В контроллере аудит ограничен кольцевым буфером (до 500 записей) и поддерживает выдачу по limit.

## Рекомендуемый layout git-репозитория шаблонов

```text
templates/
  branch-77/
    manifest.yml
    runtime/
      runtime-config.yml
    flows/
      flow-visit-created/
        flow.yml
        script.groovy
```

## Release process

1. Обновить `templateVersion`.
2. Выполнить export (`/admin/templates/export`).
3. Прогнать import dry-run на целевом стенде.
4. Зафиксировать архив и распакованный layout в git-тег релиза.

## Rollback strategy

- Хранить предыдущие версии `*.ibt`/`*.ibts` в артефакт-репозитории.
- Для rollback использовать import dry-run предыдущей версии, затем apply/import в согласованное окно изменений.


## Наблюдаемость API

`/admin/templates/import-dry-run` возвращает также `correlationId` и `requestId` (echo или сгенерированные значения), чтобы связать dry-run ответ с аудит-логом.
