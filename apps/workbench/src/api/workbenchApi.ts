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

export interface OutboundDryRunState {
  configuredDefault: boolean;
  override: boolean | null;
  effective: boolean;
}

export interface ListResponse<T> {
  items: T[];
}

export interface DlqItem {
  id: number;
  status: string;
  type?: string;
  messageId?: string;
  correlationId?: string;
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


export interface RuntimeConfigPayload {
  revision?: string;
  [key: string]: unknown;
}

export interface RuntimeConfigAuditItem {
  changedAt: string;
  actor: string;
  source: string;
  fromRevision?: string;
  toRevision?: string;
  note?: string;
  changedSections?: string;
}

export interface GroovyEmulationResult {
  success: boolean;
  output?: unknown;
  calls?: unknown[];
  debugMessages?: string[];
  errors?: string[];
}

export interface DlqReplayBatchItem {
  outcome: string;
  id: number;
}

export interface DlqReplayBatchResponse {
  total: number;
  ok: number;
  locked: number;
  failed: number;
  dead: number;
  items: DlqReplayBatchItem[];
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

  async fetchRuntimeConfig(): Promise<RuntimeConfigPayload> {
    const response = await jsonFetch<{ config?: RuntimeConfigPayload }>('/admin/runtime-config');
    return response.config ?? {};
  },

  async fetchRuntimeConfigAudit(limit = 20): Promise<RuntimeConfigAuditItem[]> {
    const response = await jsonFetch<ListResponse<RuntimeConfigAuditItem>>(`/admin/runtime-config/audit?limit=${limit}`);
    return response.items ?? [];
  },

  async dryRunRuntimeConfig(payload: Record<string, unknown>): Promise<{ ok: boolean; warnings: string[] }> {
    return jsonFetch('/admin/runtime-config/dry-run', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
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

  async listDlq(
    status = 'PENDING',
    limit = 50,
    filter?: { type?: string; source?: string; branchId?: string }
  ): Promise<DlqItem[]> {
    const params = new URLSearchParams({ status, limit: String(limit) });
    if (filter?.type) params.set('type', filter.type);
    if (filter?.source) params.set('source', filter.source);
    if (filter?.branchId) params.set('branchId', filter.branchId);
    const response = await jsonFetch<ListResponse<DlqItem>>(`/admin/dlq?${params.toString()}`);
    return response.items ?? [];
  },

  async replayDlq(id: number): Promise<unknown> {
    return jsonFetch(`/admin/dlq/${id}/replay`, { method: 'POST' });
  },

  async replayDlqBatch(filter: { type?: string; source?: string; branchId?: string; limit?: number }): Promise<DlqReplayBatchResponse> {
    return jsonFetch('/admin/dlq/replay-batch', {
      method: 'POST',
      body: JSON.stringify({
        status: 'PENDING',
        type: filter.type,
        source: filter.source,
        branchId: filter.branchId,
        limit: filter.limit ?? 50
      })
    });
  },

  async listMessagingOutbox(status = 'PENDING', limit = 50): Promise<OutboxItem[]> {
    const response = await jsonFetch<ListResponse<OutboxItem>>(`/admin/outbox/messaging?status=${encodeURIComponent(status)}&limit=${limit}`);
    return response.items ?? [];
  },

  async replayMessagingOutbox(id: number): Promise<unknown> {
    return jsonFetch(`/admin/outbox/messaging/${id}/replay`, { method: 'POST' });
  },

  async getOutboundDryRunState(): Promise<OutboundDryRunState> {
    return jsonFetch('/admin/outbound/dry-run');
  },

  async setOutboundDryRunOverride(enabled: boolean): Promise<OutboundDryRunState> {
    return jsonFetch('/admin/outbound/dry-run', {
      method: 'POST',
      body: JSON.stringify({ enabled, reset: false })
    });
  },

  async resetOutboundDryRunOverride(): Promise<OutboundDryRunState> {
    return jsonFetch('/admin/outbound/dry-run', {
      method: 'POST',
      body: JSON.stringify({ reset: true })
    });
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
    try {
      const response = await jsonFetch<{ items?: IntegrationHealth[] }>('/admin/integrations/health');
      const items = response.items ?? [];
      return items.length > 0 ? items : [{ system: 'Integration Broker', status: 'DEGRADED', latencyMs: 0, details: 'NO_ITEMS' }];
    } catch {
      return [{ system: 'Integration Broker', status: 'DOWN', latencyMs: 0, details: 'UNAVAILABLE' }];
    }
  }
};
