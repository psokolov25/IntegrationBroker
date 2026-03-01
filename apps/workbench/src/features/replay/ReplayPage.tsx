import { useEffect, useMemo, useState } from 'react';
import { workbenchApi, type DlqItem, type OutboxItem } from '../../api/workbenchApi';
import { useI18n } from '../../app/I18nContext';

const PAGE_SIZE = 10;

function outcomeToNotice(
  t: (key: 'replayQueued' | 'replayOutcomeLocked' | 'replayOutcomeDead' | 'replayOutcomeFailed' | 'replayOutcomeUnknown') => string,
  payload: unknown
): string {
  if (typeof payload !== 'object' || payload === null) {
    return t('replayQueued');
  }
  const outcome = (payload as { outcome?: unknown }).outcome;
  if (typeof outcome !== 'string') {
    return t('replayQueued');
  }
  if (outcome === 'PROCESSED' || outcome === 'REPLAYED' || outcome === 'SKIP_COMPLETED') {
    return t('replayQueued');
  }
  if (outcome === 'LOCKED') {
    return t('replayOutcomeLocked');
  }
  if (outcome === 'DEAD') {
    return t('replayOutcomeDead');
  }
  if (outcome === 'FAILED') {
    return t('replayOutcomeFailed');
  }
  return `${t('replayOutcomeUnknown')}: ${outcome}`;
}

function paginate<T>(items: T[], page: number, pageSize: number): T[] {
  const start = (page - 1) * pageSize;
  return items.slice(start, start + pageSize);
}

function totalPages(total: number, pageSize: number): number {
  return Math.max(1, Math.ceil(total / pageSize));
}

function prettyJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

