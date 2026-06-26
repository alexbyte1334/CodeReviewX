# Round 13 Start: Product UI Restyle

## 1. Round Metadata

```text
Project: CodeReviewX
Round: Round 13
Theme: Product UI Restyle — macOS-like polished delivery interface
Task Type: Stage 1.5 productization
Previous Round:
  Round 12: Final Hardening + Demo Readiness
Previous Final Verdict:
  QODER_ROUND_12_APPROVED_FINAL_MVP_DELIVERY
Current Delivery Phase:
  Stage 1.5 Productization & Live Provider Enablement
First Task To Generate:
  stage 1.5/tasks/round-13/01-cursor-product-ui-restyle.md
```

## 2. Current Project Status

Stage 1 is complete.

CodeReviewX is currently a locally runnable:

```text
Manual Diff-Grounded AI Code Review Agent MVP
```

Validated Stage 1 capabilities:

```text
Backend tests: 84 passed
Frontend typecheck: pass
Frontend build: pass
Frontend tests: 38 passed
Backend runtime smoke: pass
Frontend runtime/browser smoke: pass
Mock provider default: works
Xiaomi MiMo provider path: implemented and safely fallback-capable
Live MiMo verification: not executed due to missing local MIMO_API_KEY
```

Current frontend state:

```text
React + Vite
Single app shell in frontend/src/App.tsx
Primary stylesheet in frontend/src/styles/app.css
Create review form
Review history list
Review detail panel
Review summary
Issue cards
Provider labels
Responsive basics
```

The frontend is functional and demo-ready, but still visually reads as a simple MVP page. Round 13 upgrades product feel without changing the core product contract.

## 3. Round 13 Purpose

Round 13 moves CodeReviewX from:

```text
functional demo interface
```

to:

```text
polished product interface suitable for credible external demo
```

The goal is productization, not Stage 2 feature expansion.

## 4. Product Experience Target

Target visual direction:

```text
modern macOS-like desktop app
liquid glass influence
translucent layered panels
soft blur
high-quality gray/white surface system
clear hierarchy
generous spacing
subtle motion
professional SaaS product feel
low-noise technical dashboard
```

Desired app structure:

```text
App Shell
  Top product bar / window chrome
  Left navigation or command sidebar
  Main review workspace
  Review creation panel
  Review history panel
  Result/inspector panel
  Summary visualizations
  Provider/status surface
```

The first viewport should immediately communicate:

```text
CodeReviewX is a real AI code review product interface.
The user can create a review.
The user can inspect review results.
Provider status is visible and honest.
```

## 5. Required Product Copy Boundary

The main UI should no longer feel dominated by MVP explanations.

Allowed:

```text
Concise product positioning
Provider status
Honest limitations placed in secondary/about/footer area
Clear input labels
Clear review result labels
```

Weakly present or move out of primary workflow:

```text
large MVP limitation panel
long tutorial copy
repeated local-demo explanations
obvious demo wording in primary panels
```

Forbidden overclaims:

```text
automatic GitHub PR fetching
GitHub App integration
private repository access
repository clone
full repository analysis
production-grade review
PR comment write-back
RAG
MCP
Function Calling
Memory
auth/team model
```

Mock provider must not be disguised as real MiMo output.

## 6. Round 13 Scope

Allowed:

```text
Refactor frontend layout and visual hierarchy
Update frontend copy for a more polished product feel
Add CSS-based glass/blur/layering/motion
Add lightweight review summary visualization
Add severity distribution bars
Add risk/status visual treatments
Improve provider status placement
Improve mobile responsiveness
Add or update frontend tests for stable UI behavior
Update frontend README only if run/UX wording becomes stale
```

Allowed files:

```text
frontend/src/App.tsx
frontend/src/styles/app.css
frontend/src/components/*.tsx
frontend/src/utils/*.ts
frontend/src/test/*.tsx
frontend/src/test/*.ts
frontend/README.md
```

Backend files should not be changed unless a small test or contract issue is discovered during validation.

## 7. Explicit Non-Goals

Do not implement:

```text
backend API contract changes
database schema changes
GitHub ingestion
GitHub OAuth
GitHub App installation
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
large chart library
production deployment
CI/CD pipeline
```

Do not add a large UI framework. Keep dependencies minimal. Prefer CSS and existing React components.

## 8. Round 13 Acceptance Criteria

Required validation:

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

Backend regression check:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Expected:

```text
backend tests still pass
```

Runtime smoke:

```text
backend starts in mock mode
frontend starts
page loads
backend status appears
create review works
review history updates
review detail displays summary and issue cards
oversized diff guard still works
mobile viewport does not collapse or overlap
no browser console errors
```

Product-quality acceptance:

```text
main UI looks productized, not like a raw demo
limitations are honest but no longer dominate the main workflow
Mock/MiMo labels are accurate
results remain readable
animations are subtle and do not block usability
text does not overlap or overflow on desktop/mobile
```

## 9. Required Handoff Sequence

Round 13 proceeds as:

```text
Cursor implements Round 13
  -> writes handoff
Codex validates Round 13
  -> writes validation handoff
Qoder independently reviews Round 13
  -> writes final review handoff
Architect decides closure
```

Expected handoff paths:

```text
stage 1.5/handoff/round-13/01-cursor-product-ui-restyle-handoff.md
stage 1.5/handoff/round-13/02-codex-product-ui-restyle-validation-handoff.md
stage 1.5/handoff/round-13/03-qoder-product-ui-restyle-independent-review-handoff.md
```

