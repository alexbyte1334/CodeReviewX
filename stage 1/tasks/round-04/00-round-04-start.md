# tasks/round-04/00-round-04-start.md

# Round 04 Start Document: frontend ReviewTask Mock UI v1

## 1. Document Metadata

- Project: CodeReviewX
- Document Type: Round Start Document
- Current Round: Round 04
- Previous Round: Round 03
- Created By: ChatGPT Architect
- Target Readers:
  - Cursor
  - Codex
  - Qoder
- Recommended Next Task:
  - `tasks/round-04/01-cursor-frontend-reviewtask-mock-ui.md`

---

## 2. Round 03 Summary

### 2.1 Round 03 Name

```text
Round 03: backend-java ReviewTask API Mock v1
```

### 2.2 Round 03 Goal

Round 03 的目标是在不引入数据库、不调用 `ai-service` 的前提下，实现 `backend-java` 的 ReviewTask 业务 API mock 版本。

核心目标包括：

1. 为 frontend 提供稳定 API contract；
2. 让 backend-java 具备 ReviewTask 生命周期管理雏形；
3. 保持实现轻量、可测试、可展示；
4. 不提前进入 MySQL / MyBatis-Plus / ai-service 集成；
5. 为后续持久化和 ai-service 调用打基础。

---

## 3. Round 03 Execution Summary

Round 03 已按三 Agent 流程完成：

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

Cursor 完成了 `backend-java` ReviewTask API mock v1。

主要实现内容：

```text
backend-java/src/main/java/com/codereviewx/backend/review/model/ReviewTask.java
backend-java/src/main/java/com/codereviewx/backend/review/exception/ReviewTaskNotFoundException.java
backend-java/src/main/java/com/codereviewx/backend/common/GlobalExceptionHandler.java
backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java
backend-java/src/main/java/com/codereviewx/backend/review/controller/ReviewTaskController.java
backend-java/src/test/java/com/codereviewx/backend/review/service/ReviewTaskServiceTest.java
backend-java/src/test/java/com/codereviewx/backend/review/controller/ReviewTaskControllerTest.java
backend-java/README.md
```

实现 API：

```http
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

实现特征：

1. 使用内存级 `ConcurrentHashMap` 存储；
2. 使用 `AtomicLong` 生成 id；
3. 创建任务后同步 mock 状态流：
   - `PENDING`
   - `RUNNING`
   - `SUCCESS`
4. 最终返回 mock `SUCCESS`；
5. `summary` 明确包含 `Mock`；
6. `riskLevel` 固定为 `LOW`；
7. `issues` 返回空数组；
8. 所有响应使用 `ApiResponse<T>`；
9. validation failure 使用统一 `ApiResponse`；
10. not found 使用统一 `ApiResponse`。

Cursor Handoff：

```text
handoff/round-03/01-cursor-backend-reviewtask-api-mock-handoff.md
```

---

### 3.2 Codex Validation Summary

Codex 完成独立验证。

验证内容：

1. 配置 Java 17；
2. 执行 `mvn test`；
3. 启动 Spring Boot 服务；
4. 验证 `GET /api/health`；
5. 验证 `POST /api/review-tasks`；
6. 验证 `GET /api/review-tasks`；
7. 验证 `GET /api/review-tasks/{id}`；
8. 验证 not found；
9. 验证 validation failure；
10. 检查数据库 / 外部集成 / 未授权依赖是否缺失。

Codex 验证结果：

```text
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

运行时验证通过：

```text
GET  /api/health                       PASS
POST /api/review-tasks                 PASS
GET  /api/review-tasks                 PASS
GET  /api/review-tasks/{id}            PASS
GET  /api/review-tasks/{missing_id}    PASS
POST /api/review-tasks invalid body    PASS
```

Codex 做了一个最小测试环境修复：

```text
backend-java/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker
```

内容：

```text
mock-maker-subclass
```

该修复仅影响 test runtime，不影响 production runtime，不新增依赖，不改变业务行为。

Codex Handoff：

