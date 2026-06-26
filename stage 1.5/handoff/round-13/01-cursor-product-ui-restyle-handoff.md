# Round 13 Handoff: Product UI Restyle

## Agent

```text
Agent: Cursor
Task: stage 1.5/tasks/round-13/01-cursor-product-ui-restyle.md
Date: 2026-06-26
```

## Summary of UI Changes

CodeReviewX frontend was upgraded from a single-page MVP layout to a macOS 26–inspired product shell while preserving all existing behavior and API contracts.

### App shell

```text
- Left sidebar: CodeReviewX branding, Review Agent / Review History nav, Status widget, collapsible About & limits
- Window chrome: traffic-light decoration, Review Workspace title, backend chip, light/dark theme toggle
- Single-column workspace stack (max-width 840px) with collapsible panels instead of a crowded two-column grid
- Workspace toolbar card: title/subtitle, quick-nav pills (Run Review / History / Findings), backend status chip
```

### macOS 26 visual system

```text
- Vibrancy / frosted-glass surfaces with backdrop blur and glossy rounded borders
- Stage Manager–style floating card shadows and Apple gray/white + saturated system accent colors
- Apple easing curves (--ease-apple, --ease-apple-out) for expand/collapse and micro-interactions
- Manual light/dark mode via html[data-theme] + localStorage (codereviewx-theme); flash-free boot via applyStoredTheme()
```

### Collapsible workspace (default collapsed)

```text
- Run Review, Review History, and Findings panels start collapsed; users expand on demand
- Sidebar nav and toolbar quick pills expand the corresponding section
- Creating or selecting a task auto-expands History + Findings
- Inside Findings: nested collapsible Review Summary and Issue Details; each issue card collapses to title until expanded
- CollapsiblePanel uses grid 0fr→1fr height animation; children stay mounted for smooth transitions
```

### Status widget

```text
- Sidebar desktop-widget style card with semi-circular SVG arc gauges (Backend + Reviews)
- Breathing status dot animation for live connection state
- Replaces earlier inline provider-status-card / sidebar-limits-toggle markup
```

### Existing feature polish (unchanged behavior)

```text
- Summary metric cards (Findings, High, Medium, Low)
- CSS severity distribution bars with animated fill widths
- Risk level badges and provider/status chips in list and detail views
- Create form copy and panel headers (Run Review, Review History, Findings)
- Limitations moved to secondary collapsible About & limits panel
```

### Code cleanup (this pass)

```text
- Extracted shared types/constants to frontend/src/types/ui.ts (BackendStatus, PanelId, PRODUCT_LIMITS)
- Centralized theme boot in useColorTheme.applyStoredTheme(); simplified main.tsx
- Removed dead CSS: provider-status-card, sidebar-limits-*, card-header, card--detail, card-intro,
  window-status-text, unused @keyframes pulse/slideDown, redundant tokens (--radius-xs, --float-shadow-lg,
  --transition-spring, --color-text* aliases)
- Consolidated panel-intro styling; replaced transition-spring with --ease-apple throughout
- Removed duplicate card-intro class from ReviewTaskCreateForm (uses panel-intro only)
```

Product copy boundary preserved:

```text
- Sidebar tagline retains: Manual Diff-Grounded AI Code Review Agent MVP
- Limitations in secondary collapsible panel (About & limits)
- No forbidden overclaim wording added
- Mock/MiMo provider labels unchanged and accurate
```

## Files Changed

```text
frontend/src/App.tsx
frontend/src/main.tsx
frontend/src/styles/app.css
frontend/src/types/ui.ts                                    (new)
frontend/src/hooks/useColorTheme.ts                         (new)
frontend/src/components/CollapsiblePanel.tsx                (new)
frontend/src/components/StatusWidget.tsx                    (new)
frontend/src/components/WorkspaceToolbar.tsx                (new)
frontend/src/components/ThemeToggle.tsx                     (new)
frontend/src/components/ReviewTaskCreateForm.tsx
frontend/src/components/ReviewTaskList.tsx
frontend/src/components/ReviewTaskDetail.tsx
frontend/src/test/App.test.tsx                              (new)
frontend/src/test/setup.ts
frontend/src/test/ReviewTaskCreateForm.test.tsx
frontend/src/test/ReviewTaskList.test.tsx
frontend/src/test/ReviewTaskDetail.test.tsx
frontend/README.md
stage 1.5/handoff/round-13/01-cursor-product-ui-restyle-handoff.md
```

