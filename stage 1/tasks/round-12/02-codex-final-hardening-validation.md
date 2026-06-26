# tasks/round-12/02-codex-final-hardening-validation.md

# Codex Task: Round 12 Final Hardening Validation

## 1. Task Metadata

```text
Project: CodeReviewX
Round: Round 12
Agent: Codex
Task: Independent validation of final hardening, demo readiness, provider safety, and MVP delivery readiness
Input Documents:
  - tasks/round-12/00-round-12-start.md
  - tasks/round-12/01-cursor-final-hardening-demo-readiness-handoff.md
  - tasks/round-12/demo-script.md
  - tasks/round-12/final-mvp-checklist.md
Expected Handoff:
  - tasks/round-12/02-codex-final-hardening-validation-handoff.md
```

## 2. Current Status

Cursor reports Round 12 final hardening is complete.

Reported status:

```text
Backend tests: 84 passed
Frontend typecheck: pass
Frontend build: pass
Frontend tests: 38 passed
Backend runtime smoke: pass
Frontend dev server smoke: pass
Live MiMo verification: not executed because MIMO_API_KEY unavailable
Provider timeout: added 60s default timeout
Demo script: created
Final MVP checklist: created
Scope creep: none reported
```

Codex must now independently validate these claims.

Do not assume Cursor’s result is sufficient without verification.

## 3. Primary Objective

Validate whether CodeReviewX is ready to proceed to Qoder final MVP delivery review.

Target conclusion:

```text
Proceed to Qoder final review
```

or, if blockers exist:

```text
Do not proceed; exact blockers listed
```

## 4. MVP Positioning to Preserve

Use this exact MVP positioning:

```text
Manual Diff-Grounded AI Code Review Agent MVP
```

CodeReviewX should be described as:

```text
A locally runnable AI-assisted code review agent prototype.

It supports manual review task creation with repository URL, PR number, and optional pasted PR diff context.

It can run with a safe Mock provider by default, or with Xiaomi MiMo when configured through environment variables.
```

Do not allow documentation, UI copy, API behavior, or handoff language to claim:

```text
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
long-term memory
PR comment write-back
```

## 5. Strict Scope

This is validation, not feature development.

### Allowed

Only apply small fixes if a validation blocker is found:

```text
test fix
smoke blocker fix
documentation correction
provider error sanitization correction
timeout configuration correction
minor frontend demo-blocking fix
minor backend demo-blocking fix
```

### Forbidden

Do not implement:

```text
GitHub OAuth
GitHub App installation
automatic PR fetching
repository clone
private repository access
PR comment write-back
RAG
vector database
embedding service
MCP
Function Calling
tool registry
long-term Memory
multi-agent planner
async job queue
streaming
trace UI
provider registry UI
auth
organization/team model
dashboard analytics
Monaco editor
syntax highlighting package
visual diff viewer
new UI library
chart library
production deployment
CI/CD pipeline
large UI redesign
new backend architecture
```

If you find a missing Stage 2 feature, document it as a limitation. Do not implement it.

## 6. Required Validation Sequence

Complete these steps in order.

---

## 7. Inspect Cursor Changes

Review Cursor’s changes.

Focus on:

```text
backend-java/src/main/java/.../mimo/XiaomiMiMoClient.java
backend-java/src/main/java/.../mimo/XiaomiMiMoProperties.java
backend-java/src/main/resources/application.yml
backend-java/README.md
README.md
tasks/round-12/demo-script.md
tasks/round-12/final-mvp-checklist.md
frontend files if changed
backend tests if changed
```

Validate:

```text
timeout configuration is correctly wired
default timeout is bounded and reasonable
MIMO_TIMEOUT_SECONDS is documented if supported
mock provider remains default
provider=mimo remains env-key based
no API key or secret is committed
no raw prompt/model response is exposed through public API/UI
no large refactor was introduced
```

## 8. Backend Test Validation

Run:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Expected:

```text
all backend tests pass
```

Record:

```text
test command
pass/fail result
test count if available
failures if any
```

If tests fail, identify whether the failure is:

```text
environmental
pre-existing
introduced by Cursor
true MVP blocker
```

Apply only the smallest safe fix if needed.

## 9. Frontend Test Validation

Run:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

Expected:

```text
typecheck passes
build passes
all frontend tests pass
```

Record:

```text
typecheck result
build result
test result
test count if available
failures if any
```

Apply only the smallest safe fix if needed.

## 10. Backend Runtime Smoke

Start backend:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

If port `8080` or H2 file lock is already occupied:

