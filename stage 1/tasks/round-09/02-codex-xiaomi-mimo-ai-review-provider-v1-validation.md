# tasks/round-09/02-codex-xiaomi-mimo-ai-review-provider-v1-validation.md

# Codex Task: Xiaomi MiMo AI Review Provider v1 Validation

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 09
- Task: 02
- Owner: Codex
- Task Type: Independent validation and corrective patching
- Upstream Input:
  - `tasks/round-09/00-round-09-start.md`
  - `tasks/round-09/01-cursor-xiaomi-mimo-ai-review-provider-v1.md`
  - `tasks/round-09/01-cursor-xiaomi-mimo-ai-review-provider-v1-handoff.md`
- Expected Output:
  - `tasks/round-09/02-codex-xiaomi-mimo-ai-review-provider-v1-validation-handoff.md`

## 2. Objective

Independently validate Cursor's Xiaomi MiMo AI Review Provider v1 implementation.

You must verify that CodeReviewX now has a configurable Xiaomi MiMo provider path behind the existing `ReviewProvider` boundary while preserving:

1. default mock-mode stability;
2. public API contract;
3. persistence model;
4. `riskLevel == issueSummary.riskLevel` invariant;
5. frontend compatibility;
6. safe fallback behavior;
7. secret handling;
8. accurate agent capability documentation.

If you find defects, apply the smallest safe patch and document it.

---

## 3. Cursor Implementation Summary To Validate

Cursor claims the following were implemented:

```text
ConfigurableReviewProvider
XiaomiMiMoReviewProvider
XiaomiMiMoClient
ReviewPromptBuilder
XiaomiMiMoFindingParser
XiaomiMiMoProperties
IssueSource.MIMO
frontend IssueSource update
README updates
backend and frontend tests
```

Claimed runtime behavior:

```text
provider=mock
  -> MockReviewProvider

provider=mimo + valid MIMO_API_KEY
  -> XiaomiMiMoReviewProvider

provider=mimo + missing key / client failure / parser failure
  -> fallback to MockReviewProvider
```

Claimed public contract:

```text
POST /api/review-tasks unchanged
GET /api/review-tasks unchanged
GET /api/review-tasks/{id} unchanged
ReviewTaskResponse unchanged
ReviewIssueResponse unchanged
IssueSummaryResponse unchanged
```

Claimed limitations:

```text
No PR diff context yet.
MiMo prompt currently receives repoUrl + prNumber only.
Live MiMo API verification was not completed by Cursor.
Restart persistence was not re-verified by Cursor.
Browser smoke was not run by Cursor.
Task summary text may still say "Mock review completed" regardless of provider.
```

You must verify these claims directly from code and runtime behavior.

---

## 4. Non-Negotiable Secret Handling Rules

The Xiaomi MiMo API key must never be committed, printed, logged, written to README, written to tests, returned through API, or included in the handoff.

The only allowed key source is:

```bash
export MIMO_API_KEY="<local-secret>"
```

Required checks:

```text
1. Search git diff and repository files for accidental key material.
2. Confirm application config only uses placeholders such as ${MIMO_API_KEY:}.
3. Confirm README does not contain the real key.
4. Confirm tests do not contain the real key.
5. Confirm logs do not print Authorization header or key.
6. Confirm API responses do not expose key, headers, prompt, raw model output, or fallback reason.
7. Confirm handoff does not include the real key.
```

Use placeholder examples only:

```bash
export MIMO_API_KEY="<local-secret-not-committed>"
```

Do not include the actual value in the handoff.

---

## 5. Required Agent Structure and Flow Review

Verify that the implementation and documentation reflect this agent chain:

```text
Input -> Context -> Pipeline -> Provider Selection -> Provider -> Prompt -> Model Client -> Parser -> Finding -> Persistence -> API DTO -> Frontend
```

Expected mapping:

```text
Input:
  repoUrl + prNumber

Context:
  ReviewContext

Pipeline:
  ReviewPipelineService

Provider Selection:
  ConfigurableReviewProvider or equivalent

Provider:
  MockReviewProvider or XiaomiMiMoReviewProvider

Prompt:
  ReviewPromptBuilder

Model Client:
  XiaomiMiMoClient

Parser:
  XiaomiMiMoFindingParser

Finding:
  ReviewFinding

Persistence:
  ReviewTaskEntity + ReviewIssueEntity

API DTO:
  ReviewTaskResponse + ReviewIssueResponse + IssueSummaryResponse

Frontend:
  existing task list/detail and issue cards
```

