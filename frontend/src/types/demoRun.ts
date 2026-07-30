export type DemoMode = 'LIVE' | 'REPLAY';

export interface DemoStep {
  id: string;
  label: string;
  status: string;
  durationMs: number | null;
  summary: string | null;
  errorCode: string | null;
}

export interface DemoFinding {
  issueKey: string;
  severity: string;
  category: string;
  filePath: string;
  line: number;
  title: string;
  description: string;
  recommendation: string;
}

export interface DemoEvidence {
  issueKey: string;
  citationLabel: string;
  path: string;
  startLine: number;
  endLine: number;
  excerpt: string;
  rank: number;
  score: number;
}

export interface DemoToolTrace {
  sequence: number;
  toolName: string;
  status: string;
  inputSummary: string | null;
  outputSummary: string | null;
  errorCode: string | null;
  durationMs: number | null;
}

export interface DemoPreview {
  id: number;
  issueKey: string;
  filePath: string;
  line: number;
  severity: string;
  category: string;
  body: string;
  selected: boolean;
  publishStatus: string;
  githubUrl: string | null;
}

export interface DemoEvent {
  sequence: number;
  type: string;
  step: string | null;
  status: string;
  summary: string | null;
  errorCode: string | null;
  durationMs: number | null;
  createdAt: string;
}

export interface DemoSnapshot {
  runId: string;
  scenarioId: string;
  mode: DemoMode;
  status: string;
  decision: string | null;
  replayReason: string | null;
  diffText: string;
  steps: DemoStep[];
  findings: DemoFinding[];
  evidence: DemoEvidence[];
  toolTrace: DemoToolTrace[];
  commentPreviews: DemoPreview[];
  events: DemoEvent[];
  publishedCommentUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DemoCreateResponse {
  runId: string;
  status: string;
  snapshotUrl: string;
  eventsUrl: string;
  mode: DemoMode;
}
