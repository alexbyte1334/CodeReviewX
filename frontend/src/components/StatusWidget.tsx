import type { ReviewTask } from '../types/reviewTask';
import type { BackendStatus } from '../types/ui';

interface StatusWidgetProps {
  backendStatus: BackendStatus;
  tasks: ReviewTask[];
  mimoConfigured?: boolean;
}

interface ArcGaugeProps {
  label: string;
  value: number;
  displayValue: string;
  color: string;
  trackColor?: string;
  indeterminate?: boolean;
  semi?: boolean;
}

const R = 28;
const STROKE = 5;
const CIRC = 2 * Math.PI * R;

function ArcGauge({
  label,
  value,
  displayValue,
  color,
  trackColor = 'rgba(120, 120, 128, 0.16)',
  indeterminate = false,
  semi = false,
}: ArcGaugeProps) {
  const clamped = Math.min(Math.max(value, 0), 1);
  const arcLength = semi ? CIRC * 0.5 : CIRC * 0.72;
  const offset = arcLength * (1 - clamped);
  const rotation = semi ? 180 : 135;

  return (
    <div className="arc-gauge" role="img" aria-label={`${label}: ${displayValue}`}>
      <svg
        className={`arc-gauge-svg${indeterminate ? ' arc-gauge-svg--indeterminate' : ''}`}
        viewBox="0 0 72 72"
        width="72"
        height="72"
        aria-hidden="true"
      >
        <circle
          className="arc-gauge-track"
          cx="36"
          cy="36"
          r={R}
          fill="none"
          stroke={trackColor}
          strokeWidth={STROKE}
          strokeLinecap="round"
          strokeDasharray={`${arcLength} ${CIRC}`}
          transform={`rotate(${rotation} 36 36)`}
        />
        <circle
          className="arc-gauge-fill"
          cx="36"
          cy="36"
          r={R}
          fill="none"
          stroke={color}
          strokeWidth={STROKE}
          strokeLinecap="round"
          strokeDasharray={`${arcLength} ${CIRC}`}
          strokeDashoffset={indeterminate ? arcLength * 0.65 : offset}
          transform={`rotate(${rotation} 36 36)`}
        />
      </svg>
      <div className="arc-gauge-center">
        <span className="arc-gauge-value">{displayValue}</span>
        <span className="arc-gauge-label">{label}</span>
      </div>
    </div>
  );
}

function backendGaugeProps(status: BackendStatus): {
  value: number;
  display: string;
  color: string;
  indeterminate: boolean;
} {
  switch (status) {
    case 'up':
      return { value: 1, display: 'OK', color: 'var(--apple-green)', indeterminate: false };
    case 'down':
      return { value: 0.12, display: '—', color: 'var(--apple-red)', indeterminate: false };
    default:
      return { value: 0.45, display: '…', color: 'var(--apple-orange)', indeterminate: true };
  }
}

function reviewGaugeProps(tasks: ReviewTask[]): { value: number; display: string } {
  if (tasks.length === 0) {
    return { value: 0, display: '0' };
  }
  const done = tasks.filter((t) => t.status === 'SUCCESS').length;
  return {
    value: done / tasks.length,
    display: String(tasks.length),
  };
}

export function StatusWidget({
  backendStatus,
  tasks,
  mimoConfigured = false,
}: StatusWidgetProps) {
  const backend = backendGaugeProps(backendStatus);
  const reviews = reviewGaugeProps(tasks);

  const statusLabel =
    backendStatus === 'up'
      ? 'Connected'
      : backendStatus === 'down'
        ? 'Unavailable'
        : 'Checking…';

  const providerCaption = mimoConfigured
    ? 'MiMo dual-agent review'
    : 'MiMo keys required';

  return (
    <div className="status-widget" aria-label="Provider status widget">
      <div className="status-widget-header">
        <span className="status-widget-title">Provider Status</span>
        <span className="status-widget-live">
          <span
            className={`status-dot status-dot--breathing status-dot--${backendStatus}`}
            aria-hidden="true"
          />
          <span className="status-widget-status-text">{statusLabel}</span>
        </span>
      </div>

      <div className="status-widget-rings">
        <ArcGauge
          label="Backend"
          value={backend.value}
          displayValue={backend.display}
          color={backend.color}
          indeterminate={backend.indeterminate}
          semi
        />
        <ArcGauge
          label="Reviews"
          value={reviews.value}
          displayValue={reviews.display}
          color="var(--apple-blue)"
          semi
        />
      </div>

      <p className="status-widget-caption">{providerCaption}</p>
    </div>
  );
}