```text
handoff/round-03/02-codex-backend-reviewtask-api-validation-handoff.md
```

---

### 3.3 Qoder Independent Review Summary

Qoder 完成独立架构审查、代码审查、测试审查和 scope audit。

Qoder 结论：

```text
Overall result: PASS
No blocking issues found.
Recommend accepting Round 03 with non-blocking notes.
```

Qoder 确认：

1. API contract 符合 Round 03；
2. mock 行为符合 Round 03；
3. `ApiResponse<T>` 统一响应符合要求；
4. validation 和 exception handling 符合要求；
5. in-memory storage 符合要求；
6. 分层清晰；
7. 测试覆盖关键路径；
8. 未引入数据库；
9. 未引入 MyBatis-Plus / MySQL / JPA / Hibernate；
10. 未创建 Entity / Mapper / Repository；
11. 未调用 `ai-service`；
12. 未调用 GitHub API；
13. 未执行 Semgrep；
14. 未调用 LLM；
15. 未引入 Redis / MQ / Scheduler；
16. 未引入 Spring Security / Swagger / OpenAPI / Lombok；
17. 未创建 frontend / ai-service 业务代码；
18. 未添加未授权依赖。

Qoder Handoff：

```text
handoff/round-03/03-qoder-backend-reviewtask-api-independent-review.md
```

---

## 4. Round 03 Final Architect Decision

### 4.1 Acceptance Result

```text
Round 03: Accepted with non-blocking notes
```

### 4.2 Acceptance Basis

Round 03 满足以下条件：

1. `backend-java` ReviewTask mock API 已实现；
2. `GET /api/health` 继续可用；
3. `POST /api/review-tasks` 可用；
4. `GET /api/review-tasks` 可用；
5. `GET /api/review-tasks/{id}` 可用；
6. API 返回统一 `ApiResponse<T>`；
7. 创建任务时使用 `@Valid`；
8. 无效 `repoUrl` 可被校验；
9. 无效 `prNumber` 可被校验；
10. 查询不存在任务时返回 HTTP 404 + `ApiResponse`；
11. 创建任务后生成唯一 id；
12. 创建任务后保存到内存；
13. list 接口返回已创建任务；
14. detail 接口能按 id 返回任务；
15. mock task 最终状态为 `SUCCESS`；
16. mock summary 清晰标记为 mock；
17. mock riskLevel 为 `LOW`；
18. issues 返回空数组；
19. `mvn test` 通过；
20. Spring Boot runtime 验证通过；
21. 未引入数据库；
22. 未引入 MyBatis-Plus / MySQL / JPA / Hibernate；
23. 未创建 Entity / Mapper / Repository；
24. 未调用 `ai-service`；
25. 未调用 GitHub API；
26. 未执行 Semgrep；
27. 未调用 LLM；
28. 未创建 frontend / ai-service 业务代码；
29. 未发现未授权依赖；
30. Qoder 未发现 blocking issue。

---

## 5. Round 03 Non-blocking Backlog

以下问题不影响 Round 03 验收，但应进入后续 backlog。

### 5.1 ReviewTask issues 类型收敛

当前：

```text
ReviewTask.issues: List<Object>
```

风险：

1. 当前 mock v1 总是返回空数组，因此不阻塞；
2. 后续引入真实 issue 时，`List<Object>` 会成为类型债务；
3. 后续应改为 domain issue model 或明确 DTO mapping。

建议处理时机：

```text
Round 05 persistence or Round 06 ai-service integration preparation
```

---

### 5.2 createdAt / updatedAt timestamp 统一

当前 `createdAt` 和 `updatedAt` 可能由两次 `LocalDateTime.now()` 生成，存在纳秒级差异。

建议后续统一为：

```java
LocalDateTime now = LocalDateTime.now();
```

建议处理时机：

```text
Round 05 persistence v1 or API polish round
```

---

### 5.3 Controller test isolation 改善

当前 `ReviewTaskControllerTest.listTasks_success` 只检查 `data` 非空，不严格检查 list size。

