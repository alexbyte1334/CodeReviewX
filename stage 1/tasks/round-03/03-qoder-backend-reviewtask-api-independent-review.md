# tasks/round-03/03-qoder-backend-reviewtask-api-independent-review.md

# Qoder Task: backend-java ReviewTask API Mock Independent Review

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 03
- Task ID: `03-qoder-backend-reviewtask-api-independent-review`
- Agent: Qoder
- Target Module: `backend-java`
- Task Type: Independent Architecture Review / Code Review / Scope Audit
- Upstream Implementation Task:

```text id="ex3u92"
tasks/round-03/01-cursor-backend-reviewtask-api-mock.md
```

- Upstream Implementation Handoff:

```text id="qktxcr"
handoff/round-03/01-cursor-backend-reviewtask-api-mock-handoff.md
```

- Upstream Validation Task:

```text id="hq0kq4"
tasks/round-03/02-codex-backend-reviewtask-api-validation.md
```

- Upstream Validation Handoff:

```text id="sw0ydp"
handoff/round-03/02-codex-backend-reviewtask-api-validation-handoff.md
```

- Expected Review Output:

```text id="xbuye2"
handoff/round-03/03-qoder-backend-reviewtask-api-independent-review.md
```

---

## 2. Background

Round 03 目标是实现 `backend-java` 的 ReviewTask API mock v1。

本轮的核心原则是：

```text id="fopzxf"
实现 ReviewTask API mock，但不做数据库、不做 ai-service、不做 GitHub、不做 Semgrep、不做 LLM。
```

Cursor 已完成实现，并声明：

1. 新增 `POST /api/review-tasks`；
2. 新增 `GET /api/review-tasks`；
3. 新增 `GET /api/review-tasks/{id}`；
4. 使用内存级 mock 存储；
5. 使用 `ConcurrentHashMap` 和 id generator；
6. 实现 `ReviewTaskService`；
7. 实现 `ReviewTaskController`；
8. 实现 `ReviewTaskNotFoundException`；
9. 实现 `GlobalExceptionHandler`；
10. 新增 Controller / Service 测试；
11. `mvn test` 通过；
12. 未引入数据库或外部集成。

Codex 已完成独立验证，并声明：

1. 经过一个最小测试环境修复后，`mvn test` 通过；
2. Spring Boot 服务可启动；
3. `GET /api/health` 通过；
4. `POST /api/review-tasks` 通过；
5. `GET /api/review-tasks` 通过；
6. `GET /api/review-tasks/{id}` 通过；
7. not found 返回 HTTP 404 + `ApiResponse`；
8. validation failure 返回 HTTP 400 + `ApiResponse`；
9. 未发现数据库、JPA、MyBatis、MySQL、Mapper、Entity；
10. 未发现 `ai-service`、GitHub、Semgrep、LLM 调用；
11. 未发现 Redis / MQ / Scheduler / Security / Swagger / Lombok；
12. 未发现 frontend / ai-service 业务代码新增。

Qoder 本轮任务不是继续实现功能，也不是重复 Codex 的验证动作，而是进行独立架构审查、代码审查和范围审计。

---

## 3. Qoder Mission

请基于当前仓库状态，独立审查 Round 03 实现是否满足产品架构目标、API contract、mock 边界和代码质量要求。

Qoder 必须完成：

1. 审查 Round 03 task 文档；
2. 审查 Cursor handoff；
3. 审查 Codex handoff；
4. 审查 `backend-java` 源码；
5. 审查 `backend-java` 测试；
6. 审查 `backend-java/pom.xml`；
7. 审查 API contract 是否符合 Round 03 目标；
8. 审查 mock service 是否只使用 in-memory storage；
9. 审查是否存在提前数据库化或外部集成；
10. 审查错误处理是否合理；
11. 审查 validation 是否完整；
12. 审查测试质量和稳定性；
13. 如环境允许，重新执行 `mvn test`；
14. 输出独立 review handoff。

---

## 4. Review Scope

### 4.1 Must Review Documents

必须阅读：

```text id="ellkng"
tasks/round-03/01-cursor-backend-reviewtask-api-mock.md
tasks/round-03/02-codex-backend-reviewtask-api-validation.md
handoff/round-03/01-cursor-backend-reviewtask-api-mock-handoff.md
handoff/round-03/02-codex-backend-reviewtask-api-validation-handoff.md
```

如果存在 Round 03 start 文档，也应阅读：

```text id="cxv0uj"
tasks/round-03/00-round-03-start.md
```

---

### 4.2 Must Review Source Files

必须审查至少以下文件：

