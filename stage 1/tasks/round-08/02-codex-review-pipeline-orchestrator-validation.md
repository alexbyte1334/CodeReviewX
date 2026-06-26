# tasks/round-08/02-codex-review-pipeline-orchestrator-validation.md

# Round 08 / Task 02: Codex Validation — Review Pipeline Orchestrator Skeleton

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 08
- Task: 02
- Role: Codex Independent Validation
- Theme: Review Pipeline Orchestrator Skeleton
- Task Type: Independent architecture validation, regression testing, and scope-control review
- Previous Task:
  - `tasks/round-08/01-cursor-review-pipeline-orchestrator-skeleton.md`
- Cursor Handoff:
  - `tasks/round-08/01-cursor-review-pipeline-orchestrator-skeleton-handoff.md`
- Expected Output:
  - Validation findings
  - Any necessary minimal fixes
  - Test/runtime evidence
  - Handoff document
- Handoff File To Produce:

```text
tasks/round-08/02-codex-review-pipeline-orchestrator-validation-handoff.md
```

---

## 2. Strategic Context

Round 08 is an architecture-enabling round.

The project must finish in the next 3–5 rounds, so this validation must be strict but not expansive.

Round 08’s purpose is to move CodeReviewX from:

```text
ReviewTaskService directly generates deterministic mock issues
```

to:

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

This task must verify that Cursor’s implementation is a real pipeline/provider boundary and not just a cosmetic refactor.

---

## 3. Cursor Claimed Implementation Summary

Cursor claims that the backend now contains:

```text
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/
  ReviewContext.java
  ReviewFinding.java
  ReviewProvider.java
  ReviewProviderResult.java
  ReviewPipelineService.java

backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/
  MockReviewProvider.java
```

Cursor also claims:

1. `ReviewTaskService` now injects `ReviewPipelineService`;
2. `ReviewTaskService` builds `ReviewContext`;
3. `ReviewTaskService` calls `reviewPipelineService.run(context)`;
4. `MockReviewProvider` returns exactly 3 deterministic findings;
5. findings are mapped to `ReviewIssueEntity`;
6. persisted issues remain the source for response issues and summary;
7. API DTOs and controllers are unchanged;
8. persistence entities and repositories are unchanged;
9. frontend source is unchanged;
10. backend tests pass: 46 tests;
11. frontend tests pass: 26 tests;
12. runtime curl validation passed;
13. restart persistence smoke passed;
14. browser smoke was not run.

Your job is to independently verify these claims.

---

## 4. Primary Validation Goals

Codex must validate:

1. pipeline/provider architecture exists and is meaningful;
2. mock generation has actually moved out of `ReviewTaskService`;
3. public API contract is unchanged;
4. persistence behavior is unchanged;
5. frontend behavior is not regressed;
6. tests are sufficient and passing;
7. README claims are accurate;
8. no Round 08 non-goals were accidentally implemented;
9. implementation is suitable for Round 09 AI Review Provider v1.

---

## 5. Strict Scope

This task is primarily validation.

You may make minimal corrective changes only if required to preserve Round 08 acceptance criteria.

Allowed fixes:

1. small compile fixes;
2. missing test assertions;
3. small README wording corrections;
4. minor package/import cleanup;
5. fixing accidental API exposure;
6. fixing persistence or summary regression;
7. correcting mock provider output mismatch.

Forbidden additions:

1. real GitHub API integration;
2. repository clone;
3. PR diff ingestion;
4. pasted diff UI;
5. Semgrep execution;
6. real LLM call;
7. `ai-service` integration;
8. async queue;
9. full agent planner;
10. provider registry UI;
11. frontend redesign;
12. auth;
13. production DB migration;
14. Flyway/Liquibase;
15. CI/CD or deployment work.

If you find a larger issue, document it clearly instead of expanding the scope.

---

## 6. Architecture Review Checklist

### 6.1 Pipeline Package

Verify that the implementation contains an internal review pipeline package, preferably:

```text
backend-java/src/main/java/com/codereviewx/backend/review/pipeline
```

Required concepts:

```text
ReviewContext
ReviewFinding
ReviewProvider
ReviewProviderResult
ReviewPipelineService
```

Check:

- [ ] classes are internal backend concepts;
- [ ] they are not DTOs exposed by controllers;
- [ ] they are not JPA entities;
- [ ] they do not depend on frontend or HTTP response models;
- [ ] they are cohesive and minimal.

---

### 6.2 ReviewContext

Verify expected fields exist:

```text
taskId
repoUrl
prNumber
createdAt
```

Acceptable type implementation:

- Java record;
- Java class;
- builder/static factory if consistent.

Check:

- [ ] no GitHub payload added;
- [ ] no diff model added yet;
- [ ] no repository clone metadata added;
- [ ] no unnecessary generic metadata map unless justified;
- [ ] suitable for Round 09 extension.

---

### 6.3 ReviewFinding

Verify it represents normalized internal findings.

Expected fields:

```text
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
```

Check:

- [ ] uses existing enums where appropriate;
- [ ] not exposed in API;
- [ ] not persisted directly;
- [ ] maps cleanly to `ReviewIssueEntity`;
- [ ] contains enough data for future AI/static-analysis providers.

---

### 6.4 ReviewProvider

Expected interface:

```java
ReviewProviderResult review(ReviewContext context);
```

or equivalent.

Check:

- [ ] provider abstraction exists;
- [ ] `MockReviewProvider` implements it;
- [ ] future provider can implement it without changing controller;
- [ ] future provider can implement it with minimal `ReviewTaskService` change;
- [ ] no async/generic workflow overbuild.

---

### 6.5 ReviewProviderResult

Expected fields:

```text
findings
providerName
successful
message
```

Check:

- [ ] internal only;
- [ ] not returned in public API;
- [ ] not persisted;
- [ ] minimal and useful;
- [ ] no raw LLM output;
- [ ] no token/cost accounting;
- [ ] no complex trace graph.

---

### 6.6 MockReviewProvider

Verify:

- [ ] returns exactly 3 findings;
- [ ] one HIGH;
- [ ] one MEDIUM;
- [ ] one LOW;
- [ ] all `source = MOCK`;
- [ ] all `status = OPEN`;
- [ ] issue ids exactly:

```text
ISSUE-1
ISSUE-2
ISSUE-3
```

- [ ] deterministic output;
- [ ] no network call;
- [ ] no GitHub call;
- [ ] no Semgrep call;
- [ ] no LLM call;
- [ ] no required API key.

---

### 6.7 ReviewPipelineService

Verify:

- [ ] Spring service exists;
- [ ] depends on `ReviewProvider` abstraction;
- [ ] currently invokes `MockReviewProvider`;
- [ ] returns provider result or findings;
- [ ] does not contain business-specific mock issue construction itself;
- [ ] does not expose provider internals externally;
- [ ] does not implement provider registry or multi-agent workflow.

Preferred flow:

```text
ReviewPipelineService.run(context)
  -> reviewProvider.review(context)
  -> return ReviewProviderResult
```

---

### 6.8 ReviewTaskService

Verify that `ReviewTaskService` now does this:

```text
create task
save ReviewTaskEntity
build ReviewContext
call ReviewPipelineService
map ReviewFinding -> ReviewIssueEntity
save issues
return same ReviewTaskResponse
```

Check:

- [ ] no direct hardcoded mock issue construction remains in `ReviewTaskService`;
- [ ] no API DTO is used as internal provider model;
- [ ] no JPA entity is returned by provider;
- [ ] mapping is explicit and readable;
- [ ] `issueSummary` still computed from persisted issues;
- [ ] `riskLevel` still derived from issue summary;
- [ ] `riskLevel == issueSummary.riskLevel`.

A helper mapper method inside `ReviewTaskService` is acceptable.

---

## 7. API Contract Validation

Verify endpoints remain:

```text
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

Verify create request remains:

```json
{
  "repoUrl": "https://github.com/example/demo",
  "prNumber": 8
}
```

Verify wrapper remains:

```json
{
  "success": true,
  "message": "OK",
  "data": {}
}
```

Verify `ReviewTaskResponse` still contains:

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

Verify `ReviewIssueResponse` still contains:

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

Verify `IssueSummaryResponse` still contains:

```text
totalIssues
highCount
mediumCount
lowCount
riskLevel
```

Verify no API response exposes:

```text
ReviewContext
ReviewFinding
ReviewProvider
ReviewProviderResult
providerName
successful
provider message
internal DB issue id
```

Unless `providerName` or similar already existed before Round 08, it must not be newly exposed.

---

## 8. Persistence Validation

Verify:

- [ ] `ReviewTaskEntity` remains persisted;
- [ ] `ReviewIssueEntity` remains persisted;
- [ ] no new `IssueSummaryEntity`;
- [ ] no independent persisted risk source of truth;
- [ ] no unnecessary DB schema changes;
- [ ] no provider result table;
- [ ] no execution trace table;
- [ ] public issue id remains `ISSUE-1/2/3`;
- [ ] internal DB issue id remains hidden;
- [ ] restart persistence still works.

Strongly inspect entity files and generated schema behavior.

If new DB columns were added, determine whether they were necessary. If unnecessary, flag them.

---

## 9. Test Validation

Run:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

If unavailable:

```bash
cd backend-java
mvn test
```

Expected:

```text
BUILD SUCCESS
all backend tests pass
```

Cursor claims:

```text
Tests run: 46, Failures: 0, Errors: 0, Skipped: 0
```

Verify independently.

Also inspect new tests:

```text
MockReviewProviderTest
ReviewPipelineServiceTest
```

Check that tests are meaningful and not superficial.

Minimum expected coverage:

- [ ] mock provider returns exactly 3 findings;
- [ ] severities HIGH/MEDIUM/LOW;
- [ ] source MOCK;
- [ ] status OPEN;
- [ ] ids ISSUE-1/2/3;
- [ ] provider result successful;
- [ ] provider name present;
- [ ] pipeline invokes provider;
- [ ] pipeline preserves findings;
- [ ] create task persists provider findings;
- [ ] get task returns persisted issues;
- [ ] issue summary correct;
- [ ] risk invariant correct.

If important tests are missing but easy to add, add them.

---

## 10. Frontend Validation

Run:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

Expected:

```text
typecheck passes
build passes
all tests pass
```

Cursor claims:

```text
26 tests passed
```

Verify independently.

Since frontend source was reportedly unchanged, do not redesign or refactor frontend.

---

## 11. Runtime Validation

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

Health:

```bash
curl http://localhost:8080/api/health
```

Create:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/round-08-codex-validation",
    "prNumber": 8
  }'
```

