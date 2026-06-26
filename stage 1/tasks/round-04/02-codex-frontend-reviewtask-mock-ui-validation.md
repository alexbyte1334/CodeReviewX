# tasks/round-04/02-codex-frontend-reviewtask-mock-ui-validation.md

# Codex Task: Round 04 Frontend ReviewTask Mock UI Validation

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 04
- Task ID: `02-codex-frontend-reviewtask-mock-ui-validation`
- Task Owner: Codex
- Task Type: Independent Validation / Minimal Fix
- Target Areas:
  - `frontend/`
  - `backend-java/`
  - `handoff/round-04/01-cursor-frontend-reviewtask-mock-ui-handoff.md`
- Expected Output:
  - `handoff/round-04/02-codex-frontend-reviewtask-mock-ui-validation-handoff.md`

---

## 2. Background

Round 03 backend mock API has been accepted.

Round 04 Cursor task has implemented a frontend ReviewTask Mock UI v1 using React + TypeScript + Vite.

Cursor handoff claims:

1. `frontend/` was initialized as a React + TypeScript + Vite app.
2. Frontend consumes:
   - `GET /api/health`
   - `POST /api/review-tasks`
   - `GET /api/review-tasks`
   - `GET /api/review-tasks/{id}`
3. Frontend supports create/list/detail ReviewTask flows.
4. Frontend handles loading/error/empty states.
5. Frontend parses `ApiResponse<T>`.
6. Frontend handles `success=false` and `data=null`.
7. Frontend build/typecheck/tests pass.
8. Backend was minimally modified for CORS in:
   - `backend-java/src/main/java/com/codereviewx/backend/config/WebConfig.java`

Cursor did not start backend runtime during implementation. Therefore, Codex must perform independent runtime validation.

---

## 3. Validation Objective

Your objective is to independently validate the Cursor implementation.

You must verify:

1. Frontend dependencies install successfully.
2. Frontend typecheck passes.
3. Frontend build passes.
4. Frontend tests pass.
5. Backend tests still pass.
6. Backend runtime starts successfully.
7. Frontend runtime starts successfully.
8. Frontend can call backend mock APIs.
9. Create/list/detail ReviewTask UI flow works.
10. Backend validation failure is visible in frontend.
11. `success=false` and `data=null` do not crash frontend.
12. CORS / Vite proxy / API base URL behavior is valid.
13. No forbidden scope was introduced.

If you find small issues that block validation and can be fixed with minimal, low-risk changes, you may fix them.

Do not re-implement the feature.

Do not expand scope.

---

## 4. Key Files to Inspect First

Read the Cursor handoff:

```text
handoff/round-04/01-cursor-frontend-reviewtask-mock-ui-handoff.md
```

Then inspect at minimum:

```text
frontend/package.json
frontend/vite.config.ts
frontend/README.md
frontend/src/api/reviewTaskApi.ts
frontend/src/types/apiResponse.ts
frontend/src/types/reviewTask.ts
frontend/src/App.tsx
frontend/src/components/ReviewTaskCreateForm.tsx
frontend/src/components/ReviewTaskList.tsx
frontend/src/components/ReviewTaskDetail.tsx
backend-java/src/main/java/com/codereviewx/backend/config/WebConfig.java
```

If tests exist, inspect:

```text
frontend/src/test/
```

If backend CORS was modified, inspect related backend config carefully.

---

## 5. Required Frontend Static Validation

From repository root:

```bash
cd frontend
```

Run:

```bash
npm install
```

Then run:

```bash
npm run typecheck
npm run build
npm test
```

If `npm test` is not configured, record that clearly.

If a lockfile exists and `npm ci` is more appropriate, you may use `npm ci`, but document the choice.

Validation expectations:

1. install succeeds;
2. typecheck succeeds;
3. build succeeds;
4. tests succeed if configured;
5. no unexpected dependency or script issues.

Record exact command outputs or concise output summaries in your handoff.

---

## 6. Required Backend Static and Test Validation

From repository root:

```bash
cd backend-java
```

Run:

```bash
mvn test
```

Expected:

```text
BUILD SUCCESS
```

You must verify that the CORS change did not break existing backend tests.

Also inspect backend dependency changes, if any.

Cursor should not have added backend dependencies for CORS.

---

## 7. Required Runtime Validation

You must validate frontend and backend together.

### 7.1 Start Backend

From repository root:

```bash
cd backend-java
mvn spring-boot:run
```

Confirm backend starts on:

```text
http://localhost:8080
```

Validate directly with curl:

```bash
curl -s http://localhost:8080/api/health
```

Expected response shape:

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

Then create a task directly:

```bash
curl -s -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/repo","prNumber":123}'
```

Expected:

1. `success=true`;
2. `data.id` exists;
3. `data.status=SUCCESS`;
4. `data.summary` contains mock review text;
5. `data.riskLevel=LOW`;
6. `data.issues=[]`.

Validate list:

```bash
curl -s http://localhost:8080/api/review-tasks
```

