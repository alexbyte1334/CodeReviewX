import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import {
  createReview, getCommentPreviews, getRetrievalEvidence, getReviewSnapshot,
  getLocalConfigStatus, publishSelectedCommentPreviews, retryReview, reviewEventsUrl,
  updateCommentPreviewSelection, type LocalConfigStatus, type ReviewApiSnapshot,
} from '../api/reviewApi';
import type { CommentPreview, RetrievalEvidence, ReviewIssue } from '../types/reviewTask';

const TERMINAL = new Set(['SUCCEEDED', 'FAILED', 'SUCCESS']);
const PIPELINE = ['INGEST', 'INDEX', 'RETRIEVE', 'PLAN', 'EXECUTE', 'EVIDENCE', 'PREVIEW'];
type HistoryItem = { runId: string; repositoryUrl: string; prNumber: number; status: string };

function loadHistory(): HistoryItem[] {
  try {
    const value = JSON.parse(localStorage.getItem('codereviewx.review-history') || '[]');
    return Array.isArray(value) ? value : [];
  } catch { return []; }
}

function statusLabel(status: string) {
  if (status === 'SUCCESS' || status === 'SUCCEEDED') return 'READY FOR HUMAN REVIEW';
  if (status === 'FAILED') return 'RUN FAILED';
  if (status === 'RUNNING') return 'LIVE · RUNNING';
  return 'QUEUED';
}

function issueLine(issue: ReviewIssue) {
  return `${issue.filePath}:${issue.startLine}${issue.endLine && issue.endLine !== issue.startLine ? `–${issue.endLine}` : ''}`;
}

function stepState(snapshot: ReviewApiSnapshot, step: string, index: number) {
  if (snapshot.status === 'SUCCESS' || snapshot.status === 'SUCCEEDED') return 'complete';
  if (snapshot.status === 'FAILED') return snapshot.events.some(event => event.type === `STAGE_${step}`) ? 'complete' : index === 0 ? 'active' : 'pending';
  const current = snapshot.events.reduce((latest, event) => event.type.startsWith('STAGE_') ? event.type.replace('STAGE_', '') : latest, 'INGEST');
  const currentIndex = PIPELINE.indexOf(current);
  if (index < currentIndex) return 'complete';
  if (index === currentIndex) return 'active';
  return 'pending';
}

function evidenceScore(score: number) {
  const normalized = score > 1 ? score / 100 : score;
  return Math.max(0, Math.min(100, Math.round(normalized * 100)));
}