```text id="kleo8u"
backend-java/pom.xml
backend-java/README.md
backend-java/src/main/java/com/codereviewx/backend/CodeReviewXBackendApplication.java
backend-java/src/main/java/com/codereviewx/backend/common/ApiResponse.java
backend-java/src/main/java/com/codereviewx/backend/common/GlobalExceptionHandler.java
backend-java/src/main/java/com/codereviewx/backend/controller/HealthController.java
backend-java/src/main/java/com/codereviewx/backend/review/controller/ReviewTaskController.java
backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java
backend-java/src/main/java/com/codereviewx/backend/review/model/ReviewTask.java
backend-java/src/main/java/com/codereviewx/backend/review/exception/ReviewTaskNotFoundException.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/CreateReviewTaskRequest.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewTaskResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewIssueResponse.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/ReviewTaskStatus.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/RiskLevel.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueType.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSeverity.java
backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSource.java
```

---

### 4.3 Must Review Tests

必须审查：

```text id="1skmta"
backend-java/src/test/java/com/codereviewx/backend/CodeReviewXBackendApplicationTests.java
backend-java/src/test/java/com/codereviewx/backend/review/controller/ReviewTaskControllerTest.java
backend-java/src/test/java/com/codereviewx/backend/review/service/ReviewTaskServiceTest.java
backend-java/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker
```

特别注意 Codex 添加的测试环境修复文件：

```text id="tmupfa"
backend-java/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker
```

审查它是否：

1. 仅影响测试环境；
2. 不影响 production runtime；
3. 不新增依赖；
4. 不掩盖真实业务问题。

---

## 5. API Contract Review Requirements

Qoder 必须审查以下接口是否符合 Round 03 要求。

### 5.1 Health API

```http id="x586rr"
GET /api/health
```

应继续返回：

```json id="oe90id"
{
  "success": true,
  "message": "OK",
  "data": {
    "status": "UP",
    "service": "backend-java"
  }
}
```

Review focus:

1. Round 02 health API 是否未被破坏；
2. 是否仍使用 `ApiResponse`；
3. 是否没有被 Round 03 业务代码污染。

---

### 5.2 Create ReviewTask API

```http id="p9ao0f"
POST /api/review-tasks
```

Expected request:

```json id="6y1l3b"
{
  "repoUrl": "https://github.com/example/repo",
  "prNumber": 123
}
```

Expected behavior:

1. 使用 `@Valid`；
2. 调用 service；
3. 生成唯一 id；
4. 保存到内存；
5. 返回 `ApiResponse<ReviewTaskResponse>`；
6. 最终 mock 状态为 `SUCCESS`；
7. `summary` 明确包含 mock 语义；
8. `riskLevel` 为 `LOW`；
9. `errorMessage` 为 `null`；
10. `issues` 为空数组或符合 DTO contract；
11. 不执行真实代码审查；
12. 不启动异步任务；
13. 不调用外部系统。

---

### 5.3 List ReviewTask API

```http id="i6qpqj"
GET /api/review-tasks
```

Expected behavior:

1. 返回 `ApiResponse<List<ReviewTaskResponse>>`；
2. 无数据时返回空数组；
3. 有数据时返回已创建任务；
4. 不需要分页；
5. 不需要查询参数；
6. 不访问数据库；
7. 不调用外部系统。

---

### 5.4 Get ReviewTask Detail API

```http id="s55nm3"
GET /api/review-tasks/{id}
```

Expected behavior:

1. 返回 `ApiResponse<ReviewTaskResponse>`；
2. 按 id 返回已有任务；
3. 不存在时抛出或转换为 `ReviewTaskNotFoundException`；
4. not found 返回 HTTP 404；
5. not found body 使用 `ApiResponse`；
6. not found message 清晰；
7. 不访问数据库；
8. 不调用外部系统。

---

### 5.5 Validation Failure

必须审查：

1. blank `repoUrl` 是否会被拦截；
2. negative / zero `prNumber` 是否会被拦截；
3. validation response 是否是 HTTP 400；
4. response body 是否使用 `ApiResponse`；
5. 是否没有暴露 stack trace；
6. message 是否至少包含 validation failure 和字段名。

不要求 message 固定为英文，因为 Codex 已发现本地环境返回中文 Bean Validation 消息。

---

## 6. Mock Behavior Review Requirements

Qoder 必须审查 mock 行为是否合理。

### 6.1 In-memory Storage

应使用类似：

```java id="cz0maj"
private final Map<Long, ReviewTask> tasks = new ConcurrentHashMap<>();
private final AtomicLong idGenerator = new AtomicLong(1);
```

