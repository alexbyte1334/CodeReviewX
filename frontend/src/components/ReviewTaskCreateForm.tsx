import { useState } from 'react';
import type { ReviewTask } from '../types/reviewTask';
import { MAX_DIFF_TEXT_LENGTH, MAX_PR_NUMBER } from '../types/reviewTask';
import { createReviewTask } from '../api/reviewTaskApi';
import { formatProviderHitLabel } from '../utils/providerHit';
import { LoadingState } from './LoadingState';
import { ErrorMessage } from './ErrorMessage';
import { CollapsiblePanel } from './CollapsiblePanel';

interface ReviewTaskCreateFormProps {
  onCreated: (task: ReviewTask) => void;
  backendAvailable?: boolean;
  mimoConfigured?: boolean;
  expanded: boolean;
  onToggle: () => void;
}

type FormState = 'idle' | 'submitting' | 'success' | 'error';

function parsePositiveInteger(value: string): number | null {
  const trimmed = value.trim();
  if (!/^[1-9]\d*$/.test(trimmed)) {
    return null;
  }
  const parsed = Number(trimmed);
  if (!Number.isSafeInteger(parsed) || parsed > MAX_PR_NUMBER) {
    return null;
  }
  return parsed;
}

export function ReviewTaskCreateForm({
  onCreated,
  backendAvailable = true,
  mimoConfigured = true,
  expanded,
  onToggle,
}: ReviewTaskCreateFormProps) {
  const [repoUrl, setRepoUrl] = useState('');
  const [prNumber, setPrNumber] = useState('');
  const [diffText, setDiffText] = useState('');
  const [formState, setFormState] = useState<FormState>('idle');
  const [errorMsg, setErrorMsg] = useState('');
  const [lastProviderHit, setLastProviderHit] = useState<boolean | null>(null);
  const [lastProviderHitLabel, setLastProviderHitLabel] = useState('');
  const [validationErrors, setValidationErrors] = useState<{
    repoUrl?: string;
    prNumber?: string;
    diffText?: string;
  }>({});

  const diffLength = diffText.length;
  const diffOverLimit = diffLength > MAX_DIFF_TEXT_LENGTH;
  const diffCounterClass =
    diffOverLimit ? 'diff-counter diff-counter--over' : 'diff-counter';

  function validate(): boolean {
    const errors: { repoUrl?: string; prNumber?: string; diffText?: string } = {};
    if (!repoUrl.trim()) {
      errors.repoUrl = 'Repository URL is required.';
    }
    if (!prNumber.trim()) {
      errors.prNumber = 'PR Number is required.';
    } else if (parsePositiveInteger(prNumber) == null) {
      errors.prNumber = 'PR Number must be a positive integer.';
    }
    if (diffText.length > MAX_DIFF_TEXT_LENGTH) {
      errors.diffText = `PR diff is too large. Maximum length is ${MAX_DIFF_TEXT_LENGTH} characters.`;
    }
    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!validate()) return;

    setFormState('submitting');
    setErrorMsg('');
    setLastProviderHit(null);
    setLastProviderHitLabel('');

    const trimmedDiff = diffText.trim();
    const payload = {
      repoUrl: repoUrl.trim(),
      prNumber: parsePositiveInteger(prNumber) as number,
      provider: 'mimo' as const,
      ...(trimmedDiff ? { diffText: trimmedDiff } : {}),
    };

    try {
      const res = await createReviewTask(payload);
      if (res.success && res.data) {
        setFormState('success');
        setLastProviderHit(res.data.providerHit ?? null);
        setLastProviderHitLabel(
          formatProviderHitLabel(
            res.data.providerHit,
            res.data.requestedProvider ?? 'mimo',
            res.data.providerUsed,
          ),
        );
        setRepoUrl('');
        setPrNumber('');
        setDiffText('');
        setValidationErrors({});
        onCreated(res.data);
      } else {
        setFormState('error');
        setErrorMsg(
          res.message ||
            'The review agent could not create this task. Please check the input and try again.',
        );
      }
    } catch {
      setFormState('error');
      setErrorMsg(
        'Backend is unavailable. Check that backend-java is running on localhost:8080.',
      );
    }
  }

  const isSubmitting = formState === 'submitting';

  return (
    <CollapsiblePanel
      panelId="panel-review"
      title="Run Review"
      summary="Repository, PR, optional diff"
      expanded={expanded}
      onToggle={onToggle}
    >
      <p className="panel-intro">
        Enter repository and PR details. Paste an optional diff for grounded analysis.
      </p>
      <form onSubmit={handleSubmit} noValidate>
        <div className="form-group">
          <span className="form-group-label">Review Provider</span>
          <div className="provider-choice" aria-label="Review provider">
            <div
              className={`provider-choice-option provider-choice-option--active${!mimoConfigured ? ' provider-choice-option--disabled' : ''}`}
            >
              <span className="provider-choice-title">Xiaomi MiMo</span>
              <span className="provider-choice-desc">Dual-agent AI review</span>
            </div>
          </div>
          {!mimoConfigured && (
            <p className="field-help">
              MiMo requires planner and executor API keys on the backend server.
            </p>
          )}
        </div>

        <div className="form-group">
          <label htmlFor="repoUrl">Repository URL</label>
          <input
            id="repoUrl"
            type="url"
            value={repoUrl}
            onChange={(e) => setRepoUrl(e.target.value)}
            placeholder="https://github.com/example/repo"
            disabled={isSubmitting}
            aria-describedby={validationErrors.repoUrl ? 'repoUrl-error' : undefined}
          />
          {validationErrors.repoUrl && (
            <span id="repoUrl-error" className="field-error">{validationErrors.repoUrl}</span>
          )}
        </div>

        <div className="form-group">
          <label htmlFor="prNumber">Pull Request Number</label>
          <input
            id="prNumber"
            type="number"
            value={prNumber}
            onChange={(e) => setPrNumber(e.target.value)}
            placeholder="123"
            min={1}
            max={MAX_PR_NUMBER}
            disabled={isSubmitting}
            aria-describedby={validationErrors.prNumber ? 'prNumber-error' : undefined}
          />
          {validationErrors.prNumber && (
            <span id="prNumber-error" className="field-error">{validationErrors.prNumber}</span>
          )}
        </div>

        <div className="form-group">
          <div className="form-group-label-row">
            <label htmlFor="diffText">Optional PR Diff</label>
            <span className={diffCounterClass} aria-live="polite">
              {diffLength.toLocaleString()} / {MAX_DIFF_TEXT_LENGTH.toLocaleString()}
            </span>
          </div>
          <textarea
            id="diffText"
            value={diffText}
            onChange={(e) => setDiffText(e.target.value)}
            placeholder="diff --git a/src/App.tsx b/src/App.tsx..."
            rows={6}
            disabled={isSubmitting}
            aria-describedby="diffText-help diffText-error"
            aria-invalid={diffOverLimit}
          />
          <p id="diffText-help" className="field-help">
            Paste a unified diff to review exact code changes. Leave empty to let the backend
            load bounded GitHub PR diff with GITHUB_TOKEN.
          </p>
          {validationErrors.diffText && (
            <span id="diffText-error" className="field-error">{validationErrors.diffText}</span>
          )}
        </div>

        <button
          type="submit"
          disabled={isSubmitting || !backendAvailable || !mimoConfigured}
          className="btn-primary"
        >
          {isSubmitting ? 'Running review…' : 'Run Review'}
        </button>
      </form>

      {isSubmitting && <LoadingState message="Analyzing your input…" />}
      {formState === 'error' && <ErrorMessage message={errorMsg} />}
      {formState === 'success' && (
        <div className="success-panel" role="status">
          <p className="success-message">Review complete. Findings are ready to inspect.</p>
          {lastProviderHitLabel && (
            <p
              className={`provider-hit-banner${lastProviderHit ? ' provider-hit-banner--hit' : ' provider-hit-banner--miss'}`}
            >
              {lastProviderHitLabel}
            </p>
          )}
        </div>
      )}
    </CollapsiblePanel>
  );
}
