# tasks/round-05/01-cursor-review-result-visualization-mock-v1.md

# Cursor Task: Round 05 Review Result Visualization Mock v1

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 05
- Task ID: `01-cursor-review-result-visualization-mock-v1`
- Executor: Cursor
- Task Type: Implementation
- Depends On:
  - `tasks/round-05/00-round-05-start.md`
  - Round 04 accepted frontend ReviewTask Mock UI v1
  - Round 03 accepted backend ReviewTask mock API
- Expected Handoff:
  - `handoff/round-05/01-cursor-review-result-visualization-mock-v1-handoff.md`

---

## 2. Background

Round 04 已完成 frontend ReviewTask Mock UI v1，并通过 Codex validation 与 Qoder independent review。

当前系统已经具备：

1. backend-java mock API；
2. frontend React + TypeScript + Vite；
3. create ReviewTask；
4. list ReviewTask；
5. detail ReviewTask；
6. backend health display；
7. loading / error / empty state；
8. frontend 与 backend 基础联调；
9. in-memory mock storage；
10. 最小 CORS 配置。

但是当前 ReviewTask detail 页面仍然缺少真实审查结果体验：

```text
issues 仍然是弱类型 / 空数组 / unknown[]
```

Round 05 的目标是先做 review result visualization mock v1，而不是进入数据库持久化。

---

## 3. Round 05 Strategic Goal

本任务目标是在不接入真实 AI、不调用 GitHub、不执行 Semgrep、不引入数据库的前提下，让 CodeReviewX 呈现更接近真实产品使用场景的代码审查结果页面。

核心目标：

1. 收敛 `ReviewIssue` typed contract；
2. backend 返回 deterministic mock issues；
3. frontend detail 页面展示 mock review summary；
4. frontend detail 页面展示 issue cards；
5. 展示 severity / category / file path / line range / recommendation；
6. 保持 create/list/detail flow 不破坏；
7. 保持现有 API endpoints 不变；
8. 保持工程化交付质量；
9. 为后续 persistence / ai-service integration 打好 contract 基础。

---

## 4. Non-goals

本任务不是：

1. 数据库持久化；
2. 真实代码审查；
3. GitHub PR integration；
4. Semgrep integration；
5. LLM integration；
6. ai-service integration；
7. production-grade frontend architecture；
8. design system implementation；
9. diff viewer；
10. syntax highlighting；
11. CI/CD；
12. deployment。

---

## 5. Mandatory Scope Boundaries

### 5.1 Strictly Forbidden

本任务严禁：

1. 引入数据库；
2. 引入 MySQL / PostgreSQL / Redis；
3. 引入 MyBatis-Plus；
4. 引入 JPA / Hibernate；
5. 创建 Entity / Repository / Mapper；
6. 创建 database schema；
7. 创建 migration；
8. 调用 `ai-service`；
9. 创建 ai-service client；
10. 调用 GitHub API；
11. 创建 GitHub API client；
12. clone repository；
13. parse real repository code；
14. 执行 Semgrep；
15. 调用 LLM；
16. 调用 OpenAI / Anthropic / Gemini / local model；
17. 引入 Spring Security；
18. 引入 Swagger / OpenAPI；
19. 引入 Chart.js / ECharts / D3；
20. 引入 Ant Design / Material UI；
21. 引入 Redux / MobX / React Query / XState；
22. 迁移到 Next.js；
23. 实现 SSR；
24. 进入 Round 06。

### 5.2 Allowed

本任务允许：

1. 新增 backend typed issue DTO/model；
2. 新增 backend issue severity enum；
3. 新增 backend issue category enum；
4. 将 `ReviewTaskResponse.issues` 改为 typed issue list；
5. backend 创建 ReviewTask 时生成 deterministic mock issues；
6. 更新 backend tests；
7. 更新 frontend TypeScript types；
8. 更新 `ReviewTaskDetail` visualization；
9. 新增 issue cards；
10. 新增 summary panel；
11. 新增 risk badge；
12. 新增 severity/category badges；
13. 更新 frontend tests；
14. 更新 README；
15. minimal CSS polish；
16. 保持现有 API endpoints；
17. 保持 in-memory storage；
18. 保持现有 CORS 配置。

---

## 6. Required Backend Changes

### 6.1 Introduce Typed Review Issue Model

在 `backend-java` 中新增明确的 review issue response model。

建议新增：

```java
ReviewIssueResponse
```

字段至少包含：

```java
private String id;
private IssueSeverity severity;
private IssueCategory category;
private String filePath;
private Integer startLine;
private Integer endLine;
private String title;
private String description;
private String recommendation;
```

也可以使用 `String severity` / `String category`，但优先建议使用 enum，以便 contract 更清晰。

---

### 6.2 Introduce IssueSeverity Enum

