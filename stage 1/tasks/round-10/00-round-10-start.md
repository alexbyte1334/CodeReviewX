# tasks/round-10/00-round-10-start.md

# Round 10 Start: PR / Diff Context v1

## 1. Round Metadata

- Project: CodeReviewX
- Round: Round 10
- Theme: PR / Diff Context v1
- Task Type: Architecture-guided agent context enrichment round
- Primary Goal: 在 Round 09 Xiaomi MiMo provider path 基础上，为 CodeReviewX Review Agent 引入真实或半真实的 diff/context 输入能力，使 agent 不再只依赖 `repoUrl + prNumber`，而是可以基于 changed files / pasted diff 生成更有价值的 review findings。
- Previous Round:
  - Round 09: Xiaomi MiMo AI Review Provider v1
- Previous Final Verdict:
  - `ROUND_09_CLOSED_READY_FOR_ROUND_10`
- First Task To Generate:
  - `tasks/round-10/01-cursor-pr-diff-context-v1.md`

---

## 2. Strategic Context

Round 09 已经完成 CodeReviewX 从 mock review app 到 AI-provider-capable review agent prototype 的关键跃迁。

当前 agent 链路为：

```text
Input -> Context -> Pipeline -> Provider Selection -> Provider -> Prompt -> Model Client -> Parser -> Finding -> Persistence -> API DTO -> Frontend
```

Round 09 后系统具备：

```text
ReviewTaskService
  -> ReviewPipelineService
      -> ConfigurableReviewProvider
          -> MockReviewProvider
          OR
          -> XiaomiMiMoReviewProvider
              -> ReviewPromptBuilder
              -> XiaomiMiMoClient
              -> XiaomiMiMoFindingParser
              -> ReviewFinding[]
  -> persist ReviewFinding as ReviewIssueEntity
  -> compute issueSummary
  -> return unchanged ReviewTaskResponse
```

但是当前 MiMo provider 的主要限制是：

```text
ReviewContext only contains repoUrl + prNumber.
No actual PR diff.
No changed files.
No file patches.
No GitHub API ingestion.
No repository clone.
```

这意味着当前 AI review 仍然是 agent provider path 的验证，而不是实质代码变更审查。

Round 10 的目标是补齐 agent 的输入上下文层，使后续 review findings 可以基于真实 diff 内容产生。

---

## 3. Round 10 Product Positioning

Round 10 应被描述为：

```text
CodeReviewX Review Agent now supports optional diff/context input for more meaningful AI review.
```

不要过度宣传为：

```text
CodeReviewX fully integrates with GitHub and automatically reviews real pull requests.
```

正确表述：

```text
Round 10 introduces PR/diff context v1 through manual pasted diff or structured diff input.
GitHub API ingestion remains planned unless explicitly scoped and completed.
```

---

## 4. Recommended Scope Decision

Round 10 推荐采用：

```text
Manual pasted diff input first.
GitHub API second.
```

原因：

1. 当前最重要的是让 MiMo provider 获得真实 review material；
2. GitHub auth、rate limit、repo permissions、private repo access 会显著拉长交付周期；
3. manual diff input 可以快速验证 agent prompt、parser、persistence、frontend 体验；
4. 这符合 3 到 5 个 round 内完成整体项目的节奏；
5. 后续再接 GitHub API 时，可以复用本轮的 `PullRequestContext` / `ChangedFile` / `FileDiff` 模型。

---

## 5. Round 10 Primary Goal

Implement:

```text
PR / Diff Context v1
```

Target internal behavior:

```text
User submits repoUrl + prNumber + optional diff/context
  -> ReviewTaskService creates ReviewTaskEntity
  -> ReviewContext is built with diff context
  -> ReviewPipelineService runs
  -> provider selected by config
  -> XiaomiMiMoReviewProvider builds prompt with diff context
  -> XiaomiMiMoClient calls MiMo API
  -> Parser converts structured JSON to ReviewFinding[]
  -> Findings persist as ReviewIssueEntity
  -> issueSummary/risk computed from persisted issues
  -> Existing frontend renders result
```

Mock mode must remain stable.

If no diff is provided:

```text
System should retain Round 09 behavior.
```

If diff is provided:

```text
Prompt should include diff context.
MiMo review should be grounded in the provided changed files / patch text.
```

---

## 6. Agent Structure Update

