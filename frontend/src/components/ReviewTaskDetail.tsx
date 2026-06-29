import { useState } from 'react';
import type { CommentPreview, IssueSummary, ReviewIssue, ReviewTask, ToolTraceItem } from '../types/reviewTask';
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
  commentPreviews?: CommentPreview[];
  commentPreviewLoading?: boolean;
  commentPreviewError?: string | null;
  commentPublishing?: boolean;
  toolTraceItems?: ToolTraceItem[];
  toolTraceLoading?: boolean;
  toolTraceError?: string | null;
  onCommentPreviewSelectionChange?: (previewId: number, selected: boolean) => void;
  onPublishSelectedCommentPreviews?: () => void;
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

function publishStatusClass(status: string): string {
  switch (status) {
    case 'PUBLISHED': return 'comment-preview-status comment-preview-status--published';
    case 'FAILED': return 'comment-preview-status comment-preview-status--failed';
    case 'PUBLISHING': return 'comment-preview-status comment-preview-status--publishing';
    default: return 'comment-preview-status';
  }
}

function CommentPreviewPanel({
  previews,
  loading,
  error,
  publishing,
  onSelectionChange,
  onPublishSelected,
}: {
  previews: CommentPreview[];
  loading: boolean;
  error: string | null;
  publishing: boolean;
  onSelectionChange?: (previewId: number, selected: boolean) => void;
  onPublishSelected?: () => void;
}) {
  const publishableSelectedCount = previews.filter(
    (preview) => preview.selectedForPublish && preview.publishStatus !== 'PUBLISHED',
  ).length;
  const canPublish = publishableSelectedCount > 0 && !publishing && Boolean(onPublishSelected);

  return (
    <div className="comment-preview-panel">
      {loading && <LoadingState message="Loading comment previews…" />}
      {error && <ErrorMessage message={error} />}

      {!loading && previews.length === 0 && (
        <p className="empty-state">No comment previews were generated for this review.</p>
      )}

      {!loading && previews.length > 0 && (
        <>
          <div className="comment-preview-toolbar">
            <span className="comment-preview-count">
              {publishableSelectedCount} ready to publish
            </span>
            <button
              type="button"
              className="comment-preview-publish-button"
              onClick={onPublishSelected}
              disabled={!canPublish}
            >
              Publish selected
            </button>
          </div>

          <div className="comment-preview-list">
            {previews.map((preview) => (
              <article className="comment-preview-card" key={preview.id}>
                <label className="comment-preview-select">
                  <input
                    type="checkbox"
                    checked={preview.selectedForPublish}
                    disabled={publishing || preview.publishStatus === 'PUBLISHED'}
                    onChange={(event) => onSelectionChange?.(preview.id, event.currentTarget.checked)}
                  />
                  <span>Select</span>
                </label>

                <div className="comment-preview-main">
                  <div className="comment-preview-meta">
                    <span className="comment-preview-location">
                      {preview.filePath}
                      {preview.line ? `:${preview.line}` : ''}
                    </span>
                    <span className={publishStatusClass(preview.publishStatus)}>
                      {preview.publishStatus}
                    </span>
                  </div>
                  <p className="comment-preview-body">{preview.draftBody}</p>
                  {preview.githubCommentId && (
                    <p className="comment-preview-result">
                      GitHub comment #{preview.githubCommentId}
                    </p>
                  )}
                  {preview.publishErrorMessage && (
                    <p className="comment-preview-error">{preview.publishErrorMessage}</p>
                  )}
                </div>
              </article>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

function traceStatusClass(status: string): string {
  switch (status) {
    case 'SUCCESS': return 'agent-trace-status agent-trace-status--success';
    case 'FAILED': return 'agent-trace-status agent-trace-status--failed';
    case 'RUNNING': return 'agent-trace-status agent-trace-status--running';
    default: return 'agent-trace-status';
  }
}

function traceStepLabel(toolName: string): string {
  const labels: Record<string, string> = {
    'github.pr.metadata.load': 'GitHub PR metadata',
    'github.pr.diff.load': 'GitHub PR diff',
    'mimo.ai1.plan': 'AI-1 planner',
    'mimo.ai2.execute': 'AI-2 executor',
    'mimo.ai1.gate': 'AI-1 gatekeeper',
    'issue.generate': 'Issue generation',
    'comment.preview.build': 'Comment preview',
    'mimo.auth.check': 'MiMo auth check',
  };
  return labels[toolName] ?? toolName;
}

function formatTraceDuration(durationMs: number | null): string {
  if (durationMs == null) return 'Pending';
  if (durationMs < 1000) return `${durationMs} ms`;
  return `${(durationMs / 1000).toFixed(1)} s`;
}

function formatTraceTime(startedAt: string, finishedAt: string | null): string {
  const start = new Date(startedAt).toLocaleTimeString();
  if (!finishedAt) return start;
  return `${start} - ${new Date(finishedAt).toLocaleTimeString()}`;
}

function AgentTracePanel({
  items,
  loading,
  error,
}: {
  items: ToolTraceItem[];
  loading: boolean;
  error: string | null;
}) {
  return (
    <div className="agent-trace-panel">
      {loading && <LoadingState message="Loading agent trace…" />}
      {error && <ErrorMessage message={error} />}

      {!loading && items.length === 0 && (
        <p className="empty-state">No agent trace was recorded for this review.</p>
      )}

      {!loading && items.length > 0 && (
        <ol className="agent-trace-list" aria-label="Agent step timeline">
          {items.map((item) => (
            <li className="agent-trace-item" key={item.id}>
              <div className="agent-trace-marker" aria-hidden="true" />
              <div className="agent-trace-content">
                <div className="agent-trace-header">
                  <div>
                    <p className="agent-trace-title">{traceStepLabel(item.toolName)}</p>
                    <p className="agent-trace-name">{item.toolName}</p>
                  </div>
                  <span className={traceStatusClass(item.status)}>{item.status}</span>
                </div>
                <div className="agent-trace-meta">
                  <span>{formatTraceDuration(item.durationMs)}</span>
                  <span>{formatTraceTime(item.startedAt, item.finishedAt)}</span>
                  {item.errorCode && <span>{item.errorCode}</span>}
                </div>
                {item.outputSummary && (
                  <p className="agent-trace-summary">{item.outputSummary}</p>
                )}
              </div>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

export function ReviewTaskDetail({
  task,
  loading,
  error,
  expanded,
  onToggle,
  summary,
  commentPreviews = [],
  commentPreviewLoading = false,
  commentPreviewError = null,
  commentPublishing = false,
  toolTraceItems = [],
  toolTraceLoading = false,
  toolTraceError = null,
  onCommentPreviewSelectionChange,
  onPublishSelectedCommentPreviews,
}: ReviewTaskDetailProps) {
  const [summaryOpen, setSummaryOpen] = useState(false);
  const [issuesOpen, setIssuesOpen] = useState(false);
  const [traceOpen, setTraceOpen] = useState(false);
  const [commentPreviewsOpen, setCommentPreviewsOpen] = useState(false);

  const issueSummary = task ? getIssueSummary(task) : null;
  const summaryLine = issueSummary
    ? `${issueSummary.totalIssues} findings · ${issueSummary.riskLevel} risk`
    : 'Summary metrics and severity';

  const issuesLine = task
    ? `${task.issues.length} issue${task.issues.length === 1 ? '' : 's'}`
    : 'Issue cards';

  const commentPreviewLine = task
    ? `${commentPreviews.length || task.commentPreviewCount || 0} preview${
        (commentPreviews.length || task.commentPreviewCount || 0) === 1 ? '' : 's'
      }`
    : 'Draft comments';

  const traceLine = task
    ? `${toolTraceItems.length || task.traceSummary?.toolCount || 0} step${
        (toolTraceItems.length || task.traceSummary?.toolCount || 0) === 1 ? '' : 's'
      }`
    : 'Agent timeline';

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

          <CollapsiblePanel
            panelId="panel-findings-agent-trace"
            title="Agent Trace"
            summary={traceLine}
            badge={
              (toolTraceItems.length || task.traceSummary?.toolCount || 0) > 0 ? (
                <span className="collapsible-badge">
                  {toolTraceItems.length || task.traceSummary?.toolCount || 0}
                </span>
              ) : undefined
            }
            expanded={traceOpen}
            onToggle={() => setTraceOpen((v) => !v)}
            compact
          >
            <AgentTracePanel
              items={toolTraceItems}
              loading={toolTraceLoading}
              error={toolTraceError}
            />
          </CollapsiblePanel>

          <CollapsiblePanel
            panelId="panel-findings-comment-previews"
            title="Comment Previews"
            summary={commentPreviewLine}
            badge={
              (commentPreviews.length || task.commentPreviewCount || 0) > 0 ? (
                <span className="collapsible-badge">
                  {commentPreviews.length || task.commentPreviewCount || 0}
                </span>
              ) : undefined
            }
            expanded={commentPreviewsOpen}
            onToggle={() => setCommentPreviewsOpen((v) => !v)}
            compact
          >
            <CommentPreviewPanel
              previews={commentPreviews}
              loading={commentPreviewLoading}
              error={commentPreviewError}
              publishing={commentPublishing}
              onSelectionChange={onCommentPreviewSelectionChange}
              onPublishSelected={onPublishSelectedCommentPreviews}
            />
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
