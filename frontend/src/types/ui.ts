export type BackendStatus = 'checking' | 'up' | 'down';
export type PanelId = 'review' | 'history' | 'findings';

export const PRODUCT_LIMITS = [
  'Manual diff input only',
  'MiMo is the default provider (falls back to Mock without API key)',
  'MiMo requires MIMO_API_KEY on the backend server',
  'No automatic GitHub PR fetching yet',
] as const;
