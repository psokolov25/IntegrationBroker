import { useEffect, useState } from 'react';
import { workbenchApi, type DlqItem, type OutboxItem } from '../../api/workbenchApi';
import { useI18n } from '../../app/I18nContext';

export function ReplayPage() {
  const [mode, setMode] = useState<'single' | 'batch'>('single');
  const [dlqItems, setDlqItems] = useState<DlqItem[]>([]);
  const [msgOutboxItems, setMsgOutboxItems] = useState<OutboxItem[]>([]);
  const [restOutboxItems, setRestOutboxItems] = useState<OutboxItem[]>([]);
  const [notice, setNotice] = useState<string>('');
  const { t } = useI18n();

  const load = async () => {
    const [dlq, msg, rest] = await Promise.all([
      workbenchApi.listDlq('PENDING', 10),
      workbenchApi.listMessagingOutbox('PENDING', 10),
      workbenchApi.listRestOutbox('PENDING', 10)
    ]);
    setDlqItems(dlq);
    setMsgOutboxItems(msg);
    setRestOutboxItems(rest);
  };

  useEffect(() => {
    load().catch(() => setNotice('API unavailable'));
  }, []);

  const replayAndRefresh = async (task: () => Promise<unknown>) => {
    await task();
    setNotice('Replay queued');
    await load();
  };

  return (
    <section>
      <h2>{t('replayTitle')}</h2>
      <div className="actions">
        <button onClick={() => setMode('single')}>{t('replaySingle')}</button>
        <button onClick={() => setMode('batch')}>{t('replayBatch')}</button>
        <button onClick={() => load()}>{t('refresh')}</button>
      </div>
      {notice && <p>{notice}</p>}
      {mode === 'single' ? (
        <>
          <h3>{t('replayDlq')}</h3>
          <table>
            <thead>
              <tr><th>{t('messageId')}</th><th>{t('status')}</th><th>{t('details')}</th><th>{t('actions')}</th></tr>
            </thead>
            <tbody>
              {dlqItems.map((row) => (
                <tr key={`dlq-${row.id}`}>
                  <td>{row.id}</td><td>{row.status}</td><td>{row.type ?? row.messageId ?? '-'}</td>
                  <td><button onClick={() => replayAndRefresh(() => workbenchApi.replayDlq(row.id))}>{t('replayMessage')}</button></td>
                </tr>
              ))}
            </tbody>
          </table>

          <h3>{t('replayMsgOutbox')}</h3>
          <table>
            <thead>
              <tr><th>{t('messageId')}</th><th>{t('status')}</th><th>{t('details')}</th><th>{t('actions')}</th></tr>
            </thead>
            <tbody>
              {msgOutboxItems.map((row) => (
                <tr key={`msg-${row.id}`}>
                  <td>{row.id}</td><td>{row.status}</td><td>{row.destination ?? '-'}</td>
                  <td><button onClick={() => replayAndRefresh(() => workbenchApi.replayMessagingOutbox(row.id))}>{t('replayMessage')}</button></td>
                </tr>
              ))}
            </tbody>
          </table>

          <h3>{t('replayRestOutbox')}</h3>
          <table>
            <thead>
              <tr><th>{t('messageId')}</th><th>{t('status')}</th><th>{t('details')}</th><th>{t('actions')}</th></tr>
            </thead>
            <tbody>
              {restOutboxItems.map((row) => (
                <tr key={`rest-${row.id}`}>
                  <td>{row.id}</td><td>{row.status}</td><td>{row.destination ?? '-'}</td>
                  <td><button onClick={() => replayAndRefresh(() => workbenchApi.replayRestOutbox(row.id))}>{t('replayMessage')}</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      ) : (
        <div className="card">
          <label>{t('filter')}</label>
          <input placeholder="type/source/branch" />
          <button>{t('replayByFilter')}</button>
        </div>
      )}
    </section>
  );
}
