# tasks/round-07/00-round-07-start.md

# Round 07 Start: Database Persistence v1

## 1. Round Metadata

- Project: CodeReviewX
- Round: Round 07
- Theme: Database Persistence v1
- Task Type: Architecture-guided Implementation Round
- Primary Goal: 将 ReviewTask / ReviewIssue 从 in-memory storage 迁移到 database persistence，同时保持 Round 06 API contract 不变
- Previous Round:
  - Round 06: Review Result Contract Hardening
- Previous Final Verdict:
  - `ROUND_06_ACCEPTED_WITH_NOTES`
- First Task To Generate:
  - `tasks/round-07/01-cursor-database-persistence-v1.md`

---

## 2. Current Project State

CodeReviewX 当前已经完成到 Round 06。

系统已经具备：

1. backend-java Spring Boot mock API；
2. frontend React + TypeScript + Vite；
3. ReviewTask create/list/detail flow；
4. backend health display；
5. loading/error/empty states；
6. in-memory task storage；
7. deterministic mock review issues；
8. typed `ReviewIssue` contract；
9. typed `IssueSummary` contract；
10. backend-computed issue summary；
11. backend as authoritative source for risk summary；
12. frontend summary panel 优先使用 backend `issueSummary`；
13. frontend computed summary fallback；
14. issue cards；
15. severity/category/source/status badges；
16. file path/line range/description/recommendation 展示；
17. `IssueSource` contract；
18. `IssueStatus` contract；
19. `RiskLevel.NONE`；
20. legacy `IssueType` cleanup；
21. README current/planned boundary；
22. backend/frontend tests 通过；
23. runtime curl/browser validation 通过。

Round 06 最终由 Qoder 判定为：

```text
Verdict: ROUND_06_ACCEPTED_WITH_NOTES
```

无 blocking issue，可以关闭 Round 06。

---

## 3. Round 06 Accepted Baseline

### 3.1 Backend API Baseline

Current endpoints:

```text
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

Current create request:

```json
{
  "repoUrl": "https://github.com/example/demo",
  "prNumber": 6
}
```

Current API wrapper:

```json
{
  "success": true,
  "message": "OK",
  "data": {}
}
```

Round 07 must preserve all endpoint paths and the `ApiResponse<T>` wrapper.

---

### 3.2 ReviewTaskResponse Baseline

Current `ReviewTaskResponse` should contain at least:

```text
id
repoUrl
prNumber
status
riskLevel
summary
issues
issueSummary
createdAt
updatedAt
```

Existing fields must not be removed.

The invariant from Round 06 must remain true:

```text
ReviewTaskResponse.riskLevel == ReviewTaskResponse.issueSummary.riskLevel
```

---

### 3.3 ReviewIssueResponse Baseline

Current `ReviewIssueResponse` contains:

```text
id
severity
category
source
status
filePath
startLine
endLine
title
description
recommendation
```

Current enum values:

```text
IssueSeverity:
- LOW
- MEDIUM
- HIGH
```

```text
IssueCategory:
- BUG
- SECURITY
- PERFORMANCE
- MAINTAINABILITY
- STYLE
- TEST
```

```text
IssueSource:
- MOCK
- SEMGREP
- LLM
- MANUAL
```

```text
IssueStatus:
- OPEN
- RESOLVED
- FALSE_POSITIVE
```

Current mock issue defaults:

```text
source = MOCK
status = OPEN
```

---

### 3.4 IssueSummary Baseline

Current `IssueSummaryResponse` contains:

```text
totalIssues
highCount
mediumCount
lowCount
riskLevel
```

Current risk aggregation rule:

```text
totalIssues = issues.size()
highCount = count(severity == HIGH)
mediumCount = count(severity == MEDIUM)
lowCount = count(severity == LOW)

If highCount > 0:
  riskLevel = HIGH
Else if mediumCount > 0:
  riskLevel = MEDIUM
Else if lowCount > 0:
  riskLevel = LOW
Else:
  riskLevel = NONE