Round 10 updates the agent structure from:

```text
Input:
  repoUrl + prNumber
```

to:

```text
Input:
  repoUrl + prNumber + optional diff/context
```

Updated structure:

```text
CodeReviewX Review Agent
├── Input Layer
│   └── CreateReviewTaskRequest
│       ├── repoUrl
│       ├── prNumber
│       └── optional diff/context
├── Context Layer
│   └── ReviewContext
│       ├── taskId
│       ├── repoUrl
│       ├── prNumber
│       └── PullRequestContext / diffText / changedFiles
├── Orchestration Layer
│   └── ReviewPipelineService
├── Provider Selection Layer
│   └── ConfigurableReviewProvider
├── Provider Layer
│   ├── MockReviewProvider
│   └── XiaomiMiMoReviewProvider
├── Prompt Layer
│   └── ReviewPromptBuilder
│       └── includes diff context when available
├── Model Client Layer
│   └── XiaomiMiMoClient
├── Output Parsing Layer
│   └── XiaomiMiMoFindingParser
├── Normalization Layer
│   └── ReviewFinding
├── Persistence Layer
│   ├── ReviewTaskEntity
│   └── ReviewIssueEntity
└── Presentation Layer
    └── ReviewTaskResponse + frontend issue cards
```

Runtime flow:

```text
User submits repoUrl + prNumber + optional pasted diff
  -> ReviewTaskEntity persisted
  -> ReviewContext built with PullRequestContext
  -> ReviewPipelineService runs
  -> ConfigurableReviewProvider selects mock or MiMo
  -> MiMo prompt includes actual diff context
  -> MiMo returns structured findings
  -> Parser maps findings
  -> Findings persist
  -> API response remains stable
  -> Frontend renders summary and issue cards
```

---

## 7. API Design Direction

Round 10 should preserve existing endpoints:

```text
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

Existing create request:

```json
{
  "repoUrl": "https://github.com/example/demo",
  "prNumber": 10
}
```

Recommended Round 10 create request extension:

```json
{
  "repoUrl": "https://github.com/example/demo",
  "prNumber": 10,
  "diffText": "diff --git a/src/App.tsx b/src/App.tsx\n..."
}
```

Alternative structured shape:

```json
{
  "repoUrl": "https://github.com/example/demo",
  "prNumber": 10,
  "pullRequestContext": {
    "diffText": "diff --git a/src/App.tsx b/src/App.tsx\n..."
  }
}
```

Preferred for Round 10 speed:

```text
Add optional top-level diffText.
```

Reason:

1. minimal frontend change;
2. minimal backend DTO change;
3. avoids new nested validation complexity;
4. can later evolve into structured `PullRequestContext`.

Important compatibility rule:

```text
diffText must be optional.
Existing clients that send only repoUrl + prNumber must still work.
```

---

## 8. Backend Data Model Direction

Round 10 should avoid large database redesign.

Recommended minimal persistence:

```text
Persist optional diffText on ReviewTaskEntity only if simple and safe.
```

However, to keep scope tight, there are two acceptable options.

### Option A: Persist diffText on ReviewTaskEntity

Pros:

1. restart can preserve task review context;
2. detail page can show context metadata later;
3. better debugging and reproducibility.

Cons:

1. adds one DB column;
2. may require handling large text.

Suggested field:

```java
@Lob
@Column(name = "diff_text")
private String diffText;
```

or equivalent.

### Option B: Do not persist raw diffText yet

Pros:

1. no DB schema change;
2. faster and less risky.

Cons:

1. review context not reproducible after restart;
2. task detail cannot show original diff;
3. less useful for audit/debug.

Preferred decision:

```text
Option A: persist optional diffText as text/lob if current H2/JPA setup supports it safely.
```

But do not introduce:

```text
ChangedFileEntity
FileDiffEntity
ExecutionTraceEntity
ProviderInputEntity
RawPromptEntity
RawModelOutputEntity
```

Round 10 should not become a data-model expansion round.

---

## 9. ReviewContext Update

Extend `ReviewContext` to carry optional diff context.

Minimal shape:

```text
ReviewContext
  taskId
  repoUrl
  prNumber
  createdAt
  diffText?
```

Alternative clean shape:

```text
ReviewContext
  taskId
  repoUrl
  prNumber
  createdAt
  PullRequestContext?
