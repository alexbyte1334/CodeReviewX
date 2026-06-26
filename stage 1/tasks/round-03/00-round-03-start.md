# tasks/round-03/00-round-03-start.md

# Round 03 Start Document: backend-java ReviewTask API Mock v1

## 1. Document Metadata

- Project: CodeReviewX
- Document Type: Round Start Document
- Current Round: Round 03
- Previous Round: Round 02
- Created By: ChatGPT Architect
- Target Readers:
  - Cursor
  - Codex
  - Qoder
- Recommended Next Task:
  - `tasks/round-03/01-cursor-backend-reviewtask-api-mock.md`

---

## 2. Round 02 Summary

### 2.1 Round 02 Name

```text
Round 02: backend-java Skeleton v1
```

### 2.2 Round 02 Goal

Round 02 的目标是为 `backend-java` 创建最小 Spring Boot 3 + Java 17 + Maven 项目骨架。

Round 02 目标包括：

1. 创建可编译的 Maven 项目；
2. 创建可启动的 Spring Boot 应用；
3. 提供基础健康检查接口；
4. 创建基础 `ApiResponse<T>`；
5. 创建 Review 相关 DTO 占位类；
6. 创建 Review 相关枚举；
7. 创建基础配置文件；
8. 创建 Spring context load 测试；
9. 严格不实现 ReviewTask 业务流程；
10. 严格不引入数据库、MyBatis-Plus、MySQL；
11. 严格不调用 `ai-service`；
12. 严格不实现 GitHub / Semgrep / LLM。

---

## 3. Round 02 Execution Summary

Round 02 按三 Agent 流程完成：

```text
Cursor implementation
    ↓
Codex validation
    ↓
Qoder independent review
    ↓
ChatGPT Architect final acceptance
```

### 3.1 Cursor Implementation

Cursor 完成了 `backend-java` skeleton。

主要创建内容：

```text
backend-java/pom.xml
backend-java/src/main/java/com/codereviewx/backend/CodeReviewXBackendApplication.java
backend-java/src/main/java/com/codereviewx/backend/common/ApiResponse.java
backend-java/src/main/java/com/codereviewx/backend/controller/HealthController.java
backend-java/src/main/java/com/codereviewx/backend/config/WebConfig.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/CreateReviewTaskRequest.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewTaskResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewIssueResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/ReviewTaskStatus.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/RiskLevel.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueType.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSeverity.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSource.java
backend-java/src/main/resources/application.yml
backend-java/src/main/resources/application-local.yml
backend-java/src/test/java/com/codereviewx/backend/CodeReviewXBackendApplicationTests.java
```

Cursor 未执行 `mvn test`，原因是当时执行环境缺少 Java 17 和 Maven。

Cursor Handoff：

```text
handoff/round-02/01-cursor-backend-java-skeleton-handoff.md
```

### 3.2 Codex Validation

Codex 配置了 Java 17 + Maven 环境，并完成验证。

关键结果：

1. Java 17 配置成功；
2. Maven 配置成功；
3. `mvn test` 执行成功；
4. Spring context load test 通过；
5. `GET /api/health` 运行时验证通过；
6. 未发现 ReviewTask 业务 API；
7. 未发现持久化层；
8. 未发现 MyBatis-Plus / MySQL / JPA；
9. 未发现 `ai-service` / GitHub / Semgrep / LLM 集成；
10. 未发现 frontend / ai-service 业务代码；
11. 未发现未授权依赖；
12. Codex 没有修改 backend 源码或配置。

Codex 验证结果：

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

健康检查响应：

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

Codex Handoff：

```text
handoff/round-02/02-codex-backend-java-validation-handoff.md
```

### 3.3 Qoder Independent Review

Qoder 完成独立架构审查和代码审查。

Qoder 审查范围包括：

1. Round 02 task 文档；
2. Cursor handoff；
3. Codex handoff；
4. `backend-java` 全部源码、配置和测试；
5. Maven 依赖；
6. API 范围；
7. 持久化范围；
8. 外部集成范围；
9. frontend / ai-service 边界；
10. 依赖污染检查。

Qoder 独立重新执行了 `mvn test`，结果通过：

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Qoder 结论：

```text
Overall Risk: LOW
Recommend accepting Round 02 with non-blocking suggestions.
```

Qoder Handoff：

```text
handoff/round-02/03-qoder-backend-java-independent-review.md
```

---

## 4. Round 02 Final Architect Decision

Round 02 正式验收通过。

### 4.1 Acceptance Result

```text
Round 02: Accepted
```

### 4.2 Acceptance Basis

Round 02 满足以下条件：

