export type ReviewTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';

export type RiskLevel = 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH';

export type PublishStatus = 'NOT_PUBLISHED' | 'PUBLISHING' | 'PUBLISHED' | 'FAILED';

export type ToolTraceStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';

export type IssueSeverity = 'LOW' | 'MEDIUM' | 'HIGH';

export type IssueCategory =
  | 'BUG'
  | 'SECURITY'
  | 'PERFORMANCE'
  | 'MAINTAINABILITY'
  | 'STYLE'
  | 'TEST';

export type IssueSource = 'MOCK' | 'MIMO' | 'SEMGREP' | 'LLM' | 'MANUAL';

export type ReviewProviderChoice = 'mimo';
export type HistoricalReviewProvider = ReviewProviderChoice | 'mock';

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

export interface TraceSummary {
  toolCount: number;
  failedToolCount: number;
  providerFallback: boolean;
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
  requestedProvider?: HistoricalReviewProvider;
  providerUsed?: HistoricalReviewProvider;
  providerHit?: boolean;
  latestRunId?: number | null;
  traceSummary?: TraceSummary | null;
  commentPreviewCount?: number;
}

export interface CommentPreview {
  id: number;
  issueId: string;
  filePath: string;
  line: number | null;
  draftBody: string;
  selectedForPublish: boolean;
  publishStatus: PublishStatus;
  githubCommentId?: number | null;
  publishErrorMessage?: string | null;
}

export interface CommentPreviewListResponse {
  items: CommentPreview[];
}

export interface ToolTraceItem {
  id: number;
  toolName: string;
  status: ToolTraceStatus;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  outputSummary: string | null;
  errorCode: string | null;
}

export interface ToolTraceListResponse {
  items: ToolTraceItem[];
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
  reviewProvider?: string;
  mimoConfigured?: boolean;
}
