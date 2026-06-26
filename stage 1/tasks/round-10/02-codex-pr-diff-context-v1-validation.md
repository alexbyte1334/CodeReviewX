# tasks/round-10/02-codex-pr-diff-context-v1-validation.md

# Round 10 / Codex Task: PR Diff Context v1 Validation

## 1. Role

You are Codex, the independent validation agent for CodeReviewX Round 10.

Cursor has implemented **PR / Diff Context v1**. Your job is to verify the implementation rigorously, not to assume the handoff is correct.

You must inspect the codebase, run tests, perform runtime checks where possible, and determine whether Round 10 is ready for Qoder independent review.

---

## 2. Validation Target

Round 10 should add optional pasted PR diff context to the review agent.

Expected user input:

```json
{
  "repoUrl": "https://github.com/example/demo",
  "prNumber": 10,
  "diffText": "diff --git a/src/App.tsx b/src/App.tsx\n..."
}
```

Expected behavior:

```text
User submits repoUrl + prNumber + optional diffText
  -> ReviewTaskService creates task
  -> ReviewContext carries optional diffText
  -> ReviewPipelineService runs
  -> ConfigurableReviewProvider selects Mock or Xiaomi MiMo
  -> ReviewPromptBuilder includes diffText only when present
  -> XiaomiMiMoReviewProvider uses diff-aware prompt when available
  -> Parser returns ReviewFinding[]
  -> Findings persist as ReviewIssueEntity
  -> API response shape remains stable
  -> Frontend renders existing result UI
```

---

## 3. Cursor Handoff Claims to Verify

Cursor claims the following were implemented:

```text
diffText is optional on POST /api/review-tasks.
Blank or whitespace-only diffText is normalized to absent.
Maximum diffText length is 20000 characters.
ReviewContext carries optional diffText.
ReviewPromptBuilder has diff-aware and no-diff prompt variants.
Mock provider remains unchanged and returns 3 deterministic issues.
MiMo fallback behavior remains unchanged.
ReviewTaskEntity persists optional diffText using @Lob diff_text.
Public API response does not expose raw diffText.
Frontend create form has optional diff textarea and client-side max length validation.
README and backend README are updated.
```

Do not accept these claims without direct verification.

---

## 4. Files and Areas to Inspect

Inspect at minimum:

```text
backend-java/src/main/java/com/codereviewx/backend/review/dto/CreateReviewTaskRequest.java
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/ReviewContext.java
backend-java/src/main/java/com/codereviewx/backend/review/persistence/entity/ReviewTaskEntity.java
backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/ReviewPromptBuilder.java
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoReviewProvider.java
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mock/MockReviewProvider.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewTaskResponse.java
frontend/src/types/reviewTask.ts
frontend/src/components/ReviewTaskCreateForm.tsx
frontend/src/styles/app.css
README.md
backend-java/README.md
```

Also inspect relevant tests:

```text
backend-java/src/test/java/com/codereviewx/backend/review/controller/ReviewTaskControllerTest.java
backend-java/src/test/java/com/codereviewx/backend/review/service/ReviewTaskServiceTest.java
backend-java/src/test/java/com/codereviewx/backend/review/pipeline/provider/mimo/ReviewPromptBuilderTest.java
backend-java/src/test/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoReviewProviderTest.java
backend-java/src/test/java/com/codereviewx/backend/review/pipeline/ReviewPipelineFallbackIntegrationTest.java
frontend/src/test/ReviewTaskCreateForm.test.tsx
```

---

## 5. Backend Validation Checklist

Verify:

```text
CreateReviewTaskRequest accepts optional diffText.
Existing repoUrl + prNumber request still works.
diffText is not required.
Blank diffText is treated as absent.
Whitespace-only diffText is treated as absent.
diffText longer than 20000 characters returns validation error.
Validation error uses existing API error style.
repoUrl validation remains unchanged.
prNumber validation remains unchanged.
```

Verify persistence:

```text
ReviewTaskEntity has optional diffText persistence if Cursor implemented Option A.
diffText persistence does not force public response exposure.
Existing tasks without diff remain readable.
No unnecessary new entities were introduced.
```

Reject scope creep if you find:

```text
ChangedFileEntity
FileDiffEntity
ExecutionTraceEntity
ProviderInputEntity
RawPromptEntity
RawModelOutputEntity
GitHub OAuth
GitHub automatic fetching
Repository clone logic
```

---

## 6. ReviewContext and Pipeline Validation

Verify:

```text
ReviewContext carries diffText when provided.
ReviewContext has a safe absent state when diffText is omitted.
No null pointer risk exists in pipeline/provider flow.
ReviewTaskService normalizes diffText consistently before persistence and context creation.
ReviewPipelineService behavior remains compatible with existing providers.
```

Check whether trimming behavior is acceptable and documented.

---

## 7. Prompt Builder Validation

Verify the no-diff prompt:

```text
Preserves Round 09 limited-context behavior.
Clearly states actual PR diff is not available.
Does not pretend to review real changed code.
Still requires strict JSON output.
```

Verify the diff prompt:

```text
Includes the actual diffText.
States that the provided PR diff is the primary review context.
Instructs model to review changed lines and nearby context.
Instructs model to identify security, reliability, maintainability, performance, test, style, and bug risks.
Instructs model to use only files/code present in the diff.
Instructs model not to invent files.
Instructs model to prefer changed-hunk line numbers where possible.
Requires strict JSON only.
Forbids markdown fences.
Uses backend enum values only.
Allows [] when no meaningful findings exist.
```

Verify schema alignment:

```text
severity: HIGH | MEDIUM | LOW
category: BUG | SECURITY | PERFORMANCE | MAINTAINABILITY | STYLE | TEST
source behavior remains valid for MOCK/MIMO
```