```

Current `RiskLevel`:

```text
NONE
LOW
MEDIUM
HIGH
```

Round 07 must preserve this rule.

---

## 4. Why Round 07 Is Needed

Round 06 hardened the review result contract and made the backend authoritative for issue summary and risk aggregation.

However, the backend still uses in-memory storage. This means:

1. data is lost when backend restarts；
2. task IDs reset；
3. list/detail only work within one process lifetime；
4. no foundation exists for future real review pipeline；
5. no persistence layer exists for later GitHub/Semgrep/LLM results；
6. no schema exists for ReviewTask / ReviewIssue evolution；
7. issue lifecycle fields exist in contract but are not persisted；
8. Round 08+ real pipeline work would be unstable without persistence.

Round 07 should introduce database persistence while keeping the current mock review generation and API contract stable.

The strategic principle:

```text
Persist the current stable contract before introducing real review intelligence.
```

---

## 5. Round 07 Strategic Goal

Round 07 的目标是完成：

```text
Database Persistence v1
```

具体目标：

1. introduce backend database persistence；
2. persist `ReviewTask`；
3. persist `ReviewIssue`；
4. replace in-memory `ConcurrentHashMap` storage；
5. keep deterministic mock issue generation；
6. keep create/list/detail endpoint paths unchanged；
7. keep `ApiResponse<T>` wrapper unchanged；
8. keep `ReviewTaskResponse` contract unchanged；
9. keep `ReviewIssueResponse` contract unchanged；
10. keep `IssueSummaryResponse` contract unchanged；
11. compute `issueSummary` from persisted issues at response assembly time；
12. keep `ReviewTaskResponse.riskLevel == issueSummary.riskLevel`；
13. persist issue `source`；
14. persist issue `status`；
15. keep `IssueStatus` read-only；
16. preserve frontend behavior without requiring major UI changes；
17. update backend tests；
18. update frontend tests only if API mock fixtures need adjustment；
19. update README to clarify database persistence v1；
20. runtime verify backend restart persistence.

---

## 6. Round 07 Non-goals

Round 07 is not:

1. real GitHub integration；
2. GitHub API client；
3. repository clone；
4. PR diff ingestion；
5. real code parsing；
6. Semgrep execution；
7. LLM integration；
8. ai-service integration；
9. tool orchestration；
10. agent planning loop；
11. status update API；
12. resolve issue workflow；
13. false-positive workflow；
14. human reviewer comment workflow；
15. auth/user system；
16. multi-user ownership；
17. organization/team model；
18. diff viewer；
19. syntax highlighting；
20. issue filtering/sorting；
21. dashboard analytics；
22. charts；
23. deployment；
24. production database hardening；
25. CI/CD pipeline；
26. frontend redesign；
27. component library migration。

---

## 7. Strict Scope Boundaries

### 7.1 Strictly Forbidden

Round 07 严禁：

1. 调用 GitHub API；
2. clone repository；
3. parse real repository code；
4. execute Semgrep；
5. call OpenAI / Anthropic / Gemini / local LLM；
6. call ai-service；
7. create ai-service client；
8. implement agent planner；
9. implement tool orchestration；
10. implement status update endpoint；
11. implement resolve / false-positive action；
12. implement human reviewer workflow；
13. introduce auth；
14. introduce Spring Security；
15. introduce Swagger/OpenAPI unless already present and required by existing build；
16. introduce Chart.js/ECharts/D3；
17. introduce Ant Design/Material UI；
18. introduce Redux/MobX/React Query/XState；
19. migrate frontend to Next.js；
20. implement SSR；
21. add diff viewer；
22. add filtering/sorting；
23. break existing API contract；
24. remove `riskLevel` compatibility field；
25. persist `issueSummary` as an independently mutable object；
26. allow `riskLevel` to drift from `issueSummary.riskLevel`。

### 7.2 Allowed

Round 07 允许：

1. add database dependency；
2. add JPA/Hibernate if choosing Spring Data JPA；
3. add H2 for local development/testing；
4. optionally add Docker Compose for database if using MySQL/PostgreSQL；
5. create persistence entities；
6. create repositories；
7. create mapper/conversion logic；
8. update service layer；
9. update test configuration；
10. update backend tests；
11. update README；
12. keep frontend mostly unchanged；
13. update frontend fixtures/tests if needed；
14. runtime curl verification；
15. backend restart persistence verification。

---

## 8. Database Choice Recommendation

Round 07 should optimize for fast, stable engineering delivery.

Recommended default:

```text
Spring Data JPA + H2 for local/dev/test
```

Reason:

1. fastest to integrate；
2. no external database required；
3. easy for tests；
4. good enough for persistence v1；
5. lets us validate schema/model/contract before choosing production database；
6. avoids slowing down Round 07 with Docker/database setup issues。

Alternative acceptable option:

```text
Spring Data JPA + MySQL/PostgreSQL via docker-compose
```

Only choose this if the project already has Docker/database conventions or the implementation environment can reliably run it.

Preferred Round 07 stance:

```text
Use H2 first, preserve clean persistence boundaries, and defer production database hardening.
```

Do not over-engineer multi-database production setup in Round 07.

---

## 9. Persistence Architecture Recommendation

### 9.1 Entity Model

Introduce persistence entities for:

```text
ReviewTaskEntity
ReviewIssueEntity
```

Suggested package:

```text
backend-java/src/main/java/com/codereviewx/backend/review/persistence/entity
```

Alternative acceptable package:

```text
backend-java/src/main/java/com/codereviewx/backend/review/entity
```

Prefer explicit `persistence/entity` if it does not conflict with existing project style.

---

### 9.2 Repository Model

Introduce repositories:

```text
ReviewTaskRepository
ReviewIssueRepository
```

Suggested package:

```text
backend-java/src/main/java/com/codereviewx/backend/review/persistence/repository
```

Use Spring Data JPA if JPA chosen:

```java
public interface ReviewTaskRepository extends JpaRepository<ReviewTaskEntity, Long> {
}
```

```java
public interface ReviewIssueRepository extends JpaRepository<ReviewIssueEntity, Long> {
    List<ReviewIssueEntity> findByReviewTaskIdOrderByIdAsc(Long reviewTaskId);
}
```

Repository method names may vary depending on entity mapping.

---

### 9.3 Entity Fields

#### `ReviewTaskEntity`

Recommended fields:

```text
id
repoUrl
prNumber
status
summary
errorMessage
createdAt
updatedAt
```

Java type suggestions:

```text
id: Long
repoUrl: String
prNumber: Integer
status: ReviewTaskStatus
summary: String
errorMessage: String nullable
createdAt: LocalDateTime or Instant
updatedAt: LocalDateTime or Instant
```

Do not persist `issueSummary`.

Strong recommendation:

```text
Do not persist riskLevel as an independently mutable column in Round 07.
```

If the existing code structure makes it significantly simpler to persist `riskLevel`, it must be treated as derived/cache only, and service tests must verify it never drifts from `issueSummary.riskLevel`.

Preferred:

```text
ReviewTaskResponse.riskLevel = buildIssueSummary(issues).getRiskLevel()
```

not:

```text
ReviewTaskResponse.riskLevel = persistedTask.riskLevel
```

---

#### `ReviewIssueEntity`

Recommended fields:

```text
id
reviewTask
issueKey or displayId
severity
category
source
status
filePath
startLine
endLine
title
description
recommendation
createdAt
updatedAt
```

Java type suggestions:

```text
id: Long
reviewTask: ReviewTaskEntity
issueKey: String
severity: IssueSeverity
category: IssueCategory
source: IssueSource
status: IssueStatus
filePath: String
startLine: Integer
endLine: Integer nullable
title: String
description: String
recommendation: String
createdAt: LocalDateTime or Instant
updatedAt: LocalDateTime or Instant
```

Important distinction:

```text
database id != public issue id
```

Round 06 uses issue IDs such as:

```text
ISSUE-1
ISSUE-2
ISSUE-3
```

Round 07 should preserve the public API field `ReviewIssueResponse.id`.

Recommended persistence field:

```text
issueKey
```

or:

```text
displayId
```

Mapping:

```text
ReviewIssueResponse.id = ReviewIssueEntity.issueKey
```

Do not expose internal database numeric issue ID as the public issue ID unless deliberately chosen and documented.

---

### 9.4 Relationship

Recommended relationship:

```text
ReviewTaskEntity 1 -> many ReviewIssueEntity
```

Acceptable JPA mapping:

```java
@OneToMany(mappedBy = "reviewTask", cascade = CascadeType.ALL, orphanRemoval = true)
private List<ReviewIssueEntity> issues = new ArrayList<>();
```

or service-driven separate save:

```java
ReviewTaskEntity savedTask = reviewTaskRepository.save(task);
reviewIssueRepository.saveAll(issues);
```

Preferred for Round 07:

```text
Keep relationship simple and predictable.
```

Avoid complex lazy-loading traps.

If using `@OneToMany`, ensure list/detail responses reliably include issues.

---

### 9.5 IssueSummary Persistence Policy

Do not create:

```text
IssueSummaryEntity
issue_summary table
```

Round 07 should compute `issueSummary` dynamically from persisted `ReviewIssueEntity` records.

Reason:

1. avoids data duplication；
2. avoids summary/issue drift；
3. keeps schema minimal；
4. matches Round 06 contract hardening principle；
5. enough for current scale。

Recommended response flow:

```text
ReviewTaskEntity
  -> load issues
  -> map issues to ReviewIssueResponse
  -> buildIssueSummary(issueResponses)
  -> set ReviewTaskResponse.issueSummary
  -> set ReviewTaskResponse.riskLevel = issueSummary.riskLevel
