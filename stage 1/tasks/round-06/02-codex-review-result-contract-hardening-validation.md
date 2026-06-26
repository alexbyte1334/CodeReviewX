# tasks/round-06/02-codex-review-result-contract-hardening-validation.md

# Codex Task: Round 06 - Review Result Contract Hardening Validation

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 06
- Task ID: `02-codex-review-result-contract-hardening-validation`
- Role: Codex Independent Validation
- Previous Task: `tasks/round-06/01-cursor-review-result-contract-hardening.md`
- Cursor Handoff: `handoff/round-06/01-cursor-review-result-contract-hardening-handoff.md`
- Theme: Review Result Contract Hardening
- Primary Goal: 独立验证 Cursor 对 review result contract hardening 的实现是否满足 Round 06 acceptance criteria
- Expected Output: `handoff/round-06/02-codex-review-result-contract-hardening-validation-handoff.md`

---

## 2. Context

Round 06 的目标是完成：

```text
Review Result Contract Hardening
```

核心方向：

1. backend 成为 review result aggregate / risk level 的 authoritative source；
2. backend 新增并返回 `IssueSummaryResponse`；
3. `ReviewTaskResponse.issueSummary` 进入 API contract；
4. `ReviewTaskResponse.riskLevel` 与 `issueSummary.riskLevel` 保持一致；
5. frontend summary panel 优先使用 backend `issueSummary`；
6. frontend computed summary 只作为 fallback；
7. `ReviewIssueResponse` 新增 `source`；
8. `ReviewIssueResponse` 新增 `status`；
9. mock issue source 固定为 `MOCK`；
10. mock issue status 固定为 `OPEN`；
11. legacy `IssueType` 删除或明确处理；
12. README 明确 current implementation 与 planned architecture 边界；
13. 不引入 database / persistence / GitHub / Semgrep / LLM / ai-service。

Cursor handoff 声称已经完成上述实现，并且本地测试通过。

Codex 的职责不是继续开发功能，而是独立验证 Cursor 实现是否真实满足 contract、测试、runtime、文档和 scope 要求。

---

## 3. Cursor Handoff Claims to Validate

Cursor handoff 声称：

### Backend

1. `RiskLevel` 新增 `NONE`；
2. `IssueSource` 更新为：

```text
MOCK
SEMGREP
LLM
MANUAL
```

3. 新增 `IssueStatus`：

```text
OPEN
RESOLVED
FALSE_POSITIVE
```

4. 新增 `IssueSummaryResponse`；
5. `ReviewIssueResponse` 新增：

```text
source
status
```

6. `ReviewTaskResponse` 新增：

```text
issueSummary
```

7. `ReviewTaskService` 新增 `buildIssueSummary`；
8. mock issues 默认：

```text
source = MOCK
status = OPEN
```

9. task creation 不再硬编码 `RiskLevel.HIGH`，而是从 summary 聚合得到；
10. `ReviewTaskResponse.riskLevel == ReviewTaskResponse.issueSummary.riskLevel`；
11. `IssueType` 已删除，且无 live references。

### Frontend

1. `frontend/src/types/reviewTask.ts` 新增：

```typescript
RiskLevel
IssueSource
IssueStatus
IssueSummary
```

2. `ReviewIssue` 新增：

```typescript
source
status
```

3. `ReviewTask` 新增 optional：

```typescript
issueSummary?: IssueSummary
```

4. 新增或调整 summary utility：

```typescript
computeIssueSummaryFromIssues
getIssueSummary
```

5. `ReviewTaskDetail` 优先使用 backend `task.issueSummary`；
6. `issueSummary` 缺失时 fallback 到 local computed summary；
7. issue card 展示：

```text
[severity] [category] [source] [status]
```

8. demo/mock label 保留并更新；
9. frontend tests 覆盖 backend summary priority 和 fallback behavior。

### Documentation

1. 根 README 更新；
2. frontend README 更新；
3. 文档明确：
   - backend-computed issue summary；
   - backend is authoritative source；
   - frontend local summary is fallback only；
   - current issue source is `MOCK`；
   - current issue status is `OPEN`；
   - no real repository code is analyzed；
   - no GitHub API；
   - no Semgrep；
   - no LLM / ai-service；
   - data remains in-memory；
   - no database。

---

## 4. Strict Validation Scope

Codex 只做验证与必要的小修复建议。

### 4.1 Must Not Implement

严禁新增：