1. `backend-java` Spring Boot 3 + Java 17 + Maven skeleton 已创建；
2. `GET /api/health` 已实现并通过运行时验证；
3. `ApiResponse<T>` 已创建；
4. DTO placeholder 已创建；
5. Review enums 已创建；
6. `application.yml` 和 `application-local.yml` 已创建；
7. Spring context load test 已创建并通过；
8. `mvn test` 已通过 Codex 和 Qoder 双重验证；
9. 未实现 ReviewTask 业务 API；
10. 未实现数据库持久化；
11. 未引入 MyBatis-Plus / MySQL / JPA；
12. 未调用 `ai-service`；
13. 未实现 GitHub / Semgrep / LLM；
14. 未创建 frontend / ai-service 业务代码；
15. 未发现未授权依赖；
16. 未发现架构漂移。

### 4.3 Non-blocking Notes for Future Rounds

Qoder 提出的非阻塞建议进入后续轮次关注：

1. `ReviewTaskResponse.createdAt` / `updatedAt` 当前使用 `LocalDateTime`，Round 03 或后续真实 API 返回时，需要确认时间序列化格式是否与 `docs/API.md` 一致；
2. `CreateReviewTaskRequest` 已有 `@NotBlank` 和 `@Positive`，Round 03 实现 Controller 时必须使用 `@Valid`；
3. `ApiResponse.isSuccess()` 当前符合 Java Bean / Jackson 规范，无需调整；
4. `WebConfig` 当前保持空占位，CORS / Interceptor / Security 不应在 Round 03 非必要提前引入；
5. `backend-java/README.md` 的未来职责描述可以保留，但实现状态必须继续保持准确。

---

## 5. Round 03 Proposed Direction

### 5.1 Round 03 Name

```text
Round 03: backend-java ReviewTask API Mock v1
```

### 5.2 Round 03 Strategic Goal

Round 03 的目标是在不引入数据库、不调用 `ai-service` 的前提下，实现 `backend-java` 的 ReviewTask 业务 API mock 版本。

核心目标：

1. 让前端未来可以基于稳定 API contract 进行联调；
2. 让 backend-java 开始具备 ReviewTask 生命周期管理雏形；
3. 保持实现轻量、可测试、可展示；
4. 不提前进入 MySQL / MyBatis-Plus / ai-service 集成；
5. 为后续 Round 04 或 Round 05 的持久化和 ai-service 调用打基础。

---

## 6. Round 03 Recommended Scope

### 6.1 Round 03 Allowed Implementation

Round 03 建议允许 Cursor 实现：

1. `POST /api/review-tasks`
2. `GET /api/review-tasks`
3. `GET /api/review-tasks/{id}`
4. 内存级 ReviewTask mock 存储；
5. ReviewTask service；
6. ReviewTask domain model 或 internal model；
7. ReviewTask mock 数据生成；
8. ReviewTask 状态流 mock：
   - `PENDING`
   - `RUNNING`
   - `SUCCESS`
   - `FAILED`
9. 基础参数校验：
   - `repoUrl` not blank
   - `prNumber` positive
10. 基础错误响应：
   - task not found
   - validation failure
11. Controller 层基础测试或 context test 扩展；
12. README 更新 Round 03 状态。

### 6.2 Round 03 Recommended API Contract

#### 6.2.1 Create Review Task

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

Response:

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
    "createdAt": "2026-06-22T10:00:00",
    "updatedAt": "2026-06-22T10:00:00",
    "issues": []
  }
}
```

Round 03 可以直接返回 mock completed task，不需要异步任务。

#### 6.2.2 List Review Tasks

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
      "createdAt": "2026-06-22T10:00:00",
      "updatedAt": "2026-06-22T10:00:00",
      "issues": []
    }
  ]
}
```

#### 6.2.3 Get Review Task Detail

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
    "createdAt": "2026-06-22T10:00:00",
    "updatedAt": "2026-06-22T10:00:00",
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

---

## 7. Round 03 Recommended Backend Structure

Round 03 建议在现有 `backend-java` 基础上新增：

```text
backend-java/src/main/java/com/codereviewx/backend/review/controller/ReviewTaskController.java
backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java
backend-java/src/main/java/com/codereviewx/backend/review/model/ReviewTask.java
backend-java/src/main/java/com/codereviewx/backend/review/exception/ReviewTaskNotFoundException.java
backend-java/src/main/java/com/codereviewx/backend/common/GlobalExceptionHandler.java
```

可选新增测试：

```text
backend-java/src/test/java/com/codereviewx/backend/review/controller/ReviewTaskControllerTest.java
backend-java/src/test/java/com/codereviewx/backend/review/service/ReviewTaskServiceTest.java
```

