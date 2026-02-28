import { useState } from 'react';
import { workbenchApi } from '../../api/workbenchApi';
import { useI18n } from '../../app/I18nContext';

export function RuntimeConfigPage() {
  const [raw, setRaw] = useState('{\n  "retry.maxAttempts": 4\n}');
  const [result, setResult] = useState<string>('');
  const { t } = useI18n();

  const dryRun = async () => {
    try {
      const parsed = JSON.parse(raw) as Record<string, unknown>;
      const response = await workbenchApi.dryRunRuntimeConfig(parsed);
      setResult(response.ok ? t('dryRunOk') : `${t('dryRunWarning')}: ${response.warnings.join(', ')}`);
    } catch {
      setResult(t('dryRunInvalid'));
    }
  };

  const save = async () => {
    try {
      const parsed = JSON.parse(raw) as Record<string, unknown>;
      const response = await workbenchApi.saveRuntimeConfig(parsed);
      setResult(response.source === 'server' ? t('runtimeSavedServer') : t('runtimeSavedLocal'));
    } catch {
      setResult(t('dryRunInvalid'));
    }
  };

  return (
    <section>
      <h2>{t('runtimeTitle')}</h2>
      <p>{t('runtimeHint')}</p>
      <textarea value={raw} onChange={(e) => setRaw(e.target.value)} rows={10} />
      <div className="actions">
        <button onClick={dryRun}>{t('dryRun')}</button>
        <button onClick={save}>{t('save')}</button>
      </div>
      {result && <p>{result}</p>}
    </section>
  );
}
