# tasks/round-05/00-round-05-start.md

# Round 05 Start Document: Review Result Visualization Mock v1

## 1. Document Metadata

- Project: CodeReviewX
- Document Type: Round Start Document
- Current Round: Round 05
- Previous Round: Round 04
- Created By: ChatGPT Architect
- Target Readers:
  - Cursor
  - Codex
  - Qoder
- Recommended Next Task:
  - `tasks/round-05/01-cursor-review-result-visualization-mock-v1.md`

---

## 2. Round 04 Summary

### 2.1 Round 04 Name

```text
Round 04: frontend ReviewTask Mock UI v1
```

### 2.2 Round 04 Goal

Round 04 的目标是在 frontend 中实现 ReviewTask mock UI，并与 Round 03 已完成的 `backend-java` mock API 完成基础联调。

核心目标包括：

1. 形成可演示的前后端闭环；
2. 验证 ReviewTask API contract 是否适合 UI；
3. 让用户能够创建 ReviewTask；
4. 让用户能够查看 ReviewTask list；
5. 让用户能够查看 ReviewTask detail；
6. 实现基础 loading / error / empty state；
7. 不引入数据库；
8. 不调用 `ai-service`；
9. 不调用 GitHub API；
10. 不执行 Semgrep；
11. 不调用 LLM；
12. 不做 production-grade frontend 架构。

---

## 3. Round 04 Execution Summary

Round 04 已按三 Agent 流程完成：

```text
Cursor implementation
    ↓
Codex validation
    ↓
Qoder independent review
    ↓
ChatGPT Architect final acceptance
```

### 3.1 Cursor Implementation Summary

Cursor 完成了 `frontend` ReviewTask Mock UI v1。

主要实现内容：

```text
frontend/package.json
frontend/vite.config.ts
frontend/tsconfig.json
frontend/index.html
frontend/src/main.tsx
frontend/src/App.tsx
frontend/src/api/reviewTaskApi.ts
frontend/src/types/apiResponse.ts
frontend/src/types/reviewTask.ts
frontend/src/components/ReviewTaskCreateForm.tsx
frontend/src/components/ReviewTaskList.tsx
frontend/src/components/ReviewTaskDetail.tsx
frontend/src/components/LoadingState.tsx
frontend/src/components/ErrorMessage.tsx
frontend/src/styles/app.css
frontend/src/test/
frontend/README.md
backend-java/src/main/java/com/codereviewx/backend/config/WebConfig.java
```

实现特征：

1. 使用 React + TypeScript + Vite；
2. 实现 ReviewTask 创建表单；
3. 实现 ReviewTask list；
4. 实现 ReviewTask detail；
5. 实现 frontend API client；
6. 支持 `VITE_API_BASE_URL`；
7. 默认 backend API base URL 为 `http://localhost:8080`；
8. 实现 backend health 状态显示；
9. 实现 loading state；
10. 实现 error state；
11. 实现 empty state；
12. 实现基础测试；
13. 添加最小 backend CORS 配置；
14. 未引入复杂状态管理；
15. 未引入 Next.js / SSR；
16. 未引入数据库；
17. 未调用 `ai-service`；
18. 未调用 GitHub API；
19. 未执行 Semgrep；
20. 未调用 LLM。

Cursor Handoff：

```text
handoff/round-04/01-cursor-frontend-reviewtask-mock-ui-handoff.md
```

---

### 3.2 Codex Validation Summary

Codex 完成独立验证。

验证内容：

1. frontend install；
2. frontend typecheck；
3. frontend build；
4. frontend tests；
5. backend tests；
6. backend runtime；
7. frontend runtime；
8. browser create/list/detail flow；
9. backend health display；
10. CORS validation；
11. `success=false` / `data=null` handling；
12. backend unavailable handling；
13. scope audit。

Codex 验证结果：

```text
PASS
```

关键验证结果：

