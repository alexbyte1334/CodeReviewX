# tasks/round-03/01-cursor-backend-reviewtask-api-mock.md

# Cursor Task: backend-java ReviewTask API Mock v1

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 03
- Task ID: `01-cursor-backend-reviewtask-api-mock`
- Agent: Cursor
- Target Module: `backend-java`
- Task Type: Backend Implementation
- Expected Handoff Output:

```text
handoff/round-03/01-cursor-backend-reviewtask-api-mock-handoff.md
```

---

## 2. Background

Round 02 已完成并验收通过。

当前 `backend-java` 已具备：

1. Spring Boot 3 + Java 17 + Maven 项目骨架；
2. 可启动应用入口；
3. `GET /api/health` 健康检查接口；
4. 统一响应结构 `ApiResponse<T>`；
5. Review 相关 DTO placeholder；
6. Review 相关 enum；
7. `application.yml` 和 `application-local.yml`；
8. Spring context load 测试；
9. Maven 测试已通过 Codex 和 Qoder 双重验证。

Round 03 的目标是在不接入数据库、不调用 `ai-service`、不调用 GitHub、Semgrep 或 LLM 的前提下，实现 ReviewTask API mock v1。

---

## 3. Task Goal

请在 `backend-java/` 内实现 ReviewTask mock API。

本轮目标是让后续 frontend 可以基于稳定 API contract 做联调，同时为后续持久化和 `ai-service` 集成打基础。

本轮必须保持轻量、可测试、可验证。

---

## 4. Implementation Scope

### 4.1 Required APIs

必须实现以下接口：

```http
POST /api/review-tasks
GET /api/review-tasks
GET /api/review-tasks/{id}
```

同时必须保证已有接口继续可用：

```http
GET /api/health
```

---

## 5. API Contract

### 5.1 Create Review Task

#### Request

```http
POST /api/review-tasks
Content-Type: application/json
```

```json
{
  "repoUrl": "https://github.com/example/repo",
  "prNumber": 123
}
```

#### Success Response

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

说明：

1. Round 03 可以同步返回 mock completed task；
2. 不需要异步任务；
3. 不需要真实代码审查；
4. `summary` 必须清楚标记为 mock；
5. `riskLevel` 可以固定为 `LOW`；
6. `issues` 可以为空数组。

---

### 5.2 List Review Tasks

#### Request

```http
GET /api/review-tasks
```

#### Success Response

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

说明：

1. 返回内存中已创建的任务；
2. 若无任务，返回空数组；
3. 不需要分页；
4. 不需要排序参数；
5. 建议按创建顺序或 id 升序返回。

---

### 5.3 Get Review Task Detail

#### Request

```http
GET /api/review-tasks/{id}
```

#### Success Response

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

#### Not Found Response

```json
{
  "success": false,
  "message": "Review task not found",
  "data": null
}
```

---

## 6. Required Backend Structure

请在现有 `backend-java` 基础上新增或完善以下文件。

### 6.1 Required New Files

```text
backend-java/src/main/java/com/codereviewx/backend/review/controller/ReviewTaskController.java
backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java
backend-java/src/main/java/com/codereviewx/backend/review/model/ReviewTask.java
backend-java/src/main/java/com/codereviewx/backend/review/exception/ReviewTaskNotFoundException.java
backend-java/src/main/java/com/codereviewx/backend/common/GlobalExceptionHandler.java
```

### 6.2 Required Test Files

至少新增以下一种测试，推荐两者都新增：

```text
backend-java/src/test/java/com/codereviewx/backend/review/controller/ReviewTaskControllerTest.java
backend-java/src/test/java/com/codereviewx/backend/review/service/ReviewTaskServiceTest.java
```

### 6.3 Optional Existing Files to Modify

允许修改：

```text
backend-java/src/main/java/com/codereviewx/backend/review/dto/CreateReviewTaskRequest.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewTaskResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewIssueResponse.java
backend-java/src/main/java/com/codereviewx/backend/common/ApiResponse.java
backend-java/README.md
```

修改原则：

1. 只做必要补全；
2. 不破坏 Round 02 已验收能力；
3. 不引入未授权依赖；
4. 保持 DTO 与 API contract 一致。

---

## 7. Implementation Requirements

### 7.1 Controller

新增：

```text
ReviewTaskController
```

要求：

1. 使用 `@RestController`；
2. 路径前缀为 `/api/review-tasks`；
3. `POST /api/review-tasks` 必须使用 `@Valid @RequestBody CreateReviewTaskRequest request`；
4. `GET /api/review-tasks` 返回全部 mock tasks；
5. `GET /api/review-tasks/{id}` 按 id 返回 task；
6. 所有响应必须使用 `ApiResponse<T>`；
7. 不允许直接在 Controller 中保存状态；
8. Controller 必须调用 `ReviewTaskService`。

