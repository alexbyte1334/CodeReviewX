# tasks/round-07/02-codex-database-persistence-v1-validation.md

# Codex Task: Round 07 Database Persistence v1 Validation

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 07
- Task: 02
- Owner: Codex
- Theme: Database Persistence v1
- Task Type: Independent Validation
- Previous Task: `tasks/round-07/01-cursor-database-persistence-v1.md`
- Cursor Handoff: `tasks/round-07/01-cursor-database-persistence-v1-handoff.md`
- Expected Output: 独立验证 Cursor 的 database persistence implementation，并输出 Codex validation handoff

---

## 2. Validation Goal

本任务不是重新实现 Round 07，而是独立验证 Cursor 已完成的实现是否真实满足 Round 07 要求。

核心验证目标：

```text
ReviewTask / ReviewIssue 已从 in-memory storage 迁移到 database persistence，
同时 Round 06 API contract、mock product behavior、frontend behavior 均保持稳定。
```

Codex 必须重点验证：

1. backend tests 是否真实通过；
2. frontend typecheck/build/tests 是否真实通过；
3. API contract 是否未破坏；
4. `ReviewTask` 是否真实持久化；
5. `ReviewIssue` 是否真实持久化；
6. backend restart 后数据是否仍存在；
7. `issueSummary` 是否从 persisted issues 计算；
8. `riskLevel` 是否始终 derived from `issueSummary.riskLevel`；
9. public issue id 是否未暴露内部 DB id；
10. 是否存在 scope creep。

---

## 3. Cursor Handoff Summary to Validate

Cursor 声称已完成：

1. added `spring-boot-starter-data-jpa`；
2. added `com.h2database:h2`；
3. main runtime uses file-based H2:

```text
jdbc:h2:file:./data/codereviewx
```

4. tests use in-memory H2:

```text
jdbc:h2:mem:testdb
```

5. added `ReviewTaskEntity`；
6. added `ReviewIssueEntity`；
7. added `ReviewTaskRepository`；
8. added `ReviewIssueRepository`；
9. removed in-memory `ConcurrentHashMap` / `AtomicLong` storage；
10. removed old `ReviewTask` in-memory model；
11. persisted 3 deterministic mock issues per created task；
12. persisted issue `source = MOCK`；
13. persisted issue `status = OPEN`；
14. introduced `issueKey` to preserve public API issue id；
15. avoided persisting `issueSummary`；
16. avoided persisting independently mutable `riskLevel`；
17. computes `issueSummary` from persisted issues at response assembly time；
18. derives `riskLevel` from the computed `issueSummary`；
19. updated backend tests；
20. updated frontend tests only if needed；
21. updated README；
22. verified restart persistence.

Codex must independently confirm these claims.

---

## 4. Strict Validation Boundaries

Do not implement new product features.

Do not introduce:

1. GitHub API；
2. repository clone；
3. PR diff ingestion；
4. real code parsing；
5. Semgrep；
6. LLM；
7. ai-service；
8. agent planner；
9. tool orchestration；
10. status update endpoint；
11. resolve issue workflow；
12. false-positive workflow；
13. human reviewer workflow；
14. auth；
15. Spring Security；
16. frontend redesign；
17. component library；
18. chart library；
19. Redux/MobX/React Query/XState；
20. deployment；
21. production DB hardening。

This task is validation only.

If small fixes are required to make the implementation pass Round 07 acceptance criteria, Codex may patch them, but must clearly document every change and rationale.

---

## 5. Repository Inspection Requirements

Before running tests, inspect the implementation.

### 5.1 Backend Dependency Inspection

Check:

```text
backend-java/pom.xml
```

Verify:

1. `spring-boot-starter-data-jpa` exists；
2. `h2` dependency exists；
3. no unused MySQL/PostgreSQL driver was introduced；
4. no unnecessary migration/tooling dependency was introduced unless justified；
5. no Spring Security / Swagger / unrelated dependency was introduced。

Expected dependency direction:

```text
Spring Data JPA + H2
```

---

### 5.2 Database Configuration Inspection

Check:

```text
backend-java/src/main/resources/application.yml
backend-java/src/main/resources/application.properties
backend-java/src/test/resources/application.yml
backend-java/src/test/resources/application.properties
```

Validate main runtime config:

1. uses file-based H2 or another persistent database；
2. does not use `jdbc:h2:mem` for runtime persistence；
3. has reasonable local configuration；
4. `ddl-auto=update` is acceptable for Round 07；
5. H2 console may be enabled for local development。

Expected runtime example:

```text
jdbc:h2:file:./data/codereviewx
```

Validate test config:

1. uses in-memory H2；
2. uses isolated test database；
3. preferably uses `ddl-auto=create-drop`；
4. tests do not depend on local file database state。

Expected test example:

```text
jdbc:h2:mem:testdb
```

Important:

```text
If runtime config uses only jdbc:h2:mem, restart persistence is not satisfied.
```

---

### 5.3 Entity Inspection

Inspect:

```text
backend-java/src/main/java/**/review/**/persistence/entity/**
```

or equivalent package.

Validate `ReviewTaskEntity`:

1. has internal DB id；
2. persists `repoUrl`；
3. persists `prNumber`；
4. persists `status`；
5. persists `summary`；
6. persists optional `errorMessage` if present in existing model；
7. persists `createdAt`；
8. persists `updatedAt`；
9. does not persist `issueSummary`；
10. preferably does not persist `riskLevel`；
11. if `riskLevel` is persisted, it is clearly derived/cache only and never used as source of truth。

Validate `ReviewIssueEntity`:

1. has internal DB id；
2. has FK/reference to `ReviewTaskEntity`；
3. has `issueKey` or equivalent public issue id field；
4. persists `severity`；
5. persists `category`；
6. persists `source`；
7. persists `status`；
8. persists `filePath`；
9. persists `startLine`；
10. persists `endLine`；
11. persists `title`；
12. persists `description`；
13. persists `recommendation`；
14. persists `createdAt`；
15. persists `updatedAt`；
16. maps `ReviewIssueResponse.id` from `issueKey`, not internal DB id。

Enums should use:

```java
@Enumerated(EnumType.STRING)
```

or equivalent safe string-based persistence.

Numeric enum ordinal persistence is not preferred and should be called out.

---

### 5.4 Repository Inspection

Inspect:

```text
backend-java/src/main/java/**/review/**/persistence/repository/**
```

Validate:

1. `ReviewTaskRepository` extends `JpaRepository<ReviewTaskEntity, Long>` or equivalent；
2. `ReviewIssueRepository` extends `JpaRepository<ReviewIssueEntity, Long>` or equivalent；
3. there is a stable task ordering method, such as:

```java
findAllByOrderByCreatedAtDesc()
```

or:

```java
findAllByOrderByIdDesc()
```

4. there is a stable issue ordering method, such as:

```java
findByReviewTaskIdOrderByIdAsc(Long reviewTaskId)
```

5. service layer reads from repositories, not in-memory map。

---

### 5.5 Service Layer Inspection

Inspect:

```text
ReviewTaskService
```

or equivalent.

Validate:

1. service injects repositories；
2. `createTask` saves `ReviewTaskEntity`；
3. `createTask` saves exactly 3 `ReviewIssueEntity` records；
4. mock generation remains deterministic；
5. `listTasks` reads from database；
6. `getTask` reads from database；
7. missing task behavior remains unchanged；
8. response assembly maps entities to DTOs；
9. JPA entities are not returned directly from controller；
10. `issueSummary` is computed from loaded persisted issues；
11. `riskLevel` is set from `issueSummary.getRiskLevel()`；
12. no in-memory `ConcurrentHashMap` / `AtomicLong` remains as source of truth。

Run code search:

```bash
grep -R "ConcurrentHashMap" backend-java/src/main/java || true
grep -R "AtomicLong" backend-java/src/main/java || true
grep -R "new ReviewTask" backend-java/src/main/java || true
grep -R "IssueSummaryEntity" backend-java/src/main/java || true
```

Expected:

```text
No authoritative in-memory task storage remains.
No IssueSummaryEntity exists.
```

---

## 6. API Contract Validation

Validate endpoints remain:

