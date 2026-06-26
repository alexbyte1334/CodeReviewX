import type { BackendStatus, PanelId } from '../types/ui';

interface WorkspaceToolbarProps {
  backendStatus: BackendStatus;
  tasksCount: number;
  findingsLabel: string;
  expandedPanels: Set<PanelId>;
  onTogglePanel: (id: PanelId) => void;
}

const QUICK_PANELS: { id: PanelId; label: string }[] = [
  { id: 'review', label: 'Run Review' },
  { id: 'history', label: 'History' },
  { id: 'findings', label: 'Findings' },
];

export function WorkspaceToolbar({
  backendStatus,
  tasksCount,
  findingsLabel,
  expandedPanels,
  onTogglePanel,
}: WorkspaceToolbarProps) {
  const statusLabel =
    backendStatus === 'up'
      ? 'Backend connected'
      : backendStatus === 'down'
        ? 'Backend unavailable'
        : 'Checking backend…';

  return (
    <div className="workspace-toolbar">
      <div className="workspace-toolbar-intro">
        <p className="workspace-toolbar-eyebrow">CodeReviewX · Review Agent</p>
        <h1 className="workspace-toolbar-title">Review Workspace</h1>
        <p className="workspace-toolbar-subtitle">
          {expandedPanels.size === 0
            ? 'Choose a section below — panels stay collapsed until you expand them.'
            : `${expandedPanels.size} section${expandedPanels.size === 1 ? '' : 's'} open`}
        </p>
      </div>

      <div className="workspace-toolbar-actions">
        <div className="workspace-quick-nav" role="toolbar" aria-label="Quick panel navigation">
          {QUICK_PANELS.map(({ id, label }) => {
            const isOpen = expandedPanels.has(id);
            const badge =
              id === 'history' && tasksCount > 0
                ? tasksCount
                : id === 'findings' && findingsLabel.includes('Review #')
                  ? '•'
                  : null;

            return (
              <button
                key={id}
                type="button"
                className={`workspace-quick-pill${isOpen ? ' workspace-quick-pill--active' : ''}`}
                onClick={() => onTogglePanel(id)}
                aria-pressed={isOpen}
              >
                {label}
                {badge !== null && badge !== '•' && (
                  <span className="workspace-quick-pill-badge">{badge}</span>
                )}
                {badge === '•' && (
                  <span className="workspace-quick-pill-dot" aria-hidden="true" />
                )}
              </button>
            );
          })}
        </div>

        <div className="workspace-toolbar-status">
          <span
            className={`status-dot status-dot--breathing status-dot--${backendStatus}`}
            aria-hidden="true"
          />
          <span className="workspace-toolbar-status-text">{statusLabel}</span>
        </div>
      </div>
    </div>
  );
}
