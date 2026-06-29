# CodeReviewX Architecture

> Current architecture, aligned with the implementation in this repository.
> Historical plans for a separate Python `ai-service` are documented as future
> expansion, not as the current runtime shape.

## 1. Product Boundary

CodeReviewX is a locally runnable AI-assisted pull request review MVP. It is
designed for portfolio and interview demonstration: a user submits a GitHub PR
or pasted unified diff, the system runs a bounded AI review, persists evidence,
and lets the user manually publish selected comment previews back to GitHub.

The current implementation is intentionally not a production SaaS. It has no
multi-user auth, no GitHub App installation flow, no queue worker, no full
repository clone, and no semantic/vector RAG or long-term memory system. It
does include a bounded changed-file repository context index for GitHub PR
reviews.

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
| - Repository context index    |
| - MiMo dual-agent provider    |
| - Static finding merger       |
| - trace/snapshot persistence  |
| - comment preview publisher   |
+------+------------------------+
       |
       | JPA / Flyway
       v
+-------------------------------+
| H2 file database              |
| local runtime persistence     |
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
        +-- GITHUB_PR: github.pr.metadata.load -> github.pr.diff.load
        |              -> repository.context.index
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
- Load bounded changed-file contents at the PR head SHA for a lightweight
  lexical repository context index.
- Generate Semgrep-style and dependency-hygiene findings and persist them as
  normal review issues with explicit `source` provenance.
- Execute the MiMo dual-agent review workflow.
- Normalize approved model output into structured issues.
- Persist review tasks, runs, issues, traces, sanitized snapshots, and comment
  previews.
- Publish selected comment previews to GitHub only after explicit user
  confirmation.

Boundaries:

- Does not clone repositories or run full repository analysis.
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

- A later version may move full repository cloning/indexing, external Semgrep
  execution, semantic/vector RAG, and provider orchestration into a dedicated
  service. If that happens, this architecture document should be updated before
  code is split.

## 5. Review Modes

`CreateReviewTaskRequest` supports two review modes:

| Mode | Trigger | Input |
|---|---|---|
| `MANUAL_DIFF` | explicit mode or non-blank `diffText` | user-pasted unified diff, max 20,000 characters |
| `GITHUB_PR` | no `diffText` by default | GitHub PR metadata + bounded files patch + bounded changed-file context |

`GITHUB_PR` requires `GITHUB_TOKEN`. Missing token fails fast with
`GITHUB_AUTH_MISSING`; the system does not pretend to review a PR without
context.

## 6. Repository Context and Static Findings

`GITHUB_PR` mode performs a lightweight repository context pass after the PR
diff is loaded:

```text
github.pr.metadata.load
  -> github.pr.diff.load
  -> repository.context.index
  -> static.analysis.findings
```

The context index fetches selected changed files from GitHub Contents API at
the PR head SHA. It is bounded by file count, per-file bytes, and total context
bytes. Removed files and non-text-like files are skipped. The resulting context
is appended to the MiMo executor prompt and is not exposed through public APIs.

This is intentionally a lexical changed-file context index. It is not a full
repository clone, not full dependency graph analysis, and not semantic/vector
RAG.

Static findings are persisted through the same `review_issue` table as MiMo
findings:

| Source | Current role |
|---|---|
| `MIMO` | MiMo dual-agent findings after gate approval |
| `SEMGREP` | request-time Semgrep-style changed-line heuristics |
| `DEPENDENCY` | request-time dependency hygiene checks from indexed files |

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

Runtime uses H2 file storage:

```text
jdbc:h2:file:./data/codereviewx
```

Tests use in-memory H2.

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
- No async queue, retry worker, cancellation, or progress streaming.
- No full repository indexing or clone-based analysis.
- No semantic/vector RAG, MCP, function-calling tools, or durable memory.
- Request-time static findings are lightweight heuristics; external Semgrep is
  still local/CI tooling, not a long-running analysis worker.
- H2 is for local development; production would need PostgreSQL or MySQL plus
  managed secret storage.