建议新增：

```java
public enum IssueSeverity {
    LOW,
    MEDIUM,
    HIGH
}
```

不要在本轮扩展到 CRITICAL，除非已有代码明确需要。

---

### 6.3 Introduce IssueCategory Enum

建议新增：

```java
public enum IssueCategory {
    BUG,
    SECURITY,
    PERFORMANCE,
    MAINTAINABILITY,
    STYLE,
    TEST
}
```

不要引入过度复杂的 taxonomy。

---

### 6.4 Update ReviewTaskResponse

将当前弱类型 issues 改为：

```java
private List<ReviewIssueResponse> issues;
```

要求：

1. API endpoint 不变；
2. `ApiResponse<T>` wrapper 不变；
3. response JSON shape 保持稳定；
4. create/list/detail 均能返回 typed issues；
5. 不破坏现有 task fields。

---

### 6.5 Deterministic Mock Issue Generation

在创建 ReviewTask 时生成 deterministic mock issues。

要求：

1. 不调用真实 AI；
2. 不调用 GitHub；
3. 不执行 Semgrep；
4. 不读取真实 repository；
5. 不 parse code；
6. 每次创建任务返回稳定结构；
7. mock issues 数量建议 3 到 5 个；
8. issue 内容必须清楚表明是 demo/mock result；
9. tests 可稳定断言。

建议 mock issues 示例：

```json
[
  {
    "id": "ISSUE-1",
    "severity": "HIGH",
    "category": "SECURITY",
    "filePath": "src/main/java/com/example/AuthController.java",
    "startLine": 42,
    "endLine": 48,
    "title": "Potential missing authorization check",
    "description": "This demo issue indicates that a sensitive endpoint should explicitly check authorization before processing the request.",
    "recommendation": "Add an authorization guard before the business logic and cover the behavior with a controller test."
  },
  {
    "id": "ISSUE-2",
    "severity": "MEDIUM",
    "category": "MAINTAINABILITY",
    "filePath": "src/main/java/com/example/ReviewTaskService.java",
    "startLine": 76,
    "endLine": 93,
    "title": "Service method is doing too much work",
    "description": "This demo issue suggests the service method combines validation, state transition, and response mapping in one place.",
    "recommendation": "Extract validation and response mapping into smaller private methods to improve readability and testability."
  },
  {
    "id": "ISSUE-3",
    "severity": "LOW",
    "category": "TEST",
    "filePath": "src/test/java/com/example/ReviewTaskServiceTest.java",
    "startLine": 21,
    "endLine": 21,
    "title": "Missing negative-path coverage",
    "description": "This demo issue highlights that validation and not-found scenarios should be covered explicitly.",
    "recommendation": "Add tests for invalid request payloads and missing ReviewTask IDs."
  }
]
```

可以根据当前项目 package/path 命名调整 filePath，但不要声称真实分析了这些文件。

---

## 7. Required Frontend Changes

### 7.1 Update TypeScript Types

将 frontend 当前 `issues: unknown[]` 或弱类型改为明确类型。

建议：

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

并确保：

```typescript
ReviewTask.issues: ReviewIssue[];
```

---

### 7.2 Update ReviewTaskDetail Visualization

更新 ReviewTask detail 页面，使其展示：

1. ReviewTask basic metadata；
2. mock/demo result label；
3. issue summary panel；
4. total issue count；
5. high / medium / low count；
6. risk level；
7. issue cards；
8. severity badge；
9. category badge；
10. file path；
11. line number or line range；
12. title；
13. description；
14. recommendation block；
15. empty issues fallback。

---

### 7.3 Risk Level Calculation

frontend 可以根据 issues severity 简单计算 risk level。

建议规则：

```text
If high count > 0:
  risk level = HIGH
Else if medium count > 0:
  risk level = MEDIUM
Else if low count > 0:
  risk level = LOW
Else:
  risk level = NONE
```

展示文本可以是：

```text
High Risk
Medium Risk
Low Risk
No Issues
```

不要引入复杂 scoring system。

---

### 7.4 Issue Summary Panel

建议展示：

```text
Review Result Summary
Mock result / Demo review

Total Issues: 3
High: 1
Medium: 1
Low: 1
Risk Level: High Risk
```

可以用 cards、badges、simple flex layout，不需要 chart library。

---

### 7.5 Issue Card Layout

每个 issue card 建议结构：

```text
[HIGH] [SECURITY]

Potential missing authorization check

src/main/java/com/example/AuthController.java:42-48

Description
This demo issue indicates ...

Recommendation
Add an authorization guard ...
```

要求：

1. 信息层级清楚；
2. severity/category 易读；
3. file path + line range 清晰；
4. recommendation 明显；
5. 不要做真实 diff viewer；
6. 不要做 syntax highlighting。

---