```text
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

Validate create request shape remains:

```json
{
  "repoUrl": "https://github.com/example/demo",
  "prNumber": 7
}
```

Validate `ApiResponse<T>` wrapper remains:

```json
{
  "success": true,
  "message": "OK",
  "data": {}
}
```

Validate `ReviewTaskResponse` still contains at least:

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

Validate `ReviewIssueResponse` still contains:

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

Validate `IssueSummaryResponse` still contains:

```text
totalIssues
highCount
mediumCount
lowCount
riskLevel
```

Validate invariant:

```text
ReviewTaskResponse.riskLevel == ReviewTaskResponse.issueSummary.riskLevel
```

Validate mock issue defaults:

```text
source = MOCK
status = OPEN
```

---

## 7. Backend Test Validation

Run backend tests.

Preferred command:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

If the environment does not require explicit Java path:

```bash
cd backend-java
mvn test
```

Record:

1. total test count；
2. failures；
3. errors；
4. skipped；
5. build result。

Expected from Cursor handoff:

```text
37 tests
0 failures
0 errors
BUILD SUCCESS
```

If numbers differ, explain why.

Codex must not rely only on Cursor's reported test result.

---

## 8. Frontend Validation

Inspect frontend scripts:

```bash
cd frontend
cat package.json
```

Run actual scripts.

Expected commands:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

If project uses a different test command, use the actual command and document it.

Record:

1. typecheck result；
2. build result；
3. frontend test count；
4. failures；
5. errors。

Expected from Cursor handoff:

```text
typecheck clean
build succeeds
26 tests pass
```

Codex must verify frontend still compiles against the API types and fixtures.

---

## 9. Runtime API Validation

Start backend:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

or:

```bash
cd backend-java
mvn spring-boot:run
```

### 9.1 Health

Run:

```bash
curl http://localhost:8080/api/health
```

Expected:

```text
success=true
data.status=UP
```

### 9.2 Create Task

Run:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/round-07-codex-validation",
    "prNumber": 7
  }'
```

Record returned `data.id`.

Validate response:

```text
success=true
data.id exists
data.repoUrl=https://github.com/example/round-07-codex-validation
data.prNumber=7
data.issues.length=3
data.issueSummary.totalIssues=3
data.issueSummary.highCount=1
data.issueSummary.mediumCount=1
data.issueSummary.lowCount=1
data.issueSummary.riskLevel=HIGH
data.riskLevel=HIGH
data.riskLevel == data.issueSummary.riskLevel
```

Validate each issue includes:

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

Validate:

```text
source=MOCK
status=OPEN
```

Validate public issue ids:

```text
ISSUE-1
ISSUE-2
ISSUE-3
```

or equivalent existing public issue id semantics.

Internal DB numeric issue id must not be exposed as `ReviewIssueResponse.id`.

---

### 9.3 List Tasks

Run:

```bash
curl http://localhost:8080/api/review-tasks
```

Validate created task appears.

Validate list item includes:

```text
issues
issueSummary
riskLevel
source/status on issues
```

Validate:

```text
riskLevel == issueSummary.riskLevel
```

Validate list ordering is stable enough for UI/tests.

---

### 9.4 Detail Task

Run:

```bash
curl http://localhost:8080/api/review-tasks/{id}
```

Validate:

```text
success=true
data.id matches created id
data.issues.length=3
data.issueSummary.totalIssues=3
data.issueSummary.highCount=1
data.issueSummary.mediumCount=1
data.issueSummary.lowCount=1
data.issueSummary.riskLevel=HIGH
data.riskLevel=HIGH
data.riskLevel == data.issueSummary.riskLevel
source=MOCK
status=OPEN
```

---

## 10. Restart Persistence Validation

This is mandatory for Round 07.

Codex must perform actual restart verification.

Steps:

1. start backend；
2. create a task；
3. record returned task id；
4. call detail and confirm task exists；
5. stop backend process；
6. restart backend；
7. call list；
8. call detail by recorded id。

Expected after restart:

```text
created task still exists
detail still returns task
detail still returns 3 issues
issueSummary still computed correctly
riskLevel still equals issueSummary.riskLevel
source remains MOCK
status remains OPEN
```

If the task disappears after restart, Round 07 validation fails.

Likely cause:

```text
runtime is using jdbc:h2:mem instead of file-based H2
```

Also verify local DB files are ignored by git:

```bash
git status --short
```

Expected:

```text
backend-java/data/ files should not appear as tracked/untracked changes
```

If DB files appear, `.gitignore` is incomplete.

---

## 11. Persistence Behavior Validation Through Repositories

If feasible, inspect or add validation around repositories.

Confirm:

1. after create, `ReviewTaskRepository` has the created row；
2. after create, `ReviewIssueRepository` has exactly 3 rows for that task；
3. issue rows include `source=MOCK`；
4. issue rows include `status=OPEN`；
5. issue rows include `issueKey`；
6. task row does not include independently mutable summary entity；
7. response issue ids come from `issueKey`。

If using tests, check existing tests cover this.

If not covered, either add minimal tests or record as validation gap.

---

## 12. IssueSummary / Risk Validation

Validate risk rule remains:

```text
If highCount > 0:
  riskLevel = HIGH
Else if mediumCount > 0:
  riskLevel = MEDIUM
Else if lowCount > 0:
  riskLevel = LOW
Else:
  riskLevel = NONE
```

Validate current deterministic mock output:

```text
totalIssues=3
highCount=1
mediumCount=1
lowCount=1
riskLevel=HIGH
```

Validate:

```text
ReviewTaskResponse.riskLevel == ReviewTaskResponse.issueSummary.riskLevel
```

Important inspection:

1. `riskLevel` must not be read from a stale persisted column；
2. `issueSummary` must not be loaded from an independently persisted table；
3. `issueSummary` must be computed from current persisted issue records。

Search:

```bash
grep -R "riskLevel" backend-java/src/main/java
grep -R "IssueSummaryEntity" backend-java/src/main/java || true
grep -R "issue_summary" backend-java/src/main/java backend-java/src/main/resources || true
```

---

## 13. Public Issue ID Validation

Round 06 public issue IDs were string-like:

```text
ISSUE-1
ISSUE-2
ISSUE-3
```

Round 07 database IDs are numeric.

Validate implementation does not expose internal numeric issue DB ids.

Expected:

```text
ReviewIssueEntity.id = internal DB id
ReviewIssueEntity.issueKey = public API id
ReviewIssueResponse.id = issueKey
```

If API now returns issue ids like:

```json
"id": 1
```

for issues, this is a contract regression unless intentionally documented and accepted.

Codex should treat accidental DB issue id exposure as blocking.

---

## 14. Frontend Runtime Smoke Validation

If practical, run frontend:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Browser smoke check:

1. backend health display works；
2. create task works；
3. list displays task；
4. detail displays task；
5. summary panel displays backend `issueSummary`；
6. issue cards display source badge；
7. issue cards display status badge；
8. loading/error/empty states not obviously broken。

If browser validation is not feasible, document why and rely on tests/build/curl validation.

---

## 15. README Validation

Inspect root README and backend/frontend README if present.

Validate documentation states:

1. Round 07 introduces database persistence v1；
2. `ReviewTask` is persisted；
3. `ReviewIssue` is persisted；
4. data no longer depends purely on in-memory storage；
5. local runtime persistence uses file-based H2；
6. tests use in-memory H2 or isolated test DB；
7. `issueSummary` is computed from persisted issues；
8. `riskLevel` is derived from `issueSummary`；
9. issue source remains `MOCK`；
10. issue status remains `OPEN`；
11. no GitHub API is called；
12. no repository is cloned；
13. no Semgrep is executed；
14. no LLM / ai-service is called；
15. status update workflow is not implemented；
16. false-positive workflow is not implemented；
17. current vs planned architecture boundary is clear；
18. root README “Project Overview” is clarified as future/planned product vision or equivalent。

README must not overclaim that real code review is implemented.

Expected current implementation statement:

```text
Database persistence exists, but real code review does not yet exist.
```

---

## 16. Scope Creep Validation

Run code/documentation inspection for forbidden scope.

Search for suspicious additions:

```bash
grep -R "GitHub" backend-java/src/main/java frontend/src README.md || true
grep -R "Semgrep" backend-java/src/main/java frontend/src README.md || true
grep -R "OpenAI\|Anthropic\|Gemini\|LLM" backend-java/src/main/java frontend/src README.md || true
grep -R "ai-service" backend-java/src/main/java frontend/src README.md || true
grep -R "SecurityFilterChain\|Spring Security" backend-java/src/main/java backend-java/pom.xml || true
```

The README may mention these as non-goals, which is acceptable.

Backend/frontend implementation must not introduce real calls or clients for these systems.

Blocking scope creep:

1. GitHub client added；
2. repo clone logic added；
3. Semgrep process execution added；
4. LLM client added；
5. ai-service client added；
6. agent planner added；
7. status update endpoint added；
8. auth/security added；
9. frontend redesign/component library added。

