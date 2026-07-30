import { useCallback, useEffect, useRef, useState } from 'react';
import type { CommentPreview, ReviewTask, ToolTraceItem } from './types/reviewTask';
import {
  getCommentPreviews,
  getHealth,
  getRepositoryIndexStatus, requestRepositoryIndex, requestRepositoryReindex, getRetrievalEvidence, getRetrievalTrace,
  getReviewTask,
  getToolTrace,
  listReviewTasks,
  publishSelectedCommentPreviews,
  updateCommentPreviewSelection,
} from './api/reviewTaskApi';
import { ReviewTaskCreateForm } from './components/ReviewTaskCreateForm';
import { ReviewTaskDetail } from './components/ReviewTaskDetail';
import { ReviewTaskList } from './components/ReviewTaskList';
import { StatusWidget } from './components/StatusWidget';
import { ThemeToggle } from './components/ThemeToggle';
import { WorkspaceToolbar } from './components/WorkspaceToolbar';
import { CollapsiblePanel } from './components/CollapsiblePanel';
import { LiveReviewStory } from './components/LiveReviewStory';
import { useColorTheme } from './hooks/useColorTheme';
import type { BackendStatus, PanelId } from './types/ui';
import type { RepositoryIndexStatus, RetrievalEvidence } from './types/reviewTask';
import { PRODUCT_LIMITS } from './types/ui';
import './styles/app.css';

type NavSection = 'workspace' | 'history';
const FULL_COMMIT_SHA = /^[0-9a-f]{40}$/;