```

---

### 9.6 RiskLevel Persistence Policy

Recommended:

```text
riskLevel is derived-only in Round 07.
```

Do not store independently mutable `risk_level` column.

If stored as cache for implementation simplicity, then:

1. it must be set from `buildIssueSummary(issues)`；
2. it must never be user-editable；
3. it must be recomputed whenever issues change；
4. tests must verify response consistency；
5. README/handoff must state it is derived/cache only。

Preferred response assembly:

```java
IssueSummaryResponse issueSummary = buildIssueSummary(issueResponses);
response.setRiskLevel(issueSummary.getRiskLevel());
response.setIssueSummary(issueSummary);
```

---

## 10. Backend Required Changes

### 10.1 Add Dependencies

If using H2 + Spring Data JPA, add to `backend-java/pom.xml`:

```text
spring-boot-starter-data-jpa
h2
```

Use Spring Boot dependency management if already configured.

Do not add database drivers not used in Round 07.

---

### 10.2 Add Configuration

Add or update `application.properties` / `application.yml`.

For local H2 example:

```properties
spring.datasource.url=jdbc:h2:mem:codereviewx;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.h2.console.enabled=true
```

For tests, prefer isolated config:

```text
backend-java/src/test/resources/application-test.properties
```

If using in-memory H2, note that data persists across app runtime but not across full process restart. For Round 07 “restart persistence” requirement, file-based H2 may be better:

```properties
spring.datasource.url=jdbc:h2:file:./data/codereviewx
```

Recommended practical approach:

```text
Use file-based H2 for local runtime persistence verification.
Use in-memory H2 for tests if simpler.
```

Do not make tests depend on local file database state.

---

### 10.3 Replace In-memory Storage

Current storage likely resembles:

```java
ConcurrentHashMap
AtomicLong
```

Round 07 should replace this with repositories.

Requirements:

1. `createTask` writes task + issues to database；
2. `listTasks` reads tasks from database；
3. `getTask` reads task by id from database；
4. list/detail include persisted issues；
5. task ids should come from database；
6. order should be stable enough for UI/tests。

Suggested ordering:

```text
list tasks by createdAt desc or id desc
```

If current UI/tests expect old ordering, update tests explicitly.

---

### 10.4 Preserve Mock Generation

Keep deterministic mock generation:

```text
3 issues:
- 1 HIGH
- 1 MEDIUM
- 1 LOW
```

All persisted issues must have:

```text
source = MOCK
status = OPEN
```

Do not make mock generation depend on repo contents.

---

### 10.5 Mapping Layer

Introduce mapping methods or mapper class.

Possible service-private methods:

```java
private ReviewTaskResponse toResponse(ReviewTaskEntity taskEntity)
private ReviewIssueResponse toIssueResponse(ReviewIssueEntity issueEntity)
private ReviewIssueEntity toIssueEntity(ReviewTaskEntity task, ReviewIssueResponse mockIssue)
private IssueSummaryResponse buildIssueSummary(List<ReviewIssueResponse> issues)
```

Do not expose JPA entities directly from controllers.

DTOs remain the API contract.

---

### 10.6 Error Handling

Preserve current behavior for missing task detail.

If current behavior is:

```text
success=false
message="Review task not found"
data=null
```

or HTTP 404, keep the existing convention.

Do not redesign error contract in Round 07.

---

## 11. Frontend Required Changes

Round 07 should require minimal frontend changes.

Expected:

1. no major UI redesign；
2. no new state management；
3. no new component library；
4. no new charts；
5. no GitHub/Semgrep/LLM wording；
6. existing create/list/detail flow should keep working；
7. frontend types should remain compatible with Round 06 API contract。

Potential necessary updates:

1. update tests if backend task IDs become numeric strings from database；
2. update mock fixtures if timestamps/order differ；
3. update README to mention backend data is now persisted；
4. verify frontend still displays issueSummary/source/status correctly；
5. verify browser create/list/detail works after backend restart。

Important:

```text
Frontend should not compute persistence state.
Frontend should not know database implementation details.
```

---

## 12. README Update Requirements

Update root README and backend/frontend README as applicable.

Must document:

1. Round 07 introduces database persistence v1；
2. ReviewTask is now persisted；
3. ReviewIssue is now persisted；
4. data no longer depends purely on in-memory storage；
5. current issue summary is still backend-computed；
6. `issueSummary` is computed from persisted issues；
7. `riskLevel` is derived from the same issue summary；
8. current issue source remains `MOCK`；
9. current issue status remains `OPEN`；
10. no GitHub API is called；
11. no repository is cloned；
12. no Semgrep is executed；
13. no LLM / ai-service is called；
14. status update workflow is not implemented；
15. false-positive workflow is not implemented；
16. current vs planned architecture remains clear。

Also carry forward Qoder documentation note:

```text
Rename or clarify root README "Project Overview" as "Planned Product Vision" or add a sentence that it describes the future product direction, not the current implementation.
```

---

## 13. Backend Tests Required

Backend tests must be updated or added to cover persistence.

### 13.1 Required Test Coverage

At minimum:

1. create task persists `ReviewTask`；
2. create task persists 3 `ReviewIssue` records；
3. list reads persisted tasks；
4. detail reads persisted task；
5. detail reads persisted issues；
6. `issueSummary` is computed from persisted issues；
7. `riskLevel == issueSummary.riskLevel`；
8. all persisted issues have `source=MOCK`；
9. all persisted issues have `status=OPEN`；
10. `ReviewIssueResponse.id` preserves public issue id semantics；
11. missing task behavior unchanged；
12. `ApiResponse<T>` wrapper unchanged；
13. existing create/list/detail controller tests still pass；
14. service tests no longer depend on in-memory map internals；
15. no `IssueSummaryEntity` / summary table is introduced unless explicitly justified；
16. no independently mutable `riskLevel` drift。

### 13.2 Persistence Behavior Test

Add integration-style test if feasible:

```text
create task
retrieve detail
assert persisted task and issues exist through repository
assert API response contract unchanged
```

If using H2 in tests, ensure tests are isolated.

Use transaction/test cleanup strategy to avoid state leakage.

---

## 14. Frontend Tests Required

Frontend tests should continue to pass.

At minimum verify:

1. review task creation still works with API response shape；
2. task list renders persisted task response；
3. detail renders backend `issueSummary`；
4. issue card renders `source`；
5. issue card renders `status`；
6. fallback summary still works；
7. loading/error/empty states still work；
8. no new frontend type errors；
9. build passes。

If no frontend code changes are needed, still run:

```bash
cd frontend
npm run typecheck
npm run build
npm test
```

---

## 15. Runtime Verification Requirements

Round 07 runtime verification must include persistence behavior.

### 15.1 Backend Startup

Run:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

or:

```bash
cd backend-java
mvn spring-boot:run
```

### 15.2 Health

```bash
curl http://localhost:8080/api/health
```

Confirm:

```text
success=true
data.status=UP
```

### 15.3 Create

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/round-07-persistence-demo",
    "prNumber": 7
  }'
```

