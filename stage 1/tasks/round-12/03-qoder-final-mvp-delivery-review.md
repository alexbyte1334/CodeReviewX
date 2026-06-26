# tasks/round-12/03-qoder-final-mvp-delivery-review.md

# Qoder Task: Round 12 Final MVP Delivery Review

## 1. Task Metadata

```text id="xbvdr9"
Project: CodeReviewX
Round: Round 12
Agent: Qoder
Task: Final independent MVP delivery review
Input Documents:
  - tasks/round-12/00-round-12-start.md
  - tasks/round-12/01-cursor-final-hardening-demo-readiness-handoff.md
  - tasks/round-12/02-codex-final-hardening-validation-handoff.md
  - tasks/round-12/demo-script.md
  - tasks/round-12/final-mvp-checklist.md
Expected Handoff:
  - tasks/round-12/03-qoder-final-mvp-delivery-review-handoff.md
Target Final Verdict:
  - QODER_ROUND_12_APPROVED_FINAL_MVP_DELIVERY
```

## 2. Current Status

Cursor completed Round 12 hardening.

Codex independently validated the result and gave:

```text id="thpwjv"
CODEX_ROUND_12_VALIDATED_READY_FOR_QODER
```

Codex reported:

```text id="m47p24"
Backend tests: 84 passed
Frontend typecheck: pass
Frontend build: pass
Frontend tests: 38 passed
Backend runtime smoke: pass
Frontend browser runtime smoke: pass
Live MiMo verification: not executed because MIMO_API_KEY unavailable
Provider timeout/fallback behavior: safe
Sensitive data exposure review: pass
Documentation validation: pass
Demo script: ready
Final MVP checklist: ready
Scope boundary: no Stage 2 features introduced
```

Qoder must now perform the final independent delivery review.

This is a final judgment task, not an implementation task.

## 3. Primary Objective

Independently decide whether CodeReviewX can be delivered as:

```text id="7y1m23"
Manual Diff-Grounded AI Code Review Agent MVP
```

Final decision must answer:

```text id="ghzxyf"
Can CodeReviewX be closed as a locally runnable MVP suitable for credible demo and handoff?
```

Expected final outcome if accepted:

```text id="c09wj3"
QODER_ROUND_12_APPROVED_FINAL_MVP_DELIVERY
```

If not accepted, give:

```text id="y5wu1h"
QODER_ROUND_12_BLOCKED
```

and list exact blockers with minimum required fixes.

## 4. MVP Positioning to Enforce

Use this positioning:

```text id="ix699e"
CodeReviewX is a locally runnable AI-assisted code review agent prototype.

It supports manual review task creation with repository URL, PR number, and optional pasted PR diff context.

It can run with a safe Mock provider by default, or with Xiaomi MiMo when configured through environment variables.
```

Correct MVP name:

```text id="pgo64r"
Manual Diff-Grounded AI Code Review Agent MVP
```

Do not approve if the project or docs materially overclaim any of the following as implemented:

```text id="3wfrhf"
automatic GitHub PR fetching
GitHub App integration
private repository access
repository clone
production-grade review
full repository analysis
autonomous engineering agent
RAG-based project intelligence
MCP-based tool system
Function Calling tool use
long-term Memory
PR comment write-back
```

Future roadmap references are acceptable only if clearly marked as not implemented.

## 5. Qoder Scope

Qoder should independently review and judge.

### Allowed

```text id="gki7hj"
inspect files
run tests if practical
run runtime smoke if practical
review handoff evidence
review documentation
review demo script
review final checklist
verify no scope creep
verify no overclaiming
verify no secret exposure path
produce final handoff
```

### Avoid

Do not perform feature development.

Do not start Stage 2.

Do not redesign architecture.

Only make a small documentation correction if it is absolutely necessary to avoid a false final approval. If any code change appears necessary, treat it as a blocker unless it is truly trivial and safe.

## 6. Review Inputs

Review these documents first:

```text id="lkdq9l"
tasks/round-12/00-round-12-start.md
tasks/round-12/01-cursor-final-hardening-demo-readiness-handoff.md
tasks/round-12/02-codex-final-hardening-validation-handoff.md
tasks/round-12/demo-script.md
tasks/round-12/final-mvp-checklist.md
README.md
backend-java/README.md
frontend/README.md
```

Then inspect the implementation only as needed to validate the final decision.

Recommended implementation files:

```text id="dx74u1"
backend-java/src/main/resources/application.yml
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/ConfigurableReviewProvider.java
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoClient.java
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoProperties.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewTaskResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java
frontend source and tests relevant to review task creation/detail
```

## 7. Evidence to Confirm

Confirm whether the following evidence is credible and sufficient.

### 7.1 Backend

```text id="s7852h"
backend tests passed: 84 tests
health endpoint works
create review task without diff works
create review task with diff works
list/detail flow works
response contains riskLevel
response contains issueSummary
response contains issues
response does not expose raw diffText
response does not expose raw prompt
response does not expose raw model output
response does not expose API key or secrets
```

### 7.2 Frontend

```text id="3t2acu"
npm run typecheck passes
npm run build passes
npm test -- --run passes: 38 tests
frontend dev server loads
backend connected indicator visible
create task without diff works
create task with diff works
oversized diff guard works
whitespace-only diff behavior is safe
task list updates
task detail renders
review summary visible
issue cards readable
provider labels readable
mobile/narrow layout acceptable
no browser console errors reported
UI does not expose raw diffText/prompt/model output
```

### 7.3 Provider and MiMo

```text id="9bcqex"
mock provider remains default
MiMo configuration remains environment-based
MIMO_API_KEY is not committed
MIMO_API_KEY is not printed
MIMO_TIMEOUT_SECONDS or equivalent timeout config exists if claimed
XiaomiMiMoClient has bounded timeout if claimed
missing MiMo key handled safely
MiMo client failure fallback is safe
MiMo parser failure fallback is safe
provider errors do not expose stack traces, prompts, model output, keys, or auth headers
Live MiMo skip is documented if key unavailable
Live MiMo absence is not treated as blocker if fallback/default pass
```

### 7.4 Documentation and Demo

```text id="po9b4p"
root README accurately describes MVP
backend README accurately describes provider config and limitations
frontend README accurately describes UI/run flow and limitations
demo-script.md exists
demo-script.md covers 10 required demo sections
final-mvp-checklist.md exists
known limitations documented
post-MVP roadmap is future-only
documentation does not overclaim
```

## 8. Optional Re-Run Commands

If practical, re-run commands.

Backend:

```bash id="vzex42"
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Frontend:

```bash id="cflivw"
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

Backend smoke:

```bash id="e71mdi"
curl http://localhost:8080/api/health

curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/demo","prNumber":1201}'

curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/demo","prNumber":1202,"diffText":"diff --git a/src/AuthController.java b/src/AuthController.java\n+String token = request.getHeader(\"Authorization\");\n+log.info(\"token={}\", token);\n"}'
```

Frontend smoke:

```bash id="v3w1oe"
cd frontend
npm run dev -- --host 127.0.0.1
```

Open:

```text id="9m8kol"
http://127.0.0.1:5173
```

If Qoder cannot re-run tests or smoke due to environment limitations, it may rely on Cursor/Codex evidence, but must state this explicitly.

## 9. Live MiMo Handling

Check whether `MIMO_API_KEY` is available in the Qoder environment.

Do not print the key.

Do not commit the key.

Do not include the key in handoff.

If available, Qoder may run live MiMo verification.

If unavailable, write exactly:

```text id="i99erj"
Live MiMo verification not executed because MIMO_API_KEY was not available in the local environment.
```

This is not a blocker if:

```text id="jrn3x5"
mock provider default works
fallback behavior works
provider safety is validated
absence is documented
```

## 10. Final Acceptance Criteria

Approve final MVP delivery only if all are true:

```text id="1bndk9"
CodeReviewX can be run locally
backend test evidence is passing
frontend test evidence is passing
runtime backend smoke evidence is passing
runtime frontend smoke evidence is passing
review task can be created without diff
review task can be created with diff
task list/detail flow works
review summary and issue cards are visible/readable
mock provider default works
MiMo configuration is environment-based
provider failure behavior is safe
no secrets are committed
no raw prompt/model output exposed
no raw diffText exposed in public API response
documentation accurately describes MVP
known limitations are documented
demo script is ready
final checklist is ready
no Stage 2 features were introduced
no overclaiming remains
```

## 11. Blocking Conditions

Return `QODER_ROUND_12_BLOCKED` if any of these are true:

```text id="re5m9q"
backend tests fail without clear non-blocking explanation
frontend checks fail without clear non-blocking explanation
review task creation does not work
API exposes raw diffText, prompt, model output, key, or stack trace
provider failure can leak secrets or crash normal MVP flow
mock provider default is broken
MiMo configuration requires committed secrets
documentation claims unsupported capabilities as implemented
demo script is missing or materially overclaims
final checklist is missing or materially false
Stage 2 feature implementation was introduced and destabilizes MVP
```

## 12. Non-Blocking Known Issues

The following are acceptable if accurately documented:

```text id="qkpz0m"
Live MiMo not verified because MIMO_API_KEY is unavailable
file-based H2 allows only one backend instance using the database at a time
git metadata unavailable in a validation workspace
browser smoke partially manual if automated browser tooling is unavailable
```

These should not block final MVP delivery unless they hide a functional or safety failure.

## 13. Required Agent Structure and Flow Section

Every Round 12 handoff must include:

```markdown id="ma6mtg"
## Agent Structure and Flow

Input:
  repoUrl + prNumber + optional diffText

Context:
  ReviewContext with optional diffText

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
  ReviewTaskResponse + polished frontend summary and issue cards
```

## 14. Required Scope Boundary Confirmation

The final handoff must explicitly confirm whether each item is absent:

```text id="mc1bbt"
No GitHub ingestion added
No RAG added
No MCP added
No Function Calling added
No Memory system added
No PR comment workflow added
No auth/team model added
No new UI library added
No chart library added
No visual diff viewer added
No production deployment work added
No repository clone added
No private repository access added
No async job queue added
No streaming added
No trace UI added
No provider registry UI added
No Monaco editor added
No dashboard analytics added
```

## 15. Required Handoff

Create:

```text id="2btkdr"
tasks/round-12/03-qoder-final-mvp-delivery-review-handoff.md
```

Use this structure:

```markdown id="79gbre"
# Qoder Handoff: Round 12 Final MVP Delivery Review

## 1. Summary

## 2. Review Scope

## 3. Evidence Reviewed

## 4. Commands Re-Run

## 5. Backend Readiness Judgment

## 6. Frontend Readiness Judgment

## 7. Runtime Smoke Judgment

## 8. Provider and MiMo Safety Judgment

## 9. Sensitive Data Exposure Judgment

## 10. Documentation and Demo Judgment

## 11. Final Checklist Judgment

## 12. Agent Structure and Flow

## 13. Scope Boundary Confirmation

## 14. Known Non-Blocking Issues

## 15. Blocking Issues

## 16. Final Verdict

## 17. Final Recommendation
```

## 16. Final Verdict Format

Use exactly one of:

```text id="soqlch"
QODER_ROUND_12_APPROVED_FINAL_MVP_DELIVERY
```

or:

```text id="7gypea"
QODER_ROUND_12_BLOCKED
```

If blocked, list exact blockers and minimum required fixes.

## 17. Final Recommendation

If approved, write:

```text id="82b4bk"
CodeReviewX Stage 1 MVP can be closed as a locally runnable Manual Diff-Grounded AI Code Review Agent MVP.

Recommended next phase: post-MVP roadmap planning, starting with GitHub PR ingestion and real provider hardening.
```

If blocked, write:

```text id="x19itk"
Do not close Stage 1 MVP until the listed blockers are resolved and revalidated.
```

## 18. Final Instruction

This is the last Round 12 review step.

Do not add features.

Do not start post-MVP implementation.

Decide whether CodeReviewX is deliverable now.

The desired final outcome is:

```text id="rr63sr"
QODER_ROUND_12_APPROVED_FINAL_MVP_DELIVERY
```