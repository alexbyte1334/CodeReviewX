# tasks/round-04/01-cursor-frontend-reviewtask-mock-ui.md

# Cursor Task: Round 04 Frontend ReviewTask Mock UI v1

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 04
- Task ID: `01-cursor-frontend-reviewtask-mock-ui`
- Task Owner: Cursor
- Task Type: Frontend Implementation
- Target Directory: `frontend/`
- Expected Handoff:
  - `handoff/round-04/01-cursor-frontend-reviewtask-mock-ui-handoff.md`

---

## 2. Background

Round 03 has been accepted.

The backend mock API for ReviewTask has already been implemented and independently validated.

Available backend APIs:

```http
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

Round 04 starts frontend implementation.

The goal of this task is to build a minimal but complete frontend UI that consumes the existing backend mock API and demonstrates the ReviewTask lifecycle from the user’s perspective.

This round must not move into persistence, ai-service integration, GitHub integration, Semgrep execution, or real LLM review.

---

## 3. Task Objective

Implement `frontend` ReviewTask Mock UI v1.

The UI must allow a user to:

1. Open the frontend page.
2. View basic backend connectivity status.
3. Enter a GitHub repository URL.
4. Enter a PR number.
5. Create a mock review task.
6. See the created review task detail.
7. See the review task list.
8. Select a task from the list and view its detail.
9. See empty state when no tasks exist.
10. See loading state during API calls.
11. See error state for API or validation failures.

The frontend should consume the backend mock API created in Round 03.

---

## 4. Recommended Tech Stack

If `frontend/` is not yet initialized or only contains placeholder files, create a minimal frontend app using:

```text
React + TypeScript + Vite
```

Recommended package manager:

```text
npm
```

Do not use:

```text
Next.js
Redux
MobX
SSR
Micro frontend
Complex routing
Complex design system
Complex auth system
```

The frontend should remain lightweight and suitable for MVP demonstration.

---

## 5. Required API Base URL Configuration

The frontend must support configurable backend API base URL.

Use:

```text
VITE_API_BASE_URL=http://localhost:8080
```

If the environment variable is not set, default to:

```text
http://localhost:8080
```

Frontend must call:

```http
GET  ${VITE_API_BASE_URL}/api/health
POST ${VITE_API_BASE_URL}/api/review-tasks
GET  ${VITE_API_BASE_URL}/api/review-tasks
GET  ${VITE_API_BASE_URL}/api/review-tasks/{id}
```

Do not hardcode a fixed task id.

Do not depend on fixed timestamps.

Do not depend on a fixed validation message language.

---

## 6. Backend API Contract

### 6.1 Health API

Request:

```http
GET /api/health
```

Expected response:

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "status": "UP",
    "service": "backend-java"
  }
}
```

Frontend requirement:

- Display a small backend status indicator.
- If health check fails, show a user-visible warning.
- The UI should still render even if backend health check fails.

---

### 6.2 Create Review Task API

Request:

```http
POST /api/review-tasks
Content-Type: application/json
```

Request body:

```json
{
  "repoUrl": "https://github.com/example/repo",
  "prNumber": 123
}
```

