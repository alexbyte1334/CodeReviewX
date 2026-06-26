import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReviewTaskCreateForm } from '../components/ReviewTaskCreateForm';
import { MAX_DIFF_TEXT_LENGTH } from '../types/reviewTask';
import * as reviewTaskApi from '../api/reviewTaskApi';

const defaultProps = {
  onCreated: vi.fn(),
  expanded: true,
  onToggle: vi.fn(),
};

describe('ReviewTaskCreateForm', () => {
  it('renders Repository URL and PR Number fields when expanded', () => {
    render(<ReviewTaskCreateForm {...defaultProps} defaultReviewProvider="mock" />);
    expect(screen.getByLabelText(/repository url/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/pull request number/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^run review$/i })).toBeInTheDocument();
  });

  it('hides form fields when collapsed', () => {
    render(<ReviewTaskCreateForm {...defaultProps} expanded={false} />);
    expect(document.getElementById('panel-review-body')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByRole('button', { name: /expand run review panel/i })).toBeInTheDocument();
  });

  it('renders optional diff textarea with helper copy', () => {
    render(<ReviewTaskCreateForm {...defaultProps} defaultReviewProvider="mock" />);
    expect(screen.getByLabelText(/optional pr diff/i)).toBeInTheDocument();
    expect(
      screen.getByText(/paste a unified diff to let the review agent inspect actual code changes/i),
    ).toBeInTheDocument();
  });

  it('renders character counter for diff textarea', () => {
    render(<ReviewTaskCreateForm {...defaultProps} defaultReviewProvider="mock" />);
    expect(screen.getByText(`0 / ${MAX_DIFF_TEXT_LENGTH.toLocaleString()}`)).toBeInTheDocument();
  });

  it('submit without diff works', async () => {
    const onCreated = vi.fn();
    vi.spyOn(reviewTaskApi, 'createReviewTask').mockResolvedValueOnce({
      success: true,
      message: 'OK',
      data: {
        id: 1,
        repoUrl: 'https://github.com/example/repo',
        prNumber: 10,
        status: 'SUCCESS',
        summary: null,
        riskLevel: 'HIGH',
        errorMessage: null,
        createdAt: '',
        updatedAt: '',
        issues: [],
      },
    });

    render(<ReviewTaskCreateForm {...defaultProps} onCreated={onCreated} />);
    await userEvent.type(screen.getByLabelText(/repository url/i), 'https://github.com/example/repo');
    await userEvent.type(screen.getByLabelText(/pull request number/i), '10');
    await userEvent.click(screen.getByRole('button', { name: /^run review$/i }));

    await waitFor(() => {
      expect(reviewTaskApi.createReviewTask).toHaveBeenCalledWith({
        repoUrl: 'https://github.com/example/repo',
        prNumber: 10,
        provider: 'mimo',
      });
    });
    expect(onCreated).toHaveBeenCalled();
  });

  it('submit with diff sends diffText', async () => {
    vi.spyOn(reviewTaskApi, 'createReviewTask').mockResolvedValueOnce({
      success: true,
      message: 'OK',
      data: {
        id: 1,
        repoUrl: 'https://github.com/example/repo',
        prNumber: 10,
        status: 'SUCCESS',
        summary: null,
        riskLevel: 'HIGH',
        errorMessage: null,
        createdAt: '',
        updatedAt: '',
        issues: [],
      },
    });

    render(<ReviewTaskCreateForm {...defaultProps} defaultReviewProvider="mock" />);
    await userEvent.type(screen.getByLabelText(/repository url/i), 'https://github.com/example/repo');
    await userEvent.type(screen.getByLabelText(/pull request number/i), '10');
    await userEvent.type(
      screen.getByLabelText(/optional pr diff/i),
      'diff --git a/src/App.tsx b/src/App.tsx\n+const x = 1;\n',
    );
    await userEvent.click(screen.getByRole('button', { name: /^run review$/i }));

    await waitFor(() => {
      expect(reviewTaskApi.createReviewTask).toHaveBeenCalledWith({
        repoUrl: 'https://github.com/example/repo',
        prNumber: 10,
        provider: 'mock',
        diffText: 'diff --git a/src/App.tsx b/src/App.tsx\n+const x = 1;',
      });
    });
  });

  it('renders provider selector', () => {
    render(<ReviewTaskCreateForm {...defaultProps} defaultReviewProvider="mock" />);
    expect(screen.getByRole('radiogroup', { name: /review provider/i })).toBeInTheDocument();
    expect(screen.getByText(/xiaomi mimo/i)).toBeInTheDocument();
  });

  it('submit with MiMo when configured sends provider mimo', async () => {
    vi.spyOn(reviewTaskApi, 'createReviewTask').mockResolvedValueOnce({
      success: true,
      message: 'OK',
      data: {
        id: 1,
        repoUrl: 'https://github.com/example/repo',
        prNumber: 10,
        status: 'SUCCESS',
        summary: null,
        riskLevel: 'HIGH',
        errorMessage: null,
        createdAt: '',
        updatedAt: '',
        issues: [],
      },
    });

    render(<ReviewTaskCreateForm {...defaultProps} mimoConfigured />);
    await userEvent.type(screen.getByLabelText(/repository url/i), 'https://github.com/example/repo');
    await userEvent.type(screen.getByLabelText(/pull request number/i), '10');
    await userEvent.click(screen.getByLabelText(/xiaomi mimo/i));
    await userEvent.click(screen.getByRole('button', { name: /^run review$/i }));

    await waitFor(() => {
      expect(reviewTaskApi.createReviewTask).toHaveBeenCalledWith({
        repoUrl: 'https://github.com/example/repo',
        prNumber: 10,
        provider: 'mimo',
      });
    });
  });

  it('blocks submit when diff exceeds max length', async () => {
    const createSpy = vi.spyOn(reviewTaskApi, 'createReviewTask');

    render(<ReviewTaskCreateForm {...defaultProps} defaultReviewProvider="mock" />);
    await userEvent.type(screen.getByLabelText(/repository url/i), 'https://github.com/example/repo');
    await userEvent.type(screen.getByLabelText(/pull request number/i), '10');
    fireEvent.change(screen.getByLabelText(/optional pr diff/i), {
      target: { value: 'x'.repeat(MAX_DIFF_TEXT_LENGTH + 1) },
    });
    await userEvent.click(screen.getByRole('button', { name: /^run review$/i }));

    expect(createSpy).not.toHaveBeenCalled();
    expect(screen.getByText(/pr diff is too large/i)).toBeInTheDocument();
  });

  it('shows submitting loading state', async () => {
    vi.spyOn(reviewTaskApi, 'createReviewTask').mockImplementation(
      () => new Promise(() => {}),
    );

    render(<ReviewTaskCreateForm {...defaultProps} defaultReviewProvider="mock" />);
    await userEvent.type(screen.getByLabelText(/repository url/i), 'https://github.com/example/repo');
    await userEvent.type(screen.getByLabelText(/pull request number/i), '10');
    await userEvent.click(screen.getByRole('button', { name: /^run review$/i }));

    expect(screen.getByText(/running review/i)).toBeInTheDocument();
    expect(screen.getByText(/analyzing your input/i)).toBeInTheDocument();
  });

  it('shows provider hit banner after successful review', async () => {
    vi.spyOn(reviewTaskApi, 'createReviewTask').mockResolvedValueOnce({
      success: true,
      message: 'OK',
      data: {
        id: 1,
        repoUrl: 'https://github.com/example/repo',
        prNumber: 10,
        status: 'SUCCESS',
        summary: null,
        riskLevel: 'HIGH',
        errorMessage: null,
        createdAt: '',
        updatedAt: '',
        issues: [],
        requestedProvider: 'mimo',
        providerUsed: 'mock',
        providerHit: false,
      },
    });

    render(<ReviewTaskCreateForm {...defaultProps} defaultReviewProvider="mock" />);
    await userEvent.type(screen.getByLabelText(/repository url/i), 'https://github.com/example/repo');
    await userEvent.type(screen.getByLabelText(/pull request number/i), '10');
    await userEvent.click(screen.getByRole('button', { name: /^run review$/i }));

    expect(await screen.findByText(/未命中/i)).toBeInTheDocument();
  });

  it('disables submit when backend is unavailable', () => {
    render(<ReviewTaskCreateForm {...defaultProps} backendAvailable={false} />);
    expect(screen.getByRole('button', { name: /^run review$/i })).toBeDisabled();
  });
});
