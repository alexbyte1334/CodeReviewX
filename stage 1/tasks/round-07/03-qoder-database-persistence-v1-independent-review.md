# tasks/round-07/03-qoder-database-persistence-v1-independent-review.md

# Qoder Task: Round 07 Database Persistence v1 Independent Review

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 07
- Task: 03
- Owner: Qoder
- Theme: Database Persistence v1
- Task Type: Independent Architecture Review / Round Closure Review
- Previous Task 01: `tasks/round-07/01-cursor-database-persistence-v1.md`
- Cursor Handoff: `tasks/round-07/01-cursor-database-persistence-v1-handoff.md`
- Previous Task 02: `tasks/round-07/02-codex-database-persistence-v1-validation.md`
- Codex Handoff: `tasks/round-07/02-codex-database-persistence-v1-validation-handoff.md`
- Expected Output: 判断 Round 07 是否可以关闭，并推荐 Round 08 方向

---

## 2. Review Context

Round 07 的目标是：

```text id="n6fx48"
Database Persistence v1
```

核心原则：

```text id="5l905d"
Persist the stable Round 06 contract without changing product behavior.
```

本轮不应引入真实代码审查能力，只应完成存储层从 in-memory 到 database persistence 的迁移。

Cursor 已完成实现并输出 handoff。Cursor 声称：

1. `ReviewTask` 已持久化；
2. `ReviewIssue` 已持久化；
3. runtime 使用 file-based H2；
4. tests 使用 in-memory H2；
5. `issueSummary` 不持久化；
6. `riskLevel` 不持久化；
7. `riskLevel` 从 `issueSummary` 派生；
8. `ReviewIssueResponse.id` 使用 `issueKey`，不暴露 DB internal id；
9. Round 06 API contract 保持不变；
10. backend/frontend tests 通过。

Codex 已完成独立验证并输出 handoff。Codex verdict:

```text id="x2txm8"
Codex Verdict: ROUND_07_VALIDATED_READY_FOR_QODER_REVIEW
```

Codex 还进行了少量 validation-scope cleanup：

1. 更新 stale 的 `backend-java/README.md`；
2. 更新 `application-local.yml` stale comment；
3. 删除未使用的旧 in-memory model file: `ReviewTask.java`。

Qoder 本轮任务不是继续实现，而是独立审查：

```text id="s6plm1"
当前 Round 07 是否满足关闭条件？
当前 persistence architecture 是否足以作为 Round 08 的基础？
是否存在需要返工的 blocking issue？
```

---

## 3. Inputs to Review

请重点阅读：

```text id="3u3b7a"
tasks/round-07/00-round-07-start.md
tasks/round-07/01-cursor-database-persistence-v1.md
tasks/round-07/01-cursor-database-persistence-v1-handoff.md
tasks/round-07/02-codex-database-persistence-v1-validation.md
tasks/round-07/02-codex-database-persistence-v1-validation-handoff.md
```

同时审查相关代码：

```text id="o8td1v"
backend-java/pom.xml
backend-java/src/main/resources/application.yml
backend-java/src/main/resources/application-local.yml
backend-java/src/test/resources/application.yml
backend-java/src/main/java/com/codereviewx/backend/review/**
backend-java/src/test/java/**
frontend/src/**
README.md
backend-java/README.md
.gitignore
```

---

## 4. Review Goal

Qoder 需要独立判断：

1. Cursor implementation 是否真实满足 Round 07；
2. Codex validation 是否充分可信；
3. Codex patches 是否合理且未改变产品行为；
4. persistence schema 是否简洁、稳定、可演进；
5. `ReviewTask` / `ReviewIssue` 是否已成为 source of truth；
6. `issueSummary` 是否保持 read-time derived；
7. `riskLevel` 是否不会与 `issueSummary.riskLevel` 漂移；
8. public API issue id 是否与 DB internal id 正确分离；
9. Round 06 API contract 是否完整保留；
10. README 是否准确描述 current vs planned；
11. 是否存在 scope creep；
12. 是否可以关闭 Round 07；
13. Round 08 应优先推进哪个方向。

---

## 5. Round 07 Acceptance Criteria to Re-check