Expected success response:

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": 1,
    "repoUrl": "https://github.com/example/repo",
    "prNumber": 123,
    "status": "SUCCESS",
    "summary": "Mock review completed for PR #123.",
    "riskLevel": "LOW",
    "errorMessage": null,
    "createdAt": "2026-06-23T06:22:07.527724",
    "updatedAt": "2026-06-23T06:22:07.528436",
    "issues": []
  }
}
```

Frontend requirements:

- Provide form fields:
  - `repoUrl`
  - `prNumber`
- Add basic client-side validation:
  - `repoUrl` is required.
  - `prNumber` is required.
  - `prNumber` must be a positive number.
- On submit:
  - Show submit loading state.
  - Call backend API.
  - If success:
    - Display created task detail.
    - Refresh task list or insert created task into task list.
  - If failure:
    - Display user-visible error message.

---

### 6.3 List Review Tasks API

Request:

```http
GET /api/review-tasks
```

Expected success response:

```json
{
  "success": true,
  "message": "OK",
  "data": [
    {
      "id": 1,
      "repoUrl": "https://github.com/example/repo",
      "prNumber": 123,
      "status": "SUCCESS",
      "summary": "Mock review completed for PR #123.",
      "riskLevel": "LOW",
      "errorMessage": null,
      "createdAt": "2026-06-23T06:22:07.527724",
      "updatedAt": "2026-06-23T06:22:07.528436",
      "issues": []
    }
  ]
}
```

Frontend requirements:

- Load task list when the page opens.
- Show loading state while loading.
- Show empty state when no tasks exist.
- Show task cards or a simple table when tasks exist.
- Each task item must show at least:
  - `id`
  - `repoUrl`
  - `prNumber`
  - `status`
  - `riskLevel`
  - `summary`
  - `createdAt`
- User must be able to select a task to view detail.

---

### 6.4 Get Review Task Detail API

Request:

```http
GET /api/review-tasks/{id}
```

Expected success response:

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": 1,
    "repoUrl": "https://github.com/example/repo",
    "prNumber": 123,
    "status": "SUCCESS",
    "summary": "Mock review completed for PR #123.",
    "riskLevel": "LOW",
    "errorMessage": null,
    "createdAt": "2026-06-23T06:22:07.527724",
    "updatedAt": "2026-06-23T06:22:07.528436",
    "issues": []
  }
}
```

Expected not found response:

```json
{
  "success": false,
  "message": "Review task not found",
  "data": null
}
```

Frontend requirements:

- Display complete task detail.
- If `issues` is an empty array, display:

```text
No issues found in mock review.
```

- If `success=false`, display the backend `message`.
- If `data=null`, do not crash.

---

## 7. Recommended Frontend Structure

If creating a new React + TypeScript + Vite app, use the following structure or a similarly clean minimal structure:

```text
frontend/
  package.json
  index.html
  vite.config.ts
  tsconfig.json
  tsconfig.node.json
  src/
    main.tsx
    App.tsx
    api/
      reviewTaskApi.ts
    types/
      apiResponse.ts
      reviewTask.ts
    components/
      ReviewTaskCreateForm.tsx
      ReviewTaskList.tsx
      ReviewTaskDetail.tsx
      LoadingState.tsx
      ErrorMessage.tsx
    styles/
      app.css
  README.md
```

Requirements:

1. API client logic must not be mixed deeply into UI components.
2. TypeScript types must be explicit.
3. Avoid putting all business logic into one large component.
4. README must explain install, run, build, test/typecheck if available.
5. README must explain `VITE_API_BASE_URL`.
6. README must state that backend task data is in-memory mock data and may disappear after backend restart.

---

## 8. Required Types

Create or equivalent:

```ts
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T | null;
}
```

Create or equivalent:

```ts
export type ReviewTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export interface ReviewTask {
  id: number;
  repoUrl: string;
  prNumber: number;
  status: ReviewTaskStatus;
  summary: string | null;
  riskLevel: RiskLevel | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
  issues: unknown[];
}

export interface CreateReviewTaskRequest {
  repoUrl: string;
  prNumber: number;
}
```

Do not model real code review issues yet.

`issues` may remain `unknown[]` or another clearly minimal placeholder type because backend currently returns an empty array in mock v1.

---

## 9. API Client Requirements

Implement an API client, for example:

```text
src/api/reviewTaskApi.ts
```

It should expose functions equivalent to:

```ts
getHealth()
createReviewTask(payload)
listReviewTasks()
getReviewTask(id)
```

Behavior requirements:

1. Read base URL from `import.meta.env.VITE_API_BASE_URL`.
2. Default to `http://localhost:8080`.
3. Parse `ApiResponse<T>`.
4. Treat `success=false` as a handled API failure.
5. Handle network errors gracefully.
6. Do not swallow backend error messages.
7. Do not rely on fixed task id.
8. Do not rely on fixed validation message text.

