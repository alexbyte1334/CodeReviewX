import { useState } from 'react';
import type { IssueSummary, ReviewIssue, ReviewTask } from '../types/reviewTask';
import { getIssueSummary } from '../utils/reviewSummary';
import { formatIssueSourceLabel, resolveProviderSourceLabel } from '../utils/providerLabels';
import { formatProviderHitLabel } from '../utils/providerHit';
import { riskLevelDisplayLabel } from '../utils/riskLevel';
import { LoadingState } from './LoadingState';
import { ErrorMessage } from './ErrorMessage';
import { CollapsiblePanel } from './CollapsiblePanel';

interface ReviewTaskDetailProps {
  task: ReviewTask | null;
  loading: boolean;
  error: string | null;
  expanded: boolean;
  onToggle: () => void;
  summary: string;
}

function riskBadgeClass(riskLevel: string): string {
  switch (riskLevel) {
    case 'HIGH': return 'risk-badge risk-badge--high';
    case 'MEDIUM': return 'risk-badge risk-badge--medium';
    case 'LOW': return 'risk-badge risk-badge--low';
    default: return 'risk-badge risk-badge--none';
  }
}

function severityBarWidth(count: number, total: number): string {
  if (total === 0 || count === 0) return '0%';
  return `${Math.max((count / total) * 100, count > 0 ? 4 : 0)}%`;
}

function SeverityBars({ summary }: { summary: IssueSummary }) {
  const total = summary.totalIssues;

  return (
    <div className="severity-bars">
      <div className="severity-bar-row">
        <span className="severity-bar-label">High</span>
        <div className="severity-bar-track" role="presentation">
          <div
            className="severity-bar-fill severity-bar-fill--high"
            style={{ width: severityBarWidth(summary.highCount, total) }}
          />
        </div>
        <span className="severity-bar-count">{summary.highCount}</span>
      </div>
      <div className="severity-bar-row">
        <span className="severity-bar-label">Medium</span>
        <div className="severity-bar-track" role="presentation">
          <div
            className="severity-bar-fill severity-bar-fill--medium"
            style={{ width: severityBarWidth(summary.mediumCount, total) }}
          />
        </div>
        <span className="severity-bar-count">{summary.mediumCount}</span>
      </div>
      <div className="severity-bar-row">
        <span className="severity-bar-label">Low</span>
        <div className="severity-bar-track" role="presentation">
          <div
            className="severity-bar-fill severity-bar-fill--low"
            style={{ width: severityBarWidth(summary.lowCount, total) }}
          />
        </div>
        <span className="severity-bar-count">{summary.lowCount}</span>
      </div>
    </div>
  );
}

function ReviewSummaryPanel({
  task,
  summary,
  issues,
}: {
  task: ReviewTask;
  summary: IssueSummary;
  issues: ReviewIssue[];
}) {
  const providerLabel = resolveProviderSourceLabel(issues.map((issue) => issue.source));
  const riskLabel = riskLevelDisplayLabel(summary.riskLevel);
  const providerHitLabel = formatProviderHitLabel(
    task.providerHit,
    task.requestedProvider,
    task.providerUsed,
  );

  return (
    <div className="review-summary-panel">
      <div className="review-summary-meta">
        <div className="summary-meta-item">
          <span className="summary-meta-label">Reviewed Target</span>
          <span className="summary-meta-value">
            {task.repoUrl} · PR #{task.prNumber}
          </span>
        </div>
        <div className="summary-meta-item">
          <span className="summary-meta-label">Created</span>
          <span className="summary-meta-value">
            {new Date(task.createdAt).toLocaleString()}
          </span>
        </div>
        <div className="summary-meta-item">
          <span className="summary-meta-label">Provider Source</span>
          <div className="summary-meta-chips">
            <span className="chip">
              <span className="chip-dot chip-dot--success chip-dot--breathing" aria-hidden="true" />
              {providerLabel}
            </span>
          </div>
        </div>
        {task.providerHit !== undefined && (
          <div className="summary-meta-item">
            <span className="summary-meta-label">Provider Hit</span>
            <span
              className={`provider-hit-chip${task.providerHit ? ' provider-hit-chip--hit' : ' provider-hit-chip--miss'}`}
            >
              {providerHitLabel}
            </span>
          </div>
        )}
      </div>

      <div className="risk-row">
        <span className="risk-row-label">Risk Level</span>
        <span className={riskBadgeClass(summary.riskLevel)}>{riskLabel}</span>
      </div>

      <div className="metric-cards">
        <div className="metric-card">
          <div className="metric-card-value">{summary.totalIssues}</div>
          <div className="metric-card-label">Findings</div>
        </div>
        <div className="metric-card">
          <div className="metric-card-value metric-card-value--high">{summary.highCount}</div>
          <div className="metric-card-label">High</div>
        </div>
        <div className="metric-card">
          <div className="metric-card-value metric-card-value--medium">{summary.mediumCount}</div>
          <div className="metric-card-label">Medium</div>
        </div>
        <div className="metric-card">
          <div className="metric-card-value metric-card-value--low">{summary.lowCount}</div>
          <div className="metric-card-label">Low</div>
        </div>
      </div>

      <div className="severity-breakdown">
        <span className="severity-breakdown-label">Severity Breakdown</span>
        <SeverityBars summary={summary} />
      </div>
    </div>
  );
}

