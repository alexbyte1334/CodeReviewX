# tasks/round-06/01-cursor-review-result-contract-hardening.md

# Cursor Task: Round 06 - Review Result Contract Hardening

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 06
- Task ID: `01-cursor-review-result-contract-hardening`
- Role: Cursor Implementation
- Theme: Review Result Contract Hardening
- Previous Round: Round 05 - Review Result Visualization Mock v1
- Previous Verdict: `ROUND_05_ACCEPTED_WITH_NOTES`
- Primary Goal: 硬化 review result contract，统一 risk summary source-of-truth，为 Round 07 persistence 做稳定前置

---

## 2. Background

CodeReviewX 当前已完成 Round 05，系统已经具备：

1. Spring Boot backend mock API；
2. React + TypeScript + Vite frontend；
3. ReviewTask create/list/detail flow；
4. in-memory task storage；
5. deterministic mock review issues；
6. typed `ReviewIssue` contract；
7. frontend review result summary panel；
8. issue cards；
9. severity/category/file path/line range/recommendation 展示；
10. backend/frontend tests；
11. runtime curl/browser validation；
12. README 中明确当前 mock/demo 状态。

Round 05 已被接受，但留下了几个会影响后续 persistence 的非阻塞架构问题：

1. backend `ReviewTask.riskLevel` 与 frontend computed risk 存在双 source-of-truth；
2. backend 尚未返回统一的 `IssueSummary` aggregate；
3. frontend summary panel 当前依赖本地计算；
4. `IssueStatus` 尚不存在；
5. `ReviewIssue.source` 尚未进入 contract；
6. legacy `IssueType` enum 已无引用；
7. `IssueSource` enum 存在但未被实际使用；
8. README 对 current implementation 和 planned architecture 的边界仍可更清晰。

Round 06 的目标是先稳定 review result contract，再进入后续数据库持久化。

---

## 3. Non-negotiable Scope Boundary

本任务只做 contract hardening。

### 3.1 Must Not Do

严禁实现或引入：

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

### 3.2 Allowed

允许：

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
12. 小范围 CSS / 文案调整；
13. runtime curl/browser 验证。

---

## 4. Current Baseline

### 4.1 Backend Existing Contract

当前 backend 已有 `ReviewIssueResponse`：

```text
id: String
severity: IssueSeverity
category: IssueCategory
filePath: String
startLine: Integer
endLine: Integer
title: String
description: String
recommendation: String
```

当前 `IssueSeverity`：

```text
LOW
MEDIUM
HIGH
```

当前 `IssueCategory`：

```text
BUG
SECURITY
PERFORMANCE
MAINTAINABILITY
STYLE
TEST
```

当前 `ReviewTask.issues`：

```java
List<ReviewIssueResponse>
```

当前 `ReviewTaskResponse.issues`：

```java
List<ReviewIssueResponse>
```

当前 endpoints：

```text
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

当前 request：

```json
{
  "repoUrl": "https://github.com/example/demo",
  "prNumber": 1
}
```

当前 response wrapper：

```json
{
  "success": true,
  "message": "OK",
  "data": {}
}
```

### 4.2 Frontend Existing Contract

当前 frontend 已有类似类型：

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

当前 detail 页面已经展示：

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

## 5. Required Backend Implementation

## 5.1 Add `IssueSummaryResponse`

新增 DTO，建议路径：

```text
backend-java/src/main/java/com/codereviewx/backend/review/dto/IssueSummaryResponse.java
```

字段：

```java
private int totalIssues;
private int highCount;
private int mediumCount;
private int lowCount;
private RiskLevel riskLevel;
```

目标 JSON：

```json
{
  "totalIssues": 3,
  "highCount": 1,
  "mediumCount": 1,
  "lowCount": 1,
  "riskLevel": "HIGH"
}
```

实现要求：

1. 使用项目当前 DTO 风格；
2. 如果项目使用 constructor / getter / setter / Lombok，请保持一致；
3. 不要引入新框架；
4. 不要改变 `ApiResponse<T>` wrapper。

---

## 5.2 Harden `RiskLevel`

检查现有 `RiskLevel` enum。

最终应支持：

```java
NONE,
LOW,
MEDIUM,
HIGH
```

如果当前没有 `NONE`，请新增。

不要新增 `CRITICAL`。

---

## 5.3 Add Summary Aggregation Logic

在 `ReviewTaskService` 或当前负责 task response assembly 的服务中新增统一 summary 构建逻辑。

建议方法：

```java
private IssueSummaryResponse buildIssueSummary(List<ReviewIssueResponse> issues) {
    int highCount = 0;
    int mediumCount = 0;
    int lowCount = 0;

    for (ReviewIssueResponse issue : issues) {
        if (issue.getSeverity() == IssueSeverity.HIGH) {
            highCount++;
        } else if (issue.getSeverity() == IssueSeverity.MEDIUM) {
            mediumCount++;
        } else if (issue.getSeverity() == IssueSeverity.LOW) {
            lowCount++;
        }
    }

    RiskLevel riskLevel;
    if (highCount > 0) {
        riskLevel = RiskLevel.HIGH;
    } else if (mediumCount > 0) {
        riskLevel = RiskLevel.MEDIUM;
    } else if (lowCount > 0) {
        riskLevel = RiskLevel.LOW;
    } else {
        riskLevel = RiskLevel.NONE;
    }

    return new IssueSummaryResponse(
        issues.size(),
        highCount,
        mediumCount,
        lowCount,
        riskLevel
    );
}
```

请根据当前代码风格调整构造方式。

规则必须为：

```text
totalIssues = issues.size()