```text
Frontend typecheck: PASS
Frontend build: PASS
Frontend tests: PASS
Backend mvn test: PASS
Backend runtime: PASS
Frontend runtime: PASS
Create/list/detail browser flow: PASS
CORS direct backend mode: PASS
Scope audit: PASS
```

Codex Handoff：

```text
handoff/round-04/02-codex-frontend-reviewtask-mock-ui-validation-handoff.md
```

---

### 3.3 Qoder Independent Review Summary

Qoder 完成独立架构审查、代码审查、测试审查、CORS 审查和 scope audit。

Qoder 结论：

```text
PASS_WITH_NON_BLOCKING_NOTES
```

Qoder 确认：

1. frontend architecture 适合 MVP；
2. component separation 合理；
3. API client 与 UI 分离；
4. TypeScript types 与 backend DTO 基本一致；
5. `ApiResponse<T>` 处理合理；
6. loading / error / empty states 覆盖完整；
7. UI 真实消费 backend API；
8. 没有 hardcoded task id；
9. 没有 hardcoded timestamp；
10. CORS 配置最小；
11. backend business logic 未被改变；
12. frontend tests 对当前阶段足够；
13. 未引入数据库；
14. 未调用 `ai-service`；
15. 未调用 GitHub API；
16. 未执行 Semgrep；
17. 未调用 LLM；
18. 未引入复杂状态管理；
19. 未进入 Round 05。

Qoder Handoff：

```text
handoff/round-04/03-qoder-frontend-reviewtask-mock-ui-independent-review.md
```

---

## 4. Round 04 Final Architect Decision

### 4.1 Acceptance Result

```text
Round 04: Accepted with non-blocking notes
```

### 4.2 Acceptance Basis

Round 04 满足以下条件：

1. `frontend/` 存在可运行前端项目；
2. frontend 使用 React + TypeScript + Vite；
3. frontend 可创建 ReviewTask；
4. frontend 可展示 ReviewTask list；
5. frontend 可展示 ReviewTask detail；
6. frontend 调用 `GET /api/health`；
7. frontend 调用 `POST /api/review-tasks`；
8. frontend 调用 `GET /api/review-tasks`；
9. frontend 调用 `GET /api/review-tasks/{id}`；
10. frontend 正确处理 `ApiResponse<T>`；
11. frontend 正确处理 `success=false`；
12. frontend 正确处理 `data=null`；
13. frontend 有 loading state；
14. frontend 有 error state；
15. frontend 有 empty state；
16. frontend 支持 configurable API base URL；
17. frontend build 通过；
18. frontend typecheck 通过；
19. frontend tests 通过；
20. backend `mvn test` 仍通过；
21. backend runtime 验证通过；
22. frontend runtime 验证通过；
23. browser create/list/detail flow 验证通过；
24. backend CORS 配置最小；
25. 未引入数据库；
26. 未调用 `ai-service`；
27. 未调用 GitHub API；
28. 未执行 Semgrep；
29. 未调用 LLM；
30. 未发现 blocking issue。

---

## 5. Round 04 Non-blocking Backlog

以下问题不影响 Round 04 验收，但应进入后续 backlog。

### 5.1 Vite proxy configured but not used by default

当前 API client 默认使用：

```text
http://localhost:8080
```

因此浏览器默认直接跨域调用 backend，而不是通过 Vite proxy。

当前不阻塞，因为 backend CORS 已验证可用。

后续建议：

1. README 明确默认 direct backend mode；
2. 或移除 dormant proxy；
3. 或正式支持 proxy-relative mode。

---

### 5.2 README proxy wording should be clarified

当前 README 提到 Vite dev server proxy，但默认 runtime path 实际不使用 proxy。

建议后续改为：

```text
By default, frontend calls backend directly through VITE_API_BASE_URL=http://localhost:8080.
The Vite proxy is optional and not used in the default configuration.
```

---

### 5.3 `VITE_API_BASE_URL=/api` would produce wrong paths

