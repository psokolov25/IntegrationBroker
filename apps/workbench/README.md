# Integration Broker Workbench (React + TypeScript)

Стартовый frontend-контур для задач roadmap 19-27:
- каркас приложения на Vite + React + TS;
- login/logout c OIDC PKCE callback обработкой для Keycloak + demo role login для локальной отладки;
- role-aware layout для ролей admin/operator/auditor/support;
- RU-first локализация с региональными кодами `ru-RU`/`en-EN` + roadmap локалей по популярности;
- страницы: Monitoring, Runtime Config (dry-run + save with server/local fallback), Runtime Config Revisions (read-only), Replay, Groovy Tooling, Integrations, Incident Export (JSON/Markdown).

## Локальный запуск

```bash
cd apps/workbench
npm install
npm run dev
```

## Проверки

```bash
npm run lint
npm run build
```

См. также: `docs/roadmap/release-notes-packet-02.md`.
