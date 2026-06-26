# tasks/round-08/01-cursor-review-pipeline-orchestrator-skeleton.md

# Round 08 / Task 01: Cursor Implementation — Review Pipeline Orchestrator Skeleton

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 08
- Task: 01
- Role: Cursor Implementation
- Theme: Review Pipeline Orchestrator Skeleton
- Task Type: Architecture-guided backend implementation with behavior preservation
- Expected Output: Code changes, tests, runtime validation, README updates, and handoff document
- Handoff File To Produce:

```text
tasks/round-08/01-cursor-review-pipeline-orchestrator-skeleton-handoff.md
```

---

## 2. Strategic Context

CodeReviewX must now move faster.

The project must reach a complete demo-ready state within the next 3–5 rounds, including:

1. agent / AI review capability；
2. provider-based backend architecture；
3. persisted review tasks and issues；
4. final frontend visual polish；
5. documentation and demo hardening。

Round 08 is the first round that turns the current mock review app into an agent-ready architecture.

Current backend behavior:

```text
ReviewTaskService directly generates deterministic mock issues.
```

Target internal behavior after this task:

```text
ReviewTaskService
  -> ReviewPipelineService
      -> ReviewProvider
          -> MockReviewProvider
      -> ReviewFinding[]
  -> map ReviewFinding to ReviewIssueEntity
  -> persist task/issues
  -> return unchanged API response
```

External behavior must remain unchanged.

---

## 3. Current Project State

The project already has:

1. Spring Boot backend；
2. React + TypeScript + Vite frontend；
3. `/api/health` endpoint；
4. review task create/list/detail API；
5. frontend create/list/detail flow；
6. typed review task / issue / summary DTOs；
7. deterministic 3 mock issues；
8. persisted `ReviewTask`；
9. persisted `ReviewIssue`；
10. file-based H2 runtime database；
11. in-memory H2 test database；
12. restart persistence verification from Round 07；
13. backend tests passing；
14. frontend typecheck/build/tests passing；
15. runtime curl validation passing；
16. browser smoke validation passing。

Round 07 is closed with:

```text
ROUND_07_ACCEPTED_WITH_NOTES
```

---

## 4. Primary Goal

Implement the minimal but real backend review pipeline boundary.

You must introduce:

```text
ReviewPipelineService
ReviewContext
ReviewFinding
ReviewProvider
ReviewProviderResult
MockReviewProvider
```

The current deterministic mock issue generation must be moved behind the provider abstraction.

---

## 5. Non-negotiable Rule

This task must preserve all current external behavior.

Do not break:

```text
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

Do not change:

```text
ApiResponse<T>
ReviewTaskCreateRequest
ReviewTaskResponse
ReviewIssueResponse
IssueSummaryResponse
```

Do not change the frontend contract.

Do not expose pipeline internals through the public API.

---

## 6. Required Backend Architecture

### 6.1 Recommended Package Structure

Create:

```text
backend-java/src/main/java/com/codereviewx/backend/review/pipeline
```

Recommended files:

```text
ReviewPipelineService.java
ReviewContext.java
ReviewFinding.java
ReviewProvider.java
ReviewProviderResult.java
```

Create provider package:

```text
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider
```

Recommended file:

```text
MockReviewProvider.java
```

Alternative package names are acceptable only if they are consistent, readable, and do not blur API / persistence / pipeline boundaries.

---

## 7. Required Internal Models

### 7.1 ReviewContext

Purpose:

```text
Represent the input context for a review pipeline run.
```

Minimum required fields:

```java
Long taskId
String repoUrl
Integer prNumber
LocalDateTime createdAt
```

Acceptable implementation:

- Java class；
- Java record；
- builder pattern if already used in project；
- simple constructor/static factory。

Do not overbuild.

Do not add real GitHub payloads yet.

Do not add repository clone fields yet.

Do not add changed file / diff model yet.

Those belong to a later round.

---

### 7.2 ReviewFinding

Purpose:

```text
Internal normalized finding emitted by any review provider.
```

Required fields:

```java
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

Rules:

1. `ReviewFinding` is internal；
2. it must not replace `ReviewIssueResponse`；
3. it must not replace `ReviewIssueEntity`；
4. it must not be exposed directly through API responses；
5. it must be mappable to the existing persisted issue model。

---

### 7.3 ReviewProvider

Purpose:

```text
A provider that analyzes a ReviewContext and returns normalized findings.
```

Preferred interface:

```java
public interface ReviewProvider {
    ReviewProviderResult review(ReviewContext context);
}
```

Do not add async behavior.

Do not add streaming.

Do not add retries.

Do not add external tool execution.

Do not add registry UI.

---

### 7.4 ReviewProviderResult

Purpose:

```text
Minimal provider execution result wrapper.
```

Required fields:

```java
List<ReviewFinding> findings
String providerName
boolean successful
String message
```

`message` may be nullable/optional/empty.

Rules:

1. do not expose this through public API；
2. do not persist it in Round 08；
3. keep it lightweight；
4. no raw LLM output；
5. no token/cost accounting；
6. no trace graph；
7. no complex error taxonomy。

---

### 7.5 MockReviewProvider

Purpose:

```text
Current deterministic mock issue generation lives here.
```

Requirements:

1. returns exactly 3 findings；
2. one HIGH issue；
3. one MEDIUM issue；
4. one LOW issue；
5. all `source = MOCK`；
6. all `status = OPEN`；
7. public issue ids remain:

```text
ISSUE-1
ISSUE-2
ISSUE-3
```

8. deterministic output；
9. no GitHub API；
10. no repository clone；
11. no Semgrep；
12. no LLM；
13. no network calls；
14. no required API keys；
15. no dependency on frontend changes。

Move the existing mock issue content as-is where possible.

If exact existing titles/descriptions/recommendations exist, preserve them unless there is a compile-time reason not to.

---

## 8. Required ReviewPipelineService

Create a pipeline service responsible for invoking the provider.

Expected behavior:

```text
ReviewPipelineService.run(context)
  -> call ReviewProvider.review(context)
  -> validate/return provider result
```

Acceptable method names:

```java
run
review
execute
runReview
```

Preferred:

```java
public ReviewProviderResult run(ReviewContext context)
```

Rules:

1. pipeline should be injectable as a Spring service；
2. provider should be injectable；
3. use `MockReviewProvider` as the current provider；
4. do not build a provider registry；
5. do not build multi-provider orchestration yet；
6. do not add planner/agent graph yet；
7. do not persist provider result directly；
8. do not expose provider result directly。

This must be a real extensibility seam for future providers, especially:

```text
AIReviewProvider
StaticAnalysisProvider
GitHubContextProvider
```

---

## 9. Required ReviewTaskService Refactor

Refactor current behavior from:

```text
ReviewTaskService directly generates mock issues.
```

To:

```text
ReviewTaskService creates task
ReviewTaskService builds ReviewContext
ReviewTaskService calls ReviewPipelineService
ReviewTaskService maps ReviewFinding to ReviewIssueEntity
ReviewTaskService persists issues
ReviewTaskService returns same ReviewTaskResponse
```

Expected flow:

```text
POST /api/review-tasks
  -> ReviewTaskService.createTask(request)
      -> save ReviewTaskEntity
      -> build ReviewContext
      -> reviewPipelineService.run(context)
      -> extract findings
      -> map findings to ReviewIssueEntity
      -> save issues
      -> compute summary as before
      -> return ReviewTaskResponse as before
```

Important:

1. task should still be persisted；
2. issues should still be persisted；
3. issue summary should still be computed from persisted issues；
4. `riskLevel` should still be derived from `issueSummary`；
5. `riskLevel == issueSummary.riskLevel` must remain true；
6. public issue id remains `issueKey` / existing public id field；
7. internal database issue id must remain hidden。

---

## 10. Persistence Rules

Do not change the persistence model unless absolutely necessary.

Expected:

```text
ReviewTaskEntity remains
ReviewIssueEntity remains
IssueSummaryEntity does not exist
riskLevel is not independently persisted as source of truth
```

Strong preference:

```text
No new database columns in Round 08.
```

Allowed only if already necessary for compatibility:

```text
minimal internal field adjustments
```

Forbidden:

1. new production database；
2. Flyway/Liquibase；
3. queue table；
4. provider result table；
5. raw output table；
6. execution trace table；
7. task status workflow expansion；
8. independent summary/risk persistence。

---

## 11. API Contract Preservation

### 11.1 Create Request Must Remain

```json
{
  "repoUrl": "https://github.com/example/demo",
  "prNumber": 8
}
```

### 11.2 Response Wrapper Must Remain

```json
{
  "success": true,
  "message": "OK",
  "data": {}
}
```

### 11.3 ReviewTaskResponse Must Still Contain

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