Validate detail using the created id:

```bash
curl -s http://localhost:8080/api/review-tasks/<created_id>
```

Validate not found:

```bash
curl -s -i http://localhost:8080/api/review-tasks/999999
```

Validate backend validation failure:

```bash
curl -s -i -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"","prNumber":0}'
```

Record status codes and response shapes.

---

### 7.2 Start Frontend

From repository root:

```bash
cd frontend
npm run dev
```

Confirm frontend starts on:

```text
http://localhost:5173
```

Then validate browser-visible behavior manually.

If browser automation is available, use it. If not, use manual browser validation and document exactly what was checked.

Required UI checks:

1. Page opens.
2. Header or visible area shows backend status.
3. Empty task list state is visible when no tasks exist.
4. Create form has:
   - repository URL input;
   - PR number input;
   - create button.
5. Invalid empty form submission shows client-side validation error.
6. Valid form submission creates task.
7. Created task appears in list.
8. Created task detail is displayed.
9. Clicking a task in list fetches and displays detail.
10. Issues section shows:
    - `No issues found in mock review.`
11. Backend validation failure produces a user-visible error.
12. Backend unavailable scenario produces a user-visible error or warning without crashing.

---

## 8. Required API Base URL / CORS Validation

Cursor handoff claims:

1. API base URL is read from `import.meta.env.VITE_API_BASE_URL`;
2. default is `http://localhost:8080`;
3. Vite dev server proxy forwards `/api` to `http://localhost:8080`;
4. backend CORS allows:
   - `http://localhost:5173`
   - `http://127.0.0.1:5173`

You must verify actual implementation behavior.

Check `frontend/src/api/reviewTaskApi.ts`.

Answer these questions in the handoff:

1. Does the API client default to `http://localhost:8080`?
2. Does the default path use Vite proxy or direct backend URL?
3. If Vite proxy is configured, is it actually used by default?
4. Does direct browser call mode work with backend CORS?
5. Does backend CORS only allow local Vite origins?
6. Is CORS limited to `/api/**`?
7. Were any broad wildcard origins added?
8. Was Spring Security introduced? It must not be.

Validation requirement:

At minimum, confirm that the normal frontend dev mode can successfully call backend APIs from browser runtime.

If practical, test both modes:

### Mode A: Direct backend URL

Create `frontend/.env.local` temporarily:

```text
VITE_API_BASE_URL=http://localhost:8080
```

Run:

```bash
npm run dev
```

Validate create/list/detail in browser.

Then remove `.env.local` unless it already existed and is intended to stay uncommitted.

### Mode B: Proxy-relative mode

If the API client supports relative `/api` base URL, validate proxy behavior.

If the API client does not support relative `/api` base URL by default, document that Vite proxy is configured but not used in the default mode.

This is not automatically blocking if direct mode works and CORS is minimal.

---

## 9. Required ApiResponse Handling Validation

Inspect and validate that frontend handles:

```ts
ApiResponse<T>
```

Correctly.

Specifically verify:

1. `success=true` with `data` renders normally.
2. `success=false` displays `message`.
3. `data=null` does not crash UI.
4. HTTP 400 validation responses from backend are parsed if they contain `ApiResponse`.
5. Network failures show a reasonable fallback message.
6. Not found detail response shows backend message.

Important: backend validation failure may return HTTP 400 with a JSON body. The API client should not discard this body by only checking `response.ok`.

If the API client currently discards useful backend messages on HTTP 400, you may make a minimal fix.

Acceptable fix:

1. parse JSON body first when available;
2. if body is an `ApiResponse` with `message`, show that message;
3. otherwise fallback to generic HTTP error.

Do not redesign the API client.

---

## 10. Required Scope Audit

Search the repository for forbidden additions.

Verify no new usage of:

```text
MyBatis
MySQL
JPA
Hibernate
Entity
Mapper
Repository
ai-service
GitHub API
Semgrep
LLM
OpenAI
Redis
RabbitMQ
Kafka
Scheduler
Spring Security
Swagger
OpenAPI
Next.js
Redux
MobX
localStorage persistence
```

Suggested commands:

```bash
grep -R "mybatis\|mysql\|jpa\|hibernate\|@Entity\|Mapper\|Repository" -n backend-java frontend || true
grep -R "ai-service\|github.com\|api.github.com\|semgrep\|openai\|llm" -n backend-java frontend || true
grep -R "redis\|rabbitmq\|kafka\|scheduler\|Spring Security\|swagger\|openapi" -n backend-java frontend || true
grep -R "redux\|mobx\|next\|localStorage" -n frontend || true
```

Use judgment: a string in README or test fixture is not necessarily a scope violation. Source code or dependency additions are what matter most.

Also inspect:

```text
frontend/package.json
backend-java/pom.xml
```

Confirm no unauthorized dependencies were added.

---

## 11. Allowed Minimal Fixes

You may make minimal fixes if validation fails due to small implementation errors.

Allowed examples:

1. Fix TypeScript type error.
2. Fix broken npm script.
3. Fix API client error parsing for `success=false`.
4. Fix frontend environment variable fallback.
5. Fix CORS config if it is too narrow or slightly incorrect.
6. Fix Vite proxy config if clearly intended and low risk.
7. Fix test setup issue.
8. Fix README inaccuracies caused by actual behavior.

Forbidden fixes:

1. Do not rewrite frontend architecture.
2. Do not replace React/Vite stack.
3. Do not introduce Redux/MobX.
4. Do not introduce routing unless absolutely trivial and already present.
5. Do not add database.
6. Do not add persistence.
7. Do not add ai-service client.
8. Do not add GitHub API client.
9. Do not add Semgrep execution.
10. Do not call LLM.
11. Do not create real issue model unless necessary for typing only.
12. Do not start Round 05.

If a problem requires larger changes, do not fix it. Document it as a blocking or non-blocking issue.

---

## 12. Validation Result Classification

Use one of the following final classifications:

```text
PASS
PASS_WITH_MINOR_FIXES
FAIL_BLOCKED
```

Definitions:

### PASS

Use when:

1. all required frontend commands pass;
2. backend tests pass;
3. backend runtime starts;
4. frontend runtime starts;
5. create/list/detail UI flow works;
6. validation errors are visible;
7. no scope violations are found;
8. no fixes were needed.

### PASS_WITH_MINOR_FIXES

Use when:

1. one or more small issues were found;
2. Codex fixed them with minimal, scoped changes;
3. all validation checks pass after fixes;
4. no scope violations remain.

### FAIL_BLOCKED

Use when:

1. frontend cannot install/build/typecheck;
2. backend tests fail;
3. backend runtime cannot start;
4. frontend runtime cannot start;
5. core create/list/detail flow does not work;
6. CORS prevents normal use and cannot be minimally fixed;
7. unauthorized scope was introduced;
8. required validation cannot be completed for a substantive reason.

---

## 13. Required Handoff Output

Create:

```text
handoff/round-04/02-codex-frontend-reviewtask-mock-ui-validation-handoff.md
```

Use this exact structure:

```markdown
# Round 04 Codex Handoff: Frontend ReviewTask Mock UI Validation

## 1. Summary

## 2. Validation Result

Choose one:

- PASS
- PASS_WITH_MINOR_FIXES
- FAIL_BLOCKED

## 3. Cursor Handoff Reviewed

State whether the Cursor handoff was reviewed.

## 4. Files Inspected

List key files inspected.

## 5. Commands Run

Include command, result, and output summary for:

- frontend install
- frontend typecheck
- frontend build
- frontend tests
- backend tests

## 6. Frontend Runtime Validation

Include:

- frontend dev server URL
- page opened
- backend status visible
- create form visible
- empty state visible
- create success
- list success
- detail success
- invalid input handling
- backend validation handling
- backend unavailable handling, if tested

## 7. Backend Runtime Validation

Include:

- backend startup result
- health API result
- create API result
- list API result
- detail API result
- not found result
- validation failure result

## 8. API Base URL / CORS Findings

Answer:

- default API base URL behavior
- whether Vite proxy is used by default
- whether direct backend mode works
- CORS allowed origins
- CORS path scope
- whether wildcard origins exist
- whether Spring Security was introduced

## 9. ApiResponse Handling Findings

Cover:

- success=true
- success=false
- data=null
- HTTP 400 response body parsing
- network error handling
- not found handling

## 10. Fixes Applied

If no fixes, write:

- No fixes applied.

If fixes were applied, list:

- file changed
- reason
- before behavior
- after behavior
- why fix is minimal

## 11. Files Modified by Codex

If none, write:

- No files modified by Codex.

## 12. Scope Audit

Confirm:

- No database added.
- No MyBatis-Plus added.
- No MySQL driver added.
- No JPA/Hibernate added.
- No Entity/Mapper/Repository added.
- No ai-service call added.
- No GitHub API call added.
- No Semgrep execution added.
- No LLM call added.
- No Redis/MQ/Scheduler added.
- No Spring Security added.
- No Swagger/OpenAPI added.
- No complex frontend state management added.
- No Next.js/SSR added.
- No localStorage persistence added.
- Round 05 not started.

## 13. Blocking Issues

If none, write:

- None.

## 14. Non-blocking Issues / Notes

Include minor observations such as:

- Vite proxy configured but not used by default, if true.
- Backend runtime validation required specific environment setup, if true.
- Any README mismatch, if true.

## 15. Recommendation

Recommend one:

- Proceed to Qoder independent review.
- Return to Cursor for fixes.
- Architect decision required.
```

---

## 14. Final Instruction

Validate thoroughly, but keep fixes minimal.

Your main role is not to continue implementation. Your role is to independently prove whether Cursor’s frontend ReviewTask Mock UI v1 actually works against the Round 03 backend mock API.

If validation passes, recommend:

```text
Proceed to Qoder independent review.
```

If validation fails, provide exact reproduction steps and classify the issue clearly.