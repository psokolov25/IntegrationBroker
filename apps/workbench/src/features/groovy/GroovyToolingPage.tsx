import { useState } from 'react';
import { useI18n } from '../../app/I18nContext';
import { workbenchApi } from '../../api/workbenchApi';

export function GroovyToolingPage() {
  const { t } = useI18n();
  const [script, setScript] = useState('return [status: "OK", context: ctx]');
  const [input, setInput] = useState('{"type":"demo.event"}');
  const [result, setResult] = useState<string>('');

  const validate = async () => {
    const response = await workbenchApi.validateGroovy(script);
    setResult(JSON.stringify(response, null, 2));
  };

  const emulate = async () => {
    try {
      const parsed = JSON.parse(input) as Record<string, unknown>;
      const response = await workbenchApi.emulateGroovy(script, parsed);
      setResult(JSON.stringify(response, null, 2));
    } catch {
      setResult(t('dryRunInvalid'));
    }
  };

  return (
    <section>
      <h2>{t('groovyTitle')}</h2>
      <p>{t('groovyHint')}</p>
      <label>{t('script')}</label>
      <textarea rows={8} value={script} onChange={(e) => setScript(e.target.value)} />
      <label>{t('inputJson')}</label>
      <textarea rows={6} value={input} onChange={(e) => setInput(e.target.value)} />
      <div className="actions">
        <button onClick={validate}>{t('validate')}</button>
        <button onClick={emulate}>{t('emulate')}</button>
      </div>
      <h3>{t('result')}</h3>
      <pre>{result}</pre>
    </section>
  );
}
