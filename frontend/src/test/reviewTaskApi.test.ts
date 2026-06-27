import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  createReviewTask,
  getCommentPreviews,
  getHealth,
  getToolTrace,
  listReviewTasks,
  publishSelectedCommentPreviews,
  updateCommentPreviewSelection,
} from '../api/reviewTaskApi';

describe('reviewTaskApi', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('getHealth returns success response', async () => {
    const mockResponse = { success: true, message: 'OK', data: { status: 'UP', service: 'backend-java' } };
    vi.mocked(fetch).mockResolvedValueOnce({
      json: async () => mockResponse,
    } as Response);

    const result = await getHealth();
    expect(result.success).toBe(true);
    expect(result.data?.status).toBe('UP');
  });

  it('handles success=false from API', async () => {
    const mockResponse = { success: false, message: 'Something went wrong', data: null };
    vi.mocked(fetch).mockResolvedValueOnce({
      json: async () => mockResponse,
    } as Response);

    const result = await listReviewTasks();
    expect(result.success).toBe(false);
    expect(result.message).toBe('Something went wrong');
    expect(result.data).toBeNull();
  });

  it('createReviewTask sends correct payload', async () => {
    const mockResponse = {
      success: true, message: 'OK', data: {
        id: 1, repoUrl: 'https://github.com/a/b', prNumber: 1,
        status: 'SUCCESS', summary: null, riskLevel: 'LOW',
        errorMessage: null, createdAt: '', updatedAt: '', issues: [],
      },
    };
    vi.mocked(fetch).mockResolvedValueOnce({
      json: async () => mockResponse,
    } as Response);

    const result = await createReviewTask({ repoUrl: 'https://github.com/a/b', prNumber: 1 });
    expect(result.success).toBe(true);
    expect(result.data?.id).toBe(1);

    const callArgs = vi.mocked(fetch).mock.calls[0];
    expect(callArgs[1]?.method).toBe('POST');
    const body = JSON.parse(callArgs[1]?.body as string);
    expect(body.repoUrl).toBe('https://github.com/a/b');
    expect(body.prNumber).toBe(1);
  });

  it('getCommentPreviews reads run comment previews', async () => {
    const mockResponse = { success: true, message: 'OK', data: { items: [] } };
    vi.mocked(fetch).mockResolvedValueOnce({
      json: async () => mockResponse,
    } as Response);

    const result = await getCommentPreviews(11);

    expect(result.success).toBe(true);
    expect(vi.mocked(fetch).mock.calls[0][0]).toContain('/api/review-runs/11/comment-previews');
  });

  it('getToolTrace reads run trace timeline', async () => {
    const mockResponse = { success: true, message: 'OK', data: { items: [] } };
    vi.mocked(fetch).mockResolvedValueOnce({
      json: async () => mockResponse,
    } as Response);

    const result = await getToolTrace(11);

    expect(result.success).toBe(true);
    expect(vi.mocked(fetch).mock.calls[0][0]).toContain('/api/review-runs/11/trace');
  });

  it('updateCommentPreviewSelection sends selected preview ids', async () => {
    const mockResponse = { success: true, message: 'OK', data: { items: [] } };
    vi.mocked(fetch).mockResolvedValueOnce({
      json: async () => mockResponse,
    } as Response);

    await updateCommentPreviewSelection(11, [101, 102]);

    const callArgs = vi.mocked(fetch).mock.calls[0];
    expect(callArgs[1]?.method).toBe('PATCH');
    expect(JSON.parse(callArgs[1]?.body as string)).toEqual({ selectedPreviewIds: [101, 102] });
  });

  it('publishSelectedCommentPreviews sends confirmed publish request', async () => {
    const mockResponse = { success: true, message: 'OK', data: { items: [] } };
    vi.mocked(fetch).mockResolvedValueOnce({
      json: async () => mockResponse,
    } as Response);

    await publishSelectedCommentPreviews(11);

    const callArgs = vi.mocked(fetch).mock.calls[0];
    expect(callArgs[1]?.method).toBe('POST');
    expect(JSON.parse(callArgs[1]?.body as string)).toEqual({ confirmed: true });
  });
});
