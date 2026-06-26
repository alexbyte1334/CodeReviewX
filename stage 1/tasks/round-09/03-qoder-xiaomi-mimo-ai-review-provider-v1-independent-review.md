# tasks/round-09/03-qoder-xiaomi-mimo-ai-review-provider-v1-independent-review.md

# Qoder Task: Xiaomi MiMo AI Review Provider v1 Independent Review

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 09
- Task: 03
- Owner: Qoder
- Task Type: Independent architecture, safety, and release-readiness review
- Upstream Inputs:
  - `tasks/round-09/00-round-09-start.md`
  - `tasks/round-09/01-cursor-xiaomi-mimo-ai-review-provider-v1.md`
  - `tasks/round-09/01-cursor-xiaomi-mimo-ai-review-provider-v1-handoff.md`
  - `tasks/round-09/02-codex-xiaomi-mimo-ai-review-provider-v1-validation.md`
  - `tasks/round-09/02-codex-xiaomi-mimo-ai-review-provider-v1-validation-handoff.md`
- Expected Output:
  - `tasks/round-09/03-qoder-xiaomi-mimo-ai-review-provider-v1-independent-review-handoff.md`

## 2. Objective

Independently judge whether Round 09 can close.

Round 09’s goal is not to build a complete production PR review system. The goal is to confirm that CodeReviewX now has a safe, configurable Xiaomi MiMo AI review provider path behind the existing `ReviewProvider` boundary, while preserving mock-mode stability, public API contract, persistence, frontend compatibility, and fallback behavior.

You must decide whether the project can advance to Round 10.

Expected final verdict options:

```text
ROUND_09_CLOSED_READY_FOR_ROUND_10
```

or:

```text
ROUND_09_BLOCKED_NEEDS_FIX
```

Use the blocked verdict only if you find a serious unresolved issue that prevents safe progression.

---

## 3. Upstream Status To Review

Cursor implemented:

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

Codex then validated and patched:

```text
ReviewTaskService summary wording
ReviewTaskService tests
ReviewTaskController tests
backend-java README example
```

Codex verdict:

```text
ROUND_09_CODEX_PATCHED_READY_FOR_QODER
```

Codex reported:

```text
backend mvn test: PASS, 68 tests
frontend typecheck: PASS
frontend build: PASS
frontend tests: PASS, 27 tests
mock runtime: PASS
mimo missing-key fallback runtime: PASS
restart persistence: PASS
browser smoke: PASS
```

Codex did not run live MiMo success because no local `MIMO_API_KEY` was present.

---

## 4. Independent Review Scope

You must review the implementation and handoffs from an architecture and release-readiness perspective.

Focus areas:

1. agent architecture correctness;
2. provider boundary correctness;
3. provider selection behavior;
4. MiMo client safety;
5. prompt and parser safety;
6. fallback semantics;
7. public API contract;
8. persistence safety;
9. frontend impact;
10. product positioning;
11. secret handling;
12. whether Round 09 can close despite no live MiMo success call.

Do not implement large new features.

If you find a small critical defect, you may recommend a targeted patch, but the preferred Qoder role is independent review and verdict.

---

## 5. Agent Structure and Flow Review

Verify and document this chain:

```text
Input -> Context -> Pipeline -> Provider Selection -> Provider -> Prompt -> Model Client -> Parser -> Finding -> Persistence -> API DTO -> Frontend
```

Expected implementation mapping:

