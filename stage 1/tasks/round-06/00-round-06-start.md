# tasks/round-06/00-round-06-start.md

# Round 06 Start: Review Result Contract Hardening

## 1. Round Metadata

- Project: CodeReviewX
- Round: Round 06
- Theme: Review Result Contract Hardening
- Task Type: Architecture-guided Implementation Round
- Primary Goal: 收敛 review result contract、统一 risk source-of-truth、为后续 persistence 做稳定前置
- Previous Round:
  - Round 05: Review Result Visualization Mock v1
- Previous Final Verdict:
  - `ROUND_05_ACCEPTED_WITH_NOTES`
- First Task To Generate:
  - `tasks/round-06/01-cursor-review-result-contract-hardening.md`

---

## 2. Current Project State

CodeReviewX 当前已经完成到 Round 05。

系统已经具备：

1. backend-java Spring Boot mock API；
2. frontend React + TypeScript + Vite；
3. ReviewTask create/list/detail flow；
4. backend health display；
5. loading/error/empty states；
6. in-memory task storage；
7. deterministic mock review issues；
8. typed `ReviewIssue` contract；
9. frontend review result summary panel；
10. frontend issue cards；
11. severity/category/file path/line range/recommendation 展示；
12. README 中明确 current mock/demo state；
13. backend/frontend tests 通过；
14. runtime curl/browser validation 通过。

Round 05 最终由 Qoder 判定为：

```text
Verdict: ROUND_05_ACCEPTED_WITH_NOTES
```

无 blocking issue，可以关闭 Round 05。

---

## 3. Round 05 Accepted Baseline

### 3.1 Backend Baseline

当前 backend 已有 typed issue contract：

```text
ReviewIssueResponse:
- id: String
- severity: IssueSeverity
- category: IssueCategory
- filePath: String
- startLine: Integer
- endLine: Integer
- title: String
- description: String
- recommendation: String
```

当前 enum：

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

当前 `ReviewTask.issues`：

```java
List<ReviewIssueResponse>
```

当前 `ReviewTaskResponse.issues`：

```java
List<ReviewIssueResponse>
```

当前 create request contract：

```json
{
  "repoUrl": "https://github.com/example/demo",
  "prNumber": 1
}
```

当前 endpoints：

```text
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

当前 API wrapper：

```json
{
  "success": true,
  "message": "OK",
  "data": {}
}
```

### 3.2 Frontend Baseline

当前 frontend 已有：

```typescript
export type IssueSeverity = 'LOW' | 'MEDIUM' | 'HIGH';

export type IssueCategory =
  | 'BUG'
  | 'SECURITY'
  | 'PERFORMANCE'
  | 'MAINTAINABILITY'
  | 'STYLE'
  | 'TEST';