export function ReviewRunWorkspace() {
  const [repositoryUrl, setRepositoryUrl] = useState('');
  const [prNumber, setPrNumber] = useState('');
  const [snapshot, setSnapshot] = useState<ReviewApiSnapshot | null>(null);
  const [configStatus, setConfigStatus] = useState<LocalConfigStatus | null>(null);
  const [configLoading, setConfigLoading] = useState(true);
  const [history, setHistory] = useState<HistoryItem[]>(loadHistory);
  const [previews, setPreviews] = useState<CommentPreview[]>([]);
  const [evidence, setEvidence] = useState<Record<string, RetrievalEvidence[]>>({});
  const [evidenceErrors, setEvidenceErrors] = useState<Record<string, string>>({});
  const [selectedIssue, setSelectedIssue] = useState<string | null>(null);
  const [openEvidence, setOpenEvidence] = useState<Set<string>>(new Set());
  const [showComments, setShowComments] = useState(false);
  const [busy, setBusy] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const lastSequence = useRef(0);

  const remember = (next: HistoryItem) => setHistory(previous => {
    const value = [next, ...previous.filter(item => item.runId !== next.runId)].slice(0, 12);
    localStorage.setItem('codereviewx.review-history', JSON.stringify(value));
    return value;
  });

  const refreshConfigStatus = async () => {
    setConfigLoading(true);
    const response = await getLocalConfigStatus();
    if (response.data) setConfigStatus(response.data);
    setConfigLoading(false);
  };

  useEffect(() => { void refreshConfigStatus(); }, []);

  const selectIssue = async (issue: ReviewIssue) => {
    setSelectedIssue(issue.id);
    if (evidence[issue.id]) return;
    const response = await getRetrievalEvidence(snapshot!.runId, issue.id);
    if (response.data) {
      setEvidence(previous => ({ ...previous, [issue.id]: response.data! }));
      setOpenEvidence(previous => new Set([...previous, ...response.data!.map(item => `${issue.id}:${item.rank}`)]));
    } else {
      setEvidenceErrors(previous => ({ ...previous, [issue.id]: response.message || 'No retrieval evidence is available for this run.' }));
    }
  };

  useEffect(() => {
    const match = window.location.hash.match(/^#\/reviews\/([^/?]+)/);
    if (!match) return;
    void getReviewSnapshot(match[1]).then(response => {
      if (response.data) {
        setSnapshot(response.data);
        remember({ runId: response.data.runId, repositoryUrl: response.data.repositoryUrl, prNumber: response.data.prNumber, status: response.data.status });
      }
    });
  }, []);

  useEffect(() => {
    if (!snapshot) return;
    remember({ runId: snapshot.runId, repositoryUrl: snapshot.repositoryUrl, prNumber: snapshot.prNumber, status: snapshot.status });
    if (TERMINAL.has(snapshot.status)) {
      void getCommentPreviews(snapshot.runId).then(response => response.data && setPreviews(response.data.items));
      const firstIssue = snapshot.review.issues?.[0];
      if (firstIssue && !selectedIssue) void selectIssue(firstIssue);
    }
  }, [snapshot?.runId, snapshot?.status]);

  useEffect(() => {
    if (!snapshot || TERMINAL.has(snapshot.status)) return;
    let disposed = false;
    let source: EventSource | null = null;
    let timer: number | undefined;
    let delay = 1000;
    const connect = () => {
      source = new EventSource(reviewEventsUrl(snapshot.runId, lastSequence.current));
      source.onmessage = () => {
        delay = 1000;
        void getReviewSnapshot(snapshot.runId).then(response => {
          if (!disposed && response.data) {
            setSnapshot(response.data);
            lastSequence.current = Math.max(lastSequence.current, ...response.data.events.map(event => event.sequence));
          }
        });
      };
      source.addEventListener('stream-complete', () => {
        source?.close();
        void getReviewSnapshot(snapshot.runId).then(response => response.data && setSnapshot(response.data));
      });
      source.onerror = () => {
        source?.close();
        if (!disposed) { timer = window.setTimeout(connect, delay); delay = Math.min(delay * 2, 15000); }
      };
    };
    connect();
    return () => { disposed = true; source?.close(); if (timer) window.clearTimeout(timer); };
  }, [snapshot?.runId, snapshot?.status]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const githubUrlPattern = /^https:\/\/github\.com\/[^/]+\/[^/]+\/?$/;
    if (!githubUrlPattern.test(repositoryUrl.trim())) {
      setError('请输入标准 GitHub 仓库地址，例如 https://github.com/owner/repository。');
      return;
    }
    const parsedPrNumber = Number(prNumber);
    if (!Number.isInteger(parsedPrNumber) || parsedPrNumber < 1) {
      setError('Pull request 编号必须是正整数。');
      return;
    }
    const currentConfig = configStatus ?? (await getLocalConfigStatus()).data;
    if (!currentConfig || currentConfig.model !== 'READY' || currentConfig.github !== 'READY') {
      setError('基础配置尚未就绪。请先完成模型和 GitHub Token 测试；Embedding/Rerank 可以稍后配置。');
      return;
    }
    setBusy(true); setError(null);
    try {
      const response = await createReview({ repositoryUrl: repositoryUrl.trim().replace(/\/$/, ''), prNumber: parsedPrNumber, inputMode: 'GITHUB_PR' }, crypto.randomUUID());
      if (!response.data) throw new Error(response.message || 'Unable to create review');
      setSnapshot(response.data); lastSequence.current = 0;
      window.history.replaceState(null, '', `#/reviews/${response.data.runId}`);
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to create review'); }
    finally { setBusy(false); }
  }

  function startNewReview() {
    setSnapshot(null); setPreviews([]); setEvidence({}); setEvidenceErrors({}); setSelectedIssue(null); setError(null);
    window.history.replaceState(null, '', '#/reviews/new');
  }

  async function retry() {
    if (!snapshot) return;
    setError(null);
    const response = await retryReview(snapshot.runId);
    if (response.data) { setSnapshot(response.data); setPreviews([]); setEvidence({}); setEvidenceErrors({}); }
    else setError(response.message || 'Unable to retry review');
  }

  const issues = snapshot?.review.issues ?? [];
  const activeIssue = issues.find(issue => issue.id === selectedIssue) ?? issues[0];
  const activeEvidence = activeIssue ? evidence[activeIssue.id] ?? [] : [];
  const highRisk = issues.filter(issue => issue.severity === 'HIGH').length;
  const completed = snapshot && TERMINAL.has(snapshot.status) && snapshot.status !== 'FAILED';
  const lastEvent = snapshot?.events[snapshot.events.length - 1];
  const publishBlocked = !snapshot?.events.some(event => event.type === 'STAGE_EVIDENCE' && event.summary?.toLowerCase().includes('validated'));
  const pipelineMessage = snapshot?.status === 'FAILED'
    ? snapshot.errorMessage || snapshot.review.errorMessage || 'The review pipeline failed.'
    : snapshot?.status === 'SUCCESS' || snapshot?.status === 'SUCCEEDED'
      ? 'Evidence has been collected. Review the proposed comments before publishing.'
      : 'Waiting for the next durable event.';
  const issueRows = useMemo(() => issues.length ? issues : [], [issues]);

  if (!snapshot) {
    return <main className="story story--empty" aria-live="polite">
      <header className="story-header"><div><div className="story-mode-badge story-mode-badge--live">LIVE REVIEW</div><h1>Review a real GitHub pull request</h1><p>Evidence-backed findings, recoverable events, and human approval.</p></div></header>
      <form className="story-create" onSubmit={submit}>
        <label>Repository URL<input required type="url" value={repositoryUrl} onChange={event => setRepositoryUrl(event.target.value)} placeholder="https://github.com/owner/repository" /></label>
        <label>Pull request<input required type="number" min="1" step="1" value={prNumber} onChange={event => setPrNumber(event.target.value)} placeholder="42" /></label>
        <button type="submit" className="story-primary" disabled={busy || configLoading}>{busy ? 'Queueing review…' : configLoading ? 'Checking configuration…' : 'Run review'} <span aria-hidden="true">→</span></button>
      </form>
      <section className="story-config-strip" aria-label="Local configuration status">
        <div><span className={`story-status-dot story-status-dot--${configStatus?.model === 'READY' ? 'ready' : 'blocked'}`} /><strong>Model</strong><span>{configStatus?.model ?? 'CHECKING'}</span></div>
        <div><span className={`story-status-dot story-status-dot--${configStatus?.github === 'READY' ? 'ready' : 'blocked'}`} /><strong>GitHub</strong><span>{configStatus?.github ?? 'CHECKING'}</span></div>
        <div><span className={`story-status-dot story-status-dot--${configStatus?.evidenceAvailable ? 'ready' : 'optional'}`} /><strong>Evidence</strong><span>{configStatus?.evidenceAvailable ? 'READY' : 'OPTIONAL / DEGRADED'}</span></div>
        <button type="button" className="story-config-refresh" onClick={() => void refreshConfigStatus()} disabled={configLoading}>{configLoading ? 'Checking…' : 'Refresh status'}</button>
      </section>
      {error && <div className="story-mode-notice story-mode-notice--error" role="alert">{error}</div>}
      {history.length > 0 && <div className="story-history"><strong>Recent runs</strong>{history.map(item => <button key={item.runId} type="button" onClick={() => void getReviewSnapshot(item.runId).then(response => response.data && setSnapshot(response.data))}>{item.repositoryUrl.replace('https://github.com/', '')} #{item.prNumber}<span>{item.status}</span></button>)}</div>}
    </main>;
  }

  return <main className="story" aria-live="polite">
    <header className="story-header">
      <div>
        <div className={`story-mode-badge story-mode-badge--${snapshot.status === 'FAILED' ? 'error' : 'live'}`}>{statusLabel(snapshot.status)}</div>
        <h1>Live Review Run</h1>
        <p>{snapshot.repositoryUrl.replace('https://github.com/', '')} · Pull request #{snapshot.prNumber} · <span className="story-run-id">{snapshot.runId.slice(0, 8)}</span></p>
      </div>
      <div className="story-controls"><button type="button" className="story-button" onClick={startNewReview}>New review</button>{snapshot.status === 'FAILED' && <button type="button" className="story-button story-button--icon" onClick={() => void retry()}>Retry run</button>}</div>
    </header>

    <div className="story-pipeline" aria-label="Review pipeline">
      <div className="story-progress-track" aria-hidden="true"><span className="story-progress-fill" style={{ width: `${completed ? 100 : snapshot.status === 'RUNNING' ? 28 : snapshot.status === 'FAILED' ? 18 : 5}%` }} /></div>
      <div className="story-steps">{PIPELINE.map((step, index) => {
        const state = stepState(snapshot, step, index);
        return <div key={step} className={`story-step story-step--${state}`}><span className="story-step-number">{state === 'complete' ? '✓' : index + 1}</span><span className="story-step-copy"><strong>{step}</strong><small>{state === 'complete' ? 'COMPLETE' : state === 'active' ? snapshot.status : 'WAITING'}</small></span></div>;
      })}</div>
    </div>

    <div className={`story-note${snapshot.status === 'FAILED' ? ' story-note--error' : ''}`} role={snapshot.status === 'FAILED' ? 'alert' : 'status'}><span className="story-note-mark" aria-hidden="true">{snapshot.status === 'FAILED' ? '!' : 'i'}</span><span>{pipelineMessage}{lastEvent?.createdAt ? ` · Last event ${new Date(lastEvent.createdAt).toLocaleTimeString()}` : ''}</span></div>

    <div className="story-stage">
      <section className="story-code" aria-label="Pull request findings">
        <header className="story-panel-header"><div><strong>Changed lines</strong><span> · {snapshot.repositoryUrl.replace('https://github.com/', '')}#{snapshot.prNumber}</span></div><div className="story-diff-count"><span>{issues.length} findings</span><span>{highRisk} high risk</span></div></header>
        <div className="story-code-body" role="list">
          {issueRows.length ? issueRows.map(issue => <button type="button" role="listitem" key={issue.id} className={`story-code-line${activeIssue?.id === issue.id ? ' story-code-line--selected' : ''}`} onClick={() => void selectIssue(issue)}><span className="story-code-number">{issue.startLine}</span><code>{issueLine(issue)}</code><span className="story-finding-pin">{issue.severity}</span><span className="story-finding-copy">{issue.title}</span></button>) : <div className="story-empty-panel">Findings will appear here when the run reaches Evidence.</div>}
        </div>
        <footer className="story-code-footer"><span>{issues.length} evidence-gated finding(s)</span>{activeIssue && <><span className="story-risk">{activeIssue.severity}</span><span>{activeIssue.category}</span></>}</footer>
      </section>

      <section className="story-evidence" aria-label="Evidence inspector">
        <header className="story-panel-header story-panel-header--evidence"><div><span className="story-shield" aria-hidden="true">✓</span><strong>Why this finding is trusted</strong></div><span>Evidence · {activeEvidence.length}</span></header>
        <div className="story-evidence-list">
          {activeIssue && <div className="story-active-finding"><strong>{activeIssue.title}</strong><p>{activeIssue.description}</p><small>Recommendation: {activeIssue.recommendation}</small></div>}
          {activeEvidence.map(item => { const key = `${activeIssue!.id}:${item.rank}`; const open = openEvidence.has(key); const score = evidenceScore(item.score); return <article className={`story-evidence-item${open ? ' story-evidence-item--open' : ''}`} key={key}><button type="button" className="story-evidence-trigger" onClick={() => setOpenEvidence(current => { const next = new Set(current); if (next.has(key)) next.delete(key); else next.add(key); return next; })} aria-expanded={open}><span className="story-evidence-rank">{item.rank}</span><span className="story-evidence-title"><strong>{item.path}</strong><small>Lines {item.startLine}–{item.endLine}</small></span><span className="story-score"><span>Relevance {score}%</span><i><b style={{ width: `${score}%` }} /></i></span><span className={`story-caret${open ? ' story-caret--open' : ''}`} /></button>{open && <div className="story-evidence-body"><pre>{item.excerpt}</pre><p><strong>Citation:</strong> {item.citationLabel}</p><footer><span>Grounded repository context</span><span className="story-verified">✓ Verified</span></footer></div>}</article>; })}
          {!activeIssue && <div className="story-empty-panel">Select a finding to inspect its evidence.</div>}
          {activeIssue && activeEvidence.length === 0 && <div className="story-empty-panel">{evidenceErrors[activeIssue.id] ? 'No retrieval evidence is available for this run.' : 'Loading repository evidence…'}</div>}
          <details className="story-trace"><summary>Run events · {snapshot.events.length}</summary>{snapshot.events.map(event => <div key={event.sequence}><strong>#{event.sequence} {event.type}</strong><span>{event.summary || event.status}</span><small>{event.status}</small></div>)}</details>
        </div>
      </section>
    </div>

    <footer className="story-results"><div className="story-result"><strong>{issues.length}</strong><span>findings</span></div><div className="story-result story-result--danger"><strong>{highRisk}</strong><span>high risk</span></div><div className="story-result story-result--accent"><strong>{activeEvidence.length}</strong><span>evidence items</span></div><div className="story-ready"><span className="story-ready-mark">{completed ? '✓' : '…'}</span><span>{completed ? (publishBlocked ? 'Publish blocked: Evidence unavailable' : 'Publish allowed') : snapshot.status}</span></div><button type="button" className="story-primary" onClick={() => setShowComments(true)} disabled={previews.length === 0}>Review comments <span aria-hidden="true">→</span></button></footer>

    {showComments && <div className="story-drawer" role="dialog" aria-modal="true" aria-labelledby="story-comments-title"><button type="button" className="story-drawer-backdrop" onClick={() => setShowComments(false)} aria-label="Close comments" /><div className="story-drawer-panel"><header><div><h2 id="story-comments-title">Human review</h2><p>Select previews first. Publishing remains explicitly owner-controlled.</p></div><button type="button" onClick={() => setShowComments(false)}>Close</button></header>{previews.map(preview => <label className="story-comment" key={preview.id}><input type="checkbox" checked={preview.selectedForPublish} onChange={event => { const ids = previews.filter(item => item.id === preview.id ? event.target.checked : item.selectedForPublish).map(item => item.id); void updateCommentPreviewSelection(snapshot.runId, ids).then(response => response.data && setPreviews(response.data.items)); }} /><span><strong>{preview.filePath}:{preview.line}</strong><small>{preview.publishStatus} · {preview.issueId}</small><p>{preview.draftBody}</p></span></label>)}{publishBlocked && <div className="story-mode-notice story-mode-notice--error">Publish blocked: Evidence unavailable. Configure Embedding/Rerank and rerun this PR.</div>}<div className="story-decision-actions"><button type="button" className="story-secondary" onClick={() => setShowComments(false)}>Cancel</button><button type="button" className="story-primary story-primary--publish" disabled={publishing || publishBlocked || !previews.some(item => item.selectedForPublish)} onClick={async () => { setPublishing(true); const response = await publishSelectedCommentPreviews(snapshot.runId); if (response.data) setPreviews(response.data.items); else setError(response.message || 'Publishing was rejected'); setPublishing(false); }}>{publishing ? 'Publishing…' : 'Approve and publish selected'}</button></div></div></div>}
  </main>;
}
