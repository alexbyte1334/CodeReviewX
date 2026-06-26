import { describe, it, expect } from 'vitest';
import { formatIssueSourceLabel, resolveProviderSourceLabel } from '../utils/providerLabels';

describe('providerLabels', () => {
  it('maps historical MOCK data without exposing it as an active provider', () => {
    expect(formatIssueSourceLabel('MOCK')).toBe('Historical Mock');
  });

  it('maps MIMO to Xiaomi MiMo', () => {
    expect(formatIssueSourceLabel('MIMO')).toBe('Xiaomi MiMo');
  });

  it('resolves single provider source', () => {
    expect(resolveProviderSourceLabel(['MOCK'])).toBe('Historical Mock');
  });

  it('returns N/A for empty sources', () => {
    expect(resolveProviderSourceLabel([])).toBe('N/A');
  });
});