Confirm:

```text
success=true
data.id exists
data.issues.length=3
data.issueSummary.totalIssues=3
data.issueSummary.highCount=1
data.issueSummary.mediumCount=1
data.issueSummary.lowCount=1
data.issueSummary.riskLevel=HIGH
data.riskLevel=HIGH
data.riskLevel == data.issueSummary.riskLevel
data.issues[0].source=MOCK
data.issues[0].status=OPEN
```

### 15.4 List

```bash
curl http://localhost:8080/api/review-tasks
```

Confirm created task appears and contains:

```text
issueSummary
issues
source/status
riskLevel consistency
```

### 15.5 Detail

```bash
curl http://localhost:8080/api/review-tasks/{id}
```

Confirm detail contract remains unchanged.

### 15.6 Restart Persistence Verification

This is new and required for Round 07.

Steps:

1. create task；
2. record task id；
3. stop backend；
4. restart backend；
5. call list；
6. call detail by recorded id。

Expected:

```text
created task still exists after backend restart
detail still returns issues
issueSummary still computed correctly
riskLevel still equals issueSummary.riskLevel
```

If using in-memory H2 for tests but file-based H2 for local runtime, document that distinction.

If using purely in-memory H2 for Round 07, restart persistence is not truly satisfied and should be treated as a blocking issue unless explicitly justified.

