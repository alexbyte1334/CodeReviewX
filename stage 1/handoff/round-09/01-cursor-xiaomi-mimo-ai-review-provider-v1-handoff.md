# Cursor Handoff: Xiaomi MiMo AI Review Provider v1

## 1. Summary

Round 09 implements a configurable Xiaomi MiMo AI review provider behind the existing `ReviewProvider` interface. Default local behavior remains mock mode (`codereviewx.review.provider=mock`). MiMo mode is enabled via configuration and `MIMO_API_KEY` environment variable only.

Key deliverables:

- `ConfigurableReviewProvider` for configuration-driven selection with safe mock fallback
- `XiaomiMiMoReviewProvider`, `XiaomiMiMoClient`, `ReviewPromptBuilder`, `XiaomiMiMoFindingParser`
- `IssueSource.MIMO` enum value
- Frontend `IssueSource` type and dynamic source label in summary panel
- Comprehensive backend unit/integration tests
- README updates for Round 09

Public API contract, persistence model, and `riskLevel == issueSummary.riskLevel` invariant are preserved.

## 2. Files Changed

### Backend (new)

- `backend-java/src/main/java/com/codereviewx/backend/review/config/ReviewProperties.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/config/ReviewPipelineConfig.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/ConfigurableReviewProvider.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoProperties.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoReviewProvider.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoClient.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoClientRequest.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoClientResponse.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/ReviewPromptBuilder.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoFindingParser.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoClientException.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoParseException.java`

### Backend (modified)

- `backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSource.java` — added `MIMO`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/ReviewPipelineService.java` — comment update
- `backend-java/src/main/resources/application.yml` — provider + MiMo config
- `backend-java/src/test/resources/application.yml` — test defaults

### Backend (tests)

- `backend-java/src/test/java/com/codereviewx/backend/review/pipeline/provider/ConfigurableReviewProviderTest.java`
- `backend-java/src/test/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoReviewProviderTest.java`
- `backend-java/src/test/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoFindingParserTest.java`
- `backend-java/src/test/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoClientTest.java`
- `backend-java/src/test/java/com/codereviewx/backend/review/pipeline/ReviewPipelineFallbackIntegrationTest.java`

### Frontend (modified)

- `frontend/src/types/reviewTask.ts` — added `MIMO` to `IssueSource`
- `frontend/src/components/ReviewTaskDetail.tsx` — dynamic source label in summary panel
- `frontend/src/test/ReviewTaskDetail.test.tsx` — MIMO badge test

### Documentation

- `README.md`
- `backend-java/README.md`

## 3. Agent Structure and Flow

```text
Input -> Context -> Pipeline -> Provider Selection -> Provider -> Prompt -> Model Client -> Parser -> Finding -> Persistence -> API DTO -> Frontend
```

| Layer | Component |
|---|---|
| Input | `repoUrl` + `prNumber` via `CreateReviewTaskRequest` |
| Context | `ReviewContext` |
| Orchestrator | `ReviewPipelineService` |
| Provider Selection | `ConfigurableReviewProvider` (reads `ReviewProperties`) |
| Provider | `MockReviewProvider` or `XiaomiMiMoReviewProvider` |
| Prompt | `ReviewPromptBuilder` |
| Model Client | `XiaomiMiMoClient` → Xiaomi MiMo `/chat/completions` |
| Parser | `XiaomiMiMoFindingParser` |
| Normalization | `ReviewFinding` |
| Persistence | `ReviewTaskEntity`, `ReviewIssueEntity` |
| Presentation | `ReviewTaskResponse` + frontend issue cards |

**Current capability:** configurable mock or MiMo review with limited context (`repoUrl`, `prNumber` only).

**Planned next:** PR/diff context enrichment so MiMo receives actual code changes.

## 4. Provider Configuration

```properties
codereviewx.review.provider=mock
codereviewx.ai.mimo.base-url=https://api.xiaomimimo.com/v1
codereviewx.ai.mimo.model=mimo-v2.5-pro
```

Environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `CODEREVIEWX_REVIEW_PROVIDER` | `mock` | Provider mode: `mock` or `mimo` |
| `MIMO_API_KEY` | (empty) | MiMo API key — environment only |
| `MIMO_BASE_URL` | `https://api.xiaomimimo.com/v1` | MiMo base URL |
| `MIMO_MODEL` | `mimo-v2.5-pro` | MiMo model name |

## 5. Xiaomi MiMo Provider Implementation

`XiaomiMiMoReviewProvider` implements `ReviewProvider`:

1. Builds user prompt from `ReviewContext` via `ReviewPromptBuilder`
2. Calls `XiaomiMiMoClient.complete(systemPrompt, userPrompt)`
3. Parses assistant content via `XiaomiMiMoFindingParser`
4. Returns `ReviewProviderResult` with `source=MIMO` findings

`XiaomiMiMoClient` uses Spring `RestClient` with OpenAI-compatible `POST {baseUrl}/chat/completions` and `Authorization: Bearer` header. API key, headers, and raw bodies are never logged.

## 6. Prompt and Parser Behavior

**System prompt:** CodeReviewX agent role + strict JSON output instruction.

**User prompt:** includes `repoUrl`, `prNumber`, limitation notice (no PR diff yet), JSON schema with backend enum values (`BUG|SECURITY|PERFORMANCE|MAINTAINABILITY|STYLE|TEST`, `HIGH|MEDIUM|LOW`), and rules to return only JSON.

