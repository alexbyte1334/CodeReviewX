# tasks/round-06/03-qoder-review-result-contract-hardening-independent-review.md

# Qoder Task: Round 06 - Review Result Contract Hardening Independent Review

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 06
- Task ID: `03-qoder-review-result-contract-hardening-independent-review`
- Role: Qoder Independent Architecture Review
- Previous Task 1: `tasks/round-06/01-cursor-review-result-contract-hardening.md`
- Previous Handoff 1: `handoff/round-06/01-cursor-review-result-contract-hardening-handoff.md`
- Previous Task 2: `tasks/round-06/02-codex-review-result-contract-hardening-validation.md`
- Previous Handoff 2: `handoff/round-06/02-codex-review-result-contract-hardening-validation-handoff.md`
- Theme: Review Result Contract Hardening
- Primary Goal: 独立审查 Round 06 contract hardening 是否足以关闭本轮，并判断是否可以进入 Round 07 Database Persistence v1
- Expected Output: `handoff/round-06/03-qoder-review-result-contract-hardening-independent-review-handoff.md`

---

## 2. Current Round Status

Round 06 当前已经完成：

1. Cursor implementation；
2. Codex independent validation；
3. backend/frontend tests；
4. backend runtime curl validation；
5. frontend browser validation；
6. README boundary validation；
7. scope creep validation。

Codex verdict：

```text
ROUND_06_CODEX_VALIDATION_PASSED_WITH_NOTES
```

Codex notes are non-blocking:

1. `/Users/liyi/projects/CodeReviewX` is not currently a git repository, so `git status --short` could not produce workspace diff.
2. Root `README.md` still has a broad "Project Overview" sentence phrased like full GitHub/Semgrep/LLM product vision, but it appears after clear current/planned/out-of-scope sections.
3. `ReviewTaskDetail` metadata table still displays top-level `task.riskLevel`, while summary panel uses `getIssueSummary(task)`. Runtime verification confirmed `riskLevel == issueSummary.riskLevel`.

Qoder 的职责不是再次实现功能，而是从架构完整性、contract 稳定性、后续 persistence 可迁移性、scope discipline 角度做最后独立审查。

---

## 3. Round 06 Strategic Goal

Round 06 的核心目标：

```text
Review Result Contract Hardening
```

具体目标：

1. backend 成为 review result aggregate / risk level 的 authoritative source；
2. backend 新增 `IssueSummaryResponse`；
3. `ReviewTaskResponse` 返回 `issueSummary`；
4. `ReviewTaskResponse.riskLevel` 与 `issueSummary.riskLevel` 保持一致；
5. frontend summary panel 优先消费 backend `issueSummary`；
6. frontend computed summary 只作为 fallback；
7. 新增 `IssueStatus`；
8. `ReviewIssueResponse.status` 进入 contract；
9. 复用或调整 `IssueSource`；
10. `ReviewIssueResponse.source` 进入 contract；
11. legacy `IssueType` 删除或明确处理；
12. 保持 deterministic mock issue generation；
13. 保持现有 endpoints 和 `ApiResponse<T>` wrapper；
14. 不引入 database；
15. 不引入 ai-service；
16. 不接 GitHub/Semgrep/LLM；
17. 更新 backend/frontend tests；
18. 更新 README current/planned boundary。

---

## 4. Non-goals / Strict Scope

Qoder 审查时必须以以下边界为准。

Round 06 不是：

1. database persistence；
2. JPA/MyBatis/Hibernate integration；
3. database migration；
4. Redis/cache；
5. real GitHub integration；
6. Semgrep execution；
7. LLM integration；
8. ai-service integration；
9. repository clone；
10. real code parsing；
11. diff viewer；
12. syntax highlighting；
13. issue filtering/sorting；
14. false-positive workflow；
15. human reviewer comment workflow；
16. auth/user system；
17. deployment；
18. CI/CD；
19. design system；
20. component library migration。