```text
Input:
  repoUrl + prNumber

Context:
  ReviewContext

Pipeline:
  ReviewPipelineService

Provider Selection:
  ConfigurableReviewProvider

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

Your handoff must include a section named:

```markdown
## Agent Structure and Flow
```

You must explicitly state whether CodeReviewX is now correctly describable as:

```text
A configurable Xiaomi MiMo-powered review agent prototype with safe mock fallback.
```

You must also explicitly state that it is not yet:

```text
A complete production GitHub pull request review platform.
```

unless real PR/diff ingestion has been implemented, which is not expected in Round 09.

---

## 6. Provider Architecture Review

Inspect or reason from the implementation and Codex handoff.

Confirm:

```text
XiaomiMiMoReviewProvider implements ReviewProvider.
MockReviewProvider remains deterministic and available.
ConfigurableReviewProvider or equivalent owns provider selection.
ReviewTaskService does not contain provider-specific MiMo logic.
Controller layer does not contain provider-specific MiMo logic.
DTO layer does not contain provider-specific MiMo logic.
ReviewPipelineService remains the pipeline orchestration seam.
```

Reject Round 09 if:

```text
MiMo provider bypasses ReviewProvider abstraction.
Provider selection is hardcoded in controller.
Provider selection is hardcoded in ReviewTaskService.
Mock mode is no longer the default.
Tests require a real MiMo API key.
Application startup requires a real MiMo API key.
```

---

## 7. Configuration and Secret Handling Review

Expected config defaults:

```properties
codereviewx.review.provider=mock
codereviewx.ai.mimo.base-url=https://api.xiaomimimo.com/v1
codereviewx.ai.mimo.model=mimo-v2.5-pro
```

Expected environment variables:

```text
CODEREVIEWX_REVIEW_PROVIDER
MIMO_API_KEY
MIMO_BASE_URL
MIMO_MODEL
```

Secret rules:

```text
MIMO_API_KEY must be read only from environment/config binding.
No real key in application.yml.
No real key in README.
No real key in tests.
No real key in frontend.
No real key in handoffs.
No real key in logs.
No real key in API responses.
No raw Authorization header logging.
```

Codex reported that git-diff verification was blocked because the environment was not recognized as a git repository. You should decide whether repository file search is sufficient for Round 09, or whether this requires a follow-up note.

Do not include any real API key in your handoff.

---

## 8. Xiaomi MiMo Client Review

Expected client behavior:

```text
POST {baseUrl}/chat/completions
OpenAI-compatible request shape
Authorization: Bearer <MIMO_API_KEY>
model from config
temperature set conservatively
returns assistant content
non-2xx becomes safe client exception
network failure becomes safe client exception
missing key does not crash app startup
no key/header/raw response logging
```

Known Codex note:

```text
No explicit timeout configuration yet.
```

You must judge whether this is acceptable for Round 09.

Recommended decision:

```text
Acceptable for Round 09 prototype if documented as a Round 10/11 hardening item.
```

Only block Round 09 if the missing timeout can hang core request handling indefinitely in a way that makes local demo or fallback semantics unsafe.

---

## 9. Prompt and Parser Review

Verify prompt quality:

```text
agent role included
review objective included
repoUrl included
prNumber included
no PR diff context limitation included
strict JSON array requested
no markdown fences requested
backend enum values match actual backend enums
empty array allowed
does not overclaim real code-diff review
```

Verify parser behavior:

```text
strict JSON array accepted
[] accepted as successful zero findings
malformed JSON rejected safely
non-array output rejected safely
invalid severity rejected safely
invalid category rejected safely
missing issueKey generates MIMO-ISSUE-N
blank filePath defaults safely
invalid/missing line numbers default safely
blank title/description/recommendation default safely
source=MIMO
status=OPEN
invalid partial records are not persisted
```

You should specifically judge whether accepting only strict JSON is appropriate. If the parser tolerates minor model artifacts, decide whether that is safe or too permissive. Round 09 instructions prefer strict structured output.

---

## 10. Fallback Semantics Review

Confirm the following behavior is implemented and validated.

### 10.1 Default Mock Mode

```text
No MIMO_API_KEY required.
No MiMo call attempted.
Creates task successfully.
Persists 3 MOCK/OPEN issues.
riskLevel=HIGH.
issueSummary.totalIssues=3.
riskLevel == issueSummary.riskLevel.
```

### 10.2 Explicit Mock Mode

```text
codereviewx.review.provider=mock
Same as default mock mode.
No MiMo key required.
```

### 10.3 MiMo Mode Without Key

```text
codereviewx.review.provider=mimo
MIMO_API_KEY absent or blank
App starts.
Task creation succeeds.
Fallback to mock.
Public API response shape unchanged.
No stack trace in API.
No fallback reason in API.
Safe warning log only.
```

### 10.4 MiMo API Failure

```text
Network failure / non-2xx / invalid client response
Fallback to mock.
Task creation succeeds.
No raw provider error exposed in API.
```

### 10.5 Parser Failure

```text
Malformed or invalid model output
Fallback to mock.
No malformed partial MiMo findings persisted.
Task creation succeeds.
```

### 10.6 Valid Empty MiMo Result

```text
[] from MiMo is successful AI result.
No fallback.
Zero issues.
issueSummary.totalIssues=0.
riskLevel=NONE.
```

Block only if any of these core fallback semantics are broken.

---

## 11. API Contract Review

Confirm endpoints are unchanged:

```text
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

