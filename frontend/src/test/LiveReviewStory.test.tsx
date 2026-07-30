import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LiveReviewStory } from '../components/LiveReviewStory';
import type { DemoSnapshot } from '../types/demoRun';
import * as api from '../api/demoRunApi';

vi.mock('../api/demoRunApi', () => ({
  DEMO_API_BASE: 'https://api.example.test',
  loadRecordedRun: vi.fn(),
  createDemoRun: vi.fn(),
  getDemoSnapshot: vi.fn(),
  decideDemoRun: vi.fn(),
}));

function snapshot(mode: 'LIVE' | 'REPLAY' = 'REPLAY', status = 'READY'): DemoSnapshot {
  return {
    runId: mode === 'LIVE' ? '1f5d42e3-2030-4fa4-bc4d-8b35d4c7baf2' : 'recorded-v1',
    scenarioId: 'sql-injection-pr',
    mode,
    status,
    decision: null,
    replayReason: mode === 'REPLAY' ? 'Recorded, sanitized run.' : null,
    diffText: 'diff --git a/src/App.java b/src/App.java\n+++ b/src/App.java\n@@ -1,1 +1,1 @@\n-old\n+unsafe();',
    steps: [
      'PR ingest', 'Repository index', 'Hybrid RAG', 'AI plan',
      'AI review', 'Evidence gate', 'Human review',
    ].map((label, index) => ({
      id: `STEP_${index}`,
      label,
      status: status === 'RUNNING' && index > 1 ? 'PENDING' : index === 6 ? status : 'SUCCESS',
      durationMs: index === 6 ? null : 100,
      summary: `${label} state`,
      errorCode: null,
    })),
    findings: [{
      issueKey: 'SQL-1',
      severity: 'HIGH',
      category: 'SECURITY',
      filePath: 'src/App.java',
      line: 1,
      title: 'Unsafe query',
      description: 'Unsafe',
      recommendation: 'Bind parameters',
    }],
    evidence: [{
      issueKey: 'SQL-1',
      citationLabel: 'E1',
      path: 'src/App.java',
      startLine: 1,
      endLine: 1,
      excerpt: 'unsafe();',
      rank: 1,
      score: 0.9,
    }],
    toolTrace: [{
      sequence: 1,
      toolName: 'rag.retrieve.hybrid',
      status: 'SUCCESS',
      inputSummary: 'query=SQL injection',
      outputSummary: 'matches=1',
      errorCode: null,
      durationMs: 42,
    }],
    commentPreviews: [{
      id: 11,
      issueKey: 'SQL-1',
      filePath: 'src/App.java',
      line: 1,
      severity: 'HIGH',
      category: 'SECURITY',
      body: 'Use a bound parameter.',
      selected: true,
      publishStatus: 'NOT_PUBLISHED',
      githubUrl: null,
    }],
    events: [{
      sequence: 7,
      type: 'TOOL_COMPLETED',
      step: 'HYBRID_RAG',
      status: 'SUCCESS',
      summary: 'Retrieved evidence.',
      errorCode: null,
      durationMs: 42,
      createdAt: '2026-07-30T10:00:00',
    }],
    publishedCommentUrl: null,
    createdAt: '2026-07-30T10:00:00',
    updatedAt: '2026-07-30T10:00:01',
  };
}

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  onmessage: (() => void) | null = null;
  onerror: (() => void) | null = null;
  listeners = new Map<string, () => void>();
  closed = false;

  constructor(readonly url: string) {
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: EventListenerOrEventListenerObject) {
    this.listeners.set(type, listener as () => void);
  }

  close() {
    this.closed = true;
  }
}

describe('LiveReviewStory', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
    FakeEventSource.instances = [];
    window.history.replaceState(null, '', '/');
    vi.stubGlobal('EventSource', FakeEventSource);
    vi.mocked(api.loadRecordedRun).mockResolvedValue(snapshot('REPLAY'));
  });

  it('uses explicit replay mode and records rejection locally', async () => {
    const user = userEvent.setup();
    render(<LiveReviewStory />);

    expect(await screen.findByRole('heading', { name: 'Recorded Review Story' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Review comments' }));
    await user.click(screen.getByRole('button', { name: 'Reject previews' }));

    expect(screen.getByText(/replay decision recorded locally/i)).toBeInTheDocument();
    expect(api.decideDemoRun).not.toHaveBeenCalled();
  });

  it('restores a terminal live run from the URL and sends approval', async () => {
    const user = userEvent.setup();
    const live = snapshot('LIVE');
    const approved = { ...live, decision: 'APPROVE_PREVIEW' };
    window.history.replaceState(null, '', `/?runId=${live.runId}`);
    vi.mocked(api.getDemoSnapshot).mockResolvedValue(live);
    vi.mocked(api.decideDemoRun).mockResolvedValue(approved);

    render(<LiveReviewStory />);

    expect(await screen.findByRole('heading', { name: 'Live Review Run' })).toBeInTheDocument();
    await user.click(screen.getByText(/safe tool trace/i));
    const trace = document.querySelector('.story-trace span');
    expect(trace?.textContent).toContain('Input: query=SQL injection');
    expect(trace?.textContent).toContain('Output: matches=1');
    await user.click(screen.getByRole('button', { name: 'Review comments' }));
    const dialog = screen.getByRole('dialog', { name: 'Human review' });
    await user.click(within(dialog).getByRole('button', { name: 'Approve 1 preview' }));

    expect(api.decideDemoRun).toHaveBeenCalledWith(
      live.runId, 'APPROVE_PREVIEW', [11],
    );
    expect(screen.getByText(/github publishing is owner-controlled/i)).toBeInTheDocument();
  });

  it('reconnects SSE from the latest durable sequence', async () => {
    const live = snapshot('LIVE', 'RUNNING');
    window.history.replaceState(null, '', `/?runId=${live.runId}`);
    vi.mocked(api.getDemoSnapshot).mockResolvedValue(live);
    render(<LiveReviewStory />);
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    expect(FakeEventSource.instances[0].url).toContain('afterSequence=7');

    let retryHandler: (() => void) | undefined;
    vi.spyOn(window, 'setTimeout').mockImplementation((handler) => {
      retryHandler = handler as () => void;
      return 1;
    });
    FakeEventSource.instances[0].onerror?.();
    expect(retryHandler).toBeDefined();
    await act(async () => retryHandler?.());

    expect(FakeEventSource.instances).toHaveLength(2);
    expect(FakeEventSource.instances[0].closed).toBe(true);
    expect(FakeEventSource.instances[1].url).toContain('afterSequence=7');
  });

  it('falls back visibly when a saved live run cannot be restored', async () => {
    const runId = '1f5d42e3-2030-4fa4-bc4d-8b35d4c7baf2';
    window.history.replaceState(null, '', `/?runId=${runId}`);
    vi.mocked(api.getDemoSnapshot).mockRejectedValue(new Error('API unavailable'));

    render(<LiveReviewStory />);

    expect(await screen.findByRole('heading', { name: 'Recorded Review Story' })).toBeInTheDocument();
    expect(screen.getByText(/live execution could not be restored: API unavailable/i)).toBeInTheDocument();
    expect(screen.getByText('Replay is explicit.')).toBeInTheDocument();
  });
});
