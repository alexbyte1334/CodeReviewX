import type { ReactNode } from 'react';

interface CollapsiblePanelProps {
  panelId: string;
  title: string;
  summary?: string;
  badge?: ReactNode;
  expanded: boolean;
  onToggle: () => void;
  children: ReactNode;
  className?: string;
  compact?: boolean;
}

export function CollapsiblePanel({
  panelId,
  title,
  summary,
  badge,
  expanded,
  onToggle,
  children,
  className = '',
  compact = false,
}: CollapsiblePanelProps) {
  const toggleLabel = expanded ? `Collapse ${title} panel` : `Expand ${title} panel`;

  return (
    <section
      className={`collapsible-panel card${compact ? ' collapsible-panel--compact' : ''}${expanded ? ' collapsible-panel--expanded' : ''} ${className}`.trim()}
      aria-labelledby={`${panelId}-title`}
    >
      <div className="collapsible-header">
        <button
          type="button"
          className="collapsible-trigger"
          onClick={onToggle}
          aria-expanded={expanded}
          aria-controls={`${panelId}-body`}
          aria-label={toggleLabel}
        >
          <span className="collapsible-trigger-main">
            <span id={`${panelId}-title`} className="collapsible-title">
              {title}
            </span>
            {summary && (
              <span
                className={`collapsible-summary${expanded ? ' collapsible-summary--hidden' : ''}`}
                aria-hidden={expanded}
              >
                {summary}
              </span>
            )}
          </span>
          <span className="collapsible-trigger-meta">
            {badge}
            <span className={`collapsible-chevron${expanded ? ' collapsible-chevron--open' : ''}`} aria-hidden="true" />
          </span>
        </button>
      </div>

      <div
        id={`${panelId}-body`}
        className={`collapsible-body${expanded ? ' collapsible-body--open' : ''}`}
        aria-hidden={!expanded}
      >
        <div className="collapsible-body-wrap">
          <div className="collapsible-body-inner">{children}</div>
        </div>
      </div>
    </section>
  );
}
