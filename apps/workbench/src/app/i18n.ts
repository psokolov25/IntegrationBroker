export const POPULAR_LOCALE_ORDER = [
  'ru-RU',
  'en-EN',
  'zh-CN',
  'hi-IN',
  'es-ES',
  'ar-SA',
  'fr-FR',
  'bn-BD',
  'pt-BR',
  'id-ID',
  'ur-PK'
] as const;

export type LocaleCode = (typeof POPULAR_LOCALE_ORDER)[number];

export const SUPPORTED_LOCALES: LocaleCode[] = ['ru-RU', 'en-EN'];
export const DEFAULT_LOCALE: LocaleCode = 'ru-RU';

export const LOCALE_LABELS: Record<LocaleCode, string> = {
  'ru-RU': 'Русский (Россия)',
  'en-EN': 'English (Global)',
  'zh-CN': '中文 (简体)',
  'hi-IN': 'हिन्दी',
  'es-ES': 'Español',
  'ar-SA': 'العربية',
  'fr-FR': 'Français',
  'bn-BD': 'বাংলা',
  'pt-BR': 'Português (Brasil)',
  'id-ID': 'Bahasa Indonesia',
  'ur-PK': 'اردو'
};

const dictionaries = {
  'ru-RU': {
    appTitle: 'IB Workbench',
    dashboard: 'Главная',
    role: 'Роль',
    logout: 'Выйти',
    language: 'Язык',
    soon: 'скоро',
    refresh: 'Обновить',
    loading: 'Загрузка...',
    loginTitle: 'Вход через Keycloak (OIDC PKCE)',
    loginDescription:
      'По кнопке ниже запускается реальный OIDC PKCE flow через Keycloak. Для локальной отладки доступны demo-входы по ролям.',
    loginKeycloak: 'Войти через Keycloak',
    loginAdmin: 'Войти как admin',
    loginOperator: 'Войти как operator',
    loginAuditor: 'Войти как auditor',
    loginSupport: 'Войти как support',
    navMonitoring: 'Мониторинг',
    navRuntime: 'Runtime Config',
    navReplay: 'Повтор сообщений',
    navGroovy: 'Groovy Tooling',
    navIntegrations: 'Интеграции',
    navIncidents: 'Инциденты',
    monitoringTitle: 'Ingress / Idempotency / DLQ / Outbox',
    metricIngress: 'inbound DLQ (visible)',
    metricIdempotency: 'idempotency completed-rate',
    metricDlq: 'DLQ pending',
    metricOutbox: 'outbox pending',
    monitoringAutoRefresh: 'Автообновление (каждые 10с)',
    monitoringReplayMetricsTitle: 'Метрики последнего batch replay',
    monitoringReplayMetricsValues: 'Результат',
    monitoringReplayMetricsEmpty: 'Batch replay ещё не запускался',
    runtimeTitle: 'Runtime Config',
    runtimeHint: 'Используйте dry-run проверку JSON перед сохранением runtime-конфигурации.',
    dryRun: 'Dry-run',
    save: 'Сохранить',
    replayTitle: 'Replay DLQ / Outbox',
    replaySingle: 'Single replay',
    replayBatch: 'Batch replay',
    replayDlq: 'Inbound DLQ',
    replayMsgOutbox: 'Messaging outbox',
    replayRestOutbox: 'REST outbox',
    messageId: 'ID записи',
    replayMessage: 'Повторить',
    filter: 'Фильтр',
    replayByFilter: 'Повторить по фильтру',
    replayTypeFilter: 'type',
    replaySourceFilter: 'source',
    replayBranchFilter: 'branchId',
    replayApplyFilters: 'Применить фильтры',
    replayPrevPage: 'Назад',
    replayNextPage: 'Вперёд',
    replayPage: 'Страница',
    groovyTitle: 'Groovy Tooling',
    groovyHint: 'Валидация, эмуляция и просмотр trace для flow-скриптов.',
    validate: 'Проверить',
    emulate: 'Эмулировать',
    trace: 'Трассировка',
    integrationsTitle: 'Состояние интеграций',
    incidentsTitle: 'Экспорт инцидента',
    exportJson: 'Экспорт JSON',
    exportMarkdown: 'Экспорт Markdown',
    status: 'Статус',
    details: 'Детали',
    actions: 'Действия',
    script: 'Скрипт',
    inputJson: 'Input JSON',
    result: 'Результат',
    dryRunOk: 'Dry-run: OK',
    dryRunInvalid: 'Dry-run: невалидный JSON',
    dryRunWarning: 'Dry-run предупреждения',
    runtimeSavedServer: 'Конфигурация сохранена на сервере',
    runtimeSavedLocal: 'Сервер недоступен: сохранён локальный черновик',
    runtimeSaveBlocked: 'Сохранение отменено: dry-run вернул предупреждения',
    runtimeAuditTitle: 'Аудит runtime-конфигурации (последние 20)',
    integrationsSystem: 'Система',
    integrationsLatency: 'Задержка',
    replayApiUnavailable: 'API недоступен',
    replayQueued: 'Повтор поставлен в очередь',
    replayOutcomeLocked: 'Повтор не выполнен: запись заблокирована',
    replayOutcomeDead: 'Повтор не выполнен: запись в DEAD',
    replayOutcomeFailed: 'Повтор завершился ошибкой',
    replayOutcomeUnknown: 'Неизвестный outcome replay',
    replayBatchPlaceholder: 'тип/источник/филиал',
    replayBatchResult: 'Результат batch replay',
    replayBatchConfirm: 'Подтвердить batch replay?',
    replayBatchFilterInvalid: 'Некорректный фильтр: используйте type/source/branchId',
    replayCopyCorrelation: 'Копировать correlationId',
    replayCorrelationCopied: 'correlationId скопирован',
    replayCorrelationCopyFailed: 'Не удалось скопировать correlationId',
    replayCorrelationMissing: 'correlationId отсутствует',
    replayResultPayload: 'Replay result payload',
    runtimeAuditEmpty: 'Нет записей аудита',
    runtimeAuditExport: 'Экспорт аудита JSON',
    runtimeDiffPreviewTitle: 'Предпросмотр изменений (dry-run diff)',
    runtimeDiffPreviewInvalid: 'Предпросмотр недоступен: невалидный JSON',
    runtimeDiffPreviewNoChanges: 'Новых ключей относительно серверной конфигурации не найдено',
    integrationsHealthStatusPrefix: 'Статус health endpoint',
    integrationsHealthUnavailable: 'Health endpoint недоступен',
    integrationsStatusFilter: 'Фильтр статуса',
    integrationsStatusAll: 'Все',
    integrationsLastChecked: 'Последняя проверка',
    incidentsReportTitle: 'Инцидент',
    incidentsSourceLabel: 'Источник',
    incidentsMessageLabel: 'Сообщение',
    incidentsPayloadLabel: 'Payload',
    incidentsSampleMaskedMessage: 'Чувствительный payload замаскирован',
    outboundDryRunTitle: 'Outbound dry-run',
    outboundDryRunConfigured: 'Конфиг по умолчанию',
    outboundDryRunOverride: 'Override',
    outboundDryRunEffective: 'Эффективное значение',
    outboundDryRunOn: 'Включён',
    outboundDryRunOff: 'Выключен',
    outboundDryRunNoOverride: 'нет',
    outboundDryRunEnable: 'Включить override',
    outboundDryRunDisable: 'Выключить override',
    outboundDryRunReset: 'Сбросить override',
    outboundDryRunUpdated: 'Состояние outbound dry-run обновлено',
    outboundDryRunUnavailable: 'Outbound dry-run API недоступен',
    integrationsRestPoliciesTitle: 'REST-коннекторы: retry/circuit-breaker',
    integrationsRestPoliciesEmpty: 'REST-коннекторы не настроены',
    integrationsConnector: 'Коннектор',
    integrationsCircuitBreaker: 'Circuit breaker'
  },
  'en-EN': {
    appTitle: 'IB Workbench',
    dashboard: 'Dashboard',
    role: 'Role',
    logout: 'Logout',
    language: 'Language',
    soon: 'soon',
    refresh: 'Refresh',
    loading: 'Loading...',
    loginTitle: 'Login with Keycloak (OIDC PKCE)',
    loginDescription:
      'Use the button below to start a real OIDC PKCE flow with Keycloak. Demo role-based login is available for local development.',
    loginKeycloak: 'Sign in with Keycloak',
    loginAdmin: 'Sign in as admin',
    loginOperator: 'Sign in as operator',
    loginAuditor: 'Sign in as auditor',
    loginSupport: 'Sign in as support',
    navMonitoring: 'Monitoring',
    navRuntime: 'Runtime Config',
    navReplay: 'Replay',
    navGroovy: 'Groovy Tooling',
    navIntegrations: 'Integrations',
    navIncidents: 'Incidents',
    monitoringTitle: 'Ingress / Idempotency / DLQ / Outbox',
    metricIngress: 'inbound DLQ (visible)',
    metricIdempotency: 'idempotency completed-rate',
    metricDlq: 'DLQ pending',
    metricOutbox: 'outbox pending',
    monitoringAutoRefresh: 'Auto-refresh (every 10s)',
    monitoringReplayMetricsTitle: 'Last batch replay metrics',
    monitoringReplayMetricsValues: 'Result',
    monitoringReplayMetricsEmpty: 'Batch replay has not been run yet',
    runtimeTitle: 'Runtime Config',
    runtimeHint: 'Use JSON dry-run validation before saving runtime config.',
    dryRun: 'Dry-run',
    save: 'Save',
    replayTitle: 'Replay DLQ / Outbox',
    replaySingle: 'Single replay',
    replayBatch: 'Batch replay',
    replayDlq: 'Inbound DLQ',
    replayMsgOutbox: 'Messaging outbox',
    replayRestOutbox: 'REST outbox',
    messageId: 'Record ID',
    replayMessage: 'Replay',
    filter: 'Filter',
    replayByFilter: 'Replay by filter',
    replayTypeFilter: 'type',
    replaySourceFilter: 'source',
    replayBranchFilter: 'branchId',
    replayApplyFilters: 'Apply filters',
    replayPrevPage: 'Prev',
    replayNextPage: 'Next',
    replayPage: 'Page',
    groovyTitle: 'Groovy Tooling',
    groovyHint: 'Validate, emulate and inspect trace for flow scripts.',
    validate: 'Validate',
    emulate: 'Emulate',
    trace: 'Trace',
    integrationsTitle: 'Integrations health',
    incidentsTitle: 'Incident export',
    exportJson: 'Export JSON',
    exportMarkdown: 'Export Markdown',
    status: 'Status',
    details: 'Details',
    actions: 'Actions',
    script: 'Script',
    inputJson: 'Input JSON',
    result: 'Result',
    dryRunOk: 'Dry-run: OK',
    dryRunInvalid: 'Dry-run: invalid JSON',
    dryRunWarning: 'Dry-run warnings',
    runtimeSavedServer: 'Configuration has been saved on server',
    runtimeSavedLocal: 'Server unavailable: local draft has been saved',
    runtimeSaveBlocked: 'Save canceled: dry-run returned warnings',
    runtimeAuditTitle: 'Runtime Config Audit (last 20)',
    integrationsSystem: 'System',
    integrationsLatency: 'Latency',
    replayApiUnavailable: 'API unavailable',
    replayQueued: 'Replay queued',
    replayOutcomeLocked: 'Replay skipped: record is locked',
    replayOutcomeDead: 'Replay skipped: record is DEAD',
    replayOutcomeFailed: 'Replay failed',
    replayOutcomeUnknown: 'Unknown replay outcome',
    replayBatchPlaceholder: 'type/source/branch',
    replayBatchResult: 'Batch replay result',
    replayBatchConfirm: 'Confirm batch replay?',
    replayBatchFilterInvalid: 'Invalid filter: use type/source/branchId',
    replayCopyCorrelation: 'Copy correlationId',
    replayCorrelationCopied: 'correlationId copied',
    replayCorrelationCopyFailed: 'Failed to copy correlationId',
    replayCorrelationMissing: 'correlationId is missing',
    replayResultPayload: 'Replay result payload',
    runtimeAuditEmpty: 'No audit records',
    runtimeAuditExport: 'Export audit JSON',
    runtimeDiffPreviewTitle: 'Change preview (dry-run diff)',
    runtimeDiffPreviewInvalid: 'Preview unavailable: invalid JSON',
    runtimeDiffPreviewNoChanges: 'No new keys compared to server config',
    integrationsHealthStatusPrefix: 'Health endpoint status',
    integrationsHealthUnavailable: 'Health endpoint unavailable',
    integrationsStatusFilter: 'Status filter',
    integrationsStatusAll: 'All',
    integrationsLastChecked: 'Last checked',
    incidentsReportTitle: 'Incident',
    incidentsSourceLabel: 'Source',
    incidentsMessageLabel: 'Message',
    incidentsPayloadLabel: 'Payload',
    incidentsSampleMaskedMessage: 'Sensitive payload masked',
    outboundDryRunTitle: 'Outbound dry-run',
    outboundDryRunConfigured: 'Configured default',
    outboundDryRunOverride: 'Override',
    outboundDryRunEffective: 'Effective',
    outboundDryRunOn: 'Enabled',
    outboundDryRunOff: 'Disabled',
    outboundDryRunNoOverride: 'none',
    outboundDryRunEnable: 'Enable override',
    outboundDryRunDisable: 'Disable override',
    outboundDryRunReset: 'Reset override',
    outboundDryRunUpdated: 'Outbound dry-run state updated',
    outboundDryRunUnavailable: 'Outbound dry-run API unavailable',
    integrationsRestPoliciesTitle: 'REST connectors: retry/circuit-breaker',
    integrationsRestPoliciesEmpty: 'REST connectors are not configured',
    integrationsConnector: 'Connector',
    integrationsCircuitBreaker: 'Circuit breaker'
  }
} as const;