Review focus:

1. 是否确实是内存存储；
2. 是否线程安全；
3. 是否不会持久化到文件或数据库；
4. 是否不会创建 Repository 层伪装未来数据库；
5. 是否 service restart 后数据自然丢失；
6. 是否 README 明确说明数据只在内存中。

---

### 6.2 ID Generation

Review focus:

1. id 是否唯一；
2. id 是否正数；
3. id 是否从合理初始值开始；
4. 并发创建时是否不会重复；
5. 是否没有依赖数据库 sequence。

---

### 6.3 Status Flow

Round 03 推荐 mock status flow：

```text id="dftljy"
PENDING -> RUNNING -> SUCCESS
```

Review focus:

1. 实现是否至少最终返回 `SUCCESS`；
2. 是否没有引入异步线程；
3. 是否没有引入 Scheduler；
4. 是否没有引入 MQ；
5. 是否没有真实审查逻辑；
6. 中间状态如果不可见，是否有合理注释或实现语义。

---

### 6.4 Mock Result

Review focus:

1. `summary` 是否明确标记 mock；
2. `riskLevel` 是否为 `LOW`；
3. `issues` 是否不为 `null`；
4. `errorMessage` 是否为 `null`；
5. `createdAt` / `updatedAt` 是否存在；
6. 时间格式是否是 JSON 字符串，而不是数组；
7. DTO 字段是否与 contract 一致。

---

## 7. Architecture Review Requirements

### 7.1 Layering

Review whether responsibilities are reasonably separated:

```text id="7igt2a"
Controller: HTTP request / response mapping
Service: mock business behavior and in-memory storage
Model: internal ReviewTask representation
DTO: API request / response contract
Exception: domain-specific not found error
GlobalExceptionHandler: API error response normalization
```

Red flags:

1. Controller 直接操作 storage；
2. Service 返回 HTTP-specific classes unnecessarily；
3. DTO 混入 persistence annotations；
4. Model 使用 `@Entity`；
5. Exception handler 过度复杂；
6. API response shape 不统一；
7. 隐式外部系统调用。

---

### 7.2 Dependency Direction

Expected:

```text id="g5p329"
Controller -> Service -> Model
Controller/Service -> DTO mapping as needed
ExceptionHandler -> ApiResponse
```

Should not have:

```text id="4v9xmm"
Service -> Controller
Model -> Controller
DTO -> Service
DTO -> Persistence
```

---

### 7.3 Simplicity

Round 03 should remain small.

Qoder should flag if implementation introduces:

1. excessive abstractions;
2. unused interfaces;
3. repository layer;
4. fake persistence layer;
5. speculative ai-service client;
6. workflow engine;
7. event system;
8. background worker;
9. configuration complexity.

---

### 7.4 Future Extensibility

Qoder should evaluate whether the implementation can support later rounds without overfitting.

Acceptable future-facing design:

1. service boundary exists;
2. DTO separated from model;
3. exception handling centralized;
4. enums reused;
5. README documents mock limitation.

Unacceptable future-facing design:

1. premature database abstractions;
2. external clients before integration round;
3. hardcoded behavior scattered across controller;
4. response format inconsistent with future frontend needs.

---

## 8. Static Scope Audit Requirements

Qoder must independently verify no out-of-scope implementation exists.

### 8.1 Dependency Audit

Inspect:

```bash id="65u3y5"
cat backend-java/pom.xml
```

Forbidden dependencies include:

```text id="6a1n1i"
mybatis
mybatis-plus
mysql
postgresql
h2
spring-boot-starter-data-jpa
hibernate
lombok
springdoc
swagger
openapi
spring-boot-starter-security
redis
amqp
kafka
```

Expected dependencies remain limited to:

```text id="09mx18"
spring-boot-starter-web
spring-boot-starter-validation
spring-boot-starter-test
```

---

### 8.2 Source Scan

Run or manually inspect:

```bash id="dg6ron"
grep -R "Entity\|Mapper\|Repository\|JpaRepository\|CrudRepository\|MyBatis\|TableName\|TableId\|DataSource" backend-java/src/main/java backend-java/src/test/java backend-java/src/main/resources backend-java/pom.xml || true

grep -R "RestTemplate\|WebClient\|HttpClient\|OpenAI\|Claude\|Gemini\|DeepSeek\|GitHub\|Semgrep\|ai-service" backend-java/src/main/java backend-java/src/test/java backend-java/src/main/resources backend-java/pom.xml || true

grep -R "EnableScheduling\|Scheduled\|Async\|EnableAsync\|Rabbit\|Kafka\|Redis\|Security\|Swagger\|OpenAPI\|lombok" backend-java/src/main/java backend-java/src/test/java backend-java/src/main/resources backend-java/pom.xml || true
```

