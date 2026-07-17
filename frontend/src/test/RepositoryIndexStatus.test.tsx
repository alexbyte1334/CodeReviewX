import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { RepositoryIndexStatus } from '../components/RepositoryIndexStatus';

describe('RepositoryIndexStatus', () => {
  it('shows the index job error when there is no action error', () => {
    render(
      <RepositoryIndexStatus
        status={{ status: 'FAILED', errorMessage: 'Repository checkout failed safely.' }}
        onReindex={vi.fn()}
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Repository checkout failed safely.');
  });

  it('prefers the action error without duplicating the index job error', () => {
    render(
      <RepositoryIndexStatus
        status={{ status: 'FAILED', errorMessage: 'Old job error.' }}
        actionError="Current action error."
        onReindex={vi.fn()}
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Current action error.');
    expect(screen.queryByText('Old job error.')).not.toBeInTheDocument();
  });
});
