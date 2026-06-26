# Codex Task: Round 13 Product UI Restyle Validation

## 1. Task Metadata

```text
Project: CodeReviewX
Round: Round 13
Agent: Codex
Task: Repository-level validation of product UI restyle
Input Documents:
  stage 1.5/tasks/round-13/00-round-13-start.md
  stage 1.5/tasks/round-13/01-cursor-product-ui-restyle.md
  stage 1.5/handoff/round-13/01-cursor-product-ui-restyle-handoff.md
Expected Handoff:
  stage 1.5/handoff/round-13/02-codex-product-ui-restyle-validation-handoff.md
```

## 2. Primary Objective

Independently validate Cursor's Round 13 UI restyle.

Decision output:

```text
CODEX_ROUND_13_VALIDATED_READY_FOR_QODER
```

or:

```text
CODEX_ROUND_13_BLOCKED
```

with exact blockers and minimum required fixes.

## 3. Validation Scope

Validate:

```text
frontend typecheck/build/tests
backend tests unchanged/pass
runtime frontend behavior
runtime backend smoke
browser UI quality
responsive behavior
copy accuracy
provider label honesty
no Stage 2 scope creep
no secret exposure
```

Allowed fixes:

```text
minor frontend test fix
minor CSS/layout fix
minor copy correction
minor accessibility fix
minor README correction
```

Forbidden:

```text
major redesign beyond Cursor's scope
backend API redesign
GitHub ingestion
RAG/MCP/Memory/Function Calling
auth/team model
new chart/UI framework
production deployment
```

## 4. Required Document Review

Read:

```text
stage 1.5/start.md
stage 1.5/tasks/round-13/00-round-13-start.md
stage 1.5/tasks/round-13/01-cursor-product-ui-restyle.md
stage 1.5/handoff/round-13/01-cursor-product-ui-restyle-handoff.md
docs/AGENT_RULES.md
README.md
frontend/README.md
```

Confirm Cursor did not overclaim:

```text
automatic GitHub PR fetching
GitHub App integration
private repository access
repository clone
full repository analysis
PR comment write-back
RAG
MCP
Function Calling
Memory
production auth/team model
```

## 5. Required Code Review

Inspect at minimum:

```text
frontend/src/App.tsx
frontend/src/styles/app.css
frontend/src/components/ReviewTaskCreateForm.tsx
frontend/src/components/ReviewTaskList.tsx
frontend/src/components/ReviewTaskDetail.tsx
frontend/src/types/reviewTask.ts
frontend/src/api/reviewTaskApi.ts
frontend/src/test/*
frontend/package.json
```

Check:

```text
API request payload is unchanged
diffText size limit remains 20000
whitespace-only diff is still omitted
public UI does not expose raw diff after submit
provider labels remain accurate
Mock provider is not disguised as MiMo
layout handles long repo URLs and long file paths
buttons/text do not overflow
mobile layout is usable
animations are non-blocking
```

## 6. Required Test Commands

Run:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

Run backend regression:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Record exact results.

If Maven needs the known local Java path, use:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17
```

If environment prevents a command from running, document the exact error and whether it is a product blocker.

## 7. Runtime Smoke

Start backend in mock mode:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

Start frontend:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Verify:

```text
GET /api/health returns success
frontend loads
backend connected indicator is visible
create metadata-only review works
create diff-grounded review works
list/detail flow works
oversized diff guard blocks 20001 chars
desktop viewport is polished
mobile viewport is readable
no console errors
```

If browser tooling is unavailable, record the fallback method.

## 8. Product Quality Review

Judge whether the UI now feels:

```text
polished
credible
product-like
clear
honest about provider state
not dominated by MVP limitations
```

Do not reject only because taste differs. Reject only for concrete product, usability, responsiveness, accessibility, overclaiming, or regression issues.

## 9. Handoff Requirements

Create:

```text
stage 1.5/handoff/round-13/02-codex-product-ui-restyle-validation-handoff.md
```

Include:

```text
validation verdict
documents reviewed
files inspected
test commands and exact results
runtime smoke evidence
browser/responsive observations
scope compliance result
secret exposure check
blockers if any
recommended decision for architect
```

