import { useCallback, useEffect, useState } from 'react';
import { workbenchApi, type MonitoringSnapshot, type OutboundDryRunState } from '../../api/workbenchApi';
import { useI18n } from '../../app/I18nContext';

export function MonitoringPage() {
  const [data, setData] = useState<MonitoringSnapshot | null>(null);
  const [dryRunState, setDryRunState] = useState<OutboundDryRunState | null>(null);
  const [dryRunNotice, setDryRunNotice] = useState('');
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [replayBatchMetrics, setReplayBatchMetrics] = useState<{ ok: number; locked: number; failed: number; dead: number; at?: string } | null>(null);
  const { t } = useI18n();

  const load = useCallback(() => {
    workbenchApi.fetchMonitoring().then(setData);
    workbenchApi
      .getOutboundDryRunState()
      .then((state) => {
        setDryRunState(state);
        setDryRunNotice('');
      })
      .catch(() => {
        setDryRunState(null);
        setDryRunNotice(t('outboundDryRunUnavailable'));
      });

    const replayRaw = window.localStorage.getItem('ib.workbench.replay.last-batch');
    if (replayRaw) {
      try {
        const parsed = JSON.parse(replayRaw) as { ok: number; locked: number; failed: number; dead: number; at?: string };
        setReplayBatchMetrics(parsed);
      } catch {
        setReplayBatchMetrics(null);
      }
    } else {
      setReplayBatchMetrics(null);
    }
  }, [t]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!autoRefresh) {
      return;
    }
    const timer = window.setInterval(() => {
      load();
    }, 10000);
    return () => window.clearInterval(timer);
  }, [autoRefresh, load]);

  const stateLabel = (enabled: boolean) => (enabled ? t('outboundDryRunOn') : t('outboundDryRunOff'));

  const updateState = async (task: () => Promise<OutboundDryRunState>) => {
    try {
      const state = await task();
      setDryRunState(state);
      setDryRunNotice(t('outboundDryRunUpdated'));
    } catch {
      setDryRunNotice(t('outboundDryRunUnavailable'));
    }
  };

  if (!data) {
    return <p>{t('loading')}</p>;
  }

  return (
    <section>
      <h2>{t('monitoringTitle')}</h2>
      <div className="actions">
        <button onClick={load}>{t('refresh')}</button>
        <label>
          <input type="checkbox" checked={autoRefresh} onChange={(e) => setAutoRefresh(e.target.checked)} />
          {t('monitoringAutoRefresh')}
        </label>
      </div>
      <div className="grid4">
        <article className="metric"><strong>{data.ingressPerMinute}</strong><span>{t('metricIngress')}</span></article>
        <article className="metric"><strong>{(data.idempotencyHitRate * 100).toFixed(0)}%</strong><span>{t('metricIdempotency')}</span></article>
        <article className="metric"><strong>{data.dlqDepth}</strong><span>{t('metricDlq')}</span></article>
        <article className="metric"><strong>{data.outboxPending}</strong><span>{t('metricOutbox')}</span></article>
      </div>


      <h3>{t('monitoringReplayMetricsTitle')}</h3>
      {replayBatchMetrics ? (
        <p>
          {t('monitoringReplayMetricsValues')}: OK={replayBatchMetrics.ok}, LOCKED={replayBatchMetrics.locked}, FAILED={replayBatchMetrics.failed}, DEAD={replayBatchMetrics.dead}
          {replayBatchMetrics.at ? ` | ${replayBatchMetrics.at}` : ''}
        </p>
      ) : (
        <p>{t('monitoringReplayMetricsEmpty')}</p>
      )}

      <h3>{t('outboundDryRunTitle')}</h3>
      {dryRunState ? (
        <>
          <p>
            {t('outboundDryRunConfigured')}: <strong>{stateLabel(dryRunState.configuredDefault)}</strong> | {t('outboundDryRunOverride')}:{' '}
            <strong>{dryRunState.override === null ? t('outboundDryRunNoOverride') : stateLabel(dryRunState.override)}</strong> | {t('outboundDryRunEffective')}:{' '}
            <strong>{stateLabel(dryRunState.effective)}</strong>
          </p>
          <div className="actions">
            <button onClick={() => updateState(() => workbenchApi.setOutboundDryRunOverride(true))}>{t('outboundDryRunEnable')}</button>
            <button onClick={() => updateState(() => workbenchApi.setOutboundDryRunOverride(false))}>{t('outboundDryRunDisable')}</button>
            <button onClick={() => updateState(() => workbenchApi.resetOutboundDryRunOverride())}>{t('outboundDryRunReset')}</button>
          </div>
        </>
      ) : (
        <p>{t('outboundDryRunUnavailable')}</p>
      )}
      {dryRunNotice && <p>{dryRunNotice}</p>}
    </section>
  );
}