function githubRepositoryParts(repoUrl: string): [string, string] | null {
  const match = repoUrl.match(/^https?:\/\/github\.com\/([^/]+)\/([^/#]+?)(?:\.git)?\/?$/i);
  return match ? [match[1], match[2]] : null;
}

export default function App() {
  const { theme, toggleTheme } = useColorTheme();
  const [backendStatus, setBackendStatus] = useState<BackendStatus>('checking');
  const [mimoConfigured, setMimoConfigured] = useState(false);
  const [activeNav, setActiveNav] = useState<NavSection>('workspace');
  const [expandedPanels, setExpandedPanels] = useState<Set<PanelId>>(() => new Set());
  const [showLimits, setShowLimits] = useState(false);
  const [demoMode, setDemoMode] = useState(() => import.meta.env.MODE === 'pages');

  const [tasks, setTasks] = useState<ReviewTask[]>([]);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);

  const [selectedTask, setSelectedTask] = useState<ReviewTask | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [commentPreviews, setCommentPreviews] = useState<CommentPreview[]>([]);
  const [commentPreviewLoading, setCommentPreviewLoading] = useState(false);
  const [commentPreviewError, setCommentPreviewError] = useState<string | null>(null);
  const [commentPublishing, setCommentPublishing] = useState(false);
  const [toolTraceItems, setToolTraceItems] = useState<ToolTraceItem[]>([]);
  const [toolTraceLoading, setToolTraceLoading] = useState(false);
  const [toolTraceError, setToolTraceError] = useState<string | null>(null);
  const [repositoryIndexStatus, setRepositoryIndexStatus] = useState<RepositoryIndexStatus | null>(null);
  const [repositoryIndexError, setRepositoryIndexError] = useState<string | null>(null);
  const [repositoryIndexLoading, setRepositoryIndexLoading] = useState(false);
  const [indexMutationLoading, setIndexMutationLoading] = useState(false);
  const [retrievalDegraded, setRetrievalDegraded] = useState<boolean | null>(null);
  const [indexPollTrigger, setIndexPollTrigger] = useState(0);
  const [evidenceByIssue, setEvidenceByIssue] = useState<Record<string, RetrievalEvidence[]>>({});
  const [evidenceLoadingIssue, setEvidenceLoadingIssue] = useState<string | null>(null);
  const [evidenceError, setEvidenceError] = useState<string | null>(null);
  const [evidenceLoadingByIssue, setEvidenceLoadingByIssue] = useState<Record<string, boolean>>({});
  const [evidenceErrorByIssue, setEvidenceErrorByIssue] = useState<Record<string, string | null>>({});
  const indexGeneration = useRef(0);
  const taskGeneration = useRef(0);
  const indexMutationInFlight = useRef(false);
  const evidenceInFlight = useRef(new Set<string>());
  const workspaceRef = useRef<HTMLElement>(null);

  useEffect(() => {
    getHealth()
      .then((res) => {
        if (res.success && res.data) {
          setBackendStatus('up');
          setMimoConfigured(Boolean(res.data.mimoConfigured));
        } else {
          setBackendStatus('down');
        }
      })
      .catch(() => setBackendStatus('down'));
  }, []);

  const loadTasks = useCallback(async () => {
    setListLoading(true);
    setListError(null);
    try {
      const res = await listReviewTasks();
      if (res.success && res.data) {
        setTasks(res.data);
      } else {
        setListError(res.message || 'Failed to load review tasks.');
        setTasks([]);
      }
    } catch {
      setListError(
        'Backend is unavailable. Check the configured API service.',
      );
      setTasks([]);
    } finally {
      setListLoading(false);
    }
  }, []);

  useEffect(() => {
    loadTasks();
  }, [loadTasks]);

  function togglePanel(id: PanelId) {
    setExpandedPanels((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function expandPanel(id: PanelId) {
    setExpandedPanels((prev) => new Set(prev).add(id));
  }

  function handleTaskCreated(task: ReviewTask) {
    setTasks((prev) => [task, ...prev]);
    handleSelectTask(task);
    setActiveNav('history');
    expandPanel('history');
    expandPanel('findings');
  }

  async function handleSelectTask(task: ReviewTask) {
    const generation = ++taskGeneration.current;
    setActiveNav('history');
    expandPanel('findings');
    setDetailLoading(true);
    setDetailError(null);
    setCommentPreviewError(null);
    setCommentPreviews([]);
    setCommentPreviewLoading(false);
    setToolTraceError(null);
    setToolTraceItems([]);
    setToolTraceLoading(false);
    indexMutationInFlight.current = false;
    setIndexMutationLoading(false);
    setSelectedTask(null);
    setEvidenceByIssue({}); setEvidenceError(null); setRepositoryIndexStatus(null); setRepositoryIndexError(null); setRetrievalDegraded(null);
    setEvidenceLoadingByIssue({}); setEvidenceErrorByIssue({});
    try {
      const res = await getReviewTask(task.id);
      if (generation !== taskGeneration.current) return;
      if (res.success && res.data) {
        setSelectedTask(res.data);
        if (res.data.latestRunId) {
          await Promise.all([
            loadCommentPreviews(res.data.latestRunId, generation),
            loadToolTrace(res.data.latestRunId, generation),
            loadRetrievalState(res.data.latestRunId, generation),
          ]);
        }
      } else {
        setDetailError(res.message || 'Task not found.');
      }
    } catch {
      if (generation !== taskGeneration.current) return;
      setDetailError(
        'Backend is unavailable. Check the configured API service.',
      );
    } finally {
      if (generation === taskGeneration.current) setDetailLoading(false);
    }
  }

  useEffect(() => {
    const current = selectedTask;
    if (!current) return;
    const repository = githubRepositoryParts(current.repoUrl);
    const sha = current.ingestionSummary?.headSha;
    if (!repository || !sha || !FULL_COMMIT_SHA.test(sha)) return;
    const generation = ++indexGeneration.current;
    let stopped = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const poll = async () => {
      if (stopped) return;
      setRepositoryIndexLoading(true);
      setRepositoryIndexError(null);
      try {
        const res = await getRepositoryIndexStatus(repository[0], repository[1], sha);
        if (stopped || generation !== indexGeneration.current) return;
        if (res.success && res.data) {
          setRepositoryIndexStatus(res.data);
          if (res.data.status === 'QUEUED' || res.data.status === 'RUNNING') timer = setTimeout(poll, 2000);
        } else {
          setRepositoryIndexStatus(null);
          setRepositoryIndexError('Unable to load index status.');
        }
      } catch {
        if (!stopped && generation === indexGeneration.current) {
          setRepositoryIndexStatus(null);
          setRepositoryIndexError('Unable to load index status.');
        }
      } finally {
        if (!stopped && generation === indexGeneration.current) setRepositoryIndexLoading(false);
      }
    };
    poll();
    return () => { stopped = true; if (timer) clearTimeout(timer); indexGeneration.current++; };
  }, [selectedTask?.id, selectedTask?.repoUrl, selectedTask?.ingestionSummary?.headSha, indexPollTrigger]);

  async function handleRequestRepositoryIndex() {
    if (!selectedTask || indexMutationInFlight.current) return;
    const generation = taskGeneration.current;
    const sha = selectedTask.ingestionSummary?.headSha;
    if (!sha || !FULL_COMMIT_SHA.test(sha)) { setRepositoryIndexStatus({ status: 'FAILED', errorMessage: 'Repository head SHA is unavailable.' }); return; }
    if (!githubRepositoryParts(selectedTask.repoUrl)) { setRepositoryIndexStatus({ status: 'FAILED', errorMessage: 'Unsupported repository URL' }); return; }
    indexMutationInFlight.current = true;
    setIndexMutationLoading(true);
    setRepositoryIndexError(null);
    setRepositoryIndexStatus({ status: 'QUEUED', commitSha: sha });
    try {
      const res = await requestRepositoryIndex(selectedTask.repoUrl, sha);
      if (generation !== taskGeneration.current) return;
      if (res.success && res.data) { setRepositoryIndexStatus({ status: res.data.status, commitSha: sha }); setIndexPollTrigger((value) => value + 1); }
      else if (res.httpStatus === 409) { setRepositoryIndexStatus({ status: 'QUEUED', commitSha: sha }); setIndexPollTrigger((value) => value + 1); }
      else setRepositoryIndexStatus({ status: 'FAILED', errorMessage: res.message || 'Index request failed.' });
    } catch { if (generation === taskGeneration.current) setRepositoryIndexStatus({ status: 'FAILED', errorMessage: 'Index request failed.' }); }
    finally {
      if (generation === taskGeneration.current) {
        indexMutationInFlight.current = false;
        setIndexMutationLoading(false);
      }
    }
  }
  async function handleRequestRepositoryReindex() {
    if (!selectedTask || indexMutationInFlight.current) return;
    const generation = taskGeneration.current;
    const repository = githubRepositoryParts(selectedTask.repoUrl);
    const sha = selectedTask.ingestionSummary?.headSha;
    if (!sha || !FULL_COMMIT_SHA.test(sha)) { setRepositoryIndexStatus({ status: 'FAILED', errorMessage: 'Repository head SHA is unavailable.' }); return; }
    if (!repository) { setRepositoryIndexStatus({ status: 'FAILED', errorMessage: 'Unsupported repository URL' }); return; }
    indexMutationInFlight.current = true;
    setIndexMutationLoading(true);
    setRepositoryIndexError(null);
    setRepositoryIndexStatus({ status: 'QUEUED', commitSha: sha });
    try {
      const res = await requestRepositoryReindex(repository[0], repository[1], sha);
      if (generation !== taskGeneration.current) return;
      if (res.success && res.data) { setRepositoryIndexStatus({ status: res.data.status, commitSha: sha }); setIndexPollTrigger((value) => value + 1); }
      else if (res.httpStatus === 409) { setRepositoryIndexStatus({ status: 'QUEUED', commitSha: sha }); setIndexPollTrigger((value) => value + 1); }
      else setRepositoryIndexStatus({ status: 'FAILED', errorMessage: res.message || 'Reindex request failed.' });
    } catch { if (generation === taskGeneration.current) setRepositoryIndexStatus({ status: 'FAILED', errorMessage: 'Reindex request failed.' }); }
    finally {
      if (generation === taskGeneration.current) {
        indexMutationInFlight.current = false;
        setIndexMutationLoading(false);
      }
    }
  }

  async function loadRetrievalState(runId: number, generation: number) {
    try {
      const res = await getRetrievalTrace(runId);
      if (generation !== taskGeneration.current) return;
      setRetrievalDegraded(res.success && res.data ? Boolean(res.data.degraded) : null);
    } catch {
      if (generation === taskGeneration.current) setRetrievalDegraded(null);
    }
  }

  async function handleIssueEvidence(issueId: string) {
    if (!selectedTask) return;
    const evidenceKey = `${selectedTask.id}:${issueId}`;
    if (evidenceByIssue[issueId] || evidenceInFlight.current.has(evidenceKey)) return;
    const generation = taskGeneration.current;
    evidenceInFlight.current.add(evidenceKey);
    setEvidenceLoadingIssue(issueId); setEvidenceLoadingByIssue((p) => ({ ...p, [issueId]: true })); setEvidenceErrorByIssue((p) => ({ ...p, [issueId]: null }));
    try { const res = await getRetrievalEvidence(selectedTask.id, issueId); if (generation !== taskGeneration.current) return; if (res.success && res.data) setEvidenceByIssue((prev) => ({ ...prev, [issueId]: res.data! })); else setEvidenceErrorByIssue((p) => ({ ...p, [issueId]: res.message || 'Evidence unavailable.' })); }
    catch { if (generation === taskGeneration.current) setEvidenceErrorByIssue((p) => ({ ...p, [issueId]: 'Evidence unavailable.' })); }
    finally { evidenceInFlight.current.delete(evidenceKey); if (generation === taskGeneration.current) { setEvidenceLoadingIssue(null); setEvidenceLoadingByIssue((p) => ({ ...p, [issueId]: false })); } }
  }

  async function loadToolTrace(runId: number, generation: number) {
    if (generation !== taskGeneration.current) return;
    setToolTraceLoading(true);
    setToolTraceError(null);
    try {
      const res = await getToolTrace(runId);
      if (generation !== taskGeneration.current) return;
      if (res.success && res.data) {
        setToolTraceItems(res.data.items);
      } else {
        setToolTraceError(res.message || 'Failed to load agent trace.');
        setToolTraceItems([]);
      }
    } catch {
      if (generation === taskGeneration.current) {
        setToolTraceError('Backend is unavailable. Agent trace could not be loaded.');
        setToolTraceItems([]);
      }
    } finally {
      if (generation === taskGeneration.current) setToolTraceLoading(false);
    }
  }

  async function loadCommentPreviews(runId: number, generation: number) {
    if (generation !== taskGeneration.current) return;
    setCommentPreviewLoading(true);
    setCommentPreviewError(null);
    try {
      const res = await getCommentPreviews(runId);
      if (generation !== taskGeneration.current) return;
      if (res.success && res.data) {
        setCommentPreviews(res.data.items);
      } else {
        setCommentPreviewError(res.message || 'Failed to load comment previews.');
        setCommentPreviews([]);
      }
    } catch {
      if (generation === taskGeneration.current) {
        setCommentPreviewError('Backend is unavailable. Comment previews could not be loaded.');
        setCommentPreviews([]);
      }
    } finally {
      if (generation === taskGeneration.current) setCommentPreviewLoading(false);
    }
  }

  async function handleCommentPreviewSelection(previewId: number, selected: boolean) {
    if (!selectedTask?.latestRunId) return;
    const selectedPreviewIds = commentPreviews
      .filter((preview) => (preview.id === previewId ? selected : preview.selectedForPublish))
      .map((preview) => preview.id);
    setCommentPreviewError(null);
    try {
      const res = await updateCommentPreviewSelection(selectedTask.latestRunId, selectedPreviewIds);
      if (res.success && res.data) {
        setCommentPreviews(res.data.items);
      } else {
        setCommentPreviewError(res.message || 'Failed to update selected comments.');
      }
    } catch {
      setCommentPreviewError('Backend is unavailable. Selected comments could not be updated.');
    }
  }

  async function handlePublishSelectedCommentPreviews() {
    if (!selectedTask?.latestRunId) return;
    const confirmed = window.confirm('Publish selected comments to GitHub?');
    if (!confirmed) return;
    setCommentPublishing(true);
    setCommentPreviewError(null);
    try {
      const res = await publishSelectedCommentPreviews(selectedTask.latestRunId);
      if (res.success && res.data) {
        setCommentPreviews(res.data.items);
      } else {
        setCommentPreviewError(res.message || 'Failed to publish selected comments.');
      }
    } catch {
      setCommentPreviewError('Backend is unavailable. Selected comments could not be published.');
    } finally {
      setCommentPublishing(false);
    }
  }

  function scrollToPanel(panel: PanelId) {
    if (panel === 'review') {
      setActiveNav('workspace');
    } else if (panel === 'history') {
      setActiveNav('history');
    }
    expandPanel(panel);

    const sectionId =
      panel === 'review'
        ? 'section-workspace'
        : panel === 'history'
          ? 'section-history'
          : 'section-findings';

    requestAnimationFrame(() => {
      const container = workspaceRef.current;
      const section = document.getElementById(sectionId);
      if (!section) return;

      if (container) {
        const containerTop = container.getBoundingClientRect().top;
        const sectionTop = section.getBoundingClientRect().top;
        const nextTop = container.scrollTop + (sectionTop - containerTop) - 12;
        container.scrollTo({ top: Math.max(nextTop, 0), behavior: 'smooth' });
      } else {
        section.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    });
  }

  function scrollToSection(section: NavSection) {
    if (section === 'workspace') {
      scrollToPanel('review');
      return;
    }
    scrollToPanel('history');
  }

  const findingsSummary = selectedTask
    ? `Review #${selectedTask.id}${selectedTask.riskLevel ? ` · ${selectedTask.riskLevel}` : ''}`
    : 'Select a review from history';

  const historySummary =
    tasks.length === 0
      ? 'No reviews yet'
      : `${tasks.length} review${tasks.length === 1 ? '' : 's'}`;

  return (
    <div className={`app-root${demoMode ? ' app-root--demo' : ''}`}>
      <div className="app-vibrancy-bg" aria-hidden="true">
        <div className="vibrancy-mesh" />
        <div className="vibrancy-noise" />
      </div>

      <div className="app-shell">
        <aside className="sidebar" aria-label="Product navigation">
          <div className="sidebar-brand">
            <div className="sidebar-logo" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" width="22" height="22">
                <rect x="3" y="3" width="18" height="18" rx="5" stroke="currentColor" strokeWidth="1.5" />
                <path d="M8 12l3 3 5-6" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </div>
            <div>
              <p className="sidebar-product-name">CodeReviewX</p>
              <p className="sidebar-product-tag">Manual Diff-Grounded AI Code Review Agent MVP</p>
            </div>
          </div>

          <nav className="sidebar-nav" aria-label="Main navigation">
            <button
              type="button"
              className={`sidebar-nav-item${activeNav === 'workspace' ? ' sidebar-nav-item--active' : ''}`}
              onClick={() => scrollToSection('workspace')}
              aria-current={activeNav === 'workspace' ? 'page' : undefined}
            >
              <span className="sidebar-nav-icon" aria-hidden="true">◈</span>
              Review Agent
            </button>
            <button
              type="button"
              className={`sidebar-nav-item${activeNav === 'history' ? ' sidebar-nav-item--active' : ''}`}
              onClick={() => scrollToSection('history')}
              aria-current={activeNav === 'history' ? 'page' : undefined}
            >
              <span className="sidebar-nav-icon" aria-hidden="true">☰</span>
              Review History
              {tasks.length > 0 && (
                <span className="sidebar-nav-badge">{tasks.length}</span>
              )}
            </button>
          </nav>

          <div className="sidebar-footer">
            <StatusWidget
              backendStatus={backendStatus}
              tasks={tasks}
              mimoConfigured={mimoConfigured}
              demoMode={demoMode}
            />

            <CollapsiblePanel
              panelId="panel-about"
              title="About & limits"
              summary="Scope and constraints"
              expanded={showLimits}
              onToggle={() => setShowLimits((v) => !v)}
              compact
            >
              <ul className="limitations-list">
                {PRODUCT_LIMITS.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </CollapsiblePanel>
          </div>
        </aside>

        <div className="main-column">
          <header className="window-chrome">
            <div className="window-chrome-leading">
              <div className="window-traffic-lights" aria-hidden="true">
                <span className="traffic-light traffic-light--close" />
                <span className="traffic-light traffic-light--minimize" />
                <span className="traffic-light traffic-light--maximize" />
              </div>
            </div>
            <div className="window-chrome-trailing">
              {!demoMode && (
                <button type="button" className="demo-launch-button" onClick={() => setDemoMode(true)}>
                  <span className="demo-launch-dot" aria-hidden="true" />
                  Start live demo
                </button>
              )}
              <ThemeToggle theme={theme} onToggle={toggleTheme} />
            </div>
          </header>

          {demoMode ? (
            <main className="workspace workspace--story">
              <LiveReviewStory />
            </main>
          ) : (
            <main className="workspace" ref={workspaceRef}>
              <WorkspaceToolbar
                backendStatus={backendStatus}
                tasksCount={tasks.length}
                findingsLabel={findingsSummary}
                expandedPanels={expandedPanels}
                onNavigatePanel={scrollToPanel}
              />

            {backendStatus === 'down' && (
              <div className="global-warning" role="alert">
                Backend is unavailable. Check the configured API service.
              </div>
            )}

            <div className="workspace-stack">
              <section id="section-workspace" className="workspace-section">
                <ReviewTaskCreateForm
                  expanded={expandedPanels.has('review')}
                  onToggle={() => togglePanel('review')}
                  onCreated={handleTaskCreated}
                  backendAvailable={backendStatus === 'up'}
                  mimoConfigured={mimoConfigured}
                />
              </section>

              <section id="section-history" className="workspace-section">
                <ReviewTaskList
                  expanded={expandedPanels.has('history')}
                  onToggle={() => togglePanel('history')}
                  summary={historySummary}
                  tasks={tasks}
                  loading={listLoading}
                  error={listError}
                  selectedId={selectedTask?.id ?? null}
                  onSelect={handleSelectTask}
                />
              </section>

              <section id="section-findings" className="workspace-section">
                <ReviewTaskDetail
                  expanded={expandedPanels.has('findings')}
                  onToggle={() => togglePanel('findings')}
                  summary={findingsSummary}
                  task={selectedTask}
                  loading={detailLoading}
                  error={detailError}
                  commentPreviews={commentPreviews}
                  commentPreviewLoading={commentPreviewLoading}
                  commentPreviewError={commentPreviewError}
                  commentPublishing={commentPublishing}
                  toolTraceItems={toolTraceItems}
                  toolTraceLoading={toolTraceLoading}
                  toolTraceError={toolTraceError}
                  onCommentPreviewSelectionChange={handleCommentPreviewSelection}
                  onPublishSelectedCommentPreviews={handlePublishSelectedCommentPreviews}
                  repositoryIndexStatus={repositoryIndexStatus}
                  retrievalDegraded={retrievalDegraded}
                  indexActionsDisabled={
                    !FULL_COMMIT_SHA.test(selectedTask?.ingestionSummary?.headSha ?? '')
                    || (repositoryIndexStatus === null && repositoryIndexError === null)
                    || repositoryIndexLoading
                    || indexMutationLoading
                  }
                  indexActionError={
                    selectedTask && !FULL_COMMIT_SHA.test(selectedTask.ingestionSummary?.headSha ?? '')
                      ? 'Repository head SHA is unavailable.'
                      : repositoryIndexError
                  }
                  onRequestRepositoryIndex={handleRequestRepositoryIndex}
                  onRequestRepositoryReindex={handleRequestRepositoryReindex}
                  evidenceByIssue={evidenceByIssue}
                  evidenceLoadingIssue={evidenceLoadingIssue}
                  evidenceError={evidenceError}
                  evidenceLoadingByIssue={evidenceLoadingByIssue}
                  evidenceErrorByIssue={evidenceErrorByIssue}
                  onIssueEvidenceRequest={handleIssueEvidence}
                />
              </section>
            </div>
            </main>
          )}
        </div>
      </div>
    </div>
  );
}
