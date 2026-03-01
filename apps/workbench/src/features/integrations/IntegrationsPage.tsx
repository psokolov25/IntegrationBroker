import { useCallback, useEffect, useMemo, useState } from 'react';
import { workbenchApi, type IntegrationHealth } from '../../api/workbenchApi';
import { useI18n } from '../../app/I18nContext';

type ConnectorRow = {
  name: string;
  baseUrl: string;
  retryMaxAttempts: string;
  retryBaseDelaySec: string;
  retryMaxDelaySec: string;
  circuitBreakerState: string;
};

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : null;
}

function asNumberText(value: unknown): string {
  return typeof value === 'number' ? String(value) : '-';
}

export function IntegrationsPage() {
  const [rows, setRows] = useState<IntegrationHealth[]>([]);
  const [connectors, setConnectors] = useState<ConnectorRow[]>([]);
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'UP' | 'DEGRADED' | 'DOWN'>('ALL');
  const [lastCheckedAt, setLastCheckedAt] = useState<string | null>(null);
  const { t } = useI18n();

  const load = useCallback(() => {
    workbenchApi
      .fetchIntegrationsHealth()
      .then((items) => {
        setRows(
          items.map((row) => ({
            ...row,
            details:
              row.details === 'UNAVAILABLE'
                ? t('integrationsHealthUnavailable')
                : `${t('integrationsHealthStatusPrefix')}: ${row.details}`
          }))
        );
        setLastCheckedAt(new Date().toLocaleString());
      });

    workbenchApi
      .fetchRuntimeConfig()
      .then((cfg) => {
        const restConnectors = asRecord(cfg.restConnectors);
        if (!restConnectors) {
          setConnectors([]);
          return;
        }
        const parsed = Object.entries(restConnectors).map(([name, raw]) => {
          const connector = asRecord(raw) ?? {};
          const retry = asRecord(connector.retryPolicy) ?? {};
          const breaker = asRecord(connector.circuitBreaker) ?? {};
          const breakerEnabled = breaker.enabled === true ? t('outboundDryRunOn') : t('outboundDryRunOff');
          return {
            name,
            baseUrl: typeof connector.baseUrl === 'string' ? connector.baseUrl : '-',
            retryMaxAttempts: asNumberText(retry.maxAttempts),
            retryBaseDelaySec: asNumberText(retry.baseDelaySec),
            retryMaxDelaySec: asNumberText(retry.maxDelaySec),
            circuitBreakerState: breaker.enabled == null ? '-' : breakerEnabled
          } satisfies ConnectorRow;
        });
        setConnectors(parsed);
      })
      .catch(() => setConnectors([]));
  }, [t]);

  useEffect(() => {
    load();
  }, [load]);

  const visibleRows = useMemo(
    () => rows.filter((row) => (statusFilter === 'ALL' ? true : row.status === statusFilter)),
    [rows, statusFilter]
  );

  return (
    <section>
      <h2>{t('integrationsTitle')}</h2>
      <div className="actions">
        <button onClick={load}>{t('refresh')}</button>
        <label>
          {t('integrationsStatusFilter')}
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as 'ALL' | 'UP' | 'DEGRADED' | 'DOWN')}>
            <option value="ALL">{t('integrationsStatusAll')}</option>
            <option value="UP">UP</option>
            <option value="DEGRADED">DEGRADED</option>
            <option value="DOWN">DOWN</option>
          </select>
        </label>
      </div>
      <p>{t('integrationsLastChecked')}: {lastCheckedAt ?? '-'}</p>
      <table>
        <thead>
          <tr><th>{t('integrationsSystem')}</th><th>{t('status')}</th><th>{t('integrationsLatency')}</th><th>{t('details')}</th></tr>
        </thead>
        <tbody>
          {visibleRows.map((row) => (
            <tr key={row.system}>
              <td>{row.system}</td>
              <td>{row.status}</td>
              <td>{row.latencyMs} ms</td>
              <td>{row.details}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <h3>{t('integrationsRestPoliciesTitle')}</h3>
      {connectors.length === 0 ? (
        <p>{t('integrationsRestPoliciesEmpty')}</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>{t('integrationsConnector')}</th>
              <th>baseUrl</th>
              <th>retry.maxAttempts</th>
              <th>retry.baseDelaySec</th>
              <th>retry.maxDelaySec</th>
              <th>{t('integrationsCircuitBreaker')}</th>
            </tr>
          </thead>
          <tbody>
            {connectors.map((row) => (
              <tr key={row.name}>
                <td>{row.name}</td>
                <td>{row.baseUrl}</td>
                <td>{row.retryMaxAttempts}</td>
                <td>{row.retryBaseDelaySec}</td>
                <td>{row.retryMaxDelaySec}</td>
                <td>{row.circuitBreakerState}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
