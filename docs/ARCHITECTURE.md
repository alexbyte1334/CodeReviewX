# CodeReviewX Architecture

> Current architecture, aligned with the implementation in this repository.
> Historical plans for a separate Python `ai-service` are documented as future
> expansion, not as the current runtime shape.

## 1. Product Boundary

CodeReviewX is a locally runnable AI-assisted pull request review MVP. It is
designed for portfolio and interview demonstration: a user submits a GitHub PR
or pasted unified diff, the system runs a bounded AI review, persists evidence,
and lets the user manually publish selected comment previews back to GitHub.

The current implementation is intentionally not a multi-tenant production
SaaS. It has no multi-user auth or GitHub App installation flow. The default H2
profile keeps the bounded changed-file context path; the production PostgreSQL
profile adds asynchronous full-repository clone/index snapshots, pgvector plus
full-text hybrid retrieval, reranking, and evidence-gated review.

## 2. Runtime Architecture

```text
-----------------------+
| React + Vite frontend|
| Review workspace     |
+----------+------------+
           |
           | REST API
           v
+-------------------------------+
| backend-java                  |
| Spring Boot 3 + Java 17       |
|                               |
| - ReviewTask API              |
| - ReviewRun orchestration     |
| - GitHub metadata/diff loader |
| - Legacy context + RAG module |
| - MiMo dual-agent provider    |
| - Static finding merger       |
| - trace/snapshot persistence  |
| - comment preview publisher   |
+------+------------------------+
       |
       | JPA / Flyway
       v
+-------------------------------+
| H2 local demo or PostgreSQL   |
| 16 + pgvector production RAG  |
+-------------------------------+

External HTTP dependencies:
  - GitHub REST API
  - Xiaomi MiMo OpenAI-compatible API
```

There is no active `ai-service` process in the current implementation. The
`ai-service/` folder is retained only as a historical placeholder and possible
future extraction target.

## 3. Main User Flow

```text
User submits repoUrl + prNumber [+ diffText]
        |
        v
frontend POST /api/review-tasks
        |
        v
ReviewTaskService creates review_task + review_run
        |
        +-- MANUAL_DIFF: use pasted bounded diff
        |
        +-- GITHUB_PR legacy: github.pr.metadata.load -> github.pr.diff.load
        |                    -> repository.context.index
        |
        +-- GITHUB_PR RAG: github.pr.metadata.load -> github.pr.diff.load
                             -> rag.index.ensure -> rag.query.build
                             -> rag.retrieve.hybrid -> rag.rerank
                             -> rag.context.assemble
        |
        v
Static finding pass
        |
        +-- Semgrep-style changed-line checks
        +-- Dependency hygiene checks from indexed files
        |
        v
ReviewPipelineService
        |
        v
ConfigurableReviewProvider
        |
        v
XiaomiMiMoReviewProvider
        |
        +-- mimo.ai1.plan
        +-- mimo.ai2.execute
        +-- mimo.ai1.gate
        +-- issue.generate
        |
        v
Merge MiMo + static findings
        |
        v
Persist ReviewIssue rows
        |
        v
Build local comment previews
        |
        v
Frontend displays summary, issues, trace, previews
        |
        v
User selects previews and confirms publish
        |
        v
GitHub PR review comment API
```

## 4. Module Responsibilities

### frontend

Responsibilities:

- Health check and MiMo readiness display.
- Create review tasks.
- Display review history and selected task details.
- Display issue summary, risk level, provider hit state, agent trace, and
  comment previews.
- Let the user select comment previews and explicitly confirm publish actions.

Boundaries:

- Does not call GitHub, MiMo, or any LLM provider directly.
- Does not store secrets.
- Does not make trust decisions about raw provider output.

### backend-java

Responsibilities:

- Provide public REST APIs for the frontend.
- Validate review task requests.
- Resolve `MANUAL_DIFF` vs `GITHUB_PR` mode.
- Load bounded GitHub PR metadata and files patch when needed.
- Preserve bounded changed-file context for H2/local fallback.
- Clone/index immutable repository commit snapshots and retrieve bounded
  pgvector/full-text evidence in the PostgreSQL RAG profile.
- Generate Semgrep-style and dependency-hygiene findings and persist them as
  normal review issues with explicit `source` provenance.
- Execute the MiMo dual-agent review workflow.
- Normalize approved model output into structured issues.
- Persist review tasks, runs, issues, traces, sanitized snapshots, and comment
  previews.
- Publish selected comment previews to GitHub only after explicit user
  confirmation.

Boundaries:

- Does not execute repository code; clone/index work is bounded by file, byte,
  path, and commit-SHA controls.
- Does not expose GitHub token, MiMo keys, raw prompts, raw model output, or raw
  full diff through public APIs.
- Does not silently fall back to mock results for new tasks.
- Does not execute the external Semgrep binary in the request path; current
  request-time static findings are built-in lightweight rules.

### ai-service

Current status:

- Placeholder only.
- No runtime process is required.
- Not referenced by the active frontend/backend flow.

Future extraction option:

- A later version may extract the existing in-process RAG indexing and provider
  orchestration into a dedicated worker/service. External Semgrep execution
  also remains a possible extraction.

## 5. Review Modes

`CreateReviewTaskRequest` supports two review modes:

| Mode | Trigger | Input |
|---|---|---|
| `MANUAL_DIFF` | explicit mode or non-blank `diffText` | user-pasted unified diff, max 20,000 characters |
| `GITHUB_PR` | no `diffText` by default | GitHub PR metadata + bounded files patch + commit-scoped RAG evidence; bounded changed-file context only when disabled/degraded |

`GITHUB_PR` requires `GITHUB_TOKEN`. Missing token fails fast with
`GITHUB_AUTH_MISSING`; the system does not pretend to review a PR without
context.

## 6. Repository RAG, Fallback, and Static Findings

In the production PostgreSQL profile, `GITHUB_PR` mode binds retrieval to the
resolved PR head SHA and executes the complete RAG path:

```text
github.pr.metadata.load
  -> github.pr.diff.load
  -> rag.index.ensure
  -> rag.query.build
  -> rag.retrieve.hybrid
  -> rag.rerank
  -> rag.context.assemble
  -> static.analysis.findings
```

Index jobs safely resolve a requested branch/ref to a 40-character commit SHA,
checkout only that immutable commit in a controlled JGit workspace, discover
bounded text files, chunk and embed them, and atomically mark a compatible
snapshot READY. Review retrieval filters by repository, commit, model,
dimensions, and index version. Vector and PostgreSQL FTS candidates are fused
with RRF, independently reranked, deduplicated, and assembled into at most 12
labelled evidence chunks and 36,000 characters. Model findings return
`evidenceChunkIds`; commit/path/line/hash validation runs before issue evidence
and comment previews are persisted.

When RAG is disabled by rollout configuration, or fails while
`RAG_FALLBACK_ENABLED=true`, `RepositoryContextIndexService` fetches a bounded
set of changed files through GitHub Contents API at the same head SHA. Trace,
API, and UI identify this as degraded fallback. If fallback is disabled, the
review fails closed. This legacy path is not full-repository or vector RAG.

Static findings are persisted through the same `review_issue` table as MiMo
findings:

| Source | Current role |
|---|---|
| `MIMO` | MiMo dual-agent findings after gate approval |
| `SEMGREP` | request-time Semgrep-style changed-line heuristics |
| `DEPENDENCY` | request-time dependency hygiene checks from bounded full changed manifests in the immutable snapshot, or changed-file fallback context |

The project also keeps `.semgrep.yml` and `scripts/static-scan.mjs` for local
or CI static analysis. That offline toolchain is separate from the request-time
lightweight finding merger.

## 7. MiMo Dual-Agent Flow

