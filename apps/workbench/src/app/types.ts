import type { TranslationKey } from './i18n';

export type UserRole = 'admin' | 'operator' | 'auditor' | 'support';

export interface SessionState {
  authenticated: boolean;
  userName: string;
  role: UserRole;
  accessToken?: string;
}

export interface NavItem {
  path: string;
  label: TranslationKey;
  roles: UserRole[];
}
