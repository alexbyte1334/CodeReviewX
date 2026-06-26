# backend-java

> Round 10 Status: PR / Diff Context v1. Optional pasted diff context for MiMo prompts; default mock mode and fallback behavior unchanged.

## Current State

This module contains the Spring Boot 3 + Java 17 + Maven backend service for CodeReviewX.

### Implemented

- Spring Boot application entry class: `CodeReviewXBackendApplication`
- Health check endpoint: `GET /api/health`
- ReviewTask API:
  - `POST /api/review-tasks` creates a review task
  - `GET /api/review-tasks` lists persisted review tasks
  - `GET /api/review-tasks/{id}` reads a persisted review task
- Internal review pipeline (`review/pipeline/`):
  - `ReviewPipelineService` — orchestrates provider invocation
  - `ReviewContext` — input context for a pipeline run
  - `ReviewFinding` — normalized internal finding model
  - `ReviewProvider` — provider interface
  - `ReviewProviderResult` — provider execution result wrapper
  - `ConfigurableReviewProvider` — configuration-driven provider selection with mock fallback
  - `MockReviewProvider` — deterministic mock provider (default)
  - `XiaomiMiMoReviewProvider` — Xiaomi MiMo AI provider (`review/pipeline/provider/mimo/`)
  - `ReviewPromptBuilder`, `XiaomiMiMoClient`, `XiaomiMiMoFindingParser`
- `ApiResponse<T>` response wrapper
- Spring Data JPA repositories:
  - `ReviewTaskRepository`
  - `ReviewIssueRepository`
- JPA entities:
  - `ReviewTaskEntity` (optional persisted `diffText`, not exposed in public API)
  - `ReviewIssueEntity`
- DTOs:
  - `CreateReviewTaskRequest` (optional `diffText`, max 20000 characters)
  - `ReviewTaskResponse`
  - `ReviewIssueResponse`
  - `IssueSummaryResponse`
- Issue sources: `MOCK`, `MIMO` (plus reserved `SEMGREP`, `LLM`, `MANUAL`)
- Backend-computed `issueSummary`
- `ReviewTaskResponse.riskLevel` derived from `issueSummary.riskLevel`

### Persistence

Local runtime uses file-based H2:

```text
jdbc:h2:file:./data/codereviewx
```

Tests use isolated in-memory H2:

```text
jdbc:h2:mem:testdb
```

`ReviewTask` and `ReviewIssue` records survive backend restarts in local runtime. `issueSummary` is not persisted as an independent entity or table; it is computed from persisted issue rows when responses are assembled.

## Review Provider Configuration

Round 09 introduces a configurable Xiaomi MiMo provider behind the `ReviewProvider` interface. **Default local behavior remains mock mode.**

```properties
codereviewx.review.provider=mock
codereviewx.ai.mimo.base-url=https://api.xiaomimimo.com/v1
codereviewx.ai.mimo.model=mimo-v2.5-pro
```

Environment variables (optional overrides):

| Variable | Purpose |
|---|---|
| `CODEREVIEWX_REVIEW_PROVIDER` | `mock` (default) or `mimo` |
| `MIMO_API_KEY` | Xiaomi MiMo API key (**never commit**) |
| `MIMO_BASE_URL` | MiMo API base URL |
| `MIMO_MODEL` | MiMo model name |
| `MIMO_TIMEOUT_SECONDS` | MiMo HTTP connect/read timeout (default `60`) |

### Default mock mode

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

Returns 3 deterministic mock issues with `source: MOCK`. No API key required.

### Xiaomi MiMo mode

```bash
export MIMO_API_KEY="<local-secret-not-committed>"

cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run \
  -Dspring-boot.run.arguments="--codereviewx.review.provider=mimo"
```

When MiMo succeeds, findings use `source: MIMO`. Valid empty `[]` from MiMo means zero findings with `riskLevel: NONE`.

### Fallback behavior

If `provider=mimo` but:

- `MIMO_API_KEY` is missing or blank
- MiMo HTTP call fails (timeout, network, non-2xx)
- Model output cannot be parsed safely

…the pipeline **falls back to `MockReviewProvider`** with a safe server-side warning log. Task creation still succeeds; the public API shape is unchanged; fallback reason is not exposed.

**Never commit `MIMO_API_KEY`.** Read it only from environment variables.

## Review Agent Flow (Round 10)

