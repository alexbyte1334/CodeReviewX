import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from '../App';
import * as reviewTaskApi from '../api/reviewTaskApi';

vi.mock('../api/reviewTaskApi', () => ({
  getHealth: vi.fn(),
  listReviewTasks: vi.fn(),
  getReviewTask: vi.fn(),
  createReviewTask: vi.fn(),
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

    expect(screen.getByText(/manual diff input only/i)).toBeVisible();
    expect(screen.getByText(/mimo dual-agent review only/i)).toBeVisible();
    expect(screen.getByText(/no automatic github pr fetching yet/i)).toBeVisible();
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
});
