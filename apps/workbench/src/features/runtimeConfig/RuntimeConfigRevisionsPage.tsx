import { useCallback, useEffect, useState } from 'react';
import { workbenchApi, type RuntimeConfigRevisionItem } from '../../api/workbenchApi';
import { useI18n } from '../../app/I18nContext';

export function RuntimeConfigRevisionsPage() {
  const [items, setItems] = useState<RuntimeConfigRevisionItem[]>([]);
  const [error, setError] = useState('');
  const { t } = useI18n();

  const load = useCallback(async () => {
    try {
      setItems(await workbenchApi.fetchRuntimeConfigRevisions(200));
      setError('');
    } catch {
      setItems([]);
      setError(t('replayApiUnavailable'));
    }
  }, [t]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <section>
      <h2>{t('runtimeRevisionsTitle')}</h2>
      <div className="actions">
        <button onClick={() => load()}>{t('refresh')}</button>
      </div>
      {error && <p>{error}</p>}
      {items.length === 0 ? (
        <p>{t('runtimeAuditEmpty')}</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>{t('details')}</th>
              <th>{t('status')}</th>
            </tr>
          </thead>
          <tbody>
            {items.map((row) => (
              <tr key={`${row.changedAt}-${row.actor}-${row.toRevision ?? 'none'}`}>
                <td>{row.changedAt} | {row.actor} | {row.source}</td>
                <td>{row.fromRevision ?? '-'} → {row.toRevision ?? '-'} | {row.changedSections ?? '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
