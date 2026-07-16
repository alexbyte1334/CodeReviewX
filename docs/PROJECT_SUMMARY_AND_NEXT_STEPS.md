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

The project is not positioned as a production SaaS: multi-user authentication
and GitHub App installation remain out of scope. The production profile now
provides bounded repository clone/indexing and semantic/vector RAG on
PostgreSQL/pgvector; the default H2 demo profile intentionally does not.

## 2. Current Runtime Shape

```text
React frontend
  -> Spring Boot backend-java
  -> H2 file database
  -> GitHub REST API
  -> Xiaomi MiMo OpenAI-compatible API
```

There is no active Python `ai-service` process in the current runtime.
`ai-service/` remains only as a historical placeholder; the active runtime is
fully implemented in `backend-java`. The production profile includes PostgreSQL/pgvector indexing, hybrid retrieval,
rerank, evidence persistence, rollout switches, and an explicit legacy fallback;
it is not claimed as production-ready until the Docker smoke and CI gates below
are observed.

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
node scripts/secret-scan.mjs
node scripts/dependency-scan.mjs
node scripts/run-rag-evals.mjs
git diff --check
```

## 6. Task 12 delivery evidence (2026-07-16)

- Verified branch head before this evidence refresh: `3cfdc15796e006d0d061e5c4cf555d189c8551d0`.
- `node scripts/run-rag-evals.mjs`: passed offline deterministic mode. Recall@10
  1.00, MRR@10 0.7222, nDCG@10 0.7586, forbidden-hit 0, cross-commit
  contamination 0, expected-finding pass 1.00, p95 latency 9.50 ms.
- `node scripts/run-evals.mjs`: passed (schema pass 100%, expected finding hit
  100%). `git diff --check`: passed.
- Frontend verification passed: 71 tests, typecheck, and production build.
- All non-Docker backend tests passed with the six PostgreSQL/Testcontainers
  classes excluded. Local HTTP client tests passed with socket permission.
  Checkout fails closed on this filesystem because it does not provide
  `SecureDirectoryStream`; the capable-provider security tests remain enabled
  for Linux CI.
- Static scan passed with 0 findings; secret scan and dependency scan passed
  with 0 blocking issues. The dependency report records the expected H2
  local-demo warning.
- The complete unfiltered Maven command is not green only because Docker is
  unavailable, so the six PostgreSQL/Testcontainers classes cannot start.
- `docker compose build` and `bash scripts/rag-smoke.sh`: not run/passed because
  Docker is unavailable. No smoke `jobId`/`runId` exists. CI run: not run/unknown.

The project handoff is `DONE_WITH_CONCERNS`: operations/evaluation docs and
rollout switches are delivered, but Definition of Done remains open until a
Docker-enabled environment runs PostgreSQL migration, compose smoke, and CI.

## 6. Recommended Interview Narrative

Use this project to explain:

- how an AI agent workflow is decomposed into planner, executor, gatekeeper,
  deterministic normalization, and explicit action steps;
- why raw model output should not directly mutate application state;
- how trace and snapshot tables make the review workflow observable without
  leaking secrets;
- how the production profile uses PostgreSQL/pgvector full-repository hybrid
  retrieval while the H2 profile remains a local demo;
- how human confirmation reduces risk before external side effects;
- how bounded GitHub diff loading controls cost, latency, and privacy.

## 7. Next Engineering Steps

### Production Integration Gate

Run the PostgreSQL/pgvector migration, Compose smoke, and CI in a Docker-enabled
environment; record the real job/run IDs and keep the release at 0% until all
quality, security, evidence, and latency gates pass.

### Live Eval Capture

Capture sanitized real backend/MiMo outputs into an ignored or reviewed eval
artifact folder, then compare prompt/model changes over time.

### Richer Static Analysis in Review Runs

Replace the current lightweight heuristics with a controlled external Semgrep
and dependency-analysis worker. Preserve the existing source provenance and
safe-summary rules.

### Operational Hardening

Add managed secret storage, authentication/GitHub App installation, queue
retry/cancellation, and audited retention/deletion workflows without changing
the existing evidence and publish invariants.