---

## 16. Required Commands

Backend:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

or:

```bash
cd backend-java
mvn test
```

Frontend:

```bash
cd frontend
npm install
npm run typecheck
npm run build
npm test
```

If script names differ, inspect `package.json`, run actual scripts, and record results.

Runtime:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

---

## 17. Suggested Round 07 Task Sequence

Round 07 should continue using Cursor → Codex → Qoder.

### 17.1 Cursor Implementation

Generate:

```text
tasks/round-07/01-cursor-database-persistence-v1.md
```

Responsibilities:

1. add persistence dependency；
2. configure database；
3. create entities；
4. create repositories；
5. replace in-memory storage；
6. preserve API contract；
7. persist ReviewTask；
8. persist ReviewIssue；
9. compute issueSummary from persisted issues；
10. keep riskLevel derived from issueSummary；
11. preserve mock issue generation；
12. update backend tests；
13. update frontend tests only if needed；
14. update README；
15. perform runtime and restart persistence verification；
16. output Cursor handoff。

### 17.2 Codex Validation

Generate:

```text
tasks/round-07/02-codex-database-persistence-v1-validation.md
```

Responsibilities:

1. independently run backend tests；
2. independently run frontend typecheck/build/tests；
3. inspect entity/repository design；
4. verify API contract compatibility；
5. verify persistence behavior；
6. verify restart persistence；
7. verify issueSummary read-time aggregation；
8. verify riskLevel derived consistency；
9. verify no GitHub/Semgrep/LLM/workflow scope creep；
10. verify README；
11. output Codex handoff。

