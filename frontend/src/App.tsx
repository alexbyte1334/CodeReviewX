import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReviewTask } from './types/reviewTask';
import { getHealth, getReviewTask, listReviewTasks } from './api/reviewTaskApi';
import { ReviewTaskCreateForm } from './components/ReviewTaskCreateForm';
import { ReviewTaskDetail } from './components/ReviewTaskDetail';
import { ReviewTaskList } from './components/ReviewTaskList';
import { StatusWidget } from './components/StatusWidget';
import { ThemeToggle } from './components/ThemeToggle';
import { WorkspaceToolbar } from './components/WorkspaceToolbar';
import { CollapsiblePanel } from './components/CollapsiblePanel';
import { useColorTheme } from './hooks/useColorTheme';
import type { BackendStatus, PanelId } from './types/ui';
import type { ReviewProviderChoice } from './types/reviewTask';
import { PRODUCT_LIMITS } from './types/ui';
import './styles/app.css';

type NavSection = 'workspace' | 'history';

export default function App() {
  const { theme, toggleTheme } = useColorTheme();
  const [backendStatus, setBackendStatus] = useState<BackendStatus>('checking');
  const [mimoConfigured, setMimoConfigured] = useState(false);
  const [defaultReviewProvider, setDefaultReviewProvider] = useState<ReviewProviderChoice>('mock');
  const [activeNav, setActiveNav] = useState<NavSection>('workspace');
  const [expandedPanels, setExpandedPanels] = useState<Set<PanelId>>(() => new Set());
  const [showLimits, setShowLimits] = useState(false);

  const [tasks, setTasks] = useState<ReviewTask[]>([]);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);

  const [selectedTask, setSelectedTask] = useState<ReviewTask | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const workspaceRef = useRef<HTMLElement>(null);

  useEffect(() => {
    getHealth()
      .then((res) => {
        if (res.success && res.data) {
          setBackendStatus('up');
          setMimoConfigured(Boolean(res.data.mimoConfigured));
          const serverDefault = (res.data.defaultReviewProvider ?? 'mock').toLowerCase();
          setDefaultReviewProvider(serverDefault === 'mimo' ? 'mimo' : 'mock');
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
        'Backend is unavailable. Check that backend-java is running on localhost:8080.',
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
    setActiveNav('history');
    expandPanel('findings');
    setDetailLoading(true);
    setDetailError(null);
    setSelectedTask(null);
    try {
      const res = await getReviewTask(task.id);
      if (res.success && res.data) {
        setSelectedTask(res.data);
      } else {
        setDetailError(res.message || 'Task not found.');
      }
    } catch {
      setDetailError(
        'Backend is unavailable. Check that backend-java is running on localhost:8080.',
      );
    } finally {
      setDetailLoading(false);
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
    <div className="app-root">
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
              defaultReviewProvider={defaultReviewProvider}
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
              <ThemeToggle theme={theme} onToggle={toggleTheme} />
            </div>
          </header>

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
                Backend is unavailable. Check that backend-java is running on localhost:8080.
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
                  defaultReviewProvider={defaultReviewProvider}
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
                />
              </section>
            </div>
          </main>
        </div>
      </div>
    </div>
  );
}
