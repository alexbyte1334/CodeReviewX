import type { RiskLevel } from '../types/reviewTask';

export function riskLevelDisplayLabel(riskLevel: RiskLevel | string | null | undefined): string {
  if (riskLevel === 'HIGH') return 'HIGH';
  if (riskLevel === 'MEDIUM') return 'MEDIUM';
  if (riskLevel === 'LOW') return 'LOW';
  return 'NONE';
}

export function riskLevelBadgeClass(riskLevel: RiskLevel | string | null | undefined): string {
  if (riskLevel === 'HIGH') return 'badge badge-high';
  if (riskLevel === 'MEDIUM') return 'badge badge-medium';
  if (riskLevel === 'LOW') return 'badge badge-low';
  return 'badge badge-none';
}