当前 API client 会 append `/api/...`。

如果配置：

```text
VITE_API_BASE_URL=/api
```

则会产生：

```text
/api/api/health
/api/api/review-tasks
```

建议 README 明确：

```text
VITE_API_BASE_URL should be a backend origin, for example http://localhost:8080.
Do not set it to /api.
```

---

### 5.4 API client non-JSON error robustness

当前 `fetchJson` 直接解析 JSON。

这适用于当前 Spring Boot `ApiResponse` error body，但如果未来遇到 non-JSON error body，例如 reverse proxy 502 HTML page，错误信息会退化为 generic network error。

建议后续增强：

1. 先读取 text；
2. 尝试 JSON parse；
3. 如果不是 JSON，则返回更明确 HTTP error；
4. 保持现有 `ApiResponse<T>` contract。

---

### 5.5 Badge styling naming inconsistency

当前 list 与 detail 的 badge class naming 有轻微不一致。

不影响功能。

建议后续 UI polish 时统一。

---

### 5.6 Test coverage gaps

当前测试对 Round 04 足够，但后续可补：

1. create form submit success；
2. create form validation errors；
3. create form backend failure；
4. list error state；
5. detail loading state；
6. API client non-JSON error body。

---

## 6. Round 05 Strategic Decision

### 6.1 Why Round 05 Should Not Start Persistence Yet

此前自然演进方向可以是 backend persistence v1。

但在当前产品要求下：

```text
按照工程化交付的标准，且尽可能加快速度来呈现可视化结果以便于调整实际使用效果
```

Round 05 不建议立刻进入数据库持久化。

原因：

1. 当前产品还没有足够真实的 review result 可视化形态；
2. 用户真正需要先看到“代码审查结果长什么样”；
3. 过早做 persistence 会增加后端工程复杂度，但不直接提升可视化反馈速度；
4. 目前 `issues` 仍是空数组，frontend detail 页缺少真实评审体验；
5. 先做 richer mock result 可以更快验证：
   - issue card 设计；
   - severity 展示；
   - file path / line range 展示；
   - recommendation 展示；
   - risk summary 展示；
   - frontend 信息层级；
   - 用户是否看得懂结果；
6. 等 ReviewTask result contract 稳定后，再做 persistence 成本更低。

因此 Round 05 应优先：

```text
Review result visualization mock v1
```

而不是：

```text
backend persistence v1
```

---

## 7. Round 05 Name

```text
Round 05: Review Result Visualization Mock v1
```

---

## 8. Round 05 Strategic Goal

Round 05 的目标是在不接入真实 AI、不调用 GitHub、不执行 Semgrep、不引入数据库的前提下，让 CodeReviewX 呈现更接近实际使用场景的 review result 可视化效果。

核心目标：

1. 快速提升可演示效果；
2. 让用户看到“审查结果页面”的真实雏形；
3. 通过 mock issue data 验证 review result 信息架构；
4. 收敛 ReviewIssue contract；
5. 修复 Round 03 `issues: List<Object>` / frontend `unknown[]` 的类型债务；
6. 为后续 persistence 和 ai-service integration 提供稳定数据模型；
7. 继续保持工程化交付：
   - clear task；
   - build/test validation；
   - runtime verification；
   - handoff；
   - independent review；
8. 继续严格禁止数据库、GitHub、Semgrep、LLM、真实 AI review。

---

## 9. Round 05 Product Scope

### 9.1 User-visible Outcome

Round 05 完成后，用户应能看到：

1. 创建 ReviewTask 后，不再只是空 issues；
2. detail 页面展示 mock review summary；
3. detail 页面展示多个 mock issues；
4. 每个 issue 至少包含：
   - severity；
   - category；
   - file path；
   - line number or line range；
   - title；
   - description；
   - recommendation；
5. 页面顶部展示结果概览：
   - total issue count；
   - high / medium / low count；
   - risk level；
   - status；
