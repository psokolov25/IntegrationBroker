import { describe, expect, it, vi, afterEach } from 'vitest';
import { workbenchApi } from './workbenchApi';

describe('workbenchApi.replayDlqBatch', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('maps backend batch replay fields to normalized client response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new globalThis.Response(
          JSON.stringify({
            selected: 3,
            success: 2,
            locked: 1,
            failed: 0,
            dead: 0,
            requestedLimit: 500,
            appliedLimit: 100,
            limitClamped: true,
            items: [{ id: 10, outcome: 'PROCESSED' }]
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        )
      )
    );

    const result = await workbenchApi.replayDlqBatch({ type: 'visit.created', limit: 500 });

    expect(result).toEqual({
      total: 3,
      ok: 2,
      locked: 1,
      failed: 0,
      dead: 0,
      requestedLimit: 500,
      appliedLimit: 100,
      limitClamped: true,
      items: [{ id: 10, outcome: 'PROCESSED' }]
    });
  });

  it('fills optional clamp fields with defaults when backend omits them', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new globalThis.Response(
          JSON.stringify({ selected: 1, success: 1, locked: 0, failed: 0, dead: 0, items: [] }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        )
      )
    );

    const result = await workbenchApi.replayDlqBatch({ source: 'crm', limit: 77 });

    expect(result.requestedLimit).toBe(77);
    expect(result.appliedLimit).toBe(77);
    expect(result.limitClamped).toBe(false);
  });


  it('returns latency histogram fallback when integration metrics API is unavailable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new globalThis.Response('boom', { status: 500 }))
    );

    const result = await workbenchApi.fetchIntegrationMetrics();

    expect(result).toEqual({ restConnectorLatencyHistogram: {}, adminOperations: undefined });
  });


  it('returns admin operations metrics from integration metrics API', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new globalThis.Response(
          JSON.stringify({
            restConnectorLatencyHistogram: { vm: { lt100ms: 1, lt300ms: 0, lt1000ms: 0, gte1000ms: 0 } },
            adminOperations: { dlqReplayBatchRuns: 2 }
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        )
      )
    );

    const result = await workbenchApi.fetchIntegrationMetrics();

    expect(result.adminOperations?.dlqReplayBatchRuns).toBe(2);
  });

  it('requests sanitized DLQ payload preview by id', async () => {
    const fetchMock = vi.fn(async () =>
      new globalThis.Response(
        JSON.stringify({ id: 12, maxLen: 1200, preview: '{"masked":true}' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    );
    vi.stubGlobal('fetch', fetchMock);

    const result = await workbenchApi.previewDlqPayload(12);

    expect(result.preview).toContain('masked');
    expect(fetchMock).toHaveBeenCalledWith('/admin/dlq/12/preview?maxLen=1200', expect.anything());
  });

});