### 17.3 Qoder Independent Review

Generate:

```text
tasks/round-07/03-qoder-database-persistence-v1-independent-review.md
```

Responsibilities:

1. independently review persistence architecture；
2. decide whether schema is stable enough for future real pipeline；
3. review riskLevel/issueSummary invariant；
4. review issue source/status persistence；
5. review tests/runtime evidence；
6. review README；
7. judge if Round 07 can close；
8. recommend Round 08 direction；
9. output Qoder handoff。

---

## 18. Acceptance Criteria for Round 07

### Backend Persistence

- [ ] database dependency introduced；
- [ ] database configuration added；
- [ ] `ReviewTask` is persisted；
- [ ] `ReviewIssue` is persisted；
- [ ] in-memory task map removed or no longer used as source of truth；
- [ ] create task writes task to database；
- [ ] create task writes 3 mock issues to database；
- [ ] list tasks reads from database；
- [ ] detail task reads from database；
- [ ] backend restart does not lose previously created task；
- [ ] task detail after restart still includes issues；
- [ ] no `IssueSummaryEntity` introduced unless explicitly justified；
- [ ] no independently mutable persisted `riskLevel` drift。

### API Contract

- [ ] endpoint paths unchanged；
- [ ] `ApiResponse<T>` wrapper unchanged；
- [ ] create request shape unchanged；
- [ ] `ReviewTaskResponse` fields preserved；
- [ ] `ReviewIssueResponse` fields preserved；
- [ ] `IssueSummaryResponse` fields preserved；
- [ ] `ReviewTaskResponse.issueSummary` exists；
- [ ] `ReviewTaskResponse.riskLevel` exists；
- [ ] `riskLevel == issueSummary.riskLevel`；
- [ ] `ReviewIssueResponse.source` exists；
- [ ] `ReviewIssueResponse.status` exists；
- [ ] issue source remains `MOCK`；
- [ ] issue status remains `OPEN`。

