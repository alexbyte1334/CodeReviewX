import type { RepositoryIndexStatus as Status } from '../types/reviewTask';

interface RepositoryIndexStatusProps {
  status?: Status | null;
  onIndex?: () => void;
  onReindex?: () => void;
  actionsDisabled?: boolean;
  actionError?: string | null;
}

export function RepositoryIndexStatus({
  status,
  onIndex,
  onReindex,
  actionsDisabled = false,
  actionError,
}: RepositoryIndexStatusProps) {
  const value = status?.status ?? 'NOT_INDEXED';
  const label = value === 'RUNNING' ? 'Indexing' : value[0] + value.slice(1).toLowerCase().replace('_', ' ');
  const error = actionError ?? status?.errorMessage;
  return (
    <div className="status-widget">
      <span>{label}</span>
      {value === 'NOT_INDEXED' && onIndex && <button type="button" disabled={actionsDisabled} onClick={onIndex}>Index</button>}
      {(value === 'FAILED' || value === 'READY') && onReindex && <button type="button" disabled={actionsDisabled} onClick={onReindex}>Reindex</button>}
      {error && <span role="alert">{error}</span>}
    </div>
  );
}