Your handoff must contain a section named:

```markdown
## Agent Structure and Flow
```

---

## 6. Backend Architecture Validation

Inspect the implementation and verify the following.

### 6.1 Provider Boundary

Confirm:

```text
XiaomiMiMoReviewProvider implements ReviewProvider.
MockReviewProvider remains available.
ReviewTaskService does not contain MiMo-specific logic.
Controller layer does not contain MiMo-specific logic.
Provider selection is isolated in ConfigurableReviewProvider, ReviewPipelineConfig, or equivalent.
ReviewPipelineService remains the orchestration seam.
```

Fail if:

```text
MiMo logic leaks into controller.
MiMo logic leaks into DTO mapper.
MiMo logic leaks into ReviewTaskService except through existing pipeline call.
Provider selection is hardcoded.
Mock mode is no longer the safe default.
```

### 6.2 Configuration

Verify these defaults:

```properties
codereviewx.review.provider=mock
codereviewx.ai.mimo.base-url=https://api.xiaomimimo.com/v1
codereviewx.ai.mimo.model=mimo-v2.5-pro
```

Verify environment overrides:

```text
CODEREVIEWX_REVIEW_PROVIDER
MIMO_API_KEY
MIMO_BASE_URL
MIMO_MODEL
```

Required behavior:

```text
Default startup requires no MIMO_API_KEY.
Default tests require no MIMO_API_KEY.
provider=mock never calls MiMo.
provider=mimo without key falls back to mock.
```

### 6.3 Xiaomi MiMo Client

Verify:

```text
Client calls {baseUrl}/chat/completions.
Request shape is OpenAI-compatible.
Authorization uses Bearer token or documented supported method.
API key is not logged.
Headers are not logged.
Raw response is not exposed in public API.
HTTP failure becomes a safe provider failure.
Timeout/non-2xx/unexpected response causes fallback.
```

If timeout configuration is missing but current RestClient defaults are acceptable for this prototype, document it as a limitation rather than expanding scope.

### 6.4 Prompt Builder

Verify prompt includes:

```text
agent role
review objective
repoUrl
prNumber
explicit no-diff-context limitation
strict JSON output requirement
no markdown fences instruction
allowed enum values matching backend enums
required schema
empty array allowed
```

Important:

The prompt enum values must match actual backend enums. Do not rely on the Round 09 document’s example blindly.

### 6.5 Parser

Verify:

```text
strict JSON array is accepted
empty [] is treated as successful zero findings
malformed JSON is rejected safely
invalid severity is rejected safely
invalid category is rejected safely
missing issueKey generates deterministic MIMO-ISSUE-N
blank title/description/recommendation get safe defaults
blank filePath becomes safe default
invalid lines become safe default
source=MIMO
status=OPEN
invalid partial records are not persisted
```

If parser currently accepts markdown fences, decide whether that violates the strict-output requirement. Prefer strict parser unless the implementation intentionally sanitizes simple model wrapper artifacts. Document the decision.

---

## 7. Fallback Semantics Validation

Verify all cases.

### 7.1 Default Mock Mode

Run with no provider override and no `MIMO_API_KEY`.

Expected:

```text
task creation succeeds
issues.length=3
source=MOCK
status=OPEN
riskLevel=HIGH
issueSummary.totalIssues=3
riskLevel == issueSummary.riskLevel
no MiMo call attempted
```

### 7.2 Explicit Mock Mode

Run with:

```bash
--codereviewx.review.provider=mock
```

Expected:

```text
same as default mock mode
no MiMo call attempted
no MIMO_API_KEY required
```

### 7.3 MiMo Mode Without Key

Run with:

```bash
unset MIMO_API_KEY
--codereviewx.review.provider=mimo
```

Expected:

```text
application starts
task creation succeeds
fallback to mock
issues.length=3
source=MOCK
API response shape unchanged
no stack trace in API
no fallback reason in API
safe warning in logs only
```

### 7.4 MiMo Client Failure

Simulate by using invalid `MIMO_BASE_URL` or a stubbed failing client.

Expected:

```text
fallback to mock
task creation succeeds
persisted issues valid
summary/risk correct
no raw provider error in API
```

### 7.5 Parser Failure

Simulate invalid model output in tests.

Expected:

```text
fallback to mock
no malformed MiMo records persisted
task creation succeeds
summary/risk correct
```

