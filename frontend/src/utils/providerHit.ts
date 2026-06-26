import type { HistoricalReviewProvider } from '../types/reviewTask';

export function formatProviderSlug(provider?: string | null): string {
  if (!provider) return '—';
  return provider.toLowerCase() === 'mimo' ? 'MiMo' : 'Historical Mock';
}

export function formatProviderHitLabel(
  providerHit?: boolean,
  requestedProvider?: HistoricalReviewProvider | string | null,
  providerUsed?: HistoricalReviewProvider | string | null,
): string {
  if (providerHit === undefined || providerHit === null) {
    return 'Provider hit status unavailable';
  }
  if (providerHit) {
    return `命中 · ${formatProviderSlug(requestedProvider ?? providerUsed)} 已生效`;
  }
  const requested = formatProviderSlug(requestedProvider);
  const used = formatProviderSlug(providerUsed);
  return `未命中 · 请求 ${requested}，实际使用 ${used}`;
}
