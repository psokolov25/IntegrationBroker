import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../app/App';

function mockJson(body: unknown, status = 200) {
  return new globalThis.Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('Workbench routes smoke', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: string | URL) => {
        const path = typeof input === 'string' ? input : input.toString();
        if (path.includes('/admin/runtime-config/audit')) return mockJson({ items: [] });
        if (path.includes('/admin/runtime-config')) return mockJson({ config: { revision: 'rev-test' } });
        if (path.includes('/admin/dlq/replay-batch')) return mockJson({ selected: 0, success: 0, locked: 0, failed: 0, dead: 0, items: [] });
        if (path.includes('/admin/dlq')) return mockJson({ items: [] });
        if (path.includes('/admin/outbox/messaging')) return mockJson({ items: [] });
        if (path.includes('/admin/outbox/rest')) return mockJson({ items: [] });
        if (path.includes('/admin/idempotency')) return mockJson({ items: [] });
        if (path.includes('/admin/integrations/health')) return mockJson({ items: [] });
        if (path.includes('/admin/outbound/dry-run')) return mockJson({ configuredDefault: false, override: null, effective: false });
        if (path.includes('/admin/groovy-tooling/validate')) return mockJson({ valid: true, errors: [] });
        if (path.includes('/admin/groovy-tooling/emulate')) return mockJson({ success: true, output: {} });
        return mockJson({});
      })
    );
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('opens key workbench routes after login', async () => {
    const user = userEvent.setup();

    render(
      <MemoryRouter initialEntries={['/']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <App />
      </MemoryRouter>
    );

    await user.click(await screen.findByRole('button', { name: 'Войти как admin' }));

    expect(await screen.findByRole('heading', { name: 'Ingress / Idempotency / DLQ / Outbox' })).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: 'Runtime Config' }));
    expect(await screen.findByRole('heading', { name: 'Runtime Config' })).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: 'Ревизии Runtime Config' }));
    expect(await screen.findByRole('heading', { name: 'Ревизии runtime-конфигурации' })).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: 'Повтор сообщений' }));
    expect(await screen.findByRole('heading', { name: 'Replay DLQ / Outbox' })).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: 'Интеграции' }));
    expect(await screen.findByRole('heading', { name: 'Состояние интеграций' })).toBeInTheDocument();
  });
});
