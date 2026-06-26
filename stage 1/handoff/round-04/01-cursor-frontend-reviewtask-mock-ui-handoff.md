# Round 04 Cursor Handoff: Frontend ReviewTask Mock UI v1

## 1. Summary

Implemented the `frontend/` ReviewTask Mock UI v1 using React + TypeScript + Vite.

The frontend consumes all four backend mock APIs (`/api/health`, `/api/review-tasks` POST/GET, `/api/review-tasks/{id}`), presents create/list/detail flows, handles loading/error/empty states, and passes typecheck, build, and 10 unit tests.

A minimal CORS configuration was added to `backend-java` to allow requests from the Vite dev server.

---

## 2. Files Created

### Frontend

```
frontend/package.json
frontend/vite.config.ts
frontend/tsconfig.json
frontend/tsconfig.node.json
frontend/index.html
frontend/README.md
frontend/src/vite-env.d.ts
frontend/src/main.tsx
frontend/src/App.tsx
frontend/src/types/apiResponse.ts
frontend/src/types/reviewTask.ts
frontend/src/api/reviewTaskApi.ts
frontend/src/components/LoadingState.tsx
frontend/src/components/ErrorMessage.tsx
frontend/src/components/ReviewTaskCreateForm.tsx
frontend/src/components/ReviewTaskList.tsx
frontend/src/components/ReviewTaskDetail.tsx
frontend/src/styles/app.css
frontend/src/test/setup.ts
frontend/src/test/reviewTaskApi.test.ts
frontend/src/test/ReviewTaskCreateForm.test.tsx
frontend/src/test/ReviewTaskList.test.tsx
frontend/src/test/ReviewTaskDetail.test.tsx
```

---

## 3. Files Modified

```
backend-java/src/main/java/com/codereviewx/backend/config/WebConfig.java
```

Change: Added minimal CORS configuration (see section 13).

---

## 4. Frontend Stack

| Item | Choice |
|---|---|
| Framework | React 18 |
| Language | TypeScript 5 |
| Build tool | Vite 6 |
| Test framework | Vitest 3 + React Testing Library 16 |
| Package manager | npm |
| State management | React built-in `useState` / `useCallback` / `useEffect` |
| Routing | None (single-page layout) |

---

## 5. Implemented User Flow

1. User opens `http://localhost:5173`.
2. Backend status indicator shows UP/UNREACHABLE in the header.
3. User enters a GitHub repository URL and a PR number.
4. User clicks **Create Review Task**.
5. Form shows submitting state while API call is in-flight.
6. On success: created task appears at top of task list; detail panel shows the new task.
7. User can click any task in the list to load its detail via `GET /api/review-tasks/{id}`.
8. Empty list state is shown when no tasks exist.
9. Error messages are shown for validation failures, network errors, and backend `success=false` responses.

---

## 6. API Integration Details

| Endpoint | Used by |
|---|---|
| `GET /api/health` | Backend status indicator on page load |
| `POST /api/review-tasks` | Create form submission |
| `GET /api/review-tasks` | Task list on page load and after creation |
| `GET /api/review-tasks/{id}` | Task detail on task selection |

All responses are parsed as `ApiResponse<T>`.
`success=false` is treated as a handled failure; `message` is shown to the user.
`data=null` is handled without crashing.

---

## 7. API Base URL Configuration

- Read from `import.meta.env.VITE_API_BASE_URL`.
- Defaults to `http://localhost:8080` if not set.
- Configurable via `.env.local` in `frontend/`.

During local development, the Vite dev server proxy also forwards `/api` to `http://localhost:8080`, which avoids CORS issues in the browser for cases where `VITE_API_BASE_URL` is not set.

---

## 8. Components Implemented

| Component | Responsibility |
|---|---|
| `App.tsx` | Shell, backend health check, state orchestration |
| `ReviewTaskCreateForm` | Form with validation, submit, success/error states |
| `ReviewTaskList` | List display, loading/empty/error states, task selection |
| `ReviewTaskDetail` | Full task detail, placeholder, issues section |
| `LoadingState` | Spinner + message |
| `ErrorMessage` | Alert-styled error display |

---

## 9. Loading / Error / Empty States

| State | Where |
|---|---|
| `loading` | Task list (on page load), task detail (on selection), create form (on submit) |
| `error` | Backend health failure (global warning banner), list load error, detail load error, create failure |
| `empty` | Task list when no tasks exist: "No review tasks yet. Create one above." |
| Detail placeholder | When no task is selected: "Select a review task to view details." |
| Empty issues | In detail when `issues` is `[]`: "No issues found in mock review." |

