# Cursor Task: Round 13 Product UI Restyle

## 1. Task Metadata

```text
Project: CodeReviewX
Round: Round 13
Agent: Cursor
Task: Product UI Restyle — macOS-like polished delivery interface
Input Document:
  stage 1.5/tasks/round-13/00-round-13-start.md
Expected Handoff:
  stage 1.5/handoff/round-13/01-cursor-product-ui-restyle-handoff.md
```

## 2. Primary Objective

Upgrade the existing frontend from a functional MVP page into a polished product interface while preserving all existing behavior.

Target outcome:

```text
CodeReviewX looks and feels like a credible AI code review product, not a raw local demo.
```

Do not implement Stage 2 features.

## 3. Files to Inspect First

Read these before editing:

```text
stage 1.5/start.md
stage 1.5/tasks/round-13/00-round-13-start.md
docs/AGENT_RULES.md
README.md
frontend/README.md
frontend/src/App.tsx
frontend/src/styles/app.css
frontend/src/components/ReviewTaskCreateForm.tsx
frontend/src/components/ReviewTaskList.tsx
frontend/src/components/ReviewTaskDetail.tsx
frontend/src/types/reviewTask.ts
frontend/src/utils/providerLabels.ts
frontend/src/utils/reviewSummary.ts
frontend/src/utils/riskLevel.ts
frontend/src/test/*
```

## 4. Implementation Scope

Allowed frontend work:

```text
Restructure App shell
Add left sidebar or command rail
Add polished top product bar/window chrome
Refine create review panel
Refine review history panel
Refine review detail/result panel
Add severity distribution bars
Add summary metric cards
Add risk level visual badge
Add provider/status chip
Add subtle CSS animations and transitions
Move limitations out of primary hero workflow
Improve responsive behavior
Update tests for changed accessible text/structure
```

Preferred technical approach:

```text
Use existing React + CSS
Use CSS transitions/animations first
Avoid new runtime dependencies unless absolutely necessary
Do not add chart libraries
Do not add a large UI framework
Keep API calls and DTO types unchanged
```

## 5. Visual Direction

Aim for:

```text
modern macOS-like desktop app
liquid glass influence
translucent panels
soft blur
clear content depth
professional gray/white surface system
calm accent colors
low-noise workspace
subtle hover/press motion
strong readability
```

Suggested layout:

```text
App Shell
  Left sidebar:
    CodeReviewX logo/name
    Review Agent nav item
    History nav item
    Provider status
    About/limits link or compact note

  Main workspace:
    Compact product header
    Create review panel
    Review history list

  Result/inspector area:
    Review summary
    Severity distribution
    Provider source
    Findings cards
```

Do not use a marketing landing page. The first screen must be the usable app.

## 6. Copy Requirements

Keep this product truth:

```text
Manual Diff-Grounded AI Code Review Agent MVP
```

But do not let the main workflow be dominated by MVP explanation text.

Good primary copy:

```text
CodeReviewX
Review Agent
Run Review
Review Workspace
Provider Status
Review History
Findings
Risk Level
Severity Breakdown
```

Move or compress:

```text
large MVP limitation copy
long tutorial text
repeated local demo disclaimers
```

Keep honest secondary limitations:

```text
Manual diff input only
Mock provider is default
MiMo requires local environment configuration
No automatic GitHub PR fetching yet
```

Forbidden wording:

```text
Connected GitHub App
Automatically fetches PRs
Reviews private repositories
Writes PR comments
Full repository analysis
Production security scanner
RAG-powered project memory
MCP tool system
```

## 7. Behavior Must Remain Stable

Do not change the backend API contract.

Must still work:

```text
GET /api/health
POST /api/review-tasks with repoUrl + prNumber
POST /api/review-tasks with repoUrl + prNumber + diffText
GET /api/review-tasks
GET /api/review-tasks/{id}
oversized diff validation at 20000 characters
whitespace-only diff omitted from payload
backend unavailable state
task list selection
detail loading/error/empty states
provider labels for MOCK and MIMO
issueSummary rendering
fallback summary calculation
```

## 8. Testing Requirements

Run:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

Expected:

```text
all pass
```

Update tests only to match intentional UI/copy changes. Do not weaken behavior assertions.

Recommended test coverage updates:

```text
App shell renders product navigation/status
Create form still validates required fields and diff size
Review detail still renders summary, severity breakdown, provider labels, and issue cards
Limitations remain available in secondary UI
```

## 9. Runtime Smoke Requirements

If practical, run backend and frontend:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Smoke:

```text
page loads
backend connected status appears
create metadata-only review works
create diff-grounded review works
review detail displays summary and findings
oversized diff is blocked
mobile viewport is readable
no browser console errors
```

## 10. Handoff Requirements

Create:

```text
stage 1.5/handoff/round-13/01-cursor-product-ui-restyle-handoff.md
```

The handoff must include:

```text
summary of UI changes
files changed
dependency changes, if any
test commands and exact results
runtime smoke status
known limitations preserved
confirmation no Stage 2 features were added
screenshots or browser notes if available
issues for Codex to validate
```

Do not include secrets.