```

Where:

```text
PullRequestContext
  diffText
```

Preferred if simple:

```text
PullRequestContext
```

because Round 11 may add:

```text
changedFiles
baseSha
headSha
title
description
author
sourceBranch
targetBranch
```

Do not overbuild in Round 10.

---

## 10. Prompt Builder Requirements

Update `ReviewPromptBuilder` so it can produce two prompt variants.

### 10.1 Without Diff

If no diff is provided, keep Round 09 behavior:

```text
Current available context does not include the actual PR diff yet.
Return findings only if you can identify meaningful risks from the provided context.
```

### 10.2 With Diff

If diff is provided, prompt must include:

```text
The following PR diff is provided and should be used as the primary review context.
```

Prompt should instruct the model to:

1. review changed lines and nearby context;
2. identify security, reliability, maintainability, performance, test, style, and bug risks;
3. cite file paths and line numbers when possible;
4. avoid inventing files not present in the diff;
5. return only strict JSON;
6. use backend enum values only;
7. return `[]` if no meaningful findings;
8. not wrap JSON in markdown fences.

Recommended user prompt with diff:

```text
Review this pull request.

repoUrl: <repoUrl>
prNumber: <prNumber>

The following PR diff is provided and should be used as the primary review context.

<diff>
...
</diff>

Return only a JSON array with objects using this schema:
[
  {
    "issueKey": "MIMO-ISSUE-1",
    "severity": "HIGH|MEDIUM|LOW",
    "category": "BUG|SECURITY|PERFORMANCE|MAINTAINABILITY|STYLE|TEST",
    "filePath": "string",
    "startLine": 1,
    "endLine": 1,
    "title": "string",
    "description": "string",
    "recommendation": "string"
  }
]

Rules:
- Return only JSON.
- Do not wrap output in markdown.
- Use only files and code present in the provided diff.
- Prefer line numbers from the changed hunk when possible.
- If unsure, use the file path and line number 1 rather than inventing exact lines.
- If there are no meaningful findings, return [].
```

---

## 11. Diff Size Guardrails

Because model context is limited and frontend users may paste large diffs, add basic safeguards.

Recommended:

```text
Max diffText length: 20,000 characters
```

or a similar reasonable value.

Behavior:

```text
If diffText exceeds max length:
  return validation error
```

or:

```text
truncate safely and document truncation
```

Preferred for Round 10:

```text
Validation error if too large.
```

Reason:

1. avoids hidden prompt truncation;
2. preserves deterministic behavior;
3. keeps user aware.

Suggested validation message:

```text
diffText is too large. Maximum length is 20000 characters.
```

Keep this error in existing API error wrapper style.

Also validate:

```text
Blank diffText should be treated as absent.
Whitespace-only diffText should be treated as absent.
```

---

## 12. Mock Provider Behavior

Mock mode should remain deterministic.

Acceptable behavior:

```text
MockReviewProvider ignores diffText and returns the same 3 deterministic issues.
```

Alternative:

```text
MockReviewProvider includes slightly more realistic file paths if diffText is present.
```

Preferred:

```text
Do not change MockReviewProvider findings in Round 10.
```

Reason:

1. preserves tests;
2. avoids accidental fixture churn;
3. keeps fallback deterministic.

---

## 13. Xiaomi MiMo Provider Behavior

MiMo provider should use diff context when available.

Expected:

```text
if diffText exists:
  ReviewPromptBuilder includes diffText
else:
  ReviewPromptBuilder uses Round 09 limited-context prompt
