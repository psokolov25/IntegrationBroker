import { useEffect, useState } from 'react';
import { workbenchApi, type IntegrationHealth } from '../../api/workbenchApi';
import { useI18n } from '../../app/I18nContext';

export function IntegrationsPage() {
  const [rows, setRows] = useState<IntegrationHealth[]>([]);
  const { t } = useI18n();

  const load = () => {
    workbenchApi.fetchIntegrationsHealth().then(setRows);
  };

  useEffect(() => {
    load();
  }, []);

  return (
    <section>
      <h2>{t('integrationsTitle')}</h2>
      <div className="actions">
        <button onClick={load}>{t('refresh')}</button>
      </div>
      <table>
        <thead>
          <tr><th>System</th><th>{t('status')}</th><th>Latency</th><th>{t('details')}</th></tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.system}>
              <td>{row.system}</td>
              <td>{row.status}</td>
              <td>{row.latencyMs} ms</td>
              <td>{row.details}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