### 7.6 Empty Issues Fallback

即使当前 mock creation 会返回 issues，也必须保留 empty issues fallback。

例如：

```text
No review issues are available for this task yet.
```

该 fallback 用于未来真实 review 尚未完成或失败场景。

---

## 8. Tests Requirements

### 8.1 Backend Tests

更新或新增 backend tests，至少覆盖：

1. create ReviewTask returns typed issues；
2. issue contains severity；
3. issue contains category；
4. issue contains filePath；
5. issue contains startLine；
6. issue contains title；
7. issue contains description；
8. issue contains recommendation；
9. list ReviewTask 不破坏；
10. detail ReviewTask 不破坏；
11. existing validation / not-found tests 不破坏。

运行：

```bash
cd backend-java
./mvnw test
```

如果当前项目使用系统 Maven：

```bash
mvn test
```

---

### 8.2 Frontend Tests

更新或新增 frontend tests，至少覆盖：

1. `ReviewTaskDetail` renders issue summary；
2. `ReviewTaskDetail` renders issue cards；
3. severity badge visible；
4. category badge visible；
5. file path visible；
6. line range visible；
7. recommendation visible；
8. empty issues fallback still works；
9. existing create/list/detail tests 不破坏。

运行：

```bash
cd frontend
npm install
npm run typecheck
npm run build
npm test
```

如果当前项目 test script 是：

```bash
npm run test
```

则按实际 `package.json` 执行。

---

## 9. README Update Requirements

更新 README，说明 Round 05 的 mock review result 行为。

必须明确：

1. current review issues are deterministic mock data；
2. no real repository code is analyzed；
3. no GitHub API is called；
4. no Semgrep is executed；
5. no LLM / AI service is called；
6. data remains in-memory；
7. backend restart may lose created tasks；
8. frontend displays demo review result for product validation。

同时建议澄清 Round 04 遗留的 API base URL 注意事项：

```text
By default, frontend calls backend directly through VITE_API_BASE_URL=http://localhost:8080.
Do not set VITE_API_BASE_URL to /api, otherwise requests may become /api/api/...
```

如当前 README 已有相关内容，则只需修正，不要重复堆叠。

---

## 10. Runtime Verification Requirements

完成实现后，必须手动验证：

### 10.1 Backend Runtime

启动 backend：

```bash
cd backend-java
./mvnw spring-boot:run
```

或：

```bash
mvn spring-boot:run
```

验证：

```bash
curl http://localhost:8080/api/health
```

创建 task：

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repositoryUrl": "https://github.com/example/demo",
    "branch": "main"
  }'
```

确认 response 中：

1. `success=true`；
2. `data.issues` 是数组；
3. issue 包含 severity/category/filePath/startLine/endLine/title/description/recommendation。

---

### 10.2 Frontend Runtime

启动 frontend：

```bash
cd frontend
npm run dev
```

浏览器验证：

1. 打开 frontend dev server；
2. backend health 显示正常；
3. 创建 ReviewTask；
4. list 页面出现新 task；
5. 点击 detail；
6. detail 页面显示 summary panel；
7. detail 页面显示 risk level；
8. detail 页面显示 issue cards；
9. issue card 展示 severity；
10. issue card 展示 category；
11. issue card 展示 file path；
12. issue card 展示 line range；
13. issue card 展示 recommendation；
14. refresh list 不破坏；
15. backend unavailable error state 仍可显示。

---

## 11. Implementation Priority

请按以下优先级推进：

```text
1. Backend typed ReviewIssue contract
2. Backend deterministic mock issue generation
3. Backend tests
4. Frontend ReviewIssue TypeScript model
5. ReviewTaskDetail issue visualization
6. Summary panel and risk level
7. CSS polish
8. Frontend tests
9. README update
10. Runtime verification
11. Handoff
```

不要把时间投入到：

```text
database
real AI review
GitHub API
Semgrep
diff viewer
syntax highlighting
advanced filtering
sorting
pagination
auth
deployment
CI/CD
```

---

## 12. Quality Bar

本任务完成后，应达到：

1. backend/frontend contract 对齐；
2. ReviewTask detail 页面具备可演示的 review result 视觉效果；
3. mock issue 数据稳定；
4. UI 明确标识 demo/mock；
5. create/list/detail flow 不破坏；
6. loading/error/empty states 不破坏；
7. backend tests 通过；
8. frontend typecheck/build/tests 通过；
9. README 描述准确；
10. 没有越界引入数据库、AI、GitHub、Semgrep 或 LLM。

---

## 13. Handoff Requirements

完成后生成：

```text
handoff/round-05/01-cursor-review-result-visualization-mock-v1-handoff.md
```

handoff 必须包含以下内容。

---

### 13.1 Summary

说明本任务实现了什么。

至少包括：

1. backend typed issue contract；
2. deterministic mock issues；
3. frontend issue visualization；
4. tests update；
5. README update；
6. scope boundary compliance。

---

### 13.2 Files Changed

列出所有修改文件，例如：

```text
backend-java/src/main/java/...
frontend/src/...
frontend/README.md
```

不要只写目录。

---

### 13.3 Backend Contract

说明最终 backend issue model 字段。

示例：

```text
ReviewIssueResponse:
- id
- severity
- category
- filePath
- startLine
- endLine
- title
- description
- recommendation
```

说明 severity/category 枚举值。

---

### 13.4 Mock Data Behavior

说明：

1. mock issue 是 deterministic；
2. 每个新 ReviewTask 会得到多少 issue；
3. issue 是否与 request 参数相关；
4. 明确未分析真实代码；
5. 明确未调用 GitHub / Semgrep / LLM / ai-service。

---

### 13.5 Frontend Visualization

说明 detail 页面新增了哪些 UI：

1. summary panel；
2. risk level；
3. severity counts；
4. issue cards；
5. file path + line range；
6. recommendation block；
7. empty issues fallback。

---

### 13.6 Commands Run

记录实际执行命令和结果。

格式：

```text
cd backend-java
mvn test
Result: PASS