Notes:

1. `ObjectMapper` in tests is allowed;
2. sample `https://github.com/example/repo` test data is allowed;
3. comments saying "no @Entity" are allowed;
4. README mentions of forbidden scope are allowed;
5. source comments should be evaluated contextually.

---

### 8.3 Module Boundary Audit

Run:

```bash id="wj4elt"
find frontend ai-service -type f 2>/dev/null | sort
git status --short
```

Review focus:

1. no frontend business code created in Round 03;
2. no ai-service business code created in Round 03;
3. changes are limited to backend-java and handoff/task files;
4. no unrelated project files modified.

If current workspace is not a Git repository, note that limitation and inspect file tree manually.

---

## 9. Test Review Requirements

### 9.1 Maven Test

If environment allows, run:

```bash id="bjr799"
cd backend-java
mvn test
```

If Java 17 needs explicit `JAVA_HOME`, use it.

Record:

```text id="hbja37"
Java version
Maven version
Test command
Tests run
Failures
Errors
Skipped
Build result
```

If unable to run tests, do not guess. Document exact blocker.

---

### 9.2 Test Quality Review

Review whether tests cover:

1. context load;
2. create success;
3. list success;
4. detail success;
5. not found;
6. blank `repoUrl`;
7. invalid `prNumber`;
8. service id generation;
9. service not found behavior;
10. issues array not null;
11. mock result fields.

Review whether tests are:

1. order-independent;
2. stable across repeated runs;
3. not relying on fixed timestamps;
4. not requiring external services;
5. not requiring database;
6. not brittle to locale-specific validation messages.

---

### 9.3 Codex Mockito Fix Review

Review:

```text id="9hj77c"
backend-java/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker
```

Expected content:

```text id="pz9i4e"
mock-maker-subclass
```

Assess whether this is acceptable.

Expected conclusion if no hidden issue:

```text id="k1yz4q"
Acceptable as a test-only environment compatibility fix.
```

Potential concern to check:

1. Does it break any Mockito feature needed by tests?
2. Does it hide production code defects?
3. Is it committed only under `src/test/resources`?
4. Does it add no runtime dependency?

---

## 10. Error Handling Review Requirements

Qoder must review:

```text id="n5641t"
GlobalExceptionHandler
ReviewTaskNotFoundException
```

Check:

1. `ReviewTaskNotFoundException` maps to HTTP 404;
2. validation exceptions map to HTTP 400;
3. generic exceptions map to an appropriate HTTP 500 response;
4. all error responses use `ApiResponse`;
5. no stack trace leaks to response;
6. messages are useful but not over-engineered;
7. no complex error code system introduced prematurely.

---

## 11. README Review Requirements

Review `backend-java/README.md`.

It should state:

1. ReviewTask mock API is available;
2. data is in-memory only;
3. service restart loses mock data;
4. no database connection;
5. no `ai-service`;
6. no GitHub / Semgrep / LLM;
7. curl examples for health/create/list/detail;
8. current implementation status is accurate.

Flag if README claims real AI review, real persistence, or production-grade capabilities.

---

## 12. Findings Classification

Qoder must classify findings as:

```text id="brafny"
Blocking
Non-blocking
Notes
```

### 12.1 Blocking Finding

Use Blocking if any of the following is found:

1. project does not compile;
2. tests fail without acceptable explanation;
3. service cannot start;
4. required API missing;
5. response format not using `ApiResponse`;
6. create/list/detail broken;
7. validation missing;
8. not found broken;
9. database/JPA/MyBatis/MySQL introduced;
10. `ai-service` / GitHub / Semgrep / LLM called;
11. unauthorized dependencies added;
12. frontend or ai-service business code created;
13. large architecture drift.

### 12.2 Non-blocking Finding

Use Non-blocking for issues like:

1. validation message locale varies;
2. timestamp precision or timezone should be standardized later;
3. test isolation can be improved;
4. README wording can be clearer;
5. minor naming or code style improvement;
6. generic exception message can be refined later.

### 12.3 Notes

Use Notes for observations that do not require action.

Examples:

1. mock implementation intentionally uses in-memory storage;
2. Codex Mockito fix is test-only;
3. issues array currently empty;
4. risk level fixed to LOW for mock v1;
5. no pagination in Round 03 by design.

---

## 13. Required Qoder Handoff Format

Create:

```text id="kjzdqc"
handoff/round-03/03-qoder-backend-reviewtask-api-independent-review.md
```

