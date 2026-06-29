# CodeReviewX PRD

## 1. Product Positioning

CodeReviewX is a local-first AI code review agent MVP for GitHub pull request
review. It is built to demonstrate an end-to-end engineering workflow:

- collect PR or manual diff input,
- run an AI-assisted review,
- normalize findings into structured issues,
- persist execution evidence,
- present the result in a web workspace,
- publish selected comments only after human confirmation.

The project is scoped as a portfolio/interview project, not a production SaaS.

## 2. Target Users

Primary user:

- A developer who wants to review a GitHub PR or pasted diff locally.

Secondary audience:

- Interviewers and reviewers evaluating the project's backend orchestration,
  AI integration, safety boundaries, and frontend presentation.

## 3. Core Problems

CodeReviewX addresses these problems:

1. Raw model output is hard to trust without structure.
2. PR review agents need traceability so users can see what happened.
3. Automated comments should not be posted without user approval.
4. Demo AI projects often hide failure modes; this project should surface them
   with explicit error codes and safe summaries.

## 4. Current MVP Scope

### In Scope

- Create review tasks from GitHub repository URL and PR number.
- Optional pasted unified diff review.
- Default GitHub PR mode when no diff is supplied.
- GitHub PR metadata and bounded files patch loading.
- Bounded changed-file repository context index for GitHub PR mode.
- Xiaomi MiMo dual-agent review:
  - AI-1 Planner,
  - AI-2 Executor,
  - AI-1 Gatekeeper.
- Request-time Semgrep-style and dependency hygiene findings merged into review
  task results with explicit source provenance.
- Deterministic issue generation from approved structured output.
- Persisted review tasks, runs, issues, input snapshots, tool traces, provider
  traces, and comment previews.
- React workspace for review history, findings, trace, and comment preview
  publishing.
- Human-in-the-loop GitHub comment publishing.
- Local evals, secret scan, dependency scan, and Semgrep static-analysis script.

### Out of Scope

- GitHub App or OAuth installation flow.
- Multi-user accounts, teams, or permissions.
- Production secret management.
- Full repository clone and cross-file semantic analysis.
- Queue workers, retries, cancellation, and progress streaming.
- Semantic/vector RAG, durable memory, MCP, or function-calling tool
  orchestration.
- External Semgrep execution as a long-running review worker.
- Production database deployment.

## 5. User Stories

### Create a Manual Diff Review

As a developer, I can paste a unified diff with a repo URL and PR number so that
the system reviews the exact code change I provide.

Acceptance:

- blank diff is treated as absent unless `MANUAL_DIFF` is explicitly requested,
- oversized diff is rejected,
- public responses do not return raw diff text.

### Create a GitHub PR Review

As a developer, I can submit a GitHub repository URL and PR number without
pasting diff text so that the backend loads bounded PR context from GitHub.

Acceptance:

- missing `GITHUB_TOKEN` fails with a clear `GITHUB_AUTH_MISSING` error,
- PR metadata and files patch are represented in sanitized snapshots,
- changed-file contents are fetched under strict file and byte limits,
- overly large or unavailable diffs fail or truncate according to configured
  limits.

### Inspect Review Evidence

As a user, I can see issue summary, individual findings, provider state, and
agent trace so that I can understand what the review did.

Acceptance:

- trace contains safe summaries only,
- raw prompt and raw model output are not exposed,
- failures have stable error codes.

### Publish Selected Comments

As a user, I can select generated comment previews and confirm publishing so
that no automated comment reaches GitHub without my approval.

Acceptance:

- publishing requires selected previews,
- publishing requires `confirmed=true`,
- publish success stores the GitHub comment id,
- publish failure stores a safe error message.

## 6. Success Criteria

The MVP is successful when:

- backend tests pass,
- frontend typecheck/build/tests pass,
- local evals pass expected schema and finding checks,
- static scan passes without committed secrets,
- manual diff and GitHub PR paths are represented clearly in documentation,
- the public README describes current capabilities without overstating future
  production features.

## 7. Future Enhancements

Reasonable next steps:

1. Async review execution with status polling and retries.
2. GitHub App or OAuth-based installation.
3. Full-repository semantic index for broader code understanding.
4. External Semgrep/dependency worker with richer rule coverage.
5. Production database and deployment profile.
6. Role-based auth and team workflows.
7. Optional vector RAG over project rules and historical review decisions.
