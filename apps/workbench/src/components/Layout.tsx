import { Link, NavLink } from 'react-router-dom';
import type { ReactNode } from 'react';
import type { NavItem, SessionState, UserRole } from '../app/types';
import { useI18n } from '../app/I18nContext';
import { isSupportedLocale, LOCALE_LABELS, POPULAR_LOCALE_ORDER } from '../app/i18n';

const navItems: NavItem[] = [
  { path: '/monitoring', label: 'navMonitoring', roles: ['admin', 'operator', 'auditor', 'support'] },
  { path: '/runtime-config', label: 'navRuntime', roles: ['admin', 'operator'] },
  { path: '/replay', label: 'navReplay', roles: ['admin', 'operator', 'support'] },
  { path: '/groovy-tooling', label: 'navGroovy', roles: ['admin', 'operator'] },
  { path: '/integrations', label: 'navIntegrations', roles: ['admin', 'auditor', 'support'] },
  { path: '/incidents', label: 'navIncidents', roles: ['admin', 'support', 'auditor'] }
];

export function Layout({
  session,
  onLogout,
  role,
  onRoleChange,
  children
}: {
  session: SessionState;
  onLogout: () => void;
  role: UserRole;
  onRoleChange: (role: UserRole) => void;
  children: ReactNode;
}) {
  const { t, locale, setLocale } = useI18n();
  const allowedItems = navItems.filter((item) => item.roles.includes(session.role));

  return (
    <div className="layout">
      <aside>
        <h1>{t('appTitle')}</h1>
        <p>{session.userName}</p>
        <label>
          {t('role')}
          <select value={role} onChange={(e) => onRoleChange(e.target.value as UserRole)}>
            <option value="admin">admin</option>
            <option value="operator">operator</option>
            <option value="auditor">auditor</option>
            <option value="support">support</option>
          </select>
        </label>
        <label>
          {t('language')}
          <select value={locale} onChange={(e) => setLocale(e.target.value as typeof locale)}>
            {POPULAR_LOCALE_ORDER.map((code) => (
              <option key={code} value={code}>
                {LOCALE_LABELS[code]}{isSupportedLocale(code) ? '' : ` (${t('soon')})`}
              </option>
            ))}
          </select>
        </label>
        <nav>
          {allowedItems.map((item) => (
            <NavLink key={item.path} to={item.path} className={({ isActive }) => (isActive ? 'active' : '')}>
              {t(item.label)}
            </NavLink>
          ))}
        </nav>
        <button onClick={onLogout}>{t('logout')}</button>
      </aside>
      <main>
        {children}
        <footer>
          <Link to="/">{t('dashboard')}</Link>
        </footer>
      </main>
    </div>
  );
}