### 7.6 Valid Empty MiMo Result

Using stubbed client/parser test or controlled response.

Expected:

```text
successful AI path
zero issues
issueSummary.totalIssues=0
riskLevel=NONE
no fallback to mock
```

If current service cannot handle zero issues correctly, patch it.

---

## 8. API Contract Validation

Endpoints must remain:

```text
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

Create request must remain:

```json
{
  "repoUrl": "https://github.com/example/demo",
  "prNumber": 9
}
```

Response wrapper must remain:

```json
{
  "success": true,
  "message": "OK",
  "data": {}
}
```

`ReviewTaskResponse` must still expose:

```text
id
repoUrl
prNumber
status
riskLevel
summary
errorMessage
issues
issueSummary
createdAt
updatedAt
```

`ReviewIssueResponse` must still expose:

```text
id
severity
category
source
status
filePath
startLine
endLine
title
description
recommendation
```

`IssueSummaryResponse` must still expose:

```text
totalIssues
highCount
mediumCount
lowCount
riskLevel
```

Must not expose:

```text
providerName
successful
provider message
raw prompt
raw model output
API key
headers
stack trace
internal DB issue id
fallback reason
```

Validate by inspecting DTOs and real API responses.

---

## 9. Persistence Validation

Verify:

```text
No new tables.
No new columns unless absolutely necessary and documented.
ReviewTaskEntity unchanged unless justified.
ReviewIssueEntity unchanged unless justified.
IssueSource.MIMO persists safely.
Mock tasks remain readable.
MiMo or fallback tasks remain readable after restart.
issueSummary is computed from persisted issues.
riskLevel is derived from issueSummary.
riskLevel == issueSummary.riskLevel.
```

Required restart test:

```text
1. Start backend in mock mode.
2. Create one mock task.
3. Start backend in mimo mode without key or with real key.
4. Create one MiMo/fallback task.
5. Stop backend.
6. Restart backend.
7. GET both task details.
8. Confirm tasks and issues persist.
9. Confirm source values persist.
10. Confirm summary/risk remain correct.
```

---

## 10. Product Semantics Check: Summary Text

Cursor’s handoff notes:

```text
Task summary text still says "Mock review completed" regardless of provider.
```

You must inspect this.

Decision rule:

```text
If summary is visible to users and says "Mock review completed" for a successful MIMO task, patch it.
```

Preferred behavior:

```text
source=MOCK:
  summary can mention mock review.

source=MIMO:
  summary should mention AI review or Xiaomi MiMo review without overclaiming full PR diff review.

zero MIMO findings:
  summary should say no findings were produced from the available context.

fallback to MOCK:
  public API should not expose fallback reason.
  summary may use generic wording such as "Review completed with demo findings."
```

Do not expose provider internals or fallback reason if the API contract intentionally hides them.

Acceptable generic wording:

```text
Review completed.
```

or:

```text
Review completed with generated findings.
```

Avoid:

```text
Mock review completed.
```

for all provider modes if it causes user confusion.

Add or update tests if patched.

---

## 11. Frontend Validation

Verify minimal frontend behavior only.

Required checks:

```text
IssueSource type includes MIMO.
MOCK issue badges still render.
MIMO issue badges render.
Summary panel source label does not crash for:
  MOCK
  MIMO
  MIXED
  no issues
No redesign was introduced.
No new UI library was introduced.
No route overhaul.
No state management migration.
```

Run:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

If browser smoke is feasible, run:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Verify:

```text
backend status UP
create task works
list updates
detail renders
summary panel renders
issue cards render
MOCK source badge renders
MIMO source badge renders through fixture or live MiMo result
no browser console errors
```

---

## 12. Required Test Commands

Run:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Run:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

If any fail:

```text
1. identify root cause;
2. patch only within Round 09 scope;
3. rerun affected command;
4. document before/after result.
```

Do not hide failures.

---

## 13. Runtime Verification Commands

### 13.1 Mock Mode

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

Create:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/round-09-codex-mock",
    "prNumber": 9
  }'
```

Verify:

```text
success=true
issues.length=3
source=MOCK
status=OPEN
issueSummary.totalIssues=3
riskLevel=HIGH
riskLevel == issueSummary.riskLevel
```

### 13.2 MiMo Mode Without Key

```bash
unset MIMO_API_KEY

cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run \
  -Dspring-boot.run.arguments="--codereviewx.review.provider=mimo"
```