### 11.4 ReviewIssueResponse Must Still Contain

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

### 11.5 IssueSummaryResponse Must Still Contain

```text
totalIssues
highCount
mediumCount
lowCount
riskLevel
```

### 11.6 Required Invariant

```text
ReviewTaskResponse.riskLevel == ReviewTaskResponse.issueSummary.riskLevel
```

---

## 12. Frontend Scope

Round 08 should require little to no frontend change.

Allowed:

1. update fixtures only if necessary；
2. update copy only if it does not create extra work；
3. update tests only if timestamps/order require it。

Forbidden:

1. frontend redesign；
2. component library migration；
3. routing overhaul；
4. charts；
5. new state management library；
6. new visual system；
7. final styling pass；
8. dashboard rebuild。

The existing frontend must still work.

---

## 13. Required Backend Tests

Add or update backend tests covering the new architecture.

Minimum required tests:

### 13.1 MockReviewProvider Test

Verify:

1. returns exactly 3 findings；
2. includes one HIGH；
3. includes one MEDIUM；
4. includes one LOW；
5. every finding has `source = MOCK`；
6. every finding has `status = OPEN`；
7. public issue ids are exactly:

```text
ISSUE-1
ISSUE-2
ISSUE-3
```

8. result is successful；
9. provider name is present。

---

### 13.2 ReviewPipelineService Test

Verify:

1. pipeline invokes provider；
2. pipeline returns provider result；
3. findings are preserved；
4. no API DTOs are involved；
5. no JPA entities are exposed from provider/pipeline。

Use a simple mock/fake provider if appropriate.

---

### 13.3 ReviewTaskService Integration Test

Verify:

1. `createTask` uses pipeline result；
2. findings are persisted as issues；
3. created task still returns 3 issues；
4. issue severities are HIGH/MEDIUM/LOW；
5. source is MOCK；
6. status is OPEN；
7. issue ids are `ISSUE-1/2/3`；
8. `issueSummary.totalIssues = 3`；
9. `issueSummary.highCount = 1`；
10. `issueSummary.mediumCount = 1`；
11. `issueSummary.lowCount = 1`；
12. `issueSummary.riskLevel = HIGH`；
13. `riskLevel = HIGH`；
14. `riskLevel == issueSummary.riskLevel`；
15. `getTask` still returns persisted issues；
16. missing task behavior remains unchanged。

---

### 13.4 API Tests

If API/controller tests already exist, update or preserve them.

Verify:

1. POST `/api/review-tasks` contract unchanged；
2. GET `/api/review-tasks` contract unchanged；
3. GET `/api/review-tasks/{id}` contract unchanged；
4. wrapper shape unchanged；
5. no pipeline internals exposed。

---

## 14. Required Commands

Run backend tests:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

If that Java path is unavailable:

```bash
cd backend-java
mvn test
```

Run frontend validation:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

If script names differ, inspect `package.json` and use equivalent commands.

---

## 15. Runtime Verification

Start backend:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

Or:

```bash
cd backend-java
mvn spring-boot:run
```

Health check:

```bash
curl http://localhost:8080/api/health
```

Create review task:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/round-08-pipeline-demo",
    "prNumber": 8
  }'
```

Confirm response:

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
data.issues[*].source=MOCK
data.issues[*].status=OPEN
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
1. create task
2. record returned task id
3. stop backend
4. restart backend
5. GET /api/review-tasks/{id}
6. confirm task still exists
7. confirm issues still exist
8. confirm summary/risk remains correct
```

Frontend smoke if feasible:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Confirm in browser:

1. health shows UP；
2. create task works；
3. list updates；
4. detail page renders；
5. summary panel renders；
6. issue cards render；
7. severity/category/source/status badges render。

---

## 16. README Updates

Update root README and/or backend README as appropriate.

Must document:

1. Round 08 introduces internal Review Pipeline Orchestrator Skeleton；
2. current provider is `MockReviewProvider`；
3. current provider still emits deterministic mock findings；
4. findings are persisted as `ReviewIssue` records；
5. `issueSummary` is computed from persisted issues；
6. `riskLevel` is derived from issue summary；
7. current system still does not call GitHub；
8. current system still does not clone repositories；
9. current system still does not run Semgrep；
10. current system still does not call an LLM；
11. current system still does not call `ai-service`；
12. pipeline/provider abstraction prepares for near-term AI provider integration；
13. roadmap now targets completion within 3–5 rounds；
14. likely next step is AI Review Provider v1。

