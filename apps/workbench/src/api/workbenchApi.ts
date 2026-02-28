export interface MonitoringSnapshot {
  ingressPerMinute: number;
  idempotencyHitRate: number;
  dlqDepth: number;
  outboxPending: number;
}

export interface IntegrationHealth {
  system: string;
  status: 'UP' | 'DEGRADED' | 'DOWN';
  latencyMs: number;
  details: string;
}

export interface ListResponse<T> {
  items: T[];
}

export interface DlqItem {
  id: number;
  status: string;
  type?: string;
  messageId?: string;
  attempts: number;
  maxAttempts: number;
  updatedAt?: string;
}

export interface OutboxItem {
  id: number;
  status: string;
  destination?: string;
  attempts: number;
  maxAttempts: number;
  updatedAt?: string;
}

export interface GroovyValidationResult {
  valid: boolean;
  errors: string[];
}

export interface GroovyEmulationResult {
  success: boolean;
  output?: unknown;
  calls?: unknown[];
  debugMessages?: string[];
  errors?: string[];
}

async function jsonFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
    ...init
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} for ${path}`);
  }
  return (await response.json()) as T;
}

const fallbackMonitoring: MonitoringSnapshot = {
  ingressPerMinute: 0,
  idempotencyHitRate: 0,
  dlqDepth: 0,
  outboxPending: 0
};

export const workbenchApi = {
  async fetchMonitoring(): Promise<MonitoringSnapshot> {
    try {
      const [dlq, outbox, idem] = await Promise.all([
        jsonFetch<ListResponse<DlqItem>>('/admin/dlq?status=PENDING&limit=200'),
        jsonFetch<ListResponse<OutboxItem>>('/admin/outbox/messaging?status=PENDING&limit=200'),
        jsonFetch<ListResponse<{ status: string }>>('/admin/idempotency?limit=200')
      ]);
      const idemItems = idem.items ?? [];
      const completed = idemItems.filter((row) => row.status === 'COMPLETED').length;
      const rate = idemItems.length === 0 ? 0 : completed / idemItems.length;
      return {
        ingressPerMinute: dlq.items.length,
        idempotencyHitRate: rate,
        dlqDepth: dlq.items.length,
        outboxPending: outbox.items.length
      };
    } catch {
      return fallbackMonitoring;
    }
  },

  async dryRunRuntimeConfig(payload: Record<string, unknown>): Promise<{ ok: boolean; warnings: string[] }> {
    const warnings = Object.keys(payload).length === 0 ? ['Runtime config is empty'] : [];
    return { ok: warnings.length === 0, warnings };
  },

  async saveRuntimeConfig(payload: Record<string, unknown>): Promise<{ saved: boolean; source: 'server' | 'local' }> {
    try {
      await jsonFetch('/admin/runtime-config', {
        method: 'PUT',
        body: JSON.stringify(payload)
      });
      return { saved: true, source: 'server' };
    } catch {
      window.localStorage.setItem('ib.workbench.runtime-config.draft', JSON.stringify(payload));
      return { saved: true, source: 'local' };
    }
  },

  async listDlq(status = 'PENDING', limit = 50): Promise<DlqItem[]> {
    const response = await jsonFetch<ListResponse<DlqItem>>(`/admin/dlq?status=${encodeURIComponent(status)}&limit=${limit}`);
    return response.items ?? [];
  },

  async replayDlq(id: number): Promise<unknown> {
    return jsonFetch(`/admin/dlq/${id}/replay`, { method: 'POST' });
  },

  async listMessagingOutbox(status = 'PENDING', limit = 50): Promise<OutboxItem[]> {
    const response = await jsonFetch<ListResponse<OutboxItem>>(`/admin/outbox/messaging?status=${encodeURIComponent(status)}&limit=${limit}`);
    return response.items ?? [];
  },

  async replayMessagingOutbox(id: number): Promise<unknown> {
    return jsonFetch(`/admin/outbox/messaging/${id}/replay`, { method: 'POST' });
  },

  async listRestOutbox(status = 'PENDING', limit = 50): Promise<OutboxItem[]> {
    const response = await jsonFetch<ListResponse<OutboxItem>>(`/admin/outbox/rest?status=${encodeURIComponent(status)}&limit=${limit}`);
    return response.items ?? [];
  },

  async replayRestOutbox(id: number): Promise<unknown> {
    return jsonFetch(`/admin/outbox/rest/${id}/replay`, { method: 'POST' });
  },

  async validateGroovy(script: string): Promise<GroovyValidationResult> {
    return jsonFetch('/admin/groovy-tooling/validate', {
      method: 'POST',
      body: JSON.stringify({ script })
    });
  },

  async emulateGroovy(script: string, input: Record<string, unknown>): Promise<GroovyEmulationResult> {
    return jsonFetch('/admin/groovy-tooling/emulate', {
      method: 'POST',
      body: JSON.stringify({ script, input, meta: {}, mocks: {} })
    });
  },

  async fetchIntegrationsHealth(): Promise<IntegrationHealth[]> {
    const startedAt = performance.now();
    try {
      const payload = await jsonFetch<{ status?: string }>('/health');
      const elapsed = Math.round(performance.now() - startedAt);
      return [
        {
          system: 'Integration Broker',
          status: payload.status === 'UP' ? 'UP' : 'DEGRADED',
          latencyMs: elapsed,
          details: `Health endpoint status: ${payload.status ?? 'UNKNOWN'}`
        }
      ];
    } catch {
      return [{ system: 'Integration Broker', status: 'DOWN', latencyMs: 0, details: 'Health endpoint unavailable' }];
    }
  }
};
