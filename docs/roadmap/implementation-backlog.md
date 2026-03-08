# Roadmap реализации Integration Broker (пакет 01)

Документ фиксирует **первый пакет работ (30 задач)** для последовательной реализации по playbook'ам и AGENTS.

## Статусы

- `TODO` — задача не начата.
- `IN_PROGRESS` — в работе.
- `DONE` — завершена и влита в основной поток.

## 1) Платформенный контур (core + adapters)

1. `DONE` Ввести единый `CorrelationContext` для всех входящих каналов (REST, Webhook, polling).
2. `DONE` Добавить унифицированный генератор `X-Correlation-Id` и `X-Request-Id` для исходящих HTTP-вызовов.
3. `DONE` Добавить валидацию idempotency key по шаблону `source:flow:externalId`.
4. `DONE` Расширить `IdempotencyService` меткой причины `SKIPPED_REASON` (duplicate/locked/expired).
5. `DONE` Реализовать endpoint ручной разморозки `LOCKED` записей с аудитом.
6. `DONE` Добавить пер-connector retry policy (override global backoff).
7. `DONE` Реализовать `circuit-breaker` для проблемных REST-коннекторов.
8. `DONE` Добавить журнал смены runtime-конфигурации (кто/когда/что изменил).
9. `DONE` Реализовать dry-run режим для outbound (без фактической отправки).
10. `DONE` Поддержать batch replay для DLQ с фильтрацией по type/source/branch.

## 2) VisitManager и DataBus (контракты по AGENTS)

11. `DONE` Нормализовать VM-ответы в единый envelope `{status, headers, body, error}`.
12. `DONE` Добавить в `VisitManagerApiImpl` явную поддержку `sid` cookie для servicepoint-сценариев.
13. `DONE` Реализовать трассировку конфликтов `409` из VM в отдельную метрику.
14. `DONE` Ввести карту ошибок VM (`404/409/500`) -> кодов интеграционного домена IB.
15. `DONE` Укрепить `DataBusApiImpl` обязательными заголовками `Service-Sender` и `Send-Date`.
16. `DONE` Добавить режим multi-bus fan-out с частичным успехом и итоговым отчётом.
17. `DONE` Ввести лимит размера payload для DataBus на уровне IB-конфига.
18. `DONE` Добавить автообогащение DataBus-события параметрами `correlationId/requestId/idempotencyKey`.

## 3) Frontend Workbench (React) — старт реализации

19. `DONE` Создать каркас frontend-приложения (`apps/workbench`) на React + TypeScript.
20. `DONE` Реализовать login/logout через Keycloak (OIDC PKCE) с хранением сессии в memory.
21. `DONE` Добавить role-aware layout (admin/operator/auditor/support).
22. `DONE` Реализовать страницу мониторинга ingress/idempotency/DLQ/outbox.
23. `DONE` Реализовать страницу управления runtime-config с dry-run перед сохранением.
24. `DONE` Реализовать UI для replay DLQ/outbox (single + batch).
25. `DONE` Добавить экран Groovy Tooling (validate + emulate + trace).
26. `DONE` Добавить страницу интеграций (VM/DataBus/CRM/МИС) и health-check статусы.
27. `DONE` Внедрить экспорт инцидента в sanitized JSON/Markdown.

## 4) Документация и enablement

28. `DONE` Подготовить ролевые инструкции: пользователь, внедренец, поддержка, продажник (в простой, понятной форме).
29. `DONE` Добавить стандартный процесс локального экспорта документации из Markdown (bundle/manifest/HTML, без хранения бинарных файлов в git).
30. `DONE` Перестроить `README.md` по стандартной структуре: обзор, архитектура, запуск, эксплуатация, ссылки.

---

> Рекомендуемый темп: брать по 20–30 задач на релизный цикл, фиксируя статус и ссылку на PR для каждой задачи.

## 5) Пакет 02 (следующие 30 задач)

