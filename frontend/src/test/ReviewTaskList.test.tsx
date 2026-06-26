import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ReviewTaskList } from '../components/ReviewTaskList';

const defaultProps = {
  loading: false,
  error: null,
  selectedId: null,
  onSelect: vi.fn(),
  expanded: true,
  onToggle: vi.fn(),
  summary: 'No reviews yet',
};

describe('ReviewTaskList', () => {
  it('renders empty state when no tasks', () => {
    render(
      <ReviewTaskList
        {...defaultProps}
        tasks={[]}
      />,
    );
    expect(
      screen.getByText(/no reviews yet\. run a review to get started/i),
    ).toBeInTheDocument();
  });

  it('renders tasks when provided', () => {
    const mockTask = {
      id: 1,
      repoUrl: 'https://github.com/example/repo',
      prNumber: 42,
      status: 'SUCCESS' as const,
      summary: 'Mock review completed for PR #42.',
      riskLevel: 'LOW' as const,
      errorMessage: null,
      createdAt: '2026-06-23T06:22:07.527724',
      updatedAt: '2026-06-23T06:22:07.528436',
      issues: [],
    };
    render(
      <ReviewTaskList
        {...defaultProps}
        tasks={[mockTask]}
        summary="1 review"
      />,
    );
    expect(screen.getByText('Review #1')).toBeInTheDocument();
    expect(screen.getByText('https://github.com/example/repo')).toBeInTheDocument();
  });

  it('renders loading state', () => {
    render(
      <ReviewTaskList
        {...defaultProps}
        tasks={[]}
        loading={true}
      />,
    );
    expect(screen.getByRole('status')).toBeInTheDocument();
  });
});
