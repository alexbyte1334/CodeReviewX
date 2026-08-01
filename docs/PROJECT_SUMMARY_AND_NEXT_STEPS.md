# CodeReviewX Project Summary and Next Steps

> Public project summary for GitHub and interview preparation. Historical
> process notes and local validation logs are intentionally excluded from this
> document.

## 1. Positioning

CodeReviewX is a locally runnable AI code review agent MVP for pull request
review workflows. It demonstrates a complete engineering loop:

1. Create a review task from a GitHub PR or pasted unified diff.
2. Load bounded GitHub PR metadata and the files patch at the PR head SHA.
3. Ensure an immutable full-repository snapshot is indexed in PostgreSQL with
   pgvector, then retrieve commit-scoped evidence through vector search,
   PostgreSQL FTS, RRF fusion, reranking, and context-budget assembly.
4. Run request-time Semgrep-style and dependency hygiene finding checks.
5. Run a Xiaomi MiMo dual-agent review workflow against the evidence bundle.
6. Validate every model evidence reference before persisting issues or previews.
7. Generate local comment previews.
8. Let the user select and explicitly confirm comments before publishing them
   back to GitHub.
9. Preserve safe trace, snapshot, retrieval, and provider summaries for
   observability.

The project is not positioned as a production SaaS: multi-user authentication
and GitHub App installation remain out of scope. The production profile now
provides bounded repository clone/indexing and semantic/vector RAG on
PostgreSQL/pgvector; the default H2 local development profile intentionally does not.

## 2. Current Runtime Shape

```text
React frontend
  -> Spring Boot backend-java
     -> PostgreSQL 16 + pgvector (production RAG profile)
     -> H2 (RAG-disabled local development profile)
     -> GitHub REST API
     -> OpenAI-compatible embedding and rerank APIs
     -> Xiaomi MiMo planner / executor / gatekeeper APIs
```

There is no active Python service in the current runtime. The active runtime is
fully implemented in `backend-java`. The production profile includes
restart-safe indexing, PostgreSQL/pgvector hybrid retrieval, reranking, evidence
persistence, rollout switches, and an explicit legacy fallback. Local Docker,
production-path quality, performance, and smoke gates passed, and the complete
delivery was merged into `main` after GitHub Actions passed.

## 3. Implemented Capabilities

### Backend and Persistence

- Spring Boot 3 + Java 17 + Maven.
- H2 file database for local runtime persistence.
- PostgreSQL 16 + pgvector for production RAG snapshots, jobs, chunks,
  embeddings, FTS, evidence, and retrieval traces.
- Flyway-managed H2 and PostgreSQL schemas.
- ReviewTask create/list/detail APIs.
- ReviewRun, ReviewIssue, ReviewInputSnapshot, ReviewToolTrace,
  ReviewProviderTrace, and ReviewCommentPreview persistence.
- Fail-fast error handling for missing provider credentials, invalid provider
  output, missing GitHub token, and unsafe publish requests.
- Durable self-hosted review runs, execution leases, append-only events, rate buckets,
  explicit Replay fallback, and owner-controlled publishing.

### GitHub PR Input

- `GITHUB_PR` mode when no manual `diffText` is provided.
- GitHub PR metadata loader.
- GitHub PR files patch loader.
- Full-repository commit-scoped index at PR head SHA, with the bounded
  changed-file context retained only as an explicit fallback.
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
  -> rag.index.ensure
  -> rag.query.build
  -> rag.retrieve.hybrid
  -> rag.rerank
  -> rag.context.assemble
  -> static.analysis.findings
  -> mimo.ai1.plan
  -> mimo.ai2.execute
  -> mimo.ai1.gate
  -> issue.generate
  -> evidence.validate
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
- Self-hosted review publishing requires a server-only admin bearer token in addition
  to selected previews and all evidence/target validations.
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
- Repository index status, safe reindex actions, polling, and duplicate-job
  conflict handling.
- Retrieval health/degraded state and per-issue evidence inspection.

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
- GitHub comment publishing requires selected previews and explicit owner
  authorization for public or legacy HTTP routes.
- GitHub token permissions should be minimized to Metadata read, Contents read,
  and Pull requests read/write for comment publishing.

## 5. Validation Commands

