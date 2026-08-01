import type { ApiResponse } from '../types/apiResponse';
import type {
  CommentPreview,
  CommentPreviewListResponse,
  HealthData,
  ReviewTask,
  RepositoryIndexStatus, RetrievalTrace, RetrievalEvidence, RepositoryIndexResponse,
} from '../types/reviewTask';


const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');

export interface ReviewApiEvent { sequence: number; type: string; status: string; summary: string | null; errorCode: string | null; }
export type ReviewApiReview = Omit<ReviewTask, 'id' | 'latestRunId'>;
export interface ReviewApiSnapshot {
  runId: string; status: string; mode: 'LIVE' | 'REPLAY'; repositoryUrl: string; prNumber: number;
  review: ReviewApiReview; events: ReviewApiEvent[];
  errorCode: string | null; errorMessage: string | null;
}

export async function createReview(request: { repositoryUrl: string; prNumber: number; inputMode?: 'GITHUB_PR' | 'MANUAL_DIFF'; diffText?: string }, idempotencyKey: string): Promise<ApiResponse<ReviewApiSnapshot>> {
  return fetchJson<ReviewApiSnapshot>(`${BASE_URL}/api/reviews`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey }, body: JSON.stringify(request) });
}
export async function getReviewSnapshot(runId: string): Promise<ApiResponse<ReviewApiSnapshot>> { return fetchJson(`${BASE_URL}/api/reviews/${encodeURIComponent(runId)}`); }
export async function retryReview(runId: string): Promise<ApiResponse<ReviewApiSnapshot>> { return fetchJson(`${BASE_URL}/api/reviews/${encodeURIComponent(runId)}/retry`, { method: 'POST' }); }
export function reviewEventsUrl(runId: string, afterSequence: number): string { return `${BASE_URL}/api/reviews/${encodeURIComponent(runId)}/events?afterSequence=${afterSequence}`; }

async function fetchJson<T>(url: string, options?: RequestInit): Promise<ApiResponse<T>> {
  const response = await fetch(url, options);
  let json: ApiResponse<T>;
  try { json = await response.json(); } catch { json = { success: response.ok, message: response.statusText, data: null }; }
  if (response.ok === false && json.success) return { ...json, success: false, message: json.message || response.statusText, httpStatus: response.status };
  return { ...json, httpStatus: response.status };
}

export async function getHealth(): Promise<ApiResponse<HealthData>> {
  return fetchJson<HealthData>(`${BASE_URL}/api/health`);
}

export async function getCommentPreviews(
  runId: string,
): Promise<ApiResponse<CommentPreviewListResponse>> {
  return fetchJson<CommentPreviewListResponse>(
    `${BASE_URL}/api/reviews/${encodeURIComponent(runId)}/previews`,
  );
}

export async function updateCommentPreviewSelection(
  runId: string,
  selectedPreviewIds: number[],
): Promise<ApiResponse<CommentPreviewListResponse>> {
  return fetchJson<CommentPreviewListResponse>(
    `${BASE_URL}/api/reviews/${encodeURIComponent(runId)}/previews/selection`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ selectedPreviewIds }),
    },
  );
}

export async function publishSelectedCommentPreviews(
  runId: string,
): Promise<ApiResponse<CommentPreviewListResponse>> {
  return fetchJson<CommentPreviewListResponse>(
    `${BASE_URL}/api/reviews/${encodeURIComponent(runId)}/previews/publish`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ confirmed: true }),
    },
  );
}

export async function publishCommentPreview(
  runId: string,
  previewId: number,
): Promise<ApiResponse<CommentPreview>> {
  return fetchJson<CommentPreview>(
    `${BASE_URL}/api/reviews/${encodeURIComponent(runId)}/previews/${previewId}/publish`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ confirmed: true }),
    },
  );
}

export async function getRepositoryIndexStatus(owner: string, repo: string, ref: string): Promise<ApiResponse<RepositoryIndexStatus>> {
  const query = /^[0-9a-f]{40}$/.test(ref) ? `commitSha=${encodeURIComponent(ref)}` : `ref=${encodeURIComponent(ref)}`;
  const response = await fetchJson<RepositoryIndexStatus>(`${BASE_URL}/api/repositories/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/index-status?${query}`);
  if (response.httpStatus === 404) {
    return {
      success: true,
      message: 'OK',
      data: { status: 'NOT_INDEXED' },
      httpStatus: 404,
    };
  }
  return response;
}
export async function requestRepositoryIndex(repoUrl: string, ref: string): Promise<ApiResponse<RepositoryIndexResponse>> { return fetchJson(`${BASE_URL}/api/repositories/index`, {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({repoUrl,ref})}); }
export async function requestRepositoryReindex(owner: string, repo: string, ref: string): Promise<ApiResponse<RepositoryIndexResponse>> { return fetchJson(`${BASE_URL}/api/repositories/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/reindex?ref=${encodeURIComponent(ref)}`, {method:'POST'}); }
export async function getRetrievalEvidence(runId:string, issueKey:string): Promise<ApiResponse<RetrievalEvidence[]>> { return fetchJson(`${BASE_URL}/api/reviews/${encodeURIComponent(runId)}/issues/${encodeURIComponent(issueKey)}/evidence`); }
export async function getRetrievalTrace(runId:string): Promise<ApiResponse<RetrievalTrace>> { return fetchJson(`${BASE_URL}/api/reviews/${encodeURIComponent(runId)}/retrieval`); }