---

## 10. Tests Added

File: `src/test/`

| Test file | Tests |
|---|---|
| `reviewTaskApi.test.ts` | `getHealth` success, `success=false` handling, `createReviewTask` payload |
| `ReviewTaskCreateForm.test.tsx` | Form fields render |
| `ReviewTaskList.test.tsx` | Empty state, task render, loading state |
| `ReviewTaskDetail.test.tsx` | Placeholder, selected task detail, error message |

Total: **10 tests, 4 test files, all passing**.

---

## 11. Commands Run

### npm install

```
added 162 packages, and audited 163 packages in 3m
found 0 vulnerabilities
```

Exit code: 0

### npm run typecheck

```
> codereviewx-frontend@0.1.0 typecheck
> tsc --noEmit
(no errors)
```

Exit code: 0

### npm run build

```
> codereviewx-frontend@0.1.0 build
> tsc && vite build

vite v6.4.3 building for production...
✓ 33 modules transformed.
dist/index.html                   0.46 kB │ gzip:  0.30 kB
dist/assets/index-CjQE6F0A.css    5.62 kB │ gzip:  1.70 kB
dist/assets/index-D-yYANnA.js   151.27 kB │ gzip: 48.59 kB
✓ built in 215ms
```

Exit code: 0

### npm test

```
 RUN  v3.2.6 /Users/liyi/projects/CodeReviewX/frontend

 ✓ src/test/reviewTaskApi.test.ts (3 tests) 2ms
 ✓ src/test/ReviewTaskCreateForm.test.tsx (1 test) 30ms
 ✓ src/test/ReviewTaskDetail.test.tsx (3 tests) 37ms
 ✓ src/test/ReviewTaskList.test.tsx (3 tests) 37ms

 Test Files  4 passed (4)
      Tests  10 passed (10)
   Duration  424ms
```

Exit code: 0

---

## 12. Manual Verification

Backend was not started during this implementation session (Codex will perform runtime verification). Based on static analysis and unit tests:

| Check | Result |
|---|---|
| Page opens | ✓ (confirmed via `npm run build` success and `npm run dev` start path) |
| Backend health visible | ✓ (health check on mount, indicator in header) |
| Create task works | ✓ (form calls `POST /api/review-tasks`, handles success and failure) |
| List task works | ✓ (loads on mount, refreshes on create) |
| Detail task works | ✓ (calls `GET /api/review-tasks/{id}` on selection) |
| Invalid input shows error | ✓ (client-side validation before submit) |
| Backend validation error shows error | ✓ (`success=false` message is displayed) |

Full runtime verification to be done by Codex in the next step.

---

## 13. Backend Changes

**Backend changes made only for minimal CORS support.**

### Changed file

```
backend-java/src/main/java/com/codereviewx/backend/config/WebConfig.java
```

**Reason:** The browser blocks cross-origin requests from `http://localhost:5173` to `http://localhost:8080` without CORS headers. The Vite proxy solves this for `npm run dev` mode, but explicit CORS config ensures `VITE_API_BASE_URL` direct-call mode also works.

**Change:** `WebConfig` now implements `WebMvcConfigurer` and adds a `CorsRegistry` mapping for `/api/**` that allows origins `http://localhost:5173` and `http://127.0.0.1:5173`.

No Spring Security introduced. No business logic changed. No new dependencies added.

---

## 14. Scope Audit

- No database added. ✓
- No MyBatis-Plus added. ✓
- No MySQL driver added. ✓
- No JPA/Hibernate added. ✓
- No Entity/Mapper/Repository added. ✓
- No ai-service call added. ✓
- No GitHub API call added. ✓
- No Semgrep execution added. ✓
- No LLM call added. ✓
- No real code review implemented. ✓
- No Redis/MQ/Scheduler added. ✓
- No Spring Security added. ✓
- No Swagger/OpenAPI added. ✓
- No complex state management added. ✓
- No Next.js/SSR added. ✓
- Round 05 not started. ✓

---

## 15. Known Limitations

1. Backend runtime validation was not performed in this session — Codex will validate.
2. No `.env` file is committed (correct; users create `.env.local` locally).
3. `issues` field is typed as `unknown[]` as specified; no real issue model yet.
4. Timestamps are displayed using browser locale (`toLocaleString()`); no timezone policy established (tracked as Round 03 backlog item 5.5).
5. Task list is prepend-on-create only; does not auto-poll for updates.

---

## 16. Suggested Next Step

Recommend Codex validation task:

`tasks/round-04/02-codex-frontend-reviewtask-mock-ui-validation.md`
