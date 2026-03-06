import { describe, it, expect, vi, afterEach } from 'vitest';
import { KeycloakSessionManager, type KeycloakAuthConfig } from './keycloak';

const config: KeycloakAuthConfig = {
  authority: 'http://localhost:8080',
  realm: 'master',
  clientId: 'ib-workbench',
  redirectUri: 'http://localhost/callback',
  postLogoutRedirectUri: 'http://localhost/'
};

function makeJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'none', typ: 'JWT' })).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const body = btoa(JSON.stringify(payload)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${header}.${body}.sig`;
}

describe('KeycloakSessionManager', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    window.sessionStorage.clear();
    window.history.replaceState({}, document.title, '/');
  });

  it('uses fallback claim strategy for userName when preferred_username is absent', async () => {
    const manager = new KeycloakSessionManager();
    const state = 'state-1';

    window.sessionStorage.setItem('ib.workbench.oidc.state', state);
    window.sessionStorage.setItem('ib.workbench.oidc.verifier', 'verifier-1');
    window.history.replaceState({}, document.title, `/callback?code=abc&state=${state}`);

    const accessToken = makeJwt({ email: 'operator@example.com', exp: Math.floor(Date.now() / 1000) + 300 });
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new globalThis.Response(JSON.stringify({ access_token: accessToken }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        })
      )
    );

    const session = await manager.handleOidcCallback(config);

    expect(session?.userName).toBe('operator@example.com');
    expect(session?.expiresAt).toBeTypeOf('number');
  });
});
