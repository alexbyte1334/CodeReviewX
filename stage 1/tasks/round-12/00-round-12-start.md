# tasks/round-12/00-round-12-start.md

# Round 12 Start: Final Hardening + Live MiMo Verification + Demo Readiness

## 1. Round Metadata

- Project: CodeReviewX
- Round: Round 12
- Theme: Final Hardening + Live MiMo Verification + Demo Readiness
- Task Type: MVP final hardening / delivery readiness
- Previous Round:
  - Round 11: Frontend UX Polish + Agent Result Presentation v1
- Previous Final Verdict:
  - `QODER_ROUND_11_APPROVED_CLOSE`
- Current Delivery Phase:
  - Stage 1 MVP finalization
- First Task To Generate:
  - `tasks/round-12/01-cursor-final-hardening-demo-readiness.md`

---

## 2. Current Project Status

Round 11 is approved for closure.

CodeReviewX now has a coherent MVP presentation layer:

```text
Manual Diff-Grounded AI Code Review Agent MVP
```

Current validated capabilities:

```text
Input:
  repoUrl + prNumber + optional pasted diffText

Context:
  ReviewContext with optional diffText

Orchestration:
  ReviewPipelineService

Provider Selection:
  ConfigurableReviewProvider

Providers:
  MockReviewProvider
  XiaomiMiMoReviewProvider

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

API:
  ReviewTaskResponse

Frontend:
  polished review summary and issue cards
```

Round 11 validation status:

```text
Frontend typecheck: pass
Frontend build: pass
Frontend tests: 38 passed
Backend tests: 84 passed
Runtime API smoke: pass
Browser smoke: pass through Codex evidence
Qoder verdict: approved close
```

---

## 3. Round 12 Purpose

Round 12 is the final Stage 1 MVP hardening round.

The goal is not to add major features.

The goal is to make CodeReviewX ready for a credible local demo and MVP handoff.

Round 12 should answer:

```text
Can CodeReviewX be delivered as a locally runnable Manual Diff-Grounded AI Code Review Agent MVP?
```

---

## 4. MVP Positioning

Use this positioning consistently:

```text
CodeReviewX is a locally runnable AI-assisted code review agent prototype.

It supports manual review task creation with repository URL, PR number, and optional pasted PR diff context.

It can run with a safe Mock provider by default, or with Xiaomi MiMo when configured through environment variables.
```

Correct MVP name:

```text
Manual Diff-Grounded AI Code Review Agent MVP
```

Do not claim:

```text
Automatic GitHub PR fetching
GitHub App integration
Private repository access
Production-grade review
Full repository analysis
Autonomous engineering agent
RAG-based project intelligence
MCP-based tool system
Long-term memory
PR comment write-back
```

---

## 5. Round 12 Primary Goal

Implement:

```text
Final Hardening + Live MiMo Verification + Demo Readiness
```

Round 12 should focus on:

1. final backend/frontend test pass;
2. final runtime smoke;
3. live Xiaomi MiMo verification if `MIMO_API_KEY` is available;
4. provider timeout / failure handling review;
5. sanitized provider error behavior;
6. final README/demo polish;
7. known limitations list;
8. demo script;
9. final delivery checklist;
10. small bug fixes only.

---

## 6. Explicit Non-Goals

Do not implement:

```text
GitHub OAuth
GitHub App installation
automatic PR fetching
repository clone
private repo access
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

Round 12 is final hardening, not feature expansion.

---

## 7. Round 12 Scope Decision

Recommended scope:

```text
Test everything.
Smoke the full local flow.
Verify MiMo if possible.
Tighten provider failure behavior if needed.
Polish docs and demo script.
Close MVP.
```

Allowed changes:

```text
Small backend bug fix
Small frontend bug fix
Small test addition
Provider timeout configuration if missing or unsafe
Provider error message sanitization
README/demo documentation polish
Known limitation clarification
Final checklist
```

Forbidden changes:

```text
New product capability
New external integration
New UI framework
New persistence model
New endpoint family
Large refactor
Placeholder services for future roadmap
```

---

## 8. Live Xiaomi MiMo Verification

Live MiMo verification is important in Round 12 if a key is available locally.

Rules:

```text
Use environment-only MIMO_API_KEY.
Do not write the key into source code.
Do not commit the key.
Do not print the key.
Do not include the key in handoff.
Do not expose raw prompt or raw model response in public UI/API/docs.
Do not log full diffText if it may contain sensitive content.
```

Required verification if key is available:

```text
Start backend with provider=mimo.
Create review task without diff.
Create review task with small safe diffText.
Confirm success response shape.
Confirm findings are structured if returned.
Confirm zero findings are handled if returned.
Confirm fallback behavior is safe if MiMo fails.
Confirm no key leakage.
Confirm no raw prompt/model output leakage.
```

Safe sample diff:

```text
diff --git a/src/AuthController.java b/src/AuthController.java
+String token = request.getHeader("Authorization");
+log.info("token={}", token);
```

If key is not available, document exactly:

```text
Live MiMo verification not executed because MIMO_API_KEY was not available in the local environment.
```

Do not block MVP closure solely because no live key is available, as long as mock/default behavior and fallback behavior pass.

---

## 9. Provider Timeout and Failure Handling Review

Review provider failure handling.

Verify:

```text
Xiaomi MiMo HTTP client has bounded timeout behavior or documented limitation.
Provider failure does not crash task creation unexpectedly.
Fallback to Mock provider remains safe where intended.
User-facing errors do not expose secrets, raw prompts, raw model responses, stack traces, or API keys.
Logs do not print MIMO_API_KEY.
Logs do not print raw model output unless already intentionally safe and non-public.
```

Allowed small fixes:

```text
Add or adjust HTTP timeout config if missing and low-risk.
Sanitize exception messages exposed to API/UI.
Improve fallback test coverage.
Improve README explanation of provider failure behavior.
```

Do not redesign provider architecture.

---

## 10. Final Frontend Hardening

Verify the Round 11 UI remains intact.

Check:

```text
Hero and MVP limitation copy visible.
Backend status visible.
Create form works without diff.
Create form works with diff.
Oversized diff blocked.
Whitespace-only diff omitted.
Task list updates.
Task detail renders.
Review summary visible.
Issue cards readable.
Provider source labels user-facing.
No raw prompt/model output/diffText exposed.
Empty/loading/error states acceptable.
Desktop layout acceptable.
Narrow/mobile layout acceptable.
No browser console errors.
```

Small frontend fixes are allowed only if they address demo-blocking or obvious quality issues.

Do not redesign the UI.

---

## 11. Final Backend Hardening

Verify:

```text
Health endpoint works.
Create review task works.
List review tasks works.
Get review task detail works.
Mock provider default works.
MiMo provider path works or is documented as not live-verified.
Fallback behavior works.
Persistence works.
Response shape remains stable.
riskLevel and issueSummary.riskLevel remain compatible.
No raw diffText/prompt/model output in public API response.
```

Avoid backend changes unless necessary.

---

## 12. Required Tests

### 12.1 Frontend

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

### 12.2 Backend

Run:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Expected:

```text
all backend tests pass
```

### 12.3 Optional Additional Tests

Add only if needed:

```text
Provider timeout/failure test
MiMo fallback/sanitization test
Frontend smoke-related regression test
README/demo consistency check
```

Do not add brittle tests merely to increase count.

---

## 13. Runtime Smoke Requirements

### 13.1 Backend Smoke

Start backend:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

Health:

```bash
curl http://localhost:8080/api/health
```

Expected:

```text
success=true
status=UP
```

Create task without diff:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/demo","prNumber":1201}'
```

Create task with diff:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/demo","prNumber":1202,"diffText":"diff --git a/src/App.tsx b/src/App.tsx\n+const token = request.headers.authorization;\n"}'
```

Verify:

```text
success=true
status=SUCCESS
riskLevel present
issueSummary present
issues present
no raw diffText
no raw prompt
no raw model output
```

### 13.2 Frontend Smoke

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
Page loads
Backend connected status visible
Create without diff works
Create with diff works
Oversized diff blocked
Task list updates
Task detail renders
Review Summary visible
Issue cards readable
Backend unavailable state works
No browser console errors
Desktop width acceptable
Narrow/mobile width acceptable
```

---

## 14. Documentation Requirements

Update root README and frontend README only if necessary.

Final documentation must include:

```text
MVP positioning
Current capabilities
How to run backend
How to run frontend
How to run tests
How to create review task
How optional diffText works
Mock provider default
Xiaomi MiMo environment configuration
Live MiMo verification note
Known limitations
Demo script
Post-MVP roadmap
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

---

## 15. Demo Script Requirement

Create or update a concise demo script.

Recommended file:

```text
tasks/round-12/demo-script.md
```

The demo script should cover:

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

The script should be realistic and should not overclaim current capabilities.

---

## 16. Final Delivery Checklist

Create or update:

```text
tasks/round-12/final-mvp-checklist.md
```

Checklist should include:

```text
Frontend tests pass
Backend tests pass
Runtime backend smoke pass
Runtime frontend smoke pass
Live MiMo verification pass or documented as not executed
README accurate
frontend README accurate
Demo script ready
Known limitations documented
No Stage 2 features introduced
No secrets committed
No raw prompt/model output exposed
No raw diffText exposed in public API response
```

---

## 17. Agent Structure and Flow

Every Round 12 handoff must include:

```markdown
## Agent Structure and Flow
```

Use:

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

---

## 18. Acceptance Criteria

### 18.1 Final MVP Readiness

- [ ] CodeReviewX can be run locally.
- [ ] Backend starts successfully.
- [ ] Frontend starts successfully.
- [ ] Backend health endpoint works.
- [ ] Review task creation works without diff.
- [ ] Review task creation works with diff.
- [ ] Task list/detail flow works.
- [ ] Review summary is visible and readable.
- [ ] Issue cards are visible and readable.
- [ ] Backend unavailable state is handled.
- [ ] No browser console errors in smoke.

### 18.2 Tests

- [ ] Frontend typecheck passes.
- [ ] Frontend build passes.
- [ ] Frontend tests pass.
- [ ] Backend tests pass.
- [ ] Runtime API smoke passes.
- [ ] Browser smoke passes.

### 18.3 MiMo / Provider

- [ ] Mock provider default works.
- [ ] MiMo configuration remains environment-based.
- [ ] Live MiMo verified if key is available.
- [ ] If live MiMo not verified, reason is documented.
- [ ] Provider failure behavior is safe.
- [ ] No key leakage.
- [ ] No raw prompt/model output leakage.

### 18.4 Documentation

- [ ] Root README accurately describes MVP.
- [ ] Frontend README accurately describes UI and run flow.
- [ ] Known limitations documented.
- [ ] Demo script exists.
- [ ] Final MVP checklist exists.
- [ ] Post-MVP roadmap is future-only.

### 18.5 Scope Boundary

- [ ] No GitHub ingestion.
- [ ] No RAG.
- [ ] No MCP.
- [ ] No Function Calling.
- [ ] No Memory.
- [ ] No PR comment workflow.
- [ ] No auth/team model.
- [ ] No new UI library.
- [ ] No chart library.
- [ ] No visual diff viewer.
- [ ] No production deployment work.

---

## 19. Suggested Round 12 Task Sequence

Continue Cursor → Codex → Qoder.

### 19.1 Cursor Implementation

Generate:

```text
tasks/round-12/01-cursor-final-hardening-demo-readiness.md
```

Responsibilities:

```text
Inspect current project state.
Run frontend tests.
Run backend tests.
Run backend API smoke.
Run frontend browser smoke if practical.
Verify provider timeout/failure behavior.
Perform live MiMo verification if MIMO_API_KEY is available.
Apply only small safe fixes.
Update README/frontend README if needed.
Create demo script.
Create final MVP checklist.
Create Cursor handoff.
```

### 19.2 Codex Validation

Generate:

```text
tasks/round-12/02-codex-final-hardening-validation.md
```

Responsibilities:

```text
Validate Cursor hardening.
Re-run tests.
Re-run API/browser smoke where practical.
Verify MiMo result or documented absence.
Verify docs, demo script, checklist.
Verify no scope creep.
Create Codex handoff.
```

### 19.3 Qoder Final Review

Generate:

```text
tasks/round-12/03-qoder-final-mvp-delivery-review.md
```

Responsibilities:

```text
Independently judge whether MVP can be delivered.
Confirm final run/test/smoke evidence.
Confirm documentation and demo script.
Confirm no overclaiming.
Confirm no Stage 2 feature leakage.
Recommend final MVP closure or exact remaining blockers.
Create Qoder final handoff.
```

---

## 20. Final Instruction for Round 12

Round 12 is the final MVP hardening and demo-readiness round.

Do not start the post-MVP roadmap.

Do not add major features.

Make CodeReviewX deliverable as a locally runnable Manual Diff-Grounded AI Code Review Agent MVP.

The desired final outcome is:

```text
QODER_ROUND_12_APPROVED_FINAL_MVP_DELIVERY
```