---

### 7.2 Service

新增：

```text
ReviewTaskService
```

要求：

1. 使用 `@Service`；
2. 使用内存存储；
3. 生成唯一 id；
4. 创建任务后保存到内存；
5. 支持 list；
6. 支持 detail；
7. 查询不存在 id 时抛出 `ReviewTaskNotFoundException`；
8. 不允许调用数据库；
9. 不允许调用外部服务；
10. 不允许启动异步线程；
11. 不允许引入 MQ、Redis、Scheduler。

推荐实现：

```java
private final Map<Long, ReviewTask> tasks = new ConcurrentHashMap<>();
private final AtomicLong idGenerator = new AtomicLong(1);
```

---

### 7.3 Internal Model

新增：

```text
ReviewTask
```

要求：

1. 只能是普通 Java class；
2. 不允许添加 `@Entity`；
3. 不允许添加 MyBatis 注解；
4. 不允许添加 JPA 注解；
5. 不允许添加 Lombok 注解；
6. 字段应能映射到 `ReviewTaskResponse`。

推荐字段：

```text
id
repoUrl
prNumber
status
summary
riskLevel
errorMessage
createdAt
updatedAt
issues
```

---

### 7.4 Mock Status Flow

Round 03 可以同步完成 mock 状态流。

推荐逻辑：

```text
PENDING -> RUNNING -> SUCCESS
```

实现要求：

1. 创建时可以先构造 `PENDING`；
2. 然后同步切换到 `RUNNING`；
3. 最后同步切换到 `SUCCESS`；
4. 不要使用异步线程；
5. 不要使用定时任务；
6. 不要真实执行代码审查。

如果实现中没有暴露中间状态，也可以最终直接返回 `SUCCESS`，但代码或注释中应明确这是 Round 03 mock 行为。

---

### 7.5 Mock Review Result

创建任务后，最终 mock task 推荐：

```text
status = SUCCESS
riskLevel = LOW
summary = "Mock review completed for PR #<prNumber>."
errorMessage = null
issues = empty list
```

要求：

1. `summary` 必须包含 `Mock` 或 `mock`；
2. `riskLevel` 使用已有 `RiskLevel.LOW`；
3. `status` 使用已有 `ReviewTaskStatus.SUCCESS`；
4. `issues` 返回空数组，不要返回 `null`。

---

### 7.6 Validation

必须保证 `CreateReviewTaskRequest` 至少具备以下校验：

```text
repoUrl: not blank
prNumber: positive
```

要求：

1. `repoUrl` 使用 `@NotBlank`；
2. `prNumber` 使用 `@Positive`；
3. Controller 创建接口必须使用 `@Valid`；
4. validation failure 必须返回统一 `ApiResponse`；
5. 不需要复杂错误码体系。

---

### 7.7 Exception Handling

新增：

```text
GlobalExceptionHandler
ReviewTaskNotFoundException
```

要求：

1. `ReviewTaskNotFoundException` 返回：

```json
{
  "success": false,
  "message": "Review task not found",
  "data": null
}
```

2. validation failure 返回统一 `ApiResponse`；
3. generic exception fallback 可以返回统一 `ApiResponse`；
4. 不要设计复杂 error code；
5. 不要引入额外异常框架；
6. 不要暴露 Java stack trace 到 API response。

---

## 8. README Update

请更新：

```text
backend-java/README.md
```

必须说明：

1. 当前已支持 ReviewTask mock API；
2. 当前 ReviewTask 数据只保存在内存中；
3. 服务重启后 mock 数据会丢失；
4. 当前不连接数据库；
5. 当前不调用 `ai-service`；
6. 当前不调用 GitHub / Semgrep / LLM；
7. 可给出 curl 示例。

推荐 README 示例内容：

```bash
curl http://localhost:8080/api/health

curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/repo","prNumber":123}'

curl http://localhost:8080/api/review-tasks

curl http://localhost:8080/api/review-tasks/1
```

---

## 9. Tests

### 9.1 Required Test Coverage

必须至少覆盖：

1. Spring context 仍可启动；
2. `POST /api/review-tasks` 成功；
3. `GET /api/review-tasks` 成功；
4. `GET /api/review-tasks/{id}` 成功；
5. 查询不存在 id 返回清晰错误；
6. invalid `repoUrl` 被校验；
7. invalid `prNumber` 被校验。

### 9.2 Recommended Test Style

优先使用：

```text
MockMvc
```

或 service unit test。

推荐新增：

```text
ReviewTaskControllerTest
ReviewTaskServiceTest
```

### 9.3 Test Command

完成后必须执行：

```bash
cd backend-java
mvn test
```

若当前环境无法执行，请在 handoff 中明确说明原因。

---

## 10. Strict Forbidden Scope

本任务严禁以下行为。

