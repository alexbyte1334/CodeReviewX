export type BackendStatus = 'checking' | 'up' | 'down';
export type PanelId = 'review' | 'history' | 'findings';

export const PRODUCT_LIMITS = [
  'Manual diff input only',
  'Mock provider is default',
  'MiMo requires local environment configuration',
  'No automatic GitHub PR fetching yet',
] as const;
