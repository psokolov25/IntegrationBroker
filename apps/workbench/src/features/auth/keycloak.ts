import type { SessionState, UserRole } from '../../app/types';

const randomString = (): string => crypto.randomUUID().replace(/-/g, '');
const PKCE_STATE_KEY = 'ib.workbench.oidc.state';
const PKCE_VERIFIER_KEY = 'ib.workbench.oidc.verifier';

export interface KeycloakAuthConfig {
  authority: string;
  realm: string;
  clientId: string;
  redirectUri: string;
  postLogoutRedirectUri: string;
}

export class KeycloakSessionManager {
  private session: SessionState | null = null;

  getSession(): SessionState | null {
    return this.session;
  }

  login(role: UserRole = 'operator'): SessionState {
    this.session = {
      authenticated: true,
      userName: `demo.${role}`,
      role,
      accessToken: `mock-token-${randomString()}`
    };
    return this.session;
  }

  logout(): void {
    this.session = null;
  }

  private static base64UrlEncode(bytes: Uint8Array): string {
    return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }

  private async sha256(input: string): Promise<string> {
    const encoded = new TextEncoder().encode(input);
    const digest = await crypto.subtle.digest('SHA-256', encoded);
    return KeycloakSessionManager.base64UrlEncode(new Uint8Array(digest));
  }

  async buildPkceLoginUrl(config: KeycloakAuthConfig, state = randomString()): Promise<string> {
    const codeVerifier = randomString() + randomString();
    const codeChallenge = await this.sha256(codeVerifier);
    window.sessionStorage.setItem(PKCE_STATE_KEY, state);
    window.sessionStorage.setItem(PKCE_VERIFIER_KEY, codeVerifier);
    const base = `${config.authority}/realms/${config.realm}/protocol/openid-connect/auth`;
    const query = new URLSearchParams({
      client_id: config.clientId,
      response_type: 'code',
      scope: 'openid profile',
      redirect_uri: config.redirectUri,
      state,
      code_challenge_method: 'S256',
      code_challenge: codeChallenge
    });
    return `${base}?${query.toString()}`;
  }

  async handleOidcCallback(config: KeycloakAuthConfig): Promise<SessionState | null> {
    const currentUrl = new URL(window.location.href);
    const code = currentUrl.searchParams.get('code');
    const state = currentUrl.searchParams.get('state');
    if (!code || !state) {
      return null;
    }

    const expectedState = window.sessionStorage.getItem(PKCE_STATE_KEY);
    const verifier = window.sessionStorage.getItem(PKCE_VERIFIER_KEY);
    if (!expectedState || !verifier || expectedState !== state) {
      throw new Error('Invalid OIDC callback state');
    }

    const tokenEndpoint = `${config.authority}/realms/${config.realm}/protocol/openid-connect/token`;
    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: config.clientId,
      code,
      code_verifier: verifier,
      redirect_uri: config.redirectUri
    });

    const response = await fetch(tokenEndpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString()
    });

    if (!response.ok) {
      throw new Error(`Token exchange failed: HTTP ${response.status}`);
    }

    const tokenPayload = (await response.json()) as { access_token?: string; preferred_username?: string };
    const accessToken = tokenPayload.access_token;
    if (!accessToken) {
      throw new Error('Token exchange failed: no access_token');
    }

    this.session = {
      authenticated: true,
      userName: tokenPayload.preferred_username ?? 'keycloak.user',
      role: 'operator',
      accessToken
    };

    currentUrl.searchParams.delete('code');
    currentUrl.searchParams.delete('state');
    currentUrl.searchParams.delete('session_state');
    window.history.replaceState({}, document.title, `${currentUrl.pathname}${currentUrl.search}`);
    window.sessionStorage.removeItem(PKCE_STATE_KEY);
    window.sessionStorage.removeItem(PKCE_VERIFIER_KEY);
    return this.session;
  }

  buildLogoutUrl(config: KeycloakAuthConfig): string {
    const base = `${config.authority}/realms/${config.realm}/protocol/openid-connect/logout`;
    const query = new URLSearchParams({
      post_logout_redirect_uri: config.postLogoutRedirectUri,
      client_id: config.clientId
    });
    return `${base}?${query.toString()}`;
  }
}