31. `DONE` Убрать оставшиеся hardcoded-строки в Replay/Integrations и вынести в i18n-словари.
32. `DONE` Локализовать пустое состояние аудита runtime-config.
33. `DONE` Реализовать batch replay DLQ в Workbench через `/admin/dlq/replay-batch`.
34. `DONE` Локализовать контент инцидента (preview/markdown export labels).
35. `DONE` Добавить UI-индикатор состояния outbound dry-run override.
36. `DONE` Добавить управление outbound dry-run override из Workbench.
37. `DONE` Показать effective retry policy по REST-коннекторам в UI.
38. `DONE` Показать circuit-breaker состояние REST-коннекторов в UI.
39. `DONE` Добавить фильтры списка DLQ по `type/source/branch` в UI-листинге.
40. `DONE` Добавить пагинацию для DLQ/outbox списков.
41. `DONE` Добавить автorefresh режим для мониторинга (toggle + interval).
42. `DONE` Добавить экспорт audit runtime-config в JSON.
43. `DONE` Добавить подтверждение перед batch replay (confirm dialog).
44. `DONE` Добавить dry-run предпросмотр diff конфигурации до сохранения.
45. `DONE` Добавить UI-валидацию формата фильтра batch replay.
46. `DONE` Добавить метрики replay (ok/failed/locked/dead) на dashboard.
47. `DONE` Добавить E2E smoke для маршрутов Workbench.
48. `DONE` Добавить unit-тесты для workbenchApi replay-batch.
49. `DONE` Добавить unit-тесты для локализации runtime/replay screen.
50. `DONE` Добавить fallback-тексты для неизвестных исходов replay.
51. `DONE` Добавить серверный endpoint health-check интеграций с деталями VM/DataBus.
52. `DONE` Добавить ручной trigger health-check из UI с timestamp последней проверки.
53. `DONE` Добавить фильтр по статусам интеграций (UP/DEGRADED/DOWN).
54. `DONE` Добавить поддержку копирования correlationId из replay таблиц.
55. `DONE` Добавить UI для просмотра replay result payload.
56. `DONE` Добавить API client wrapper для `OutboundAdminController`.
57. `DONE` Добавить read-only экран runtime-config revisions.
58. `DONE` Добавить server-side limit guard и UI hint для больших batch replay.
59. `DONE` Добавить audit события для replay batch запросов.
60. `DONE` Подготовить release notes по пакету 02 и обновить README ссылками.


## 6) Пакет 03 (40 задач)

61. `DONE` Appointment custom connector: автогенерация `X-Correlation-Id`/`X-Request-Id` при пустом meta (unit coverage).
62. `DONE` Appointment custom connector: дефолтное правило `429/5xx -> ERROR_RETRYABLE` даже без `errorMapping`.
63. `DONE` Appointment custom connector: возвращать `correlationId/requestId` в `details` исхода вызова.
64. `DONE` Добавить пример `appointment-custom-client-settings-extended.json` для `getAppointments/getAvailableSlots/bookSlot`.
65. `DONE` Добавить пример `appointment-custom-client-request-rate-limit.json`.
66. `DONE` Добавить пример `appointment-custom-client-request-slots.json`.
67. `DONE` RuntimeConfig: добавить server-side валидацию обязательных operation полей (`method/path`).
68. `DONE` RuntimeConfig: добавить предупреждение на пустой `headersTemplate` для write-операций.
69. `DONE` Outbox: добавить фильтр по `connectorId` в admin list endpoint.
70. `DONE` Outbox: добавить endpoint массовой отмены queued записей по фильтру.
71. `DONE` DLQ: добавить preview sanitized payload по `id`.
72. `DONE` DLQ: добавить массовую маркировку записей как ignored с reason.
73. `DONE` Idempotency: endpoint поиска по `externalMessageId`.
74. `DONE` Idempotency: экспорт audit trail в JSON.
75. `DONE` VisitManagerApi: покрыть интеграционными тестами `sid` cookie для servicepoint enter/exit.
76. `DONE` VisitManagerApi: добавить mapping 207/401 для servicepoint вызовов.
77. `DONE` DataBusApi: добавить envelope validator для `publishEventRoute`.
78. `DONE` DataBusApi: добавить unit-тесты на mandatory headers в route/request/response.
79. `DONE` DataBusApi: добавить limit guard для headers payload size.
80. `DONE` Core retry: поддержать jitter для экспоненциального backoff.
81. `DONE` Core circuit breaker: добавить half-open probe policy в runtime-config.
82. `DONE` Core observability: добавить latency histogram для outbound connectors.
83. `DONE` Security: скрывать токены в admin endpoints ответах.
84. `DONE` Security: добавить тесты на READONLY mode для admin POST/PUT.
85. `DONE` Keycloak proxy: добавить fallback claim strategy для `preferred_username`.
86. `DONE` Inbound API: поддержать `X-Request-Id` passthrough при наличии.
87. `DONE` Inbound API: добавить опциональный rate-limit per source.
88. `DONE` Workbench monitoring: добавить график latencies по коннекторам.
89. `DONE` Workbench replay: добавить просмотр sanitized payload diff до/после.
90. `DONE` Workbench integrations: добавить drill-down по degraded причинам.
91. `DONE` Workbench runtime config: подсветка risky changes перед сохранением.
92. `DONE` Workbench auth: добавить session-expiry banner + автоlogout.
93. `DONE` Docs user-guide: добавить раздел по correlation/idempotency best practices.
94. `DONE` Docs support-runbook: добавить playbook по 429/5xx деградациям.
95. `DONE` Docs implementation-guide: добавить matrix retry safety per operation type.
96. `DONE` Examples: добавить end-to-end сценарий VM createVisit -> DB publishEvent.
97. `DONE` Examples: добавить сценарий fallback на generic appointment profile.
98. `DONE` Tests: контрактные тесты examples JSON (schema-smoke).
99. `DONE` Tests: добавить regression suite для sanitizer на headers/body snippets.
100. `DONE` Release notes: подготовить пакет 03 summary и checklist внедрения.