Use the following structure.

---

### 13.1 Task Metadata

Include:

```text id="x2qlku"
Project
Round
Task ID
Agent
Review Date
Target Module
Documents Reviewed
Files Reviewed
```

---

### 13.2 Executive Summary

Include:

1. overall result;
2. whether implementation meets Round 03 goal;
3. whether any blocking issues exist;
4. whether Round 03 can proceed to final architect acceptance.

---

### 13.3 Review Method

Describe:

1. document review;
2. source review;
3. test review;
4. static scope audit;
5. runtime or Maven verification if performed.

---

### 13.4 API Contract Review

Provide a table:

| Area | Expected | Actual | Result |
|---|---|---|---|
| Health | `GET /api/health` works | ... | PASS/FAIL |
| Create task | `POST /api/review-tasks` works | ... | PASS/FAIL |
| List tasks | `GET /api/review-tasks` works | ... | PASS/FAIL |
| Detail | `GET /api/review-tasks/{id}` works | ... | PASS/FAIL |
| Not found | HTTP 404 + `ApiResponse` | ... | PASS/FAIL |
| Validation | HTTP 400 + `ApiResponse` | ... | PASS/FAIL |

---

### 13.5 Mock Behavior Review

Provide a table:

| Area | Expected | Actual | Result |
|---|---|---|---|
| Storage | in-memory only | ... | PASS/FAIL |
| ID generation | unique positive id | ... | PASS/FAIL |
| Status | mock SUCCESS / allowed flow | ... | PASS/FAIL |
| Summary | clearly mock | ... | PASS/FAIL |
| Risk level | LOW | ... | PASS/FAIL |
| Issues | array, not null | ... | PASS/FAIL |
| Time fields | present JSON strings | ... | PASS/FAIL |

---

### 13.6 Architecture Review

Cover:

1. controller responsibility;
2. service responsibility;
3. model/DTO separation;
4. exception handling;
5. dependency direction;
6. complexity level;
7. future extensibility.

---

### 13.7 Test Review

Cover:

1. tests present;
2. tests passed or not run with blocker;
3. coverage quality;
4. test stability;
5. Mockito test-only fix assessment.

---

### 13.8 Scope Audit

Provide a table:

| Constraint | Result | Evidence |
|---|---|---|
| No database | PASS/FAIL | ... |
| No MyBatis-Plus | PASS/FAIL | ... |
| No MySQL | PASS/FAIL | ... |
| No JPA / Hibernate | PASS/FAIL | ... |
| No Entity | PASS/FAIL | ... |
| No Mapper | PASS/FAIL | ... |
| No database Repository | PASS/FAIL | ... |
| No ai-service call | PASS/FAIL | ... |
| No GitHub API call | PASS/FAIL | ... |
| No Semgrep | PASS/FAIL | ... |
| No LLM | PASS/FAIL | ... |
| No Redis / MQ / Scheduler | PASS/FAIL | ... |
| No Spring Security | PASS/FAIL | ... |
| No Swagger / OpenAPI | PASS/FAIL | ... |
| No Lombok | PASS/FAIL | ... |
| No frontend business code | PASS/FAIL | ... |
| No ai-service business code | PASS/FAIL | ... |
| No unauthorized dependency | PASS/FAIL | ... |

---

### 13.9 Findings

Use sections:

```markdown id="en3zoo"
## Blocking Findings

## Non-blocking Findings

## Notes
```

Each finding should include:

```text id="9y3on2"
Title
Severity
Evidence
Impact
Recommendation
```

If none, explicitly say:

```text id="zp1tim"
No blocking findings.
```

---

### 13.10 Risk Assessment

Provide:

```text id="fyz9w1"
Overall Risk: LOW / MEDIUM / HIGH
```

Explain why.

---

### 13.11 Final Recommendation

Use exactly one:

```text id="xph0wq"
Recommend accepting Round 03.
Recommend accepting Round 03 with non-blocking notes.
Do not accept Round 03 until blocking issues are fixed.
```

Then state whether the project is ready for ChatGPT Architect final acceptance.

---

## 14. Important Constraints for Qoder

Do not implement new features.

Do not modify production code unless there is a severe issue that blocks review and the minimal safe fix is obvious.

Do not introduce dependencies.

Do not add database or external integrations.

Do not redesign architecture.

Do not enter Round 04.

This task is an independent review task, not an implementation task.

---

## 15. Final Instruction

Your final output must be:

```text id="hst96n"
handoff/round-03/03-qoder-backend-reviewtask-api-independent-review.md
```

The review should be evidence-based, explicit, and suitable for final architect acceptance.