Backend:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn verify -Ppostgres-integration
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
node scripts/run-rag-evals.mjs --self-test
node scripts/run-rag-evals.mjs
bash scripts/tests/rag-smoke-contract-test.sh
bash scripts/tests/rag-smoke-deadline-test.sh
git diff --check
```

## 6. Task 12 delivery evidence (2026-07-17)

- Implementation branch: `codex/production-rag-delivery`. Final local
  acceptance implementation commit:
  `95d83f52d78efb7f98c9b0bccb2226c251446a83`; cross-platform quality-report
  stabilization commit: `1f7cdcf5c9f8a9eacb89cd9f8d713c2888600560`.
- Delivery was merged into `main` through [PR #7](https://github.com/alexbyte1334/CodeReviewX/pull/7)
  on 2026-07-18.
  [GitHub Actions run 29588905155](https://github.com/alexbyte1334/CodeReviewX/actions/runs/29588905155)
  passed all seven jobs, including
  [PostgreSQL RAG Integration job 87912754835](https://github.com/alexbyte1334/CodeReviewX/actions/runs/29588905155/job/87912754835).
- Tasks 1-12 have independent primary commits: `e1bfdc0`, `d1e4c8b`,
  `eca328a`, `0e65384`, `39c1824`, `07b312c`, `c1f69a0`, `9c73ead`,
  `1f47d5b`, `8c1a369`, `c4a058b`, and `a85493e`, followed by focused review
  fixes and evidence refreshes.
- Backend: `395` tests, `0` failures, `0` errors, `11` intentional skips. The
  unfiltered suite used Docker Desktop and real PostgreSQL 16/pgvector
  Testcontainers; Flyway migrations v1-v7 passed. The opt-in performance gate
  was executed separately, while platform-specific secure-directory tests stay
  skipped on macOS providers that cannot expose `SecureDirectoryStream`.
- Frontend: `85/85` Vitest tests passed, followed by TypeScript typecheck and a
  production Vite build.
- Java production retrieval quality: Recall@10 `1.000`, MRR@10 `0.833`,
  nDCG@10 `0.871`; forbidden hits, cross-commit contamination, and context
  budget violations were all `0`. This gate executes the production Java
  hybrid retriever and context assembler against PostgreSQL/pgvector. It uses
  deterministic in-process embedding/rerank fixtures, not external model
  network calls.
- JS deterministic reference eval: Recall@10 `1.000`, MRR@10 `0.722`, nDCG@10
  `0.759`, forbidden-hit rate `0`, cross-commit contamination `0`, evidence
  validation `1.000`, and grounded finding precision `1.000`. Mutation
  self-tests passed.
- Original review eval: schema pass `100%`, expected finding hit `100%`, and no
  false positives in the three-case fixture.
- Performance acceptance on two 10,000-chunk snapshots / 1,000 files:
  20-file incremental index `23.83s`, `20` chunks embedded and `9,980` reused;
  hybrid retrieval + rerank p95 `150.98ms`; maximum context `35,999` chars,
  rerank candidates `30`, and evidence chunks `7`.
- Docker Compose build passed. PostgreSQL, backend, and frontend were rebuilt
  and healthy; `/api/health` reported `ragReady=true` and database, GitHub,
  embedding, and rerank dependencies `UP`.
- Real GitHub PR smoke passed for `alexbyte1334/CodeReviewX` PR `#4` at
  `b3a5e235bb7335e9e0f9617fb0ff78c8d89b7352`:
  `jobId=7`, `runId=11`, `selectedChunkCount=12`, `degraded=false`. The smoke
  asserted the complete RAG trace, non-empty evidence and preview, and rejected
  unconfirmed publishing with HTTP 400 without changing preview state.
- The local embedding and rerank endpoints used deterministic fixture services
  at `host.docker.internal:18081`. Xiaomi MiMo remained the live planner,
  executor, and gatekeeper. One separate run failed closed with
  `MIMO_REVIEW_INVALID` when the executor returned non-JSON; a later run passed.
- Semgrep scanned 216 targets with 0 findings. Secret and dependency scans had
  0 blocking issues; the expected H2 local-demo warning remains informational.
- Local Definition of Done gates are complete, the delivery PR's GitHub Actions
  run is fully green, and PR #7 is merged. Runtime RAG rollout remains a
  separate operator-controlled 0% / 10% / 50% / 100% decision.

## 7. Recommended Interview Narrative

Use this project to explain:

- how an AI agent workflow is decomposed into planner, executor, gatekeeper,
  deterministic normalization, and explicit action steps;
- why raw model output should not directly mutate application state;
- how trace and snapshot tables make the review workflow observable without
  leaking secrets;
- how the production profile uses PostgreSQL/pgvector full-repository hybrid
  retrieval while the H2 profile remains a local development;
- how human confirmation reduces risk before external side effects;
- how bounded GitHub diff loading controls cost, latency, and privacy.

## 8. Next Engineering Steps

### Controlled Rollout

Remote CI is green. Keep `RAG_REVIEW_PERCENTAGE=0` until a human approves the
10% rollout, then follow the documented 10% / 50% / 100% gates. Track MiMo
invalid-output rate and retrieval degraded rate separately; do not hide either
behind fallback.

### Live Model Eval Capture

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
