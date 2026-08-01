import type { ApiResponse } from '../types/apiResponse';
import type {
  CommentPreview,
  CommentPreviewListResponse,
  CreateReviewTaskRequest,
  HealthData,
  ReviewTask,
  ToolTraceListResponse,
  RepositoryIndexStatus, RetrievalTrace, RetrievalEvidence, RepositoryIndexResponse,
} from '../types/reviewTask';


const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');

export interface ReviewApiEvent { sequence: number; type: string; status: string; summary: string | null; errorCode: string | null; }
export interface ReviewApiSnapshot {
  runId: string; status: string; mode: 'LIVE' | 'REPLAY'; repositoryUrl: string; prNumber: number;
  taskId: number; reviewRunId: number; review: ReviewTask; events: ReviewApiEvent[];
  errorCode: string | null; errorMessage: string | null;
}

export async function createReview(request: { repositoryUrl: string; prNumber: number; diffText?: string }, idempotencyKey: string): Promise<ApiResponse<ReviewApiSnapshot>> {
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

export async function createReviewTask(
  payload: CreateReviewTaskRequest,
): Promise<ApiResponse<ReviewTask>> {
  return fetchJson<ReviewTask>(`${BASE_URL}/api/review-tasks`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export async function listReviewTasks(): Promise<ApiResponse<ReviewTask[]>> {
  return fetchJson<ReviewTask[]>(`${BASE_URL}/api/review-tasks`);
}

export async function getReviewTask(id: number): Promise<ApiResponse<ReviewTask>> {
  return fetchJson<ReviewTask>(`${BASE_URL}/api/review-tasks/${id}`);
}

export async function getCommentPreviews(
  runId: number,
): Promise<ApiResponse<CommentPreviewListResponse>> {
  return fetchJson<CommentPreviewListResponse>(
    `${BASE_URL}/api/review-runs/${runId}/comment-previews`,
  );
}

export async function getToolTrace(
  runId: number,
): Promise<ApiResponse<ToolTraceListResponse>> {
  return fetchJson<ToolTraceListResponse>(
    `${BASE_URL}/api/review-runs/${runId}/trace`,
  );
}

export async function updateCommentPreviewSelection(
  runId: number,
  selectedPreviewIds: number[],
): Promise<ApiResponse<CommentPreviewListResponse>> {
  return fetchJson<CommentPreviewListResponse>(
    `${BASE_URL}/api/review-runs/${runId}/comment-previews/selection`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ selectedPreviewIds }),
    },
  );
}

export async function publishSelectedCommentPreviews(
  runId: number,
): Promise<ApiResponse<CommentPreviewListResponse>> {
  return fetchJson<CommentPreviewListResponse>(
    `${BASE_URL}/api/review-runs/${runId}/comment-previews/publish-selected`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ confirmed: true }),
    },
  );
}

export async function publishCommentPreview(
  runId: number,
  previewId: number,
): Promise<ApiResponse<CommentPreview>> {
  return fetchJson<CommentPreview>(
    `${BASE_URL}/api/review-runs/${runId}/comment-previews/${previewId}/publish`,
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
export async function getRetrievalEvidence(taskId:number, issueKey:string): Promise<ApiResponse<RetrievalEvidence[]>> { return fetchJson(`${BASE_URL}/api/review-tasks/${taskId}/issues/${encodeURIComponent(issueKey)}/evidence`); }
export async function getRetrievalTrace(runId:number): Promise<ApiResponse<RetrievalTrace>> { return fetchJson(`${BASE_URL}/api/review-runs/${runId}/retrieval`); }