1. database；
2. persistence layer；
3. JPA / MyBatis / Hibernate；
4. Entity / Repository / Mapper；
5. migration；
6. Redis / cache；
7. GitHub API；
8. repository clone；
9. real repository parsing；
10. Semgrep execution；
11. LLM / AI service；
12. OpenAI / Anthropic / Gemini / local model call；
13. ai-service client；
14. auth / Spring Security；
15. Swagger / OpenAPI；
16. Chart.js / ECharts / D3；
17. Ant Design / Material UI；
18. Redux / MobX / React Query / XState；
19. Next.js migration；
20. SSR；
21. diff viewer；
22. syntax highlighting；
23. issue filtering / sorting；
24. status update API；
25. false-positive workflow；
26. human reviewer comment workflow；
27. Round 07 persistence work。

### 4.2 Allowed

允许：

1. 运行测试；
2. 启动 backend/frontend；
3. 使用 curl 验证 API；
4. 读取代码；
5. grep 检查引用；
6. 检查 README；
7. 发现问题时给出最小修复建议；
8. 如存在明显小错误，可在不扩大 scope 的前提下修复；
9. 输出 validation handoff。

如果发现需要较大实现变更，不要擅自扩展 scope，应在 handoff 中标记为 blocking 或 non-blocking。

---

## 5. Required Validation Steps

## 5.1 Inspect Git / Workspace State

先检查当前变更状态：

```bash
git status --short
```

记录：

1. modified files；
2. new files；
3. deleted files；
4. unexpected files；
5. 是否存在明显不属于 Round 06 的改动。

不要因为工作区有变更就直接失败；需要判断变更是否符合 Round 06 scope。

---

## 5.2 Backend Static Validation

检查以下文件是否存在且内容合理：

```text
backend-java/src/main/java/com/codereviewx/backend/review/dto/IssueSummaryResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewIssueResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewTaskResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/RiskLevel.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSource.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueStatus.java
backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java
```

重点验证：

### `IssueSummaryResponse`

必须包含：

```text
totalIssues
highCount
mediumCount
lowCount
riskLevel
```

其中 `riskLevel` 类型应为 `RiskLevel`。

### `RiskLevel`

必须包含：

```text
NONE
LOW
MEDIUM
HIGH
```

不要包含无明确需求的 `CRITICAL`。

### `IssueSource`

推荐包含：

```text
MOCK
SEMGREP
LLM
MANUAL
```

至少必须包含：

```text
MOCK
SEMGREP
LLM
```

### `IssueStatus`

必须包含：

```text
OPEN
RESOLVED
FALSE_POSITIVE
```

### `ReviewIssueResponse`

必须包含：

```text
source
status
```

并且类型分别为：

```text
IssueSource
IssueStatus
```

### `ReviewTaskResponse`

必须包含：

```text
issueSummary
```

并且类型为：

```text
IssueSummaryResponse
```

---

## 5.3 Backend Aggregation Logic Validation

检查 `ReviewTaskService` 中 summary 聚合逻辑。

必须符合：

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

请特别检查：

1. 是否仍有无条件硬编码 `RiskLevel.HIGH`；
2. `ReviewTask.riskLevel` 是否由 `issueSummary.riskLevel` 设置；
3. `toResponse` 或 response assembly 是否返回 `issueSummary`；
4. create/list/detail 是否都经过同一个 response assembly path；
5. 是否存在多个重复且可能不一致的 risk 计算逻辑；
6. empty issues 时是否能得到 `RiskLevel.NONE`。

建议执行：

```bash
grep -R "RiskLevel.HIGH" backend-java/src/main/java backend-java/src/test/java || true
grep -R "buildIssueSummary" backend-java/src/main/java backend-java/src/test/java || true
```

`RiskLevel.HIGH` 可以出现在测试断言或正常 enum 比较中，但不应作为 create task 的无条件 hardcode。

---

## 5.4 Mock Issue Contract Validation

检查 mock issue generation。

必须满足：

1. deterministic mock issue generation 保持；
2. 默认生成 3 个 issues；
3. severity 分布为：
   - 1 `HIGH`
   - 1 `MEDIUM`
   - 1 `LOW`
4. 每个 issue 包含：
   - `source = MOCK`
   - `status = OPEN`
5. 不调用外部服务；
6. 不读取真实 repo；
7. 不 clone repository；
8. 不执行 Semgrep；
9. 不调用 LLM。

---

## 5.5 `IssueType` Cleanup Validation

检查 legacy `IssueType` 是否已删除干净。

执行：

```bash
grep -R "IssueType" backend-java/src frontend/src README.md frontend/README.md || true
```