注意：

- `ReviewTask` 在 Round 03 只能是内存模型或 domain model；
- 不得添加 `@Entity`；
- 不得添加 MyBatis / JPA 注解；
- 不得创建 Mapper；
- 不得创建数据库表；
- 不得引入 MySQL；
- 不得引入 MyBatis-Plus。

---

## 8. Round 03 Forbidden Scope

Round 03 仍然禁止：

1. 引入 MyBatis-Plus；
2. 引入 MySQL Driver；
3. 引入 JPA / Hibernate；
4. 创建数据库 schema；
5. 创建 migration；
6. 创建 MyBatis Mapper；
7. 创建 Repository 指向数据库；
8. 调用 `ai-service`；
9. 调用 GitHub API；
10. 执行 Semgrep；
11. 调用 LLM；
12. 实现真实异步任务队列；
13. 引入 Redis；
14. 引入 MQ；
15. 引入 Spring Security；
16. 引入 Swagger / OpenAPI；
17. 引入 Lombok；
18. 创建 frontend 业务代码；
19. 创建 ai-service 业务代码；
20. 修改 docker-compose 为真实服务；
21. 修改 GitHub Actions 为真实 CI；
22. 进入 Round 04；
23. 擅自扩大任务范围。

---

## 9. Round 03 Agent Plan

Round 03 继续沿用三 Agent 流程。

### 9.1 Cursor Task

推荐任务文件：

```text
tasks/round-03/01-cursor-backend-reviewtask-api-mock.md
```

Cursor 负责：

1. 实现 ReviewTask mock API；
2. 实现内存级 service；
3. 实现基础异常处理；
4. 实现必要测试；
5. 更新 `backend-java/README.md`；
6. 输出 Cursor Handoff。

Cursor Handoff：

```text
handoff/round-03/01-cursor-backend-reviewtask-api-mock-handoff.md
```

### 9.2 Codex Task

推荐任务文件：

```text
tasks/round-03/02-codex-backend-reviewtask-api-validation.md
```

Codex 负责：

1. 执行 `mvn test`；
2. 启动服务；
3. 验证 `GET /api/health`；
4. 验证 `POST /api/review-tasks`；
5. 验证 `GET /api/review-tasks`；
6. 验证 `GET /api/review-tasks/{id}`；
7. 检查未引入数据库 / ai-service / GitHub / Semgrep / LLM；
8. 只做最小修复；
9. 输出 Codex Handoff。

Codex Handoff：

```text
handoff/round-03/02-codex-backend-reviewtask-api-validation-handoff.md
```

### 9.3 Qoder Task

推荐任务文件：

```text
tasks/round-03/03-qoder-backend-reviewtask-api-independent-review.md
```

Qoder 负责：

1. 审查 API contract；
2. 审查 mock service 是否符合范围；
3. 审查状态流；
4. 审查异常处理；
5. 审查测试覆盖；
6. 审查是否存在提前数据库化或外部集成；
7. 输出 Qoder Review。

Qoder Handoff：

```text
handoff/round-03/03-qoder-backend-reviewtask-api-independent-review.md
```

---

## 10. Round 03 Acceptance Criteria Draft

Round 03 初步验收标准如下。

### 10.1 API

- [ ] `POST /api/review-tasks` 可用；
- [ ] `GET /api/review-tasks` 可用；
- [ ] `GET /api/review-tasks/{id}` 可用；
- [ ] `GET /api/health` 继续可用；
- [ ] API 返回统一 `ApiResponse<T>`；
- [ ] 创建任务时使用 `@Valid`；
- [ ] 无效 `repoUrl` 可被校验；
- [ ] 无效 `prNumber` 可被校验；
- [ ] 查询不存在任务时返回清晰错误响应。

### 10.2 Mock ReviewTask Behavior

- [ ] 创建任务后生成唯一 id；
- [ ] 创建任务后保存到内存；
- [ ] list 接口返回已创建任务；
- [ ] detail 接口能按 id 返回任务；
- [ ] mock task 使用 `SUCCESS` 或明确的 mock 状态流；
- [ ] mock summary 清晰标记为 mock；
- [ ] mock riskLevel 可固定为 `LOW`；
- [ ] issues 可为空数组或包含 mock issue，但必须符合 DTO contract。

### 10.3 Scope Control