Flag any enum drift or parser mismatch.

---

## 8. Provider and Fallback Validation

Verify Mock provider:

```text
Still returns exactly 3 deterministic mock issues.
Works with diffText present.
Works without diffText.
Does not depend on diff content.
```

Verify MiMo provider:

```text
Uses diff-aware prompt when diffText is present.
Uses no-diff prompt when diffText is absent.
Does not log or expose MIMO_API_KEY.
Does not expose raw prompt in public API.
Does not expose raw model output in public API.
Does not expose raw diffText in public API.
```

Verify fallback behavior:

```text
provider=mimo + missing key -> mock fallback
provider=mimo + client failure -> mock fallback
provider=mimo + parser failure -> mock fallback
valid [] from MiMo -> successful zero findings
fallback still works when diffText is present
```

---

## 9. Public API Contract Validation

Preserved endpoints must still work:

```text
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

Verify response compatibility:

```text
ReviewTaskResponse shape unchanged.
ReviewIssueResponse shape unchanged.
IssueSummaryResponse shape unchanged.
No diffText field in public response.
No prompt field in public response.
No rawModelOutput field in public response.
ReviewTaskResponse.riskLevel == ReviewTaskResponse.issueSummary.riskLevel.
```

---

## 10. Frontend Validation

Verify UI behavior:

```text
Create task form renders optional PR diff textarea.
Helper copy accurately explains pasted unified diff.
Submitting without diff works.
Submitting with non-blank diff sends diffText.
Whitespace-only diff is not sent or is safely normalized as absent.
diffText over 20000 characters is blocked client-side if implemented.
Existing list/detail/result rendering is unchanged.
Issue cards still render MOCK and MIMO sources.
```

Reject scope creep if you find:

```text
New UI library
Route overhaul
Dashboard redesign
Monaco editor
Syntax highlighting package
Visual diff viewer
Chart library
```

---

## 11. Documentation Validation

Verify root README and backend README document:

```text
Round 10 adds optional pasted PR diff context.
repoUrl + prNumber flow still works.
diffText is optional.
MiMo prompt uses diffText when provided.
Mock mode remains default and stable.
MiMo fallback behavior is unchanged.
Public response does not expose raw prompt/model output/diff.
Manual pasted diff is supported.
Automatic GitHub ingestion is not implemented.
Maximum diffText length is 20000 characters.
```

Flag overclaiming if README says or implies:

```text
CodeReviewX automatically fetches GitHub PR diffs.
CodeReviewX fully integrates with GitHub.
CodeReviewX reviews private PRs automatically.
```

unless such functionality truly exists, which is not expected in Round 10.

---

## 12. Required Commands

Run backend tests:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Run frontend checks:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

Record exact results.

If local Java path differs, use the correct local JDK 17 path and document it.

---

## 13. Runtime Verification

### 13.1 Mock Mode Without Diff

Start backend in default mock mode:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

Create task:

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
no diffText in response
```

### 13.2 Mock Mode With Diff

Create task:

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
no diffText in response
```

### 13.3 Oversized Diff

Submit a request with `diffText.length > 20000`.

Confirm:

```text
success=false
validation error returned
message mentions 20000 character limit
task is not created
```

### 13.4 MiMo Mode Without Key With Diff

Start backend:

```bash
unset MIMO_API_KEY

cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run \
  -Dspring-boot.run.arguments="--codereviewx.review.provider=mimo"
```

Create task with diff.

Confirm:

```text
task creation succeeds
fallback to MOCK
response shape unchanged
no diffText returned
no stack trace in response
no secret leakage
riskLevel == issueSummary.riskLevel
```

### 13.5 Browser Smoke

Run frontend:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Manually verify:

```text
Backend status UP.
Create task without diff works.
Create task with diff works.
List updates.
Detail renders.
Summary panel renders.
Issue cards render.
No browser console errors.
Oversized diff client-side behavior is acceptable if implemented.
```

If browser smoke cannot be executed, state why and rely on tests/build as partial evidence.

---

## 14. Agent Structure and Flow

Your handoff must include:

```markdown
## Agent Structure and Flow
```

Use this chain:

```text
Input -> Context -> Pipeline -> Provider Selection -> Provider -> Prompt -> Model Client -> Parser -> Finding -> Persistence -> API DTO -> Frontend
```

Round 10 expected structure:

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

Also state whether the implementation matches this structure.

---

## 15. Output Handoff

Create:

```text
tasks/round-10/02-codex-pr-diff-context-v1-validation-handoff.md
```

Your handoff must include:

```text
Executive summary.
Cursor claim verification table.
Backend validation findings.
Frontend validation findings.
Prompt builder validation.
Provider/fallback validation.
API contract validation.
Persistence validation.
Documentation validation.
Test results with exact commands.
Runtime smoke results.
Browser smoke result or reason not executed.
Scope creep assessment.
Security/leakage assessment.
Agent Structure and Flow.
Open issues, if any.
Required fixes, if any.
Recommendation for Qoder.
```

Final verdict must be one of:

```text
CODEX_ROUND_10_READY_FOR_QODER
CODEX_ROUND_10_NEEDS_CURSOR_FIXES
CODEX_ROUND_10_BLOCKED
```

Use:

```text
CODEX_ROUND_10_READY_FOR_QODER
```

only if the implementation is materially correct, tests pass, API compatibility is preserved, and no critical security/API/prompt defects are found.

Use:

```text
CODEX_ROUND_10_NEEDS_CURSOR_FIXES
```

if there are fixable defects such as broken validation, missing tests, response leakage, prompt mismatch, or frontend submission issues.

Use:

```text
CODEX_ROUND_10_BLOCKED
```

only if validation cannot proceed due to environment or repository state issues.

---