期望：

1. 如果 `IssueType` 已删除，应无引用；
2. 如果仍存在，应判断是否确有必要；
3. 如果仍存在但无引用，标记为 non-blocking 或 blocking，取决于它是否会误导后续 implementation；
4. 不要误删 `IssueCategory`。

---

## 5.6 Backend Tests

运行：

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

如果本地环境不需要或不存在该 JDK 路径，则运行：

```bash
cd backend-java
mvn test
```

记录：

1. command；
2. test count；
3. failures；
4. errors；
5. skipped；
6. build result。

如测试失败，必须定位失败原因：

1. implementation bug；
2. environment issue；
3. flaky test；
4. command/JDK mismatch。

不要只记录失败，要说明判断。

---

## 5.7 Backend Runtime Verification

启动 backend：

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

如果本地环境不需要或不存在该 JDK 路径，则运行：

```bash
cd backend-java
mvn spring-boot:run
```

### Health

```bash
curl http://localhost:8080/api/health
```

必须确认：

```text
success = true
data.status = UP
```

### Create

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/round-06-codex-validation",
    "prNumber": 6
  }'
```

必须确认：

```text
success = true
data.issues.length = 3
data.issueSummary.totalIssues = 3
data.issueSummary.highCount = 1
data.issueSummary.mediumCount = 1
data.issueSummary.lowCount = 1
data.issueSummary.riskLevel = HIGH
data.riskLevel = HIGH
data.riskLevel == data.issueSummary.riskLevel
data.issues[0].source = MOCK
data.issues[0].status = OPEN
data.issues[1].source = MOCK
data.issues[1].status = OPEN
data.issues[2].source = MOCK
data.issues[2].status = OPEN
```

Record the returned task id.

### List

```bash
curl http://localhost:8080/api/review-tasks
```

必须确认 list response 中对应 task 包含：

```text
issueSummary
issues[].source
issues[].status
riskLevel == issueSummary.riskLevel
```

### Detail

使用 create 返回的 id：

```bash
curl http://localhost:8080/api/review-tasks/{id}
```

必须确认 detail response 包含：

```text
issueSummary
issues[].source
issues[].status
riskLevel == issueSummary.riskLevel
```

注意：Cursor handoff 中 detail endpoint runtime curl 记录不够明确，因此 Codex 必须补测 detail curl。

---

## 5.8 Frontend Static Validation

检查以下文件：

```text
frontend/src/types/reviewTask.ts
frontend/src/utils/reviewSummary.ts
frontend/src/components/ReviewTaskDetail.tsx
frontend/src/test/ReviewTaskDetail.test.tsx
```

重点验证：

### TypeScript Types

必须包含：

```typescript
export type RiskLevel = 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH';

export type IssueSource = 'MOCK' | 'SEMGREP' | 'LLM' | 'MANUAL';

export type IssueStatus = 'OPEN' | 'RESOLVED' | 'FALSE_POSITIVE';