---

## 10. UI Requirements

### 10.1 App Shell

The page should show:

```text
CodeReviewX
ReviewTask Mock UI
Backend status
Create form
Task list
Task detail
```

No complex routing is required.

A single-page layout is sufficient.

---

### 10.2 Create Form

Required fields:

```text
Repository URL
PR Number
Submit button
```

Required states:

```text
idle
submitting
success
error
```

Basic client-side validation is recommended.

Do not over-engineer form state.

---

### 10.3 Task List

Required states:

```text
loading
empty
loaded
error
```

Each task item should be selectable.

On select:

```text
GET /api/review-tasks/{id}
```

Then display detail.

---

### 10.4 Task Detail

Display:

```text
id
repoUrl
prNumber
status
summary
riskLevel
errorMessage
createdAt
updatedAt
issues
```

If no task is selected, show a neutral placeholder:

```text
Select a review task to view details.
```

If issues are empty:

```text
No issues found in mock review.
```

---

### 10.5 Error Handling

Show user-visible error messages for:

1. Backend health check failure.
2. List task failure.
3. Create task validation failure.
4. Create task network failure.
5. Detail not found.
6. Detail network failure.

Do not crash on:

```text
success=false
data=null
empty list
missing optional summary
missing optional errorMessage
```

---

## 11. Testing Requirements

Add minimal frontend test coverage if practical.

Recommended stack:

```text
Vitest
React Testing Library
```

Minimum useful tests:

1. create form renders fields.
2. task list renders empty state.
3. task detail renders selected task.
4. API client handles `success=false`.

If test setup would significantly expand scope, prioritize:

```text
npm run build
npm run typecheck
```

At minimum, provide scripts for:

```json
{
  "scripts": {
    "dev": "...",
    "build": "...",
    "typecheck": "..."
  }
}
```

If tests are added:

```json
{
  "scripts": {
    "test": "..."
  }
}
```

---

## 12. CORS / Proxy Guidance

Prefer solving local development CORS through Vite dev server proxy.

Example acceptable approach:

```ts
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

However, this must not break `VITE_API_BASE_URL` support.

If browser calls are blocked by CORS and proxy is insufficient, a minimal backend CORS configuration is allowed only if necessary.

Backend CORS changes, if made, must be minimal:

Allowed origins:

```text
http://localhost:5173
http://127.0.0.1:5173
```

Do not add Spring Security.

Do not add complex security configuration.

If any backend file is touched, explain exactly why in the handoff.

---

## 13. Strict Scope Boundaries

You must not implement or introduce:

```text
Database
MyBatis-Plus
MySQL Driver
JPA
Hibernate
Entity
Mapper
Repository
Database schema
Migration
ai-service client
GitHub API client
Semgrep execution
LLM call
Real code review
Redis
Message queue
Scheduler
Spring Security
Swagger
OpenAPI
Production-grade design system
Complex auth system
Redux
MobX
Next.js
SSR
Round 05 work
```

Do not modify backend ReviewTask business behavior.

Do not implement persistence.

Do not add fake localStorage persistence unless clearly limited to UI state and documented. Prefer not to use localStorage in this round.

---

## 14. README Requirements

Update or create:

```text
frontend/README.md
```

The README must include:

1. Tech stack.
2. Install command.
3. Dev command.
4. Build command.
5. Typecheck command.
6. Test command if tests exist.
7. Required backend:
   - `backend-java` running on port `8080`.
8. API base URL configuration:
   - `VITE_API_BASE_URL=http://localhost:8080`
9. Statement that ReviewTask data is backend in-memory mock data.
10. Statement that backend restart may clear task data.
11. Basic user flow:
   - open page
   - create task
   - view list
   - view detail

If root README already contains project status, update it only minimally.

---

## 15. Validation Checklist for Cursor

Before handoff, run as many as applicable:

```bash
cd frontend
npm install
npm run typecheck
npm run build
npm test
```