### 5.1 Backend Persistence

Re-check:

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

### 5.2 API Contract

Re-check:

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
- [ ] issue status remains `OPEN`；
- [ ] public issue id semantics preserved。

### 5.3 IssueSummary / Risk

Re-check:

- [ ] `issueSummary` computed from persisted issues；
- [ ] `totalIssues` correct；
- [ ] `highCount` correct；
- [ ] `mediumCount` correct；
- [ ] `lowCount` correct；
- [ ] risk rule remains `HIGH > MEDIUM > LOW > NONE`；
- [ ] `riskLevel` derived from same summary；
- [ ] `RiskLevel.NONE` still supported；
- [ ] no independent summary table/entity。

### 5.4 Frontend

Re-check from Codex evidence and optionally by local run:

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

### 5.5 Documentation

Re-check:

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
- [ ] root README Project Overview wording clarified as planned product vision or equivalent。

### 5.6 Scope

Re-check no implementation of:

- [ ] GitHub API；
- [ ] repository clone；
- [ ] real code parsing；
- [ ] Semgrep execution；
- [ ] LLM call；
- [ ] ai-service client；
- [ ] agent planner；
- [ ] tool orchestration；
- [ ] status update API；
- [ ] false-positive workflow；
- [ ] human reviewer workflow；
- [ ] auth；
- [ ] frontend redesign；
- [ ] component library；
- [ ] chart library；
- [ ] complex frontend state management。

---

## 6. Specific Architecture Review Points

### 6.1 Persistence Boundary

Assess whether current persistence boundary is correct:

```text id="f6po3k"
Controller -> Service -> Repository -> Entity
Controller/Frontend must not depend on JPA entities.
DTOs remain API contract.
```

Check:

1. controllers return DTOs / `ApiResponse<T>`；
2. service owns response assembly；
3. repositories do not leak to controllers；
4. frontend has no database-specific knowledge；
5. database ids do not leak except task id if already part of API。

---

### 6.2 Entity Model Stability

Review `ReviewTaskEntity`.

Expected:

```text id="8ulzmq"
id
repoUrl
prNumber
status
summary
errorMessage
createdAt
updatedAt
```

Expected not to exist:

```text id="xvrv3j"
issueSummary
riskLevel as independent source of truth
```

Review `ReviewIssueEntity`.

Expected:

```text id="0kw5zs"
id
reviewTask
issueKey
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

Important:

```text id="y5v7te"
ReviewIssueEntity.id is internal.
ReviewIssueEntity.issueKey is public API issue id.
ReviewIssueResponse.id = issueKey.
```

Assess whether schema is sufficient for future pipeline results without overfitting to mock data.

---

### 6.3 Repository and Query Strategy

Review repository methods:

```text id="zq06kn"
ReviewTaskRepository
ReviewIssueRepository
```

Assess:

1. task ordering is stable；
2. issue ordering is stable；
3. list/detail load persisted issues predictably；
4. no response depends on accidental JPA serialization；
5. no controller returns entity graph directly。

If `@OneToMany(fetch = FetchType.EAGER)` is used, judge whether it is acceptable for Round 07.

Expected conclusion if no correctness issue:

```text id="hjaqdf"
Acceptable for Round 07 mock scale, but future rounds should consider LAZY + explicit fetch / fetch join.
```

Treat EAGER as blocking only if it causes recursive serialization, duplicate/incorrect issue loading, or runtime/test failures.

---

### 6.4 Summary and Risk Invariant

Review code path for:

```text id="sqw9d9"
buildIssueSummary(...)
setRiskLevel(issueSummary.getRiskLevel())
```

Confirm:

1. `issueSummary` is computed from actual persisted issue rows；
2. `riskLevel` is set from the same computed summary；
3. no persisted `riskLevel` column is used as source of truth；
4. no persisted `IssueSummaryEntity` exists；
5. tests/runtime evidence cover invariant。

Blocking if:

```text id="w9s8by"
riskLevel can drift from issueSummary.riskLevel
```

---

### 6.5 Public Issue ID vs DB ID

Review mapping:

```text id="srf9ji"
ReviewIssueEntity.id       -> internal DB id
ReviewIssueEntity.issueKey -> public API id
ReviewIssueResponse.id     -> issueKey
```

Confirm API returns:

```text id="djyjgn"
ISSUE-1
ISSUE-2
ISSUE-3
```

not numeric DB issue IDs.

This is important because future issue lifecycle operations may need a stable public id distinct from DB id.

---

### 6.6 Runtime Persistence Evidence

Review Codex restart evidence.

Codex claims:

1. backend started with `jdbc:h2:file:./data/codereviewx`；
2. task id `1` created；
3. backend stopped；
4. backend restarted；
5. detail for id `1` returned successfully；
6. 3 issues persisted；
7. source/status persisted；
8. summary/risk invariant remained valid。

Assess whether this evidence is sufficient.

If Qoder has environment access and time, optionally rerun restart verification. But a second rerun is not mandatory if Codex evidence is detailed and credible.

---

### 6.7 Codex Patch Review

Codex made these patches:

1. replaced stale `backend-java/README.md` Round 03 in-memory documentation with Round 07 database persistence documentation；
2. updated `backend-java/src/main/resources/application-local.yml` comments to avoid stale Round 02 wording；
3. removed unused stale in-memory model file:

```text id="ic783z"
backend-java/src/main/java/com/codereviewx/backend/review/model/ReviewTask.java
```

Review whether:

1. patches are within validation-scope cleanup；
2. no behavior changed unexpectedly；
3. no API changed；
4. no product feature changed；
5. cleanup improved consistency。

Expected:

```text id="w6e8ll"
Accept as reasonable validation-scope cleanup if tests still pass.
```

---

## 7. Commands Qoder May Run

Qoder may rely on Codex evidence, but preferably run a targeted subset.

Backend:

```bash id="p8edjb"
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