Use accurate language.

Correct:

```text
Round 08 introduces the internal review pipeline architecture.
The current provider remains deterministic mock-based.
Real AI review is planned for a near-term follow-up round.
```

Incorrect:

```text
CodeReviewX now performs real AI code review.
```

---

## 17. Strict Non-goals

Do not implement:

1. real GitHub API call；
2. repository clone；
3. PR diff ingestion；
4. pasted diff input；
5. Semgrep execution；
6. real LLM call；
7. ai-service integration；
8. agent planner；
9. multi-agent workflow；
10. queue/job worker；
11. async review status progression；
12. issue resolve workflow；
13. false-positive workflow；
14. human reviewer comments；
15. auth；
16. organization/team model；
17. dashboard analytics；
18. final frontend redesign；
19. component library migration；
20. chart library；
21. production database hardening；
22. Flyway/Liquibase；
23. deployment/CI/CD。

This round is architecture-enabling, not feature explosion.

---

## 18. Acceptance Criteria

### 18.1 Pipeline Architecture

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

### 18.2 Behavior Preservation

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

### 18.3 Persistence

- [ ] `ReviewTask` persistence preserved；
- [ ] `ReviewIssue` persistence preserved；
- [ ] restart persistence preserved；
- [ ] no `IssueSummaryEntity`；
- [ ] no independent `riskLevel` source of truth；
- [ ] public issue id remains separate from DB id；
- [ ] internal DB issue id remains hidden。

### 18.4 API Contract

- [ ] endpoint paths unchanged；
- [ ] create request unchanged；
- [ ] `ApiResponse<T>` unchanged；
- [ ] `ReviewTaskResponse` fields preserved；
- [ ] `ReviewIssueResponse` fields preserved；
- [ ] `IssueSummaryResponse` fields preserved；
- [ ] pipeline internals not exposed。

### 18.5 Frontend

- [ ] existing UI works；
- [ ] typecheck passes；
- [ ] build passes；
- [ ] tests pass；
- [ ] no redesign introduced；
- [ ] no new frontend state library；
- [ ] source/status badges still render；
- [ ] summary panel still renders。

### 18.6 Scope Control

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

### 18.7 Documentation

- [ ] README documents Round 08 pipeline；
- [ ] README documents current `MockReviewProvider`；
- [ ] README clarifies real AI review is not yet implemented；
- [ ] README includes 3–5 round completion direction；
- [ ] backend README updated if needed；
- [ ] current vs planned boundary remains clear。

---

## 19. Engineering Guidance

### 19.1 Avoid Over-abstraction

Do not build:

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

### 19.2 Avoid Under-abstraction

Do not merely rename the current mock generation method.

This is insufficient:

```text
ReviewTaskService -> MockIssueGenerator.generate()
```

This is required:

```text
ReviewTaskService
  -> ReviewPipelineService
      -> ReviewProvider.review(ReviewContext)
          -> ReviewProviderResult
```

The future AI provider must be able to replace or extend the current mock provider with minimal changes.

---

### 19.3 Preserve Product Behavior

The user-facing product should look and behave the same after this task.

The value of this round is internal architecture readiness, not visible feature expansion.

---

## 20. Required Handoff

Create:

```text
tasks/round-08/01-cursor-review-pipeline-orchestrator-skeleton-handoff.md
```

The handoff must include:

```markdown
# Round 08 / Task 01 Handoff: Review Pipeline Orchestrator Skeleton

## 1. Summary

## 2. Files Changed

## 3. Architecture Implemented

## 4. Provider/Pipeline Flow

## 5. API Contract Preservation Evidence

## 6. Persistence Preservation Evidence

## 7. Tests Added or Updated

## 8. Validation Commands Run

## 9. Runtime Verification Evidence

## 10. Frontend Validation Evidence

## 11. README Updates

## 12. Known Limitations

## 13. Risks / Follow-ups for Codex

## 14. Suggested Round 09 Direction
```

Be specific.

Include actual command results, not vague statements.

If any validation could not be run, state clearly:

```text
NOT RUN
Reason:
Risk:
Recommended follow-up:
```

---

## 21. Final Instruction

Implement the smallest real architecture step that makes CodeReviewX agent-ready.

Preserve current behavior.

Do not overbuild.

Do not delay the AI provider path.

Round 08 succeeds only if the system still behaves like Round 07 externally, while internally routing review generation through a real pipeline/provider boundary.