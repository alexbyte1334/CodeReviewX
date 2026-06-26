# tasks/round-03/02-codex-backend-reviewtask-api-validation.md

# Codex Task: backend-java ReviewTask API Mock Validation

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 03
- Task ID: `02-codex-backend-reviewtask-api-validation`
- Agent: Codex
- Target Module: `backend-java`
- Task Type: Validation / Runtime Verification / Minimal Fix
- Upstream Task:

```text
tasks/round-03/01-cursor-backend-reviewtask-api-mock.md
```

- Upstream Handoff:

```text
handoff/round-03/01-cursor-backend-reviewtask-api-mock-handoff.md
```

- Expected Handoff Output:

```text
handoff/round-03/02-codex-backend-reviewtask-api-validation-handoff.md
```

---

## 2. Background

Round 03 的目标是实现 `backend-java` 的 ReviewTask API mock v1。

Cursor 已完成第一阶段实现，并在 handoff 中声明：

1. 新增 ReviewTask mock API；
2. 使用内存级 `ConcurrentHashMap` 存储；
3. 实现 ReviewTask service；
4. 实现 ReviewTask controller；
5. 实现最小异常处理；
6. 新增 controller 和 service 测试；
7. 执行 `mvn test` 成功；
8. 未引入数据库；
9. 未调用 `ai-service`；
10. 未调用 GitHub / Semgrep / LLM；
11. 未新增 Maven 依赖。

Codex 本轮任务不是重新设计或重构，而是独立验证 Cursor 实现是否真实满足 Round 03 要求。

---

## 3. Codex Mission

请基于当前仓库状态，独立完成以下验证：

1. 验证 Maven 测试是否通过；
2. 验证 Spring Boot 服务是否可启动；
3. 验证 `GET /api/health` 是否仍可用；
4. 验证 `POST /api/review-tasks` 是否可用；
5. 验证 `GET /api/review-tasks` 是否可用；
6. 验证 `GET /api/review-tasks/{id}` 是否可用；
7. 验证 not found error 是否符合统一响应结构；
8. 验证 validation failure 是否符合统一响应结构；
9. 检查是否存在数据库、JPA、MyBatis、MySQL、Mapper、Entity 等越界实现；
10. 检查是否存在 `ai-service`、GitHub、Semgrep、LLM 调用；
11. 检查是否存在未授权依赖；
12. 如发现小问题，只允许做最小修复；
13. 输出 Codex handoff。

---

## 4. Validation Scope

### 4.1 Must Validate APIs

必须验证：

```http
GET /api/health
POST /api/review-tasks
GET /api/review-tasks
GET /api/review-tasks/{id}
```

### 4.2 Must Validate Error Cases

必须验证：

```http
GET /api/review-tasks/{non_existing_id}
POST /api/review-tasks with blank repoUrl
POST /api/review-tasks with negative prNumber
```

### 4.3 Must Validate Scope Control

必须确认没有：

```text
MyBatis-Plus
MySQL Driver
JPA
Hibernate
@Entity
Mapper
Repository pointing to database
Database schema
Database migration
ai-service call
GitHub API call
Semgrep execution
LLM call
Redis
MQ
Scheduler
Spring Security
Swagger / OpenAPI
Lombok
frontend business code
ai-service business code
unauthorized dependency
```

---

## 5. Expected API Contract

### 5.1 Health Check

Request:

```bash
curl http://localhost:8080/api/health
```

Expected response shape:

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

---

### 5.2 Create Review Task

Request:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/repo","prNumber":123}'
```

Expected response shape:

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
    "createdAt": "2026-06-23T05:57:13",
    "updatedAt": "2026-06-23T05:57:13",
    "issues": []
  }
}
```

Validation requirements:

1. `success` must be `true`;
2. `message` should be `OK`;
3. `data.id` must exist;
4. `data.repoUrl` must equal request value;
5. `data.prNumber` must equal request value;
6. `data.status` should be `SUCCESS`;
7. `data.summary` must clearly indicate mock behavior;
8. `data.riskLevel` should be `LOW`;
9. `data.errorMessage` should be `null`;
10. `data.issues` must be an array, not `null`;
11. `createdAt` and `updatedAt` must be present.

Do not require a fixed timestamp value.

---

### 5.3 List Review Tasks

Request:

```bash
curl http://localhost:8080/api/review-tasks
```