export interface ReviewIssue {
  id: string;
  severity: IssueSeverity;
  category: IssueCategory;
  filePath: string;
  startLine: number;
  endLine: number | null;
  title: string;
  description: string;
  recommendation: string;
}
```

当前 `ReviewTask.issues`：

```typescript
ReviewIssue[]
```

当前 detail 页面已展示：

1. ReviewTask metadata；
2. demo/mock label；
3. Review Result Summary panel；
4. total/high/medium/low counts；
5. frontend computed risk level；
6. issue cards；
7. severity/category badges；
8. file path + line range；
9. description；
10. recommendation block；
11. empty issues fallback。

---

## 4. Why Round 06 Is Needed

Round 05 已经完成可视化结果页，但 Qoder 最终审查留下了几个重要的非阻塞架构 notes。

这些 notes 不影响 Round 05 关闭，但会影响后续 persistence 和真实 review pipeline。如果不先处理，Round 07 引入数据库时可能会把不稳定 contract 固化进 schema，导致后续返工。

Round 06 的任务不是继续做 UI 花活，也不是进入数据库，而是先把 review result contract 进一步硬化。

核心问题包括：

1. backend `ReviewTask.riskLevel` 与 frontend computed risk 存在双 source-of-truth；
2. backend 尚未返回统一的 `IssueSummary` aggregate；
3. frontend summary panel 当前依赖本地计算；
4. `IssueStatus` 尚不存在；
5. `ReviewIssue.source` 尚未进入 contract；
6. legacy `IssueType` enum 已无引用；
7. `IssueSource` enum 存在但未被使用，未来可能复用；
8. README planned architecture 与 current implementation 可以进一步分区澄清。

---

## 5. Round 06 Strategic Goal

Round 06 的目标是完成：

```text
Review Result Contract Hardening
```

具体目标：

1. backend 成为 review result aggregate / risk level 的 authoritative source；
2. 新增 backend `IssueSummary` DTO；
3. `ReviewTaskResponse` 返回 `issueSummary`；
4. frontend summary panel 消费 backend `issueSummary`；
5. frontend computed summary/risk 只作为 fallback；
6. 新增 `IssueStatus` enum；
7. 为 `ReviewIssueResponse` 增加 `status` 字段；
8. 复用或调整 `IssueSource` enum；
9. 为 `ReviewIssueResponse` 增加 `source` 字段；
10. 删除或处理无用 `IssueType` enum；
11. 保持 deterministic mock issue generation；
12. 保持现有 endpoints 和 `ApiResponse<T>` wrapper；
13. 不引入 database；
14. 不引入 ai-service；
15. 不接 GitHub/Semgrep/LLM；
16. 更新 backend/frontend tests；
17. 更新 README current/planned boundary。

---

## 6. Round 06 Non-goals

Round 06 不是：

1. 数据库持久化；
2. JPA/MyBatis/Hibernate 接入；
3. database migration；
4. Redis/cache；
5. real GitHub integration；
6. Semgrep execution；
7. LLM integration；
8. ai-service integration；
9. real code parsing；
10. repository clone；
11. diff viewer；
12. syntax highlighting；
13. issue filtering/sorting；
14. false-positive workflow；
15. human reviewer comment；
16. auth/user system；
17. deployment；
18. CI/CD；
19. design system；
20. component library migration。

---

## 7. Strict Scope Boundaries

### 7.1 Strictly Forbidden

Round 06 严禁：

1. 引入 database；
2. 引入 persistence layer；
3. 引入 MySQL/PostgreSQL/Redis；
4. 引入 JPA/MyBatis/Hibernate；
5. 创建 Entity/Repository/Mapper；
6. 创建 migration；
7. 调用 `ai-service`；
8. 创建 ai-service client；
9. 调用 GitHub API；
10. clone repository；
11. parse real repository code；
12. 执行 Semgrep；
13. 调用 OpenAI/Anthropic/Gemini/local LLM；
14. 引入 Spring Security；
15. 引入 Swagger/OpenAPI；
16. 引入 Chart.js/ECharts/D3；
17. 引入 Ant Design/Material UI；
18. 引入 Redux/MobX/React Query/XState；
19. 迁移到 Next.js；
20. 实现 SSR；
21. 进入 Round 07；
22. 大规模 UI redesign。

### 7.2 Allowed

Round 06 允许：

1. 新增 backend DTO；
2. 新增 backend enum；
3. 调整 backend response DTO；
4. 调整 backend in-memory model；
5. 调整 mock issue generation；
6. 更新 frontend TypeScript types；
7. 更新 frontend summary panel 数据来源；
8. 保留 frontend computed fallback；
9. 删除 unused enum；
10. 更新 tests；
11. 更新 README；
12. 小范围 CSS/文案调整；
13. runtime curl/browser 验证。

---

## 8. Required Contract Changes

## 8.1 Add IssueSummary DTO

新增 backend DTO，建议路径：

```text
backend-java/src/main/java/com/codereviewx/backend/review/dto/IssueSummaryResponse.java
```

建议字段：

```java
private int totalIssues;
private int highCount;
private int mediumCount;
private int lowCount;
private RiskLevel riskLevel;
```

也可以命名为 `ReviewIssueSummaryResponse`，但必须保持语义清晰。

推荐 JSON shape：

```json
{
  "totalIssues": 3,
  "highCount": 1,
  "mediumCount": 1,
  "lowCount": 1,
  "riskLevel": "HIGH"
}
```

### Required Behavior

backend 应根据 `ReviewTask.issues` 计算 summary：

```text
totalIssues = issues.size()
highCount = count(severity == HIGH)
mediumCount = count(severity == MEDIUM)
lowCount = count(severity == LOW)
```

risk level 计算规则：

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

如当前 `RiskLevel` enum 没有 `NONE`，Round 06 应新增：

```java
NONE
```

建议 `RiskLevel` 最终枚举值：

```text
NONE
LOW
MEDIUM
HIGH
```

不要新增 `CRITICAL`，除非已有代码明确需要。

---

## 8.2 Update ReviewTaskResponse

在 `ReviewTaskResponse` 中新增：

```java
private IssueSummaryResponse issueSummary;
```

最终 response 应至少包含：

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

具体已有字段以当前代码为准，不要破坏旧字段。

### Compatibility Rule

Round 06 不删除旧的 `riskLevel` 字段。

但要明确：

1. `ReviewTaskResponse.riskLevel` 应与 `issueSummary.riskLevel` 保持一致；
2. backend 是 authoritative source；
3. frontend summary panel 优先使用 `issueSummary`；
4. frontend computed risk 只作为 fallback；
5. README 应说明当前 risk summary 由 backend 计算。

---

## 8.3 Add IssueStatus

新增 enum，建议路径：

```text
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueStatus.java
```

建议枚举值：

```java
public enum IssueStatus {
    OPEN,
    RESOLVED,
    FALSE_POSITIVE
}
```

Round 06 mock issues 默认：

```text
status = OPEN
```

不要实现 status update API。  
不要实现 false-positive workflow。  
只作为 contract hardening，为后续 persistence 和 human review 做前置。

---

## 8.4 Add IssueSource

当前代码中可能已有 legacy `IssueSource` enum：

```text
LLM
SEMGREP
```

Round 06 应处理它。

推荐做法：

1. 如果已有 `IssueSource`，复用；
2. 将 enum 值调整为：

```java
public enum IssueSource {
    MOCK,
    SEMGREP,
    LLM,
    MANUAL
}
```

3. 将 `ReviewIssueResponse.source` 设置为 `MOCK`；
4. README 明确当前 issue source 是 `MOCK`。

如果不想加入 `MANUAL`，至少应包含：

```text
MOCK
SEMGREP
LLM
```

但推荐保留 `MANUAL`，为后续 human review/comment 扩展留边界。

---

## 8.5 Update ReviewIssueResponse

在现有字段基础上新增：

```java
private IssueSource source;
private IssueStatus status;
```

最终建议字段：

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

推荐 mock issue JSON：

```json
{
  "id": "ISSUE-1",
  "severity": "HIGH",
  "category": "SECURITY",
  "source": "MOCK",
  "status": "OPEN",
  "filePath": "src/main/java/com/example/AuthController.java",
  "startLine": 42,
  "endLine": 48,
  "title": "Potential missing authorization check",
  "description": "This demo issue indicates that a sensitive endpoint should explicitly check authorization before processing the request.",
  "recommendation": "Add an authorization guard before the business logic and cover the behavior with a controller test."
}
```

---

## 8.6 Remove or Resolve IssueType

当前 `IssueType` 已被 Qoder 确认无引用。Round 06 应处理它。

推荐：

```text
Delete IssueType enum.
```

理由：

1. `IssueCategory` 已承担 classification 语义；
2. `IssueType` 与 `IssueCategory` 语义重叠；
3. 保留无引用 enum 会误导后续实现；
4. 删除不影响 runtime behavior。

如果 Cursor 判断删除会引发额外风险，可以暂不删除，但必须在 handoff 中说明原因。默认期望是删除。

---

## 9. Frontend Required Changes

## 9.1 Update Types

更新 frontend `reviewTask.ts`。

新增：

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

更新 `ReviewIssue`：

```typescript
export interface ReviewIssue {
  id: string;
  severity: IssueSeverity;
  category: IssueCategory;
  source: IssueSource;
  status: IssueStatus;
  filePath: string;
  startLine: number;
  endLine: number | null;
  title: string;
  description: string;
  recommendation: string;
}
```

更新 `ReviewTask`：

```typescript
issueSummary?: IssueSummary;
```

如果想严格对齐 backend，也可以设为必填：

```typescript
issueSummary: IssueSummary;
```

考虑兼容旧数据和 tests，推荐第一步用 optional，然后 summary panel fallback 到 computed summary。

---

## 9.2 Update ReviewTaskDetail Summary Panel

当前 summary panel 基于 frontend `issues` 计算。

Round 06 应改为：

```text
Prefer task.issueSummary from backend.
If task.issueSummary is missing, compute fallback from task.issues.
```

建议函数：

```typescript
function getIssueSummary(task: ReviewTask): IssueSummary {
  if (task.issueSummary) {
    return task.issueSummary;
  }

  return computeIssueSummaryFromIssues(task.issues);
}
```

然后 UI 只消费 `summary`：

```typescript
const summary = getIssueSummary(task);
```

不要在多个地方重复计算 high/medium/low/risk。

---

## 9.3 Update Issue Cards

issue card 应展示新增字段：

1. `source` badge；
2. `status` badge。

建议 card header：

```text
[HIGH] [SECURITY] [MOCK] [OPEN]
```

要求：

1. 不要引入复杂 status workflow；
2. 不要新增编辑状态；
3. 不要新增 resolve/false-positive 按钮；
4. 只展示字段。

---

## 9.4 Keep Demo Label

必须保留清晰 demo label：

```text
Demo result — no real code was analyzed
```

或类似文案。

新增 source 后，可以补充：

```text
Issue source: MOCK
```

但不要让用户误以为是真实分析。

---

## 10. Backend Required Changes

## 10.1 Service Layer

在 `ReviewTaskService` 中新增 summary 计算逻辑。

建议：

```java
private IssueSummaryResponse buildIssueSummary(List<ReviewIssueResponse> issues) {
    int high = ...
    int medium = ...
    int low = ...
    RiskLevel riskLevel = ...
    return new IssueSummaryResponse(...);
}
```

要求：

1. create/list/detail response 都有 `issueSummary`；
2. `ReviewTask.riskLevel` 与 `issueSummary.riskLevel` 保持一致；
3. mock issues 默认 `source=MOCK`；
4. mock issues 默认 `status=OPEN`；
5. 不改变 endpoints；
6. 不改变 wrapper；
7. 不引入 persistence。

如果当前 `ReviewTask` model 持有 `riskLevel`，create 时应使用同一套 summary 计算逻辑设置：

```java
IssueSummaryResponse issueSummary = buildIssueSummary(issues);
task.setRiskLevel(issueSummary.getRiskLevel());
```

不要继续硬编码 `RiskLevel.HIGH`，除非明确只是 fallback。

---

## 10.2 DTO/Model Updates

更新：

```text
ReviewIssueResponse
ReviewTaskResponse
ReviewTask
```

如 `ReviewTask` model 不需要持有 `IssueSummaryResponse`，可以只在 `toResponse()` 时动态计算。

推荐：

1. `ReviewTask` 继续持有 `issues` 和 `riskLevel`；
2. `ReviewTaskResponse` 持有 `issueSummary`；
3. `toResponse()` 计算 `issueSummary`；
4. 这样避免在 in-memory model 中重复存 summary。

---

## 10.3 Tests

backend tests 必须新增/更新，至少覆盖：

1. create response contains `issueSummary`；
2. `issueSummary.totalIssues = 3`；
3. `issueSummary.highCount = 1`；
4. `issueSummary.mediumCount = 1`；
5. `issueSummary.lowCount = 1`；
6. `issueSummary.riskLevel = HIGH`；
7. `ReviewTaskResponse.riskLevel == issueSummary.riskLevel`；
8. each issue contains `source=MOCK`；
9. each issue contains `status=OPEN`；
10. list response contains `issueSummary`；
11. detail response contains `issueSummary`；
12. no `IssueType` references remain if deleted；
13. existing create/list/detail tests still pass。

---

## 11. Frontend Tests

frontend tests 必须新增/更新，至少覆盖：

1. summary panel prefers backend `issueSummary`；
2. fallback summary works when `issueSummary` is missing；
3. total/high/medium/low counts render from backend summary；
4. risk level renders from backend summary；
5. issue card renders source badge；
6. issue card renders status badge；
7. existing issue severity/category/filePath/line/recommendation tests still pass；
8. empty issues fallback still works；
9. loading/error states still work；
10. typecheck/build/tests pass。

---

## 12. README Update Requirements

更新根 README 和 frontend README。

必须明确：

1. Round 06 introduces backend-computed issue summary；
2. backend is authoritative source for risk summary；
3. frontend only uses computed summary as fallback；
4. current issue source is `MOCK`；
5. current issue status is `OPEN`；
6. no real repository code is analyzed；
7. no GitHub API is called；
8. no Semgrep is executed；
9. no LLM / AI service is called；
10. data remains in-memory；
11. database is still not introduced；
12. planned MVP / future architecture 与 current implementation 分区更清楚。

建议 README 分区：

```text
Current Implementation
Planned Architecture
Demo / Mock Data Notice
```

不要让读者误以为 GitHub/Semgrep/LLM 已经实现。

---

## 13. Runtime Verification Requirements

完成实现后必须验证：

### 13.1 Backend

启动 backend：

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

或按实际环境：

```bash
cd backend-java
mvn spring-boot:run
```

Health：

```bash
curl http://localhost:8080/api/health
```

Create：

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/round-06-demo",
    "prNumber": 6
  }'
```