原因：

1. `@SpringBootTest` 可能共享应用上下文；
2. in-memory service bean 可能在测试间保留数据；
3. 当前测试是 order-independent 的务实写法。

建议后续考虑：

```text
@DirtiesContext
test reset helper
dedicated test profile
```

建议处理时机：

```text
Round 05 or test hardening round
```

---

### 5.4 validation message locale 标准化

Codex 环境返回中文 Bean Validation message，Cursor 示例返回英文 message。

当前测试只断言：

```text
success=false
message contains field name
data=null
```

因此不阻塞。

后续 frontend 联调如果要展示稳定文案，需要统一：

```text
MessageSource
LocaleResolver
explicit annotation message
error code model
```

建议处理时机：

```text
Before frontend production-grade error display
```

---

### 5.5 timezone 策略统一

当前 `LocalDateTime` 序列化为无 timezone 的 ISO string，例如：

```text
2026-06-23T06:22:07.527724
```

与 Round 03 示例兼容，但后续真实前后端联调前应明确：

```text
Instant
OffsetDateTime
UTC ISO string
local timezone policy
```

建议处理时机：

```text
Round 05 or API polish round
```

---

### 5.6 Round 02 注释陈旧清理

当前部分注释仍引用 Round 02，例如：

```text
WebConfig.java
application-local.yml
```

不影响运行。

建议处理时机：

```text
Any future cleanup task
```

---

## 6. Round 04 Proposed Direction

### 6.1 Round 04 Name

```text
Round 04: frontend ReviewTask Mock UI v1
```

### 6.2 Round 04 Strategic Goal

Round 04 的目标是在 frontend 中实现 ReviewTask mock UI，并与 Round 03 已完成的 `backend-java` mock API 完成基础联调。

本轮核心目标：

1. 形成可演示的前后端闭环；
2. 验证 ReviewTask API contract 是否适合 UI；
3. 为后续持久化前确认字段、状态、错误响应是否合理；
4. 不提前引入数据库；
5. 不调用 `ai-service`；
6. 不调用 GitHub API；
7. 不执行 Semgrep；
8. 不调用 LLM；
9. 不引入复杂权限系统；
10. 不做 production-grade frontend 架构。

---

## 7. Round 04 Recommended Product Scope

Round 04 推荐实现一个最小但完整的 ReviewTask 前端体验。

### 7.1 User Flow

用户应能够：

1. 打开前端页面；
2. 输入 GitHub repository URL；
3. 输入 PR number；
4. 点击创建 review task；
5. 看到创建成功后的 mock review result；
6. 查看 review task list；
7. 点击或选择某个 task 查看 detail；
8. 看到 API error 或 validation error 的基础提示；
9. 刷新页面后允许数据丢失或重新从 backend in-memory list 拉取。

### 7.2 Recommended Screens / Components

允许 Cursor 实现以下最小 UI：

```text
ReviewTaskPage
ReviewTaskCreateForm
ReviewTaskList
ReviewTaskDetail
ApiStatus / ErrorMessage / LoadingState
```

如果项目已存在 frontend 框架，则基于现有框架实现。

如果 `frontend/` 当前只有 README，则允许创建最小 frontend app。

---

## 8. Round 04 Recommended Technical Direction

### 8.1 Frontend Stack Decision

如果 `frontend/` 尚未初始化，推荐使用：

```text
React + TypeScript + Vite
```

理由：

1. 初始化轻量；
2. 与现代前端工程实践匹配；
3. 适合快速展示 MVP；
4. 容易配置 API base URL；
5. 适合后续扩展为更完整 frontend。

推荐但不强制：

```text
npm
Vite
React
TypeScript
```

不建议本轮引入：

```text
Next.js
Redux
MobX
复杂组件库
复杂路由系统
复杂权限系统
SSR
微前端
```

### 8.2 API Base URL

必须支持配置 backend API base URL。

推荐：

```text
VITE_API_BASE_URL=http://localhost:8080
```

如果未设置，则默认：

```text
http://localhost:8080
```

