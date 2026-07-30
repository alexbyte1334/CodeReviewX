import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from '../App';
import * as reviewTaskApi from '../api/reviewTaskApi';
import * as demoRunApi from '../api/demoRunApi';

vi.mock('../api/reviewTaskApi', () => ({
  getHealth: vi.fn(),
  listReviewTasks: vi.fn(),
  getReviewTask: vi.fn(),
  createReviewTask: vi.fn(),
  getCommentPreviews: vi.fn(),
  updateCommentPreviewSelection: vi.fn(),
  publishSelectedCommentPreviews: vi.fn(),
  publishCommentPreview: vi.fn(),
}));

vi.mock('../api/demoRunApi', () => ({
  DEMO_API_BASE: '',
  loadRecordedRun: vi.fn(),
  createDemoRun: vi.fn(),
  getDemoSnapshot: vi.fn(),
  decideDemoRun: vi.fn(),
}));

describe('App shell', () => {
  beforeEach(() => {
    document.documentElement.setAttribute('data-theme', 'light');
    vi.mocked(reviewTaskApi.getHealth).mockResolvedValue({
      success: true,
      message: 'OK',
      data: { status: 'UP', service: 'backend-java', reviewProvider: 'mimo' },
    });
    vi.mocked(reviewTaskApi.listReviewTasks).mockResolvedValue({ success: true, message: 'OK', data: [] });
    vi.mocked(demoRunApi.loadRecordedRun).mockResolvedValue({
      runId: 'recorded-v1', scenarioId: 'sql-injection-pr', mode: 'REPLAY', status: 'READY',
      decision: null, replayReason: 'Recorded, sanitized run.', diffText: '@@ -1,1 +1,1 @@\n-old\n+unsafe();',
      steps: [
        'PR ingest', 'Repository index', 'Hybrid RAG', 'AI plan', 'AI review', 'Evidence gate', 'Human review',
      ].map((label, index) => ({
        id: `STEP_${index}`, label, status: index === 6 ? 'READY' : 'SUCCESS',
        durationMs: index === 6 ? null : 100, summary: `${label} complete`, errorCode: null,
      })),
      findings: [{
        issueKey: 'ISSUE-1', severity: 'HIGH', category: 'SECURITY', filePath: 'src/App.java',
        line: 1, title: 'Unsafe query', description: 'Unsafe', recommendation: 'Bind parameters',
      }],
      evidence: [{
        issueKey: 'ISSUE-1', citationLabel: 'E1', path: 'src/App.java', startLine: 1,
        endLine: 1, excerpt: 'unsafe();', rank: 1, score: 0.9,
      }],
      toolTrace: [], commentPreviews: [{
        id: 1, issueKey: 'ISSUE-1', filePath: 'src/App.java', line: 1, severity: 'HIGH',
        category: 'SECURITY', body: 'Bind parameters.', selected: true,
        publishStatus: 'NOT_PUBLISHED', githubUrl: null,
      }],
      events: [], publishedCommentUrl: null, createdAt: '2026-07-30T10:00:00',
      updatedAt: '2026-07-30T10:00:01',
    });
  });

  it('renders product navigation and workspace header', async () => {
    render(<App />);

    expect(screen.getByText('CodeReviewX')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: /main navigation/i })).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: /main navigation/i });
    expect(within(nav).getByRole('button', { name: /review agent/i })).toBeInTheDocument();
    expect(within(nav).getByRole('button', { name: /review history/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /review workspace/i })).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Connected')).toBeInTheDocument();
    });
  });

  it('shows provider status widget in sidebar', async () => {
    vi.mocked(reviewTaskApi.getHealth).mockResolvedValue({
      success: true,
      message: 'OK',
      data: { status: 'UP', service: 'backend-java', reviewProvider: 'mimo', mimoConfigured: false },
    });
    render(<App />);

    expect(screen.getByLabelText(/provider status widget/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('MiMo keys required')).toBeInTheDocument();
    });
  });

  it('reveals limitations in secondary about panel', async () => {
    const user = userEvent.setup();
    render(<App />);

    expect(document.getElementById('panel-about-body')).toHaveAttribute('aria-hidden', 'true');

    await user.click(screen.getByRole('button', { name: /expand about & limits panel/i }));

    expect(screen.getByText(/manual diff or github pr diff input only/i)).toBeVisible();
    expect(screen.getByText(/mimo dual-agent review only/i)).toBeVisible();
    expect(screen.getByText(/no repository clone or github app integration yet/i)).toBeVisible();
  });

  it('renders collapsed workspace panels by default', async () => {
    render(<App />);

    expect(screen.getByRole('button', { name: /expand run review panel/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /expand review history panel/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /expand findings panel/i })).toBeInTheDocument();
    expect(screen.getByText(/choose a section below/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Connected')).toBeInTheDocument();
    });
  });

  it('toggles dark mode from the toolbar', async () => {
    const user = userEvent.setup();
    render(<App />);

    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    await user.click(screen.getByRole('button', { name: /switch to dark mode/i }));
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    await user.click(screen.getByRole('button', { name: /switch to light mode/i }));
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('shows backend unavailable warning when health check fails', async () => {
    vi.mocked(reviewTaskApi.getHealth).mockRejectedValue(new Error('down'));
    vi.mocked(reviewTaskApi.listReviewTasks).mockRejectedValue(new Error('down'));

    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => {
      const alerts = screen.getAllByRole('alert');
      expect(alerts.some((el) => /backend is unavailable/i.test(el.textContent ?? ''))).toBe(true);
    });

    await user.click(screen.getByRole('button', { name: /expand run review panel/i }));
    const reviewPanel = document.getElementById('panel-review-body');
    expect(reviewPanel).toBeTruthy();
    expect(within(reviewPanel!).getByRole('button', { name: /^run review$/i })).toBeDisabled();
  });

  it('opens the offline-safe interview story and human review step', async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getByRole('button', { name: /start live demo/i }));

    expect(await screen.findByRole('heading', { name: /recorded review story/i })).toBeInTheDocument();
    expect(screen.getByText(/offline-safe interview story/i)).toBeInTheDocument();
    expect(screen.getByText(/replay mode/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /review comments/i }));

    expect(screen.getByRole('dialog', { name: /human review/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /approve 1 preview/i })).toBeInTheDocument();
  });
});