Confirm request shape is unchanged:

```json
{
  "repoUrl": "https://github.com/example/demo",
  "prNumber": 9
}
```

Confirm response wrapper is unchanged:

```json
{
  "success": true,
  "message": "OK",
  "data": {}
}
```

Confirm `ReviewTaskResponse` still includes:

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

Confirm `ReviewIssueResponse` still includes:

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

Confirm `IssueSummaryResponse` still includes:

```text
totalIssues
highCount
mediumCount
lowCount
riskLevel
```

Confirm public API does not expose:

```text
providerName
provider success flag
provider message
raw prompt
raw model output
API key
headers
stack trace
internal DB issue id
fallback reason
```

---

## 12. Persistence Review

Confirm:

```text
No provider result table.
No raw model output table.
No prompt table.
No token/cost table.
No execution trace table.
No new DB columns unless justified.
ReviewTaskEntity remains persisted.
ReviewIssueEntity remains persisted.
IssueSource.MIMO is safe as enum/string persistence.
summary/risk are computed consistently.
riskLevel == issueSummary.riskLevel.
Restart persistence works for both normal mock and MiMo fallback tasks.
```

Codex reported restart persistence passed for task `id=257` and `id=289`.

You should decide whether that evidence is sufficient. If you require an additional manual verification, say so clearly.

---

## 13. Frontend Review

Expected frontend impact is minimal.

Confirm:

```text
IssueSource includes MIMO.
MOCK issue badges render.
MIMO issue badges render.
Summary panel handles MOCK.
Summary panel handles MIMO.
Summary panel handles MIXED.
Summary panel handles no issues as N/A or equivalent.
No frontend redesign.
No new UI library.
No chart library.
No route overhaul.
No state management migration.
```

Known Codex note:

```text
Frontend header/subtitle still says ReviewTask Mock UI.
```

You must judge whether this should block Round 09.

Recommended decision:

```text
Do not block Round 09 if core functionality is correct.
Mark copy cleanup for final UI polish or Round 10/11.
```

Only block if the frontend copy materially misrepresents the provider result or breaks MiMo rendering.

---

## 14. Product Semantics Review

Codex patched the previous task summary issue.

New summary behavior:

```text
With findings:
  Review completed for PR #<n> with generated findings.

Zero findings:
  Review completed for PR #<n> with no findings from the available context.
```

Judge whether this is acceptable.

Expected reasoning:

```text
This is better than "Mock review completed" because it avoids false provider-specific claims.
It also avoids overclaiming full real PR review because it says generated findings or available context.
It does not expose fallback reason or provider internals.
```

Confirm whether old persisted local H2 rows with old summary strings are acceptable.

Recommended decision:

```text
Accept old local rows as non-blocking because no data migration is in Round 09 scope.
```

---

## 15. Documentation Review

Confirm README and backend README document:

```text
Round 09 introduces Xiaomi MiMo provider.
Default mode remains mock.
MiMo mode is config-driven.
MIMO_API_KEY is environment-only.
API key must never be committed.
Fallback behavior.
Public API unchanged.
Findings persist as ReviewIssue.
Summary/risk computed from persisted issues.
Current limitation: no PR diff context yet.
Next direction: PR/diff context.
```

Reject if documentation claims:

```text
CodeReviewX fully reviews real GitHub pull requests.
Production-ready AI review platform.
Complete GitHub PR review automation.
```

Preferred positioning:

```text
CodeReviewX now has a configurable Xiaomi MiMo AI provider path.
Real PR/diff context enrichment is planned for a following round.
```

---

## 16. Validation Evidence Review

Codex reported:

```text
Backend:
  Tests run: 68
  Failures: 0
  Errors: 0
  Skipped: 0
  BUILD SUCCESS

Frontend:
  typecheck: PASS
  build: PASS
  tests: PASS
  4 files / 27 tests

Runtime:
  mock mode: PASS
  missing-key MiMo fallback: PASS
  restart persistence: PASS
  browser smoke: PASS
```