If highCount > 0:
  riskLevel = HIGH
Else if mediumCount > 0:
  riskLevel = MEDIUM
Else if lowCount > 0:
  riskLevel = LOW
Else:
  riskLevel = NONE
```

---

## 5.4 Update `ReviewTaskResponse`

在 `ReviewTaskResponse` 中新增：

```java
private IssueSummaryResponse issueSummary;
```

最终 response 至少应包含：

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

以当前已有字段为准，不要删除旧字段。

### Compatibility Rule

不要删除旧的 `riskLevel` 字段。

必须保证：

```text
ReviewTaskResponse.riskLevel == ReviewTaskResponse.issueSummary.riskLevel
```

backend 是 authoritative source。

frontend 在 Round 06 后应优先消费 `issueSummary`，而不是自行计算作为主路径。

---

## 5.5 Update `ReviewTask` In-memory Model

根据当前代码结构选择最小改动方案：

### Preferred Option

`ReviewTask` 继续持有：

```text
issues
riskLevel
```

`ReviewTaskResponse` 在 response assembly 阶段动态带上 `issueSummary`。

创建任务时：

```java
List<ReviewIssueResponse> issues = buildMockIssues();
IssueSummaryResponse issueSummary = buildIssueSummary(issues);

ReviewTask task = new ReviewTask(...);
task.setIssues(issues);
task.setRiskLevel(issueSummary.getRiskLevel());
```

不要继续硬编码：

```java
RiskLevel.HIGH
```

除非它只是测试 fallback，且不会影响正式 mock response。

---

## 5.6 Add `IssueStatus`

新增 enum，建议路径：

```text
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueStatus.java
```

内容：

```java
package com.codereviewx.backend.review.enums;