Expected response shape:

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
      "createdAt": "2026-06-23T05:57:13",
      "updatedAt": "2026-06-23T05:57:13",
      "issues": []
    }
  ]
}
```

Validation requirements:

1. `success` must be `true`;
2. `data` must be an array;
3. after a successful create request, list should include the created task;
4. do not require exact list size if previous test data exists in the same process;
5. if implementation sorts by id, verify there is no obvious ordering bug, but sorting is not a blocker unless it breaks correctness.

---

### 5.4 Get Review Task Detail

Request:

```bash
curl http://localhost:8080/api/review-tasks/1
```

Expected response shape:

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
    "createdAt": "2026-06-23T05:57:13",
    "updatedAt": "2026-06-23T05:57:13",
    "issues": []
  }
}
```

Validation requirements:

1. use the actual id returned by create response;
2. `success` must be `true`;
3. returned task id must match requested id;
4. returned task content must match created task;
5. `issues` must be an array.

---

### 5.5 Not Found

Request:

```bash
curl http://localhost:8080/api/review-tasks/99999
```

Expected HTTP status:

```text
404
```

Expected response shape:

```json
{
  "success": false,
  "message": "Review task not found",
  "data": null
}
```

Validation requirements:

1. HTTP status should be `404`;
2. `success` must be `false`;
3. `message` should clearly say review task not found;
4. `data` must be `null`;
5. response must use `ApiResponse` shape.

---

### 5.6 Validation Failure — Blank repoUrl

Request:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"","prNumber":123}'
```

Expected HTTP status:

```text
400
```

Expected response shape:

```json
{
  "success": false,
  "message": "Validation failed: repoUrl: must not be blank",
  "data": null
}
```

Validation requirements:

1. HTTP status should be `400`;
2. `success` must be `false`;
3. `message` should mention validation failure;
4. `message` should mention `repoUrl`;
5. `data` must be `null`;
6. exact wording does not need to be byte-for-byte identical.

---

### 5.7 Validation Failure — Negative prNumber

Request:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/repo","prNumber":-1}'
```

Expected HTTP status:

```text
400
```

Expected response shape:

```json
{
  "success": false,
  "message": "Validation failed: prNumber: must be greater than 0",
  "data": null
}
```

Validation requirements:

1. HTTP status should be `400`;
2. `success` must be `false`;
3. `message` should mention validation failure;
4. `message` should mention `prNumber`;
5. `data` must be `null`;
6. exact wording does not need to be byte-for-byte identical.

---

## 6. Required Commands

### 6.1 Inspect Repository

Start from repository root.

```bash
pwd
find . -maxdepth 3 -type f | sort
```

Then inspect relevant backend files:

```bash
find backend-java -type f | sort
```

---

### 6.2 Validate Maven / Java Environment

```bash
java -version
mvn -version
```

Expected:

```text
Java 17
Maven available
```

If Java or Maven is missing, configure the environment if possible. If not possible, document the exact blocker in handoff.

---

### 6.3 Run Tests

```bash
cd backend-java
mvn test
```

Expected:

```text
BUILD SUCCESS
```

Record:

1. total tests run;
2. failures;
3. errors;
4. skipped;
5. build result.

---

### 6.4 Start Runtime Service

From `backend-java`:

```bash
mvn spring-boot:run
```

If port `8080` is unavailable, either stop conflicting process or run with another port:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

If using another port, record it in handoff.

---

### 6.5 Runtime curl Validation

Use the selected port.

Recommended with `jq` if available:

```bash
curl -s http://localhost:8080/api/health | jq
```

Create task:

```bash
curl -s -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/repo","prNumber":123}' | jq
```

List tasks:

```bash
curl -s http://localhost:8080/api/review-tasks | jq
```

Get detail:

```bash
curl -s http://localhost:8080/api/review-tasks/1 | jq
```

Not found:

```bash
curl -i -s http://localhost:8080/api/review-tasks/99999
```

Blank repoUrl:

```bash
curl -i -s -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"","prNumber":123}'
```

Negative prNumber:

```bash
curl -i -s -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/repo","prNumber":-1}'
```

If `jq` is unavailable, use plain `curl` and inspect JSON manually.

Important:

- For `GET /api/review-tasks/{id}`, prefer using the actual id returned by create response.
- Do not hardcode `1` if the service has existing in-memory data from earlier calls.
- If needed, restart service before runtime validation to reset in-memory data.