如果 Qoder 发现任何上述实现进入当前代码，应标记为 scope blocker。

---

## 5. Inputs to Review

Qoder should review:

```text
tasks/round-06/00-round-06-start.md
tasks/round-06/01-cursor-review-result-contract-hardening.md
handoff/round-06/01-cursor-review-result-contract-hardening-handoff.md
tasks/round-06/02-codex-review-result-contract-hardening-validation.md
handoff/round-06/02-codex-review-result-contract-hardening-validation-handoff.md
```

And inspect implementation files as needed:

### Backend

```text
backend-java/src/main/java/com/codereviewx/backend/review/dto/IssueSummaryResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewIssueResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewTaskResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/RiskLevel.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSource.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueStatus.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueCategory.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSeverity.java
backend-java/src/main/java/com/codereviewx/backend/review/model/ReviewTask.java
backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java
backend-java/src/test/java/com/codereviewx/backend/review/controller/ReviewTaskControllerTest.java
backend-java/src/test/java/com/codereviewx/backend/review/service/ReviewTaskServiceTest.java
```

### Frontend

```text
frontend/src/types/reviewTask.ts
frontend/src/utils/reviewSummary.ts
frontend/src/components/ReviewTaskDetail.tsx
frontend/src/test/ReviewTaskDetail.test.tsx
frontend/README.md
```

### Documentation

```text
README.md
frontend/README.md
```

---

## 6. Review Method

Qoder does not need to re-run every test if Codex evidence is sufficient, but Qoder should perform enough independent inspection to confirm:

1. Cursor implementation matches Round 06 scope；
2. Codex validation evidence is credible；
3. no key contract hole remains；
4. no architectural trap is being pushed into Round 07；
5. remaining notes are truly non-blocking；
6. Round 07 can safely start from current contract.

If Qoder chooses to run commands, record them. If not, explain that review was based on code inspection plus Codex runtime/test evidence.

Recommended optional commands:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

```bash
cd frontend
npm run typecheck
npm run build
npm test
```

Optional grep:

```bash
rg -n "IssueType|RiskLevel\.HIGH|issueSummary|source|status|getIssueSummary|computeIssueSummaryFromIssues" backend-java/src frontend/src README.md frontend/README.md
```

Optional scope grep:

```bash
rg -n "JpaRepository|CrudRepository|@Entity|@Table|DataSource|Flyway|Liquibase|GitHub|Semgrep|OpenAI|Anthropic|Gemini|SecurityConfig|Swagger|OpenAPI|Repository|Mapper|Migration|LlmClient|AiServiceClient|clone|exec\(" backend-java/src frontend/src README.md frontend/README.md
```

---

## 7. Required Review Questions

Qoder must answer the following questions explicitly.

## 7.1 Backend Contract Questions

1. Does `IssueSummaryResponse` exist and represent the correct aggregate?
2. Does it include:
   - `totalIssues`
   - `highCount`
   - `mediumCount`
   - `lowCount`
   - `riskLevel`
3. Does `RiskLevel` include `NONE`, `LOW`, `MEDIUM`, `HIGH`?
4. Does the summary aggregation rule follow:
   - `HIGH > MEDIUM > LOW > NONE`
5. Does `ReviewTaskResponse` include `issueSummary`?
6. Is `ReviewTaskResponse.riskLevel` preserved for compatibility?
7. Is `ReviewTaskResponse.riskLevel` derived from or at least kept consistent with `issueSummary.riskLevel`?
8. Do create/list/detail responses share the same contract?
9. Is `ApiResponse<T>` unchanged?
10. Are endpoint paths unchanged?

## 7.2 Issue Lifecycle / Source Questions

1. Does `IssueSource` contain a future-proof but bounded set?
2. Is `MOCK` present and used for current mock issues?
3. Are `SEMGREP` and `LLM` present for future pipeline extension?
4. Is `MANUAL` reasonable for later human review extension?
5. Does `IssueStatus` contain:
   - `OPEN`
   - `RESOLVED`
   - `FALSE_POSITIVE`