- [ ] 未引入 MyBatis-Plus；
- [ ] 未引入 MySQL；
- [ ] 未引入 JPA；
- [ ] 未创建 Entity；
- [ ] 未创建 Mapper；
- [ ] 未创建数据库迁移；
- [ ] 未调用 `ai-service`；
- [ ] 未调用 GitHub API；
- [ ] 未执行 Semgrep；
- [ ] 未调用 LLM；
- [ ] 未创建 frontend 业务代码；
- [ ] 未创建 ai-service 业务代码；
- [ ] 未添加未授权依赖。

### 10.4 Test and Validation

- [ ] `mvn test` 通过；
- [ ] Controller 或 service 至少有基础测试；
- [ ] 健康检查接口仍通过；
- [ ] ReviewTask API 可通过 curl 或 MockMvc 验证；
- [ ] 无外部服务依赖；
- [ ] 无数据库依赖。

---

## 11. Important Design Decisions for Round 03

### 11.1 Use In-memory Storage Only

Round 03 不接数据库。

推荐使用：

```java
private final Map<Long, ReviewTask> tasks = new ConcurrentHashMap<>();
private final AtomicLong idGenerator = new AtomicLong(1);
```

理由：

1. 保持 Round 03 快速可验证；
2. 避免提前引入 MySQL / MyBatis-Plus；
3. 为前端提供可用 API contract；
4. 降低集成复杂度。

### 11.2 Keep ai-service Out of Round 03

Round 03 不调用 `ai-service`。

原因：

1. `ai-service` 尚未实现；
2. GitHub / Semgrep / LLM 还未进入当前阶段；
3. ReviewTask API mock 可先稳定前后端契约；
4. 后续可在 Round 04 或 Round 05 引入 ai-service client。

### 11.3 Use Mock SUCCESS Result

Round 03 可以让创建任务后直接返回 mock `SUCCESS` 结果。

推荐：

```text
PENDING -> RUNNING -> SUCCESS
```

可以在 service 内同步完成状态转换，但不要引入异步线程、MQ 或 Scheduler。

### 11.4 Do Not Add Swagger

Round 03 不引入 Swagger / OpenAPI。

原因：

1. 当前项目文档已有 `docs/API.md`；
2. Swagger 会增加依赖和配置；
3. 当前优先完成核心 MVP 链路；
4. 后续再视简历展示和接口调试需要单独评估。

### 11.5 Keep Error Handling Minimal

Round 03 可以添加最小 `GlobalExceptionHandler`。

允许处理：

1. `ReviewTaskNotFoundException`
2. validation exception
3. generic exception fallback

但不要建立复杂错误码体系。

---

## 12. Round 03 Recommended First Task

下一步建议生成 Cursor 任务：

```text
tasks/round-03/01-cursor-backend-reviewtask-api-mock.md
```

该任务应要求 Cursor：

1. 基于 Round 02 已验收的 skeleton；
2. 只在 `backend-java/` 内实现 ReviewTask mock API；
3. 新增 `ReviewTaskController`；
4. 新增 `ReviewTaskService`；
5. 新增内存 model；
6. 新增最小异常处理；
7. 新增基础测试；
8. 更新 `backend-java/README.md`；
9. 不引入数据库；
10. 不调用 `ai-service`；
11. 不实现 GitHub / Semgrep / LLM；
12. 完成后生成 handoff。

---

## 13. Round 03 Initial Risk Assessment

### 13.1 Main Risks

1. Cursor 可能提前引入数据库持久化；
2. Cursor 可能提前调用 `ai-service`；
3. Cursor 可能创建过重的 service / repository 层；
4. Cursor 可能引入 Lombok / Swagger / JPA 等未授权依赖；
5. Controller validation 可能遗漏 `@Valid`；
6. mock 状态流可能与 Round 01 / Round 02 文档不一致；
7. 错误响应可能脱离 `ApiResponse<T>`。

### 13.2 Risk Control

Round 03 任务文档必须明确：

1. 只允许 in-memory mock；
2. 禁止数据库；
3. 禁止外部集成；
4. 禁止未授权依赖；
5. 必须使用 `ApiResponse<T>`；
6. 必须使用已有 DTO / enum；
7. 必须保留 `/api/health`；
8. 必须增加测试；
9. Codex 必须验证 curl / MockMvc；
10. Qoder 必须独立审查边界。

---

## 14. Final Instruction for Round 03 Planning

Round 02 已正式完成，不再回到 Round 02 修复。

Round 03 应从以下任务开始：

```text
tasks/round-03/01-cursor-backend-reviewtask-api-mock.md
```

Round 03 的核心原则：

```text
实现 ReviewTask API mock，但不做数据库、不做 ai-service、不做 GitHub、不做 Semgrep、不做 LLM。
```

继续保持三 Agent 协作流程：

```text
Cursor implements
Codex validates
Qoder reviews
ChatGPT accepts
```