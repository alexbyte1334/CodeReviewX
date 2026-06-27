export type BackendStatus = 'checking' | 'up' | 'down';
export type PanelId = 'review' | 'history' | 'findings';

export const PRODUCT_LIMITS = [
  'Manual diff or GitHub PR diff input only',
  'MiMo dual-agent review only',
  'MiMo requires planner and executor API keys on the backend server',
  'No repository clone or GitHub App integration yet',
] as const;
