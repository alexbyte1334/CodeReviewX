# tasks/round-07/01-cursor-database-persistence-v1.md

# Cursor Handoff: Database Persistence v1

## 1. Task Summary

Round 07 implementation completed. `ReviewTask` and `ReviewIssue` are now persisted to a file-based H2 database. The Round 06 API contract is fully preserved. All backend and frontend tests pass.

---

## 2. Implementation Changes

### 2.1 Dependencies Added (`backend-java/pom.xml`)

- `spring-boot-starter-data-jpa`
- `com.h2database:h2` (runtime scope — used for both local file-based and test in-memory mode)

### 2.2 Database Configuration

**Main (`backend-java/src/main/resources/application.yml`)**:
- `jdbc:h2:file:./data/codereviewx` — file-based H2, survives backend restarts
- `spring.jpa.hibernate.ddl-auto=update`
- `spring.h2.console.enabled=true`

**Test (`backend-java/src/test/resources/application.yml`)**:
- `jdbc:h2:mem:testdb` — in-memory H2, isolated per test run
- `spring.jpa.hibernate.ddl-auto=create-drop`

### 2.3 New Persistence Entities

**`ReviewTaskEntity`** (`review/persistence/entity/`):
- Fields: `id` (Long, auto-generated), `repoUrl`, `prNumber`, `status` (enum), `summary`, `errorMessage`, `createdAt`, `updatedAt`
- Relationship: `@OneToMany(EAGER)` → `ReviewIssueEntity`
- Does NOT persist `issueSummary` or `riskLevel` (both computed at response time)

**`ReviewIssueEntity`** (`review/persistence/entity/`):
- Fields: `id` (internal DB id), `reviewTask` (FK), `issueKey` (public API id, e.g. `ISSUE-1`), `severity`, `category`, `source`, `status`, `filePath`, `startLine`, `endLine`, `title`, `description`, `recommendation`, `createdAt`, `updatedAt`
- `issueKey` maps to `ReviewIssueResponse.id` — internal DB `id` is never exposed in API

### 2.4 New Repositories (`review/persistence/repository/`)

- `ReviewTaskRepository extends JpaRepository<ReviewTaskEntity, Long>` — includes `findAllByOrderByCreatedAtDesc()`
- `ReviewIssueRepository extends JpaRepository<ReviewIssueEntity, Long>` — includes `findByReviewTaskIdOrderByIdAsc(Long)`

### 2.5 Updated Service (`ReviewTaskService`)

- Constructor-injected `ReviewTaskRepository` and `ReviewIssueRepository`
- `createTask`: saves `ReviewTaskEntity`, then saves 3 `ReviewIssueEntity` records via `reviewIssueRepository.saveAll()`
- `listTasks`: loads from `findAllByOrderByCreatedAtDesc()`, loads issues per task via `findByReviewTaskIdOrderByIdAsc()`
- `getTask`: loads task by id, loads issues
- `toResponse`: maps entity → DTO, calls `buildIssueSummary(issueResponses)`, sets `riskLevel = issueSummary.getRiskLevel()`
- Removed: `ConcurrentHashMap`, `AtomicLong`, `ReviewTask` in-memory model

### 2.6 Removed File

- `backend-java/src/main/java/com/codereviewx/backend/review/model/ReviewTask.java` — replaced by `ReviewTaskEntity`

### 2.7 Updated Tests

**`ReviewTaskServiceTest`**:
- Converted from plain unit test (`new ReviewTaskService()`) to `@SpringBootTest` integration test
- `@Autowired ReviewTaskService`, `ReviewTaskRepository`, `ReviewIssueRepository`
- `@BeforeEach` cleans up all rows before each test
- Added new persistence tests: `createTask_persistsTaskToDatabase`, `createTask_persistsThreeIssuesToDatabase`, `createTask_issueKeyPreservesPublicId`, `getTask_issueSummaryComputedFromPersistedIssues`
- Total: 24 tests, all passing

**`ReviewTaskControllerTest`**:
- Added `@Autowired ReviewIssueRepository`, `ReviewTaskRepository`
- Added `@BeforeEach` cleanup
- All 12 tests passing

### 2.8 Updated `.gitignore`

- Added `backend-java/data/` to exclude local H2 database files

### 2.9 Updated README

