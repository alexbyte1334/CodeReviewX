import { useEffect, useRef, useState } from 'react';
import { createReview, getReviewSnapshot, reviewEventsUrl, retryReview, getCommentPreviews, updateCommentPreviewSelection, publishSelectedCommentPreviews, type ReviewApiSnapshot } from '../api/reviewTaskApi';
import type { CommentPreview } from '../types/reviewTask';

const TERMINAL = new Set(['SUCCEEDED', 'FAILED', 'SUCCESS']);

export function ReviewRunWorkspace() {
  const [repositoryUrl, setRepositoryUrl] = useState('');
  const [prNumber, setPrNumber] = useState('');
  const [snapshot, setSnapshot] = useState<ReviewApiSnapshot | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [previews, setPreviews] = useState<CommentPreview[]>([]);
  const [publishing, setPublishing] = useState(false);
  const lastSequence = useRef(0);

  useEffect(() => {
    const match = window.location.hash.match(/^#\/reviews\/([^/?]+)/);
    if (!match) return;
    void getReviewSnapshot(match[1]).then((response) => { if (response.data) setSnapshot(response.data); });
  }, []);

  useEffect(() => {
    if (!snapshot || TERMINAL.has(snapshot.status)) return;
    let disposed = false;
    let source: EventSource | null = null;
    let timer: number | undefined;
    let delay = 1000;
    const connect = () => {
      source = new EventSource(reviewEventsUrl(snapshot.runId, lastSequence.current));
      source.onmessage = () => { delay = 1000; void getReviewSnapshot(snapshot.runId).then((r) => { if (!disposed && r.data) { setSnapshot(r.data); lastSequence.current = Math.max(lastSequence.current, ...r.data.events.map((e) => e.sequence)); } }); };
      source.addEventListener('stream-complete', () => { source?.close(); void getReviewSnapshot(snapshot.runId).then((r) => r.data && setSnapshot(r.data)); });
      source.onerror = () => { source?.close(); if (!disposed) { timer = window.setTimeout(connect, delay); delay = Math.min(delay * 2, 15000); } };
    };
    connect();
    return () => { disposed = true; source?.close(); if (timer) window.clearTimeout(timer); };
  }, [snapshot?.runId, snapshot?.status]);

  useEffect(() => {
    if (!snapshot || !TERMINAL.has(snapshot.status)) return;
    void getCommentPreviews(snapshot.reviewRunId).then((response) => { if (response.data) setPreviews(response.data.items); });
  }, [snapshot?.runId, snapshot?.status, snapshot?.reviewRunId]);

  async function submit(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError(null);
    try {
      const result = await createReview({ repositoryUrl, prNumber: Number(prNumber) }, crypto.randomUUID());
      if (!result.data) throw new Error(result.message || 'Unable to create review');
      setSnapshot(result.data); lastSequence.current = 0;
      window.history.replaceState(null, '', `#/reviews/${result.data.runId}`);
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to create review'); }
    finally { setBusy(false); }
  }

  async function retry() { if (!snapshot) return; const result = await retryReview(snapshot.runId); if (result.data) setSnapshot(result.data); }

  return <main className="review-run-workspace" aria-live="polite">
    <header className="review-run-header"><div><p className="eyebrow">REVIEW WORKSPACE</p><h1>Review a real GitHub pull request</h1><p>Queued execution, recoverable events, evidence-backed findings, and human approval.</p></div></header>
    {!snapshot && <form className="review-run-create" onSubmit={submit}><label>Repository URL<input required value={repositoryUrl} onChange={(e) => setRepositoryUrl(e.target.value)} placeholder="https://github.com/owner/repo" /></label><label>Pull request number<input required type="number" min="1" value={prNumber} onChange={(e) => setPrNumber(e.target.value)} placeholder="42" /></label><button disabled={busy}>{busy ? 'Queueing…' : 'Run review'}</button></form>}
    {error && <div role="alert" className="global-warning">{error}</div>}
    {snapshot && <section className="review-run-card"><div className="review-run-status"><span className={`status-dot status-dot--${snapshot.status.toLowerCase()}`} />{snapshot.status}<span className="review-run-id">{snapshot.runId.slice(0, 8)}</span></div><ol className="review-run-steps">{['INGEST','INDEX','RETRIEVE','PLAN','EXECUTE','EVIDENCE','PREVIEW'].map((step) => <li key={step} className={snapshot.events.some((e) => e.summary?.toUpperCase().includes(step)) ? 'is-done' : ''}>{step}</li>)}</ol><div className="review-run-result"><h2>{snapshot.review.summary || 'Review is running…'}</h2>{snapshot.review.issues?.length ? <ul>{snapshot.review.issues.map((issue) => <li key={issue.id}><strong>{issue.severity}</strong> {issue.title} — <code>{issue.filePath}:{issue.startLine}</code></li>)}</ul> : <p>Findings and evidence will appear here as the run advances.</p>}</div>{previews.length > 0 && <div className="review-preview"><h3>Human Review</h3>{previews.map((preview) => <label key={preview.id}><input type="checkbox" checked={preview.selectedForPublish} onChange={(e) => { const ids = previews.filter((item) => item.id === preview.id ? e.target.checked : item.selectedForPublish).map((item) => item.id); void updateCommentPreviewSelection(snapshot.reviewRunId, ids).then((r) => r.data && setPreviews(r.data.items)); }} /> <span>{preview.filePath}:{preview.line} — {preview.draftBody}</span></label>)}<button type="button" disabled={publishing || !previews.some((item) => item.selectedForPublish)} onClick={async () => { setPublishing(true); const r = await publishSelectedCommentPreviews(snapshot.reviewRunId); if (r.data) setPreviews(r.data.items); setPublishing(false); }}>{publishing ? 'Publishing…' : 'Approve and publish selected'}</button><small>Publishing is available only in self-host mode with owner credentials.</small></div>}{snapshot.status === 'FAILED' && <button type="button" onClick={retry}>Retry failed run</button>}</section>}
  </main>;
}