frontend 调用路径：

```text
GET  ${VITE_API_BASE_URL}/api/health
POST ${VITE_API_BASE_URL}/api/review-tasks
GET  ${VITE_API_BASE_URL}/api/review-tasks
GET  ${VITE_API_BASE_URL}/api/review-tasks/{id}
```

---

## 9. Round 04 API Contract to Consume

### 9.1 Health API

```http
GET /api/health
```

Response:

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "status": "UP",
    "service": "backend-java"
  }
}
```

Frontend usage:

1. 可选显示 backend status；
2. 不需要复杂健康面板；
3. 主要用于联调确认。

---

### 9.2 Create Review Task

```http
POST /api/review-tasks
Content-Type: application/json
```

Request:

```json
{
  "repoUrl": "https://github.com/example/repo",
  "prNumber": 123
}
```

Success response:

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": 1,
    "repoUrl": "https://github.com/example/repo",
    "prNumber": 123,
    "status": "SUCCESS",
    "summary": "Mock review completed for PR #123.",
    "riskLevel": "LOW",
    "errorMessage": null,
    "createdAt": "2026-06-23T06:22:07.527724",
    "updatedAt": "2026-06-23T06:22:07.528436",
    "issues": []
  }
}
```

Frontend requirements:

1. submit loading state；
2. success 后刷新 list 或将新 task 加入 list；
3. success 后显示 detail；
4. validation failure 时显示错误 message；
5. 不需要前端复杂校验，但建议做基础 required / positive 校验。

---

### 9.3 List Review Tasks

```http
GET /api/review-tasks
```

Response:

```json
{
  "success": true,
  "message": "OK",
  "data": [
    {
      "id": 1,
      "repoUrl": "https://github.com/example/repo",
      "prNumber": 123,
      "status": "SUCCESS",
      "summary": "Mock review completed for PR #123.",
      "riskLevel": "LOW",
      "errorMessage": null,
      "createdAt": "2026-06-23T06:22:07.527724",
      "updatedAt": "2026-06-23T06:22:07.528436",
      "issues": []
    }
  ]
}
```

Frontend requirements:

1. 页面加载时请求 list；
2. 空数据时显示 empty state；
3. 有数据时显示 task cards 或 table；
4. 每条 task 至少显示：
   - id
   - repoUrl
   - prNumber
   - status
   - riskLevel
   - summary
   - createdAt
5. 点击 task 后可查看 detail。

---

### 9.4 Get Review Task Detail

```http
GET /api/review-tasks/{id}
```

Success response:

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": 1,
    "repoUrl": "https://github.com/example/repo",
    "prNumber": 123,
    "status": "SUCCESS",
    "summary": "Mock review completed for PR #123.",
    "riskLevel": "LOW",
    "errorMessage": null,
    "createdAt": "2026-06-23T06:22:07.527724",
    "updatedAt": "2026-06-23T06:22:07.528436",
    "issues": []
  }
}
```

Not found response:

```json
{
  "success": false,
  "message": "Review task not found",
  "data": null
}
```

Frontend requirements:

1. detail 区域展示完整 task；
2. not found 时显示错误提示；
3. issues 为空数组时显示：
   - `No issues found in mock review.`
4. 不需要真实代码 diff 展示；
5. 不需要 issue severity 图表。

---

## 10. Round 04 Recommended Frontend Structure

如果创建 React + TypeScript + Vite 项目，推荐结构：

```text
frontend/
  package.json
  index.html
  vite.config.ts
  tsconfig.json
  src/
    main.tsx
    App.tsx
    api/
      reviewTaskApi.ts
    types/
      reviewTask.ts
      apiResponse.ts
    components/
      ReviewTaskCreateForm.tsx
      ReviewTaskList.tsx
      ReviewTaskDetail.tsx
      LoadingState.tsx
      ErrorMessage.tsx
    styles/
      app.css
  README.md