必须确认 response：

```text
success=true
data.issues.length=3
data.issueSummary.totalIssues=3
data.issueSummary.highCount=1
data.issueSummary.mediumCount=1
data.issueSummary.lowCount=1
data.issueSummary.riskLevel=HIGH
data.riskLevel=HIGH
data.issues[0].source=MOCK
data.issues[0].status=OPEN
```

List：

```bash
curl http://localhost:8080/api/review-tasks
```

Detail：

```bash
curl http://localhost:8080/api/review-tasks/{id}
```

确认 list/detail 也返回 `issueSummary`。

### 13.2 Frontend

启动 frontend：

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

浏览器验证：

1. backend health 正常；
2. 创建 ReviewTask；
3. list 出现 task；
4. detail 自动或手动打开；
5. summary panel 显示 backend issueSummary；
6. risk level 正确；
7. issue card 显示 source badge；
8. issue card 显示 status badge；
9. demo/mock label 清晰；
10. empty/loading/error states 不破坏。

---

## 14. Required Commands

Backend：

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

或：

```bash
cd backend-java
mvn test
```

Frontend：

```bash
cd frontend
npm install
npm run typecheck
npm run build
npm test
```

如 package script 名称不同，按实际 `package.json` 执行并记录。

---

## 15. Suggested Round 06 Task Sequence

