export type ReviewTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';

export type RiskLevel = 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH';

export type IssueSeverity = 'LOW' | 'MEDIUM' | 'HIGH';

export type IssueCategory =
  | 'BUG'
  | 'SECURITY'
  | 'PERFORMANCE'
  | 'MAINTAINABILITY'
  | 'STYLE'
  | 'TEST';

export type IssueSource = 'MOCK' | 'MIMO' | 'SEMGREP' | 'LLM' | 'MANUAL';

export type ReviewProviderChoice = 'mock' | 'mimo';

export type IssueStatus = 'OPEN' | 'RESOLVED' | 'FALSE_POSITIVE';

export interface IssueSummary {
  totalIssues: number;
  highCount: number;
  mediumCount: number;
  lowCount: number;
  riskLevel: RiskLevel;
}

export interface ReviewIssue {
  id: string;
  severity: IssueSeverity;
  category: IssueCategory;
  source: IssueSource;
  status: IssueStatus;
  filePath: string;
  startLine: number;
  endLine: number | null;
  title: string;
  description: string;
  recommendation: string;
}

export interface ReviewTask {
  id: number;
  repoUrl: string;
  prNumber: number;
  status: ReviewTaskStatus;
  summary: string | null;
  riskLevel: RiskLevel | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
  issues: ReviewIssue[];
  issueSummary?: IssueSummary;
  requestedProvider?: ReviewProviderChoice;
  providerUsed?: ReviewProviderChoice;
  providerHit?: boolean;
}

export interface CreateReviewTaskRequest {
  repoUrl: string;
  prNumber: number;
  diffText?: string;
  provider?: ReviewProviderChoice;
}

export const MAX_DIFF_TEXT_LENGTH = 20000;

export interface HealthData {
  status: string;
  service: string;
  defaultReviewProvider?: string;
  mimoConfigured?: boolean;
}