**Parser behavior:**

- Accepts strict JSON array; empty `[]` → zero findings (successful, no fallback)
- Rejects malformed JSON, invalid severity/category
- Generates `MIMO-ISSUE-N` when `issueKey` missing
- Defaults blank text fields and `filePath=unknown`, lines default to 1
- Sets `source=MIMO`, `status=OPEN`

## 7. Fallback and Failure Semantics

| Condition | Behavior |
|---|---|
| `provider=mock` | Always `MockReviewProvider`; 3 MOCK issues |
| `provider=mimo` + valid key + success | `XiaomiMiMoReviewProvider`; `source=MIMO` |
| `provider=mimo` + missing/blank key | Fallback to mock; safe warning log |
| MiMo HTTP failure | Fallback to mock; safe warning log |
| Parser failure | Fallback to mock; safe warning log |
| Valid empty `[]` from MiMo | Success; zero issues; `riskLevel=NONE` |

Public API never exposes fallback reason, stack trace, raw prompt, or model output.

## 8. API Contract Verification

Endpoints unchanged:

- `GET /api/health`
- `POST /api/review-tasks`
- `GET /api/review-tasks`
- `GET /api/review-tasks/{id}`

Request/response wrapper and all DTO fields preserved. Existing controller and service tests pass without modification.

## 9. Persistence Verification

- `ReviewTaskEntity` and `ReviewIssueEntity` persistence unchanged
- MiMo and fallback mock findings persist as `ReviewIssueEntity`
- `issueSummary` computed from persisted issues at response time
- No new tables or columns added
- `IssueSource.MIMO` stored as string enum (H2/JPA safe)

## 10. Frontend Impact

Minimal changes only:

- `IssueSource` union includes `MIMO`
- Summary panel shows dynamic issue source label (`MOCK`, `MIMO`, `MIXED`, or `N/A`)
- Issue cards already render `{issue.source}` badge dynamically
- Added test for MIMO source badges

## 11. Tests Added or Updated

| Test class | Coverage |
|---|---|
| `ConfigurableReviewProviderTest` | mock default, explicit mock, mimo with key, missing key fallback, client/parser failure fallback, unknown provider |
| `XiaomiMiMoReviewProviderTest` | prompt+client+parser flow, empty array, deterministic keys |
| `XiaomiMiMoFindingParserTest` | valid JSON, empty array, malformed JSON, invalid enums, missing keys, blank fields |
| `XiaomiMiMoClientTest` | success, missing key, non-2xx |
| `ReviewPipelineFallbackIntegrationTest` | Spring Boot integration for mimo-without-key fallback |

Existing `MockReviewProviderTest`, `ReviewTaskServiceTest`, `ReviewTaskControllerTest` pass unchanged.

## 12. Validation Commands and Results

```text
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
Result: PASS

cd frontend
npm run typecheck
Result: PASS

cd frontend
npm run build
Result: PASS

cd frontend
npm test -- --run
Result: PASS (27 tests)
```

## 13. Runtime Verification

### 13.1 Mock mode — PASS

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/round-09-mock-mode","prNumber":9}'
```

Verified:

- `success=true`
- `issues.length=3`
- all `source=MOCK`
- `issueSummary.totalIssues=3`
- `riskLevel=HIGH`
- `riskLevel == issueSummary.riskLevel`

### 13.2 MiMo mode with real API key — PENDING USER KEY

Requires:

```bash
export MIMO_API_KEY="<local-secret-not-committed>"
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run \
  -Dspring-boot.run.arguments="--codereviewx.review.provider=mimo"
```

Cursor did not run live MiMo API verification because the API key must be provided via environment at runtime and must not be written to code, logs, or handoff.

### 13.3 Missing key fallback — PASS

Started with `--codereviewx.review.provider=mimo` and no `MIMO_API_KEY`.

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/round-09-mimo-fallback","prNumber":9}'
```

Verified:

- task creation succeeds
- fallback to 3 MOCK issues
- `riskLevel == issueSummary.riskLevel`
- no stack trace or secret details in API response

### 13.4 Restart persistence — not re-verified this session (prior rounds verified; no persistence changes)

### 13.5 Browser smoke — not run this session

## 14. Secret Handling Verification

- No API key was committed.
- No API key was written to README.
- No API key was written to application config (only `${MIMO_API_KEY:}` placeholder).
- No API key was written to tests or fixtures.
- No API key is returned through API response.
- No API key is logged (`XiaomiMiMoProperties.toString()` masks key as `***`).
- `MIMO_API_KEY` is read only from environment/config binding.

## 15. Known Limitations

1. No PR diff or GitHub context — MiMo prompt receives `repoUrl` and `prNumber` only.
2. Live MiMo runtime verification pending user-provided `MIMO_API_KEY`.
3. Task summary text still says "Mock review completed" regardless of provider (unchanged from Round 08).
4. No provider name or execution trace exposed in API (by design).

## 16. Recommendation for Codex Validation

1. Run full backend test suite (`mvn test`).
2. Verify mock mode default startup requires no `MIMO_API_KEY`.
3. With user-provided `MIMO_API_KEY`, verify mimo mode returns `source=MIMO` or valid zero findings.
4. Verify missing-key and simulated API failure fallback paths.
5. Confirm no secrets in git diff, logs, or API responses.
6. Run frontend typecheck/build/tests.
7. Optional browser smoke with both MOCK and MIMO tasks.
