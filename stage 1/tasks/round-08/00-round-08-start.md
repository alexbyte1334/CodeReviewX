# tasks/round-08/00-round-08-start.md

# Round 08 Start: Review Pipeline Orchestrator Skeleton

## 1. Round Metadata

- Project: CodeReviewX
- Round: Round 08
- Theme: Review Pipeline Orchestrator Skeleton
- Task Type: Architecture-guided Implementation Round
- Primary Goal: 在不破坏现有 API / 前端行为 / 数据持久化的前提下，引入 Review Pipeline / Provider 抽象，为接下来 3–5 个 round 内完成完整项目交付建立最短可行路径
- Previous Round:
  - Round 07: Database Persistence v1
- Previous Final Verdict:
  - `ROUND_07_ACCEPTED_WITH_NOTES`
- First Task To Generate:
  - `tasks/round-08/01-cursor-review-pipeline-orchestrator-skeleton.md`

---

## 2. Strategic Change: Project Must Finish in 3–5 More Rounds

从 Round 08 开始，项目节奏需要从“稳步架构铺垫”调整为：

```text
3–5 个 round 内完成完整可演示项目。
```

完整项目不仅包括 agent / AI review 能力，还包括最终前端设计样式、交互体验、文档、演示流程和可验证闭环。

因此后续 round 不再允许无限期拆分基础设施工作。

Round 08 开始必须服务于最终交付目标：

```text
Build the shortest stable path from current persisted mock review app
to a complete demonstrable AI code review agent product.
```

---

## 3. Current Project State

CodeReviewX 当前已完成到 Round 07。

系统已经具备：

1. Spring Boot backend；
2. React + TypeScript + Vite frontend；
3. health endpoint；
4. review task create/list/detail API；
5. frontend create/list/detail flow；
6. loading/error/empty states；
7. typed `ReviewTaskResponse`；
8. typed `ReviewIssueResponse`；
9. typed `IssueSummaryResponse`；
10. typed `IssueSeverity`；
11. typed `IssueCategory`；
12. typed `IssueSource`；
13. typed `IssueStatus`；
14. typed `RiskLevel` including `NONE`；
15. backend-computed issue summary；
16. backend as authoritative source for risk summary；
17. frontend summary panel；
18. issue cards；
19. severity/category/source/status badges；
20. deterministic mock review issues；
21. `ReviewTask` persistence；
22. `ReviewIssue` persistence；
23. file-based H2 runtime database；
24. in-memory H2 test database；
25. restart persistence verification；
26. root/backend README updated；
27. backend tests passing；
28. frontend typecheck/build/tests passing；
29. runtime curl validation passing；
30. browser smoke validation passing。

Round 07 final verdict:

```text
Qoder Verdict: ROUND_07_ACCEPTED_WITH_NOTES
```

Round 07 is closed.

---

## 4. Current Limitation

The current system is still not a real AI agent.

Current flow:

```text
User submits repoUrl + prNumber
  -> ReviewTaskService creates task
  -> ReviewTaskService generates deterministic mock issues
  -> issues are persisted
  -> issueSummary is computed
  -> frontend displays result
```

Current missing pieces:

1. no review pipeline abstraction；
2. no review provider interface；
3. no `ReviewContext`；
4. no standardized `ReviewFinding` internal model；
5. no tool/provider result model；
6. no GitHub PR input contract；
7. no diff/file context model；
8. no LLM provider；
9. no static analysis provider；
10. no agent planner；
11. no tool execution trace；
12. no issue lifecycle workflow；
13. no final frontend visual design polish；
14. no final demo-ready experience。

---

## 5. Round 08 Strategic Purpose

Round 08 must introduce the core agent architecture boundary without yet overreaching into real integrations.

The goal is to transform the backend from:

```text
ReviewTaskService directly generates mock issues
```

into:

```text
ReviewTaskService
  -> ReviewPipelineService
      -> ReviewProvider
          -> MockReviewProvider
      -> ReviewFinding[]
  -> map findings to ReviewIssueEntity
  -> persist task/issues
```

This is the first real step from “AI review app shell” to “agent-capable review system”.

---

## 6. Round 08 Primary Goal

Implement:

```text
Review Pipeline Orchestrator Skeleton
```

with the following minimum architecture:

```text
ReviewPipelineService
ReviewContext
ReviewFinding
ReviewToolResult or ReviewProviderResult
ReviewProvider interface
MockReviewProvider
```

