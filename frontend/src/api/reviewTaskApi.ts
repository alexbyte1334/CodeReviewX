import type { ApiResponse } from '../types/apiResponse';
import type { CreateReviewTaskRequest, HealthData, ReviewTask } from '../types/reviewTask';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

async function fetchJson<T>(url: string, options?: RequestInit): Promise<ApiResponse<T>> {
  const response = await fetch(url, options);
  const json: ApiResponse<T> = await response.json();
  return json;
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