You may rerun commands if your environment permits:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

If you do not rerun them, state that your review relies on Codex’s reported validation evidence plus code inspection.

---

## 17. Live MiMo Verification Decision

Codex did not run live MiMo success because `MIMO_API_KEY` was not present.

You must explicitly decide whether this blocks Round 09.

Recommended decision:

```text
Do not block Round 09 solely on missing live MiMo success verification if:
1. client path is implemented;
2. provider selection is tested;
3. stub/fake client tests validate successful MiMo findings;
4. missing-key fallback is runtime-verified;
5. docs state live provider requires MIMO_API_KEY.
```

However, recommend live MiMo validation as one of:

```text
1. a Round 09 post-close manual verification step; or
2. an early Round 10 prerequisite before PR/diff context enrichment; or
3. a dedicated provider-hardening subtask.
```

Block only if the client implementation appears structurally incompatible with the expected MiMo API shape or if fallback is unsafe.

---

## 18. Recommended Round 10 Direction

If Round 09 closes, recommend Round 10:

```text
PR / Diff Context v1
```

Preferred scope:

```text
Manual pasted diff input first, GitHub API second.
```

Reason:

```text
Xiaomi MiMo provider now exists, but it only receives repoUrl and prNumber.
To become substantively useful, the agent needs actual changed-file or diff context.
Manual diff input avoids being blocked by GitHub auth and repo clone complexity.
```

Possible Round 10 deliverables:

```text
ReviewTask create request supports optional diff/context input.
Introduce PullRequestContext or ReviewInputContext.
Introduce ChangedFile/FileDiff model.
Prompt builder includes actual diff context when available.
Parser remains unchanged.
API response remains stable where possible.
Frontend adds minimal diff input field or textarea.
Mock mode remains stable.
No GitHub auth yet unless tightly scoped.
```

Also note:

```text
MiMo live-call hardening and explicit HTTP timeout can be included if small, but should not derail diff-context work.
```

---

## 19. Required Handoff

Create:

```text
tasks/round-09/03-qoder-xiaomi-mimo-ai-review-provider-v1-independent-review-handoff.md
```

Use this exact structure:

```markdown
# Qoder Handoff: Xiaomi MiMo AI Review Provider v1 Independent Review

## 1. Verdict

## 2. Executive Summary

## 3. Inputs Reviewed

## 4. Agent Structure and Flow

## 5. Provider Architecture Review

## 6. Configuration and Secret Handling Review

## 7. Xiaomi MiMo Client Review

## 8. Prompt and Parser Review

## 9. Fallback Semantics Review

## 10. API Contract Review

## 11. Persistence Review

## 12. Frontend Review

## 13. Product Semantics Review

## 14. Documentation Review

## 15. Validation Evidence Assessment

## 16. Live MiMo Verification Decision

## 17. Remaining Risks

## 18. Final Recommendation for Round 10
```

## 20. Verdict Rules

Use:

```text
ROUND_09_CLOSED_READY_FOR_ROUND_10
```

if all are true:

```text
Provider architecture is clean.
Default mock mode is preserved.
MiMo path is implemented behind ReviewProvider.
Fallback semantics are safe.
No secret leakage is found.
API contract is unchanged.
Persistence model is unchanged or safely extended.
Frontend handles MIMO without redesign.
Codex's summary patch is acceptable.
No unresolved blocker remains.
```

Use:

```text
ROUND_09_BLOCKED_NEEDS_FIX
```

only if there is a serious defect such as:

```text
Application cannot start.
Backend tests fail.
Frontend cannot build.
Mock mode broken.
MiMo mode breaks task creation.
Missing-key fallback broken.
Parser can persist malformed invalid findings.
API contract changed unexpectedly.
Secrets leaked.
Public API exposes raw prompt/model output/fallback reason.
Persistence broken after restart.
Documentation overclaims production real PR review.
```

## 21. Final Instruction

Be strict, but do not block on non-goal items.

Round 09 should close if CodeReviewX has safely reached this state:

```text
A configurable Xiaomi MiMo-powered review agent prototype with safe mock fallback.
```

Round 09 should not be judged as needing this state:

```text
A production-grade GitHub PR review agent with real diff ingestion, auth, trace UI, and full provider observability.
```

The likely next architecture step is:

```text
Round 10: PR / Diff Context v1
```