6. 用户可以更直观判断：
   - 哪些文件有问题；
   - 问题严重程度；
   - 应该如何修复；
   - 当前审查结果是否有用；
7. 仍然可以 create/list/detail；
8. 仍然可以刷新 list；
9. 后端重启后数据仍允许丢失。

---

### 9.2 Recommended Visual Elements

Round 05 建议实现以下轻量可视化：

```text
Review summary panel
Risk badge
Issue count cards
Severity distribution
Issue cards
File path + line display
Recommendation block
Empty issue fallback
```

不要求引入图表库。

如果用纯 CSS 实现即可，不要引入复杂 chart library。

允许使用简单结构：

```text
High: 1
Medium: 2
Low: 3
```

---

## 10. Round 05 API / Data Contract Direction

### 10.1 Backend ReviewIssueResponse

Round 05 应在 backend-java 中引入明确的 review issue response model。

推荐：

```java
public class ReviewIssueResponse {
    private String id;
    private String severity;
    private String category;
    private String filePath;
    private Integer startLine;
    private Integer endLine;
    private String title;
    private String description;
    private String recommendation;
}
```

或使用 enum：

```java
IssueSeverity:
LOW
MEDIUM
HIGH

IssueCategory:
BUG
SECURITY
PERFORMANCE
MAINTAINABILITY
STYLE
TEST
```

建议优先使用 enum，但不要过度设计。

### 10.2 ReviewTask issues 类型收敛

将当前 backend response 中的 issues 从弱类型收敛为：

```java
List<ReviewIssueResponse>
```

frontend 对应：

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

然后：

```typescript
ReviewTask.issues: ReviewIssue[];
```

### 10.3 Mock Data Requirement

Backend mock service should return deterministic mock issue data.

Example:

```json
{
  "id": "ISSUE-1",
  "severity": "HIGH",
  "category": "SECURITY",
  "filePath": "src/main/java/com/example/AuthController.java",
  "startLine": 42,
  "endLine": 48,
  "title": "Potential missing authorization check",
  "description": "This endpoint appears to process sensitive data but does not show an explicit authorization check in the mocked review result.",
  "recommendation": "Add an authorization guard before processing the request and cover the behavior with a controller test."
}
```

Important:

1. mock issues must be clearly marked as mock or generated demo result in summary；
2. do not imply real code was analyzed；
3. do not call GitHub；
4. do not execute Semgrep；
5. do not call LLM；
6. do not parse actual repository code；
7. deterministic mock output is preferred for stable tests。

---

## 11. Round 05 Recommended Technical Direction

### 11.1 Backend Changes

Allowed backend changes:

1. Add `ReviewIssueResponse` DTO or domain model；
2. Add `IssueSeverity` enum；
3. Add `IssueCategory` enum；
4. Update `ReviewTaskResponse.issues` to typed list；
5. Update mock ReviewTask creation logic to populate deterministic mock issues；
6. Update backend tests；
7. Keep API wrapper `ApiResponse<T>` unchanged；
8. Keep endpoints unchanged；
9. Keep in-memory storage；
10. Keep status flow mock；
11. Keep no database。

Backend endpoints remain:

```http
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

Do not add new backend endpoints unless strongly justified.

---

### 11.2 Frontend Changes

Allowed frontend changes:

1. Update `ReviewTask` type；
2. Add `ReviewIssue` type；
3. Update `ReviewTaskDetail` to render issue cards；
4. Add issue severity badges；
5. Add issue category badges；
6. Add issue summary counts；
7. Add file path + line range display；
8. Add recommendation block；
9. Keep create/list/detail flow；
10. Keep current API client style；
11. Update tests；
12. Update README with richer mock result behavior。

Frontend should remain simple.

Do not introduce:

```text
Redux
MobX
React Query
XState
Chart.js
ECharts
Ant Design
Material UI
Tailwind migration
Next.js
SSR
```

---

### 11.3 Visual Delivery Priority

Round 05 should optimize for fast visible value.

Implementation priority:

```text
1. Typed mock issues in backend
2. Frontend issue cards
3. Summary count panel
4. Styling polish
5. Tests
6. README
```

Do not spend time on:

```text
database schema
advanced filtering
sorting
pagination
real diff viewer
syntax highlighting
auth
multi-user
CI/CD
deployment
```

---

## 12. Round 05 Allowed Scope

Round 05 allows:

1. backend typed issue DTO/model；
2. backend mock issue generation；
3. backend tests for typed issues；
4. frontend typed ReviewIssue model；
5. frontend ReviewTask detail visualization；
6. frontend issue cards；
7. frontend severity/category badges；
8. frontend summary counts；
9. frontend tests；
10. README updates；
11. minimal CSS polish；
12. maintaining existing CORS config；
13. maintaining existing API endpoints；
14. keeping in-memory storage。

---

## 13. Round 05 Forbidden Scope

Round 05 forbids:

1. 引入数据库；
2. 修改 backend-java 为数据库持久化；
3. 引入 MyBatis-Plus；
4. 引入 MySQL Driver；
5. 引入 JPA / Hibernate；
6. 创建 database schema；
7. 创建 migration；
8. 创建 Entity / Mapper / Repository；
9. 调用 `ai-service`；
10. 创建 ai-service client；
11. 调用 GitHub API；
12. 创建 GitHub API client；
13. 执行 Semgrep；
14. 调用 LLM；
15. 调用 OpenAI / Anthropic / Gemini / local model；
16. 实现真实代码审查；
17. clone repository；
18. parse real code；
19. 引入 Redis；
20. 引入 MQ；
21. 引入 Scheduler；
22. 引入复杂权限系统；
23. 引入 Spring Security；
24. 引入 Swagger / OpenAPI；
25. 修改 docker-compose 为真实服务；
26. 修改 GitHub Actions 为真实 CI；
27. 创建 ai-service 业务代码；
28. 引入复杂 frontend state management；
29. 引入 chart library；
30. 引入 component library；
31. 实现 production-grade design system；
32. 实现 diff viewer；
33. 实现 syntax highlighting；
34. 实现 comment posting；
35. 实现 GitHub PR integration；
36. 进入 Round 06。

---

## 14. Round 05 Agent Plan

Round 05 继续沿用三 Agent 流程。

```text
Cursor implements
Codex validates
Qoder reviews
ChatGPT accepts
```

---

### 14.1 Cursor Task

推荐任务文件：

```text
tasks/round-05/01-cursor-review-result-visualization-mock-v1.md
```

Cursor 负责：

1. 阅读 Round 05 start document；
2. 检查当前 backend/frontend 实现；
3. 在 backend-java 中引入 typed ReviewIssue model/DTO；
4. 将 ReviewTask issues 从弱类型收敛为 typed issues；
5. 更新 mock ReviewTask 创建逻辑，返回 deterministic mock issues；
6. 更新 backend tests；
7. 更新 frontend TypeScript types；
8. 更新 ReviewTaskDetail 可视化；
9. 增加 issue summary panel；
10. 增加 issue cards；
11. 增加 severity/category badges；
12. 保持 create/list/detail flow；
13. 更新 frontend tests；
14. 更新 README；
15. 执行 build/typecheck/tests；
16. 输出 Cursor handoff。

Cursor Handoff：

```text
handoff/round-05/01-cursor-review-result-visualization-mock-v1-handoff.md
```

---

### 14.2 Codex Task

推荐任务文件：

```text
tasks/round-05/02-codex-review-result-visualization-validation.md
```

Codex 负责：

1. 独立验证 backend tests；
2. 独立验证 frontend install/typecheck/build/test；
3. 启动 backend；
4. 启动 frontend；
5. 验证 create task 返回 typed issues；
6. 验证 list task 包含 issues summary 或至少不破坏 list；
7. 验证 detail 页面展示 issue cards；
8. 验证 severity/category/line/recommendation 可见；
9. 验证 empty issues fallback 仍可处理；
10. 验证 not found / validation error / backend unavailable；
11. scope audit；
12. 只做最小修复；
13. 输出 Codex handoff。

Codex Handoff：

```text
handoff/round-05/02-codex-review-result-visualization-validation-handoff.md
```

---

### 14.3 Qoder Task

推荐任务文件：

```text
tasks/round-05/03-qoder-review-result-visualization-independent-review.md
```

Qoder 负责：

1. 审查 typed issue contract；
2. 审查 backend mock issue generation；
3. 审查 frontend result visualization；
4. 审查 information architecture；
5. 审查 UI 是否有助于快速调整实际使用效果；
6. 审查测试质量；
7. 审查 README；
8. 审查 scope boundary；
9. 输出 independent review。

Qoder Handoff：

```text
handoff/round-05/03-qoder-review-result-visualization-independent-review.md
```

---

## 15. Round 05 Acceptance Criteria Draft

### 15.1 Backend Contract

- [ ] `ReviewTaskResponse.issues` 使用明确 typed issue model；
- [ ] backend 返回 deterministic mock issues；
- [ ] 每个 issue 至少包含 severity/category/filePath/line/title/description/recommendation；
- [ ] API endpoints 不变；
- [ ] `ApiResponse<T>` 不变；
- [ ] in-memory storage 保持；
- [ ] backend tests 通过；
- [ ] 不引入数据库；
- [ ] 不调用 `ai-service`；
- [ ] 不调用 GitHub；
- [ ] 不执行 Semgrep；
- [ ] 不调用 LLM。

### 15.2 Frontend Visualization

- [ ] `ReviewTask` type 使用 `ReviewIssue[]`；
- [ ] detail 页面展示 issue cards；
- [ ] issue card 展示 severity；
- [ ] issue card 展示 category；
- [ ] issue card 展示 file path；
- [ ] issue card 展示 line number/range；
- [ ] issue card 展示 title；
- [ ] issue card 展示 description；
- [ ] issue card 展示 recommendation；
- [ ] detail 页面展示 issue count summary；
- [ ] detail 页面展示 risk level；
- [ ] empty issues fallback 仍可用；
- [ ] create/list/detail flow 不破坏；
- [ ] loading/error/empty states 不破坏；
- [ ] frontend typecheck 通过；
- [ ] frontend build 通过；
- [ ] frontend tests 通过。

### 15.3 Visual Feedback Speed

- [ ] 用户创建任务后可立即看到多个 mock issues；
- [ ] 页面能够用于讨论 review result 信息架构；
- [ ] 页面能够用于调整 issue card 展示密度；
- [ ] 页面能够用于调整 severity/category/risk 的可读性；
- [ ] 页面不需要等待数据库、AI、GitHub 或 Semgrep 集成。

### 15.4 Engineering Delivery

- [ ] Cursor handoff 完整；
- [ ] Codex validation 完整；
- [ ] Qoder review 完整；
- [ ] 所有命令结果记录清晰；
- [ ] runtime 验证记录清晰；
- [ ] scope audit 清晰；
- [ ] blocking/non-blocking issue 分类清晰。

---

## 16. Important Product and Architecture Decisions

### 16.1 Prioritize Visual Result Before Persistence

Round 05 选择先做可视化 mock result，而不是先做 persistence。

原因：

1. 当前最重要的是验证用户看到 review result 后是否能理解；
2. UI 信息架构需要先被验证；
3. `issues` contract 需要先稳定；
4. persistence 应该服务稳定 contract，而不是反过来锁死不成熟的数据模型；
5. mock issue result 可以最快呈现可视化效果；
6. 后续做 database schema 时可以基于更清晰的 `ReviewIssue` model。

---

### 16.2 Keep Mock Clearly Marked

Round 05 仍然是 mock review。

必须避免误导用户以为已经执行真实审查。

要求：

1. summary 中明确包含 mock/demo；
2. README 中明确说明 issue data is deterministic mock data；
3. UI 可以显示 small label：
   - `Mock result`
   - `Demo review`
4. 不声称已分析真实 GitHub repository；
5. 不声称已执行 AI review。

---

### 16.3 Contract First, Not Integration First

Round 05 的本质是 contract 和 visualization 收敛。

重点：

```text
ReviewTask
ReviewIssue
severity
category
filePath
line range
recommendation
risk summary
```

暂不关注：

```text
database table
LLM prompt
Semgrep result parser
GitHub PR diff
auth
deployment
```

---

### 16.4 Avoid Heavy Frontend Dependencies

可视化不等于引入图表库或组件库。

Round 05 的 UI 复杂度应该控制在：

```text
CSS cards
badges
simple counts
simple layout
```

不需要：

```text
Chart.js
ECharts
D3
Ant Design
Material UI
Tailwind migration
```

---

## 17. Initial Risk Assessment

### 17.1 Main Risks

1. Cursor 可能直接进入 persistence；
2. Cursor 可能调用 GitHub API；
3. Cursor 可能接入 ai-service；
4. Cursor 可能执行 Semgrep；
5. Cursor 可能引入 chart/component library；
6. Cursor 可能设计过重的 issue domain；
7. Cursor 可能破坏现有 create/list/detail flow；
8. Cursor 可能让 mock issue 看起来像真实分析；
9. Cursor 可能没有更新 tests；
10. Cursor 可能没有处理 empty issues fallback；
11. Cursor 可能让 frontend type 与 backend response 不一致；
12. Cursor 可能把 issues 继续保留为 weak type。

### 17.2 Risk Control

Round 05 任务文档必须明确：

1. 本轮只做 mock issue visualization；
2. 不做数据库；
3. 不做 persistence；
4. 不调用 GitHub；
5. 不调用 ai-service；
6. 不执行 Semgrep；
7. 不调用 LLM；
8. mock issue 必须 deterministic；
9. mock issue 必须 clearly marked；
10. backend/frontend types 必须一致；
11. create/list/detail flow 必须保持；
12. tests 必须更新；
13. Codex 必须 runtime 验证；
14. Qoder 必须审查 information architecture 和 scope boundary。

---

## 18. Recommended First Task for Round 05

下一步建议生成 Cursor 任务：

```text
tasks/round-05/01-cursor-review-result-visualization-mock-v1.md
```

该任务应要求 Cursor：

1. 基于 Round 04 已验收 frontend；
2. 基于 Round 03 已验收 backend API；
3. 收敛 `issues` typed contract；
4. 实现 deterministic mock issues；
5. 更新 frontend detail visualization；
6. 实现 issue cards；
7. 实现 severity/category display；
8. 实现 issue count summary；
9. 保持 create/list/detail flow；
10. 更新 backend/frontend tests；
11. 更新 README；
12. 不做数据库；
13. 不调用 GitHub；
14. 不调用 `ai-service`；
15. 不执行 Semgrep；
16. 不调用 LLM；
17. 不进入 Round 06；
18. 完成后生成 handoff。

---

## 19. Final Instruction for Round 05 Planning

Round 04 已正式完成，不再回到 Round 04 返工。

Round 05 应从以下任务开始：

```text
tasks/round-05/01-cursor-review-result-visualization-mock-v1.md
```

Round 05 的核心原则：

```text
优先呈现可视化 review result，快速验证实际使用效果；用 deterministic mock issues 收敛 ReviewIssue contract，但不做数据库、不做 ai-service、不做 GitHub、不做 Semgrep、不做 LLM。
```

继续保持三 Agent 协作流程：

```text
Cursor implements
Codex validates
Qoder reviews
ChatGPT accepts
```