Create:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/round-09-codex-mimo-fallback",
    "prNumber": 9
  }'
```

Verify:

```text
task creation succeeds
fallback to MOCK
response shape unchanged
no stack trace
no secret details
riskLevel == issueSummary.riskLevel
```

### 13.3 MiMo Mode With Real Key

Only if local key is available through environment:

```bash
export MIMO_API_KEY="<local-secret-not-committed>"

cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run \
  -Dspring-boot.run.arguments="--codereviewx.review.provider=mimo"
```

Create:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/round-09-codex-mimo-live",
    "prNumber": 9
  }'
```

Verify one of:

```text
MiMo success:
  success=true
  issues source=MIMO or zero findings
  riskLevel == issueSummary.riskLevel

Safe fallback:
  success=true
  issues source=MOCK
  riskLevel == issueSummary.riskLevel
  no stack trace or secret details
```

If live MiMo fails due to provider endpoint/model/auth mismatch, document the exact sanitized failure class without leaking secrets, and verify fallback.

---

## 14. Documentation Validation

Check root README and backend README.

Must document:

```text
Round 09 introduces Xiaomi MiMo provider.
Default mode remains mock.
MiMo mode is config-driven.
MIMO_API_KEY is read from environment only.
API key must never be committed.
Fallback behavior is documented.
Public API remains unchanged.
Findings persist as ReviewIssue.
Summary/risk computed from persisted issues.
Current limitation: no PR diff context yet.
Next direction: PR/diff context v1.
```

Must not claim:

```text
CodeReviewX fully reviews real GitHub pull requests.
Production-ready AI review platform.
Complete GitHub PR review automation.
```

Acceptable positioning:

```text
CodeReviewX now has a configurable Xiaomi MiMo AI provider path.
Real PR/diff context enrichment is planned for a following round.
```

---

## 15. Scope Guardrails

Do not implement:

```text
real GitHub PR diff ingestion
repository clone
GitHub auth
Semgrep
multi-agent planner
async job queue
streaming
trace UI
provider registry UI
auth
team/org model
dashboard redesign
frontend redesign
new UI library
database migration framework
deployment or CI/CD
```

If you find a defect, patch only what is necessary to make Round 09 safe and valid.

---

## 16. Required Handoff

Create:

```text
tasks/round-09/02-codex-xiaomi-mimo-ai-review-provider-v1-validation-handoff.md
```

Use this exact structure:

```markdown
# Codex Handoff: Xiaomi MiMo AI Review Provider v1 Validation

## 1. Verdict

## 2. Summary of Validation

## 3. Files Inspected

## 4. Files Changed by Codex

## 5. Agent Structure and Flow

## 6. Provider Selection Verification

## 7. Xiaomi MiMo Client Verification

## 8. Prompt and Parser Verification

## 9. Fallback and Failure Verification

## 10. API Contract Verification

## 11. Persistence and Restart Verification

## 12. Frontend Verification

## 13. Summary Text / Product Semantics Check

## 14. Secret Handling Verification

## 15. Tests and Runtime Commands

## 16. Documentation Verification

## 17. Remaining Risks

## 18. Recommendation for Qoder
```

## 17. Verdict Options

Use one of:

```text
ROUND_09_CODEX_PASS_READY_FOR_QODER
```

Only use this if:

```text
backend tests pass
frontend checks pass
mock mode verified
missing-key fallback verified
API contract preserved
secret handling safe
restart persistence verified or explicitly blocked with credible reason
browser smoke run or explicitly blocked with credible reason
summary text issue resolved or accepted with clear rationale
```

Use:

```text
ROUND_09_CODEX_PATCHED_READY_FOR_QODER
```

If you had to patch defects but all required validation passes after patching.

Use:

```text
ROUND_09_CODEX_BLOCKED_NEEDS_CURSOR_FIX
```

Only if there is a serious unresolved defect such as:

```text
app cannot start
tests fail
API contract broken
secrets leaked
fallback broken
mock mode broken
persistence broken
frontend cannot build
```

## 18. Final Instruction

Validate aggressively.

The goal is not to trust Cursor’s handoff, but to prove whether Round 09 is truly safe to close.

Round 09 can only move to Qoder if CodeReviewX is accurately described as:

```text
A configurable Xiaomi MiMo-powered review agent prototype with safe mock fallback.
```

It must not be described as:

```text
A complete production GitHub pull request review platform.
```