### IssueSummary / Risk

- [ ] `issueSummary` computed from persisted issues；
- [ ] `totalIssues` correct；
- [ ] `highCount` correct；
- [ ] `mediumCount` correct；
- [ ] `lowCount` correct；
- [ ] risk rule remains `HIGH > MEDIUM > LOW > NONE`；
- [ ] `riskLevel` derived from same summary；
- [ ] `RiskLevel.NONE` still supported；
- [ ] empty issue summary behavior tested if feasible。

### Frontend

- [ ] existing UI works；
- [ ] backend health display works；
- [ ] create task works；
- [ ] list task works；
- [ ] detail task works；
- [ ] summary panel uses backend `issueSummary`；
- [ ] source/status badges still render；
- [ ] demo/mock label remains clear；
- [ ] loading/error/empty states not broken；
- [ ] frontend typecheck passes；
- [ ] frontend build passes；
- [ ] frontend tests pass。

### Documentation

- [ ] README documents database persistence v1；
- [ ] README documents ReviewTask persistence；
- [ ] README documents ReviewIssue persistence；
- [ ] README states `issueSummary` is computed from persisted issues；
- [ ] README states `riskLevel` is derived from summary；
- [ ] README states source remains `MOCK`；
- [ ] README states status remains `OPEN`；
- [ ] README states no GitHub API；
- [ ] README states no repository clone；
- [ ] README states no Semgrep；
- [ ] README states no LLM / ai-service；
- [ ] README states no issue workflow；
- [ ] README current/planned boundary remains clear；
- [ ] root README Project Overview wording clarified as planned product vision。

### Scope

- [ ] no GitHub API；
- [ ] no repository clone；
- [ ] no real code parsing；
- [ ] no Semgrep execution；
- [ ] no LLM call；
- [ ] no ai-service client；
- [ ] no agent planner；
- [ ] no tool orchestration；
- [ ] no status update API；
- [ ] no false-positive workflow；
- [ ] no human reviewer workflow；
- [ ] no auth；
- [ ] no frontend redesign；
- [ ] no component library；
- [ ] no chart library；
- [ ] no complex frontend state management。