public enum IssueStatus {
    OPEN,
    RESOLVED,
    FALSE_POSITIVE
}
```

要求：

1. mock issues 默认 `OPEN`；
2. 不实现 status update API；
3. 不实现 resolve / false-positive workflow；
4. 只进入 response contract。

---

## 5.7 Add or Reuse `IssueSource`

检查当前是否已有 `IssueSource` enum。

如果已有，请复用并调整为：

```java
public enum IssueSource {
    MOCK,
    SEMGREP,
    LLM,
    MANUAL
}
```

如果没有，请新增，建议路径：

```text
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSource.java
```

要求：

1. mock issues 默认 `MOCK`；
2. 不执行 Semgrep；
3. 不调用 LLM；
4. 不实现 manual workflow；
5. README 明确当前 source 是 `MOCK`。

如判断 `MANUAL` 与当前架构冲突，至少保留：

```text
MOCK
SEMGREP
LLM
```

但默认推荐包含 `MANUAL`，为后续 human review/comment 留扩展点。

---

## 5.8 Update `ReviewIssueResponse`

在现有字段基础上新增：

```java
private IssueSource source;
private IssueStatus status;
```

最终建议字段顺序：

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

目标 mock issue JSON：

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

## 5.9 Update Deterministic Mock Issue Generation

现有 mock issues 应保持 deterministic。

要求：

1. 仍然生成 3 个 mock issues；
2. severity 分布保持：
   - 1 HIGH；
   - 1 MEDIUM；
   - 1 LOW；
3. 每个 issue 包含：
   - `source = MOCK`；
   - `status = OPEN`；
4. 不分析真实代码；
5. 不调用任何外部服务；
6. 不引入异步 pipeline。

---

## 5.10 Remove or Resolve `IssueType`

检查是否存在 legacy enum：

```text
IssueType
```

推荐删除。

删除前请执行搜索确认：

```bash
grep -R "IssueType" backend-java/src || true
```

如果无引用，删除 enum 文件。

如果删除会引发额外风险，可以暂不删除，但必须在 handoff 中说明：

1. 文件位置；
2. 为什么暂不删除；
3. 当前引用情况；
4. 后续建议。

默认期望：删除无引用 `IssueType`。

---

## 6. Required Frontend Implementation

## 6.1 Update TypeScript Types

更新 frontend review task 类型文件，通常类似：

```text
frontend/src/types/reviewTask.ts
```

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

建议先设为 optional，以兼容旧 mock/test fixture 和 fallback 测试。

---

## 6.2 Centralize Frontend Summary Logic

当前 summary panel 基于 frontend `issues` 计算。

Round 06 后必须改为：

```text
优先使用 backend task.issueSummary
缺失时才 fallback 到 frontend computed summary
```

建议新增或调整工具函数：

```typescript
export function computeIssueSummaryFromIssues(issues: ReviewIssue[]): IssueSummary {
  const highCount = issues.filter((issue) => issue.severity === 'HIGH').length;
  const mediumCount = issues.filter((issue) => issue.severity === 'MEDIUM').length;
  const lowCount = issues.filter((issue) => issue.severity === 'LOW').length;

  let riskLevel: RiskLevel = 'NONE';

  if (highCount > 0) {
    riskLevel = 'HIGH';
  } else if (mediumCount > 0) {
    riskLevel = 'MEDIUM';
  } else if (lowCount > 0) {
    riskLevel = 'LOW';
  }

  return {
    totalIssues: issues.length,
    highCount,
    mediumCount,
    lowCount,
    riskLevel,
  };
}

export function getIssueSummary(task: ReviewTask): IssueSummary {
  return task.issueSummary ?? computeIssueSummaryFromIssues(task.issues);
}
```

可根据当前项目结构放到：

```text
frontend/src/utils/reviewSummary.ts
```

或当前已有 detail component 附近。

要求：

1. summary 逻辑不要散落在多个 component；
2. detail summary panel 只消费统一的 `summary`；
3. frontend computed summary 只作为 fallback；
4. UI 不应再把 frontend computed risk 作为主 source-of-truth。

---

## 6.3 Update ReviewTask Detail Summary Panel

在 detail 页面中改为：

```typescript
const summary = getIssueSummary(task);
```

然后 summary panel 统一使用：

```typescript
summary.totalIssues
summary.highCount
summary.mediumCount
summary.lowCount
summary.riskLevel
```

必须展示 backend summary 中的 risk level。

如果 `issueSummary` 缺失，fallback summary 仍能正常展示。

---

## 6.4 Update Issue Cards

issue card header 增加 source/status badges。

建议显示：

```text
[HIGH] [SECURITY] [MOCK] [OPEN]
```

要求：

1. 保留 severity badge；
2. 保留 category badge；
3. 新增 source badge；
4. 新增 status badge；
5. 不新增 resolve 按钮；
6. 不新增 false-positive 按钮；
7. 不新增 issue editing；
8. 不新增 workflow 状态切换；
9. 保留 description/recommendation/file path/line range 展示。

---

## 6.5 Preserve Demo / Mock Label

detail 页必须继续清晰展示 demo 状态。

推荐保留或调整为：

```text
Demo result — no real code was analyzed.
Issue source: MOCK.
```

不要让用户误以为：

1. 已经分析真实 repo；
2. 已经调用 GitHub；
3. 已经执行 Semgrep；
4. 已经调用 LLM；
5. 已经接入 ai-service。

---

## 6.6 Empty / Loading / Error States

确认以下状态不被破坏：

1. backend health loading；
2. backend health error；
3. review task list empty；
4. review task create loading；
5. review task detail loading；
6. review task detail error；
7. no issues fallback；
8. existing recommendation block。

---

## 7. Backend Tests

更新或新增 backend tests。

至少覆盖：

1. `POST /api/review-tasks` response contains `issueSummary`；
2. `issueSummary.totalIssues = 3`；
3. `issueSummary.highCount = 1`；
4. `issueSummary.mediumCount = 1`；
5. `issueSummary.lowCount = 1`；
6. `issueSummary.riskLevel = HIGH`；
7. `data.riskLevel == data.issueSummary.riskLevel`；
8. each issue contains `source = MOCK`；
9. each issue contains `status = OPEN`；
10. `GET /api/review-tasks` list response contains `issueSummary`；
11. `GET /api/review-tasks/{id}` detail response contains `issueSummary`；
12. existing create/list/detail tests still pass；
13. no `IssueType` reference remains if deleted。

Suggested assertions for MockMvc JSON path:

```java
.andExpect(jsonPath("$.success").value(true))
.andExpect(jsonPath("$.data.issueSummary.totalIssues").value(3))
.andExpect(jsonPath("$.data.issueSummary.highCount").value(1))
.andExpect(jsonPath("$.data.issueSummary.mediumCount").value(1))
.andExpect(jsonPath("$.data.issueSummary.lowCount").value(1))
.andExpect(jsonPath("$.data.issueSummary.riskLevel").value("HIGH"))
.andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
.andExpect(jsonPath("$.data.issues[0].source").value("MOCK"))
.andExpect(jsonPath("$.data.issues[0].status").value("OPEN"));
```

根据现有测试风格调整。

---

## 8. Frontend Tests

更新或新增 frontend tests。

至少覆盖：

1. summary panel prefers backend `issueSummary`；
2. fallback summary works when `issueSummary` is missing；
3. total/high/medium/low counts render from backend summary；
4. risk level renders from backend summary；
5. issue card renders source badge；
6. issue card renders status badge；
7. existing severity/category/filePath/line/recommendation tests still pass；
8. empty issues fallback still works；
9. loading/error states still work；
10. typecheck/build/tests pass。

建议增加一个明确测试：即使 `issues` 与 `issueSummary` 不一致，summary panel 仍优先展示 backend `issueSummary`。

示例意图：

```typescript
const task = {
  ...mockTask,
  issueSummary: {
    totalIssues: 99,
    highCount: 9,
    mediumCount: 8,
    lowCount: 7,
    riskLevel: 'HIGH',
  },
};

