# Round 07 Task 02 - Codex Database Persistence v1 Validation Handoff

## Validation Summary

Codex independently validated Cursor's Round 07 Database Persistence v1 implementation.

Core result:

- `ReviewTask` is persisted with Spring Data JPA.
- `ReviewIssue` is persisted with Spring Data JPA.
- Runtime uses file-based H2: `jdbc:h2:file:./data/codereviewx`.
- Tests use isolated in-memory H2: `jdbc:h2:mem:testdb`.
- Restart persistence was proven with a real backend restart.
- Round 06 response contract remains intact.
- No real GitHub/Semgrep/LLM/ai-service review pipeline was introduced.

Codex Verdict: `ROUND_07_VALIDATED_READY_FOR_QODER_REVIEW`

## Repository Inspection Summary

Inspected:

- `backend-java/pom.xml`
- `backend-java/src/main/resources/application.yml`
- `backend-java/src/test/resources/application.yml`
- `backend-java/src/main/java/com/codereviewx/backend/review/persistence/entity/*`
- `backend-java/src/main/java/com/codereviewx/backend/review/persistence/repository/*`
- `backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/controller/ReviewTaskController.java`
- backend tests under `backend-java/src/test/java`
- frontend API/types/components/tests under `frontend/src`
- root `README.md`, `backend-java/README.md`, and `.gitignore`

## Dependency and Configuration Verification

`backend-java/pom.xml` contains the expected Round 07 dependencies:

- `spring-boot-starter-data-jpa`
- `com.h2database:h2`

No MySQL/PostgreSQL driver, migration tool, Spring Security, Swagger, or unrelated runtime dependency was introduced.

Runtime config:

- `backend-java/src/main/resources/application.yml`
- datasource URL: `jdbc:h2:file:./data/codereviewx;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
- `ddl-auto: update`
- H2 console enabled for local development

Test config:

- `backend-java/src/test/resources/application.yml`
- datasource URL: `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
- `ddl-auto: create-drop`
- H2 console disabled

## Entity and Repository Verification

`ReviewTaskEntity`:

- has internal DB id: `Long id`
- persists `repoUrl`, `prNumber`, `status`, `summary`, `errorMessage`, `createdAt`, `updatedAt`
- uses `@Enumerated(EnumType.STRING)` for `ReviewTaskStatus`
- does not persist `issueSummary`
- does not persist `riskLevel`

`ReviewIssueEntity`:

- has internal DB id: `Long id`
- references `ReviewTaskEntity`
- persists public issue id field: `issueKey`
- persists `severity`, `category`, `source`, `status`, `filePath`, `startLine`, `endLine`, `title`, `description`, `recommendation`, `createdAt`, `updatedAt`
- uses `@Enumerated(EnumType.STRING)` for issue enums

Repositories:

- `ReviewTaskRepository extends JpaRepository<ReviewTaskEntity, Long>`
- stable task ordering: `findAllByOrderByCreatedAtDesc()`
- `ReviewIssueRepository extends JpaRepository<ReviewIssueEntity, Long>`
- stable issue ordering: `findByReviewTaskIdOrderByIdAsc(Long reviewTaskId)`

## Service Layer Verification

`ReviewTaskService` injects `ReviewTaskRepository` and `ReviewIssueRepository`.

Verified behavior:

- `createTask` saves a `ReviewTaskEntity`
- `createTask` saves exactly 3 deterministic `ReviewIssueEntity` records
- persisted issue defaults are `source=MOCK` and `status=OPEN`
- `listTasks` reads from database repositories
- `getTask` reads from database repositories
- missing task behavior remains `ReviewTaskNotFoundException`
- controllers return DTO responses, not JPA entities
- `ReviewIssueResponse.id` is mapped from `ReviewIssueEntity.issueKey`
- `issueSummary` is computed from persisted issues at response assembly time
- `ReviewTaskResponse.riskLevel` is set from `issueSummary.getRiskLevel()`

Scope/remnant scans:

- no `ConcurrentHashMap` or `AtomicLong` source-of-truth storage remains in backend main code
- no `IssueSummaryEntity` exists
- no `issue_summary` table/entity mapping exists
- old unused in-memory `review/model/ReviewTask.java` was removed by Codex as a minimal cleanup

