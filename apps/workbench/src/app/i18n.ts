export const POPULAR_LOCALE_ORDER = [
  'en-EN',
  'zh-CN',
  'hi-IN',
  'es-ES',
  'ar-SA',
  'fr-FR',
  'bn-BD',
  'pt-BR',
  'ru-RU',
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
    runtimeSavedLocal: 'Сервер недоступен: сохранён локальный черновик'
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
    runtimeSavedLocal: 'Server unavailable: local draft has been saved'
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

export function detectInitialLocale(): LocaleCode {
  const saved = typeof window !== 'undefined' ? window.localStorage.getItem('ib.workbench.locale') : null;
  if (saved) {
    return normalizeLocale(saved);
  }
  const browserLang = typeof navigator !== 'undefined' ? navigator.language : null;
  return normalizeLocale(browserLang);
}