6. Are current mock issues always `OPEN`?
7. Has the implementation avoided status update workflow?
8. Has the implementation avoided false-positive workflow?
9. Has the implementation avoided human reviewer workflow?
10. Is `IssueType` deleted or otherwise resolved?

## 7.3 Frontend Source-of-Truth Questions

1. Does frontend define `IssueSummary` and aligned enum/string union types?
2. Does `ReviewTask.issueSummary` exist in the frontend contract?
3. Does summary panel prefer backend `task.issueSummary`?
4. Does frontend fallback compute summary only when `task.issueSummary` is missing?
5. Is fallback centralized rather than duplicated across multiple UI locations?
6. Do issue cards render `source` and `status`?
7. Is demo/mock messaging still clear?
8. Has the frontend avoided adding issue workflow actions?
9. Has the frontend avoided complex state management?
10. Is there any user-visible risk inconsistency between `task.riskLevel` and `issueSummary.riskLevel`?

## 7.4 Documentation Questions

1. Does root README clearly state current implementation is mock/demo?
2. Does root README clearly separate current implementation from planned architecture?
3. Does frontend README do the same?
4. Is backend-computed issue summary documented?
5. Is backend authoritative risk summary documented?
6. Is frontend fallback documented?
7. Is `MOCK` source documented?
8. Is `OPEN` status documented?
9. Are no database / no GitHub / no Semgrep / no LLM / no ai-service boundaries documented?
10. Is the Codex note about broad Project Overview wording acceptable, or should it become a required cleanup?

## 7.5 Round 07 Readiness Questions

1. Is the current review result contract stable enough to inform database schema design?
2. Should Round 07 persist `issueSummary`, compute it dynamically, or derive it as a query aggregate?
3. Should `ReviewTask.riskLevel` be persisted as an independent column, or derived from issues/summary?
4. Should `IssueSource` and `IssueStatus` enter persistence schema in Round 07?
5. Should `IssueStatus` remain read-only until a later workflow round?
6. Is it safe for Round 07 to introduce database persistence without GitHub/Semgrep/LLM?
7. What should remain explicitly forbidden in Round 07?
8. What acceptance criteria should Round 07 include for contract compatibility?

---

## 8. Known Notes from Codex to Re-evaluate

Codex identified 3 non-blocking notes. Qoder must explicitly agree or disagree with each.

## 8.1 Not a Git Repository

Codex note:

```text
/Users/liyi/projects/CodeReviewX is not currently a git repository, so git status --short could not produce a workspace diff.
```

Qoder should decide:

1. Is this acceptable for current local validation?
2. Does this affect trust in the handoff?
3. Should future agent tasks require explicit changed file list when git is unavailable?

Suggested likely conclusion:

```text
Non-blocking. Codex compensated with static file review, tests, runtime validation, and browser validation.
```

---

## 8.2 README Project Overview Wording

Codex note:

```text
Root README.md still has a broad "Project Overview" sentence phrased as if CodeReviewX is a full GitHub/Semgrep/LLM review system, but it appears after clear Current Implementation, Planned Architecture, and Out of Scope sections.
```

Qoder should decide:

1. Is this acceptable for Round 06 closure?
2. Could it confuse future implementers?
3. Should Round 06 require immediate README wording cleanup?
4. Or should this be carried as Round 07 documentation note?

Suggested likely conclusion:

```text
Non-blocking. Prefer small future cleanup: rename that section to Planned Product Vision or add one clarifying sentence.
```

---

## 8.3 Frontend Metadata Uses `task.riskLevel`

Codex note:

```text
ReviewTaskDetail still displays top-level task.riskLevel in the metadata table while the summary panel uses getIssueSummary(task).
```

Qoder should decide:

1. Is this acceptable because backend guarantees equality?
2. Could this create a source-of-truth inconsistency later?
3. Should frontend metadata risk label also use `getIssueSummary(task).riskLevel`?
4. Should Round 07 explicitly prevent independent mutation of `riskLevel`?

Suggested likely conclusion:

```text
Non-blocking for Round 06 because backend verifies equality. Important Round 07 design note: riskLevel should be derived from the same aggregate source as issueSummary.
```

---

## 9. Architecture Review Focus

Qoder should go beyond “tests passed” and assess architectural quality.

## 9.1 Source-of-Truth Model

Evaluate whether the current model is acceptable:

```text
ReviewTask stores issues + riskLevel
ReviewTaskResponse returns issueSummary computed from issues
riskLevel is kept consistent with issueSummary.riskLevel
frontend summary uses backend issueSummary
```

Questions:

1. Is there any immediate inconsistency risk in current in-memory/no-mutation model?
2. Is this acceptable before persistence?
3. What should change once issues become mutable?
4. Should `riskLevel` become derived-only in Round 07?
5. Should `issueSummary` be persisted or computed on read?

Recommended stance to evaluate:

```text
For Round 06, current design is acceptable.
For Round 07, prefer computing summary from persisted issues at read time unless performance or query needs require materialization.
Do not allow independent riskLevel mutation without invariant enforcement.
```

---

## 9.2 Contract Stability

Evaluate whether the response contract is stable enough:

```json
{
  "id": "1",
  "repoUrl": "https://github.com/example/demo",
  "prNumber": 6,
  "status": "SUCCESS",
  "riskLevel": "HIGH",
  "summary": "...",
  "issues": [
    {
      "id": "ISSUE-1",
      "severity": "HIGH",
      "category": "SECURITY",
      "source": "MOCK",
      "status": "OPEN",
      "filePath": "...",
      "startLine": 42,
      "endLine": 48,
      "title": "...",
      "description": "...",
      "recommendation": "..."
    }
  ],
  "issueSummary": {
    "totalIssues": 3,
    "highCount": 1,
    "mediumCount": 1,
    "lowCount": 1,
    "riskLevel": "HIGH"
  },
  "createdAt": "...",
  "updatedAt": "..."
}
```

Questions:

1. Are fields adequate for Round 07 schema design?
2. Are `IssueSource` and `IssueStatus` correctly placed on issue-level, not task-level?
3. Is `IssueSummary` correctly placed on task-level response?
4. Should summary include category/source/status counts now? Or is severity-only summary enough for current stage?
5. Is `RiskLevel.NONE` enough for no-issue state?
6. Should `endLine` nullable behavior remain unchanged?

Recommended stance to evaluate:

```text
Severity-only summary is sufficient for Round 06.
Category/source/status aggregate can wait until filtering/workflow requirements emerge.
```

---

## 9.3 Persistence Readiness

Qoder should explicitly advise Round 07.

Recommended Round 07 direction:

```text
Database Persistence v1
```

Round 07 should likely:

1. introduce database dependency；
2. persist `ReviewTask`；
3. persist `ReviewIssue`；
4. keep mock issue generation；
5. preserve existing API contract；
6. compute `issueSummary` from persisted issues at response assembly time；
7. keep `ReviewTaskResponse.riskLevel` equal to `issueSummary.riskLevel`；
8. persist issue `source`；
9. persist issue `status`；
10. keep status read-only；
11. avoid GitHub/Semgrep/LLM；
12. avoid issue workflow；
13. avoid auth；
14. avoid diff viewer；
15. avoid filtering/sorting unless strictly required.

Qoder should confirm or adjust this recommendation.

---

## 10. Acceptance Criteria for Qoder Review

Qoder review is successful if it produces:

1. independent verdict；
2. backend contract assessment；
3. frontend source-of-truth assessment；
4. issue lifecycle/source assessment；
5. README/documentation assessment；
6. scope compliance assessment；
7. Codex notes re-evaluation；
8. Round 07 readiness decision；
9. recommended Round 07 direction；
10. blocking/non-blocking findings；
11. final decision on whether Round 06 can close.

