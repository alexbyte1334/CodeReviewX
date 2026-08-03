import { FormEvent, useEffect, useState } from 'react';
import { applyLocalConfig, getModelPresets, LocalConfigRequest, testLocalGithub, testLocalModel, testLocalRag } from '../api/reviewApi';

const defaults: LocalConfigRequest = {
  provider: 'custom', modelBaseUrl: '', modelName: '', modelApiKey: '', githubToken: '',
  embeddingBaseUrl: '', embeddingApiKey: '', embeddingModel: 'BAAI/bge-m3',
  rerankBaseUrl: '', rerankApiKey: '', rerankModel: 'BAAI/bge-reranker-v2-m3',
};

type CheckState = 'IDLE' | 'READY' | 'FAILED' | 'OPTIONAL_NOT_CONFIGURED';

export function SetupWizard({ onComplete }: { onComplete: () => void }) {
  const [form, setForm] = useState(defaults);
  const [presets, setPresets] = useState<Record<string, {label:string; baseUrl:string; model:string}>>({});
  const [step, setStep] = useState(0);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [modelState, setModelState] = useState<CheckState>('IDLE');
  const [githubState, setGithubState] = useState<CheckState>('IDLE');
  const [embeddingState, setEmbeddingState] = useState<CheckState>('IDLE');
  const [rerankState, setRerankState] = useState<CheckState>('IDLE');

  useEffect(() => { void getModelPresets().then(response => response.data && setPresets(response.data)); }, []);
  const update = (key: keyof LocalConfigRequest, value: string) => setForm(current => ({ ...current, [key]: value }));
  const selectProvider = (provider: string) => {
    const preset = presets[provider];
    setForm(current => ({ ...current, provider, modelBaseUrl: preset?.baseUrl ?? current.modelBaseUrl, modelName: preset?.model ?? current.modelName }));
  };
  const field = (label: string, key: keyof LocalConfigRequest, type = 'text', placeholder = '') => <label className="setup-field">{label}<input type={type} value={form[key]} required={step < 2} onChange={event => update(key, event.target.value)} placeholder={placeholder} autoComplete={type === 'password' ? 'new-password' : 'off'} /></label>;

  async function testBasics() {
    setBusy(true); setMessage('');
    const [github, model] = await Promise.all([
      testLocalGithub({ baseUrl: 'https://api.github.com', token: form.githubToken }),
      testLocalModel({ provider: form.provider, baseUrl: form.modelBaseUrl, model: form.modelName, apiKey: form.modelApiKey }),
    ]);
    const githubReady = github.data?.github === 'READY';
    const modelReady = model.data?.model === 'READY';
    setGithubState(githubReady ? 'READY' : 'FAILED'); setModelState(modelReady ? 'READY' : 'FAILED');
    if (!githubReady || !modelReady) setMessage([github.data?.reason, model.data?.reason, github.message, model.message].find(Boolean) || '请检查 GitHub Token、模型地址、模型名和 API Key。');
    else setStep(2);
    setBusy(false);
  }

  async function testOptionalRag() {
    if (!form.embeddingBaseUrl && !form.embeddingApiKey && !form.rerankBaseUrl && !form.rerankApiKey) {
      setEmbeddingState('OPTIONAL_NOT_CONFIGURED'); setRerankState('OPTIONAL_NOT_CONFIGURED'); setStep(3); return;
    }
    setBusy(true); setMessage('');
    const response = await testLocalRag({ embeddingBaseUrl: form.embeddingBaseUrl, embeddingApiKey: form.embeddingApiKey, embeddingModel: form.embeddingModel, rerankBaseUrl: form.rerankBaseUrl, rerankApiKey: form.rerankApiKey, rerankModel: form.rerankModel });
    const embedding = response.data?.embedding === 'READY'; const rerank = response.data?.rerank === 'READY';
    setEmbeddingState(embedding ? 'READY' : 'FAILED'); setRerankState(rerank ? 'READY' : 'FAILED');
    if (embedding && rerank) setStep(3);
    else setMessage(response.data?.embeddingReason || response.data?.rerankReason || response.message || 'RAG 服务连接失败；可以返回修改，或跳过进入降级模式。');
    setBusy(false);
  }

  async function save(event: FormEvent) {
    event.preventDefault(); setBusy(true); setMessage('');
    const response = await applyLocalConfig(form);
    if (!response.data) { setMessage(response.message || '配置保存失败。'); setBusy(false); return; }
    window.codereviewx?.saveCredentials(form);
    localStorage.setItem('codereviewx.setup-complete', 'true');
    if (window.codereviewx?.restart) window.codereviewx.restart(); else onComplete();
  }

  return <main className="setup-page" aria-live="polite"><section className="setup-card"><div className="story-mode-badge story-mode-badge--live">PERSONAL EDITION · STEP {step + 1}/4</div>
    {step === 0 && <><h1>Run CodeReviewX locally</h1><p>数据和凭证只保存在这台 Mac。先完成基础连接，Embedding/Rerank 可以稍后配置。</p><button className="story-primary setup-submit" type="button" onClick={() => setStep(1)}>开始配置 →</button></>}
    {step === 1 && <form onSubmit={event => { event.preventDefault(); void testBasics(); }}><h1>连接基础服务</h1><p>GitHub 用于读取 PR；模型用于生成 Review。两项测试通过后才能继续。</p><h2>GitHub</h2>{field('GitHub personal access token', 'githubToken', 'password', 'github_pat_…')}<h2>Review model</h2><label className="setup-field">Provider<select value={form.provider} onChange={event => selectProvider(event.target.value)}>{Object.entries(presets).map(([key, preset]) => <option key={key} value={key}>{preset.label}</option>)}</select></label>{field('Base URL', 'modelBaseUrl', 'url', 'https://api.openai.com/v1')}{field('Model name', 'modelName', 'text', 'gpt-4o-mini')}{field('API key', 'modelApiKey', 'password', 'model key')}<button className="story-primary setup-submit" disabled={busy}>{busy ? '正在测试…' : '测试 GitHub 和模型 →'}</button></form>}
    {step === 2 && <form onSubmit={event => { event.preventDefault(); void testOptionalRag(); }}><h1>Evidence 增强（可选）</h1><p>配置外部 Embedding 和 Rerank 后，Review 才能生成可发布的 Evidence。暂时跳过也可以使用基础 Review 和本地 Preview。</p><h2>Embedding</h2>{field('Embedding Base URL', 'embeddingBaseUrl', 'url', 'https://…/v1')}{field('Embedding API key', 'embeddingApiKey', 'password')}{field('Embedding model', 'embeddingModel')}<h2>Rerank</h2>{field('Rerank Base URL', 'rerankBaseUrl', 'url', 'https://…/v1')}{field('Rerank API key', 'rerankApiKey', 'password')}{field('Rerank model', 'rerankModel')}<button className="story-primary setup-submit" type="submit" disabled={busy}>{busy ? '正在测试…' : '测试 RAG →'}</button><button className="story-button setup-skip" type="button" onClick={() => { setEmbeddingState('OPTIONAL_NOT_CONFIGURED'); setRerankState('OPTIONAL_NOT_CONFIGURED'); setStep(3); }}>暂时跳过，使用降级模式</button></form>}
    {step === 3 && <form onSubmit={save}><h1>检查并启动</h1><p>基础 Review 可以运行；当前发布权限取决于 Evidence 是否可用。</p><div className="setup-status-list"><div>GitHub <strong>{githubState}</strong></div><div>Model <strong>{modelState}</strong></div><div>Embedding <strong>{embeddingState}</strong></div><div>Rerank <strong>{rerankState}</strong></div><div>Publish <strong>{embeddingState === 'READY' && rerankState === 'READY' ? 'ALLOWED' : 'BLOCKED_WITHOUT_EVIDENCE'}</strong></div></div><button className="story-primary setup-submit" disabled={busy}>{busy ? '保存并重启…' : '保存配置并启动 CodeReviewX →'}</button></form>}
    {message && <div className="story-mode-notice story-mode-notice--error" role="alert">{message}</div>}
  </section></main>;
}
