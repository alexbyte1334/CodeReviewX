# CodeReviewX Stage 1.5 / Stage 2 Architect Execution Plan

## 1. Architect Decision

Stage 1 is closed.

```text
Final Stage 1 Verdict:
  QODER_ROUND_12_APPROVED_FINAL_MVP_DELIVERY
```

The project now moves into:

```text
Stage 1.5:
  Productization & Live Provider Enablement

Stage 2:
  Real Engineering Agent Capability Buildout
```

Stage 1.5 must finish before Stage 2 implementation begins.

## 2. Current Product Boundary

Current correct product name:

```text
Manual Diff-Grounded AI Code Review Agent MVP
```

Current supported flow:

```text
repoUrl + prNumber + optional diffText
  -> ReviewContext
  -> ReviewPipelineService
  -> ConfigurableReviewProvider
  -> MockReviewProvider or XiaomiMiMoReviewProvider
  -> ReviewFinding[]
  -> ReviewTaskEntity + ReviewIssueEntity
  -> ReviewTaskResponse
  -> React frontend summary and issue cards
```

Current explicit non-capabilities:

```text
No automatic GitHub PR fetching
No GitHub App integration
No private repository access
No repository clone
No full repository analysis
No PR comment write-back
No RAG
No MCP
No Function Calling
No Memory
No production auth/team model
```

Agents must not claim these as implemented.

## 3. Agent Operating Model

The Stage 1 agent sequence remains active:

```text
Cursor implements
Codex validates repository-wide
Qoder independently reviews
Architect decides round closure
```

Role boundaries:

```text
Cursor:
  primary implementation agent for scoped feature rounds

Codex:
  repository-level validation, tests, smoke checks, minimal targeted fixes

Qoder:
  read-only independent review and final risk judgment
```

No agent should hand off directly to another agent. The architect controls every transition.

## 4. Stage 1.5 Round Plan

### Round 13

```text
Theme:
  Product UI Restyle — macOS-like polished delivery interface

Primary owner:
  Cursor

Validation:
  Codex

Independent review:
  Qoder
```

Goal:

```text
Upgrade the frontend from a demo-like interface into a polished product shell while preserving the existing backend API contract and review flow.
```

Task docs:

```text
stage 1.5/tasks/round-13/00-round-13-start.md
stage 1.5/tasks/round-13/01-cursor-product-ui-restyle.md
stage 1.5/tasks/round-13/02-codex-product-ui-restyle-validation.md
stage 1.5/tasks/round-13/03-qoder-product-ui-restyle-independent-review.md
```

### Round 14

```text
Theme:
  Live MiMo Verification + Git/GitHub Repository Sync
```

Goal:

```text
Verify Xiaomi MiMo with a real local MIMO_API_KEY when available, harden provider status/documentation, initialize a clean Git repository, and prepare GitHub synchronization without committing secrets or generated artifacts.
```

Round 14 starts only after Qoder approves Round 13.

## 5. Stage 2 Round Plan

### Round 15

```text
Theme:
  Stage 2 Architecture Planning — GitHub PR Ingestion and Agent v2 Design
```

Goal:

```text
Design the Stage 2 architecture before implementation: GitHub ingestion, repository context loading, review policy, tool/function boundary, persistence extensions, security model, and traceability.
```

No Stage 2 implementation begins until Round 15 design is approved.

### Round 16

```text
Theme:
  GitHub PR Ingestion v1
```

Goal:

```text
Add controlled automatic PR metadata and diff loading, replacing manual pasted diff as the primary path while preserving manual fallback.
```

### Round 17

```text
Theme:
  Engineering Agent v1
```

Goal:

```text
Introduce project rules, review policy, tool abstraction, and traceable review runs.
```

## 6. Global Guardrails

Security:

```text
Never commit MIMO_API_KEY
Never commit .env
Never expose raw prompt
Never expose raw model output
Never expose raw diffText in public API response
Never print secrets in handoffs
```

Repository hygiene:

```text
Do not commit node_modules/
Do not commit backend-java/target/
Do not commit frontend/dist/
Do not commit backend-java/data/*.db
Do not commit local IDE/cache files
```

Stage boundary:

```text
Stage 1.5 improves product quality, live provider verification, and repository hygiene.
Stage 2 adds real engineering agent capabilities.
Do not mix these stages in one round.
```