## Dependency Changes

```text
None. No new npm packages added.
```

## Test Commands and Results

```bash
cd frontend
npm run typecheck   # pass
npm run build       # pass
npm test -- --run   # pass — 45 tests in 6 files
```

Test coverage updates:

```text
- App.test.tsx (6 tests): product navigation, StatusWidget, collapsible About panel,
  collapsed workspace defaults, dark mode toggle, backend unavailable flow
- ReviewTaskCreateForm tests for Run Review button/loading copy
- ReviewTaskList test for new empty-state copy
- ReviewTaskDetail tests for summary metrics, severity bars, nested collapsible sections
- setup.ts: matchMedia mock for theme toggle tests
```

## Runtime Smoke Status

Smoke performed with backend and frontend running locally:

```bash
# Backend
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run

# Frontend
cd frontend
npm run dev -- --host 127.0.0.1
```

Results:

```text
✓ Backend health: GET /api/health → success, status UP
✓ Frontend page loads at http://127.0.0.1:5173/
✓ Create metadata-only review via API: POST /api/review-tasks → success
✓ Backend connected status surfaces in StatusWidget and workspace toolbar (unit tests + live health check)
```

Not manually verified in browser this session (Codex should confirm):

```text
- Full create-review flow through UI form with collapsed→expanded panels
- Diff-grounded review through UI
- Review detail summary + severity bars + nested issue cards in browser
- Dark mode toggle persistence across page reload
- Collapsible panel expand/collapse animations feel smooth (no layout jump)
- Oversized diff client-side block in browser
- Mobile viewport layout in browser
- Browser console errors
```

## Known Limitations Preserved

Honest limitations remain available in the sidebar **About & limits** panel:

```text
- Manual diff input only
- Mock provider is default
- MiMo requires local environment configuration
- No automatic GitHub PR fetching yet
```

Behavior unchanged:

```text
- All API endpoints and DTO types unchanged
- Diff max 20,000 characters client validation
- Whitespace-only diff omitted from payload
- Provider labels: MOCK → Mock Provider, MIMO → Xiaomi MiMo
- issueSummary preferred over computed fallback
- Backend unavailable disables submit and shows warning
```

## Stage 2 Features — Not Added

Confirmed no Stage 2 features were implemented:

```text
No GitHub ingestion, OAuth, App install, repo clone, PR write-back,
RAG, MCP, Function Calling, Memory, auth/team model, chart libraries,
UI frameworks, Monaco editor, or backend API changes.
```

## Issues for Codex to Validate

```text
1. Visual polish: macOS 26 glass aesthetic reads as credible product interface on desktop and mobile
2. Primary workflow is not dominated by MVP explanation text; panels default collapsed with clear expand affordances
3. Sidebar nav + toolbar quick pills correctly expand the intended workspace section
4. StatusWidget arc gauges and breathing dot reflect backend up/down/checking states
5. Dark mode toggle works, persists in localStorage, and both themes remain readable
6. CollapsiblePanel animations (workspace + nested Findings sections) are smooth without content flash
7. Severity distribution bars render correctly for mixed severities and zero findings
8. Risk badge and metric cards match backend issueSummary values
9. All 45 frontend tests pass; backend regression (mvn test) still green
10. Browser smoke: create metadata-only review, diff-grounded review, oversized diff block, theme reload, console clean
11. Mock provider output clearly labeled; no overclaim wording anywhere in UI
```

## Screenshots / Browser Notes

```text
Screenshots not captured in this session.
Browser notes: Vite dev server confirmed ready at http://127.0.0.1:5173/.
Recommend Codex capture desktop + mobile viewport screenshots (light and dark) during validation.
```