```text
Do not start a second conflicting instance.
Either stop the existing process or validate against the already-running backend.
Document exactly which approach was used.
```

Health check:

```bash
curl http://localhost:8080/api/health
```

Expected:

```text
success=true
status=UP
```

Create metadata-only task:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/demo","prNumber":1201}'
```

Create diff-grounded task:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/demo","prNumber":1202,"diffText":"diff --git a/src/AuthController.java b/src/AuthController.java\n+String token = request.getHeader(\"Authorization\");\n+log.info(\"token={}\", token);\n"}'
```

Validate both responses:

```text
success=true
status=SUCCESS
riskLevel present
issueSummary present
issues present
provider/source visible in expected safe form
no raw diffText in response
no raw prompt in response
no raw model output in response
no API key or secret in response
```

Also validate:

```bash
curl http://localhost:8080/api/review-tasks
```

and, if task detail endpoint exists:

```bash
curl http://localhost:8080/api/review-tasks/{taskId}
```

Expected:

```text
list works
detail works
response shape remains stable
no sensitive fields exposed
```

## 11. Frontend Runtime Smoke

Start frontend:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Open:

```text
http://127.0.0.1:5173
```

Validate manually or through available browser tooling:

```text
page loads
backend connected status visible when backend is running
create task without diff works
create task with diff works
oversized diff is blocked
whitespace-only diff is omitted
task list updates
task detail renders
review summary visible
issue cards readable
provider labels readable
empty/loading/error states acceptable
desktop width acceptable
narrow/mobile width acceptable
no obvious browser console errors
no raw prompt exposed
no raw model output exposed
no raw diffText exposed in public UI
```

If browser automation is not available, document:

```text
Frontend runtime smoke was limited to dev server HTTP 200 plus manual/available inspection.
```

Do not mark browser smoke as fully automated unless actually automated.

## 12. Live Xiaomi MiMo Verification

Check whether `MIMO_API_KEY` is available in the Codex environment.

Do not print the key.

Do not write the key to source code.

Do not commit the key.

Do not include the key in handoff.

If `MIMO_API_KEY` is available, run live MiMo verification.

Start backend in MiMo mode:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run \
  -Dspring-boot.run.arguments="--codereviewx.review.provider=mimo"
```

Create review task without diff:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/demo","prNumber":1211}'
```

Create review task with safe diff:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/demo","prNumber":1212,"diffText":"diff --git a/src/AuthController.java b/src/AuthController.java\n+String token = request.getHeader(\"Authorization\");\n+log.info(\"token={}\", token);\n"}'
```

Validate:

```text
response shape remains stable
structured findings are handled if returned
zero findings are handled if returned
fallback to mock is safe if MiMo fails
no key leakage
no raw prompt leakage
no raw model output leakage
no stack traces in API response
```

If `MIMO_API_KEY` is unavailable, write exactly:

```text
Live MiMo verification not executed because MIMO_API_KEY was not available in the local environment.
```

Do not block MVP closure solely because live MiMo could not be executed, if mock/default and fallback behavior pass.

## 13. Provider Timeout and Failure Handling Validation

Independently inspect provider safety.

Validate:

```text
XiaomiMiMoClient has bounded connect/read timeout
timeout default is reasonable
timeout can be configured through environment/property
missing MIMO_API_KEY does not crash default mock mode
provider=mimo without key falls back safely or fails safely as intended
client HTTP error falls back safely or is sanitized
parser failure falls back safely or is sanitized
unexpected provider runtime error is contained
API/UI do not expose secrets
API/UI do not expose raw prompt
API/UI do not expose raw model output
API/UI do not expose stack traces
logs do not print MIMO_API_KEY
logs do not print Authorization header
logs do not print full raw model response unless intentionally safe and non-public
```

Run targeted tests if they exist, especially:

```text
ConfigurableReviewProviderTest
XiaomiMiMoClientTest
XiaomiMiMoReviewProvider tests if present
ReviewPipelineService tests if present
controller response-shape tests if present
```

Add a small regression test only if a real gap is found.

## 14. Documentation Validation

Review:

```text
README.md
backend-java/README.md
frontend/README.md
tasks/round-12/demo-script.md
tasks/round-12/final-mvp-checklist.md
```

Validate documentation includes:

```text
MVP positioning
current capabilities
how to run backend
how to run frontend
how to run tests
how to create review task
how optional diffText works
Mock provider default
Xiaomi MiMo environment configuration
MIMO_TIMEOUT_SECONDS if supported
live MiMo verification note
known limitations
demo script
post-MVP roadmap as future-only
```

Known limitations must include:

```text
No automatic GitHub PR fetching
No GitHub App integration
No private repository access
No PR comment write-back
No visual diff viewer
No syntax highlighting
No RAG
No MCP
No Function Calling
No Memory system
No production auth/team model
```

Fix documentation only if it overclaims, omits a required limitation, or contradicts actual behavior.

## 15. Demo Script Validation

Review:

```text
tasks/round-12/demo-script.md
```

Validate it includes:

```text
1. Start backend
2. Start frontend
3. Explain product positioning
4. Show backend status
5. Create metadata-only review
6. Create diff-grounded review
7. Explain review summary
8. Explain issue cards
9. Show known limitations
10. Explain post-MVP roadmap
```

Validate demo script does not claim:

```text
automatic GitHub fetching
repository clone
private repository access
PR write-back
RAG
MCP
Function Calling
Memory
production-grade review
```

Validate it documents the H2 single-instance limitation if relevant.

## 16. Final MVP Checklist Validation

Review:

```text
tasks/round-12/final-mvp-checklist.md
```

Validate checklist truthfulness.

It must reflect actual Codex findings, not only Cursor claims.

If Codex results differ, update checklist carefully.

Required checklist items:

```text
Frontend typecheck pass
Frontend build pass
Frontend tests pass
Backend tests pass
Runtime backend smoke pass
Runtime frontend smoke pass
Live MiMo verification pass or documented as not executed
Root README accurate
Frontend README accurate
Demo script ready
Known limitations documented
No Stage 2 features introduced
No secrets committed
No raw prompt/model output exposed
No raw diffText exposed in public API response
Mock provider default works
MiMo configuration remains environment-based
Provider failure behavior safe
```

## 17. Scope Boundary Validation

Confirm no Stage 2 feature leakage.

Explicitly check and report:

```text
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
```

If any was added, decide whether it is:

```text
harmless documentation-only future roadmap
actual implementation scope creep
MVP blocker
```

## 18. Agent Structure and Flow

Every Round 12 handoff must include this exact section.

```markdown
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

