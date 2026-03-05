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
});
