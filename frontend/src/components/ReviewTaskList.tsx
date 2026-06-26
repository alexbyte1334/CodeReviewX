import type { ReviewTask } from '../types/reviewTask';
import { LoadingState } from './LoadingState';
import { ErrorMessage } from './ErrorMessage';
import { CollapsiblePanel } from './CollapsiblePanel';
import { riskLevelBadgeClass, riskLevelDisplayLabel } from '../utils/riskLevel';

interface ReviewTaskListProps {
  tasks: ReviewTask[];
  loading: boolean;
  error: string | null;
  selectedId: number | null;
  onSelect: (task: ReviewTask) => void;
  expanded: boolean;
  onToggle: () => void;
  summary: string;
}

function statusChipClass(status: ReviewTask['status']): string {
  switch (status) {
    case 'SUCCESS': return 'chip-dot chip-dot--success chip-dot--breathing';
    case 'FAILED': return 'chip-dot chip-dot--error chip-dot--breathing';
    case 'RUNNING': return 'chip-dot chip-dot--running chip-dot--breathing';
    default: return 'chip-dot chip-dot--pending';
  }
}

export function ReviewTaskList({
  tasks,
  loading,
  error,
  selectedId,
  onSelect,
  expanded,
  onToggle,
  summary,
}: ReviewTaskListProps) {
  return (
    <CollapsiblePanel
      panelId="panel-history"
      title="Review History"
      summary={summary}
      badge={
        tasks.length > 0 ? (
          <span className="collapsible-badge">{tasks.length}</span>
        ) : undefined
      }
      expanded={expanded}
      onToggle={onToggle}
    >
      {loading && <LoadingState message="Loading review tasks…" />}
      {error && <ErrorMessage message={error} />}

      {!loading && !error && tasks.length === 0 && (
        <div className="empty-state-panel">
          <p className="empty-state">
            No reviews yet. Run a review to get started.
          </p>
        </div>
      )}

      {!loading && !error && tasks.length > 0 && (
        <ul className="task-list">
          {tasks.map((task) => (
            <li
              key={task.id}
              className={`task-item${task.id === selectedId ? ' task-item--selected' : ''}`}
              onClick={() => onSelect(task)}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => e.key === 'Enter' && onSelect(task)}
            >
              <div className="task-item-header">
                <span className="task-id">Review #{task.id}</span>
                <span className="chip">
                  <span className={statusChipClass(task.status)} aria-hidden="true" />
                  {task.status}
                </span>
                {task.riskLevel && (
                  <span className={riskLevelBadgeClass(task.riskLevel)}>
                    {riskLevelDisplayLabel(task.riskLevel)}
                  </span>
                )}
              </div>
              <div className="task-item-repo">{task.repoUrl}</div>
              <div className="task-item-meta">
                PR #{task.prNumber} &nbsp;·&nbsp;
                {new Date(task.createdAt).toLocaleString()}
              </div>
              {task.summary && (
                <div className="task-item-summary">{task.summary}</div>
              )}
            </li>
          ))}
        </ul>
      )}
    </CollapsiblePanel>
  );
}