export function ReplayPage() {
  const [mode, setMode] = useState<'single' | 'batch'>('single');
  const [dlqItems, setDlqItems] = useState<DlqItem[]>([]);
  const [msgOutboxItems, setMsgOutboxItems] = useState<OutboxItem[]>([]);
  const [restOutboxItems, setRestOutboxItems] = useState<OutboxItem[]>([]);
  const [notice, setNotice] = useState<string>('');
  const [lastReplayPayload, setLastReplayPayload] = useState<string>('');
  const [batchFilter, setBatchFilter] = useState('');
  const [dlqTypeFilter, setDlqTypeFilter] = useState('');
  const [dlqSourceFilter, setDlqSourceFilter] = useState('');
  const [dlqBranchFilter, setDlqBranchFilter] = useState('');
  const [dlqPage, setDlqPage] = useState(1);
  const [msgPage, setMsgPage] = useState(1);
  const [restPage, setRestPage] = useState(1);
  const { t } = useI18n();

  const load = async () => {
    const [dlq, msg, rest] = await Promise.all([
      workbenchApi.listDlq('PENDING', 200, {
        type: dlqTypeFilter.trim() || undefined,
        source: dlqSourceFilter.trim() || undefined,
        branchId: dlqBranchFilter.trim() || undefined
      }),
      workbenchApi.listMessagingOutbox('PENDING', 200),
      workbenchApi.listRestOutbox('PENDING', 200)
    ]);
    setDlqItems(dlq);
    setMsgOutboxItems(msg);
    setRestOutboxItems(rest);
    setDlqPage(1);
    setMsgPage(1);
    setRestPage(1);
  };

  useEffect(() => {
    load().catch(() => setNotice(t('replayApiUnavailable')));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [t]);

  const copyCorrelationId = async (correlationId?: string) => {
    if (!correlationId) {
      setNotice(t('replayCorrelationMissing'));
      return;
    }
    try {
      await navigator.clipboard.writeText(correlationId);
      setNotice(t('replayCorrelationCopied'));
    } catch {
      setNotice(t('replayCorrelationCopyFailed'));
    }
  };

  const replayAndRefresh = async (task: () => Promise<unknown>) => {
    const payload = await task();
    setNotice(outcomeToNotice(t, payload));
    setLastReplayPayload(prettyJson(payload));
    await load();
  };

  const replayBatch = async () => {
    const raw = batchFilter.trim();
    const parts = raw ? raw.split('/').map((part) => part.trim()) : [];
    if (parts.length > 3) {
      setNotice(t('replayBatchFilterInvalid'));
      return;
    }
    const [type, source, branchId] = parts;
    const confirmed = window.confirm(t('replayBatchConfirm'));
    if (!confirmed) {
      return;
    }
    const response = await workbenchApi.replayDlqBatch({
      type: type || undefined,
      source: source || undefined,
      branchId: branchId || undefined,
      limit: 50
    });
    window.localStorage.setItem(
      'ib.workbench.replay.last-batch',
      JSON.stringify({ ok: response.ok, locked: response.locked, failed: response.failed, dead: response.dead, at: new Date().toISOString() })
    );
    setNotice(`${t('replayBatchResult')}: OK=${response.ok}, LOCKED=${response.locked}, FAILED=${response.failed}, DEAD=${response.dead}`);
    setLastReplayPayload(prettyJson(response));
    await load();
  };

  const dlqPaged = useMemo(() => paginate(dlqItems, dlqPage, PAGE_SIZE), [dlqItems, dlqPage]);
  const msgPaged = useMemo(() => paginate(msgOutboxItems, msgPage, PAGE_SIZE), [msgOutboxItems, msgPage]);
  const restPaged = useMemo(() => paginate(restOutboxItems, restPage, PAGE_SIZE), [restOutboxItems, restPage]);

  const dlqPages = totalPages(dlqItems.length, PAGE_SIZE);
  const msgPages = totalPages(msgOutboxItems.length, PAGE_SIZE);
  const restPages = totalPages(restOutboxItems.length, PAGE_SIZE);

  return (
    <section>
      <h2>{t('replayTitle')}</h2>
      <div className="actions">
        <button onClick={() => setMode('single')}>{t('replaySingle')}</button>
        <button onClick={() => setMode('batch')}>{t('replayBatch')}</button>
        <button onClick={() => load()}>{t('refresh')}</button>
      </div>
      {notice && <p>{notice}</p>}
      {lastReplayPayload && (
        <details>
          <summary>{t('replayResultPayload')}</summary>
          <pre>{lastReplayPayload}</pre>
        </details>
      )}
      {mode === 'single' ? (
        <>
          <h3>{t('replayDlq')}</h3>
          <div className="actions">
            <input value={dlqTypeFilter} onChange={(e) => setDlqTypeFilter(e.target.value)} placeholder={t('replayTypeFilter')} />
            <input value={dlqSourceFilter} onChange={(e) => setDlqSourceFilter(e.target.value)} placeholder={t('replaySourceFilter')} />
            <input value={dlqBranchFilter} onChange={(e) => setDlqBranchFilter(e.target.value)} placeholder={t('replayBranchFilter')} />
            <button onClick={() => load()}>{t('replayApplyFilters')}</button>
          </div>
          <table>
            <thead>
              <tr><th>{t('messageId')}</th><th>{t('status')}</th><th>{t('details')}</th><th>{t('actions')}</th></tr>
            </thead>
            <tbody>
              {dlqPaged.map((row) => (
                <tr key={`dlq-${row.id}`}>
                  <td>{row.id}</td><td>{row.status}</td><td>{row.type ?? row.messageId ?? '-'}</td>
                  <td>
                    <button onClick={() => replayAndRefresh(() => workbenchApi.replayDlq(row.id))}>{t('replayMessage')}</button>
                    <button onClick={() => copyCorrelationId(row.correlationId)}>{t('replayCopyCorrelation')}</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="actions">
            <button disabled={dlqPage <= 1} onClick={() => setDlqPage((p) => Math.max(1, p - 1))}>{t('replayPrevPage')}</button>
            <span>{t('replayPage')} {dlqPage}/{dlqPages}</span>
            <button disabled={dlqPage >= dlqPages} onClick={() => setDlqPage((p) => Math.min(dlqPages, p + 1))}>{t('replayNextPage')}</button>
          </div>

          <h3>{t('replayMsgOutbox')}</h3>
          <table>
            <thead>
              <tr><th>{t('messageId')}</th><th>{t('status')}</th><th>{t('details')}</th><th>{t('actions')}</th></tr>
            </thead>
            <tbody>
              {msgPaged.map((row) => (
                <tr key={`msg-${row.id}`}>
                  <td>{row.id}</td><td>{row.status}</td><td>{row.destination ?? '-'}</td>
                  <td><button onClick={() => replayAndRefresh(() => workbenchApi.replayMessagingOutbox(row.id))}>{t('replayMessage')}</button></td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="actions">
            <button disabled={msgPage <= 1} onClick={() => setMsgPage((p) => Math.max(1, p - 1))}>{t('replayPrevPage')}</button>
            <span>{t('replayPage')} {msgPage}/{msgPages}</span>
            <button disabled={msgPage >= msgPages} onClick={() => setMsgPage((p) => Math.min(msgPages, p + 1))}>{t('replayNextPage')}</button>
          </div>

          <h3>{t('replayRestOutbox')}</h3>
          <table>
            <thead>
              <tr><th>{t('messageId')}</th><th>{t('status')}</th><th>{t('details')}</th><th>{t('actions')}</th></tr>
            </thead>
            <tbody>
              {restPaged.map((row) => (
                <tr key={`rest-${row.id}`}>
                  <td>{row.id}</td><td>{row.status}</td><td>{row.destination ?? '-'}</td>
                  <td><button onClick={() => replayAndRefresh(() => workbenchApi.replayRestOutbox(row.id))}>{t('replayMessage')}</button></td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="actions">
            <button disabled={restPage <= 1} onClick={() => setRestPage((p) => Math.max(1, p - 1))}>{t('replayPrevPage')}</button>
            <span>{t('replayPage')} {restPage}/{restPages}</span>
            <button disabled={restPage >= restPages} onClick={() => setRestPage((p) => Math.min(restPages, p + 1))}>{t('replayNextPage')}</button>
          </div>
        </>
      ) : (
        <div className="card">
          <label>{t('filter')}</label>
          <input value={batchFilter} onChange={(e) => setBatchFilter(e.target.value)} placeholder={t('replayBatchPlaceholder')} />
          <button onClick={() => replayBatch().catch(() => setNotice(t('replayApiUnavailable')))}>{t('replayByFilter')}</button>
        </div>
      )}
    </section>
  );
}
