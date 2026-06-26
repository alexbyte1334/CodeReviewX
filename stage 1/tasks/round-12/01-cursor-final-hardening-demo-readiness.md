# tasks/round-12/01-cursor-final-hardening-demo-readiness.md

# Cursor Task: Round 12 Final Hardening + Live MiMo Verification + Demo Readiness

## 1. Task Metadata

```text
Project: CodeReviewX
Round: Round 12
Agent: Cursor
Task: Final hardening, live MiMo verification, demo readiness
Input Document: tasks/round-12/00-round-12-start.md
Expected Handoff: tasks/round-12/01-cursor-final-hardening-demo-readiness-handoff.md
```

## 2. Current Phase

CodeReviewX is entering final Stage 1 MVP hardening.

Current MVP positioning:

```text
Manual Diff-Grounded AI Code Review Agent MVP
```

Current validated flow:

```text
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

Round 11 has been approved closed.

Round 12 is the final MVP delivery-readiness round.

## 3. Primary Objective

Make CodeReviewX ready for credible local demo and MVP handoff.

The goal is not to add new capabilities.

The goal is to verify, harden, document, and close the MVP.

Target final outcome:

```text
CodeReviewX can be delivered as a locally runnable Manual Diff-Grounded AI Code Review Agent MVP.
```

## 4. Strict Scope

### Allowed

You may make only small, safe changes:

```text
small backend bug fix
small frontend bug fix
small test addition
provider timeout/failure handling fix
provider error sanitization fix
README/demo documentation polish
known limitations clarification
final checklist creation
demo script creation
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

## 5. Required Work

Complete the following in order.

---

## 6. Repository Inspection

Inspect the current project state.

Confirm:

```text
backend-java exists and is runnable
frontend exists and is runnable
README files exist
Round 11 frontend result presentation remains intact
ReviewTaskResponse shape is stable
Mock provider remains default-safe
Xiaomi MiMo provider remains environment-configured
```

Do not refactor project structure.

---

## 7. Backend Validation

Run backend tests:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Expected:

```text
all backend tests pass
```

If tests fail, apply the smallest safe fix.

Do not introduce new backend architecture.

---

## 8. Frontend Validation

Run frontend checks:

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

If checks fail, apply the smallest safe fix.

Do not redesign UI.

---

## 9. Backend Runtime Smoke

Start backend:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

Verify health:

```bash
curl http://localhost:8080/api/health
```

Expected:

```text
success=true
status=UP
```

Create review task without diff:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/demo","prNumber":1201}'
```

Create review task with diff:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/demo","prNumber":1202,"diffText":"diff --git a/src/AuthController.java b/src/AuthController.java\n+String token = request.getHeader(\"Authorization\");\n+log.info(\"token={}\", token);\n"}'
```

Verify response:

```text
success=true
status=SUCCESS
riskLevel present
issueSummary present
issues present
no raw diffText exposed
no raw prompt exposed
no raw model output exposed
no API key exposed
```

---

## 10. Frontend Runtime Smoke

Start frontend:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Open:

```text
http://127.0.0.1:5173
```

Verify:

```text
page loads
backend connected status visible
create task without diff works
create task with diff works
oversized diff is blocked
whitespace-only diff is omitted
task list updates
task detail renders
review summary visible
issue cards readable
provider labels readable
backend unavailable state acceptable
desktop layout acceptable
narrow/mobile layout acceptable
no browser console errors
no raw prompt exposed
no raw model output exposed
no raw diffText exposed in public UI
```

Small UI fixes are allowed only for demo-blocking or obvious quality issues.

---

## 11. Xiaomi MiMo Live Verification

If `MIMO_API_KEY` is available locally, verify live MiMo mode.

Rules:

```text
use environment variable only
do not hardcode key
do not commit key
do not print key
do not include key in handoff
do not expose raw prompt
do not expose raw model response
do not log full diffText
```

Use safe sample diff:

```text
diff --git a/src/AuthController.java b/src/AuthController.java
+String token = request.getHeader("Authorization");
+log.info("token={}", token);
```

Verify:

```text
backend can start with provider=mimo
create review task without diff works or fails safely
create review task with safe diffText works or fails safely
response shape remains stable
structured findings are handled if returned
zero findings are handled if returned
fallback behavior is safe if MiMo fails
no key leakage
no raw prompt/model output leakage
```

If `MIMO_API_KEY` is unavailable, write exactly this in handoff:

```text
Live MiMo verification not executed because MIMO_API_KEY was not available in the local environment.
```

Do not block MVP closure solely because live key is unavailable if mock/default behavior and fallback behavior pass.

---

## 12. Provider Timeout and Failure Handling

Inspect Xiaomi MiMo provider path:

```text
XiaomiMiMoClient
XiaomiMiMoReviewProvider
ConfigurableReviewProvider
ReviewPipelineService
provider-related tests
error handling path
logging path
```

Verify:

```text
HTTP calls have bounded timeout or limitation is documented
provider failure does not expose secrets
provider failure does not expose stack traces to API/UI
provider failure does not expose raw prompt/model response
provider failure does not print MIMO_API_KEY
fallback behavior is safe where intended
mock provider default remains safe
```

Allowed small fixes:

```text
add timeout config if missing and low-risk
sanitize provider exception messages
add fallback/sanitization test if needed
improve README provider failure description
```

Do not redesign provider architecture.

---

## 13. Documentation Work

Update root README only if needed.

Update frontend README only if needed.

Documentation must accurately state:

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
live MiMo verification note
known limitations
demo script
post-MVP roadmap
```

Known limitations must include:

```text
no automatic GitHub PR fetching
no GitHub App integration
no private repository access
no PR comment write-back
no visual diff viewer
no syntax highlighting
no RAG
no MCP
no Function Calling
no Memory system
no production auth/team model
```

Do not overclaim.

---

## 14. Demo Script

Create or update:

```text
tasks/round-12/demo-script.md
```

The script must be concise and realistic.

Required sections:

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

Use the correct MVP name:

```text
Manual Diff-Grounded AI Code Review Agent MVP
```

Do not claim automatic GitHub fetching, RAG, MCP, Function Calling, Memory, PR write-back, or production-grade review.

---

## 15. Final MVP Checklist

Create or update:

```text
tasks/round-12/final-mvp-checklist.md
```

Checklist must include:

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

---

## 16. Acceptance Criteria

Cursor may mark this task complete only if:

```text
backend tests pass or exact blocker is documented
frontend typecheck/build/tests pass or exact blocker is documented
backend runtime smoke executed or exact blocker is documented
frontend runtime smoke executed or exact blocker is documented
MiMo live verification executed if key available
MiMo absence documented if key unavailable
provider failure behavior reviewed
docs accurate and non-overclaiming
demo-script.md exists
final-mvp-checklist.md exists
no major feature expansion introduced
no Stage 2 roadmap work started
handoff created
```

---

## 17. Required Handoff

Create:

```text
tasks/round-12/01-cursor-final-hardening-demo-readiness-handoff.md
```

The handoff must include the following sections.

```markdown
# Cursor Handoff: Round 12 Final Hardening + Demo Readiness

## 1. Summary

## 2. Files Changed

## 3. Test Results

## 4. Runtime Backend Smoke Result

## 5. Runtime Frontend Smoke Result

## 6. Live MiMo Verification Result

## 7. Provider Timeout and Failure Handling Review

## 8. Documentation Updates

## 9. Demo Script Status

## 10. Final MVP Checklist Status

## 11. Agent Structure and Flow

## 12. Scope Boundary Confirmation

## 13. Known Remaining Issues

## 14. Recommendation for Codex
```

Use this exact Agent Structure and Flow section:

```markdown
## 11. Agent Structure and Flow

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

In Scope Boundary Confirmation, explicitly confirm:

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

## 18. Final Instruction

This is the final MVP hardening task.

Prioritize delivery readiness over feature work.

The expected downstream path is:

```text
Cursor hardening
→ Codex validation
→ Qoder final MVP delivery review
→ QODER_ROUND_12_APPROVED_FINAL_MVP_DELIVERY
```