or:

```bash id="bk3xlq"
cd backend-java
mvn test
```

Frontend:

```bash id="gl8j3f"
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

Runtime optional:

```bash id="0u85wi"
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

Health:

```bash id="hi2y37"
curl http://localhost:8080/api/health
```

Create:

```bash id="x9db5u"
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/round-07-qoder-review",
    "prNumber": 7
  }'
```

List:

```bash id="jm1nlg"
curl http://localhost:8080/api/review-tasks
```

Detail:

```bash id="wa2h0g"
curl http://localhost:8080/api/review-tasks/{id}
```

---

## 8. Blocking Issues

Qoder should block Round 07 closure if any of the following are found:

1. backend tests fail；
2. frontend typecheck/build/tests fail；
3. runtime create/list/detail fail；
4. backend restart loses created task；
5. persisted task loses issues after restart；
6. runtime uses only `jdbc:h2:mem`；
7. API endpoint paths changed；
8. `ApiResponse<T>` wrapper changed；
9. create request shape changed；
10. required `ReviewTaskResponse` fields removed；
11. required `ReviewIssueResponse` fields removed；
12. required `IssueSummaryResponse` fields removed；
13. `ReviewTaskResponse.riskLevel != ReviewTaskResponse.issueSummary.riskLevel`；
14. `ReviewIssueResponse.id` exposes internal numeric DB id accidentally；
15. issue source/status not persisted；
16. persisted issue source is not `MOCK` for mock issues；
17. persisted issue status is not `OPEN` for mock issues；
18. `issueSummary` is persisted as independent mutable entity/table；
19. persisted `riskLevel` is used as source of truth and can drift；
20. in-memory map remains source of truth；
21. JPA entities are returned directly from controller；
22. GitHub/Semgrep/LLM/ai-service/workflow scope creep introduced；
23. README materially overclaims current implementation。

---

## 9. Non-blocking Notes Qoder May Record

Acceptable non-blocking notes:

1. H2 file database is local/dev persistence only, not production database hardening；
2. `ddl-auto=update` is acceptable for Round 07, but migration tooling should be considered later；
3. `@OneToMany(fetch = FetchType.EAGER)` is acceptable at mock scale but should be revisited；
4. list response includes full issues and may become heavy later；
5. `spring.jpa.open-in-view` warning is not currently a correctness issue；
6. no Flyway/Liquibase yet；
7. no production DB yet；
8. no real GitHub/Semgrep/LLM review pipeline yet；
9. no issue lifecycle workflow yet；
10. no auth/multi-user ownership yet。

