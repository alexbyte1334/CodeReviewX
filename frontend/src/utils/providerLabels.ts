import type { IssueSource } from '../types/reviewTask';

const SOURCE_LABELS: Record<IssueSource, string> = {
  MOCK: 'Mock Provider',
  MIMO: 'Xiaomi MiMo',
  SEMGREP: 'Semgrep',
  LLM: 'LLM',
  MANUAL: 'Manual',
};

export function formatIssueSourceLabel(source: IssueSource): string {
  return SOURCE_LABELS[source] ?? source;
}

export function resolveProviderSourceLabel(sources: IssueSource[]): string {
  if (sources.length === 0) {
    return 'N/A';
  }
  const unique = [...new Set(sources)];
  if (unique.length === 1) {
    return formatIssueSourceLabel(unique[0]);
  }
  return unique.map(formatIssueSourceLabel).join(', ');
}
