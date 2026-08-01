import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  createReview, getCommentPreviews, getHealth, getRepositoryIndexStatus,
  getRetrievalEvidence, publishSelectedCommentPreviews,
  requestRepositoryReindex, updateCommentPreviewSelection,
} from '../api/reviewApi';

describe('review API', () => {
  beforeEach(() => vi.stubGlobal('fetch', vi.fn()));
  afterEach(() => vi.unstubAllGlobals());

  it('creates an idempotent review through the UUID API', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({ json: async () => ({ success: true, message: 'OK', data: { runId: 'run-1' } }) } as Response);
    await createReview({ repositoryUrl: 'https://github.com/a/b', prNumber: 1 }, 'key-1');
    const [url, options] = vi.mocked(fetch).mock.calls[0];
    expect(url).toContain('/api/reviews');
    expect(options).toMatchObject({ method: 'POST', headers: expect.objectContaining({ 'Idempotency-Key': 'key-1' }) });
  });

  it('uses UUID routes for previews and evidence', async () => {
    vi.mocked(fetch).mockResolvedValue({ json: async () => ({ success: true, message: 'OK', data: { items: [] } }) } as Response);
    await getCommentPreviews('run-1');
    expect(vi.mocked(fetch).mock.calls[0][0]).toContain('/api/reviews/run-1/previews');
    await updateCommentPreviewSelection('run-1', [101]);
    expect(vi.mocked(fetch).mock.calls[1][0]).toContain('/api/reviews/run-1/previews/selection');
    await publishSelectedCommentPreviews('run-1');
    expect(vi.mocked(fetch).mock.calls[2][0]).toContain('/api/reviews/run-1/previews/publish');
    await getRetrievalEvidence('run-1', 'ISSUE-1');
    expect(vi.mocked(fetch).mock.calls[3][0]).toContain('/api/reviews/run-1/issues/ISSUE-1/evidence');
  });

  it('preserves readiness and index routes', async () => {
    vi.mocked(fetch).mockResolvedValue({ json: async () => ({ success: true, message: 'OK', data: null }) } as Response);
    await getHealth();
    await getRepositoryIndexStatus('o', 'r', 'a'.repeat(40));
    await requestRepositoryReindex('o', 'r', 'main');
    expect(vi.mocked(fetch).mock.calls[2][0]).toContain('/reindex?ref=main');
  });
});