```

允许更简化，但必须满足：

1. API 调用逻辑与 UI 组件适度分离；
2. 类型定义清晰；
3. 不把所有逻辑堆在一个超大文件里；
4. README 说明如何启动 frontend；
5. README 说明如何配置 backend API base URL。

---

## 11. Round 04 Allowed Implementation

Round 04 允许 Cursor：

1. 初始化 frontend 最小项目；
2. 使用 React + TypeScript + Vite；
3. 实现 ReviewTask create form；
4. 实现 ReviewTask list；
5. 实现 ReviewTask detail；
6. 实现 frontend API client；
7. 实现基础 loading state；
8. 实现基础 error state；
9. 实现 empty state；
10. 使用 backend-java mock API；
11. 增加基础样式；
12. 增加基础前端测试；
13. 增加 build / lint / typecheck script；
14. 更新 frontend README；
15. 更新根 README 中的 frontend 状态说明，如存在；
16. 输出 Cursor handoff。

---

## 12. Round 04 Forbidden Scope

Round 04 仍然禁止：

1. 引入数据库；
2. 修改 backend-java 为数据库持久化；
3. 引入 MyBatis-Plus；
4. 引入 MySQL Driver；
5. 引入 JPA / Hibernate；
6. 创建 database schema；
7. 创建 migration；
8. 创建 Mapper / Repository；
9. 调用 `ai-service`；
10. 创建 ai-service client；
11. 调用 GitHub API；
12. 执行 Semgrep；
13. 调用 LLM；
14. 实现真实代码审查；
15. 引入 Redis；
16. 引入 MQ；
17. 引入 Scheduler；
18. 引入复杂权限系统；
19. 引入 Spring Security；
20. 引入 Swagger / OpenAPI；
21. 修改 docker-compose 为真实服务；
22. 修改 GitHub Actions 为真实 CI；
23. 创建 ai-service 业务代码；
24. 实现 production-grade design system；
25. 引入复杂状态管理库；
26. 引入 Next.js / SSR，除非项目之前已经确定；
27. 进入 Round 05；
28. 擅自扩大任务范围。

---

## 13. Round 04 Agent Plan

Round 04 继续沿用三 Agent 流程。

```text
Cursor implements
Codex validates
Qoder reviews
ChatGPT accepts
```

---

### 13.1 Cursor Task

推荐任务文件：

```text
tasks/round-04/01-cursor-frontend-reviewtask-mock-ui.md
```

Cursor 负责：

1. 检查当前 `frontend/` 目录状态；
2. 如果只有 README，则初始化最小 frontend app；
3. 实现 ReviewTask mock UI；
4. 对接 `backend-java` mock API；
5. 实现 create/list/detail 基础流程；
6. 实现 loading / error / empty states；
7. 配置 API base URL；
8. 添加基础测试、build、typecheck；
9. 更新 `frontend/README.md`；
10. 不修改 backend-java 业务逻辑，除非仅为 CORS 联调做最小必要调整；
11. 输出 Cursor handoff。

Cursor Handoff：

```text
handoff/round-04/01-cursor-frontend-reviewtask-mock-ui-handoff.md
```

---

### 13.2 Codex Task

推荐任务文件：

```text
tasks/round-04/02-codex-frontend-reviewtask-mock-ui-validation.md
```

Codex 负责：

1. 安装 frontend 依赖；
2. 执行 frontend build；
3. 执行 frontend test，如存在；
4. 执行 frontend typecheck，如存在；
5. 启动 backend-java；
6. 启动 frontend；
7. 验证 UI 能调用 backend API；
8. 验证 create task 流程；
9. 验证 list task 流程；
10. 验证 detail 展示；
11. 验证 validation error 展示；
12. 验证 backend scope 未被破坏；
13. 验证未调用 ai-service / GitHub / Semgrep / LLM；
14. 只做最小修复；
15. 输出 Codex handoff。

Codex Handoff：

```text
handoff/round-04/02-codex-frontend-reviewtask-mock-ui-validation-handoff.md
```

---

### 13.3 Qoder Task

推荐任务文件：

```text
tasks/round-04/03-qoder-frontend-reviewtask-mock-ui-independent-review.md
```

Qoder 负责：

1. 审查 frontend 架构；
2. 审查 API client；
3. 审查组件分层；
4. 审查类型定义；
5. 审查状态管理复杂度；
6. 审查 loading / error / empty state；
7. 审查 UI 是否真实消费 backend-java mock API；
8. 审查是否存在越界实现；
9. 审查测试 / build 质量；
10. 输出 independent review。

Qoder Handoff：

```text
handoff/round-04/03-qoder-frontend-reviewtask-mock-ui-independent-review.md
```

---

## 14. Round 04 Acceptance Criteria Draft

### 14.1 Frontend Project

- [ ] `frontend/` 存在可运行前端项目；
- [ ] 若新建项目，技术栈轻量、合理；
- [ ] README 说明安装、启动、配置；
- [ ] 支持配置 backend API base URL；
- [ ] 不引入复杂无关架构。

### 14.2 UI Flow

- [ ] 页面可打开；
- [ ] 用户可输入 `repoUrl`；
- [ ] 用户可输入 `prNumber`；
- [ ] 用户可提交创建 ReviewTask；
- [ ] 创建成功后展示 task detail；
- [ ] 页面可展示 task list；
- [ ] 页面可选择 task 并展示 detail；
- [ ] 空列表有 empty state；
- [ ] 请求中有 loading state；
- [ ] 请求失败有 error state；
- [ ] validation failure 有用户可见提示。

### 14.3 API Integration

- [ ] frontend 调用 `GET /api/health` 或至少能配置验证 backend 状态；
- [ ] frontend 调用 `POST /api/review-tasks`；
- [ ] frontend 调用 `GET /api/review-tasks`；
- [ ] frontend 调用 `GET /api/review-tasks/{id}`；
- [ ] 正确处理 `ApiResponse<T>`；
- [ ] 正确处理 `success=false`；
- [ ] 正确处理 `data=null`；
- [ ] 不硬编码固定 task id；
- [ ] 不依赖固定 timestamp；
- [ ] 不依赖 validation message 的固定语言。

### 14.4 Scope Control

- [ ] 未引入数据库；
- [ ] 未修改 backend-java 为持久化；
- [ ] 未引入 MyBatis-Plus；
- [ ] 未引入 MySQL；
- [ ] 未引入 JPA / Hibernate；
- [ ] 未创建 Entity / Mapper / Repository；
- [ ] 未调用 `ai-service`；
- [ ] 未调用 GitHub API；
- [ ] 未执行 Semgrep；
- [ ] 未调用 LLM；
- [ ] 未创建 ai-service 业务代码；
- [ ] 未添加复杂 auth/security；
- [ ] 未添加未授权后端依赖；
- [ ] 未进入 Round 05。

### 14.5 Test and Validation

- [ ] frontend build 通过；
- [ ] frontend typecheck 通过，如存在；
- [ ] frontend test 通过，如存在；
- [ ] backend-java `mvn test` 仍通过，如本轮触及 backend；
- [ ] backend-java runtime 可启动；
- [ ] frontend runtime 可启动；
- [ ] create/list/detail 可手动或自动验证；
- [ ] 无外部服务依赖；
- [ ] 无数据库依赖。

---

## 15. Important Design Decisions for Round 04

### 15.1 Do Frontend Before Persistence

Round 04 先做 frontend，而不是先做 backend persistence。

原因：

1. Round 03 API mock 已足够支撑 UI；
2. UI 联调可以验证 API contract 是否合理；
3. 在持久化前暴露字段、状态、错误响应问题更便宜；
4. 可以更快形成可展示 MVP；
5. 后续再做 persistence 会更有数据模型依据。

---

### 15.2 Keep Frontend Minimal

Round 04 不做复杂前端架构。

允许：

```text
React state
simple components
simple API client
basic CSS
basic tests
```

禁止或不推荐：

```text
Redux
MobX
复杂路由
权限系统
design system
SSR
Next.js
微前端
复杂缓存层
```

原因：

1. 当前重点是 API 联调；
2. 不应过早投入前端架构复杂度；
3. 先完成可演示闭环。

---

### 15.3 Keep Backend Stable

Round 04 原则上不改 backend-java 业务逻辑。

唯一允许的小改动：

```text
CORS support for frontend local development
```

如果 frontend 调用 backend 因浏览器 CORS 被阻止，可以在 backend-java 添加最小 CORS 配置。

要求：

1. 不引入 Spring Security；
2. 不做复杂安全配置；
3. 仅允许本地开发所需的 origin；
4. 在 handoff 中明确说明；
5. Codex / Qoder 必须复核。

推荐本地 origin：

```text
http://localhost:5173
http://127.0.0.1:5173
```

如果能通过 Vite dev server proxy 解决 CORS，则优先使用 frontend proxy，避免修改 backend。

---

### 15.4 Do Not Depend on Persistent Data

Round 04 frontend 必须理解 backend 数据是 in-memory mock。

要求：

1. frontend 不假设刷新后一定仍有历史任务；
2. frontend empty state 必须合理；
3. README 要说明 backend 重启后任务丢失；
4. 不要使用 localStorage 假装持久化，除非只是 UI 暂存且明确说明。

---

## 16. Initial Risk Assessment

### 16.1 Main Risks

1. Cursor 可能过度设计 frontend；
2. Cursor 可能引入复杂状态管理；
3. Cursor 可能默认创建 Next.js 或 SSR 项目，增加复杂度；
4. Cursor 可能硬编码 API base URL；
5. Cursor 可能不处理 `ApiResponse<T>` 的 `success=false`；
6. Cursor 可能依赖固定 task id；
7. Cursor 可能依赖固定 validation message 文案；
8. Cursor 可能因为 CORS 修改 backend 过重；
9. Cursor 可能提前实现 GitHub / AI review 相关 UI；
10. Cursor 可能进入 persistence 或 ai-service 集成范围。

### 16.2 Risk Control

Round 04 任务文档必须明确：

1. frontend 只消费 backend-java mock API；
2. 不调用 `ai-service`；
3. 不调用 GitHub API；
4. 不执行 Semgrep；
5. 不调用 LLM；
6. 不做数据库；
7. 不做持久化；
8. API base URL 必须可配置；
9. 必须处理 `ApiResponse<T>`；
10. 不依赖固定 id；
11. 不依赖固定 timestamp；
12. 不依赖固定 validation message 语言；
13. CORS 优先用 Vite proxy，backend CORS 仅作为最小备选；
14. Codex 必须做 runtime 联调验证；
15. Qoder 必须审查 scope boundary。

---

## 17. Recommended First Task for Round 04

下一步建议生成 Cursor 任务：

```text
tasks/round-04/01-cursor-frontend-reviewtask-mock-ui.md
```

该任务应要求 Cursor：

1. 基于 Round 03 已验收的 backend API；
2. 只在 `frontend/` 内实现 ReviewTask mock UI；
3. 如必要，仅做最小 CORS/proxy 配置；
4. 实现 create/list/detail UI；
5. 实现 API client；
6. 实现 loading / error / empty state；
7. 添加基础 build/typecheck/test；
8. 更新 README；
9. 不引入数据库；
10. 不调用 `ai-service`；
11. 不实现 GitHub / Semgrep / LLM；
12. 不进入 Round 05；
13. 完成后生成 handoff。

---

## 18. Final Instruction for Round 04 Planning

Round 03 已正式完成，不再回到 Round 03 返工。

Round 04 应从以下任务开始：

```text
tasks/round-04/01-cursor-frontend-reviewtask-mock-ui.md
```

Round 04 的核心原则：

```text
实现 frontend ReviewTask mock UI，对接 backend-java mock API，但不做数据库、不做 ai-service、不做 GitHub、不做 Semgrep、不做 LLM。
```

继续保持三 Agent 协作流程：

```text
Cursor implements
Codex validates
Qoder reviews
ChatGPT accepts
```