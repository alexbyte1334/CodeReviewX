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
repository clone, and no RAG or long-term memory system.

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
| - MiMo dual-agent provider    |
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
- Execute the MiMo dual-agent review workflow.
- Normalize approved model output into structured issues.
- Persist review tasks, runs, issues, traces, sanitized snapshots, and comment
  previews.
- Publish selected comment previews to GitHub only after explicit user
  confirmation.

Boundaries:

- Does not clone repositories.
- Does not expose GitHub token, MiMo keys, raw prompts, raw model output, or raw
  full diff through public APIs.
- Does not silently fall back to mock results for new tasks.
- Does not execute Semgrep inside the main review task pipeline today.

### ai-service

Current status:

- Placeholder only.
- No runtime process is required.
- Not referenced by the active frontend/backend flow.

Future extraction option:

- A later version may move repository context loading, Semgrep execution, RAG,
  and provider orchestration into a dedicated service. If that happens, this
  architecture document should be updated before code is split.

## 5. Review Modes

`CreateReviewTaskRequest` supports two review modes:

| Mode | Trigger | Input |
|---|---|---|
| `MANUAL_DIFF` | explicit mode or non-blank `diffText` | user-pasted unified diff, max 20,000 characters |
| `GITHUB_PR` | no `diffText` by default | GitHub PR metadata + bounded files patch |

`GITHUB_PR` requires `GITHUB_TOKEN`. Missing token fails fast with
`GITHUB_AUTH_MISSING`; the system does not pretend to review a PR without
context.

## 6. MiMo Dual-Agent Flow

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

## 7. Persistence Model

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

## 8. Security and Privacy Rules

- `.env`, local H2 database files, build output, and dependency folders are
  ignored by git.
- GitHub token and MiMo keys are read from environment variables only.
- Public API responses do not include raw prompts, raw model output, full diff,
  or Authorization headers.
- Comment publish requires selected preview rows and explicit confirmation.
- Local demo credentials must never be committed.

## 9. Known Limits

- No OAuth or GitHub App.
- No team or account model.
- No async queue, retry worker, cancellation, or progress streaming.
- No full repository indexing or clone-based analysis.
- No RAG, MCP, function-calling tools, or durable memory.
- Semgrep exists as project static-analysis tooling, not as a persisted finding
  source in the review task pipeline.
- H2 is for local development; production would need PostgreSQL or MySQL plus
  managed secret storage.
