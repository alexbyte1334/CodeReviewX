export type BackendStatus = 'checking' | 'up' | 'down';
export type PanelId = 'review' | 'history' | 'findings';

export const PRODUCT_LIMITS = [
  'Manual diff or GitHub PR diff input only',
  'OpenAI-compatible model review with structured evidence checks',
  'Model credentials are configured locally and never exposed in the browser',
  'No repository clone or GitHub App integration yet',
] as const;