Verify:

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
data.issues[*].id includes ISSUE-1, ISSUE-2, ISSUE-3
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
2. record returned id
3. stop backend
4. restart backend
5. GET /api/review-tasks/{id}
6. confirm task exists
7. confirm 3 issues still exist
8. confirm issueSummary remains correct
9. confirm risk invariant remains true
```

---

## 12. Optional Browser Smoke

Cursor did not run browser smoke.

If feasible, run:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Then verify manually:

1. health status visible;
2. create task works;
3. list updates;
4. detail renders;
5. summary panel renders;
6. issue cards render;
7. severity/source/status badges render.

If not run, state clearly:

```text
NOT RUN
Reason:
Risk:
Recommended follow-up:
```

---

## 13. README Validation

Inspect:

```text
README.md
backend-java/README.md
```

Verify documentation says:

- [ ] Round 08 introduces internal review pipeline architecture;
- [ ] current provider is `MockReviewProvider`;
- [ ] output remains deterministic mock findings;
- [ ] findings are persisted as `ReviewIssue`;
- [ ] issue summary is computed from persisted issues;
- [ ] risk level is derived from issue summary;
- [ ] no GitHub call yet;
- [ ] no repository clone yet;
- [ ] no Semgrep yet;
- [ ] no LLM yet;
- [ ] no ai-service yet;
- [ ] real AI review is planned for a near-term round;
- [ ] roadmap targets completion in 3–5 rounds.

Flag or fix any overclaim.

Bad wording:

```text
CodeReviewX now performs real AI code review.
```

Acceptable wording:

```text
Round 08 introduces the internal review pipeline architecture.
The current provider remains deterministic mock-based.
Real AI review is planned for a near-term follow-up round.
```

---

## 14. Scope Creep Review

Confirm none of the following were introduced:

```text
GitHub API
repository clone
Semgrep execution
LLM call
ai-service integration
full planner
multi-agent workflow
async queue
status workflow
auth
team/org model
dashboard analytics
frontend redesign
component library migration
production DB hardening
Flyway/Liquibase
deployment/CI/CD
```

If any appear, evaluate whether they are inert documentation only or actual implementation.

Actual implementation should be flagged as scope creep unless necessary.

---

## 15. Round 09 Readiness Assessment

At the end of validation, explicitly answer:

```text
Can Round 09 proceed to AI Review Provider v1?
```

Assess whether the current architecture can support:

```text
AIReviewProvider implements ReviewProvider
LLM adapter boundary
structured JSON output parser
configuration-based provider selection
fallback to MockReviewProvider
no API key required for tests
```

You do not need to implement these in Round 08.

But you must judge whether the Round 08 abstraction is sufficient.

Recommended final verdict options:

```text
ROUND_08_READY_FOR_QODER
ROUND_08_ACCEPTED_WITH_MINOR_NOTES
ROUND_08_NEEDS_FIXES_BEFORE_QODER
ROUND_08_BLOCKED
```

Use one verdict clearly.

---

## 16. Required Handoff

Create:

```text
tasks/round-08/02-codex-review-pipeline-orchestrator-validation-handoff.md
```

Use this structure:

```markdown
# Round 08 / Task 02 Handoff: Codex Validation — Review Pipeline Orchestrator Skeleton

## 1. Summary Verdict

## 2. Validation Scope

## 3. Architecture Review Findings

## 4. Provider/Pipeline Boundary Assessment

## 5. ReviewTaskService Refactor Assessment

## 6. API Contract Validation

## 7. Persistence Validation

## 8. Test Results

## 9. Runtime Validation

## 10. Frontend Validation

## 11. Browser Smoke Result

## 12. README / Documentation Review

## 13. Scope Creep Check

## 14. Fixes Applied, If Any

## 15. Remaining Risks

## 16. Round 09 Readiness

## 17. Final Verdict
```

Every validation command must include actual result.

If something was not run, state:

```text
NOT RUN
Reason:
Risk:
Recommended follow-up:
```

---

## 17. Final Instruction

Be strict on architecture, but do not expand scope.

Round 08 should close only if the system remains externally stable while internally routing review generation through a real pipeline/provider boundary.

The correct target is:

```text
minimal, real, agent-ready review pipeline
```

not:

```text
generic workflow platform
```

and not:

```text
renamed mock helper
```