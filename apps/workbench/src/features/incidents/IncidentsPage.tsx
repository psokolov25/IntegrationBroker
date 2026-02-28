import { useI18n } from '../../app/I18nContext';

const sampleIncident = {
  id: 'incident-001',
  source: 'crm',
  message: 'Sensitive payload masked',
  payload: { customerPhone: '***', branchId: '12' }
};

export function IncidentsPage() {
  const { t } = useI18n();

  const exportJson = () => {
    const blob = new Blob([JSON.stringify(sampleIncident, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${sampleIncident.id}.sanitized.json`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const exportMarkdown = () => {
    const markdown = `# Incident ${sampleIncident.id}\n\n- Source: ${sampleIncident.source}\n- Message: ${sampleIncident.message}\n\n## Payload\n\n\`\`\`json\n${JSON.stringify(sampleIncident.payload, null, 2)}\n\`\`\`\n`;
    const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${sampleIncident.id}.sanitized.md`;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <section>
      <h2>{t('incidentsTitle')}</h2>
      <pre>{JSON.stringify(sampleIncident, null, 2)}</pre>
      <div className="actions">
        <button onClick={exportJson}>{t('exportJson')}</button>
        <button onClick={exportMarkdown}>{t('exportMarkdown')}</button>
      </div>
    </section>
  );
}
