import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  createDemoRun,
  decideDemoRun,
  DEMO_API_BASE,
  getDemoSnapshot,
  loadRecordedRun,
} from '../api/demoRunApi';
import type { DemoSnapshot } from '../types/demoRun';

const TERMINAL = new Set(['READY', 'FAILED']);

interface DiffLine {
  number: string;
  code: string;
  kind: 'context' | 'add' | 'remove';
}

function visibleDiff(diff: string): DiffLine[] {
  let nextLine = 0;
  let oldLine = 0;
  const lines: DiffLine[] = [];
  for (const raw of diff.split('\n')) {
    const hunk = raw.match(/^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@/);
    if (hunk) {
      oldLine = Number(hunk[1]);
      nextLine = Number(hunk[2]);
      continue;
    }
    if (/^(diff --git|index |--- |\+\+\+ )/.test(raw)) continue;
    if (raw.startsWith('+')) {
      lines.push({ number: String(nextLine++), code: raw.slice(1), kind: 'add' });
    } else if (raw.startsWith('-')) {
      lines.push({ number: `−${oldLine++}`, code: raw.slice(1), kind: 'remove' });
    } else {
      lines.push({ number: String(nextLine++), code: raw.startsWith(' ') ? raw.slice(1) : raw, kind: 'context' });
      oldLine++;
    }
  }
  return lines.slice(0, 80);
}

function formatDuration(value: number | null) {
  if (value == null) return '—';
  return value >= 1000 ? `${(value / 1000).toFixed(1)}s` : `${value}ms`;
}