```text
AI-1 Planner
  -> TaskPlan JSON

AI-2 Executor
  -> CandidateReview JSON

AI-1 Gatekeeper
  -> GateDecision JSON

MiMoIssueGenerator
  -> deterministic ReviewFinding list
```

Failure behavior:

- Missing planner/executor keys: fail with `MIMO_AUTH_MISSING`.
- Provider request failure: fail with `MIMO_PROVIDER_ERROR`.
- Invalid or rejected structured output: fail fast; do not use mock fallback.

## 8. Persistence Model

The default RAG-disabled local demo uses H2 file storage:

```text
jdbc:h2:file:./data/codereviewx
```

H2 tests use an in-memory database. Production RAG uses PostgreSQL 16 +
pgvector, with Flyway-managed immutable snapshot and retrieval tables.

Core tables:

| Table | Purpose |
|---|---|
| `review_task` | user-visible task, target PR, latest status, latest run pointer |
| `review_run` | one execution attempt for a task |
| `review_issue` | normalized structured review findings |
| `review_input_snapshot` | sanitized GitHub PR metadata and diff summary |
| `review_tool_trace` | ordered tool/agent step timeline |
| `review_provider_trace` | provider selection and normalization summary |
| `review_comment_preview` | local draft comments and publish status |
| `rag_repository` | repository identity, active commit, model contract, and index status |
| `rag_index_job` | persistent leased indexing job, attempts, progress, and safe errors |
| `rag_index_snapshot` | immutable repository/commit/model/version snapshot identity |
| `rag_document` / `rag_chunk` | snapshot-scoped files, chunks, FTS vectors, and embeddings |
| `rag_retrieval_trace` | safe retrieval counts, timings, budget, and degraded status |
| `review_issue_evidence` | validated bounded evidence retained with each issue |

## 9. Security and Privacy Rules

- `.env`, local H2 database files, build output, and dependency folders are
  ignored by git.
- GitHub token and MiMo keys are read from environment variables only.
- Public API responses do not include raw prompts, raw model output, full diff,
  or Authorization headers.
- Comment publish requires selected preview rows and explicit confirmation.
- Local demo credentials must never be committed.

## 10. Known Limits

- No OAuth or GitHub App.
- No team or account model.
- No asynchronous review-execution queue, separately deployed worker,
  cancellation, or progress streaming; RAG indexing does use an in-process
  leased worker with retry.
- No cross-repository knowledge graph, MCP/function-calling tools, or durable
  conversational memory.
- Request-time static findings are lightweight heuristics; external Semgrep is
  still local/CI tooling, not a long-running analysis worker.
- H2 is local-development only. Production RAG uses PostgreSQL 16 + pgvector
  and still requires managed secret storage and deployment access controls.
## 11. Production RAG Boundary

The production profile is a staged pipeline, not a single “RAG call”:

`indexing (Git checkout -> files -> chunks -> embedding -> snapshot)` -> `hybrid retrieval (lexical + pgvector)` -> `RRF + rerank` -> `context/evidence assembly` -> `MiMo generation` -> `evidence validation` -> `comment preview` -> `confirmed GitHub publish`.

Indexing writes only the repository/commit snapshot. Retrieval selects candidates;
rerank orders them; generation proposes findings and never authorizes publication.
Evidence validation checks labels, commit/path/line/hash and persists excerpts;
publish validates confirmation and target metadata and calls GitHub separately.
On model/index failure the bounded changed-file context is the legacy fallback
when `RAG_FALLBACK_ENABLED=true`; otherwise the review fails closed.

Rollout is evaluated per persisted `reviewTaskId`: `floorMod(reviewTaskId,100) <
RAG_REVIEW_PERCENTAGE`. The supported observation gates are 10% (>=20 reviews,
no P0/P1), 50% (>=50 reviews, no P0/P1), then 100%. `RAG_ENABLED=false` or
percentage 0 selects the explicitly labelled legacy path.
