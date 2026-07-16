import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App';
import * as api from '../api/reviewTaskApi';
import type { ReviewTask } from '../types/reviewTask';

vi.mock('../api/reviewTaskApi', () => ({
  getHealth: vi.fn(), listReviewTasks: vi.fn(), getReviewTask: vi.fn(), getCommentPreviews: vi.fn(),
  getToolTrace: vi.fn(), getRepositoryIndexStatus: vi.fn(), requestRepositoryIndex: vi.fn(),
  requestRepositoryReindex: vi.fn(), getRetrievalEvidence: vi.fn(), updateCommentPreviewSelection: vi.fn(),
  publishSelectedCommentPreviews: vi.fn(),
}));

const task: ReviewTask = { id: 1, repoUrl: 'https://github.com/acme/repo', prNumber: 1, status: 'SUCCESS', summary: 'ok', riskLevel: 'LOW', errorMessage: null, createdAt: '2026-01-01', updatedAt: '2026-01-01', latestRunId: null, repositoryIndex: { status: 'QUEUED', commitSha: 'a'.repeat(40) }, issues: [{ id: 'I1', severity: 'LOW', category: 'TEST', source: 'MIMO', status: 'OPEN', filePath: 'src/a.ts', startLine: 1, endLine: 1, title: 'Issue one', description: 'd', recommendation: 'r' }] };
const taskTwo: ReviewTask = { ...task, id: 2, repoUrl: 'https://github.com/acme/other-repo', repositoryIndex: { status: 'QUEUED', commitSha: 'b'.repeat(40) } };

