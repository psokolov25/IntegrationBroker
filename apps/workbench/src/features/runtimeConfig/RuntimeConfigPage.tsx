import { useEffect, useMemo, useState } from 'react';
import { workbenchApi, type RuntimeConfigAuditItem } from '../../api/workbenchApi';
import { useI18n } from '../../app/I18nContext';

function flattenKeys(value: unknown, prefix = ''): string[] {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return prefix ? [prefix] : [];
  }
  const obj = value as Record<string, unknown>;
  const keys: string[] = [];
  for (const [k, v] of Object.entries(obj)) {
    const path = prefix ? `${prefix}.${k}` : k;
    if (typeof v === 'object' && v !== null && !Array.isArray(v)) {
      keys.push(...flattenKeys(v, path));
    } else {
      keys.push(path);
    }
  }
  return keys;
}

export function RuntimeConfigPage() {
  const [raw, setRaw] = useState(`{
  "retry.maxAttempts": 4
}`);
  const [result, setResult] = useState<string>('');
  const [audit, setAudit] = useState<RuntimeConfigAuditItem[]>([]);
  const [serverConfig, setServerConfig] = useState<Record<string, unknown>>({});
  const { t } = useI18n();

  const parsedDraft = useMemo(() => {
    try {
      return JSON.parse(raw) as Record<string, unknown>;
    } catch {
      return null;
    }
  }, [raw]);

  const diffPreview = useMemo(() => {
    if (!parsedDraft) {
      return [] as string[];
    }
    const baseKeys = new Set(flattenKeys(serverConfig));
    const draftKeys = new Set(flattenKeys(parsedDraft));
    return Array.from(draftKeys).filter((k) => !baseKeys.has(k)).slice(0, 20);
  }, [parsedDraft, serverConfig]);

  const load = async () => {
    try {
      const cfg = await workbenchApi.fetchRuntimeConfig();
      setServerConfig(cfg);
      setRaw(JSON.stringify(cfg, null, 2));
    } catch {
      const localDraft = window.localStorage.getItem('ib.workbench.runtime-config.draft');
      if (localDraft) {
        setRaw(localDraft);
      }
    }

    try {
      setAudit(await workbenchApi.fetchRuntimeConfigAudit(20));
    } catch {
      setAudit([]);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const dryRun = async () => {
    try {
      const parsed = JSON.parse(raw) as Record<string, unknown>;
      const response = await workbenchApi.dryRunRuntimeConfig(parsed);
      setResult(response.ok ? t('dryRunOk') : `${t('dryRunWarning')}: ${response.warnings.join(', ')}`);
    } catch {
      setResult(t('dryRunInvalid'));
    }
  };

  const exportAuditJson = () => {
    const blob = new Blob([JSON.stringify(audit, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'runtime-config-audit.json';
    link.click();
    URL.revokeObjectURL(url);
  };

  const save = async () => {
    try {
      const parsed = JSON.parse(raw) as Record<string, unknown>;
      const dryRunResponse = await workbenchApi.dryRunRuntimeConfig(parsed);
      if (!dryRunResponse.ok) {
        setResult(`${t('runtimeSaveBlocked')}: ${dryRunResponse.warnings.join(', ')}`);
        return;
      }
      const response = await workbenchApi.saveRuntimeConfig(parsed);
      setResult(response.source === 'server' ? t('runtimeSavedServer') : t('runtimeSavedLocal'));
      await load();
    } catch {
      setResult(t('dryRunInvalid'));
    }
  };

  return (
    <section>
      <h2>{t('runtimeTitle')}</h2>
      <p>{t('runtimeHint')}</p>
      <textarea value={raw} onChange={(e) => setRaw(e.target.value)} rows={14} />
      <div className="actions">
        <button onClick={dryRun}>{t('dryRun')}</button>
        <button onClick={save}>{t('save')}</button>
        <button onClick={load}>{t('refresh')}</button>
        <button onClick={exportAuditJson}>{t('runtimeAuditExport')}</button>
      </div>
      {result && <p>{result}</p>}

      <h3>{t('runtimeDiffPreviewTitle')}</h3>
      {!parsedDraft ? (
        <p>{t('runtimeDiffPreviewInvalid')}</p>
      ) : diffPreview.length === 0 ? (
        <p>{t('runtimeDiffPreviewNoChanges')}</p>
      ) : (
        <ul>
          {diffPreview.map((row) => (
            <li key={row}>{row}</li>
          ))}
        </ul>
      )}

      <h3>{t('runtimeAuditTitle')}</h3>
      {audit.length === 0 ? (
        <p>{t('runtimeAuditEmpty')}</p>
      ) : (
        <ul>
          {audit.map((row) => (
            <li key={`${row.changedAt}-${row.actor}-${row.source}`}>
              {row.changedAt} | {row.actor} | {row.source} | {row.fromRevision ?? '-'} → {row.toRevision ?? '-'} | {row.changedSections ?? '-'}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
