# tasks/round-11/01-cursor-frontend-agent-result-presentation-v1.md

# Cursor Task: Round 11 - Frontend Agent Result Presentation v1

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 11
- Task: `01-cursor-frontend-agent-result-presentation-v1`
- Owner: Cursor
- Task Type: Frontend UX polish / MVP productization
- Previous Round: Round 10 - PR / Diff Context v1
- Current Phase: Stage 1 MVP Delivery
- Target Output: Polished frontend review-agent MVP presentation

---

## 2. Background

CodeReviewX has completed the core review-agent pipeline:

```text
Input -> Context -> Pipeline -> Provider Selection -> Provider -> Prompt -> Model Client -> Parser -> Finding -> Persistence -> API DTO -> Frontend
```

Current MVP capability:

```text
User creates a review task with:
  - repoUrl
  - prNumber
  - optional diffText

System runs:
  - MockReviewProvider by default
  - XiaomiMiMoReviewProvider when configured

System returns:
  - persisted review task
  - structured findings
  - risk level
  - issue summary
  - frontend task/detail presentation
```

Round 10 added optional pasted PR diff context. Round 11 must make the product feel like a coherent AI code review agent MVP, not a backend/API validation screen.

---

## 3. Round 11 Goal

Implement:

```text
Frontend UX Polish + Agent Result Presentation v1
```

Primary goal:

```text
Make CodeReviewX understandable, demo-ready, and visually coherent as a Manual Diff-Grounded AI Code Review Agent MVP.
```

Priorities:

1. Improve first-screen clarity.
2. Polish create review task form.
3. Improve optional diff input UX.
4. Improve review result summary.
5. Improve issue card readability.
6. Improve loading, empty, and error states.
7. Use accurate review-agent product copy.
8. Update README for current MVP positioning.
9. Preserve all existing backend/API behavior.

---

## 4. Product Positioning

Use this positioning consistently:

```text
CodeReviewX is a locally runnable AI-assisted code review agent prototype.

It supports manual review task creation with repository URL, PR number, and optional pasted PR diff context.
```

Correct MVP label:

```text
Manual Diff-Grounded AI Code Review Agent MVP
```

Do not claim:

```text
Automatic GitHub PR fetching
GitHub App integration
Private repository access
Production-grade code review
Full repository analysis
Autonomous multi-tool agent
RAG-based engineering intelligence
MCP-based tool system
Long-term memory agent
```

---

## 5. Explicit Non-Goals

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
```

Round 11 is presentation polish only.

---

## 6. Required Frontend Work

### 6.1 Main Layout

Improve the main page so users immediately understand:

```text
What CodeReviewX does
How to create a review task
What input the review agent uses
What result the agent produced
What current MVP limitations are
```

Allowed changes:

```text
Header polish
Intro / hero panel
Cleaner page spacing
Better visual hierarchy
Better responsive layout
Better task list/detail organization
Better copywriting
```

Do not add a new UI framework.

---

### 6.2 Create Review Task Form

Polish the create task form.

The form must include:

```text
Repository URL
Pull Request Number
Optional PR diff textarea
Submit button
Validation feedback
Loading/submitting state
```

Required behavior:

```text
Submitting without diff still works.
Submitting with diff still works.
Whitespace-only diff is omitted or normalized.
diffText > 20000 characters is blocked client-side.
Backend validation remains the source of truth.
```

Add or improve:

```text
Character counter for diffText
Clear oversized diff validation message
Disabled submit state while submitting
Helper copy for optional pasted diff
Non-disruptive loading state
```

Recommended helper copy:

```text
Paste a unified diff to let the review agent inspect actual code changes. Leave empty to run a metadata-only review.
```

Do not add:

```text
Diff viewer
Syntax highlighting
Monaco editor
File upload
GitHub fetch button
```

---

### 6.3 Review Summary

Improve review result summary.

It should clearly show:

```text
Overall risk level
Total issue count
Severity distribution
Repository URL
PR number
Created time
Provider/source information where available
```

Suggested labels:

```text
Review Summary
Risk Level
Findings
Severity Breakdown
Reviewed Target
Provider Source
```

Risk level display should be visually clear:

```text
HIGH
MEDIUM
LOW
```

Do not introduce charts or chart libraries.

---

### 6.4 Issue Cards

Improve issue cards so findings are easier to scan.

Each issue card should show:

```text
Severity
Category
Source
File path
Line range
Title
Description
Recommendation
```

Recommended structure:

```text
[Severity badge] [Category badge] [Source badge]

Title

Location:
  filePath:startLine-endLine

Description:
  ...

Recommendation:
  ...