Round 06 建议仍采用 Cursor → Codex → Qoder 三段式。

### 15.1 Cursor Implementation

生成：

```text
tasks/round-06/01-cursor-review-result-contract-hardening.md
```

职责：

1. 实现 backend `IssueSummaryResponse`；
2. 实现 backend risk aggregation；
3. 新增/调整 `IssueSource`；
4. 新增 `IssueStatus`；
5. 删除 `IssueType`；
6. 更新 frontend types；
7. 更新 detail summary panel；
8. 展示 source/status badges；
9. 更新 tests；
10. 更新 README；
11. 输出 Cursor handoff。

### 15.2 Codex Validation

生成：

```text
tasks/round-06/02-codex-review-result-contract-hardening-validation.md
```

职责：

1. 独立运行 tests；
2. 验证 API response；
3. 验证 `issueSummary` contract；
4. 验证 frontend source-of-truth；
5. 验证 source/status fields；
6. 验证 README；
7. 验证 scope；
8. 输出 Codex handoff。

### 15.3 Qoder Independent Review

生成：

```text
tasks/round-06/03-qoder-review-result-contract-hardening-independent-review.md
```

职责：

1. 审查 contract hardening 是否足够；
2. 判断是否可以进入 persistence；
3. 审查 risk aggregation architecture；
4. 审查 issue lifecycle/source 是否合理；
5. 审查 test/runtime/doc；
6. 给出 Round 07 建议；
7. 输出 Qoder handoff。

