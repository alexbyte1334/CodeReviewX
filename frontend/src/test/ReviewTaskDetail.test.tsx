import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReviewTaskDetail } from '../components/ReviewTaskDetail';
import type { ReviewIssue, ReviewTask } from '../types/reviewTask';

const mockIssues: ReviewIssue[] = [
  {
    id: 'ISSUE-1',
    severity: 'HIGH',
    category: 'SECURITY',
    source: 'MOCK',
    status: 'OPEN',
    filePath: 'src/main/java/com/codereviewx/backend/review/controller/ReviewTaskController.java',
    startLine: 42,
    endLine: 48,
    title: 'Potential missing authorization check',
    description: 'This demo issue indicates that a sensitive endpoint should explicitly check authorization.',
    recommendation: 'Add an authorization guard before the business logic.',
  },
  {
    id: 'ISSUE-2',
    severity: 'MEDIUM',
    category: 'MAINTAINABILITY',
    source: 'MOCK',
    status: 'OPEN',
    filePath: 'src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java',
    startLine: 76,
    endLine: 93,
    title: 'Service method is doing too much work',
    description: 'This demo issue suggests the service method combines too many responsibilities.',
    recommendation: 'Extract validation and response mapping into smaller private methods.',
  },
  {
    id: 'ISSUE-3',
    severity: 'LOW',
    category: 'TEST',
    source: 'MOCK',
    status: 'OPEN',
    filePath: 'src/test/java/com/codereviewx/backend/review/service/ReviewTaskServiceTest.java',
    startLine: 21,
    endLine: 21,
    title: 'Missing negative-path coverage',
    description: 'This demo issue highlights that validation scenarios should be covered.',
    recommendation: 'Add tests for invalid request payloads and missing ReviewTask IDs.',
  },
];

const mockTask: ReviewTask = {
  id: 5,
  repoUrl: 'https://github.com/test/repo',
  prNumber: 99,
  status: 'SUCCESS',
  summary: 'Mock review completed for PR #99.',
  riskLevel: 'HIGH',
  errorMessage: null,
  createdAt: '2026-06-23T06:22:07.527724',
  updatedAt: '2026-06-23T06:22:07.528436',
  issues: mockIssues,
  issueSummary: {
    totalIssues: 3,
    highCount: 1,
    mediumCount: 1,
    lowCount: 1,
    riskLevel: 'HIGH',
  },
};

const mockTaskNoIssues: ReviewTask = {
  ...mockTask,
  id: 6,
  issues: [],
  issueSummary: {
    totalIssues: 0,
    highCount: 0,
    mediumCount: 0,
    lowCount: 0,
    riskLevel: 'NONE',
  },
};

const baseProps = {
  loading: false,
  error: null,
  expanded: true,
  onToggle: vi.fn(),
  summary: 'Review #5 · HIGH',
};

async function expandSummaryPanel(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: /expand review summary panel/i }));
}

async function expandIssuesPanel(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: /expand issue details panel/i }));
}

async function expandAllIssueCards(user: ReturnType<typeof userEvent.setup>) {
  const triggers = screen.getAllByRole('button', { name: /^expand /i });
  for (const trigger of triggers) {
    const label = trigger.getAttribute('aria-label') ?? '';
    if (label.startsWith('Expand ') && !label.includes('panel')) {
      await user.click(trigger);
    }
  }
}