```

Fallback remains unchanged:

```text
provider=mimo + missing key -> mock
provider=mimo + client failure -> mock
provider=mimo + parser failure -> mock
valid [] -> successful zero findings
```

Do not expose diffText, prompt, or raw model output in public response.

---

## 14. Public API Response Rules

Do not add diffText to `ReviewTaskResponse` unless necessary.

Preferred:

```text
Create request accepts diffText.
Response remains unchanged.
```

Reason:

1. minimizes frontend/API churn;
2. avoids returning large raw diff;
3. avoids exposing provider input;
4. preserves existing UI.

If a small indicator is desired, consider later:

```text
hasDiffContext: true
```

But do not add it in Round 10 unless clearly needed.

Strictly preserve:

```text
ReviewTaskResponse.riskLevel == ReviewTaskResponse.issueSummary.riskLevel
```

---

## 15. Frontend Scope

Round 10 frontend change should be minimal.

Add an optional textarea to the create task form:

```text
Optional PR diff
```

Suggested helper copy:

```text
Paste a unified diff to let the AI review actual code changes. Leave empty to use repo URL and PR number only.
```

Do not redesign page.

Allowed frontend changes:

1. add optional `diffText` state;
2. include `diffText` in create request only when non-blank;
3. add max-length client-side validation if backend max is known;
4. update type definitions;
5. update tests.

Forbidden:

1. new UI library;
2. design system migration;
3. route overhaul;
4. dashboard redesign;
5. Monaco editor;
6. syntax highlighting library;
7. diff viewer;
8. chart library.

Final visual polish remains for a later round.

---

## 16. Backend Tests Required

Add or update tests.

### 16.1 Request Validation Tests

Verify:

1. existing request without diff still works;
2. request with `diffText` works;
3. blank diffText treated as absent;
4. whitespace-only diffText treated as absent;
5. too-large diffText returns validation error;
6. repoUrl/prNumber validation unchanged.

### 16.2 ReviewContext Tests

Verify:

1. `ReviewContext` includes diff context when provided;
2. `ReviewContext` has no diff context when omitted;
3. no null pointer risk in pipeline/provider.

### 16.3 Prompt Builder Tests

Verify:

1. no-diff prompt preserves Round 09 limitation wording;
2. diff prompt includes diffText;
3. diff prompt says diff is primary review context;
4. diff prompt includes strict JSON schema;
5. diff prompt includes actual backend enum values;
6. diff prompt says not to invent files outside diff;
7. blank diff does not create diff prompt.

### 16.4 Provider / Pipeline Tests

Verify:

1. mock mode still returns 3 deterministic mock issues with or without diff;
2. MiMo provider receives diff prompt when diff is present;
3. MiMo provider receives no-diff prompt when diff absent;
4. missing-key fallback still works with diff present;
5. parser behavior unchanged;
6. valid empty MiMo output still means zero findings;
7. task creation still succeeds under fallback.

### 16.5 Persistence Tests

If `diffText` is persisted:

1. saved task stores diffText;
2. restart or repository reload preserves diffText;
3. response does not expose diffText unless intentionally added;
4. existing tasks without diff remain readable.

If `diffText` is not persisted:

1. document why;
2. ensure no expectation of replay after restart.

### 16.6 API Contract Tests

Verify:

1. old request shape still works;
2. new optional field does not change response wrapper;
3. `ReviewTaskResponse` fields preserved;
4. `ReviewIssueResponse` fields preserved;
5. `IssueSummaryResponse` fields preserved;
6. risk invariant preserved.

---

## 17. Frontend Tests Required

Run:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

Add/update tests for:

1. create form renders optional diff textarea;
2. submitting without diff sends existing request shape or equivalent;
3. submitting with diff sends `diffText`;
4. too-large diff blocks submit or shows validation if implemented client-side;
5. existing list/detail render unchanged;
6. issue cards still render `MOCK` and `MIMO`.

---

## 18. Runtime Verification Requirements

### 18.1 Mock Mode Without Diff

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

Create:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/round-10-no-diff",
    "prNumber": 10
  }'
```

Confirm:

```text
success=true
issues.length=3
source=MOCK
riskLevel=HIGH
riskLevel == issueSummary.riskLevel
```

### 18.2 Mock Mode With Diff

Create:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/round-10-with-diff",
    "prNumber": 10,
    "diffText": "diff --git a/src/App.tsx b/src/App.tsx\n+const password = request.query.password;\n"
  }'
```

Confirm:

```text
success=true
issues.length=3
source=MOCK
response shape unchanged
riskLevel == issueSummary.riskLevel
```

### 18.3 MiMo Mode Without Key With Diff

```bash
unset MIMO_API_KEY

cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run \
  -Dspring-boot.run.arguments="--codereviewx.review.provider=mimo"
```

Create with diff.

Confirm:

```text
task creation succeeds
fallback to MOCK
response shape unchanged
no diffText returned unless intentionally added
no stack trace
no fallback reason
riskLevel == issueSummary.riskLevel
```

### 18.4 MiMo Mode With Key With Diff

If local key is available:

```bash
export MIMO_API_KEY="<local-secret-not-committed>"

cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run \
  -Dspring-boot.run.arguments="--codereviewx.review.provider=mimo"
```

Create task with a small diff.

Confirm one of:

```text
MiMo success:
  issues source=MIMO or zero findings
  response shape unchanged
  riskLevel == issueSummary.riskLevel

Safe fallback:
  issues source=MOCK
  response shape unchanged
  no secret leakage
```

Do not print the key.

### 18.5 Browser Smoke

Run frontend:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Confirm:

1. backend status UP;
2. create task without diff works;
3. create task with diff works;
4. list updates;
5. detail renders;
6. summary panel renders;
7. issue cards render;
8. no browser console errors.

---

## 19. README Update Requirements

Update root README and backend README.

Must document:

1. Round 10 introduces optional diff context input;
2. existing repoUrl + prNumber flow still works;
3. diffText is optional;
4. if provided, MiMo prompt uses it as primary review context;
5. default mock mode remains stable;
6. MiMo fallback behavior unchanged;
7. public response does not expose raw prompt/model output;
8. current limitation: manual pasted diff, not automatic GitHub ingestion unless implemented;
9. max diff size if validation added;
10. next direction after Round 10.

Correct wording:

```text
Round 10 adds optional pasted PR diff context so the review agent can ground prompts in actual code changes.
```

Incorrect wording:

```text
CodeReviewX now automatically fetches and reviews GitHub PRs.
```

unless GitHub fetching is actually implemented, which is not the primary scope.

---

## 20. Required Agent Structure Documentation

Every Cursor/Codex/Qoder handoff in Round 10 must include:

```markdown
## Agent Structure and Flow
```

It must show:

```text
Input -> Context -> Pipeline -> Provider Selection -> Provider -> Prompt -> Model Client -> Parser -> Finding -> Persistence -> API DTO -> Frontend
```

For Round 10, use:

```text
Input:
  repoUrl + prNumber + optional diffText

Context:
  ReviewContext with optional PullRequestContext/diffText

Orchestrator:
  ReviewPipelineService

Provider:
  MockReviewProvider or XiaomiMiMoReviewProvider

Prompt:
  ReviewPromptBuilder includes diff when available

Model:
  Xiaomi MiMo API

Output:
  ReviewFinding[]

Persistence:
  ReviewTaskEntity + ReviewIssueEntity

Presentation:
  ReviewTaskResponse + frontend issue cards