## API Contract Verification

Endpoints remain:

- `GET /api/health`
- `POST /api/review-tasks`
- `GET /api/review-tasks`
- `GET /api/review-tasks/{id}`

Create request shape remains:

```json
{
  "repoUrl": "https://github.com/example/demo",
  "prNumber": 7
}
```

`ApiResponse<T>` wrapper remains:

```json
{
  "success": true,
  "message": "OK",
  "data": {}
}
```

`ReviewTaskResponse` includes:

- `id`
- `repoUrl`
- `prNumber`
- `status`
- `riskLevel`
- `summary`
- `errorMessage`
- `issues`
- `issueSummary`
- `createdAt`
- `updatedAt`

`ReviewIssueResponse` includes:

- `id`
- `severity`
- `category`
- `source`
- `status`
- `filePath`
- `startLine`
- `endLine`
- `title`
- `description`
- `recommendation`

`IssueSummaryResponse` includes:

- `totalIssues`
- `highCount`
- `mediumCount`
- `lowCount`
- `riskLevel`

## Backend Test Validation

Command:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Result:

```text
Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Breakdown:

- `CodeReviewXBackendApplicationTests`: 1 test
- `ReviewTaskControllerTest`: 12 tests
- `ReviewTaskServiceTest`: 24 tests

## Frontend Validation

Commands:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

Results:

- `npm run typecheck`: passed
- `npm run build`: passed
- `npm test -- --run`: 4 files passed, 26 tests passed

Frontend tests covered API adapter behavior, create form behavior, task list behavior, and detail/issue-summary rendering.

## Runtime Curl Validation

Backend command:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

Startup evidence:

```text
Tomcat started on port 8080
Database available at 'jdbc:h2:file:./data/codereviewx'
```

Health:

```bash
curl -s -i http://localhost:8080/api/health
```

Result:

```text
HTTP/1.1 200
{"success":true,"message":"OK","data":{"status":"UP","service":"backend-java"}}
```

Create:

```bash
curl -s -i -X POST http://localhost:8080/api/review-tasks \
  -H 'Content-Type: application/json' \
  -d '{"repoUrl":"https://github.com/example/round-07-codex-validation","prNumber":7}'
```

Result:

- `HTTP/1.1 200`
- returned task id: `1`
- `repoUrl=https://github.com/example/round-07-codex-validation`
- `prNumber=7`
- `status=SUCCESS`
- `issues.length=3`
- issue ids: `ISSUE-1`, `ISSUE-2`, `ISSUE-3`
- all issues had `source=MOCK`
- all issues had `status=OPEN`
- `issueSummary.totalIssues=3`
- `highCount=1`, `mediumCount=1`, `lowCount=1`
- `issueSummary.riskLevel=HIGH`
- `riskLevel=HIGH`

List and detail:

```bash
curl -s -i http://localhost:8080/api/review-tasks
curl -s -i http://localhost:8080/api/review-tasks/1
```

Both returned `HTTP/1.1 200`, the created task, 3 issues, source/status fields, `issueSummary`, and matching `riskLevel`.

## Restart Persistence Validation

Procedure performed:

1. started backend
2. created task id `1`
3. verified detail before restart
4. stopped backend with Ctrl-C
5. restarted backend
6. verified list after restart
7. verified detail for id `1` after restart

Post-restart startup again showed:

```text
Database available at 'jdbc:h2:file:./data/codereviewx'
```

Post-restart detail:

```bash
curl -s -i http://localhost:8080/api/review-tasks/1
```

Result:

- `HTTP/1.1 200`
- task id `1` still existed
- `repoUrl` and `prNumber` persisted
- 3 issues persisted
- issue ids remained `ISSUE-1`, `ISSUE-2`, `ISSUE-3`
- source remained `MOCK`
- status remained `OPEN`
- `issueSummary.totalIssues=3`
- `issueSummary.riskLevel=HIGH`
- `riskLevel=HIGH`

Local DB file observed:

```text
backend-java/data/codereviewx.mv.db
```

`.gitignore` contains:

```text
backend-java/data/
```

`git status --short` could not be used in this environment:

```text
fatal: not a git repository (or any of the parent directories): .git
```