export interface IssueSummary {
  totalIssues: number;
  highCount: number;
  mediumCount: number;
  lowCount: number;
  riskLevel: RiskLevel;
}
```

`ReviewIssue` 必须包含：

```typescript
source: IssueSource;
status: IssueStatus;
```

`ReviewTask` 应支持：

```typescript
issueSummary?: IssueSummary;
```

optional 是可接受的，因为需要兼容 fallback。

### Summary Utility

必须存在统一 summary logic，类似：

```typescript
computeIssueSummaryFromIssues(issues)
getIssueSummary(task)
```

`getIssueSummary(task)` 必须：

```text
Prefer task.issueSummary
Fallback to computeIssueSummaryFromIssues(task.issues)
```

### Detail Component

必须确认：

1. summary panel 使用 `getIssueSummary(task)`；
2. summary panel 不再自行重复计算 risk；
3. risk level 展示来自归一化后的 `IssueSummary`；
4. issue card 展示 source badge；
5. issue card 展示 status badge；
6. demo/mock label 保留；
7. 没有新增 status update workflow；
8. 没有新增 resolve / false-positive button；
9. 没有新增复杂 state management。

---

## 5.9 Frontend Tests

运行：

```bash
cd frontend
npm install
npm run typecheck
npm run build
npm test
```

如果 `npm test` 不存在，请检查：

```bash
cat package.json
```

然后运行实际存在的 test script，例如：

```bash
npm run test
```

或：

```bash
npm run test:run
```

记录：

1. command；
2. result；
3. test count；
4. failed tests；
5. build output summary；
6. typecheck result。

测试必须至少覆盖或通过静态检查证明：

1. summary panel prefers backend `issueSummary`；
2. fallback summary works when `issueSummary` is missing；
3. total/high/medium/low counts render from backend summary；
4. risk level renders from backend summary；
5. issue card renders source badge；
6. issue card renders status badge；
7. existing severity/category/filePath/line/recommendation tests still pass；
8. empty issues fallback still works；
9. loading/error states still work。

重点验证 backend summary priority 测试是否足够严格。

建议检查是否存在“不一致数据”测试：

```text
task.issueSummary.totalIssues = 99
task.issues.length = 3
```

UI 应展示 backend summary 的 `99`，而不是从 issues 计算出的 `3`。

如果没有该测试，但代码逻辑明显正确，可标记为 non-blocking recommendation；如果代码逻辑不明确，则标记为 blocking。

---

## 5.10 Frontend Runtime Verification

启动 frontend：

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

浏览器验证或以可用方式验证：

1. backend health 正常显示；
2. 创建 ReviewTask；
3. list 出现 task；
4. detail 可打开；
5. summary panel 显示 backend issueSummary；
6. risk level 正确；
7. issue card 显示 source badge；
8. issue card 显示 status badge；
9. demo/mock label 清晰；
10. empty/loading/error states 不破坏。

如果 Codex 环境不支持浏览器，请说明限制，并用 tests + DOM/unit tests + API response 替代验证。

---

## 5.11 README Validation

检查：

```text
README.md
frontend/README.md
```

必须确认文档明确：

1. Round 06 introduces backend-computed issue summary；
2. backend is authoritative source for risk summary；
3. frontend uses computed summary only as fallback；
4. current issue source is `MOCK`；
5. current issue status is `OPEN`；
6. no real repository code is analyzed；
7. no GitHub API is called；
8. no Semgrep is executed；
9. no LLM / AI service is called；
10. data remains in-memory；
11. database is still not introduced；
12. planned MVP / future architecture 与 current implementation 分区清楚；
13. curl 示例使用 `repoUrl` / `prNumber`。

特别检查 README 是否出现误导性描述，例如：

```text
analyzes GitHub pull requests
runs Semgrep
uses LLM review
persists tasks in database
production-ready review pipeline
```

如果这些描述出现在 planned architecture 中是可以的，但必须清楚标记为 future/planned，而不是 current implementation。

---

## 5.12 Scope Creep Validation

执行或人工检查：

```bash
find backend-java/src/main/java -type f | sort
find frontend/src -type f | sort
```

重点确认没有新增：

```text
Repository
Entity
Mapper
Migration
JpaRepository
CrudRepository
DataSource
GitHubClient
SemgrepRunner
LlmClient
AiServiceClient
SecurityConfig
OpenApiConfig
SwaggerConfig
```

建议 grep：

```bash
grep -R "JpaRepository\|CrudRepository\|@Entity\|@Table\|DataSource\|Flyway\|Liquibase\|GitHub\|Semgrep\|OpenAI\|Anthropic\|Gemini\|SecurityConfig\|Swagger\|OpenAPI" backend-java/src frontend/src README.md frontend/README.md || true
```

注意：README planned architecture 中出现 GitHub/Semgrep/LLM 是允许的，但 current implementation 中不能声称已实现。

---

## 6. Expected Validation Decision

Codex 最终应给出明确 verdict。

可选 verdict：

```text
ROUND_06_CODEX_VALIDATION_PASSED
```

用于全部满足，只有无阻塞 notes。

```text
ROUND_06_CODEX_VALIDATION_PASSED_WITH_NOTES
```

用于核心 contract 满足，但存在非阻塞问题，例如：
- detail curl 原先未记录但 Codex 已补测通过；
- badge style 未细分；
- fallback 测试可更严格；
- README 某些表达可以更清楚。

```text
ROUND_06_CODEX_VALIDATION_FAILED_BLOCKING
```

用于存在 blocking issue，例如：
- backend 没有返回 `issueSummary`；
- create/list/detail 任一 endpoint contract 缺失；
- `riskLevel != issueSummary.riskLevel`；
- frontend 仍以 local computed summary 作为主路径；
- source/status 字段缺失；
- tests fail due to implementation issue；
- 引入 database / GitHub / Semgrep / LLM 等 scope creep；
- README 明确误导 current capability。

---

## 7. Blocking vs Non-blocking Guidance

### 7.1 Blocking Issues

以下属于 blocking：

1. backend compile/test fail due to implementation；
2. frontend typecheck/build/test fail due to implementation；
3. API response 不包含 `issueSummary`；
4. `issueSummary` counts 错误；
5. `issueSummary.riskLevel` 错误；
6. `ReviewTaskResponse.riskLevel` 与 `issueSummary.riskLevel` 不一致；
7. create/list/detail 任一 endpoint 缺少 required fields；
8. issues 缺少 `source` 或 `status`；
9. mock issue source 不是 `MOCK`；
10. mock issue status 不是 `OPEN`；
11. frontend summary panel 不优先使用 backend `issueSummary`；
12. fallback 缺失且导致旧数据无法渲染；
13. README 声称 current implementation 已支持 GitHub/Semgrep/LLM/database；
14. 引入任何 Round 06 forbidden scope。

### 7.2 Non-blocking Issues

以下通常属于 non-blocking：

1. badge source/status 没有独立颜色；
2. `IssueSummaryResponse` 命名可进一步优化但语义清楚；
3. `toResponse` 每次 recompute summary；
4. `ReviewTask` in-memory model 不存 `IssueSummaryResponse`；
5. frontend `issueSummary` optional 而不是 required；
6. README wording 可进一步润色但没有误导；
7. 缺少某个边界测试，但实现逻辑清楚且核心测试通过；
8. browser runtime 不可用，但 unit/e2e 替代验证充分。

---

## 8. Required Handoff Output

Codex 完成后，请生成：

```text
handoff/round-06/02-codex-review-result-contract-hardening-validation-handoff.md
```

handoff 必须包含以下结构：

```markdown
# Round 06 Codex Handoff: Review Result Contract Hardening Validation