```text
ReviewTaskService.createTask
  -> normalize optional diffText
  -> persist ReviewTaskEntity (optional diffText)
  -> ReviewPipelineService.run(ReviewContext with optional diffText)
  -> ConfigurableReviewProvider (mock or mimo)
      -> MockReviewProvider
      OR
      -> XiaomiMiMoReviewProvider
          -> ReviewPromptBuilder (diff-aware when diffText present)
          -> XiaomiMiMoClient (OpenAI-compatible /chat/completions)
          -> XiaomiMiMoFindingParser
  -> map ReviewFinding -> ReviewIssueEntity and persist
  -> compute issueSummary and riskLevel
```

Round 10 adds optional pasted PR diff context:

- `POST /api/review-tasks` accepts optional `diffText`.
- Blank or whitespace-only `diffText` is treated as absent.
- Maximum `diffText` length is 20000 characters.
- MiMo prompt uses pasted diff as primary review context when provided.
- Mock mode remains default and ignores diff for deterministic demo findings.
- Public API responses do not expose raw `diffText`, prompts, or model output.
- Automatic GitHub PR fetching is not implemented.

Current limitations:

- No automatic GitHub PR fetching or GitHub App integration.
- No private repository access.
- No repository is cloned or parsed.
- No full repository analysis or production-grade review claim.
- No PR comment write-back.
- No visual diff viewer or syntax highlighting.
- No RAG, MCP, Function Calling, or memory system.
- No production auth or team model.
- No Semgrep process is executed.
- No status update, resolve, false-positive, or human review workflow exists.

Creating a task in mock mode returns `status: SUCCESS`, `riskLevel: HIGH`, and 3 deterministic mock issues. The public issue ids are string keys such as `ISSUE-1`, not internal database ids.

## Quick Start

### Prerequisites

- Java 17
- Maven 3.8+

### Run

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

### Test

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

## API Examples

### Health Check

```bash
curl http://localhost:8080/api/health
```

Expected response:

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

### Create Review Task

Without diff:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/repo","prNumber":123}'
```

With optional pasted diff:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/repo","prNumber":123,"diffText":"diff --git a/src/App.tsx b/src/App.tsx\n+const x = 1;\n"}'
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
    "summary": "Review completed for PR #123 with generated findings.",
    "riskLevel": "HIGH",
    "errorMessage": null,
    "createdAt": "2026-06-23T10:00:00",
    "updatedAt": "2026-06-23T10:00:00",
    "issues": [
      {
        "id": "ISSUE-1",
        "severity": "HIGH",
        "category": "SECURITY",
        "source": "MOCK",
        "status": "OPEN",
        "filePath": "src/main/java/com/codereviewx/backend/review/controller/ReviewTaskController.java",
        "startLine": 42,
        "endLine": 48,
        "title": "Potential missing authorization check",
        "description": "This demo issue indicates that a sensitive endpoint should explicitly check authorization before processing the request.",
        "recommendation": "Add an authorization guard before the business logic and cover the behavior with a controller test."
      }
    ],
    "issueSummary": {
      "totalIssues": 3,
      "highCount": 1,
      "mediumCount": 1,
      "lowCount": 1,
      "riskLevel": "HIGH"
    }
  }
}
```

### List Review Tasks

```bash
curl http://localhost:8080/api/review-tasks
```

### Get Review Task by ID

```bash
curl http://localhost:8080/api/review-tasks/1
```

### Not Found Example

```bash
curl http://localhost:8080/api/review-tasks/99999
```

Expected response:

```json
{
  "success": false,
  "message": "Review task not found",
  "data": null
}
```

## Technology Stack

| Technology | Version | Purpose |
|---|---:|---|
| Java | 17 | Runtime |
| Spring Boot | 3.2.5 | Web framework |
| Spring Validation | - | Bean validation |
| Spring Data JPA | - | Persistence repositories |
| H2 | - | Local file DB and test DB |
| JUnit 5 | - | Testing |
| Maven | 3.8+ | Build tool |

## Planned Technology

| Technology | Purpose |
|---|---|
| MySQL or PostgreSQL | Production database |
| Spring WebClient / RestClient | Xiaomi MiMo API calls (Round 09) |

## Module Boundaries

- Does: provide REST API, manage ReviewTask lifecycle, persist ReviewTask and ReviewIssue data, accept optional pasted diff context, run configurable mock or Xiaomi MiMo review provider.
- Does not: automatically fetch GitHub PRs, install a GitHub App, access private repositories, clone repositories, perform full repository analysis, execute Semgrep, or expose provider internals, raw diff, prompts, or model output through the public API.
- Does not: write PR comments, provide RAG/MCP/Function Calling/memory features, execute issue status workflows, authentication, team workflows, or human review workflows.