describe('App RAG orchestration', () => {
  beforeEach(() => {
    vi.useRealTimers();
    vi.mocked(api.getHealth).mockResolvedValue({ success: true, message: 'OK', data: { status: 'UP', service: 'x' } });
    vi.mocked(api.listReviewTasks).mockResolvedValue({ success: true, message: 'OK', data: [task] });
    vi.mocked(api.getReviewTask).mockResolvedValue({ success: true, message: 'OK', data: task });
    vi.mocked(api.getRepositoryIndexStatus).mockResolvedValue({ success: true, message: 'OK', data: { status: 'READY' } });
    vi.mocked(api.getRetrievalEvidence).mockResolvedValue({ success: true, message: 'OK', data: [] });
  });

  it('polls immediately, stops at READY, and lazily caches evidence', async () => {
    vi.mocked(api.getRepositoryIndexStatus)
      .mockResolvedValueOnce({ success: true, message: 'OK', data: { status: 'QUEUED' } })
      .mockResolvedValueOnce({ success: true, message: 'OK', data: { status: 'INDEXING' } })
      .mockResolvedValueOnce({ success: true, message: 'OK', data: { status: 'READY' } });
    vi.useFakeTimers();
    render(<App />);
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    fireEvent.click(screen.getByText('https://github.com/acme/repo'));
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    expect(api.getRepositoryIndexStatus).toHaveBeenCalledTimes(1);
    await act(async () => { await vi.advanceTimersByTimeAsync(1999); });
    expect(api.getRepositoryIndexStatus).toHaveBeenCalledTimes(1);
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    await act(async () => { await vi.advanceTimersByTimeAsync(1); await Promise.resolve(); });
    expect(api.getRepositoryIndexStatus).toHaveBeenCalledTimes(2);
    await act(async () => { await vi.advanceTimersByTimeAsync(1999); });
    expect(api.getRepositoryIndexStatus).toHaveBeenCalledTimes(2);
    await act(async () => { await vi.advanceTimersByTimeAsync(1); });
    await act(async () => { await Promise.resolve(); });
    expect(api.getRepositoryIndexStatus).toHaveBeenCalledTimes(3);
    await act(async () => { await vi.advanceTimersByTimeAsync(4000); });
    expect(api.getRepositoryIndexStatus).toHaveBeenCalledTimes(3);
    expect(api.getRetrievalEvidence).not.toHaveBeenCalled();
    vi.useRealTimers();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /expand issue details panel/i }));
    await user.click(screen.getByRole('button', { name: /expand issue one/i }));
    await waitFor(() => expect(api.getRetrievalEvidence).toHaveBeenCalledTimes(1));
    await user.click(screen.getByRole('button', { name: /collapse issue one/i }));
    await user.click(screen.getByRole('button', { name: /expand issue one/i }));
    expect(api.getRetrievalEvidence).toHaveBeenCalledTimes(1);
  });

  it('shows FAILED with a safe retry after an index request rejection', async () => {
    vi.mocked(api.listReviewTasks).mockResolvedValue({ success: true, message: 'OK', data: [{ ...task, repositoryIndex: { status: 'NOT_INDEXED' } }] });
    vi.mocked(api.getReviewTask).mockResolvedValue({ success: true, message: 'OK', data: { ...task, repositoryIndex: { status: 'NOT_INDEXED' } } });
    vi.mocked(api.requestRepositoryIndex).mockRejectedValue(new Error('secret backend detail'));
    const user = userEvent.setup();
    render(<App />);
    await user.click(await screen.findByText('https://github.com/acme/repo'));
    await user.click(await screen.findByRole('button', { name: 'Index' }));
    await waitFor(() => expect(api.requestRepositoryIndex).toHaveBeenCalledTimes(1));
  });

  it('starts polling after a successful index request from NOT_INDEXED', async () => {
    vi.useFakeTimers();
    const notIndexed = { ...task, repositoryIndex: { status: 'NOT_INDEXED' as const } };
    vi.mocked(api.listReviewTasks).mockResolvedValue({ success: true, message: 'OK', data: [notIndexed] });
    vi.mocked(api.getReviewTask).mockResolvedValue({ success: true, message: 'OK', data: notIndexed });
    vi.mocked(api.requestRepositoryIndex).mockResolvedValue({ success: true, message: 'OK', data: { jobId: 1, status: 'QUEUED', requestedRef: 'HEAD' } });
    vi.mocked(api.getRepositoryIndexStatus)
      .mockResolvedValueOnce({ success: true, message: 'OK', data: { status: 'QUEUED' } })
      .mockResolvedValueOnce({ success: true, message: 'OK', data: { status: 'READY' } });
    render(<App />);
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    fireEvent.click(screen.getByText('https://github.com/acme/repo'));
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    vi.mocked(api.getRepositoryIndexStatus).mockClear();
    fireEvent.click(screen.getByRole('button', { name: 'Index' }));
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    expect(api.getRepositoryIndexStatus).toHaveBeenCalledTimes(1);
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
    expect(api.getRepositoryIndexStatus).toHaveBeenCalledTimes(2);
    await act(async () => { await vi.advanceTimersByTimeAsync(4000); });
    expect(api.getRepositoryIndexStatus).toHaveBeenCalledTimes(2);
    vi.useRealTimers();
  });

  it('ignores a deferred old poll response after task switch and clears timers on unmount', async () => {
    vi.mocked(api.listReviewTasks).mockResolvedValue({ success: true, message: 'OK', data: [task, taskTwo] });
    vi.mocked(api.getReviewTask).mockImplementation(async (id) => ({ success: true, message: 'OK', data: id === 1 ? task : taskTwo }));
    const pollResolvers = new Map<string, (value: any) => void>();
    vi.mocked(api.getRepositoryIndexStatus).mockImplementation((owner, repo) => new Promise((resolve) => { pollResolvers.set(`${owner}/${repo}`, resolve); }));
    const { unmount } = render(<App />);
    const user = userEvent.setup();
    await user.click(await screen.findByText('https://github.com/acme/repo'));
    fireEvent.click(screen.getByText('https://github.com/acme/other-repo'));
    vi.useFakeTimers();
    await act(async () => { await Promise.resolve(); });
    pollResolvers.get('acme/repo')!({ success: true, message: 'OK', data: { status: 'FAILED', errorMessage: 'stale' } });
    await act(async () => { await Promise.resolve(); });
    expect(screen.queryByText('stale')).not.toBeInTheDocument();
    pollResolvers.get('acme/other-repo')!({ success: true, message: 'OK', data: { status: 'READY' } });
    await act(async () => { await Promise.resolve(); });
    expect(screen.getByText('Ready')).toBeInTheDocument();
    const callsBeforeUnmount = vi.mocked(api.getRepositoryIndexStatus).mock.calls.length;
    unmount();
    await act(async () => { await vi.advanceTimersByTimeAsync(4000); });
    expect(vi.mocked(api.getRepositoryIndexStatus).mock.calls.length).toBe(callsBeforeUnmount);
    vi.useRealTimers();
  });

  it('ignores deferred evidence from a previous task with the same issue key', async () => {
    vi.mocked(api.listReviewTasks).mockResolvedValue({ success: true, message: 'OK', data: [task, taskTwo] });
    vi.mocked(api.getReviewTask).mockImplementation(async (id) => ({ success: true, message: 'OK', data: id === 1 ? task : taskTwo }));
    const resolverCalls: Array<{ taskId: number; resolve: (value: any) => void }> = [];
    vi.mocked(api.getRetrievalEvidence).mockImplementation((taskId) => new Promise((resolve) => { resolverCalls.push({ taskId, resolve }); }));
    const user = userEvent.setup();
    render(<App />);
    vi.mocked(api.getRepositoryIndexStatus).mockClear();
    await user.click(await screen.findByText('https://github.com/acme/repo'));
    await user.click(screen.getByRole('button', { name: /expand issue details panel/i }));
    await user.click(screen.getByRole('button', { name: /expand issue one/i }));
    await user.click(screen.getByText('https://github.com/acme/other-repo'));
    await user.click(screen.getByRole('button', { name: /expand issue one/i }));
    await waitFor(() => expect(vi.mocked(api.getRetrievalEvidence).mock.calls).toEqual(expect.arrayContaining([[1, 'I1'], [2, 'I1']])));
    resolverCalls.find((call) => call.taskId === 1)!.resolve({ success: true, message: 'OK', data: [{ chunkId: 'old', excerpt: 'stale evidence' }] });
    resolverCalls.find((call) => call.taskId === 2)!.resolve({ success: true, message: 'OK', data: [{ chunkId: 'new', excerpt: 'current evidence' }] });
    await waitFor(() => expect(screen.getByText('current evidence')).toBeInTheDocument());
    expect(screen.queryByText('stale evidence')).not.toBeInTheDocument();
  });
});
