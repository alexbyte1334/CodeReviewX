export type BackendStatus = 'checking' | 'up' | 'down';
export type PanelId = 'review' | 'history' | 'findings';

export const PRODUCT_LIMITS = [
  'Manual diff input only',
  'MiMo dual-agent review only',
  'MiMo requires planner and executor API keys on the backend server',
  'No automatic GitHub PR fetching yet',
] as const;