```

---

## 21. Non-goals

Do not implement unless explicitly scoped and trivial:

1. GitHub OAuth;
2. GitHub App installation;
3. private repo access;
4. automatic PR fetching;
5. repository clone;
6. Semgrep;
7. full multi-agent planner;
8. async job queue;
9. streaming;
10. trace UI;
11. provider registry UI;
12. auth;
13. organization/team model;
14. dashboard analytics;
15. frontend redesign;
16. Monaco editor;
17. syntax highlighting;
18. visual diff viewer;
19. production DB migration framework;
20. deployment/CI/CD.

Round 10 is context enrichment, not full GitHub platform integration.

---

## 22. Acceptance Criteria

### 22.1 Backend

- [ ] `CreateReviewTaskRequest` accepts optional diff context;
- [ ] existing request without diff still works;
- [ ] backend validates oversized diff;
- [ ] blank diff treated as absent;
- [ ] `ReviewContext` carries diff context;
- [ ] prompt builder includes diff when present;
- [ ] prompt builder preserves no-diff behavior when absent;
- [ ] mock mode remains deterministic;
- [ ] MiMo mode uses diff prompt when available;
- [ ] fallback behavior unchanged;
- [ ] parser behavior unchanged;
- [ ] API response shape remains stable;
- [ ] risk invariant preserved;
- [ ] persistence behavior documented and tested.

### 22.2 Agent Structure

- [ ] handoff includes `Agent Structure and Flow`;
- [ ] input/context/pipeline/provider/prompt/model/parser/persistence/presentation chain documented;
- [ ] current capability vs future GitHub ingestion boundary clear;
- [ ] no overclaiming of automatic GitHub PR review.

### 22.3 Frontend

- [ ] optional diff textarea added;
- [ ] submitting without diff works;
- [ ] submitting with diff works;
- [ ] frontend typecheck passes;
- [ ] frontend build passes;
- [ ] frontend tests pass;
- [ ] no redesign or new UI library.

### 22.4 Tests

- [ ] backend tests pass;
- [ ] frontend tests pass;
- [ ] prompt builder tests cover diff and no-diff;
- [ ] request validation tests cover max size;
- [ ] provider fallback tests cover diff present;
- [ ] API contract tests pass;
- [ ] persistence tests pass if diff is persisted.

### 22.5 Runtime

- [ ] mock mode without diff works;
- [ ] mock mode with diff works;
- [ ] MiMo mode without key with diff safely falls back;
- [ ] MiMo mode with key with diff is verified if key available;
- [ ] browser smoke works.

### 22.6 Documentation

- [ ] README documents optional diff context;
- [ ] backend README documents request example with diffText;
- [ ] max diff size documented;
- [ ] current limitation documented;
- [ ] no overclaiming of automatic GitHub integration.

---

## 23. Suggested Round 10 Task Sequence

Continue Cursor → Codex → Qoder.

### 23.1 Cursor Implementation

Generate:

```text
tasks/round-10/01-cursor-pr-diff-context-v1.md
```

Responsibilities:

1. inspect Round 09 code;
2. design minimal optional diff input;
3. update create request DTO;
4. update validation;
5. update ReviewContext / PullRequestContext;
6. decide whether to persist diffText;
7. update ReviewTaskService context building;
8. update ReviewPromptBuilder;
9. preserve mock provider behavior;
10. preserve MiMo fallback;
11. update frontend create form with optional textarea;
12. add backend tests;
13. add frontend tests;
14. run validation;
15. runtime verify mock no-diff, mock with-diff, MiMo missing-key with-diff;
16. update README;
17. include Agent Structure and Flow in handoff;
18. output Cursor handoff.

### 23.2 Codex Validation

Generate:

```text
tasks/round-10/02-codex-pr-diff-context-v1-validation.md
```

Responsibilities:

1. independently inspect diff context implementation;
2. verify API backward compatibility;
3. verify diff validation;
4. verify prompt contains diff only when present;
5. verify no prompt/model/diff leakage in response;
6. verify fallback with diff present;
7. verify persistence behavior;
8. run backend/frontend tests;
9. runtime verify modes;
10. browser smoke;
11. scope creep check;
12. README accuracy check;
13. verify Agent Structure and Flow;
14. output Codex handoff.

### 23.3 Qoder Independent Review

Generate:

```text
tasks/round-10/03-qoder-pr-diff-context-v1-independent-review.md
```

Responsibilities:

1. independently judge whether Round 10 successfully gives agent meaningful context;
2. verify API and frontend remain stable;
3. verify no overclaiming of GitHub integration;
4. verify prompt grounding quality;
5. verify fallback and secret handling remain safe;
6. decide whether Round 10 can close;
7. recommend exact Round 11 direction;
8. output Qoder handoff.

---

## 24. Recommended Round 11 Direction

If Round 10 succeeds, Round 11 should likely be one of:

```text
Option A: Live MiMo Provider Hardening + Timeout + Provider Observability
```

or:

```text
Option B: Frontend UX Polish + Agent Result Presentation
```

Given the project constraint that the full project should finish within 3 to 5 remaining rounds, recommended sequence:

```text
Round 10: PR / Diff Context v1
Round 11: Frontend UX polish + result presentation cleanup + provider copy cleanup
Round 12: Final hardening, live MiMo verification, README/demo readiness
```

If live MiMo is still not verified by the end of Round 10, Round 11 must include:

```text
Live MiMo verification with environment-only MIMO_API_KEY
Explicit HTTP timeout config
Sanitized provider failure classification
Final copy cleanup from "Mock UI" to review-agent wording
```

---

## 25. Final Instruction for Round 10

Round 10 must make CodeReviewX substantively more useful as a review agent by giving it optional PR/diff context.

Essential instruction:

```text
Add optional diff context input.
Preserve existing repoUrl + prNumber flow.
Keep mock mode as safe default.
Keep Xiaomi MiMo provider and fallback behavior unchanged.
Ground MiMo prompts in provided diff when available.
Do not expose raw diff/prompt/model output in public API unless explicitly chosen and documented.
Avoid GitHub integration scope creep.
Show the updated agent structure and execution flow in every handoff.
```

First task to generate:

```text
tasks/round-10/01-cursor-pr-diff-context-v1.md
```