## 1. Summary

简述验证范围、结果和最终 verdict。

## 2. Verdict

给出一个明确 verdict：

- ROUND_06_CODEX_VALIDATION_PASSED
- ROUND_06_CODEX_VALIDATION_PASSED_WITH_NOTES
- ROUND_06_CODEX_VALIDATION_FAILED_BLOCKING

## 3. Workspace / Files Reviewed

记录：

- git status summary
- reviewed backend files
- reviewed frontend files
- reviewed README files

## 4. Backend Static Validation

说明：

- IssueSummaryResponse 是否正确
- RiskLevel 是否包含 NONE
- IssueSource 是否正确
- IssueStatus 是否正确
- ReviewIssueResponse source/status 是否正确
- ReviewTaskResponse issueSummary 是否正确
- buildIssueSummary 聚合规则是否正确
- RiskLevel.HIGH 是否仍有硬编码风险
- IssueType 是否删除干净

## 5. Backend Test Results

记录实际命令和结果：

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

或实际替代命令。

包括：

- test count
- failures
- errors
- skipped
- build result

## 6. Backend Runtime Verification

记录：

- health curl result
- create curl result
- list curl result
- detail curl result

必须明确列出：

- issueSummary.totalIssues
- issueSummary.highCount
- issueSummary.mediumCount
- issueSummary.lowCount
- issueSummary.riskLevel
- riskLevel
- riskLevel == issueSummary.riskLevel
- issues[].source
- issues[].status

## 7. Frontend Static Validation

说明：

- TypeScript types 是否正确
- getIssueSummary 是否优先 backend issueSummary
- fallback 是否存在
- ReviewTaskDetail 是否消费统一 IssueSummary
- IssueCard 是否展示 source/status
- demo/mock label 是否保留
- 是否无 status workflow scope creep

## 8. Frontend Test Results

记录实际命令和结果：

```bash
cd frontend
npm install
npm run typecheck
npm run build
npm test
```

或实际替代命令。

包括：

- typecheck result
- build result
- test count
- failures

## 9. Frontend Runtime Verification

记录 browser 或替代验证结果：

- backend health display
- task create/list/detail
- summary panel
- source/status badges
- demo/mock label
- loading/error/empty states

如无法浏览器验证，说明原因和替代证据。

## 10. README Validation

说明 README 是否明确：

- backend-computed issue summary
- backend authoritative source
- frontend fallback
- MOCK source
- OPEN status
- in-memory only
- no database
- no GitHub/Semgrep/LLM/ai-service
- current vs planned boundary

## 11. Scope Compliance

明确确认是否没有引入：

- database
- persistence
- JPA/MyBatis/Hibernate
- GitHub API
- Semgrep
- LLM
- ai-service
- auth
- UI library
- chart library
- complex state management
- Round 07 work

## 12. Findings

分为：