describe('ReviewTaskDetail', () => {
  it('shows placeholder when no task selected', () => {
    render(<ReviewTaskDetail {...baseProps} task={null} summary="Select a review" />);
    expect(screen.getByText(/no review selected/i)).toBeInTheDocument();
    expect(
      screen.getByText(/select a review from history to inspect findings/i),
    ).toBeInTheDocument();
  });

  it('renders reviewed target in summary', async () => {
    const user = userEvent.setup();
    render(<ReviewTaskDetail {...baseProps} task={mockTask} />);
    await expandSummaryPanel(user);
    expect(screen.getByText(/https:\/\/github\.com\/test\/repo · PR #99/)).toBeInTheDocument();
  });

  it('renders review summary panel', async () => {
    const user = userEvent.setup();
    render(<ReviewTaskDetail {...baseProps} task={mockTask} />);
    await expandSummaryPanel(user);
    expect(screen.getByText('Severity Breakdown')).toBeInTheDocument();
    expect(screen.getByText('Provider Source')).toBeInTheDocument();
  });

  it('renders findings count in summary', async () => {
    const user = userEvent.setup();
    render(<ReviewTaskDetail {...baseProps} task={mockTask} />);
    await expandSummaryPanel(user);
    expect(screen.getAllByText('Findings').length).toBeGreaterThan(0);
    expect(screen.getByText('3', { selector: '.metric-card-value' })).toBeInTheDocument();
  });

  it('renders risk level HIGH when high issues exist', async () => {
    const user = userEvent.setup();
    render(<ReviewTaskDetail {...baseProps} task={mockTask} />);
    await expandSummaryPanel(user);
    const riskBadges = screen.getAllByText('HIGH');
    expect(riskBadges.length).toBeGreaterThan(0);
  });

  it('renders issue cards with title and recommendation', async () => {
    const user = userEvent.setup();
    render(<ReviewTaskDetail {...baseProps} task={mockTask} />);
    await expandIssuesPanel(user);
    await expandAllIssueCards(user);
    expect(screen.getByText('Potential missing authorization check')).toBeInTheDocument();
    expect(screen.getByText('Service method is doing too much work')).toBeInTheDocument();
    expect(screen.getByText('Missing negative-path coverage')).toBeInTheDocument();
    expect(screen.getByText('Add an authorization guard before the business logic.')).toBeInTheDocument();
  });

  it('renders severity badges', async () => {
    const user = userEvent.setup();
    render(<ReviewTaskDetail {...baseProps} task={mockTask} />);
    await expandIssuesPanel(user);
    const highBadges = screen.getAllByText('HIGH');
    expect(highBadges.length).toBeGreaterThan(0);
  });

  it('renders category badges', async () => {
    const user = userEvent.setup();
    render(<ReviewTaskDetail {...baseProps} task={mockTask} />);
    await expandIssuesPanel(user);
    expect(screen.getByText('SECURITY')).toBeInTheDocument();
    expect(screen.getByText('MAINTAINABILITY')).toBeInTheDocument();
    expect(screen.getByText('TEST')).toBeInTheDocument();
  });

  it('renders user-facing source labels for MIMO', async () => {
    const user = userEvent.setup();
    const mimoTask: ReviewTask = {
      ...mockTask,
      issues: mockIssues.map((issue) => ({ ...issue, source: 'MIMO' })),
    };
    render(<ReviewTaskDetail {...baseProps} task={mimoTask} />);
    await expandIssuesPanel(user);
    await expandAllIssueCards(user);
    const sourceBadges = screen.getAllByText('Xiaomi MiMo');
    expect(sourceBadges.length).toBeGreaterThanOrEqual(3);
  });

  it('renders user-facing source labels for MOCK', async () => {
    const user = userEvent.setup();
    render(<ReviewTaskDetail {...baseProps} task={mockTask} />);
    await expandIssuesPanel(user);
    await expandAllIssueCards(user);
    const sourceBadges = screen.getAllByText('Mock Provider');
    expect(sourceBadges.length).toBeGreaterThanOrEqual(3);
  });

  it('renders status badges', async () => {
    const user = userEvent.setup();
    render(<ReviewTaskDetail {...baseProps} task={mockTask} />);
    await expandIssuesPanel(user);
    await expandAllIssueCards(user);
    const statusBadges = screen.getAllByText('OPEN');
    expect(statusBadges.length).toBe(3);
  });

  it('renders location with file path and line range', async () => {
    const user = userEvent.setup();
    render(<ReviewTaskDetail {...baseProps} task={mockTask} />);
    await expandIssuesPanel(user);
    await user.click(screen.getByRole('button', { name: /expand potential missing authorization check/i }));
    expect(
      screen.getByText(
        'src/main/java/com/codereviewx/backend/review/controller/ReviewTaskController.java',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(':42–48')).toBeInTheDocument();
    expect(screen.getAllByText('Location').length).toBeGreaterThan(0);
  });

  it('renders description sections', async () => {
    const user = userEvent.setup();
    render(<ReviewTaskDetail {...baseProps} task={mockTask} />);
    await expandIssuesPanel(user);
    await expandAllIssueCards(user);
    expect(screen.getAllByText('Description').length).toBe(3);
  });

  it('renders recommendation sections', async () => {
    const user = userEvent.setup();
    render(<ReviewTaskDetail {...baseProps} task={mockTask} />);
    await expandIssuesPanel(user);
    await expandAllIssueCards(user);
    expect(screen.getAllByText('Recommendation').length).toBe(3);
  });

  it('renders no findings empty state', async () => {
    const user = userEvent.setup();
    render(<ReviewTaskDetail {...baseProps} task={mockTaskNoIssues} summary="Review #6" />);
    await expandIssuesPanel(user);
    expect(screen.getByText(/no findings were returned for this review/i)).toBeInTheDocument();
  });

  it('still renders summary panel with NONE risk when empty', async () => {
    const user = userEvent.setup();
    render(<ReviewTaskDetail {...baseProps} task={mockTaskNoIssues} summary="Review #6" />);
    await expandSummaryPanel(user);
    expect(screen.getByText('NONE')).toBeInTheDocument();
  });

  it('renders loading state', () => {
    render(<ReviewTaskDetail {...baseProps} task={null} loading={true} />);
    expect(screen.getByText(/loading review results/i)).toBeInTheDocument();
  });

  it('renders error message', () => {
    render(<ReviewTaskDetail {...baseProps} task={null} error="Task not found." />);
    expect(screen.getByRole('alert')).toHaveTextContent('Task not found.');
  });

  it('summary panel prefers backend issueSummary over computed values', async () => {
    const user = userEvent.setup();
    const taskWithDifferentSummary: ReviewTask = {
      ...mockTask,
      prNumber: 1,
      issues: mockIssues,
      issueSummary: {
        totalIssues: 55,
        highCount: 20,
        mediumCount: 15,
        lowCount: 10,
        riskLevel: 'HIGH',
      },
    };

    render(<ReviewTaskDetail {...baseProps} task={taskWithDifferentSummary} />);
    await expandSummaryPanel(user);

    expect(screen.getByText('55')).toBeInTheDocument();
    expect(screen.getAllByText('20').length).toBeGreaterThan(0);
    expect(screen.getAllByText('15').length).toBeGreaterThan(0);
    expect(screen.getAllByText('10').length).toBeGreaterThan(0);
  });

  it('fallback summary works when issueSummary is missing', async () => {
    const user = userEvent.setup();
    const taskWithoutSummary: ReviewTask = {
      ...mockTask,
      issueSummary: undefined,
    };

    render(<ReviewTaskDetail {...baseProps} task={taskWithoutSummary} />);
    await expandSummaryPanel(user);
    expect(screen.getAllByText('HIGH').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Findings').length).toBeGreaterThan(0);
  });
});
