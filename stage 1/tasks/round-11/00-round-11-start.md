# tasks/round-11/00-round-11-start.md

# Round 11 Start: Frontend UX Polish + Agent Result Presentation v1

## 1. Round Metadata

- Project: CodeReviewX
- Round: Round 11
- Theme: Frontend UX Polish + Agent Result Presentation v1
- Task Type: MVP productization / final delivery preparation round
- Previous Round:
  - Round 10: PR / Diff Context v1
- Previous Final Verdict:
  - `QODER_ROUND_10_APPROVED_CLOSE`
- Current Delivery Phase:
  - MVP delivery sprint
- First Task To Generate:
  - `tasks/round-11/01-cursor-frontend-agent-result-presentation-v1.md`

---

## 2. Current Project Status

Round 10 has been approved for closure.

CodeReviewX has now completed the core AI review-agent chain:

```text
Input -> Context -> Pipeline -> Provider Selection -> Provider -> Prompt -> Model Client -> Parser -> Finding -> Persistence -> API DTO -> Frontend
```

Current implemented capabilities:

```text
Input:
  repoUrl + prNumber + optional diffText

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
  ReviewPromptBuilder supports no-diff and diff-aware prompt variants

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

Round 10 materially improved the agent by enabling manually pasted PR diff context. The system can now ground Xiaomi MiMo prompts in actual code changes when `diffText` is provided.

However, the product is still closer to an engineering prototype than a polished MVP. Round 11 should make the frontend and result presentation coherent enough for demo and delivery.

---

## 3. MVP Delivery Strategy

The project should now follow a strict two-stage plan:

```text
Stage 1:
  Quickly deliver a usable MVP.

Stage 2:
  After MVP delivery, enhance it into a truly useful engineering agent.
```

Round 11 and Round 12 belong to **Stage 1: MVP delivery**.

Do not allow Stage 2 enhancements to leak into Round 11 unless they are tiny, isolated, and do not threaten delivery.

---

## 4. MVP Definition

The MVP should be defined as:

```text
CodeReviewX is a locally runnable AI code review agent prototype.

It supports:
  - creating review tasks
  - repoUrl + prNumber input
  - optional pasted PR diffText
  - Mock provider
  - Xiaomi MiMo provider
  - provider fallback
  - structured review findings
  - persisted tasks and issues
  - frontend review summary and issue card presentation
```

The MVP should not be positioned as:

```text
A production GitHub PR review platform.
A full GitHub App.
A fully autonomous engineering agent.
A RAG-based code intelligence system.
A multi-tool MCP agent.
A long-term memory agent.
```

Correct MVP positioning:

```text
Manual Diff-Grounded AI Code Review Agent MVP
```

---

## 5. Product Positioning

Use this framing:

```text
CodeReviewX is an AI-assisted code review agent prototype that reviews repository/PR metadata and optional pasted PR diffs through a provider-based review pipeline.
```

Avoid overclaiming:

```text
CodeReviewX automatically reviews GitHub pull requests.
CodeReviewX fully integrates with GitHub.
CodeReviewX can access private repositories.
CodeReviewX is production-ready.
CodeReviewX guarantees correct AI findings.
```

Correct current capability statement:

```text
CodeReviewX supports manual task creation with repoUrl, prNumber, and optional pasted diffText.
Mock mode is the safe default.
Xiaomi MiMo mode is available through environment configuration.
Automatic GitHub ingestion is not implemented in the MVP.
```

---

## 6. Round 11 Primary Goal

Implement:

```text
Frontend UX Polish + Agent Result Presentation v1
```

Goal:

```text
Make CodeReviewX look and feel like a coherent AI code review agent MVP rather than a backend/API validation screen.
```

Round 11 should improve:

1. first-screen clarity;
2. create review task experience;
3. optional diff input experience;
4. review result readability;
5. issue card presentation;
6. risk/severity/category/source display;
7. loading/error/empty states;
8. copywriting from “mock/demo” toward “review agent” terminology;
9. README and demo readiness.

Round 11 should avoid backend architecture changes unless small and API-safe.

---

## 7. Round 11 Scope Decision

Round 11 must prioritize frontend productization.

Recommended scope:

```text
Frontend polish first.
Result presentation second.
Documentation polish third.
Backend changes only if necessary and safe.
No major new agent capabilities.
```

Reason:

1. The core agent pipeline already works.
2. Round 10 gave the agent meaningful diff context.
3. The next bottleneck is user comprehension and demo readiness.
4. The project should finish MVP delivery within Round 12.
5. Adding GitHub ingestion, RAG, MCP, Memory, or Function Calling now would delay delivery.

---

## 8. Explicit Non-Goals for Round 11

Do not implement:

```text
GitHub OAuth
GitHub App installation
private repo access
automatic PR fetching
repository clone
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