## 7) Пакет 04 (стартовый backlog по шаблонам решений и переносимости конфигурации)

101. `DONE` Спроектировать доменную модель Integration Branch Template: composition из runtime YAML-конфигурации, flow YAML и Groovy-скриптов с метаданными решения/подгруппы заказчиков.
102. `DONE` Поддержать экспорт шаблона ветки в архив `*.ibt` (single-branch template) с манифестом версии формата и контрольными суммами файлов.
103. `DONE` Поддержать экспорт набора шаблонов в архив `*.ibts` (template set) для библиотеки решений по нескольким подгруппам заказчиков.
104. `DONE` Реализовать импорт `*.ibt`/`*.ibts` через dry-run валидацию: совместимость версии, целостность архива, schema-check YAML и compile-check Groovy.
105. `DONE` Добавить стратегию merge при импорте: `replace`, `merge`, `keep-local` + отчёт конфликтов и список затронутых артефактов.
106. `DONE` Зафиксировать человеко-читаемый формат артефактов: настройки только в `*.yml` (с комментариями), Groovy — отдельными `*.groovy` файлами без встраивания в JSON.
107. `DONE` Добавить git-friendly правила структуры шаблона: детерминированная сортировка полей/файлов, стабильные имена директорий и нормализованный line-ending.
108. `DONE` Добавить CLI/Admin endpoint для упаковки/распаковки шаблонов (import/export) с обязательными `correlationId/requestId` в аудит-логе операций.
109. `DONE` Реализовать механизм параметризации «базовое решение -> кастомизация заказчика» (overrides layer), чтобы шаблоны подгруппы можно было финально донастраивать per-customer.
110. `DONE` Добавить версионирование шаблонов (`templateVersion`, `compatibilityRange`) и semver-policy для безопасного обновления решений в эксплуатации.
111. `DONE` Добавить unit/integration тесты round-trip (`export -> import`) для `ibt/ibts`, включая кейсы с комментариями YAML и проверкой неизменности Groovy-скриптов.
112. `DONE` Подготовить документацию и примеры репозитория шаблонов для Git: recommended layout, naming conventions, release process и rollback strategy.

## 8) Пакет 05 (инициализация следующей волны, 40+ задач)