export type TranslationKey = keyof (typeof dictionaries)['ru-RU'];

export type SupportedLocale = keyof typeof dictionaries;

export function normalizeLocale(input: string | null | undefined): LocaleCode {
  if (!input) {
    return DEFAULT_LOCALE;
  }
  const exact = POPULAR_LOCALE_ORDER.find((loc) => loc.toLowerCase() === input.toLowerCase());
  if (exact) {
    return exact;
  }
  const byLanguage = POPULAR_LOCALE_ORDER.find((loc) => loc.slice(0, 2).toLowerCase() === input.slice(0, 2).toLowerCase());
  return byLanguage ?? DEFAULT_LOCALE;
}

export function isSupportedLocale(locale: LocaleCode): boolean {
  return SUPPORTED_LOCALES.includes(locale);
}

export function translate(locale: LocaleCode, key: TranslationKey): string {
  const active: SupportedLocale = isSupportedLocale(locale) ? (locale as SupportedLocale) : 'en-EN';
  return dictionaries[active][key];
}

const LOCALE_STORAGE_KEY = 'ib.workbench.locale';
const LOCALE_EXPLICIT_KEY = 'ib.workbench.locale.explicit';
const LOCALE_PREF_VERSION_KEY = 'ib.workbench.locale.pref-version';
const LOCALE_PREF_VERSION = '2';

export function detectInitialLocale(): LocaleCode {
  const isBrowser = typeof window !== 'undefined';
  const saved = isBrowser ? window.localStorage.getItem(LOCALE_STORAGE_KEY) : null;
  const hasExplicitSelection = isBrowser && window.localStorage.getItem(LOCALE_EXPLICIT_KEY) === 'true';
  const hasCurrentPreferenceVersion = isBrowser && window.localStorage.getItem(LOCALE_PREF_VERSION_KEY) === LOCALE_PREF_VERSION;
  if (saved && hasExplicitSelection && hasCurrentPreferenceVersion) {
    return normalizeLocale(saved);
  }
  if (isBrowser) {
    // Очищаем legacy-значения, чтобы старые автоматически проставленные языки не перебивали продуктовый дефолт.
    window.localStorage.removeItem(LOCALE_STORAGE_KEY);
    window.localStorage.removeItem(LOCALE_EXPLICIT_KEY);
  }
  // Продуктовый дефолт: русский интерфейс на первом входе.
  return DEFAULT_LOCALE;
}