---

## 11. Possible Verdicts

Qoder must choose one.

### 11.1 Accept

```text
ROUND_06_ACCEPTED
```

Use if:

1. Cursor implementation satisfies Round 06；
2. Codex validation is credible；
3. no blocking issues remain；
4. notes are minor；
5. Round 07 can proceed.

### 11.2 Accept with Notes

```text
ROUND_06_ACCEPTED_WITH_NOTES
```

Use if:

1. core contract is correct；
2. no blocking issue remains；
3. but there are important design/documentation notes to carry into Round 07.

This is the most likely outcome.

### 11.3 Needs Fixes

```text
ROUND_06_NEEDS_FIXES
```

Use if:

1. one or more blocking issues exist；
2. Round 06 should not close yet；
3. Cursor or another implementer must fix before Round 07.

Blocking examples:

- backend does not consistently return `issueSummary`；
- risk/source/status contract is incomplete；
- frontend still primarily computes summary locally；
- tests fail due to implementation；
- current README misrepresents mock implementation as real pipeline；
- forbidden scope was introduced；
- API contract would cause schema churn in Round 07.

---

## 12. Required Handoff Output

Qoder must create:

```text
handoff/round-06/03-qoder-review-result-contract-hardening-independent-review-handoff.md
```

Use this exact structure:

```markdown
# Round 06 Qoder Handoff: Review Result Contract Hardening Independent Review

## 1. Summary

Summarize what was reviewed and the overall result.

## 2. Verdict

One of:

- ROUND_06_ACCEPTED
- ROUND_06_ACCEPTED_WITH_NOTES
- ROUND_06_NEEDS_FIXES

## 3. Evidence Reviewed

List:

- Cursor handoff
- Codex handoff
- backend files
- frontend files
- README files
- tests/runtime evidence reviewed
- any commands run by Qoder

## 4. Backend Contract Review

Assess:

- IssueSummaryResponse
- ReviewTaskResponse.issueSummary
- riskLevel consistency
- aggregation rule
- RiskLevel.NONE
- IssueSource
- IssueStatus
- ReviewIssueResponse source/status
- IssueType cleanup
- endpoint/wrapper compatibility

## 5. Frontend Contract Review

Assess:

- frontend types
- getIssueSummary
- fallback behavior
- summary panel source-of-truth
- issue card source/status badges
- metadata risk display
- demo/mock label
- absence of workflow scope creep

## 6. Documentation Review

Assess:

- current implementation clarity
- planned architecture clarity
- backend authoritative issue summary
- frontend fallback
- MOCK source
- OPEN status
- no database/GitHub/Semgrep/LLM/ai-service
- README Project Overview wording note

## 7. Scope Compliance Review

Confirm whether forbidden scope was avoided.

## 8. Codex Notes Re-evaluation

For each Codex note:

### 8.1 Git Repository Unavailable

Accept / reject / impact.

### 8.2 README Project Overview Wording

Accept / reject / impact.

### 8.3 Metadata Uses task.riskLevel

Accept / reject / impact.

## 9. Blocking Findings

If none:

```text
None.
```

## 10. Non-blocking Findings

List notes to carry forward.

## 11. Round 07 Readiness

Answer:

- Is current contract ready for persistence?
- What should Round 07 implement?
- What should remain forbidden?
- Should issueSummary be persisted or computed?
- Should riskLevel be persisted or derived?
- Should IssueSource/IssueStatus be persisted?
- Should IssueStatus remain read-only?

## 12. Recommended Round 07 Direction

Provide concrete recommendation.

Expected likely output:

```text
Round 07 should start Database Persistence v1, preserve existing API contract, persist ReviewTask and ReviewIssue, compute issueSummary from persisted issues at response assembly, keep riskLevel consistent with issueSummary.riskLevel, keep mock generation, and continue excluding GitHub/Semgrep/LLM.
```

## 13. Final Recommendation

State whether Round 06 can close and whether to generate:

```text
tasks/round-07/00-round-07-start.md
```
```

