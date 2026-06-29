# CodeReviewX Project Summary and Next Steps

> Public project summary for GitHub and interview preparation. Historical
> process notes and local validation logs are intentionally excluded from this
> document.

## 1. Positioning

CodeReviewX is a locally runnable AI code review agent MVP for pull request
review workflows. It demonstrates a complete engineering loop:

1. Create a review task from a GitHub PR or pasted unified diff.
2. Load bounded GitHub PR metadata and files patch when needed.
3. Run a Xiaomi MiMo dual-agent review workflow.
4. Normalize approved model output into structured issues.
5. Generate local comment previews.
6. Let the user select and explicitly confirm comments before publishing them
   back to GitHub.
7. Preserve safe trace, snapshot, and provider summaries for observability.

The project is not positioned as a production SaaS. It intentionally omits
multi-user authentication, GitHub App installation, queue workers, repository
clone/indexing, RAG, and production database infrastructure.

## 2. Current Runtime Shape

```text
React frontend
  -> Spring Boot backend-java
  -> H2 file database
  -> GitHub REST API
  -> Xiaomi MiMo OpenAI-compatible API
```

There is no active Python `ai-service` process in the current runtime.
`ai-service/` remains only as a historical placeholder and possible future
extraction point for heavier repository analysis or provider workers.

## 3. Implemented Capabilities

### Backend and Persistence

- Spring Boot 3 + Java 17 + Maven.
- H2 file database for local runtime persistence.
- Flyway-managed schema.
- ReviewTask create/list/detail APIs.
- ReviewRun, ReviewIssue, ReviewInputSnapshot, ReviewToolTrace,
  ReviewProviderTrace, and ReviewCommentPreview persistence.
- Fail-fast error handling for missing provider credentials, invalid provider
  output, missing GitHub token, and unsafe publish requests.

### GitHub PR Input

- `GITHUB_PR` mode when no manual `diffText` is provided.
- GitHub PR metadata loader.
- GitHub PR files patch loader.
- Bounded ingestion:
  - changed files: 50 by default
  - total diff bytes: 512000 by default
  - per-file patch bytes: 20000 by default
- Sanitized input snapshot persistence; raw full diff and tokens are not
  exposed through public APIs.

### MiMo Dual-Agent Review

```text
github.pr.metadata.load
  -> github.pr.diff.load
  -> mimo.ai1.plan
  -> mimo.ai2.execute
  -> mimo.ai1.gate
  -> issue.generate
  -> comment.preview.build
```

- AI-1 Planner creates the task plan.
- AI-2 Executor performs the review.
- AI-1 Gatekeeper accepts or rejects the candidate review.
- MiMoIssueGenerator maps approved JSON into deterministic structured issues.
- New tasks do not silently fall back to mock results.

### Human-in-the-Loop Publish

- Local comment previews are generated from persisted issues.
- The frontend lets the user select previews.
- Publishing requires `confirmed=true`.
- The backend validates target metadata and selected preview ownership.
- Publish status is persisted as `NOT_PUBLISHED`, `PUBLISHING`, `PUBLISHED`, or
  `FAILED`.

### Frontend

- React 18 + TypeScript + Vite.
- Review task creation.
- Task history and detail view.
- Risk summary, issue list, provider status, trace timeline, and comment
  preview publishing states.
- MiMo readiness feedback to prevent starting reviews when the backend is not
  configured.

### Quality and Security Tooling

- Backend test suite.
- Frontend typecheck, production build, and Vitest suite.
- Offline eval benchmark under `evals/`.
- Secret scan.
- Dependency hygiene scan.
- Semgrep rules via `.semgrep.yml`.

## 4. Security Boundaries

Keep these invariants true:

- Real API keys only live in local environment variables or ignored local
  files.
- `.env`, local H2 data, build output, dependency folders, and local key notes
  are ignored or absent from the public repository.
- Public APIs do not return GitHub tokens, MiMo keys, Authorization headers,
  raw prompts, raw model output, or raw full diff.
- GitHub comment publishing requires both selected previews and explicit user
  confirmation.
- GitHub token permissions should be minimized to Metadata read, Contents read,
  and Pull requests read/write for comment publishing.

## 5. Validation Commands

Backend:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Frontend:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

Evals and static analysis:

```bash
node scripts/run-evals.mjs
node scripts/static-scan.mjs
git diff --check
```

## 6. Recommended Interview Narrative

Use this project to explain:

- how an AI agent workflow is decomposed into planner, executor, gatekeeper,
  deterministic normalization, and explicit action steps;
- why raw model output should not directly mutate application state;
- how trace and snapshot tables make the review workflow observable without
  leaking secrets;
- how human confirmation reduces risk before external side effects;
- how bounded GitHub diff loading controls cost, latency, and privacy.

## 7. Next Engineering Steps

### Repository Context Loader

Load limited neighboring context for touched files, related tests, and config
files. Keep strict byte limits and store only sanitized summaries.

### Live Eval Capture

Capture sanitized real backend/MiMo outputs into an ignored or reviewed eval
artifact folder, then compare prompt/model changes over time.

### Static Analysis in Review Runs

Move Semgrep or dependency findings from offline tooling into the persisted
review task pipeline as a separate source with clear provenance.

### Production Readiness

For a production version, add OAuth or GitHub App installation, async queueing,
retry/cancellation, PostgreSQL or MySQL, managed secret storage, and audit
logging.
