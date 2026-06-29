# CodeReviewX Project Summary and Next Steps

> Public project summary for GitHub and interview preparation. Historical
> process notes and local validation logs are intentionally excluded from this
> document.

## 1. Positioning

CodeReviewX is a locally runnable AI code review agent MVP for pull request
review workflows. It demonstrates a complete engineering loop:

1. Create a review task from a GitHub PR or pasted unified diff.
2. Load bounded GitHub PR metadata, files patch, and changed-file context when
   needed.
3. Run request-time Semgrep-style and dependency hygiene finding checks.
4. Run a Xiaomi MiMo dual-agent review workflow.
5. Normalize approved model output and static findings into structured issues.
6. Generate local comment previews.
7. Let the user select and explicitly confirm comments before publishing them
   back to GitHub.
8. Preserve safe trace, snapshot, and provider summaries for observability.

The project is not positioned as a production SaaS. It intentionally omits
multi-user authentication, GitHub App installation, queue workers, full
repository clone/indexing, semantic/vector RAG, and production database
infrastructure.

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
- Bounded changed-file repository context index at PR head SHA.
- Bounded ingestion:
  - changed files: 50 by default
  - total diff bytes: 512000 by default
  - per-file patch bytes: 20000 by default
  - context files: 8 by default
  - per-file context bytes: 12000 by default
  - total context bytes: 48000 by default
- Sanitized input snapshot persistence; raw full diff and tokens are not
  exposed through public APIs.

### MiMo Dual-Agent Review

```text
github.pr.metadata.load
  -> github.pr.diff.load
  -> repository.context.index
  -> static.analysis.findings
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
- Request-time static findings are persisted with `SEMGREP` or `DEPENDENCY`
  source provenance.
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
- Request-time lightweight Semgrep-style and dependency hygiene finding
  services.

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
- why the project uses a bounded changed-file context index now, and what is
  still required for full semantic repository understanding;
- how human confirmation reduces risk before external side effects;
- how bounded GitHub diff loading controls cost, latency, and privacy.

## 7. Next Engineering Steps

### Full Repository Context Worker

Extend the current changed-file context index into a separate worker that can
clone or checkout repositories safely, index related files/tests/config, and
return sanitized bounded context to the review pipeline.

### Live Eval Capture

Capture sanitized real backend/MiMo outputs into an ignored or reviewed eval
artifact folder, then compare prompt/model changes over time.

### Richer Static Analysis in Review Runs

Replace the current lightweight heuristics with a controlled external Semgrep
and dependency-analysis worker. Preserve the existing source provenance and
safe-summary rules.

### Production Readiness

For a production version, add OAuth or GitHub App installation, async queueing,
retry/cancellation, PostgreSQL or MySQL, managed secret storage, and audit
logging.