Round 11 is **MVP presentation polish**, not agent-capability expansion.

---

## 9. Frontend UX Requirements

### 9.1 Page-Level Layout

Improve the main app layout so the user immediately understands:

```text
What CodeReviewX does.
How to create a review task.
What input the review agent uses.
What findings were produced.
What risk level means.
What the current MVP limitations are.
```

Acceptable changes:

```text
Header polish
Hero / intro panel
Better card spacing
Cleaner task list/detail layout
Improved responsive layout
Better visual hierarchy
Better empty states
Better copy
```

Forbidden changes:

```text
New UI library
Route overhaul
Dashboard analytics
Chart library
Monaco editor
Syntax highlighting
Visual diff viewer
Authentication UI
Team/organization UI
```

---

### 9.2 Create Review Task Form

Polish the create form.

It should clearly show:

```text
Repository URL
Pull Request Number
Optional PR diff
Submit action
Validation errors
Loading/submitting state
```

Recommended helper copy:

```text
Paste a unified diff to let the review agent inspect actual code changes. Leave empty to run a metadata-only review.
```

Behavior must remain:

```text
Submitting without diff works.
Submitting with diff works.
Whitespace-only diff is omitted or normalized.
diffText > 20000 characters is blocked client-side.
Backend validation remains the source of truth.
```

Add or improve:

```text
Character counter for diffText
Clear validation message for oversized diff
Disabled submit state while submitting
Non-disruptive loading state
Clear helper text for manual pasted diff limitation
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

### 9.3 Review Result Summary

Improve review result summary.

It should show:

```text
Overall risk level
Total issue count
Severity distribution
Repository URL
PR number
Created time
Provider/source information where available
```

Keep it simple.

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

Do not introduce a chart library.

---

### 9.4 Issue Card Presentation

Improve issue cards so findings are easier to scan.

Each issue card should clearly show:

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

Recommended layout:

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

Improve labels:

```text
Description
Recommendation
Location
Source
Severity
Category
```

Do not hide any existing fields.

Do not change backend DTOs unless absolutely necessary.

---

### 9.5 Empty, Loading, and Error States

Add or improve:

```text
Empty task list state
No selected task state
No findings state
Backend unavailable state
Task creation failed state
Loading state during task creation
Loading state when fetching tasks/details
```

Use product-appropriate copy.

Examples:

```text
No review tasks yet. Create one to start an agent review.
No findings were returned for this review.
Backend is unavailable. Check that backend-java is running on localhost:8080.
The review agent could not create this task. Please check the input and try again.
```

---

### 9.6 Source / Provider Copy Cleanup

Keep source values accurate:

```text
MOCK
MIMO
```

But display them with better user-facing labels:

```text
Source: Mock Provider
Source: Xiaomi MiMo
```

Recommended product copy:

```text
Review Agent
AI Review
Findings
Recommendation
Reviewed Target
Provider Source
```

Avoid misleading copy:

```text
GitHub-integrated review
Automatic PR review
Production-grade security scan
Full repository analysis
```

---

## 10. Backend Scope

Round 11 should avoid backend changes unless they are minor and API-safe.

Allowed backend changes:

```text
Small DTO formatting helper if already present
Small README updates
Provider timeout config only if trivial
Sanitized error/fallback copy if currently user-visible
Additional tests if frontend relies on existing fields
```

Avoid:

```text
New endpoints
Breaking response changes
Authentication
GitHub integration
Async job queue
Provider registry
Trace persistence
Raw prompt/model output persistence
Tool calling
Memory
RAG
MCP
```

If backend changes are made, they must preserve:

```text
Existing endpoints
Existing response shape
riskLevel == issueSummary.riskLevel
Mock fallback behavior
No raw diff/prompt/model output leakage
```

---

## 11. Live MiMo Verification Note

Round 10 did not execute live MiMo smoke with a real key.

Round 11 may include live MiMo verification only if the key is available locally and without committing or printing it.

Rules:

```text
Use environment-only MIMO_API_KEY.
Do not write the key into source code.
Do not print the key in logs or docs.
Do not include the key in handoff.
Do not expose raw prompt/model output publicly.
```

If performed, verify:

```text
provider=mimo with MIMO_API_KEY set
small diffText request
either MIMO findings or successful zero findings
safe fallback if provider fails
response shape unchanged
no key leakage
```

If not performed, document:

```text
Live MiMo verification not executed in Round 11.
```

Live MiMo verification is useful but should not derail Round 11 frontend/productization work.

---

## 12. Required Tests

### 12.1 Frontend Tests

Update or add tests for:

```text
Create form renders polished optional diff textarea.
Character counter renders if implemented.
Oversized diff shows validation error.
Submitting without diff still works.
Submitting with diff still works.
Loading/submitting state renders.
Empty task list state renders.
No findings state renders.
Issue cards render severity/category/source/location/title/description/recommendation.
Risk summary renders correctly.
Backend unavailable or error state renders if covered.
```

Run:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

### 12.2 Backend Tests

If backend is touched, run:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

If backend is not touched, still run backend tests if practical and document the result.

---

## 13. Runtime Smoke Requirements

### 13.1 Backend

Start backend:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

Confirm:

```bash
curl http://localhost:8080/api/health
```

Expected:

```text
success=true
status=UP
```

### 13.2 Frontend

Start frontend:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Browser smoke:

```text
Backend status UP.
Create review without diff.
Create review with diff.
Oversized diff validation visible.
List updates.
Detail renders.
Risk summary visible.
Issue cards readable.
Loading state acceptable.
Empty/error states acceptable.
No browser console errors.
Responsive layout acceptable at desktop width.
```

---

## 14. Documentation Requirements

Update README where appropriate.

Document:

```text
Current MVP positioning
How to run backend
How to run frontend
How to create review task with optional diffText
Mock mode default
MiMo mode environment configuration
No automatic GitHub ingestion
Frontend result presentation
Known limitations
Post-MVP enhancement direction
```

Do not overstate the product.

Correct wording:

```text
CodeReviewX supports optional pasted PR diff context.
```

Incorrect wording:

```text
CodeReviewX automatically fetches and reviews GitHub PRs.
```

---

## 15. Agent Structure and Flow

Every Round 11 handoff must include:

```markdown
## Agent Structure and Flow
```

It must show:

```text
Input -> Context -> Pipeline -> Provider Selection -> Provider -> Prompt -> Model Client -> Parser -> Finding -> Persistence -> API DTO -> Frontend
```

For Round 11, use:

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

## 16. MVP Acceptance Criteria

### 16.1 UX / Frontend

- [ ] Main app layout is clearer and more product-like.
- [ ] Create form remains functional with and without diff.
- [ ] Optional diff textarea remains available.
- [ ] Oversized diff validation remains.
- [ ] Review summary is easier to understand.
- [ ] Issue cards are easier to scan.
- [ ] Loading/error/empty states are improved.
- [ ] Existing task list/detail flow still works.
- [ ] No new UI library.
- [ ] No visual diff viewer or syntax highlighting package.

### 16.2 Agent Presentation

- [ ] UI communicates “review agent” clearly.
- [ ] Source/provider labels are understandable.
- [ ] Risk/severity/category are visually distinct.
- [ ] Recommendations are visible and readable.
- [ ] Current manual diff limitation is not hidden.

### 16.3 Backend/API

- [ ] Existing API response shape remains stable.
- [ ] No raw diff/prompt/model output exposed.
- [ ] Mock fallback remains safe.
- [ ] MiMo configuration remains environment-based.
- [ ] Backend tests pass if backend touched.

### 16.4 Tests

- [ ] Frontend typecheck passes.
- [ ] Frontend build passes.
- [ ] Frontend tests pass.
- [ ] Backend tests pass or reason documented.
- [ ] Runtime smoke completed or blockers documented.

### 16.5 Documentation

- [ ] README reflects current MVP state.
- [ ] No overclaiming of GitHub integration.
- [ ] Known limitations documented.
- [ ] Run/demo instructions are clear.
- [ ] Post-MVP enhancement roadmap is documented.

---

## 17. Post-MVP Enhancement Roadmap

The following directions are important, but they are **not Round 11 scope**.

They should be documented as the enhancement path after MVP delivery.

### 17.1 Enhancement 01: GitHub PR Ingestion

Goal:

```text
User pastes a GitHub PR URL.
System parses owner/repo/prNumber.
System fetches PR metadata, changed files, and diff.
System builds PullRequestContext automatically.
```

Potential components:

```text
GitHubPullRequestContextFetcher
GitHubClient
PullRequestContext
ChangedFile
FileDiff
GitHubAuthConfig
```

Expected value:

```text
This is the first major step that makes CodeReviewX meaningfully better than manually pasting code into an AI chat.
```

Not included in MVP because it introduces:

```text
GitHub token management
rate limits
public/private repo permission handling
API mocking
network failure handling
larger test matrix
```

---

### 17.2 Enhancement 02: Project Rules / Review Policy

Goal:

```text
Let the review agent evaluate code according to explicit project/team rules, not only general model knowledge.
```

Example rules:

```text
All external HTTP clients must configure timeout.
Controllers should not directly access repositories.
New backend endpoints require contract tests.
Secrets must not be hardcoded.
Frontend must not store tokens in localStorage.
```

Potential components:

```text
ReviewPolicy
ProjectRule
RuleSet
PromptPolicySection
PolicyAwareReviewPromptBuilder
```

Expected value:

```text
Makes findings more project-specific and reduces generic AI feedback.
```

---

### 17.3 Enhancement 03: RAG / Knowledge Context

Goal:

```text
Retrieve project documentation, coding standards, historical findings, security checklist, and architecture notes during review.
```

Potential components:

```text
KnowledgeIngestion
DocumentChunker
EmbeddingService
VectorStore
ReviewKnowledgeRetriever
RetrievedContext
```

Expected value:

```text
Turns CodeReviewX from a diff-grounded review agent into a project-knowledge-grounded review agent.
```

Should come after Project Rules unless strong document retrieval needs appear earlier.

---

### 17.4 Enhancement 04: Function Calling / Tool Use

Goal:

```text
Allow the model or orchestrator to call controlled tools during review.
```

Potential tools:

```text
getPullRequestDiff()
getChangedFiles()
getRelatedFiles()
getCiStatus()
getProjectRules()
getHistoricalFindings()
```

Potential components:

```text
ReviewTool
ToolRegistry
ToolCallExecutor
ToolResult
ToolAwareReviewProvider
```

Important constraint:

```text
Tool use must be permissioned, logged, testable, and bounded.
```

Expected value:

```text
Moves the system from one-shot workflow agent toward a tool-using engineering agent.
```

---

### 17.5 Enhancement 05: MCP Integration

Goal:

```text
Standardize external tool and data-source access through MCP where appropriate.
```

Potential MCP servers:

```text
GitHub MCP
Filesystem MCP
Database MCP
CI/CD MCP
Issue Tracker MCP
Documentation MCP
```

Expected value:

```text
Provides a scalable integration mechanism for external engineering systems.
```

Do not introduce MCP until the tool boundaries and permission model are clear.

---

### 17.6 Enhancement 06: Memory System

Goal:

```text
Let the agent improve review quality across tasks, repositories, users, and teams.
```

Recommended memory layers:

```text
Task Memory:
  current task, diff, findings, provider result