export function LiveReviewStory() {
  const [snapshot, setSnapshot] = useState<DemoSnapshot | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [openEvidence, setOpenEvidence] = useState<Set<number>>(() => new Set([1, 2]));
  const [showComments, setShowComments] = useState(false);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const lastEvent = useRef(0);

  const useReplay = useCallback(async (reason?: string) => {
    const recorded = await loadRecordedRun();
    setSnapshot({ ...recorded, replayReason: reason || recorded.replayReason });
    setSelected(new Set(recorded.commentPreviews.filter((item) => item.selected).map((item) => item.id)));
  }, []);

  const refresh = useCallback(async (runId: string) => {
    const latest = await getDemoSnapshot(runId);
    setSnapshot(latest);
    const sequence = latest.events.length > 0
      ? latest.events[latest.events.length - 1].sequence
      : 0;
    lastEvent.current = Math.max(lastEvent.current, sequence);
    setSelected(new Set(latest.commentPreviews.filter((item) => item.selected).map((item) => item.id)));
    return latest;
  }, []);

  useEffect(() => {
    const runId = new URLSearchParams(window.location.search).get('runId');
    if (!runId) {
      void useReplay();
      return;
    }
    let disposed = false;
    let source: EventSource | null = null;
    let retryTimer: number | undefined;
    let retry = 1000;

    const connect = async () => {
      try {
        const latest = await refresh(runId);
        if (disposed || TERMINAL.has(latest.status)) return;
        source = new EventSource(
          `${DEMO_API_BASE}/api/demo-runs/${encodeURIComponent(runId)}/events?afterSequence=${lastEvent.current}`,
        );
        source.onmessage = () => {
          retry = 1000;
          void refresh(runId);
        };
        source.addEventListener('stream-complete', () => {
          source?.close();
          void refresh(runId);
        });
        source.onerror = () => {
          source?.close();
          if (!disposed) {
            retryTimer = window.setTimeout(connect, retry);
            retry = Math.min(retry * 2, 15_000);
          }
        };
      } catch (error) {
        if (!disposed) {
          const message = error instanceof Error ? error.message : 'Live API unavailable';
          setNotice(`Live execution could not be restored: ${message}`);
          await useReplay(message);
        }
      }
    };
    void connect();
    return () => {
      disposed = true;
      source?.close();
      if (retryTimer) window.clearTimeout(retryTimer);
    };
  }, [refresh, useReplay]);

  const runLive = async () => {
    setBusy(true);
    setNotice(null);
    try {
      const created = await createDemoRun();
      const url = new URL(window.location.href);
      url.searchParams.set('runId', created.runId);
      window.history.replaceState(null, '', url);
      await refresh(created.runId);
      window.location.reload();
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Live API unavailable';
      setNotice(`Replay Mode: ${message}`);
      await useReplay(message);
    } finally {
      setBusy(false);
    }
  };

  const diffLines = useMemo(() => visibleDiff(snapshot?.diffText ?? ''), [snapshot?.diffText]);
  const activeStep = useMemo(() => {
    if (!snapshot) return 0;
    let index = -1;
    snapshot.steps.forEach((step, stepIndex) => {
      if (step.status !== 'PENDING') index = stepIndex;
    });
    return Math.max(0, index);
  }, [snapshot]);
  const progress = snapshot ? `${(activeStep / Math.max(snapshot.steps.length - 1, 1)) * 100}%` : '0%';
  const primaryFinding = snapshot?.findings[0];
  const highRisk = snapshot?.findings.filter((item) => item.severity === 'HIGH').length ?? 0;

  const approve = async () => {
    if (!snapshot || selected.size === 0) return;
    if (snapshot.mode === 'LIVE') {
      try {
        const updated = await decideDemoRun(snapshot.runId, 'APPROVE_PREVIEW', [...selected]);
        setSnapshot(updated);
        setNotice('Preview approved. GitHub publishing is owner-controlled.');
      } catch (error) {
        setNotice(error instanceof Error ? error.message : 'Could not save the decision.');
        return;
      }
    } else {
      setSnapshot({ ...snapshot, decision: 'APPROVE_PREVIEW' });
      setNotice('Replay decision recorded locally. GitHub publishing is owner-controlled.');
    }
    setShowComments(false);
  };

  if (!snapshot) {
    return <section className="story"><div className="story-note">Loading trusted demo snapshot…</div></section>;
  }

  return (
    <section className="story" aria-label="Trusted AI review demo">
      <header className="story-header">
        <div>
          <div className={`story-mode-badge story-mode-badge--${snapshot.mode.toLowerCase()}`}>
            {snapshot.mode === 'LIVE' ? `LIVE · ${snapshot.status}` : 'REPLAY MODE'}
          </div>
          <h1>{snapshot.mode === 'LIVE' ? 'Live Review Run' : 'Recorded Review Story'}</h1>
          <p>Real evidence, recoverable state, and an owner-controlled GitHub boundary.</p>
        </div>
        <div className="story-controls">
          <button type="button" className="story-button story-button--icon" onClick={runLive} disabled={busy}>
            {busy ? 'Starting…' : 'Run live review'}
          </button>
          {snapshot.mode === 'LIVE' && <span className="story-run-id">Run {snapshot.runId.slice(0, 8)}</span>}
        </div>
      </header>

      {(notice || snapshot.replayReason) && (
        <div className={`story-mode-notice${snapshot.mode === 'REPLAY' ? ' story-mode-notice--replay' : ''}`} role="status">
          <strong>{snapshot.mode === 'REPLAY' ? 'Replay is explicit.' : 'Run update.'}</strong>
          <span>{notice || snapshot.replayReason}</span>
        </div>
      )}

      <div className="story-pipeline" aria-label="Review pipeline">
        <div className="story-progress-track" aria-hidden="true"><span className="story-progress-fill" style={{ width: progress }} /></div>
        <div className="story-steps">
          {snapshot.steps.map((step, index) => {
            const complete = ['SUCCESS', 'READY', 'PUBLISHED'].includes(step.status);
            const state = complete ? 'complete' : index === activeStep ? 'active' : 'pending';
            return (
              <div key={step.id} className={`story-step story-step--${state}`}>
                <span className="story-step-number">{complete ? '✓' : index + 1}</span>
                <span className="story-step-copy"><strong>{step.label}</strong><small>{step.status}</small></span>
                <span className="story-step-duration">{formatDuration(step.durationMs)}</span>
              </div>
            );
          })}
        </div>
      </div>

      <div className="story-note" role="status">
        <span className="story-note-mark" aria-hidden="true">i</span>
        <span>{snapshot.steps[activeStep]?.summary || 'Waiting for the next durable event.'}</span>
      </div>

      <div className="story-stage">
        <section className="story-code" aria-label="Pull request code diff">
          <header className="story-panel-header">
            <div><strong>Diff:</strong> <span>{primaryFinding?.filePath || 'Pinned public pull request'}</span></div>
            <div className="story-diff-count"><span>real</span><span>bounded</span></div>
          </header>
          <div className="story-code-body" role="table">
            {diffLines.map((line, index) => {
              const finding = primaryFinding && line.number === String(primaryFinding.line);
              return (
                <div key={`${line.number}-${index}`} className={`story-code-line${finding ? ' story-code-line--finding' : ''}`} role="row">
                  <span className="story-code-number" role="cell">{line.number}</span>
                  <code role="cell">{line.kind === 'add' ? '+ ' : line.kind === 'remove' ? '− ' : '  '}{line.code || ' '}</code>
                  {finding && <span className="story-finding-pin">{primaryFinding.title}</span>}
                </div>
              );
            })}
          </div>
          <footer className="story-code-footer">
            <span>{snapshot.findings.length} evidence-gated finding(s)</span>
            {primaryFinding && <><span className="story-risk">{primaryFinding.severity}</span><span>{primaryFinding.category}</span></>}
          </footer>
        </section>

        <section className="story-evidence" aria-label="Retrieval evidence inspector">
          <header className="story-panel-header story-panel-header--evidence">
            <div><span className="story-shield" aria-hidden="true">✓</span><strong>Why this finding is trusted</strong></div>
            <span>Evidence · {snapshot.evidence.length}</span>
          </header>
          <div className="story-evidence-list">
            {snapshot.evidence.map((item) => {
              const open = openEvidence.has(item.rank);
              const score = Math.round((item.score > 1 ? item.score / 100 : item.score) * 100);
              return (
                <article className={`story-evidence-item${open ? ' story-evidence-item--open' : ''}`} key={`${item.issueKey}-${item.rank}`}>
                  <button type="button" className="story-evidence-trigger" onClick={() => setOpenEvidence((current) => {
                    const next = new Set(current); if (next.has(item.rank)) next.delete(item.rank); else next.add(item.rank); return next;
                  })} aria-expanded={open}>
                    <span className="story-evidence-rank">{item.rank}</span>
                    <span className="story-evidence-title"><strong>{item.path}</strong><small>Lines {item.startLine}–{item.endLine}</small></span>
                    <span className="story-score"><span>Relevance {score / 100}</span><i><b style={{ width: `${score}%` }} /></i></span>
                    <span className={`story-caret${open ? ' story-caret--open' : ''}`} />
                  </button>
                  {open && <div className="story-evidence-body"><pre>{item.excerpt}</pre><p><strong>Citation:</strong> {item.citationLabel}</p><footer><span>Grounded repository context</span><span className="story-verified">✓ Verified</span></footer></div>}
                </article>
              );
            })}
            <details className="story-trace">
              <summary>Safe tool trace · {snapshot.toolTrace.length} events</summary>
              {snapshot.toolTrace.map((trace) => (
                <div key={trace.sequence}><strong>{trace.toolName}</strong><span>{trace.outputSummary || trace.errorCode || trace.status}</span><small>{formatDuration(trace.durationMs)}</small></div>
              ))}
            </details>
          </div>
        </section>
      </div>

      <footer className="story-results">
        <div className="story-result"><strong>{snapshot.findings.length}</strong><span>findings</span></div>
        <div className="story-result story-result--danger"><strong>{highRisk}</strong><span>high risk</span></div>
        <div className="story-result story-result--accent"><strong>{snapshot.evidence.length}</strong><span>evidence items</span></div>
        <div className="story-ready"><span className="story-ready-mark">✓</span><span>{snapshot.status === 'READY' ? 'Ready for human review' : snapshot.status}</span></div>
        <button type="button" className="story-primary" onClick={() => setShowComments(true)} disabled={snapshot.commentPreviews.length === 0}>
          Review comments <span aria-hidden="true">→</span>
        </button>
      </footer>

      {showComments && (
        <div className="story-drawer" role="dialog" aria-modal="true" aria-labelledby="story-comments-title">
          <button type="button" className="story-drawer-backdrop" onClick={() => setShowComments(false)} aria-label="Close comments" />
          <div className="story-drawer-panel">
            <header><div><h2 id="story-comments-title">Human review</h2><p>Approval changes Demo state only. GitHub publishing is owner-controlled.</p></div><button type="button" onClick={() => setShowComments(false)}>Close</button></header>
            {snapshot.commentPreviews.map((preview) => (
              <label className="story-comment" key={preview.id}>
                <input type="checkbox" checked={selected.has(preview.id)} onChange={() => setSelected((current) => {
                  const next = new Set(current); if (next.has(preview.id)) next.delete(preview.id); else next.add(preview.id); return next;
                })} />
                <span><strong>{preview.filePath}:{preview.line}</strong><small>{preview.severity} · {preview.category}</small><p>{preview.body}</p></span>
              </label>
            ))}
            <button type="button" className="story-primary story-primary--publish" onClick={approve} disabled={selected.size === 0}>
              Approve {selected.size} preview{selected.size === 1 ? '' : 's'}
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