If backend is available locally, also perform basic manual runtime check:

```bash
cd backend-java
mvn test
mvn spring-boot:run
```

Then run frontend:

```bash
cd frontend
npm run dev
```

Manually verify:

1. Page opens.
2. Backend status is visible.
3. Create task succeeds with valid input.
4. Created task detail is displayed.
5. Task list displays created task.
6. Selecting task displays detail.
7. Invalid input shows user-visible error.
8. Backend validation failure shows user-visible error.
9. Empty list state is reasonable when backend has no tasks.

If some checks cannot be performed, document why in the handoff.

---

## 16. Expected Deliverables

Cursor should deliver:

1. Frontend implementation under `frontend/`.
2. API client.
3. TypeScript types.
4. UI components.
5. Basic styling.
6. Build/typecheck scripts.
7. Optional but preferred tests.
8. Updated `frontend/README.md`.
9. Optional minimal root README update if needed.
10. Handoff document:

```text
handoff/round-04/01-cursor-frontend-reviewtask-mock-ui-handoff.md
```

---

## 17. Required Handoff Format

Create:

```text
handoff/round-04/01-cursor-frontend-reviewtask-mock-ui-handoff.md
```

The handoff must include:

```markdown
# Round 04 Cursor Handoff: Frontend ReviewTask Mock UI v1

## 1. Summary

## 2. Files Created

## 3. Files Modified

## 4. Frontend Stack

## 5. Implemented User Flow

## 6. API Integration Details

## 7. API Base URL Configuration

## 8. Components Implemented

## 9. Loading / Error / Empty States

## 10. Tests Added

## 11. Commands Run

Include exact command output summary for:

- npm install
- npm run typecheck
- npm run build
- npm test, if available

## 12. Manual Verification

Include whether the following passed:

- page opens
- backend health visible
- create task works
- list task works
- detail task works
- invalid input shows error
- backend validation error shows error

## 13. Backend Changes

State either:

- No backend changes made.

or:

- Backend changes made only for minimal CORS support.

If backend changes were made, list every changed file and justify each change.

## 14. Scope Audit

Confirm all of the following:

- No database added.
- No MyBatis-Plus added.
- No MySQL driver added.
- No JPA/Hibernate added.
- No Entity/Mapper/Repository added.
- No ai-service call added.
- No GitHub API call added.
- No Semgrep execution added.
- No LLM call added.
- No real code review implemented.
- No Redis/MQ/Scheduler added.
- No Spring Security added.
- No Swagger/OpenAPI added.
- No complex state management added.
- No Next.js/SSR added.
- Round 05 not started.

## 15. Known Limitations

## 16. Suggested Next Step

Recommend Codex validation task:

tasks/round-04/02-codex-frontend-reviewtask-mock-ui-validation.md
```

---

## 18. Acceptance Criteria for This Task

Cursor implementation is acceptable only if:

1. `frontend/` contains a runnable frontend app.
2. Frontend can create ReviewTask through backend API.
3. Frontend can list ReviewTasks through backend API.
4. Frontend can fetch ReviewTask detail through backend API.
5. Frontend handles `ApiResponse<T>`.
6. Frontend handles `success=false`.
7. Frontend handles `data=null`.
8. Frontend shows loading, error, and empty states.
9. Frontend supports configurable backend API base URL.
10. Frontend does not depend on fixed id or timestamp.
11. Frontend does not depend on validation message language.
12. Frontend README is updated.
13. Build passes.
14. Typecheck passes if configured.
15. Tests pass if configured.
16. No forbidden scope is introduced.
17. Handoff is created.

---

## 19. Final Instruction

Implement the smallest clean frontend that demonstrates the ReviewTask mock workflow end to end.

Prioritize:

```text
clear API integration
clean TypeScript types
simple React components
good error handling
reliable build/typecheck
strict scope control
```

Do not over-engineer.

Do not implement persistence.

Do not call ai-service.

Do not call GitHub.

Do not execute Semgrep.

Do not call LLM.

Do not start Round 05.