Round 08 must preserve current external behavior:

```text
Same API.
Same request shape.
Same response shape.
Same frontend behavior.
Same deterministic 3 mock issues.
Same persistence behavior.
Same issueSummary/riskLevel invariant.
```

Internally, however, mock issue generation should move behind the new pipeline/provider abstraction.

---

## 7. Delivery Compression Requirement

Because the project must finish within the next 3–5 rounds, Round 08 must avoid both extremes:

### Do Not Underbuild

Do not merely rename existing mock generation methods.

Round 08 must create a real extensibility seam for future providers:

```text
MockReviewProvider now
AIReviewProvider later
StaticAnalysisProvider later
GitHubContextProvider later
```

### Do Not Overbuild

Do not create a giant generic agent framework.

Avoid:

1. complex planner；
2. graph execution engine；
3. async queue；
4. distributed workflow；
5. multi-agent architecture；
6. streaming；
7. retries/cost accounting；
8. tool registry UI；
9. plugin marketplace；
10. unnecessary abstractions that slow delivery。

The correct target is:

```text
Minimal but real review pipeline boundary.
```

---

## 8. 3–5 Round Completion Plan

The following plan must guide all future task design.

### Preferred 5-Round Plan

#### Round 08: Review Pipeline Orchestrator Skeleton

Goal:

```text
Introduce pipeline/provider architecture while preserving current behavior.
```

Scope:

```text
ReviewPipelineService
ReviewContext
ReviewFinding
ReviewProvider
MockReviewProvider
mapping ReviewFinding -> ReviewIssueEntity
tests
README update
```

No real GitHub/Semgrep/LLM yet.

---

#### Round 09: AI Review Provider v1

Goal:

```text
Introduce AI provider boundary and optionally real LLM call if environment/config allows.
```

Scope:

```text
AIReviewProvider interface/implementation
LLM adapter boundary
prompt template
structured output parser
fallback to mock provider when API key absent
provider source = LLM when real provider used
provider source = MOCK when fallback used
```

Recommended stance:

```text
Implement LLM integration behind configuration.
Do not make API key mandatory for tests.
Do not break local demo if LLM is unavailable.
```

---

#### Round 10: PR Input / Review Context v1

Goal:

```text
Introduce stable review input model for PR metadata and changed files.
```

Scope options:

```text
ReviewContext enrichment
PullRequestContext
ChangedFile
FileDiff
mock PR diff input
manual pasted diff input if faster
optional GitHub API only if scoped and safe
```

This round should make the agent review less fake by giving it a realistic review context shape.

---

#### Round 11: Frontend Final UX / Visual Design Polish

Goal:

```text
Turn the current functional UI into a demo-ready product interface.
```

Scope:

```text
visual hierarchy
layout polish
dashboard-like review page
risk summary redesign
issue card polish
empty states
loading states
error states
responsive basics
demo labels
current/planned capability clarity
```

No heavy UI library migration unless absolutely necessary.

---

#### Round 12: Final Integration / Release Candidate

Goal:

```text
End-to-end demo hardening and final documentation.
```

Scope:

```text
full local run instructions
demo script
seed/demo data
final README
known limitations
architecture diagram in README
test pass
runtime pass
frontend smoke pass
final acceptance review
```

---

### Compressed 3-Round Plan

If the project must finish in only 3 more rounds:

```text
Round 08:
Pipeline Orchestrator + Provider abstraction + ReviewContext + MockProvider

Round 09:
AI Provider + structured output + PR/diff context v1 + backend finalization

Round 10:
Frontend final style + demo hardening + release candidate
```

This is riskier but feasible if scope is tightly controlled.

---

## 9. Round 08 Non-goals

Round 08 must not implement:

1. real GitHub API call；
2. repository clone；
3. real PR diff ingestion；
4. Semgrep execution；
5. real LLM call as mandatory behavior；
6. ai-service integration；
7. full agent planner；
8. multi-agent workflow；
9. queue/job worker；
10. async status progression；
11. issue resolve workflow；
12. false-positive workflow；
13. human reviewer comments；
14. auth；
15. organization/team model；
16. dashboard analytics；
17. final frontend redesign；
18. component library migration；
19. chart library；
20. production database hardening；
21. Flyway/Liquibase migration；
22. deployment/CI/CD。