cd frontend
npm install
Result: PASS

npm run typecheck
Result: PASS

npm run build
Result: PASS

npm test
Result: PASS
```

如果某命令名称不同，按实际项目记录。

---

### 13.7 Runtime Verification

记录：

1. backend 启动结果；
2. frontend 启动结果；
3. health check；
4. create task；
5. list task；
6. detail page；
7. issue cards；
8. mock label；
9. error/empty state spot check。

---

### 13.8 Scope Audit

必须逐项确认：

```text
Database introduced: NO
Persistence introduced: NO
MyBatis/JPA introduced: NO
ai-service called: NO
GitHub API called: NO
Semgrep executed: NO
LLM called: NO
Repository cloned: NO
Real code parsed: NO
Chart/component library introduced: NO
Complex state management introduced: NO
Existing endpoints changed: NO
ApiResponse wrapper changed: NO
Round 06 started: NO
```

---

### 13.9 Known Issues

列出：

1. blocking issues；
2. non-blocking notes；
3. recommended follow-up。

如果没有 blocking issue，明确写：

```text
Blocking issues: None known.
```

---

## 14. Acceptance Criteria

Cursor 完成后应满足：

### Backend

- [ ] `ReviewTaskResponse.issues` 使用 typed issue model；
- [ ] issue 至少包含 severity/category/filePath/startLine/endLine/title/description/recommendation；
- [ ] backend create task 返回 deterministic mock issues；
- [ ] list/detail API 不破坏；
- [ ] endpoints 不变；
- [ ] `ApiResponse<T>` 不变；
- [ ] in-memory storage 不变；
- [ ] backend tests 通过。

### Frontend

- [ ] `ReviewTask.issues` 使用 `ReviewIssue[]`；
- [ ] detail 页面展示 summary panel；
- [ ] detail 页面展示 total/high/medium/low counts；
- [ ] detail 页面展示 risk level；
- [ ] detail 页面展示 mock/demo label；
- [ ] detail 页面展示 issue cards；
- [ ] issue card 展示 severity；
- [ ] issue card 展示 category；
- [ ] issue card 展示 file path；
- [ ] issue card 展示 line number or range；
- [ ] issue card 展示 title；
- [ ] issue card 展示 description；
- [ ] issue card 展示 recommendation；
- [ ] empty issues fallback 保留；
- [ ] create/list/detail flow 不破坏；
- [ ] loading/error/empty states 不破坏；
- [ ] frontend typecheck 通过；
- [ ] frontend build 通过；
- [ ] frontend tests 通过。

### Scope

- [ ] 不引入数据库；
- [ ] 不引入 persistence；
- [ ] 不调用 `ai-service`；
- [ ] 不调用 GitHub；
- [ ] 不执行 Semgrep；
- [ ] 不调用 LLM；
- [ ] 不 clone repository；
- [ ] 不 parse real code；
- [ ] 不引入 chart/component library；
- [ ] 不引入复杂 frontend state management；
- [ ] 不进入 Round 06。

---

## 15. Final Instruction

请严格基于 Round 05 start document 执行本任务。

本轮核心原则：

```text
优先呈现可视化 review result，快速验证实际使用效果；用 deterministic mock issues 收敛 ReviewIssue contract，但不做数据库、不做 ai-service、不做 GitHub、不做 Semgrep、不做 LLM。
```

完成后不要继续推进 Codex 或 Qoder 任务。

只输出 Cursor implementation handoff：

```text
handoff/round-05/01-cursor-review-result-visualization-mock-v1-handoff.md
```