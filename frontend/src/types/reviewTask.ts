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

export type IssueSource = 'MOCK' | 'MIMO' | 'SEMGREP' | 'DEPENDENCY' | 'LLM' | 'MANUAL';

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

export interface IngestionSummary {
  headSha: string | null;
  baseSha: string | null;
  changedFiles: number | null;
  additions: number | null;
  deletions: number | null;
  truncated: boolean | null;
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
  reviewMode?: 'MANUAL_DIFF' | 'GITHUB_PR' | null;
  ingestionSummary?: IngestionSummary | null;
  traceSummary?: TraceSummary | null;
  commentPreviewCount?: number;
}

export type RepositoryIndexState = 'NOT_INDEXED' | 'QUEUED' | 'RUNNING' | 'READY' | 'FAILED';
export interface RepositoryIndexStatus { status: RepositoryIndexState; commitSha?: string | null; indexedChunks?: number; errorCode?: string | null; errorMessage?: string | null; phase?: string | null; processedFiles?: number | null; totalFiles?: number | null; lastProgressAt?: string | null; deadlineAt?: string | null; }
export interface RepositoryIndexResponse { jobId: number; status: RepositoryIndexState; repository?: string; requestedRef?: string; }
export interface RetrievalEvidence { citationLabel: string; path: string; startLine: number; endLine: number; excerpt: string; rank: number; score: number; }
export interface RetrievalTrace { degraded: boolean; degradedReason?: string | null; latencyMs: number; candidateCount: number; selectedCount: number; model?: string | null; evidence: RetrievalEvidence[]; }

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
export const MAX_PR_NUMBER = 2147483647;

export interface HealthData {
  status: string;
  service: string;
  reviewProvider?: string;
  mimoConfigured?: boolean;
}
