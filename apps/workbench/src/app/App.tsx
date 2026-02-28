import { Navigate, Route, Routes } from 'react-router-dom';
import { useMemo, useState, useEffect } from 'react';
import { KeycloakSessionManager, type KeycloakAuthConfig } from '../features/auth/keycloak';
import type { UserRole } from './types';
import { LoginPage } from '../features/auth/LoginPage';
import { Layout } from '../components/Layout';
import { MonitoringPage } from '../features/monitoring/MonitoringPage';
import { RuntimeConfigPage } from '../features/runtimeConfig/RuntimeConfigPage';
import { ReplayPage } from '../features/replay/ReplayPage';
import { GroovyToolingPage } from '../features/groovy/GroovyToolingPage';
import { IntegrationsPage } from '../features/integrations/IntegrationsPage';
import { IncidentsPage } from '../features/incidents/IncidentsPage';
import { I18nContext } from './I18nContext';
import type { LocaleCode, TranslationKey } from './i18n';
import { detectInitialLocale, normalizeLocale, translate } from './i18n';

const authManager = new KeycloakSessionManager();

const keycloakConfig: KeycloakAuthConfig = {
  authority: import.meta.env.VITE_KEYCLOAK_AUTHORITY ?? 'http://localhost:8080',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'master',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'integration-broker-workbench',
  redirectUri: import.meta.env.VITE_KEYCLOAK_REDIRECT_URI ?? window.location.origin,
  postLogoutRedirectUri: import.meta.env.VITE_KEYCLOAK_LOGOUT_REDIRECT_URI ?? window.location.origin
};

export function App() {
  const auth = useMemo(() => authManager, []);
  const [role, setRole] = useState<UserRole>('operator');
  const [locale, setLocaleState] = useState<LocaleCode>(detectInitialLocale());
  const [version, setVersion] = useState(0);
  const [authLoading, setAuthLoading] = useState(true);
  const session = auth.getSession();

  useEffect(() => {
    let isActive = true;
    auth
      .handleOidcCallback(keycloakConfig)
      .catch(() => null)
      .finally(() => {
        if (isActive) {
          setAuthLoading(false);
          setVersion((v) => v + 1);
        }
      });
    return () => {
      isActive = false;
    };
  }, [auth]);

  const t = (key: TranslationKey) => translate(locale, key);

  const setLocale = (nextLocale: LocaleCode) => {
    const normalized = normalizeLocale(nextLocale);
    setLocaleState(normalized);
    window.localStorage.setItem('ib.workbench.locale', normalized);
  };

  const handleLogin = (selectedRole: UserRole) => {
    setRole(selectedRole);
    auth.login(selectedRole);
    setVersion((v) => v + 1);
  };

  const handleRoleChange = (selectedRole: UserRole) => {
    setRole(selectedRole);
    auth.login(selectedRole);
    setVersion((v) => v + 1);
  };

  const handleLogout = () => {
    auth.logout();
    setVersion((v) => v + 1);
  };

  const handleKeycloakLogin = async () => {
    const url = await auth.buildPkceLoginUrl(keycloakConfig);
    window.location.assign(url);
  };

  if (authLoading) {
    return <p>{t('loading')}</p>;
  }

  return (
    <I18nContext.Provider value={{ locale, setLocale, t }}>
      {!session || !session.authenticated ? (
        <LoginPage onLogin={handleLogin} onKeycloakLogin={handleKeycloakLogin} />
      ) : (
        <Layout key={version} session={session} onLogout={handleLogout} role={role} onRoleChange={handleRoleChange}>
          <Routes>
            <Route path="/" element={<Navigate to="/monitoring" replace />} />
            <Route path="/monitoring" element={<MonitoringPage />} />
            <Route path="/runtime-config" element={<RuntimeConfigPage />} />
            <Route path="/replay" element={<ReplayPage />} />
            <Route path="/groovy-tooling" element={<GroovyToolingPage />} />
            <Route path="/integrations" element={<IntegrationsPage />} />
            <Route path="/incidents" element={<IncidentsPage />} />
          </Routes>
        </Layout>
      )}
    </I18nContext.Provider>
  );
}
