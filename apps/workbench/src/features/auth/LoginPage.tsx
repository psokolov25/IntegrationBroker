import type { UserRole } from '../../app/types';
import { useI18n } from '../../app/I18nContext';

export function LoginPage({ onLogin, onKeycloakLogin }: { onLogin: (role: UserRole) => void; onKeycloakLogin: () => void }) {
  const { t } = useI18n();

  return (
    <div className="card">
      <h2>{t('loginTitle')}</h2>
      <p>{t('loginDescription')}</p>
      <div className="actions">
        <button onClick={onKeycloakLogin}>{t('loginKeycloak')}</button>
      </div>
      <div className="grid2">
        <button onClick={() => onLogin('admin')}>{t('loginAdmin')}</button>
        <button onClick={() => onLogin('operator')}>{t('loginOperator')}</button>
        <button onClick={() => onLogin('auditor')}>{t('loginAuditor')}</button>
        <button onClick={() => onLogin('support')}>{t('loginSupport')}</button>
      </div>
    </div>
  );
}
