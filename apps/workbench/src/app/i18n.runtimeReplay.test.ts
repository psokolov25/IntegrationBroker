import { describe, expect, it } from 'vitest';
import { translate } from './i18n';

describe('i18n runtime/replay dictionaries', () => {
  it('contains runtime revisions strings in ru and en', () => {
    expect(translate('ru-RU', 'runtimeRevisionsTitle')).toContain('Ревизии');
    expect(translate('en-EN', 'runtimeRevisionsTitle')).toContain('revisions');
  });

  it('contains replay batch limit hint strings in ru and en', () => {
    expect(translate('ru-RU', 'replayBatchLimitLabel')).toContain('Лимит');
    expect(translate('en-EN', 'replayBatchLimitLabel')).toContain('limit');
    expect(translate('ru-RU', 'replayBatchLimitClamped')).toContain('ограничен');
    expect(translate('en-EN', 'replayBatchLimitClamped')).toContain('clamped');
  });

  it('contains monitoring latency and risky runtime labels in ru and en', () => {
    expect(translate('ru-RU', 'monitoringLatencyChartTitle')).toContain('Латентность');
    expect(translate('en-EN', 'monitoringLatencyChartTitle')).toContain('latency');
    expect(translate('ru-RU', 'runtimeRiskTitle')).toContain('Рискованные');
    expect(translate('en-EN', 'runtimeRiskTitle')).toContain('Risky changes');
  });
});