This matches prior local environment behavior for this workspace, so Codex used `.gitignore` inspection as the DB-file ignore evidence.

## IssueSummary and Risk Invariant Validation

Verified by code inspection, tests, and runtime API responses:

- `issueSummary` is computed from response issue severities derived from persisted issue rows.
- risk rule remains:
  - high issues -> `HIGH`
  - else medium issues -> `MEDIUM`
  - else low issues -> `LOW`
  - else no issues -> `NONE`
- deterministic mock output is:
  - total: 3
  - high: 1
  - medium: 1
  - low: 1
  - risk: `HIGH`
- `ReviewTaskResponse.riskLevel == ReviewTaskResponse.issueSummary.riskLevel`
- no persisted task `riskLevel` column is used as source of truth
- no independent persisted `IssueSummaryEntity` exists

## Public Issue ID Validation

Verified:

- `ReviewIssueEntity.id` is the internal DB id
- `ReviewIssueEntity.issueKey` is the public issue id
- `ReviewIssueResponse.id` is mapped from `issueKey`
- runtime API returned string issue ids: `ISSUE-1`, `ISSUE-2`, `ISSUE-3`
- internal numeric DB issue ids were not exposed as public issue response ids

## Frontend Runtime Smoke Validation

Frontend command:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

URL:

```text
http://127.0.0.1:5173/
```

Browser smoke result:

- page title: `CodeReviewX`
- backend status displayed as UP
- no browser console warn/error entries were reported
- create form controls were uniquely located:
  - `Repository URL`
  - `PR Number`
  - `Create Review Task`
- created task:
  - `https://github.com/example/round-07-frontend-smoke`
  - PR `8`
- UI displayed `Review task created successfully.`
- UI displayed the created task in the list
- detail panel displayed `Review Result Summary`
- detail panel displayed 3 total issues
- detail panel displayed High/Medium/Low counts as 1/1/1
- detail panel displayed `Source: MOCK` and `Status: OPEN` badges for issues

## README Validation

Root `README.md`:

- identifies Round 07 as Database Persistence v1
- states `ReviewTask` and `ReviewIssue` are persisted
- states runtime uses file-based H2 and tests use in-memory H2
- states `issueSummary` is computed from persisted issues
- states `riskLevel` is derived from `issueSummary.riskLevel`
- clearly marks GitHub/Semgrep/LLM/ai-service behavior as future/planned, not current
- states no real code review is performed at this stage

`backend-java/README.md` was stale from Round 03 and was updated by Codex to match Round 07.

## Scope Creep Validation

Searches found no backend/frontend implementation introducing:

- GitHub client
- repository clone logic
- PR diff ingestion
- Semgrep process execution
- LLM client
- ai-service client
- agent planner
- status update endpoint
- resolve/false-positive workflow
- auth or Spring Security
- frontend redesign/component library/chart library

README references to GitHub/Semgrep/LLM/ai-service are either explicit non-goals for current implementation or future planned architecture.

## Patches Made by Codex

Codex made minimal validation-scope cleanup:

1. Replaced stale `backend-java/README.md` Round 03 in-memory documentation with Round 07 database persistence documentation.
2. Updated `backend-java/src/main/resources/application-local.yml` comments to avoid stale Round 02 wording.
3. Removed unused stale in-memory model file:
   - `backend-java/src/main/java/com/codereviewx/backend/review/model/ReviewTask.java`

No API behavior, persistence behavior, frontend behavior, or product feature was changed.

## Blocking Issues

None.

## Non-blocking Notes

- H2 file database is acceptable for Round 07 local persistence but is not production hardening.
- `ddl-auto=update` is acceptable for this round but should be revisited before production.
- `@OneToMany(fetch = FetchType.EAGER)` is acceptable at current mock scale; future rounds may prefer LAZY plus explicit repository fetch/fetch join for list performance.
- List responses include full issues; acceptable for current mock scale, potentially heavy later.
- `spring.jpa.open-in-view` logs a default warning during tests/runtime; no correctness issue observed.
- No migration tool exists yet; acceptable for Round 07.
- No real review pipeline, issue workflow, auth, or multi-user ownership exists yet.

## Final Verdict

Codex Verdict: `ROUND_07_VALIDATED_READY_FOR_QODER_REVIEW`