---

## 16. Acceptance Criteria for Round 06

Round 06 完成时必须满足：

### Backend

- [ ] 新增 `IssueSummaryResponse`；
- [ ] `ReviewTaskResponse.issueSummary` 存在；
- [ ] `issueSummary.totalIssues` 正确；
- [ ] `issueSummary.highCount` 正确；
- [ ] `issueSummary.mediumCount` 正确；
- [ ] `issueSummary.lowCount` 正确；
- [ ] `issueSummary.riskLevel` 正确；
- [ ] `ReviewTaskResponse.riskLevel` 与 `issueSummary.riskLevel` 一致；
- [ ] 新增或复用 `IssueSource`；
- [ ] `ReviewIssueResponse.source` 存在；
- [ ] mock issue source 为 `MOCK`；
- [ ] 新增 `IssueStatus`；
- [ ] `ReviewIssueResponse.status` 存在；
- [ ] mock issue status 为 `OPEN`；
- [ ] 删除或明确处理 unused `IssueType`；
- [ ] create/list/detail endpoints 不变；
- [ ] `ApiResponse<T>` wrapper 不变；
- [ ] in-memory storage 不变；
- [ ] backend tests 通过。

### Frontend

- [ ] 新增 `IssueSummary` type；
- [ ] 新增 `RiskLevel` type；
- [ ] 新增 `IssueSource` type；
- [ ] 新增 `IssueStatus` type；
- [ ] `ReviewTask.issueSummary` 支持；
- [ ] summary panel 优先使用 backend `issueSummary`；
- [ ] frontend computed summary 仅作为 fallback；
- [ ] issue card 展示 source badge；
- [ ] issue card 展示 status badge；
- [ ] existing issue visualization 不破坏；
- [ ] demo/mock label 保留；
- [ ] loading/error/empty states 不破坏；
- [ ] frontend typecheck 通过；
- [ ] frontend build 通过；
- [ ] frontend tests 通过。