---

## 17. EAGER Relationship Review

Cursor handoff says `ReviewTaskEntity` uses:

```text
@OneToMany(EAGER)
```

This is not automatically blocking for Round 07, but Codex must inspect usage.

Validate:

1. response assembly does not accidentally serialize JPA entities；
2. list/detail work reliably；
3. no lazy loading exception；
4. no unexpected recursive serialization；
5. no duplicated issue loading problems that affect correctness。

If implementation explicitly loads issues via `ReviewIssueRepository`, then `EAGER` may be redundant.

Recommended Codex note if applicable:

```text
Non-blocking note: @OneToMany(EAGER) is acceptable for Round 07 mock scale, but future rounds should consider LAZY + explicit repository fetch/fetch join to avoid list-query overhead.
```

Treat as blocking only if it causes correctness, serialization, or test/runtime failures.

---

## 18. Blocking Failure Conditions

Codex should fail validation if any of the following are found:

1. backend tests fail；
2. frontend typecheck/build/tests fail；
3. runtime create/list/detail fail；
4. backend restart loses created task；
5. detail after restart loses issues；
6. API endpoint paths changed；
7. `ApiResponse<T>` wrapper changed；
8. create request shape changed；
9. `ReviewTaskResponse.issueSummary` missing；
10. `ReviewTaskResponse.riskLevel` missing；
11. `riskLevel != issueSummary.riskLevel`；
12. `ReviewIssueResponse.source` missing；
13. `ReviewIssueResponse.status` missing；
14. persisted issue source is not `MOCK`；
15. persisted issue status is not `OPEN`；
16. `ReviewIssueResponse.id` exposes internal DB numeric id accidentally；
17. `issueSummary` persisted as independently mutable table/entity；
18. `riskLevel` persisted and used as source of truth in a way that can drift；
19. in-memory map remains source of truth；
20. runtime database uses only in-memory H2；
21. GitHub/Semgrep/LLM/ai-service/workflow scope creep introduced；
22. README materially overclaims current capability。

---

## 19. Acceptable Non-blocking Notes

These can be recorded as notes without failing Round 07:

1. H2 file database is local/dev only；
2. `ddl-auto=update` is acceptable for this round but not production hardening；
3. `@OneToMany(EAGER)` is acceptable at current mock scale but should be revisited；
4. list endpoint may be heavy in future because it includes issues；
5. no migration tool yet；
6. no production DB yet；
7. no real review pipeline yet；
8. no issue status workflow yet；
9. no auth/multi-user ownership yet。

---

## 20. Codex Handoff Output

After validation, create:

```text
tasks/round-07/02-codex-database-persistence-v1-validation-handoff.md
```

The handoff must include:

1. validation summary；
2. repository inspection summary；
3. dependency/config verification；
4. entity/repository verification；
5. service layer verification；
6. API contract verification；
7. backend test command and result；
8. frontend command results；
9. runtime curl validation；
10. restart persistence validation；
11. issueSummary/riskLevel invariant validation；
12. public issue id validation；
13. scope creep validation；
14. README validation；
15. any patches made by Codex；
16. blocking issues, if any；
17. non-blocking notes；
18. final verdict。

Use one of these verdicts:

```text
Codex Verdict: ROUND_07_VALIDATED_READY_FOR_QODER_REVIEW
```

or:

```text
Codex Verdict: ROUND_07_VALIDATION_FAILED
```

If failed, list exact blockers and recommended fixes.

---

## 21. Recommended Validation Sequence

Follow this order:

1. inspect Cursor changed files；
2. inspect dependencies；
3. inspect database config；
4. inspect entities；
5. inspect repositories；
6. inspect service mapping and summary logic；
7. search for in-memory storage remnants；
8. search for forbidden scope creep；
9. run backend tests；
10. run frontend typecheck/build/tests；
11. start backend；
12. run health/create/list/detail curl validation；
13. restart backend；
14. verify persistence survives restart；
15. inspect README；
16. write Codex handoff。

---

## 22. Final Instruction

The essence of this validation:

```text
Do not ask whether database code exists.
Prove that it is the source of truth.
Prove that restart persistence works.
Prove that the Round 06 contract is still intact.
Prove that no real review intelligence was introduced.
```

If the implementation satisfies these points, approve it for Qoder independent review.