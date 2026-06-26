# handoff/round-03/01-cursor-backend-reviewtask-api-mock-handoff.md

# Cursor Handoff: backend-java ReviewTask API Mock v1

## 1. Task Metadata

- Project: CodeReviewX
- Round: Round 03
- Task ID: `01-cursor-backend-reviewtask-api-mock`
- Agent: Cursor
- Handoff Date: 2026-06-23

---

## 12.1 Summary

Round 03 implementation is complete.

Cursor implemented the ReviewTask mock API on top of the Round 02 `backend-java` skeleton.

The implementation provides three new REST endpoints backed by an in-memory `ConcurrentHashMap` store:

- `POST /api/review-tasks` — creates a mock task, synchronously completes mock state flow (PENDING → RUNNING → SUCCESS), returns the finished task
- `GET /api/review-tasks` — returns all in-memory tasks sorted by id
- `GET /api/review-tasks/{id}` — returns a single task by id, or a 404-style `ApiResponse` error

A `GlobalExceptionHandler` handles `ReviewTaskNotFoundException`, Jakarta Bean Validation errors, and generic fallback errors — all returning `ApiResponse<T>`.

No database, no ai-service, no external integrations were introduced.

All 13 tests pass (`mvn test` → `BUILD SUCCESS`).

---

## 12.2 Files Changed

### Added

```text
backend-java/src/main/java/com/codereviewx/backend/review/model/ReviewTask.java
backend-java/src/main/java/com/codereviewx/backend/review/exception/ReviewTaskNotFoundException.java
backend-java/src/main/java/com/codereviewx/backend/common/GlobalExceptionHandler.java
backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java
backend-java/src/main/java/com/codereviewx/backend/review/controller/ReviewTaskController.java
backend-java/src/test/java/com/codereviewx/backend/review/service/ReviewTaskServiceTest.java
backend-java/src/test/java/com/codereviewx/backend/review/controller/ReviewTaskControllerTest.java
```

### Modified

```text
backend-java/README.md
```

---

## 12.3 API Implemented

```text
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
GET  /api/health   (unchanged, continues to work)
```

---

## 12.4 Validation Result

```bash
cd backend-java
mvn test
```

Result:

```
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: ~40s (first run, dependency download included)
```

Test classes executed:

| Test Class | Tests Run | Failures | Errors | Skipped |
|---|---|---|---|---|
| `CodeReviewXBackendApplicationTests` | 1 | 0 | 0 | 0 |
| `ReviewTaskControllerTest` | 6 | 0 | 0 | 0 |
| `ReviewTaskServiceTest` | 6 | 0 | 0 | 0 |

---

## 12.5 Example Requests and Responses

### Create Task

Request:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/repo","prNumber":123}'
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
    "createdAt": "2026-06-23T05:57:13",
    "updatedAt": "2026-06-23T05:57:13",
    "issues": []
  }
}
```

### List Tasks

Request:

```bash
curl http://localhost:8080/api/review-tasks
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
      "createdAt": "2026-06-23T05:57:13",
      "updatedAt": "2026-06-23T05:57:13",
      "issues": []
    }
  ]
}
```

### Get Task Detail

Request:

```bash
curl http://localhost:8080/api/review-tasks/1
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
    "createdAt": "2026-06-23T05:57:13",
    "updatedAt": "2026-06-23T05:57:13",
    "issues": []
  }
}
```

### Not Found

Request:

```bash
curl http://localhost:8080/api/review-tasks/99999
```

Response (HTTP 404):

```json
{
  "success": false,
  "message": "Review task not found",
  "data": null
}
```

### Validation Failure — blank repoUrl

Request:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"","prNumber":123}'
```

Response (HTTP 400):

```json
{
  "success": false,
  "message": "Validation failed: repoUrl: must not be blank",
  "data": null
}
```

### Validation Failure — negative prNumber

Request:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/repo","prNumber":-1}'
```

Response (HTTP 400):

```json
{
  "success": false,
  "message": "Validation failed: prNumber: must be greater than 0",
  "data": null
}
```

---

## 12.6 Scope Compliance

| Constraint | Status |
|---|---|
| No database | ✅ Compliant |
| No MyBatis-Plus | ✅ Compliant |
| No MySQL | ✅ Compliant |
| No JPA | ✅ Compliant |
| No Entity | ✅ Compliant |
| No Mapper | ✅ Compliant |
| No ai-service call | ✅ Compliant |
| No GitHub API call | ✅ Compliant |
| No Semgrep | ✅ Compliant |
| No LLM | ✅ Compliant |
| No frontend business code | ✅ Compliant |
| No ai-service business code | ✅ Compliant |
| No unauthorized dependency | ✅ Compliant |

No new Maven dependencies were added. All implementation uses `spring-boot-starter-web`, `spring-boot-starter-validation`, and `spring-boot-starter-test` — all already present in `pom.xml` from Round 02.

---

## 12.7 Known Issues

No known blocking issues.

Notes for Codex validation:

- `ReviewTaskService` uses `@SpringBootTest` scoped bean — each `@SpringBootTest` test class shares a single application context instance, so tests that create tasks may see data from prior tests within the same context. `ReviewTaskControllerTest` is designed to be order-independent (list test just checks `notNullValue()`, not exact count).
- `LocalDateTime` serialization uses default Jackson ISO format (no timezone). This matches the API contract examples. If frontend expects a specific timezone format, that can be addressed in Round 04.