113. `DONE` CRM: включить временный fallback vendor-профилей (BITRIX24/AMOCRM/RETAILCRM/MEGAPLAN) на `GENERIC` с диагностикой профиля в `raw`.
114. `DONE` Appointment: включить fallback профилей EMIAS/MEDTOCHKA/PRODOCTOROV/YCLIENTS/NAPOPRAVKU на `GENERIC` с деталями `requestedProfile/executionProfile/fallback`.
115. `DONE` Medical: включить fallback профилей EMIAS_LIKE/MEDESK_LIKE на `FHIR_GENERIC` с enrichment `details`.
116. `DONE` Тесты CRM: добавить проверку fallback-метаданных для vendor-профиля.
117. `DONE` Тесты Appointment: добавить проверку fallback-метаданных для делегирующего профиля.
118. `DONE` Тесты Medical: добавить проверку fallback-метаданных для делегирующего профиля.
119. `DONE` Core: добавить endpoint просмотра текущего snapshot retry+jitter+circuit-breaker в одной выдаче.
120. `DONE` Core: добавить export retry/circuit policy diff между runtime и baseline.
121. `DONE` Core: добавить dry-run replay для outbox с выборочным suppress publish.
122. `DONE` VM adapter: поддержать POST `/entrypoint/.../visits/parameters` с расширенным audit trail.
123. `DONE` VM adapter: добавить нормализацию `207` для servicepoint auto-call start/cancel.
124. `DONE` VM adapter: добавить sticky `sid` propagation policy для servicepoint сценариев.
125. `DONE` DataBus adapter: добавить guard на размер `RouteEvent.dataBusUrls`.
126. `DONE` DataBus adapter: добавить валидацию RFC1123 `Send-Date` перед отправкой.
127. `DONE` DataBus adapter: добавить опцию mandatory `Service-Destination` per target profile.
128. `DONE` Idempotency: добавить endpoint удаления `EXPIRED` записей по фильтру периода.
129. `DONE` Idempotency: добавить метрику доли конфликтов duplicate/locked per source.
130. `DONE` Idempotency: добавить batch unlock с dry-run отчётом.
131. `DONE` DLQ: добавить endpoint перемещения ignored обратно в queued.
132. `DONE` DLQ: добавить фильтр `ignoredReason` в list API.
133. `DONE` DLQ: добавить policy auto-ignore для известных non-retriable ошибок.
134. `DONE` Outbox: добавить endpoint priority bump для queued сообщений.
135. `DONE` Outbox: добавить dedup fingerprint preview в API.
136. `DONE` Outbox: добавить выборочный replay по `correlationId`.
137. `DONE` Runtime config: добавить schema-валидацию для appointment/medical subprofiles.
138. `DONE` Runtime config: добавить подсветку потенциального profile fallback в validation report.
139. `DONE` Runtime config: добавить экспорт effective config с опцией redact secrets.
140. `DONE` Workbench: добавить виджет по fallback-активациям (CRM/Medical/Appointment).
141. `DONE` Workbench: добавить drill-down страницы профиля адаптера с last errors.
142. `DONE` Workbench: добавить мастер импорта template set с preview конфликтов.
143. `DONE` Workbench: добавить быстрый фильтр инцидентов по `correlationId`.
144. `DONE` Workbench: добавить таблицу последних runtime-audit изменений с diff viewer.
145. `DONE` Security: добавить строгий deny-list headers для admin proxy endpoints.
146. `DONE` Security: расширить sanitizer на вложенные массивы бинарных blob-полей.
147. `DONE` Security: добавить тесты на маскирование `Cookie sid` в debug traces.
148. `DONE` Docs: обновить playbook CRM адаптеров под fallback-поведение prerelease.
149. `DONE` Docs: обновить medical playbook с профилями fallback и рисками.
150. `DONE` Docs: добавить rollout-checklist для переключения с fallback на real connector.
151. `DONE` Examples: добавить flow-пример CRM sync+case через fallback vendor profile.
152. `DONE` Examples: добавить flow-пример medical routing для emias-like профиля.
153. `DONE` Examples: добавить flow-пример appointment queue plan через medtochka-like профиль.
154. `DONE` Tests: добавить контрактный набор для fallback outcome details across layers.
155. `DONE` Tests: добавить regression на детерминизм timestamp/id для fallback execution.
156. `DONE` Release notes: сформировать пакет 05 alpha summary.