---

## 13. Review Checklist

### Backend

- [ ] `IssueSummaryResponse` exists and is correct；
- [ ] `ReviewTaskResponse.issueSummary` exists；
- [ ] `riskLevel` compatibility field remains；
- [ ] `riskLevel == issueSummary.riskLevel`；
- [ ] severity count aggregation is correct；
- [ ] `RiskLevel.NONE` exists；
- [ ] `IssueSource.MOCK` exists；
- [ ] `IssueSource.SEMGREP` exists；
- [ ] `IssueSource.LLM` exists；
- [ ] `IssueSource.MANUAL` exists or omission is justified；
- [ ] `IssueStatus.OPEN` exists；
- [ ] `IssueStatus.RESOLVED` exists；
- [ ] `IssueStatus.FALSE_POSITIVE` exists；
- [ ] `ReviewIssueResponse.source` exists；
- [ ] `ReviewIssueResponse.status` exists；
- [ ] mock source is `MOCK`；
- [ ] mock status is `OPEN`；
- [ ] `IssueType` cleaned up；
- [ ] create endpoint unchanged；
- [ ] list endpoint unchanged；
- [ ] detail endpoint unchanged；
- [ ] `ApiResponse<T>` unchanged；
- [ ] no persistence introduced。

### Frontend

- [ ] `IssueSummary` type exists；
- [ ] `RiskLevel` type includes `NONE`；
- [ ] `IssueSource` type exists；
- [ ] `IssueStatus` type exists；
- [ ] `ReviewTask.issueSummary` exists；
- [ ] `getIssueSummary` prefers backend summary；
- [ ] fallback summary exists；
- [ ] summary logic is centralized；
- [ ] summary panel consumes normalized summary；
- [ ] issue card displays source；
- [ ] issue card displays status；
- [ ] demo/mock label remains clear；
- [ ] no issue workflow action introduced；
- [ ] no complex frontend state management introduced。

### Documentation

- [ ] backend-computed issue summary documented；
- [ ] backend as authoritative risk source documented；
- [ ] frontend fallback documented；
- [ ] `MOCK` source documented；
- [ ] `OPEN` status documented；
- [ ] no real code analysis documented；
- [ ] no database documented；
- [ ] no GitHub API documented；
- [ ] no Semgrep documented；
- [ ] no LLM / ai-service documented；
- [ ] current/planned boundary clear enough；
- [ ] any wording ambiguity recorded。

### Scope

- [ ] no database；
- [ ] no persistence；
- [ ] no JPA/MyBatis/Hibernate；
- [ ] no Entity/Repository/Mapper；
- [ ] no migration；
- [ ] no GitHub API；
- [ ] no repo clone；
- [ ] no Semgrep；
- [ ] no LLM；
- [ ] no ai-service；
- [ ] no auth；
- [ ] no status update workflow；
- [ ] no false-positive workflow；
- [ ] no human reviewer workflow；
- [ ] no chart/component library；
- [ ] no Round 07 work。

---

## 14. Final Instruction

Qoder should decide whether Round 06 has done enough to stabilize the review result contract before persistence.

The most important architectural questions are:

```text
Can the current ReviewTask / ReviewIssue / IssueSummary response contract become the basis for Round 07 database persistence?

Is riskLevel now sufficiently tied to backend issue aggregation?

Is frontend no longer the primary risk source-of-truth?

Are IssueSource and IssueStatus clear enough to persist later without immediate workflow support?

Are current vs planned capabilities documented clearly enough?

Did the implementation avoid entering GitHub/Semgrep/LLM/database scope?
```

If yes, recommend closing Round 06 and starting:

```text
tasks/round-07/00-round-07-start.md
```

with a clear Database Persistence v1 direction.