---

## 10. Round 08 Direction Review

Qoder must recommend one Round 08 direction.

Candidate options:

### Option A: GitHub PR Input Contract v1

Scope:

```text id="s6vnxr"
GitHub connector interface
mock GitHub client
PR metadata DTO
PR diff DTO
no real GitHub call unless explicitly scoped
```

Pros:

1. starts modeling real PR input；
2. prepares future GitHub integration；
3. clarifies review context shape。

Risks:

1. can drift into real GitHub API too early；
2. may couple service too tightly to GitHub；
3. may skip broader review pipeline abstraction。

---

### Option B: Review Pipeline Orchestrator Skeleton

Scope:

```text id="modiri"
ReviewPipelineService
ReviewContext
ReviewFinding
ReviewToolResult
ReviewProvider interface
MockReviewProvider
```

No real LLM/Semgrep/GitHub execution.

Pros:

1. establishes agent/tool architecture boundary；
2. allows future GitHub/Semgrep/LLM to plug in cleanly；
3. keeps current API stable；
4. avoids hardcoding GitHub into service；
5. prepares for multiple review sources；
6. aligns better with agent application engineering。

Risks:

1. less immediately visible than GitHub input；
2. requires careful boundary design；
3. could become over-abstracted if not kept minimal。

---

### Option C: Static Analysis Integration v1

Scope:

```text id="z5rr97"
Semgrep or simple rule-based analyzer
```

Pros:

1. introduces real review signal；
2. visible user value earlier。

Risks:

1. may be premature before pipeline abstraction；
2. may add execution/dependency complexity；
3. may blur current mock contract；
4. may require security/sandboxing decisions。

---

### Recommended Default

Unless Qoder finds strong contrary evidence, recommend:

```text id="s6f261"
Round 08: Review Pipeline Orchestrator Skeleton
```

Reason:

```text id="eujsmj"
Persistence is now stable.
Before adding GitHub/Semgrep/LLM, CodeReviewX needs a clean pipeline abstraction so future providers do not get hardcoded into ReviewTaskService.
```

Round 08 should still avoid real GitHub/Semgrep/LLM calls unless explicitly scoped.

---

## 11. Qoder Handoff Output

After review, create:

```text id="00wkg5"
tasks/round-07/03-qoder-database-persistence-v1-independent-review-handoff.md
```

The handoff must include:

1. independent review summary；
2. files inspected；
3. Cursor handoff assessment；
4. Codex validation assessment；
5. Codex patch assessment；
6. persistence architecture assessment；
7. entity/schema assessment；
8. repository/query assessment；
9. API contract assessment；
10. issueSummary/risk invariant assessment；
11. public issue id assessment；
12. runtime persistence assessment；
13. frontend assessment；
14. documentation assessment；
15. scope assessment；
16. blocking issues；
17. non-blocking notes；
18. final verdict；
19. recommended Round 08 direction；
20. rationale for Round 08 recommendation。

Use one of these verdicts:

```text id="mwqr44"
Qoder Verdict: ROUND_07_ACCEPTED
```

or:

```text id="eaplpw"
Qoder Verdict: ROUND_07_ACCEPTED_WITH_NOTES
```

or:

```text id="n2t7jz"
Qoder Verdict: ROUND_07_REJECTED_BLOCKED
```

Recommended if no blocking issue is found:

```text id="y5v6gc"
Qoder Verdict: ROUND_07_ACCEPTED_WITH_NOTES
```

Use `ACCEPTED_WITH_NOTES` if there are non-blocking architecture notes such as H2 local-only, `ddl-auto=update`, EAGER fetch, or list response weight.

---

## 12. Final Instruction

Qoder should focus on closure quality, not implementation volume.

The decisive question is:

```text id="pyy3v3"
Is Round 07 stable enough to become the persistence foundation for future real review pipeline work?
```

If yes, close Round 07 and recommend Round 08.

If no, identify exact blockers and required remediation before proceeding.