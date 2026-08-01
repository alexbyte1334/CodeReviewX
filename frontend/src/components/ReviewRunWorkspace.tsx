import { useEffect, useRef, useState } from 'react';
import {
  createReview, getCommentPreviews, getRetrievalEvidence, getReviewSnapshot,
  publishSelectedCommentPreviews, retryReview, reviewEventsUrl,
  updateCommentPreviewSelection, type ReviewApiSnapshot,
} from '../api/reviewApi';
import type { CommentPreview, RetrievalEvidence } from '../types/reviewTask';

const TERMINAL = new Set(['SUCCEEDED', 'FAILED', 'SUCCESS']);
type HistoryItem = { runId: string; repositoryUrl: string; prNumber: number; status: string };
function loadHistory(): HistoryItem[] {
  try { const value = JSON.parse(localStorage.getItem('codereviewx.review-history') || '[]'); return Array.isArray(value) ? value : []; }
  catch { return []; }
}

export function ReviewRunWorkspace() {
  const [repositoryUrl, setRepositoryUrl] = useState('');
  const [prNumber, setPrNumber] = useState('');
  const [snapshot, setSnapshot] = useState<ReviewApiSnapshot | null>(null);
  const [history, setHistory] = useState<HistoryItem[]>(loadHistory);
  const [previews, setPreviews] = useState<CommentPreview[]>([]);
  const [evidence, setEvidence] = useState<Record<string, RetrievalEvidence[]>>({});
  const [busy, setBusy] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const lastSequence = useRef(0);

  const remember = (next: HistoryItem) => setHistory(previous => {
    const value = [next, ...previous.filter(item => item.runId !== next.runId)].slice(0, 12);
    localStorage.setItem('codereviewx.review-history', JSON.stringify(value));
    return value;
  });

  useEffect(() => {
    const match = window.location.hash.match(/^#\/reviews\/([^/?]+)/);
    if (match) void getReviewSnapshot(match[1]).then(response => { if (response.data) { setSnapshot(response.data); remember(response.data); } });
  }, []);

  useEffect(() => {
    if (!snapshot) return;
    remember(snapshot);
    if (TERMINAL.has(snapshot.status)) void getCommentPreviews(snapshot.runId).then(response => response.data && setPreviews(response.data.items));
  }, [snapshot?.runId, snapshot?.status]);

  useEffect(() => {
    if (!snapshot || TERMINAL.has(snapshot.status)) return;
    let disposed = false; let source: EventSource | null = null; let timer: number | undefined; let delay = 1000;
    const connect = () => {
      source = new EventSource(reviewEventsUrl(snapshot.runId, lastSequence.current));
      source.onmessage = () => { delay = 1000; void getReviewSnapshot(snapshot.runId).then(response => { if (!disposed && response.data) { setSnapshot(response.data); lastSequence.current = Math.max(lastSequence.current, ...response.data.events.map(event => event.sequence)); } }); };
      source.addEventListener('stream-complete', () => { source?.close(); void getReviewSnapshot(snapshot.runId).then(response => response.data && setSnapshot(response.data)); });
      source.onerror = () => { source?.close(); if (!disposed) { timer = window.setTimeout(connect, delay); delay = Math.min(delay * 2, 15000); } };
    };
    connect();
    return () => { disposed = true; source?.close(); if (timer) window.clearTimeout(timer); };
  }, [snapshot?.runId, snapshot?.status]);

  async function submit(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError(null);
    try {
      const response = await createReview({ repositoryUrl, prNumber: Number(prNumber), inputMode: 'GITHUB_PR' }, crypto.randomUUID());
      if (!response.data) throw new Error(response.message || 'Unable to create review');
      setSnapshot(response.data); lastSequence.current = 0;
      window.history.replaceState(null, '', `#/reviews/${response.data.runId}`);
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to create review'); }
    finally { setBusy(false); }
  }

  async function loadEvidence(issueKey: string) {
    if (!snapshot || evidence[issueKey]) return;
    const response = await getRetrievalEvidence(snapshot.runId, issueKey);
    if (response.data) setEvidence(previous => ({ ...previous, [issueKey]: response.data! }));
  }

  async function retry() { if (!snapshot) return; const response = await retryReview(snapshot.runId); if (response.data) setSnapshot(response.data); }

  return <main className="review-run-workspace" aria-live="polite">
    <header className="review-run-header"><p className="eyebrow">REVIEW WORKSPACE</p><h1>Review a real GitHub pull request</h1><p>Queued execution, recoverable events, evidence-backed findings, and human approval.</p></header>
    {history.length > 0 && <nav className="review-history" aria-label="Review history"><strong>Recent runs</strong>{history.map(item => <button type="button" key={item.runId} onClick={() => void getReviewSnapshot(item.runId).then(response => response.data && setSnapshot(response.data))}>{item.repositoryUrl.replace('https://github.com/', '')} #{item.prNumber}<span>{item.status}</span></button>)}</nav>}
    {!snapshot && <form className="review-run-create" onSubmit={submit}><label>Repository URL<input required value={repositoryUrl} onChange={event => setRepositoryUrl(event.target.value)} placeholder="https://github.com/owner/repo" /></label><label>Pull request number<input required type="number" min="1" value={prNumber} onChange={event => setPrNumber(event.target.value)} placeholder="42" /></label><button disabled={busy}>{busy ? 'Queueing…' : 'Run review'}</button></form>}
    {error && <div role="alert" className="global-warning">{error}</div>}
    {snapshot && <section className="review-run-card"><div className="review-run-status"><span className={`status-dot status-dot--${snapshot.status.toLowerCase()}`} />{snapshot.status}<span className="review-run-id">{snapshot.runId.slice(0, 8)}</span></div><ol className="review-run-steps">{['INGEST','INDEX','RETRIEVE','PLAN','EXECUTE','EVIDENCE','PREVIEW'].map(step => <li key={step} className={snapshot.events.some(event => event.type.includes(step) || event.summary?.toUpperCase().includes(step)) ? 'is-done' : ''}>{step}</li>)}</ol><details className="review-event-log"><summary>Tool and run events ({snapshot.events.length})</summary>{snapshot.events.map(event => <p key={event.sequence}><code>#{event.sequence}</code> {event.type}: {event.summary || event.status}</p>)}</details><div className="review-run-result">{snapshot.status === 'FAILED' ? <><h2>Review failed</h2><p role="alert">{snapshot.errorMessage || snapshot.review.errorMessage || 'The review pipeline failed.'}</p></> : <><h2>{snapshot.review.summary || (TERMINAL.has(snapshot.status) ? 'Review complete' : 'Review is running…')}</h2>{snapshot.review.issues?.length ? <ul>{snapshot.review.issues.map(issue => <li key={issue.id}><button type="button" className="issue-link" onClick={() => void loadEvidence(issue.id)}><strong>{issue.severity}</strong> {issue.title} — <code>{issue.filePath}:{issue.startLine}</code></button>{evidence[issue.id]?.map(item => <blockquote key={item.citationLabel}>{item.path}:{item.startLine}–{item.endLine}<br />{item.excerpt}</blockquote>)}</li>)}</ul> : <p>Findings and evidence will appear here as the run advances.</p>}</>}</div>{previews.length > 0 && <div className="review-preview"><h3>Human Review</h3>{previews.map(preview => <label key={preview.id}><input type="checkbox" checked={preview.selectedForPublish} onChange={event => { const ids = previews.filter(item => item.id === preview.id ? event.target.checked : item.selectedForPublish).map(item => item.id); void updateCommentPreviewSelection(snapshot.runId, ids).then(response => response.data && setPreviews(response.data.items)); }} /> <span>{preview.filePath}:{preview.line} — {preview.draftBody}</span></label>)}<button type="button" disabled={publishing || !previews.some(item => item.selectedForPublish)} onClick={async () => { setPublishing(true); const response = await publishSelectedCommentPreviews(snapshot.runId); if (response.data) setPreviews(response.data.items); setPublishing(false); }}>{publishing ? 'Publishing…' : 'Approve and publish selected'}</button><small>Publishing is available only in self-host mode with owner credentials.</small></div>}{snapshot.status === 'FAILED' && <button type="button" onClick={() => void retry()}>Retry failed run</button>}</section>}
  </main>;
}