---

## 7. Static Scope Inspection

Codex must inspect source files and `pom.xml`.

### 7.1 Dependency Check

Inspect:

```bash
cat backend-java/pom.xml
```

Verify no unauthorized dependencies were added.

Forbidden dependencies include but are not limited to:

```text
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

Note:

- If H2 is not present, good.
- Do not add H2 for tests.
- Do not add any dependency unless absolutely necessary for build repair, and document clearly if changed.

---

### 7.2 Source Boundary Check

Run or manually inspect:

```bash
grep -R "Entity\|Mapper\|Repository\|JpaRepository\|CrudRepository\|MyBatis\|TableName\|TableId" backend-java/src/main/java || true
grep -R "RestTemplate\|WebClient\|OpenAI\|Claude\|Gemini\|DeepSeek\|GitHub\|Semgrep\|ai-service" backend-java/src/main/java || true
grep -R "EnableScheduling\|Scheduled\|Async\|EnableAsync\|Rabbit\|Kafka\|Redis" backend-java/src/main/java || true
```

Expected:

1. no database entity or mapper implementation;
2. no database repository;
3. no external service call;
4. no GitHub API call;
5. no Semgrep execution;
6. no LLM integration;
7. no async scheduler or queue infrastructure.

Important:

- A class named `ReviewTaskService` is expected and allowed.
- A plain Java model named `ReviewTask` is expected and allowed.
- Search hits in comments or README should be evaluated carefully, not treated as automatic failure.

---

### 7.3 Module Boundary Check

Verify Cursor did not create or modify business code under:

```text
frontend/
ai-service/
```

Suggested commands:

```bash
git status --short
find frontend -type f 2>/dev/null | head || true
find ai-service -type f 2>/dev/null | head || true
```

If files exist from earlier rounds, do not assume they were modified. Check `git status`.

---

## 8. Minimal Fix Policy

Codex is allowed to make only minimal fixes.

Allowed fixes:

1. fix compilation errors;
2. fix failing tests;
3. fix incorrect endpoint mapping;
4. fix missing `@Valid`;
5. fix validation response shape;
6. fix not found response shape;
7. fix `issues` being `null`;
8. fix accidental test instability;
9. fix README typo if it blocks clarity.

Forbidden fixes:

1. introduce database;
2. introduce MyBatis / JPA / Hibernate;
3. introduce MySQL / H2;
4. introduce `ai-service` client;
5. introduce GitHub API integration;
6. introduce Semgrep integration;
7. introduce LLM call;
8. introduce async queue;
9. introduce Redis / MQ / Scheduler;
10. introduce Swagger / OpenAPI;
11. introduce Lombok;
12. redesign domain architecture;
13. change API contract without necessity;
14. create frontend business code;
15. create ai-service business code;
16. start Round 04 work.

If a problem requires broad redesign, do not implement it. Document it as a finding in handoff.

---

## 9. Specific Risk Checks from Cursor Handoff

Cursor noted two non-blocking risks. Codex must explicitly evaluate them.

### 9.1 Test Context / In-memory Data Sharing

Cursor noted that `@SpringBootTest` test classes may share one Spring application context, so in-memory tasks may persist across tests.

Codex must check:

1. Does `mvn test` pass repeatedly?
2. Are tests order-independent?
3. Does any test rely on exact list size after previous tests created tasks?
4. Is there flakiness caused by shared in-memory state?

Suggested command:

```bash
mvn test
mvn test
```

If both pass, no fix is required.

If tests are flaky, apply minimal fix only. Possible acceptable fixes:

```text
@DirtiesContext
clear/reset helper in test setup
service-level test isolation
```

Do not redesign service architecture just for test isolation.

---

### 9.2 LocalDateTime Serialization

Cursor noted that `LocalDateTime` uses default Jackson ISO format without timezone.

Codex must check:

1. timestamps serialize as readable ISO-like strings;
2. no array timestamp format like `[2026,6,23,...]`;
3. API contract examples remain compatible;
4. no frontend-specific timezone requirement is introduced in this round.

If timestamps are ISO-like strings, accept.

Do not change to `Instant` or `OffsetDateTime` in this task unless current output clearly breaks contract.

---

## 10. Handoff Requirements

After validation, create:

```text
handoff/round-03/02-codex-backend-reviewtask-api-validation-handoff.md
```

The handoff must include the following sections.

---

### 10.1 Task Metadata

Include:

```text
Project
Round
Task ID
Agent
Validation Date
Repository / Module
```

---

### 10.2 Summary

Briefly summarize:

1. whether validation passed;
2. whether any fixes were made;
3. whether APIs are usable;
4. whether scope boundaries were respected.

---

### 10.3 Environment

Record:

```bash
java -version
mvn -version
```

Include relevant output.

---

### 10.4 Files Inspected

List important files inspected, including at minimum:

```text
backend-java/pom.xml
backend-java/src/main/java/com/codereviewx/backend/review/controller/ReviewTaskController.java
backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java
backend-java/src/main/java/com/codereviewx/backend/review/model/ReviewTask.java
backend-java/src/main/java/com/codereviewx/backend/common/GlobalExceptionHandler.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/CreateReviewTaskRequest.java
backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewTaskResponse.java
backend-java/src/test/java/com/codereviewx/backend/review/controller/ReviewTaskControllerTest.java
backend-java/src/test/java/com/codereviewx/backend/review/service/ReviewTaskServiceTest.java
backend-java/README.md
```

---

### 10.5 Test Result

Record exact command:

```bash
cd backend-java
mvn test
```

Record result:

```text
Tests run:
Failures:
Errors:
Skipped:
BUILD SUCCESS / BUILD FAILURE
```

If tests were run twice for stability, record both results.

---

### 10.6 Runtime Validation

Record:

1. service start command;
2. port used;
3. curl command for health;
4. curl command for create task;
5. curl command for list tasks;
6. curl command for get detail;
7. curl command for not found;
8. curl command for blank repoUrl;
9. curl command for negative prNumber;
10. response summary for each.

Include representative JSON responses.

---

### 10.7 API Contract Verdict

Provide a table:

| API | Expected | Actual | Result |
|---|---|---|---|
| `GET /api/health` | works | ... | PASS/FAIL |
| `POST /api/review-tasks` | creates mock task | ... | PASS/FAIL |
| `GET /api/review-tasks` | lists tasks | ... | PASS/FAIL |
| `GET /api/review-tasks/{id}` | returns detail | ... | PASS/FAIL |
| not found | 404 + ApiResponse | ... | PASS/FAIL |
| validation failure | 400 + ApiResponse | ... | PASS/FAIL |

---

### 10.8 Scope Compliance Verdict

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

### 10.9 Fixes Made

If no fixes were made, write:

```text
No source code changes were made by Codex.
```

If fixes were made, list:

1. files changed;
2. reason;
3. exact behavior before;
4. exact behavior after;
5. why the fix is minimal and within scope.

---

### 10.10 Findings

Classify findings as:

```text
Blocking
Non-blocking
Notes
```

For each finding include:

1. title;
2. severity;
3. evidence;
4. recommendation.

---

### 10.11 Final Codex Verdict

Use exactly one of the following:

```text
VALIDATION PASSED
VALIDATION PASSED WITH NON-BLOCKING NOTES
VALIDATION FAILED
```

Then state whether the project is ready for Qoder independent review.

---

## 11. Acceptance Criteria for Codex Task

Codex task is complete only if:

- [ ] `mvn test` has been executed or a precise blocker is documented;
- [ ] runtime service has been started or a precise blocker is documented;
- [ ] `GET /api/health` has been verified;
- [ ] `POST /api/review-tasks` has been verified;
- [ ] `GET /api/review-tasks` has been verified;
- [ ] `GET /api/review-tasks/{id}` has been verified;
- [ ] not found behavior has been verified;
- [ ] validation failure behavior has been verified;
- [ ] dependency scope has been inspected;
- [ ] database / persistence absence has been inspected;
- [ ] external integration absence has been inspected;
- [ ] module boundary has been inspected;
- [ ] any fixes are minimal and documented;
- [ ] Codex handoff has been created.

---

## 12. Final Instruction

Do not implement new product features.

Do not redesign the architecture.

Do not introduce database or external integrations.

Do not move into Round 04.

This task is a validation task for Round 03 Cursor implementation.

The expected outcome is an evidence-backed validation handoff:

```text
handoff/round-03/02-codex-backend-reviewtask-api-validation-handoff.md
```