```

Rules:

```text
Do not hide existing fields.
Do not change backend DTOs unless absolutely necessary.
Do not expose raw prompt, raw model response, or raw diff content.
```

---

### 6.5 Loading, Empty, and Error States

Add or improve states for:

```text
Empty task list
No selected task
No findings
Backend unavailable
Task creation failed
Task creation loading
Task/detail fetching loading
```

Suggested copy:

```text
No review tasks yet. Create one to start an agent review.
No findings were returned for this review.
Backend is unavailable. Check that backend-java is running on localhost:8080.
The review agent could not create this task. Please check the input and try again.
```

---

### 6.6 Source / Provider Labels

Keep internal source values accurate:

```text
MOCK
MIMO
```

Improve user-facing display:

```text
Source: Mock Provider
Source: Xiaomi MiMo
```

Use product language:

```text
Review Agent
AI Review
Findings
Recommendation
Reviewed Target
Provider Source
```

Avoid misleading language:

```text
GitHub-integrated review
Automatic PR review
Production security scan
Full repository analysis
```

---

## 7. Backend Scope

Avoid backend changes.

Allowed only if necessary and API-safe:

```text
Small README updates
Small tests if required
Sanitized user-facing error copy
Tiny DTO helper with no response-shape breakage
```

Do not change:

```text
Existing endpoints
Response shape
Provider fallback behavior
riskLevel / issueSummary.riskLevel consistency
Persistence model
Provider architecture
Prompt/model output exposure rules
```

If backend is touched, run backend tests.

---

## 8. Documentation Requirements

Update README where appropriate.

README must document:

```text
Current MVP positioning
How to run backend
How to run frontend
How to create review task
Optional diffText behavior
Mock mode default
Xiaomi MiMo environment configuration
No automatic GitHub ingestion
Known limitations
Post-MVP enhancement roadmap
```

Correct wording:

```text
CodeReviewX supports optional pasted PR diff context.
```

Incorrect wording:

```text
CodeReviewX automatically fetches and reviews GitHub PRs.
```

---

## 9. Post-MVP Roadmap Documentation

Document these as future directions only.

Do not implement them in Round 11.

```text
1. GitHub PR ingestion
2. Project rules / review policy
3. RAG / knowledge context
4. Function Calling / tool use
5. MCP integration
6. Memory system
7. PR comment workflow
```

Important boundary:

```text
Do not create placeholder services.
Do not add fake UI buttons.
Do not add unused abstractions.
Do not add dead code.
```

---

## 10. Required Tests

Update or add frontend tests for:

```text
Create form renders optional diff textarea.
Character counter renders if implemented.
Oversized diff shows validation error.
Submitting without diff works.
Submitting with diff works.
Loading/submitting state renders.
Empty task list state renders.
No findings state renders.
Review summary renders risk level and severity breakdown.
Issue cards render severity/category/source/location/title/description/recommendation.
Backend unavailable or error state renders if already testable.
```

Run:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

If backend is touched, run:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

If backend is not touched, say so in the handoff.

---

## 11. Runtime Smoke

Perform local smoke if practical.

Backend:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
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

Frontend:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Browser smoke checklist:

```text
Backend status is visible and correct.
Create review without diff works.
Create review with diff works.
Oversized diff validation is visible.
Task list updates.
Task detail renders.
Review summary is visible.
Issue cards are readable.
Loading state is acceptable.
Empty/error states are acceptable.
No browser console errors.
Desktop responsive layout is acceptable.
```

---

## 12. Live MiMo Verification

Live MiMo verification is optional in this round.

Only run it if a key is available locally through environment variables.

Rules:

```text
Use MIMO_API_KEY from environment only.
Do not write the key into source code.
Do not print the key.
Do not commit the key.
Do not include the key in handoff.
Do not expose raw prompt/model output.
```

If not run, write:

```text
Live MiMo verification not executed in Round 11.
```

---

## 13. Agent Structure and Flow

The final handoff must include this section:

```markdown
## Agent Structure and Flow
```

Use this flow:

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

## 14. Acceptance Criteria

### UX / Frontend

- [ ] Main app layout is clearer and more product-like.
- [ ] Create form works with and without diff.
- [ ] Optional diff textarea remains available.
- [ ] Oversized diff validation works.
- [ ] Review summary is easier to understand.
- [ ] Issue cards are easier to scan.
- [ ] Loading, error, and empty states are improved.
- [ ] Existing task list/detail flow still works.
- [ ] No new UI library.
- [ ] No visual diff viewer or syntax highlighting package.

### Agent Presentation

- [ ] UI clearly communicates “review agent”.
- [ ] Provider/source labels are understandable.
- [ ] Risk/severity/category are visually distinct.
- [ ] Recommendations are visible and readable.
- [ ] Manual pasted diff limitation is clear.

### Backend/API

- [ ] Existing API response shape remains stable.
- [ ] No raw diff/prompt/model output is exposed.
- [ ] Mock fallback remains safe.
- [ ] MiMo configuration remains environment-based.
- [ ] Backend tests pass if backend is touched.

### Tests

- [ ] Frontend typecheck passes.
- [ ] Frontend build passes.
- [ ] Frontend tests pass.
- [ ] Backend tests pass or reason documented.
- [ ] Runtime smoke completed or blocker documented.

### Documentation

- [ ] README reflects current MVP state.
- [ ] README does not overclaim GitHub integration.
- [ ] Known limitations are documented.
- [ ] Run/demo instructions are clear.
- [ ] Post-MVP roadmap is documented as future work only.

---

## 15. Required Handoff

Create:

```text
tasks/round-11/01-cursor-frontend-agent-result-presentation-v1-handoff.md
```

The handoff must include:

```markdown
# Cursor Handoff: Round 11 - Frontend Agent Result Presentation v1

## Summary

## Files Changed

## UX Changes

## Create Form Behavior

## Review Summary Behavior

## Issue Card Behavior

## Loading / Empty / Error States

## Backend/API Compatibility

## Documentation Updates

## Agent Structure and Flow

## Tests Run

## Runtime Smoke

## Live MiMo Verification

## Scope Boundary Confirmation

## Known Issues / Follow-ups

## Recommendation for Codex Validation
```

Scope boundary confirmation must explicitly state:

```text
No GitHub ingestion was implemented.
No RAG was implemented.
No MCP was implemented.
No Function Calling was implemented.
No Memory system was implemented.
No PR comment workflow was implemented.
No new UI library was introduced.
Backend/API response shape was preserved.
```

---

## 16. Final Instruction

Polish CodeReviewX into a coherent, trustworthy, demo-ready Manual Diff-Grounded AI Code Review Agent MVP.

Do not expand the agent architecture in Round 11.

Keep the implementation small, clear, testable, and compatible with Round 12 final hardening.