Round 08 is architecture-enabling, not feature explosion.

---

## 10. Strict Scope Boundaries

### 10.1 Allowed

Round 08 may add:

1. `ReviewPipelineService`；
2. `ReviewContext`；
3. `ReviewFinding`；
4. `ReviewProvider`；
5. `ReviewProviderResult` or `ReviewToolResult`；
6. `MockReviewProvider`；
7. mapper from `ReviewFinding` to `ReviewIssueEntity`；
8. provider-level tests；
9. pipeline service tests；
10. service integration tests；
11. README architecture update；
12. small package restructuring if necessary；
13. internal source/provider metadata if it does not break API；
14. internal trace fields only if minimal and not exposed prematurely。

### 10.2 Forbidden

Round 08 must not:

1. remove current endpoints；
2. alter `ApiResponse<T>`；
3. alter create request shape；
4. remove response fields；
5. remove `issueSummary`；
6. remove `riskLevel`；
7. change `riskLevel == issueSummary.riskLevel` invariant；
8. expose JPA entities；
9. expose internal DB issue ids；
10. replace persistence layer；
11. break restart persistence；
12. introduce required external services；
13. require API keys；
14. require Docker；
15. require network access；
16. redesign frontend；
17. add large dependencies。

---

## 11. Recommended Backend Package Structure

Suggested package:

```text
backend-java/src/main/java/com/codereviewx/backend/review/pipeline
```

Inside:

```text
ReviewPipelineService.java
ReviewContext.java
ReviewFinding.java
ReviewProvider.java
ReviewProviderResult.java
```

Suggested provider package:

```text
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider
```

Inside:

```text
MockReviewProvider.java
```

Alternative package names are acceptable if consistent.

---

## 12. Proposed Internal Models

### 12.1 ReviewContext

Purpose:

```text
Represent the input context for a review pipeline run.
```

Minimum fields:

```text
repoUrl
prNumber
taskId optional
```

Recommended fields:

```text
Long taskId
String repoUrl
Integer prNumber
LocalDateTime createdAt
```

Optional future-ready fields, only if lightweight:

```text
String providerMode
Map<String, Object> metadata
```

Avoid overbuilding.

Do not introduce real GitHub payloads yet.

---

### 12.2 ReviewFinding

Purpose:

```text
Internal normalized finding emitted by any review provider.
```

Recommended fields:

```text
String issueKey
IssueSeverity severity
IssueCategory category
IssueSource source
IssueStatus status
String filePath
Integer startLine
Integer endLine
String title
String description
String recommendation
```

Important:

```text
ReviewFinding is internal.
ReviewIssueResponse remains API DTO.
ReviewIssueEntity remains persistence entity.
```

This model becomes the bridge between future tools and persisted issues.

---

### 12.3 ReviewProvider

Purpose:

```text
A provider that can analyze a ReviewContext and return normalized findings.
```

Suggested interface:

```java
public interface ReviewProvider {
    ReviewProviderResult review(ReviewContext context);
}
```

or:

```java
public interface ReviewProvider {
    List<ReviewFinding> review(ReviewContext context);
}
```

Preferred:

```text
ReviewProviderResult
```

because it can later hold provider metadata, warnings, duration, raw output, or errors.

Keep it minimal.

---

### 12.4 ReviewProviderResult

Recommended fields:

```text
List<ReviewFinding> findings
String providerName
boolean successful
String message optional
```

Do not expose this through current API yet.

Do not persist it unless explicitly needed.

---

### 12.5 MockReviewProvider

Purpose:

```text
Current deterministic mock issue generation moves here.
```

Requirements:

1. returns exactly 3 findings；
2. one HIGH；
3. one MEDIUM；
4. one LOW；
5. source = MOCK；
6. status = OPEN；
7. issueKey = ISSUE-1 / ISSUE-2 / ISSUE-3；
8. deterministic output；
9. no GitHub；
10. no Semgrep；
11. no LLM；
12. no network call。

---

## 13. ReviewTaskService Changes

Current responsibility should shift from:

```text
ReviewTaskService generates mock issues directly.
```

to:

```text
ReviewTaskService creates task
ReviewTaskService builds ReviewContext
ReviewTaskService calls ReviewPipelineService
ReviewTaskService persists returned findings as ReviewIssueEntity
ReviewTaskService returns same ReviewTaskResponse
```

Expected flow:

```text
POST /api/review-tasks
  -> ReviewTaskService.createTask(request)
      -> save ReviewTaskEntity
      -> build ReviewContext
      -> reviewPipelineService.run(context)
      -> get ReviewFinding list
      -> map ReviewFinding to ReviewIssueEntity
      -> save issues
      -> load/marshal response
```

The API response must remain the same as Round 07.

---

## 14. Persistence Rules

Round 08 must preserve Round 07 persistence architecture.

Rules:

1. `ReviewTaskEntity` remains persisted；
2. `ReviewIssueEntity` remains persisted；
3. `issueSummary` remains computed；
4. `riskLevel` remains derived；
5. no `IssueSummaryEntity`；
6. no independent `riskLevel` column；
7. public issue id remains `issueKey`；
8. internal DB issue id remains hidden；
9. restart persistence must remain valid；
10. no schema bloat unless justified。

If new fields are added, they must be minimal and documented.

Recommended:

```text
Do not add new database columns in Round 08 unless necessary.
```

The pipeline can operate with existing persisted issue fields.

---

## 15. API Contract Rules

Round 08 must preserve:

```text
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

Create request remains:

```json
{
  "repoUrl": "https://github.com/example/demo",
  "prNumber": 8
}
```

Response wrapper remains:

```json
{
  "success": true,
  "message": "OK",
  "data": {}
}
```

`ReviewTaskResponse` must still contain:

```text
id
repoUrl
prNumber
status
riskLevel
summary
errorMessage
issues
issueSummary
createdAt
updatedAt
```

`ReviewIssueResponse` must still contain:

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

`IssueSummaryResponse` must still contain:

```text
totalIssues
highCount
mediumCount
lowCount
riskLevel
```

Invariant remains:

```text
ReviewTaskResponse.riskLevel == ReviewTaskResponse.issueSummary.riskLevel
```

---

## 16. Frontend Requirements

Round 08 should require little to no frontend change.

Expected:

1. frontend still works；
2. no visual redesign yet；
3. no component library；
4. no charts；
5. no routing overhaul；
6. no state management library；
7. tests still pass；
8. issue cards still render；
9. source/status badges still render；
10. summary panel still uses backend `issueSummary`。

Allowed frontend changes:

1. update copy from “mock result” to “pipeline mock provider result” only if useful；
2. update test fixtures if backend response timestamps/order differ；
3. small documentation/comment adjustments。

The full frontend final style polish is reserved for a later round, likely Round 11 or compressed Round 10.

---

## 17. Backend Tests Required

Add or update backend tests to cover:

1. `MockReviewProvider` returns exactly 3 findings；
2. `MockReviewProvider` returns one HIGH, one MEDIUM, one LOW；
3. `MockReviewProvider` returns `source=MOCK`；
4. `MockReviewProvider` returns `status=OPEN`；
5. `MockReviewProvider` returns public issue ids `ISSUE-1/2/3`；
6. `ReviewPipelineService` invokes provider and returns findings/result；
7. `ReviewTaskService.createTask` uses pipeline result；
8. `ReviewTaskService.createTask` persists findings as issues；
9. `ReviewTaskService.getTask` still returns persisted issues；
10. `issueSummary` still computed correctly；
11. `riskLevel == issueSummary.riskLevel`；
12. API wrapper unchanged；
13. missing task behavior unchanged；
14. no database regression；
15. restart persistence still valid by runtime verification。

Existing tests must continue to pass.

---

## 18. Frontend Tests Required

Run:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

If script names differ, inspect `package.json`.

Expected:

1. typecheck passes；
2. build passes；
3. all existing tests pass；
4. no frontend behavior regression。

---

## 19. Runtime Verification Requirements

Backend:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

or:

```bash
cd backend-java
mvn spring-boot:run
```

Health:

```bash
curl http://localhost:8080/api/health
```

Create:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/round-08-pipeline-demo",
    "prNumber": 8
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

List:

```bash
curl http://localhost:8080/api/review-tasks
```

Detail:

```bash
curl http://localhost:8080/api/review-tasks/{id}
```

Restart persistence smoke:

```text
create task
record id
restart backend
get detail by id
confirm task/issues still exist
```

Browser smoke if feasible:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Confirm:

1. health UP；
2. create task works；
3. list updates；
4. detail renders；
5. summary and issue cards render；
6. source/status badges render。

---

## 20. README Update Requirements

Update README and backend README as needed.

Must document:

1. Round 08 introduces Review Pipeline Orchestrator Skeleton；
2. current provider is `MockReviewProvider`；
3. current pipeline still produces deterministic mock findings；
4. findings are persisted as `ReviewIssue` records；
5. `issueSummary` is still computed from persisted issues；
6. `riskLevel` is still derived from `issueSummary`；
7. current system still does not call GitHub；
8. current system still does not clone repository；
9. current system still does not run Semgrep；
10. current system still does not call LLM；
11. current system still does not call ai-service；
12. pipeline/provider abstraction is preparation for near-term AI/agent integration；
13. project roadmap now targets completion within the next 3–5 rounds；
14. planned next steps include AI provider and final frontend polish。

Avoid overclaiming.

Correct wording:

```text
Round 08 introduces the internal review pipeline architecture.
The current provider remains deterministic mock-based.
Real AI review is planned for a near-term follow-up round.
```

Incorrect wording:

```text
CodeReviewX now performs real AI code review.
```

---

## 21. Required Commands

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
npm run typecheck
npm run build
npm test -- --run
```

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

