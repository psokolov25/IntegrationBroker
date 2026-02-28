import { useEffect, useState } from 'react';
import { workbenchApi, type MonitoringSnapshot } from '../../api/workbenchApi';
import { useI18n } from '../../app/I18nContext';

export function MonitoringPage() {
  const [data, setData] = useState<MonitoringSnapshot | null>(null);
  const { t } = useI18n();

  const load = () => {
    workbenchApi.fetchMonitoring().then(setData);
  };

  useEffect(() => {
    load();
  }, []);

  if (!data) {
    return <p>{t('loading')}</p>;
  }

  return (
    <section>
      <h2>{t('monitoringTitle')}</h2>
      <div className="actions">
        <button onClick={load}>{t('refresh')}</button>
      </div>
      <div className="grid4">
        <article className="metric"><strong>{data.ingressPerMinute}</strong><span>{t('metricIngress')}</span></article>
        <article className="metric"><strong>{(data.idempotencyHitRate * 100).toFixed(0)}%</strong><span>{t('metricIdempotency')}</span></article>
        <article className="metric"><strong>{data.dlqDepth}</strong><span>{t('metricDlq')}</span></article>
        <article className="metric"><strong>{data.outboxPending}</strong><span>{t('metricOutbox')}</span></article>
      </div>
    </section>
  );
}