### Documentation

- [ ] README 说明 backend-computed issue summary；
- [ ] README 说明 backend 是 risk authoritative source；
- [ ] README 说明 frontend computed summary 是 fallback；
- [ ] README 说明 current issue source 是 `MOCK`；
- [ ] README 说明 current issue status 是 `OPEN`；
- [ ] README 明确 current implementation vs planned architecture；
- [ ] README 明确无 database；
- [ ] README 明确无 GitHub/Semgrep/LLM/ai-service；
- [ ] curl 示例使用 `repoUrl` / `prNumber`。

### Scope

- [ ] 不引入 database；
- [ ] 不引入 persistence；
- [ ] 不引入 MyBatis/JPA/Hibernate；
- [ ] 不调用 ai-service；
- [ ] 不调用 GitHub；
- [ ] 不执行 Semgrep；
- [ ] 不调用 LLM；
- [ ] 不 clone repository；
- [ ] 不 parse real code；
- [ ] 不引入 chart/component library；
- [ ] 不引入复杂 state management；
- [ ] 不进入 Round 07。

---

## 17. Known Risks to Track

### 17.1 API Contract Expansion Risk

Round 06 会扩展 response contract：

```text
ReviewTaskResponse.issueSummary
ReviewIssueResponse.source
ReviewIssueResponse.status
```

这是 additive change，理论上兼容旧 frontend，但 tests 必须覆盖。

### 17.2 Frontend Fallback Complexity

frontend 需要优先使用 backend summary，同时保留 fallback。必须避免重复计算逻辑散落在多个地方。

### 17.3 RiskLevel Enum Compatibility

如果新增 `NONE`，必须同步 backend/frontend tests，并确认 UI 能展示 `No Issues` 或等价文案。

### 17.4 Legacy Enum Cleanup

删除 `IssueType` 应通过 grep 确认无引用，不要误删 `IssueCategory`。

### 17.5 README Clarity

README 需要更清楚区分 current implementation 和 planned architecture，避免用户误以为当前系统已经具备真实代码审查能力。

---

## 18. Recommended Round 07 Direction

如果 Round 06 顺利完成，Round 07 建议进入：

```text
Database Persistence v1
```

Round 07 才考虑：

1. 引入 database；
2. persist `ReviewTask`；
3. persist `ReviewIssue`；
4. persist `IssueSummary` 或动态计算；
5. 保持 mock generation；
6. 保持 API contract；
7. 准备真实 review pipeline。

Round 07 仍不建议直接进入 LLM/GitHub/Semgrep，除非 persistence 已稳定。

---

## 19. Final Instruction for Round 06

Round 06 的核心原则：

```text
先硬化 Review Result contract，再进入 persistence。
```

本轮只处理：

```text
IssueSummary
Risk source-of-truth
IssueSource
IssueStatus
IssueType cleanup
Frontend summary source alignment
README current/planned clarification
```

不要做：

```text
database
ai-service
GitHub
Semgrep
LLM
diff viewer
filtering/sorting
auth
deployment
```

第一步请生成：

```text
tasks/round-06/01-cursor-review-result-contract-hardening.md
```