Project Memory:
  repo-specific rules, historical findings, recurring risks

Team Memory:
  severity preferences, review style, accepted false positives

Operational Memory:
  provider latency, failure rates, cost, fallback statistics
```

Potential components:

```text
ReviewMemoryService
ProjectMemoryStore
FindingHistoryStore
FalsePositiveFeedback
MemoryRetriever
```

Expected value:

```text
Makes the agent increasingly useful for a specific engineering team over time.
```

Do not implement long-term memory in MVP.

---

### 17.7 Enhancement 07: PR Comment Workflow

Goal:

```text
Turn review findings into actionable GitHub PR comments.
```

Potential flow:

```text
AI finding
  -> user confirms or edits
  -> system maps finding to file/line
  -> system posts GitHub review comment
  -> system saves comment status
```

Potential components:

```text
ReviewCommentDraft
GitHubReviewCommentClient
CommentStatus
HumanApprovalFlow
```

Expected value:

```text
Connects CodeReviewX to the real engineering workflow.
```

Should happen after GitHub ingestion is stable.

---

## 18. Enhancement Roadmap Boundary

Round 11 must not implement the enhancement roadmap.

Round 11 should only:

```text
Document the roadmap clearly.
Keep architecture compatible with future enhancements.
Avoid decisions that block GitHub ingestion, Project Rules, RAG, Tool Use, MCP, Memory, or PR comments later.
```

Allowed small preparation:

```text
Use accurate naming.
Keep ReviewContext clean.
Keep provider abstraction stable.
Keep response DTOs stable.
Avoid UI copy that implies current GitHub automation.
```

Forbidden preparation:

```text
Creating empty placeholder services for GitHub/RAG/MCP/Memory.
Adding unused abstractions.
Adding dead code.
Adding fake UI buttons for unimplemented capabilities.
```

---

## 19. Suggested Round 11 Task Sequence

Continue Cursor → Codex → Qoder.

### 19.1 Cursor Implementation

Generate:

```text
tasks/round-11/01-cursor-frontend-agent-result-presentation-v1.md
```

Responsibilities:

```text
Inspect current frontend.
Improve layout and copy.
Polish create form.
Polish optional diff experience.
Polish review summary.
Polish issue cards.
Improve loading/error/empty states.
Preserve API contract.
Avoid backend changes unless necessary.
Avoid all Post-MVP enhancement implementation.
Update tests.
Run frontend checks.
Run backend tests if backend touched.
Perform browser smoke.
Update README with MVP positioning and Post-MVP roadmap.
Create Cursor handoff.
```

### 19.2 Codex Validation

Generate:

```text
tasks/round-11/02-codex-frontend-agent-result-presentation-validation.md
```

Responsibilities:

```text
Inspect implementation.
Verify frontend behavior.
Verify no API breakage.
Verify no scope creep into Post-MVP enhancements.
Run frontend checks.
Run backend tests if needed.
Browser smoke.
Verify README accuracy and roadmap boundary.
Create Codex handoff.
```

### 19.3 Qoder Independent Review

Generate:

```text
tasks/round-11/03-qoder-frontend-agent-result-presentation-independent-review.md
```

Responsibilities:

```text
Independently judge whether frontend is MVP/demo-ready.
Verify user can understand the review-agent flow.
Verify issue presentation is readable.
Verify no misleading GitHub claims.
Verify Post-MVP roadmap is documented but not implemented.
Recommend whether Round 11 can close.
Recommend exact final Round 12 hardening/demo-readiness scope.
Create Qoder handoff.
```

---

## 20. Recommended Round 12 Direction

If Round 11 succeeds, Round 12 should be final MVP hardening and demo readiness:

```text
Round 12: Final Hardening + Live MiMo Verification + Demo Readiness
```

Round 12 should include:

```text
Live MiMo verification if key is available.
HTTP timeout configuration.
Provider failure classification cleanup.
Final README polish.
Final browser smoke.
Final backend/frontend test pass.
Known limitations list.
Demo script.
Final delivery checklist.
Optional final bug fixes only.
```

Round 12 should not introduce major new features.

Do not start Post-MVP enhancements in Round 12 unless MVP is already fully closed.

---

## 21. Final Instruction for Round 11

Round 11 must make CodeReviewX feel like a coherent AI review-agent MVP.

Essential instruction:

```text
Polish the frontend and result presentation.
Keep the backend/API stable.
Do not add GitHub integration.
Do not add RAG, MCP, Function Calling, Memory, or PR comments.
Do not redesign the system architecture.
Do not introduce heavy UI dependencies.
Improve clarity, trust, and demo readiness.
Document the Post-MVP enhancement roadmap without implementing it.
Keep all current Round 10 agent capabilities working.
```

First task to generate:

```text
tasks/round-11/01-cursor-frontend-agent-result-presentation-v1.md
```