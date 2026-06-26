import { describe, it, expect } from 'vitest';
import { formatIssueSourceLabel, resolveProviderSourceLabel } from '../utils/providerLabels';

describe('providerLabels', () => {
  it('maps MOCK to Mock Provider', () => {
    expect(formatIssueSourceLabel('MOCK')).toBe('Mock Provider');
  });

  it('maps MIMO to Xiaomi MiMo', () => {
    expect(formatIssueSourceLabel('MIMO')).toBe('Xiaomi MiMo');
  });

  it('resolves single provider source', () => {
    expect(resolveProviderSourceLabel(['MOCK'])).toBe('Mock Provider');
  });

  it('returns N/A for empty sources', () => {
    expect(resolveProviderSourceLabel([])).toBe('N/A');
  });
});