render(<ReviewTaskDetail task={task} />);

expect(screen.getByText(/99/)).toBeInTheDocument();
expect(screen.getByText(/9/)).toBeInTheDocument();
expect(screen.getByText(/8/)).toBeInTheDocument();
expect(screen.getByText(/7/)).toBeInTheDocument();
```

根据当前 component/API mock 结构调整。

---

## 9. README Updates

更新根 README 和 frontend README。

必须明确以下内容：

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
12. planned MVP / future architecture 与 current implementation 分区更清楚；
13. curl 示例继续使用 `repoUrl` / `prNumber`。

建议 README 分区：

```text
Current Implementation
Demo / Mock Data Notice
Current Review Result Contract
Planned Architecture
Out of Scope for Current Version
```

### Suggested README Text

可参考：

```markdown
## Current Implementation

CodeReviewX currently runs as a mock/demo review task system.

The backend exposes Spring Boot APIs for creating, listing, and reading review tasks. Review tasks are stored in memory. Review issues are generated deterministically as mock data.

Round 06 introduces a backend-computed issue summary. The backend is the authoritative source for review result aggregation and risk level. The frontend prefers `issueSummary` from the backend and only computes a local summary as a compatibility fallback.

Current issue source is `MOCK`. Current issue status is `OPEN`.

## Demo / Mock Data Notice

No real repository code is analyzed in the current implementation. The system does not call the GitHub API, does not clone repositories, does not execute Semgrep, and does not call any LLM or AI service.

## Planned Architecture