## 19. Completion Criteria

Codex may recommend moving to Qoder only if:

```text
backend tests pass
frontend typecheck/build/tests pass
backend runtime smoke passes or exact non-blocking reason documented
frontend runtime smoke passes or exact non-blocking reason documented
MiMo live verification executed if key available
MiMo absence documented if key unavailable
provider timeout/failure behavior is safe
documentation is accurate and non-overclaiming
demo script is ready
final MVP checklist is accurate
no Stage 2 features were introduced
no secrets are committed
no raw prompt/model output/diffText exposed through public API/UI
```

## 20. Required Handoff

Create:

```text
tasks/round-12/02-codex-final-hardening-validation-handoff.md
```

Use this structure:

```markdown
# Codex Handoff: Round 12 Final Hardening Validation

## 1. Summary

## 2. Validation Scope

## 3. Files Reviewed

## 4. Files Changed

## 5. Backend Test Results

## 6. Frontend Test Results

## 7. Backend Runtime Smoke Result

## 8. Frontend Runtime Smoke Result

## 9. Live MiMo Verification Result

## 10. Provider Timeout and Failure Handling Validation

## 11. Sensitive Data Exposure Review

## 12. Documentation Validation

## 13. Demo Script Validation

## 14. Final MVP Checklist Validation

## 15. Agent Structure and Flow

## 16. Scope Boundary Confirmation

## 17. Known Remaining Issues

## 18. Final Verdict

## 19. Recommendation for Qoder
```

### 20.1 Final Verdict Format

Use exactly one of:

```text
CODEX_ROUND_12_VALIDATED_READY_FOR_QODER
```

or:

```text
CODEX_ROUND_12_BLOCKED
```

If blocked, list exact blockers and minimum required fixes.

### 20.2 Recommendation for Qoder

If validation passes, recommend:

```text
Proceed to Qoder final MVP delivery review.
Expected final target: QODER_ROUND_12_APPROVED_FINAL_MVP_DELIVERY.
```

If validation does not pass, recommend:

```text
Do not proceed to Qoder until the listed blockers are resolved.
```

## 21. Final Instruction

This is the final validation step before Qoder delivery review.

Be strict on correctness, secrets, provider safety, documentation accuracy, and scope boundary.

Do not expand product capability.

Do not start post-MVP implementation.

Validate that CodeReviewX is deliverable as a locally runnable Manual Diff-Grounded AI Code Review Agent MVP.