### 10.1 Database / Persistence Forbidden

不得：

1. 引入 MyBatis-Plus；
2. 引入 MySQL Driver；
3. 引入 JPA；
4. 引入 Hibernate；
5. 创建 `@Entity`；
6. 创建 Mapper；
7. 创建 Repository 指向数据库；
8. 创建数据库 schema；
9. 创建 migration；
10. 修改配置以连接真实数据库。

---

### 10.2 External Integration Forbidden

不得：

1. 调用 `ai-service`；
2. 创建 ai-service client；
3. 调用 GitHub API；
4. 执行 Semgrep；
5. 调用 LLM；
6. 调用 OpenAI / Claude / Gemini / DeepSeek 等模型；
7. 创建真实代码审查流程；
8. 创建 webhook；
9. 创建异步任务队列。

---

### 10.3 Infrastructure Forbidden

不得：

1. 引入 Redis；
2. 引入 MQ；
3. 引入 Scheduler；
4. 引入 Spring Security；
5. 引入 Swagger / OpenAPI；
6. 引入 Lombok；
7. 修改 docker-compose 为真实服务；
8. 修改 GitHub Actions 为真实 CI；
9. 添加未授权依赖。

---

### 10.4 Module Boundary Forbidden

不得：

1. 创建 frontend 业务代码；
2. 创建 ai-service 业务代码；
3. 修改 unrelated module；
4. 跨越 Round 03 范围进入 Round 04；
5. 实现真实持久化；
6. 实现真实 AI review。

---

## 11. Acceptance Criteria

Cursor 完成后，必须满足以下验收标准。

### 11.1 API

- [ ] `POST /api/review-tasks` 可用；
- [ ] `GET /api/review-tasks` 可用；
- [ ] `GET /api/review-tasks/{id}` 可用；
- [ ] `GET /api/health` 继续可用；
- [ ] API 返回统一 `ApiResponse<T>`；
- [ ] 创建任务时使用 `@Valid`；
- [ ] 无效 `repoUrl` 可被校验；
- [ ] 无效 `prNumber` 可被校验；
- [ ] 查询不存在任务时返回清晰错误响应。

### 11.2 Mock Behavior

- [ ] 创建任务后生成唯一 id；
- [ ] 创建任务后保存到内存；
- [ ] list 接口返回已创建任务；
- [ ] detail 接口能按 id 返回任务；
- [ ] mock task 最终状态为 `SUCCESS`；
- [ ] mock summary 清晰标记为 mock；
- [ ] mock riskLevel 为 `LOW`；
- [ ] issues 为空数组或符合 DTO contract；
- [ ] 服务不依赖任何外部系统。

### 11.3 Scope Control

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

### 11.4 Test and Validation

- [ ] `mvn test` 通过；
- [ ] Controller 或 service 至少有基础测试；
- [ ] 健康检查接口仍通过；
- [ ] ReviewTask API 可通过 MockMvc 或 curl 验证；
- [ ] 无外部服务依赖；
- [ ] 无数据库依赖。

---

## 12. Required Handoff

完成实现后，请创建：

```text
handoff/round-03/01-cursor-backend-reviewtask-api-mock-handoff.md
```

handoff 必须包含以下内容。

### 12.1 Summary

说明本次完成了什么。

### 12.2 Files Changed

列出新增和修改文件。

示例：

```text
Added:
- backend-java/src/main/java/...
- backend-java/src/test/java/...

Modified:
- backend-java/README.md
```

### 12.3 API Implemented

列出：

```text
POST /api/review-tasks
GET /api/review-tasks
GET /api/review-tasks/{id}
GET /api/health
```

### 12.4 Validation Result

必须记录：

```bash
cd backend-java
mvn test
```

结果。

若无法执行，必须说明：

1. 未执行原因；
2. 当前环境缺少什么；
3. 是否有替代验证。

### 12.5 Example Requests and Responses

至少提供：

1. create task 示例；
2. list task 示例；
3. get detail 示例；
4. not found 示例；
5. validation failure 示例。

### 12.6 Scope Compliance

明确声明是否遵守以下限制：

```text
No database
No MyBatis-Plus
No MySQL
No JPA
No Entity
No Mapper
No ai-service call
No GitHub API call
No Semgrep
No LLM
No frontend business code
No ai-service business code
No unauthorized dependency
```

### 12.7 Known Issues

如有问题，列出。

如果没有，写：

```text
No known blocking issues.
```

---

## 13. Final Instruction

请严格基于 Round 02 已验收的 `backend-java` skeleton 开发。

本任务的核心原则是：

```text
实现 ReviewTask API mock，但不做数据库、不做 ai-service、不做 GitHub、不做 Semgrep、不做 LLM。
```

不要扩大范围。

不要进入 Round 04。

完成后输出 Cursor handoff。