### Blocking Findings

如无，写：

```text
None.
```

### Non-blocking Findings

列出 notes。

## 13. Recommended Fixes

如果存在问题，给出具体最小修复建议。

## 14. Recommendation for Qoder

说明是否建议进入 Qoder independent review。

如果通过，建议下一步生成：

```text
tasks/round-06/03-qoder-review-result-contract-hardening-independent-review.md
```
```

---

## 9. Validation Checklist

### Backend

- [ ] `IssueSummaryResponse` exists；
- [ ] `IssueSummaryResponse.totalIssues` exists；
- [ ] `IssueSummaryResponse.highCount` exists；
- [ ] `IssueSummaryResponse.mediumCount` exists；
- [ ] `IssueSummaryResponse.lowCount` exists；
- [ ] `IssueSummaryResponse.riskLevel` exists；
- [ ] `RiskLevel.NONE` exists；
- [ ] `ReviewTaskResponse.issueSummary` exists；
- [ ] `ReviewTaskResponse.riskLevel` remains；
- [ ] `riskLevel == issueSummary.riskLevel`；
- [ ] `ReviewIssueResponse.source` exists；
- [ ] `ReviewIssueResponse.status` exists；
- [ ] `IssueSource.MOCK` exists；
- [ ] `IssueStatus.OPEN` exists；
- [ ] mock issues source = `MOCK`；
- [ ] mock issues status = `OPEN`；
- [ ] issue summary counts are correct；
- [ ] issue summary risk rule is correct；
- [ ] create endpoint returns new contract；
- [ ] list endpoint returns new contract；
- [ ] detail endpoint returns new contract；
- [ ] `ApiResponse<T>` wrapper unchanged；
- [ ] endpoints unchanged；
- [ ] in-memory storage unchanged；
- [ ] `IssueType` removed or explicitly justified；
- [ ] backend tests pass。

### Frontend

- [ ] `RiskLevel` type includes `NONE`；
- [ ] `IssueSource` type exists；
- [ ] `IssueStatus` type exists；
- [ ] `IssueSummary` interface exists；
- [ ] `ReviewIssue.source` exists；
- [ ] `ReviewIssue.status` exists；
- [ ] `ReviewTask.issueSummary` supported；
- [ ] summary panel prefers backend `issueSummary`；
- [ ] fallback summary works；
- [ ] summary logic centralized；
- [ ] issue card renders source badge；
- [ ] issue card renders status badge；
- [ ] demo/mock label retained；
- [ ] loading state works；
- [ ] error state works；
- [ ] empty issues state works；
- [ ] frontend typecheck passes；
- [ ] frontend build passes；
- [ ] frontend tests pass。

### Documentation

- [ ] root README updated；
- [ ] frontend README updated；
- [ ] backend-computed issue summary documented；
- [ ] backend authoritative source documented；
- [ ] frontend fallback documented；
- [ ] `MOCK` source documented；
- [ ] `OPEN` status documented；
- [ ] no real code analysis documented；
- [ ] no GitHub API documented；
- [ ] no Semgrep documented；
- [ ] no LLM / ai-service documented；
- [ ] in-memory only documented；
- [ ] no database documented；
- [ ] current/planned boundary clear；
- [ ] curl examples use `repoUrl` / `prNumber`。

### Scope

- [ ] no database；
- [ ] no persistence；
- [ ] no JPA/MyBatis/Hibernate；
- [ ] no Entity/Repository/Mapper；
- [ ] no migration；
- [ ] no Redis/cache；
- [ ] no GitHub API；
- [ ] no repository clone；
- [ ] no Semgrep；
- [ ] no LLM；
- [ ] no ai-service；
- [ ] no auth；
- [ ] no Swagger/OpenAPI；
- [ ] no chart library；
- [ ] no UI component library；
- [ ] no complex state management；
- [ ] no Round 07 work。

---

## 10. Final Instruction

Codex should validate implementation correctness, contract stability, and scope discipline.

Do not treat this as a feature implementation task.

The most important validation questions are:

```text
Does backend own issue summary and risk aggregation?
Do all endpoints return the same hardened contract?
Does frontend prefer backend issueSummary?
Are source/status fields present and visible?
Is current mock-only state clearly documented?
Did the implementation avoid Round 07 scope?
```

If all are true, return:

```text
ROUND_06_CODEX_VALIDATION_PASSED
```

or:

```text
ROUND_06_CODEX_VALIDATION_PASSED_WITH_NOTES
```

Then recommend moving to Qoder independent review.