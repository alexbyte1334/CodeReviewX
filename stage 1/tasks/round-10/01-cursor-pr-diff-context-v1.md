# tasks/round-10/01-cursor-pr-diff-context-v1.md

# Round 10 / Cursor Task: PR Diff Context v1 Implementation

## 1. Role

You are Cursor, the implementation agent for CodeReviewX Round 10.

Your job is to implement **PR / Diff Context v1**: allow users to optionally paste a PR diff so the review agent can ground AI review prompts in actual code changes.

Keep the implementation minimal, stable, and compatible with the existing Round 09 Xiaomi MiMo provider path.

---

## 2. Product Goal

Round 10 must upgrade CodeReviewX from:

```text
repoUrl + prNumber only
```

to:

```text
repoUrl + prNumber + optional diffText
```

Expected behavior:

```text
User submits repoUrl + prNumber + optional diffText
  -> ReviewTaskService creates task
  -> ReviewContext carries optional diff context
  -> ReviewPipelineService runs
  -> ConfigurableReviewProvider selects Mock or Xiaomi MiMo
  -> Xiaomi MiMo prompt includes diffText when present
  -> Parser returns ReviewFinding[]
  -> Findings persist as ReviewIssueEntity
  -> API response shape remains stable
  -> Frontend renders existing result UI
```

Do **not** implement automatic GitHub PR fetching in this round.

---

## 3. Non-Goals

Do not implement:

```text
GitHub OAuth
GitHub App
private repo access
automatic PR fetching
repository clone
Semgrep
async job queue
trace UI
provider registry UI
auth
dashboard redesign
Monaco editor
syntax highlighting
visual diff viewer
CI/CD
```

Round 10 is **manual pasted diff context**, not full GitHub integration.

---

## 4. Backend Requirements

### 4.1 Extend Create Request

Update the create review task request DTO to accept:

```json
{
  "repoUrl": "https://github.com/example/demo",
  "prNumber": 10,
  "diffText": "diff --git a/src/App.tsx b/src/App.tsx\n..."
}
```

Rules:

```text
diffText is optional.
Existing requests with only repoUrl + prNumber must still work.
Blank or whitespace-only diffText must be treated as absent.
```

### 4.2 Add Diff Size Guardrail

Add backend validation:

```text
Maximum diffText length: 20000 characters
```

If exceeded, return a validation error using the existing API error style.

Suggested message:

```text
diffText is too large. Maximum length is 20000 characters.
```

### 4.3 ReviewContext Update

Extend ReviewContext to carry optional diff context.

Preferred minimal shape:

```text
ReviewContext
  taskId
  repoUrl
  prNumber
  createdAt
  diffText?
```

Alternative acceptable shape:

```text
ReviewContext
  taskId
  repoUrl
  prNumber
  createdAt
  PullRequestContext?
    diffText
```

Choose the simpler approach that best fits the current codebase.

### 4.4 Persistence

Prefer persisting optional diffText on ReviewTaskEntity if simple and safe:

```java
@Lob
@Column(name = "diff_text")
private String diffText;
```

Requirements:

```text
Persist diffText if added.
Do not expose raw diffText in public API response.
Do not create ChangedFileEntity, FileDiffEntity, ExecutionTraceEntity, RawPromptEntity, or RawModelOutputEntity.
```

If you decide not to persist diffText, document the reason in the handoff.

### 4.5 ReviewTaskService

Update task creation flow:

```text
Normalize diffText.
Persist it if persistence is implemented.
Build ReviewContext with diffText when present.
Keep no-diff behavior unchanged.
```

### 4.6 Prompt Builder

Update ReviewPromptBuilder to produce two variants.

Without diff:

```text
Preserve Round 09 limited-context behavior.
The prompt should clearly state that actual PR diff is not available.
```

With diff:

Prompt must include:

```text
The following PR diff is provided and should be used as the primary review context.
```

The prompt must instruct the model to:

```text
Review changed lines and nearby context.
Identify security, reliability, maintainability, performance, test, style, and bug risks.
Use only files/code present in the provided diff.
Avoid inventing files.
Prefer changed-hunk line numbers when possible.
Return only strict JSON.
Do not wrap JSON in markdown.
Use backend enum values only.
Return [] if no meaningful findings.
```

Required output schema:

```json
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
```

### 4.7 Provider Behavior

Mock provider:

```text
Keep deterministic behavior unchanged.
It may ignore diffText.
It should still return the same 3 mock issues.
```

Xiaomi MiMo provider:

```text
Use diff prompt when diffText is present.
Use no-diff prompt when absent.
Keep all fallback behavior unchanged.
```

Fallback behavior must remain:

```text
provider=mimo + missing key -> mock fallback
provider=mimo + client failure -> mock fallback
provider=mimo + parser failure -> mock fallback
valid [] from MiMo -> successful zero findings
```

Do not leak:

```text
MIMO_API_KEY
raw prompt
raw model output
raw diffText
fallback internals
```

---

## 5. Public API Contract

Preserve existing endpoints:

```text
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

Response shape should remain unchanged unless absolutely necessary.

Do not add diffText to ReviewTaskResponse.

Must preserve invariant:

```text
ReviewTaskResponse.riskLevel == ReviewTaskResponse.issueSummary.riskLevel
```

---

## 6. Frontend Requirements

Add minimal optional diff input to the create task form.

Label:

```text
Optional PR diff
```

Helper copy:

```text
Paste a unified diff to let the AI review actual code changes. Leave empty to use repo URL and PR number only.
```

Behavior:

```text
Submitting without diff still works.
Submitting with non-blank diff sends diffText.
Whitespace-only diff should not be sent or should be normalized as absent.
If client-side validation is added, block diffText > 20000 characters.
```

Allowed frontend changes:

```text
Add diffText state.
Update request type.
Add textarea.
Update tests.
```

Forbidden frontend changes:

```text
New UI library
Route overhaul
Dashboard redesign
Monaco editor
Syntax highlighting
Diff viewer
Chart library
```

---

## 7. Required Tests

### 7.1 Backend Tests

Add or update tests for:

```text
Existing request without diff still works.
Request with diffText works.
Blank diffText treated as absent.
Whitespace-only diffText treated as absent.
Too-large diffText returns validation error.
repoUrl/prNumber validation remains unchanged.
ReviewContext includes diffText when provided.
ReviewContext omits diffText when absent.
Prompt builder includes diff only when present.
Prompt builder no-diff wording remains.
Prompt builder uses strict JSON schema.
Prompt builder tells model not to invent files outside diff.
Mock provider still returns 3 deterministic issues with or without diff.
MiMo provider receives diff prompt when diff is present.
MiMo provider receives no-diff prompt when diff is absent.
Missing-key fallback still works with diff present.
Parser behavior unchanged.
API response wrapper and DTO shape remain stable.
Risk invariant remains true.
Persistence behavior is tested if diffText is persisted.
```

### 7.2 Frontend Tests

Run and update tests for:

```text
Create form renders optional diff textarea.
Submit without diff works.
Submit with diff sends diffText.
Too-large diff blocks submit if client validation exists.
List/detail render unchanged.
Issue cards still render MOCK and MIMO sources.
```

---

## 8. Validation Commands

Run backend validation:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Run frontend validation:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

---

## 9. Runtime Smoke Tests

### 9.1 Mock Mode Without Diff

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

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

### 9.2 Mock Mode With Diff

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

### 9.3 MiMo Mode Without Key With Diff

```bash
unset MIMO_API_KEY

cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run \
  -Dspring-boot.run.arguments="--codereviewx.review.provider=mimo"
```

Create with diff.

Confirm:

```text
Task creation succeeds.
Fallback to MOCK.
Response shape unchanged.
No diffText returned.
No stack trace.
No secret leakage.
riskLevel == issueSummary.riskLevel
```

### 9.4 Browser Smoke

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Confirm:

```text
Backend status UP.
Create task without diff works.
Create task with diff works.
List updates.
Detail renders.
Summary panel renders.
Issue cards render.
No browser console errors.
```

---

## 10. README Updates

Update root README and backend README.

Document:

```text
Round 10 adds optional pasted PR diff context.
repoUrl + prNumber flow still works.
diffText is optional.
MiMo prompt uses diffText as primary review context when provided.
Mock mode remains default and stable.
MiMo fallback behavior is unchanged.
Public response does not expose raw prompt/model output/diff.
Manual pasted diff is supported.
Automatic GitHub ingestion is not implemented.
Maximum diffText length is 20000 characters.
```

Do not claim:

```text
CodeReviewX automatically fetches and reviews GitHub PRs.
```

---

## 11. Agent Structure and Flow

Your handoff must include this section exactly:

```markdown
## Agent Structure and Flow
```

Include the updated chain:

```text
Input -> Context -> Pipeline -> Provider Selection -> Provider -> Prompt -> Model Client -> Parser -> Finding -> Persistence -> API DTO -> Frontend
```

Use this Round 10 structure:

```text
Input:
  repoUrl + prNumber + optional diffText

Context:
  ReviewContext with optional diffText or PullRequestContext

Orchestrator:
  ReviewPipelineService

Provider Selection:
  ConfigurableReviewProvider

Provider:
  MockReviewProvider or XiaomiMiMoReviewProvider

Prompt:
  ReviewPromptBuilder includes diff when available

Model:
  Xiaomi MiMo API through XiaomiMiMoClient

Parser:
  XiaomiMiMoFindingParser

Finding:
  ReviewFinding[]

Persistence:
  ReviewTaskEntity + ReviewIssueEntity

Presentation:
  ReviewTaskResponse + frontend issue cards
```

---

## 12. Cursor Handoff Output

After implementation, create:

```text
tasks/round-10/01-cursor-pr-diff-context-v1-handoff.md
```

The handoff must include:

```text
Summary of implemented changes.
Backend files changed.
Frontend files changed.
Persistence decision.
Validation rules.
Prompt behavior with diff.
Prompt behavior without diff.
Mock behavior.
MiMo fallback behavior.
API compatibility status.
Test results with exact commands.
Runtime smoke results.
README updates.
Agent Structure and Flow.
Known limitations.
Recommendation for Codex validation.
```

Final verdict must be one of:

```text
CURSOR_ROUND_10_READY_FOR_CODEX
CURSOR_ROUND_10_BLOCKED
```

Use `CURSOR_ROUND_10_READY_FOR_CODEX` only if backend tests, frontend checks, and smoke verification are reasonably completed or clearly documented.