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
  publishSelectedCommentPreviews: vi.fn(), getRetrievalTrace: vi.fn(),
}));

const ingestionSummary = (headSha: string) => ({ headSha, baseSha: 'c'.repeat(40), changedFiles: 1, additions: 2, deletions: 1, truncated: false });
const task: ReviewTask = { id: 1, repoUrl: 'https://github.com/acme/repo', prNumber: 1, status: 'SUCCESS', summary: 'ok', riskLevel: 'LOW', errorMessage: null, createdAt: '2026-01-01', updatedAt: '2026-01-01', latestRunId: null, reviewMode: 'GITHUB_PR', ingestionSummary: ingestionSummary('a'.repeat(40)), issues: [{ id: 'I1', severity: 'LOW', category: 'TEST', source: 'MIMO', status: 'OPEN', filePath: 'src/a.ts', startLine: 1, endLine: 1, title: 'Issue one', description: 'd', recommendation: 'r' }] };
const taskTwo: ReviewTask = { ...task, id: 2, repoUrl: 'https://github.com/acme/other-repo', ingestionSummary: ingestionSummary('b'.repeat(40)) };

describe('App RAG orchestration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useRealTimers();
    vi.mocked(api.getHealth).mockResolvedValue({ success: true, message: 'OK', data: { status: 'UP', service: 'x' } });
    vi.mocked(api.listReviewTasks).mockResolvedValue({ success: true, message: 'OK', data: [task] });
    vi.mocked(api.getReviewTask).mockResolvedValue({ success: true, message: 'OK', data: task });
    vi.mocked(api.getRepositoryIndexStatus).mockResolvedValue({ success: true, message: 'OK', data: { status: 'READY' } });
    vi.mocked(api.getRetrievalEvidence).mockResolvedValue({ success: true, message: 'OK', data: [] });
    vi.mocked(api.getCommentPreviews).mockResolvedValue({ success: true, message: 'OK', data: { items: [] } });
    vi.mocked(api.getToolTrace).mockResolvedValue({ success: true, message: 'OK', data: { items: [] } });
    vi.mocked(api.getRetrievalTrace).mockResolvedValue({ success: true, message: 'OK', data: { degraded: false, latencyMs: 1, candidateCount: 1, selectedCount: 1, evidence: [] } });
  });

  it('polls immediately, stops at READY, and lazily caches evidence', async () => {
    vi.mocked(api.getRepositoryIndexStatus)
      .mockResolvedValueOnce({ success: true, message: 'OK', data: { status: 'QUEUED' } })
      .mockResolvedValueOnce({ success: true, message: 'OK', data: { status: 'RUNNING' } })
      .mockResolvedValueOnce({ success: true, message: 'OK', data: { status: 'READY' } });
    vi.useFakeTimers();
    render(<App />);
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    fireEvent.click(screen.getByText('https://github.com/acme/repo'));
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    expect(api.getRepositoryIndexStatus).toHaveBeenCalledTimes(1);
    expect(api.getRepositoryIndexStatus).toHaveBeenLastCalledWith('acme', 'repo', 'a'.repeat(40));
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

  it('shows a safe error and retry after a non-409 index request failure', async () => {
    vi.mocked(api.getRepositoryIndexStatus).mockResolvedValue({ success: true, message: 'OK', data: { status: 'NOT_INDEXED' } });
    vi.mocked(api.requestRepositoryIndex).mockResolvedValue({ success: false, message: 'Index request failed safely.', data: null, httpStatus: 500 });
    const user = userEvent.setup();
    render(<App />);
    await user.click(await screen.findByText('https://github.com/acme/repo'));
    await user.click(await screen.findByRole('button', { name: 'Index' }));
    await waitFor(() => expect(api.requestRepositoryIndex).toHaveBeenCalledTimes(1));
    expect(screen.getByText('Index request failed safely.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reindex' })).toBeEnabled();
  });

  it('shows the safe error returned by a FAILED index job', async () => {
    vi.mocked(api.getRepositoryIndexStatus).mockResolvedValue({
      success: true,
      message: 'OK',
      data: { status: 'FAILED', errorMessage: 'Repository checkout failed safely.' },
    });
    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByText(task.repoUrl));

    expect(await screen.findByText('Repository checkout failed safely.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reindex' })).toBeEnabled();
  });

  it('starts polling after a successful index request from NOT_INDEXED', async () => {
    vi.useFakeTimers();
    vi.mocked(api.requestRepositoryIndex).mockResolvedValue({ success: true, message: 'OK', data: { jobId: 1, status: 'QUEUED', requestedRef: 'a'.repeat(40) } });
    vi.mocked(api.getRepositoryIndexStatus)
      .mockResolvedValueOnce({ success: true, message: 'OK', data: { status: 'NOT_INDEXED' } })
      .mockResolvedValueOnce({ success: true, message: 'OK', data: { status: 'QUEUED' } })
      .mockResolvedValueOnce({ success: true, message: 'OK', data: { status: 'READY' } });
    render(<App />);
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    fireEvent.click(screen.getByText('https://github.com/acme/repo'));
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    vi.mocked(api.getRepositoryIndexStatus).mockClear();
    fireEvent.click(screen.getByRole('button', { name: 'Index' }));
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    expect(api.requestRepositoryIndex).toHaveBeenCalledWith(task.repoUrl, 'a'.repeat(40));
    expect(api.getRepositoryIndexStatus).toHaveBeenCalledTimes(1);
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
    expect(api.getRepositoryIndexStatus).toHaveBeenCalledTimes(2);
    await act(async () => { await vi.advanceTimersByTimeAsync(4000); });
    expect(api.getRepositoryIndexStatus).toHaveBeenCalledTimes(2);
    vi.useRealTimers();
  });

  it('keeps Index disabled until the initial status request resolves', async () => {
    let resolveStatus!: (value: any) => void;
    vi.mocked(api.getRepositoryIndexStatus).mockImplementation(() => new Promise((resolve) => { resolveStatus = resolve; }));
    const user = userEvent.setup();
    render(<App />);
    await user.click(await screen.findByText(task.repoUrl));
    const indexButton = await screen.findByRole('button', { name: 'Index' });
    expect(indexButton).toBeDisabled();
    await user.click(indexButton);
    expect(api.requestRepositoryIndex).not.toHaveBeenCalled();
    resolveStatus({ success: true, message: 'OK', data: { status: 'NOT_INDEXED' } });
    await waitFor(() => expect(indexButton).toBeEnabled());
  });

  it('keeps transport failures distinct and preserves the Index retry action', async () => {
    vi.mocked(api.getRepositoryIndexStatus).mockRejectedValue(new TypeError('Failed to fetch'));
    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByText(task.repoUrl));

    const indexButton = await screen.findByRole('button', { name: 'Index' });
    await waitFor(() => expect(indexButton).toBeEnabled());
    expect(screen.getByText('Unable to load index status.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reindex' })).not.toBeInTheDocument();
  });

  it('prevents duplicate index requests and refreshes status after a 409', async () => {
    let resolvePost!: (value: any) => void;
    vi.mocked(api.getRepositoryIndexStatus).mockResolvedValue({ success: true, message: 'OK', data: { status: 'NOT_INDEXED' } });
    vi.mocked(api.requestRepositoryIndex).mockImplementation(() => new Promise((resolve) => { resolvePost = resolve; }));
    const user = userEvent.setup();
    render(<App />);
    await user.click(await screen.findByText(task.repoUrl));
    const indexButton = await screen.findByRole('button', { name: 'Index' });
    await waitFor(() => expect(indexButton).toBeEnabled());
    await user.click(indexButton);
    await user.click(indexButton);
    expect(api.requestRepositoryIndex).toHaveBeenCalledTimes(1);
    resolvePost({ success: false, message: 'Already queued', data: null, httpStatus: 409 });
    await waitFor(() => expect(api.getRepositoryIndexStatus).toHaveBeenCalledTimes(2));
  });

  it('disables index actions and never falls back to HEAD without a valid head SHA', async () => {
    const missingSha = { ...task, ingestionSummary: { ...ingestionSummary(''), headSha: null } };
    vi.mocked(api.listReviewTasks).mockResolvedValue({ success: true, message: 'OK', data: [missingSha] });
    vi.mocked(api.getReviewTask).mockResolvedValue({ success: true, message: 'OK', data: missingSha });
    const user = userEvent.setup();
    render(<App />);
    await user.click(await screen.findByText(task.repoUrl));
    const indexButton = await screen.findByRole('button', { name: 'Index' });
    expect(indexButton).toBeDisabled();
    expect(screen.getByText('Repository head SHA is unavailable.')).toBeInTheDocument();
    await user.click(indexButton);
    expect(api.requestRepositoryIndex).not.toHaveBeenCalled();
    expect(api.getRepositoryIndexStatus).not.toHaveBeenCalled();
  });

  it('loads degraded state from the selected run and ignores a stale trace response', async () => {
    const runTask = { ...task, latestRunId: 11 };
    const runTaskTwo = { ...taskTwo, latestRunId: 22 };
    vi.mocked(api.listReviewTasks).mockResolvedValue({ success: true, message: 'OK', data: [runTask, runTaskTwo] });
    vi.mocked(api.getReviewTask).mockImplementation(async (id) => ({ success: true, message: 'OK', data: id === 1 ? runTask : runTaskTwo }));
    const traceResolvers = new Map<number, (value: any) => void>();
    vi.mocked(api.getRetrievalTrace).mockImplementation((runId) => new Promise((resolve) => traceResolvers.set(runId, resolve)));
    const user = userEvent.setup();
    render(<App />);
    await user.click(await screen.findByText(runTask.repoUrl));
    await waitFor(() => expect(api.getRetrievalTrace).toHaveBeenCalledWith(11));
    fireEvent.click(screen.getByText(runTaskTwo.repoUrl));
    await waitFor(() => expect(api.getRetrievalTrace).toHaveBeenCalledWith(22));
    traceResolvers.get(11)!({ success: true, message: 'OK', data: { degraded: true, latencyMs: 1, candidateCount: 0, selectedCount: 0, evidence: [] } });
    traceResolvers.get(22)!({ success: true, message: 'OK', data: { degraded: false, latencyMs: 1, candidateCount: 1, selectedCount: 1, evidence: [] } });
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    expect(screen.queryByText('Degraded retrieval')).not.toBeInTheDocument();
  });

  it('shows degraded retrieval from the selected run only', async () => {
    const runTask = { ...task, latestRunId: 11 };
    vi.mocked(api.listReviewTasks).mockResolvedValue({ success: true, message: 'OK', data: [runTask] });
    vi.mocked(api.getReviewTask).mockResolvedValue({ success: true, message: 'OK', data: runTask });
    vi.mocked(api.getRetrievalTrace).mockResolvedValue({ success: true, message: 'OK', data: { degraded: true, degradedReason: 'RERANK_UNAVAILABLE', latencyMs: 1, candidateCount: 1, selectedCount: 1, evidence: [] } });
    const user = userEvent.setup();
    render(<App />);
    await user.click(await screen.findByText(runTask.repoUrl));
    expect(await screen.findByText('Degraded retrieval')).toBeInTheDocument();
  });

  it('clears degraded state when the current run has no RAG retrieval trace', async () => {
    const runTask = { ...task, latestRunId: 11 };
    const runTaskTwo = { ...taskTwo, latestRunId: 22 };
    vi.mocked(api.listReviewTasks).mockResolvedValue({ success: true, message: 'OK', data: [runTask, runTaskTwo] });
    vi.mocked(api.getReviewTask).mockImplementation(async (id) => ({ success: true, message: 'OK', data: id === 1 ? runTask : runTaskTwo }));
    vi.mocked(api.getRetrievalTrace)
      .mockResolvedValueOnce({ success: true, message: 'OK', data: { degraded: true, latencyMs: 1, candidateCount: 0, selectedCount: 0, evidence: [] } })
      .mockResolvedValueOnce({ success: false, message: 'Retrieval trace not found', data: null });
    const user = userEvent.setup();
    render(<App />);
    await user.click(await screen.findByText(runTask.repoUrl));
    expect(await screen.findByText('Degraded retrieval')).toBeInTheDocument();
    await user.click(screen.getByText(runTaskTwo.repoUrl));
    await waitFor(() => expect(api.getRetrievalTrace).toHaveBeenCalledWith(22));
    expect(screen.queryByText('Degraded retrieval')).not.toBeInTheDocument();
  });

  it('reindexes READY content using the exact head SHA', async () => {
    vi.mocked(api.getRepositoryIndexStatus).mockResolvedValue({ success: true, message: 'OK', data: { status: 'READY' } });
    vi.mocked(api.requestRepositoryReindex).mockResolvedValue({ success: true, message: 'OK', data: { jobId: 2, status: 'QUEUED', requestedRef: 'a'.repeat(40) } });
    const user = userEvent.setup();
    render(<App />);
    await user.click(await screen.findByText(task.repoUrl));
    await user.click(await screen.findByRole('button', { name: 'Reindex' }));
    expect(api.requestRepositoryReindex).toHaveBeenCalledWith('acme', 'repo', 'a'.repeat(40));
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

  it('ignores deferred comment previews from the previous task', async () => {
    const runTask = { ...task, latestRunId: 11 };
    const runTaskTwo = { ...taskTwo, latestRunId: 22 };
    vi.mocked(api.listReviewTasks).mockResolvedValue({ success: true, message: 'OK', data: [runTask, runTaskTwo] });
    vi.mocked(api.getReviewTask).mockImplementation(async (id) => ({ success: true, message: 'OK', data: id === 1 ? runTask : runTaskTwo }));
    const resolvers = new Map<number, (value: any) => void>();
    vi.mocked(api.getCommentPreviews).mockImplementation((runId) => new Promise((resolve) => resolvers.set(runId, resolve)));
    const user = userEvent.setup();
    render(<App />);
    await user.click(await screen.findByText(runTask.repoUrl));
    await user.click(screen.getByText(runTaskTwo.repoUrl));
    resolvers.get(22)!({ success: true, message: 'OK', data: { items: [] } });
    resolvers.get(11)!({ success: true, message: 'OK', data: { items: [{ id: 1, issueId: 'I1', filePath: 'old.ts', line: 1, draftBody: 'stale comment', selectedForPublish: false, publishStatus: 'NOT_PUBLISHED' }] } });
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    expect(screen.queryByText('stale comment')).not.toBeInTheDocument();
  });

  it('ignores deferred tool trace from the previous task', async () => {
    const runTask = { ...task, latestRunId: 11 };
    const runTaskTwo = { ...taskTwo, latestRunId: 22 };
    vi.mocked(api.listReviewTasks).mockResolvedValue({ success: true, message: 'OK', data: [runTask, runTaskTwo] });
    vi.mocked(api.getReviewTask).mockImplementation(async (id) => ({ success: true, message: 'OK', data: id === 1 ? runTask : runTaskTwo }));
    const resolvers = new Map<number, (value: any) => void>();
    vi.mocked(api.getToolTrace).mockImplementation((runId) => new Promise((resolve) => resolvers.set(runId, resolve)));
    const user = userEvent.setup();
    render(<App />);
    await user.click(await screen.findByText(runTask.repoUrl));
    await user.click(screen.getByText(runTaskTwo.repoUrl));
    resolvers.get(22)!({ success: true, message: 'OK', data: { items: [] } });
    resolvers.get(11)!({ success: true, message: 'OK', data: { items: [{ id: 1, toolName: 'stale.tool', status: 'SUCCESS', startedAt: '', finishedAt: '', durationMs: 1, outputSummary: null, errorCode: null }] } });
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    expect(screen.queryByText('stale.tool')).not.toBeInTheDocument();
  });
});