- Documents database persistence v1
- Documents `ReviewTask` and `ReviewIssue` persistence
- Documents `issueSummary` computed from persisted issues
- Documents `riskLevel` derived from `issueSummary`
- Clarifies H2 file vs in-memory distinction
- States no GitHub, Semgrep, LLM, status workflow
- Clarified "Project Overview" section as "Planned Product Vision"
- Updated round progress table

---

## 3. API Contract Verification

All Round 06 contract fields preserved:

| Field | Preserved |
|---|---|
| `GET /api/health` | ✅ |
| `POST /api/review-tasks` | ✅ |
| `GET /api/review-tasks` | ✅ |
| `GET /api/review-tasks/{id}` | ✅ |
| `ApiResponse<T>` wrapper | ✅ |
| `ReviewTaskResponse.id` | ✅ (now DB-generated Long) |
| `ReviewTaskResponse.riskLevel` | ✅ derived from issueSummary |
| `ReviewTaskResponse.issueSummary` | ✅ computed from persisted issues |
| `ReviewIssueResponse.id` = `ISSUE-1` etc | ✅ (issueKey, not DB id) |
| `ReviewIssueResponse.source = MOCK` | ✅ |
| `ReviewIssueResponse.status = OPEN` | ✅ |
| `riskLevel == issueSummary.riskLevel` | ✅ |

---

## 4. Persistence Architecture

```
ReviewTaskEntity (review_task table)
  id: Long (PK, auto-generated)
  repoUrl, prNumber, status, summary, errorMessage
  createdAt, updatedAt
  -- no issueSummary column
  -- no riskLevel column

ReviewIssueEntity (review_issue table)
  id: Long (PK, auto-generated, internal only)
  review_task_id: FK → review_task.id
  issueKey: String  ← maps to ReviewIssueResponse.id
  severity, category, source, status
  filePath, startLine, endLine
  title, description, recommendation
  createdAt, updatedAt
```

Response assembly flow:
```
1. load ReviewTaskEntity from DB
2. load ReviewIssueEntity list via findByReviewTaskIdOrderByIdAsc(taskId)
3. map each entity → ReviewIssueResponse (issueKey → id)
4. buildIssueSummary(issueResponses)  ← computed, not stored
5. riskLevel = issueSummary.getRiskLevel()  ← derived, not stored
6. assemble ReviewTaskResponse
```

---

## 5. Test Results

**Backend**: 37 tests, 0 failures, 0 errors — BUILD SUCCESS

```
Tests run: 1  — CodeReviewXBackendApplicationTests
Tests run: 12 — ReviewTaskControllerTest
Tests run: 24 — ReviewTaskServiceTest
```

**Frontend**: all 26 tests pass, typecheck clean, build succeeds

```
Tests run: 26 — 4 test files
npm run typecheck: clean
npm run build: ✓ built in ~1s
```

---

## 6. Scope Compliance

- ✅ No GitHub API
- ✅ No repository clone
- ✅ No Semgrep
- ✅ No LLM / ai-service
- ✅ No agent planner
- ✅ No status update API
- ✅ No false-positive workflow
- ✅ No auth / Spring Security
- ✅ No frontend redesign
- ✅ No new component library
- ✅ No chart library
- ✅ No `IssueSummaryEntity` introduced
- ✅ No independently mutable `riskLevel` column

---

## 7. Restart Persistence

File-based H2 (`jdbc:h2:file:./data/codereviewx`) stores data in `backend-java/data/codereviewx.mv.db`. This file survives backend process restarts. Created tasks and their issues remain accessible after restart.

Tests use in-memory H2 (`jdbc:h2:mem:testdb`) for isolation — test data does not persist across test runs.

---

## 8. Next Step for Codex

Codex should independently validate:

1. Run `cd backend-java && JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test` and confirm all 37 tests pass
2. Run `cd frontend && npm run typecheck && npm run build && npm test -- --run` and confirm 26 tests pass
3. Start backend and curl the endpoints to verify API contract
4. Verify persistence entities and repositories match architecture spec
5. Verify `riskLevel == issueSummary.riskLevel` invariant
6. Verify no scope creep (no GitHub/Semgrep/LLM/workflow)
7. Verify README accuracy

Output: `tasks/round-07/02-codex-database-persistence-v1-validation.md`