## 22. Acceptance Criteria for Round 08

### Pipeline Architecture

- [ ] `ReviewPipelineService` introduced；
- [ ] `ReviewContext` introduced；
- [ ] `ReviewFinding` introduced；
- [ ] `ReviewProvider` introduced；
- [ ] `ReviewProviderResult` or equivalent introduced；
- [ ] `MockReviewProvider` introduced；
- [ ] mock issue generation moved out of `ReviewTaskService`；
- [ ] `ReviewTaskService` calls pipeline；
- [ ] pipeline calls provider；
- [ ] provider returns normalized findings；
- [ ] findings are mapped to persisted `ReviewIssueEntity` records；
- [ ] architecture can support future AI/Semgrep/GitHub providers。

### Behavior Preservation

- [ ] create task still returns 3 issues；
- [ ] issue severities remain HIGH/MEDIUM/LOW；
- [ ] source remains `MOCK`；
- [ ] status remains `OPEN`；
- [ ] issue ids remain `ISSUE-1/2/3`；
- [ ] summary remains total=3/high=1/medium=1/low=1；
- [ ] risk remains HIGH；
- [ ] `riskLevel == issueSummary.riskLevel`；
- [ ] list/detail behavior unchanged；
- [ ] missing task behavior unchanged。

### Persistence

- [ ] `ReviewTask` persistence preserved；
- [ ] `ReviewIssue` persistence preserved；
- [ ] restart persistence preserved；
- [ ] no `IssueSummaryEntity`；
- [ ] no independent `riskLevel` source of truth；
- [ ] public issue id remains separate from DB id。

### API Contract

- [ ] endpoint paths unchanged；
- [ ] create request unchanged；
- [ ] `ApiResponse<T>` unchanged；
- [ ] `ReviewTaskResponse` fields preserved；
- [ ] `ReviewIssueResponse` fields preserved；
- [ ] `IssueSummaryResponse` fields preserved。

### Frontend

- [ ] existing UI works；
- [ ] typecheck passes；
- [ ] build passes；
- [ ] tests pass；
- [ ] no redesign introduced；
- [ ] no new frontend state library；
- [ ] source/status badges still render；
- [ ] summary panel still renders。

### Scope

- [ ] no GitHub API；
- [ ] no repository clone；
- [ ] no Semgrep execution；
- [ ] no LLM call required；
- [ ] no ai-service；
- [ ] no full agent planner；
- [ ] no async queue；
- [ ] no status workflow；
- [ ] no auth；
- [ ] no frontend redesign；
- [ ] no production DB hardening。

### Documentation

- [ ] README documents Round 08 pipeline；
- [ ] README documents current `MockReviewProvider`；
- [ ] README clarifies real AI review is not yet implemented；
- [ ] README includes 3–5 round completion direction；
- [ ] backend README updated if needed；
- [ ] current vs planned boundary remains clear。

---

## 23. Known Risks to Track

### 23.1 Over-abstraction Risk

Because Round 08 introduces pipeline/provider architecture, implementation may overbuild.

Avoid:

```text
generic workflow engine
multi-agent planner
tool marketplace
complex metadata system
provider registry UI
async execution framework
```

Target:

```text
one pipeline service
one provider interface
one mock provider
one normalized finding model
```

---

### 23.2 Under-abstraction Risk

Implementation may only move mock generation into another method without real provider boundary.

That is insufficient.

Must have a clear seam where future providers can plug in:

```text
ReviewProvider.review(ReviewContext)
```

or equivalent.

---

### 23.3 API Drift Risk

Internal architecture changes must not leak to frontend.

Do not expose:

```text
ReviewProviderResult
ReviewContext
ReviewFinding
provider internals
trace internals
```

through the existing API unless explicitly scoped later.

---

### 23.4 Delivery Compression Risk

Because the user requires completion in 3–5 rounds, Round 08 must avoid unnecessary detours.

Every new abstraction should answer:

```text
Does this directly help us ship AI/provider/frontend-final within 3–5 rounds?
```

If not, defer it.

---

## 24. Suggested Round 08 Task Sequence

Continue Cursor → Codex → Qoder.

### 24.1 Cursor Implementation

Generate:

```text
tasks/round-08/01-cursor-review-pipeline-orchestrator-skeleton.md
```

Responsibilities:

1. inspect current Round 07 code；
2. create pipeline package；
3. create `ReviewContext`；
4. create `ReviewFinding`；
5. create `ReviewProvider`；
6. create `ReviewProviderResult` or equivalent；
7. create `MockReviewProvider`；
8. create `ReviewPipelineService`；
9. move deterministic mock generation into `MockReviewProvider`；
10. update `ReviewTaskService` to call pipeline；
11. preserve persistence behavior；
12. preserve API contract；
13. add/update backend tests；
14. run backend tests；
15. run frontend validations；
16. runtime verify；
17. update README；
18. output Cursor handoff。

### 24.2 Codex Validation

Generate:

```text
tasks/round-08/02-codex-review-pipeline-orchestrator-validation.md
```

Responsibilities:

1. independently inspect architecture；
2. verify pipeline/provider seam；
3. verify mock generation moved out of `ReviewTaskService`；
4. verify behavior unchanged；
5. verify persistence unchanged；
6. verify API contract unchanged；
7. run backend/frontend tests；
8. runtime verify；
9. scope creep check；
10. README check；
11. output Codex handoff。

### 24.3 Qoder Independent Review

Generate:

```text
tasks/round-08/03-qoder-review-pipeline-orchestrator-independent-review.md
```

Responsibilities:

1. independently review pipeline architecture；
2. judge if abstraction is sufficient for AI provider in Round 09；
3. judge if abstraction is not overbuilt；
4. verify behavior stability；
5. verify tests/runtime evidence；
6. decide if Round 08 can close；
7. recommend exact Round 09 direction under 3–5 round completion constraint；
8. output Qoder handoff。

---

## 25. Recommended Round 09 Direction

If Round 08 succeeds, Round 09 should move directly toward:

```text
AI Review Provider v1
```

Preferred Round 09 scope:

```text
AIReviewProvider
LLM adapter boundary
prompt template
structured review finding output
safe parser
configuration-based enable/disable
fallback MockReviewProvider
no API key required for tests
```

Round 09 should aim to make the system capable of real or near-real AI review while preserving local testability.

If real LLM integration is not feasible in the environment, Round 09 must still implement the adapter boundary and deterministic fake AI provider so the final demo path remains unblocked.

---

## 26. Recommended Final Delivery Path

Target final state within 3–5 rounds:

```text
User opens frontend
  -> sees polished CodeReviewX UI
  -> enters repo/PR or demo input
  -> creates review task
  -> backend runs review pipeline
  -> provider emits findings
  -> findings persist as issues
  -> summary/risk computed
  -> frontend displays professional review result
  -> README explains current capabilities and limitations
```

Minimum acceptable final project:

```text
A polished, demo-ready AI code review agent prototype
with stable backend architecture, persisted tasks/issues,
provider-based review pipeline, optional or simulated AI provider,
and final frontend visual design.
```

---

## 27. Final Instruction for Round 08

Round 08 must make CodeReviewX agent-ready.

The essential instruction:

```text
Introduce the review pipeline/provider boundary now,
because the project must finish in 3–5 more rounds.
Preserve all current product behavior.
Do not overbuild.
Do not delay agent readiness.
```

First task to generate:

```text
tasks/round-08/01-cursor-review-pipeline-orchestrator-skeleton.md
```