Future rounds may introduce database persistence, real repository ingestion, static analysis, LLM-assisted review, and human review workflows. These are not part of the current implementation.
```

---

## 10. Runtime Verification

完成代码后执行以下验证。

### 10.1 Backend Test

```bash
cd backend-java
mvn test
```

如本地必须指定 Java 17：

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

### 10.2 Backend Runtime

启动 backend：

```bash
cd backend-java
mvn spring-boot:run
```

或：

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
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

必须确认：

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

确认 list/detail 也返回：

```text
issueSummary
issues[].source
issues[].status
```

---

## 11. Frontend Verification

安装依赖：

```bash
cd frontend
npm install
```

类型检查：

```bash
npm run typecheck
```

构建：

```bash
npm run build
```

测试：

```bash
npm test
```

如果当前项目没有 `npm test` 或 script 名称不同，请：

1. 查看 `package.json`；
2. 执行实际存在的 test script；
3. 在 handoff 中记录实际命令和结果。

启动 frontend：

```bash
npm run dev -- --host 127.0.0.1
```

浏览器验证：

1. backend health 正常；
2. 创建 ReviewTask；
3. list 出现 task；
4. detail 可打开；
5. summary panel 显示 backend `issueSummary`；
6. risk level 显示正确；
7. issue card 显示 source badge；
8. issue card 显示 status badge；
9. demo/mock label 清晰；
10. empty/loading/error states 不破坏。

---

## 12. Acceptance Criteria

### 12.1 Backend

- [ ] 新增 `IssueSummaryResponse`；
- [ ] `ReviewTaskResponse.issueSummary` 存在；
- [ ] `issueSummary.totalIssues` 正确；
- [ ] `issueSummary.highCount` 正确；
- [ ] `issueSummary.mediumCount` 正确；
- [ ] `issueSummary.lowCount` 正确；
- [ ] `issueSummary.riskLevel` 正确；
- [ ] `ReviewTaskResponse.riskLevel` 与 `issueSummary.riskLevel` 一致；
- [ ] `RiskLevel` 支持 `NONE`；
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

### 12.2 Frontend

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

### 12.3 Documentation

- [ ] README 说明 backend-computed issue summary；
- [ ] README 说明 backend 是 risk authoritative source；
- [ ] README 说明 frontend computed summary 是 fallback；
- [ ] README 说明 current issue source 是 `MOCK`；
- [ ] README 说明 current issue status 是 `OPEN`；
- [ ] README 明确 current implementation vs planned architecture；
- [ ] README 明确无 database；
- [ ] README 明确无 GitHub/Semgrep/LLM/ai-service；
- [ ] curl 示例使用 `repoUrl` / `prNumber`。

### 12.4 Scope

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

## 13. Expected Handoff Output

完成后请生成 handoff 文件：

```text
tasks/round-06/01-cursor-review-result-contract-hardening-handoff.md
```

handoff 必须包含：

```markdown
# Round 06 Cursor Handoff: Review Result Contract Hardening

## 1. Summary

简述本次完成的 backend/frontend/doc/test 工作。

## 2. Files Changed

列出关键文件：

- backend DTO
- backend enum
- backend service/model/controller/test
- frontend types
- frontend components/utils/tests
- README files

## 3. Backend Contract Result

贴出或描述最终 JSON shape：

- ReviewTaskResponse.issueSummary
- ReviewIssueResponse.source
- ReviewIssueResponse.status
- ReviewTaskResponse.riskLevel 与 issueSummary.riskLevel 一致性

## 4. Frontend Result

说明：

- summary panel 如何优先使用 backend issueSummary
- fallback 如何工作
- source/status badge 如何展示

## 5. Tests Run

列出实际执行命令和结果：

```bash
cd backend-java
mvn test
```

```bash
cd frontend
npm run typecheck
npm run build
npm test
```

如有命令不存在，说明替代命令。

## 6. Runtime Verification

记录实际 curl/browser 验证结果。

至少包括：

- health
- create
- list
- detail
- frontend detail page

## 7. IssueType Handling

说明：

- 是否删除 `IssueType`
- 删除前 grep 结果
- 如果未删除，原因是什么

## 8. Scope Compliance

明确确认未引入：

- database
- persistence
- GitHub
- Semgrep
- LLM
- ai-service
- auth
- component library
- complex state management

## 9. Known Issues / Follow-ups

列出剩余非阻塞问题。

## 10. Recommendation for Codex

说明建议 Codex 在下一步重点验证：

- issueSummary contract
- risk source-of-truth
- source/status fields
- frontend fallback behavior
- README current/planned boundary
```

---

## 14. Final Instruction

本任务的成功标准不是增加功能数量，而是降低后续 persistence 的 schema/contract 返工风险。

请优先保证：

```text
backend issueSummary authoritative
riskLevel source-of-truth 收敛
ReviewIssue source/status contract 明确
frontend summary fallback 清晰
README current/planned 边界清楚
```

不要做任何超出 Round 06 scope 的实现。