---

## 19. Known Risks to Track

### 19.1 Persistence Scope Creep

Introducing database often tempts implementation to also introduce workflow, auth, filtering, or real integrations.

Do not do that in Round 07.

Persistence first.

---

### 19.2 riskLevel / issueSummary Drift

If `riskLevel` is persisted independently, it can drift from `issueSummary.riskLevel`.

Preferred solution:

```text
Do not persist riskLevel as independently mutable state.
Derive response riskLevel from buildIssueSummary(issues).
```

If persisted for compatibility/cache, tests must prove invariant.

---

### 19.3 Public Issue ID vs Database ID

Round 06 public issue IDs are string-like:

```text
ISSUE-1
ISSUE-2
ISSUE-3
```

Round 07 database IDs may be numeric.

Do not accidentally break API contract by exposing internal numeric IDs unless intentionally accepted.

Recommended:

```text
ReviewIssueEntity.id = internal DB id
ReviewIssueEntity.issueKey = public API id
ReviewIssueResponse.id = issueKey
```

---

### 19.4 H2 Runtime Persistence

If using H2 in-memory mode:

```text
jdbc:h2:mem
```

data will not survive backend restart.

Round 07 requires restart persistence verification.

Recommended:

```text
file-based H2 for runtime
in-memory H2 for tests
```

Document the distinction.

---

### 19.5 Test Isolation

Database tests can leak state.

Ensure tests isolate state using one or more of:

```text
@Transactional rollback
repository cleanup
test profile
unique database per test
@DirtiesContext only when necessary
```

Avoid flaky tests.

---

### 19.6 Entity Lazy Loading

If using JPA relationships, avoid response assembly failing due to lazy loading outside transaction.

Options:

1. service method transactional read；
2. explicit repository method to fetch issues；
3. avoid relying on lazy collection serialization；
4. map entities to DTOs inside service boundary。

Do not return JPA entities directly from controller.

---

### 19.7 README Overclaiming

After adding database, README must still clearly say:

```text
database persistence exists,
but real code review does not yet exist.
```

Avoid implying GitHub/Semgrep/LLM are implemented.

---

## 20. Recommended Round 08 Direction

If Round 07 succeeds, Round 08 should not necessarily jump directly to LLM.

Recommended next directions, in priority order:

### Option A: GitHub PR Input Contract v1

Introduce a stable abstraction for PR metadata/diff ingestion without necessarily executing full review.

Possible scope:

```text
GitHub connector interface
mock GitHub client
PR metadata DTO
PR diff DTO
no real GitHub call unless explicitly scoped
```

### Option B: Review Pipeline Orchestrator Skeleton

Introduce a backend orchestration layer:

```text
ReviewPipelineService
ReviewContext
ReviewFinding
ReviewToolResult
MockReviewProvider
```

Still no real LLM/Semgrep, but prepare tool/plugin structure.

### Option C: Static Analysis Integration v1

Introduce Semgrep or simple rule-based analyzer only after persistence is stable.

Qoder in Round 07 should recommend one direction.

Initial recommendation:

```text
Round 08 should likely introduce Review Pipeline Orchestrator Skeleton before real GitHub/Semgrep/LLM.
```

Reason:

1. keeps agent architecture clean；
2. avoids hardcoding GitHub/Semgrep directly into service；
3. gives future tools a stable interface；
4. preserves current API contract。

---

## 21. Final Instruction for Round 07

Round 07 的核心原则：

```text
Persist the stable Round 06 contract without changing product behavior.
```

This round should change storage, not product capability.

Do:

```text
database
ReviewTask persistence
ReviewIssue persistence
read-time issueSummary aggregation
derived riskLevel
restart persistence verification
README update
```

Do not do:

```text
GitHub
Semgrep
LLM
ai-service
agent planner
tool orchestration
status workflow
diff viewer
filtering/sorting
auth
frontend redesign
```

First task to generate:

```text
tasks/round-07/01-cursor-database-persistence-v1.md
```