function IssueCard({ issue }: { issue: ReviewIssue }) {
  const [open, setOpen] = useState(false);
  const lineRange =
    issue.endLine && issue.endLine !== issue.startLine
      ? `${issue.startLine}–${issue.endLine}`
      : `${issue.startLine}`;

  return (
    <article className={`issue-card issue-card--collapsible${open ? ' issue-card--open' : ''}`}>
      <button
        type="button"
        className="issue-card-trigger"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-label={open ? `Collapse ${issue.title}` : `Expand ${issue.title}`}
      >
        <div className="issue-card-trigger-main">
          <div className="issue-card-badges">
            <span
              className={`badge badge-${issue.severity.toLowerCase()}`}
              aria-label={`Severity: ${issue.severity}`}
            >
              {issue.severity}
            </span>
            <span className="badge badge-category">{issue.category}</span>
          </div>
          <h4 className="issue-card-title">{issue.title}</h4>
        </div>
        <span className={`issue-card-chevron${open ? ' issue-card-chevron--open' : ''}`} aria-hidden="true" />
      </button>

      <div className={`issue-card-body-wrap${open ? ' issue-card-body-wrap--open' : ''}`} aria-hidden={!open}>
        <div className="issue-card-body">
          <div className="issue-card-badges issue-card-badges--secondary">
            <span className="badge badge-source">{formatIssueSourceLabel(issue.source)}</span>
            <span className="badge badge-status">{issue.status}</span>
          </div>

          <div className="issue-card-section">
            <span className="issue-card-section-label">Location</span>
            <p className="issue-card-location">
              <span className="issue-card-filepath">{issue.filePath}</span>
              <span className="issue-card-line">:{lineRange}</span>
            </p>
          </div>

          <div className="issue-card-section">
            <span className="issue-card-section-label">Description</span>
            <p className="issue-card-section-text">{issue.description}</p>
          </div>

          <div className="issue-card-section issue-card-recommendation">
            <span className="issue-card-section-label">Recommendation</span>
            <p className="issue-card-section-text">{issue.recommendation}</p>
          </div>
        </div>
      </div>
    </article>
  );
}

export function ReviewTaskDetail({
  task,
  loading,
  error,
  expanded,
  onToggle,
  summary,
}: ReviewTaskDetailProps) {
  const [summaryOpen, setSummaryOpen] = useState(false);
  const [issuesOpen, setIssuesOpen] = useState(false);

  const issueSummary = task ? getIssueSummary(task) : null;
  const summaryLine = issueSummary
    ? `${issueSummary.totalIssues} findings · ${issueSummary.riskLevel} risk`
    : 'Summary metrics and severity';

  const issuesLine = task
    ? `${task.issues.length} issue${task.issues.length === 1 ? '' : 's'}`
    : 'Issue cards';

  return (
    <CollapsiblePanel
      panelId="panel-findings"
      title="Findings"
      summary={summary}
      expanded={expanded}
      onToggle={onToggle}
    >
      {loading && <LoadingState message="Loading review results…" />}
      {error && <ErrorMessage message={error} />}

      {!loading && !error && !task && (
        <div className="empty-state-panel">
          <p className="empty-state-title">No review selected</p>
          <p className="empty-state">
            Select a review from history to inspect findings and recommendations.
          </p>
        </div>
      )}

      {!loading && !error && task && issueSummary && (
        <div className="detail-content">
          <CollapsiblePanel
            panelId="panel-findings-summary"
            title="Review Summary"
            summary={summaryLine}
            expanded={summaryOpen}
            onToggle={() => setSummaryOpen((v) => !v)}
            compact
          >
            <ReviewSummaryPanel task={task} summary={issueSummary} issues={task.issues} />
          </CollapsiblePanel>

          <CollapsiblePanel
            panelId="panel-findings-issues"
            title="Issue Details"
            summary={issuesLine}
            badge={
              task.issues.length > 0 ? (
                <span className="collapsible-badge">{task.issues.length}</span>
              ) : undefined
            }
            expanded={issuesOpen}
            onToggle={() => setIssuesOpen((v) => !v)}
            compact
          >
            {task.issues.length === 0 ? (
              <p className="empty-state">No findings were returned for this review.</p>
            ) : (
              <div className="issue-card-list">
                {task.issues.map((issue) => (
                  <IssueCard key={issue.id} issue={issue} />
                ))}
              </div>
            )}
          </CollapsiblePanel>

          {task.errorMessage && (
            <div className="task-error-panel" role="alert">
              <span className="task-error-label">Task Error</span>
              <p>{task.errorMessage}</p>
            </div>
          )}
        </div>
      )}
    </CollapsiblePanel>
  );
}
