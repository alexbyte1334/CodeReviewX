# Codex Handoff: Xiaomi MiMo AI Review Provider v1 Validation

## 1. Verdict

`ROUND_09_CODEX_PATCHED_READY_FOR_QODER`

Codex found and patched one Round 09 product-semantics defect: newly created tasks still used a provider-specific "Mock review completed" summary even when the configured path could be MiMo. After the patch, new tasks use generic review-completed wording and zero-finding MiMo results get no-findings wording. Backend tests, frontend checks, mock runtime, missing-key fallback, restart persistence, and browser smoke all passed after the patch.

## 2. Summary of Validation

- Validated Cursor's MiMo provider implementation from code because the upstream handoff was supplied at `handoff/round-09/01-cursor-xiaomi-mimo-ai-review-provider-v1-handoff.md`.
- Confirmed provider selection is isolated behind `ConfigurableReviewProvider`; `ReviewTaskService` only invokes `ReviewPipelineService`.
- Confirmed public API DTO shape is unchanged and does not expose provider internals, raw prompts, raw model output, API keys, headers, stack traces, or fallback reasons.
- Confirmed default mock mode and `provider=mimo` without `MIMO_API_KEY` both create successful tasks with 3 persisted `MOCK` / `OPEN` issues.
- Confirmed restart persistence for the Codex-created mock task `id=257` and missing-key fallback task `id=289`.
- Confirmed browser smoke at `http://127.0.0.1:5173/`: Backend UP, create task, list update, detail render, summary panel, MOCK/OPEN badges, and no browser console warn/error entries.

## 3. Files Inspected

- `tasks/round-09/00-round-09-start.md`
- `tasks/round-09/01-cursor-xiaomi-mimo-ai-review-provider-v1.md`
- `tasks/round-09/02-codex-xiaomi-mimo-ai-review-provider-v1-validation.md`
- `handoff/round-09/01-cursor-xiaomi-mimo-ai-review-provider-v1-handoff.md`
- `backend-java/src/main/resources/application.yml`
- `backend-java/src/main/java/com/codereviewx/backend/review/config/ReviewProperties.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/config/ReviewPipelineConfig.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/ConfigurableReviewProvider.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/MockReviewProvider.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/ReviewPromptBuilder.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoClient.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoFindingParser.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoProperties.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoReviewProvider.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewTaskResponse.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewIssueResponse.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/dto/IssueSummaryResponse.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/persistence/entity/ReviewTaskEntity.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/persistence/entity/ReviewIssueEntity.java`
- `backend-java/src/test/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoFindingParserTest.java`
- `backend-java/src/test/java/com/codereviewx/backend/review/pipeline/provider/ConfigurableReviewProviderTest.java`
- `backend-java/src/test/java/com/codereviewx/backend/review/service/ReviewTaskServiceTest.java`
- `backend-java/src/test/java/com/codereviewx/backend/review/controller/ReviewTaskControllerTest.java`
- `frontend/src/types/reviewTask.ts`
- `frontend/src/components/ReviewTaskDetail.tsx`
- `frontend/src/utils/reviewSummary.ts`
- `frontend/src/test/ReviewTaskDetail.test.tsx`
- `README.md`
- `backend-java/README.md`

## 4. Files Changed by Codex

- `backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java`
  - Moved task success summary assignment until after provider execution.
  - Replaced provider-specific mock summary with generic review-completed wording.
  - Added zero-finding wording for successful empty provider results.
- `backend-java/src/test/java/com/codereviewx/backend/review/service/ReviewTaskServiceTest.java`
  - Updated summary assertion to require the generic wording and reject mock-specific wording for new tasks.
- `backend-java/src/test/java/com/codereviewx/backend/review/controller/ReviewTaskControllerTest.java`
  - Updated API summary assertion to require the generic wording and reject mock-specific wording.
- `backend-java/README.md`
  - Updated the API example summary to match the patched runtime behavior.
- `tasks/round-09/02-codex-xiaomi-mimo-ai-review-provider-v1-validation-handoff.md`
  - This Codex validation handoff.

## 5. Agent Structure and Flow

Validated chain:

```text
Input -> Context -> Pipeline -> Provider Selection -> Provider -> Prompt -> Model Client -> Parser -> Finding -> Persistence -> API DTO -> Frontend
```

Mapping:

```text
Input: repoUrl + prNumber
Context: ReviewContext
Pipeline: ReviewPipelineService
Provider Selection: ConfigurableReviewProvider
Provider: MockReviewProvider or XiaomiMiMoReviewProvider
Prompt: ReviewPromptBuilder
Model Client: XiaomiMiMoClient
Parser: XiaomiMiMoFindingParser
Finding: ReviewFinding
Persistence: ReviewTaskEntity + ReviewIssueEntity
API DTO: ReviewTaskResponse + ReviewIssueResponse + IssueSummaryResponse
Frontend: existing task list/detail and issue cards
```

## 6. Provider Selection Verification

- `XiaomiMiMoReviewProvider` implements `ReviewProvider`.
- `MockReviewProvider` remains available and deterministic.
- `ConfigurableReviewProvider` is the provider-selection boundary.
- `ReviewProperties` defaults to mock behavior because only exact `mimo` enables MiMo.
- `ReviewTaskService` invokes `ReviewPipelineService.run(context)` and contains no MiMo-specific logic.
- Controller and DTO mapper layers contain no MiMo-specific logic.
- Unknown provider values fall back to mock behavior by design through `isMockMode()`.

## 7. Xiaomi MiMo Client Verification

- Client endpoint is `normalizeBaseUrl(baseUrl) + "/chat/completions"`.
- Request shape is OpenAI-compatible: `model`, `messages`, `temperature`.
- Auth uses `Authorization: Bearer <api key>` inside the HTTP adapter only.
- Missing key throws an internal `XiaomiMiMoClientException`; in configured MiMo mode this is avoided by `ConfigurableReviewProvider` and falls back to mock.
- Non-2xx and `RestClientException` failures become safe client exceptions.
- Raw request bodies, raw responses, headers, and key values are not logged by the client.
- Timeout configuration is not explicit; this remains acceptable for the current prototype and should be revisited with live provider hardening.

## 8. Prompt and Parser Verification

Prompt includes:

- agent role;
- review objective;
- `repoUrl`;
- `prNumber`;
- explicit no-PR-diff-context limitation;
- strict JSON array schema;
- no markdown fences instruction;
- allowed backend enum values: `HIGH|MEDIUM|LOW` and `BUG|SECURITY|PERFORMANCE|MAINTAINABILITY|STYLE|TEST`;
- empty array allowed.

Parser behavior verified from code and tests:

- accepts strict JSON arrays;
- accepts `[]` as successful zero findings;
- rejects malformed JSON and non-array output;
- rejects invalid severity/category;
- generates deterministic `MIMO-ISSUE-N` for missing issue keys;
- defaults blank file path to `unknown`;
- defaults invalid/missing lines to `1`;
- defaults blank title/description/recommendation;
- sets `source=MIMO` and `status=OPEN`;
- rejects invalid model output before persistence, so malformed partial records are not persisted.

## 9. Fallback and Failure Verification

Unit/integration tests cover:

- default mock provider path;
- explicit mock provider path;
- MiMo mode with key attempts MiMo provider;
- MiMo mode without key falls back to mock;
- MiMo client failure falls back to mock;
- MiMo parser failure falls back to mock;
- valid empty MiMo output remains successful zero findings, not fallback.

Runtime missing-key fallback:

```bash
env -u MIMO_API_KEY JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run -Dspring-boot.run.arguments=--codereviewx.review.provider=mimo
curl -s -X POST http://localhost:8080/api/review-tasks \
  -H 'Content-Type: application/json' \
  -d '{"repoUrl":"https://github.com/example/round-09-codex-mimo-fallback","prNumber":9}'
```

Observed task `id=289`:

- `success=true`;
- `status=SUCCESS`;
- `issues.length=3`;
- all issue `source=MOCK`;
- all issue `status=OPEN`;
- `issueSummary.totalIssues=3`;
- `riskLevel=HIGH`;
- `riskLevel == issueSummary.riskLevel`;
- no stack trace, secret, prompt, model output, provider message, or fallback reason in API response.

Server log contained only the safe warning:

```text
Review provider mode is mimo but MIMO_API_KEY is missing; falling back to mock provider
```

## 10. API Contract Verification

Endpoints unchanged:

```text
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

Create request unchanged:

```json
{"repoUrl":"https://github.com/example/round-09-codex-mock","prNumber":9}
```

Response wrapper unchanged:

```json
{"success":true,"message":"OK","data":{}}
```

Runtime mock create task `id=257` returned:

- `id`;
- `repoUrl`;
- `prNumber`;
- `status`;
- `riskLevel`;
- `summary`;
- `errorMessage`;
- `issues`;
- `issueSummary`;
- `createdAt`;
- `updatedAt`.

Issue response fields remained:

- `id`;
- `severity`;
- `category`;
- `source`;
- `status`;
- `filePath`;
- `startLine`;
- `endLine`;
- `title`;
- `description`;
- `recommendation`.

No response exposed:

- `providerName`;
- provider success flag;
- provider message;
- raw prompt;
- raw model output;
- API key;
- headers;
- stack trace;
- internal DB issue id;
- fallback reason.

## 11. Persistence and Restart Verification

- No new entity tables or columns were introduced by Cursor or Codex.
- `ReviewTaskEntity` remains the task table.
- `ReviewIssueEntity` remains the issue table.
- `IssueSource.MIMO` is safe as a string enum value.
- `ReviewIssueResponse.id` still maps from public `issueKey`, not internal DB id.
- `issueSummary` is computed from persisted issue rows at response assembly time.
- `ReviewTaskResponse.riskLevel` is derived from `issueSummary.riskLevel`.

Restart test:

1. Started backend in mock mode.
2. Created mock task `id=257`.
3. Restarted backend in MiMo mode without key.
4. Created fallback task `id=289`.
5. Stopped backend.
6. Restarted backend.
7. Fetched both `GET /api/review-tasks/257` and `GET /api/review-tasks/289`.

Both tasks were readable after restart with persisted issues, `source=MOCK`, `status=OPEN`, `issueSummary.totalIssues=3`, `riskLevel=HIGH`, and `riskLevel == issueSummary.riskLevel`.

## 12. Frontend Verification

Static checks:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

Results:

- typecheck: PASS;
- build: PASS;
- tests: PASS, 4 files / 27 tests.

Code verification:

- `IssueSource` includes `MIMO`.
- Issue cards render source dynamically.
- Summary panel source label resolves `MOCK`, `MIMO`, `MIXED`, and `N/A`.
- No frontend redesign, new UI library, route overhaul, or state-management migration was introduced.

Browser smoke:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Browser target: `http://127.0.0.1:5173/`

Observed:

- page title `CodeReviewX`;
- meaningful app content present;
- Backend UP shown;
- no framework overlay;
- browser console warn/error list empty;
- created task through UI with repo `https://github.com/example/round-09-codex-browser`;
- new task `id=321` appeared at top of list;
- detail view rendered summary panel and issue cards;
- source/status badges rendered `MOCK` and `OPEN`.

## 13. Summary Text / Product Semantics Check

Cursor's handoff called out that task summary text still said "Mock review completed" regardless of provider. Codex confirmed this in `ReviewTaskService` and patched it.

New behavior:

- provider result with findings: `Review completed for PR #<n> with generated findings.`
- successful zero-finding provider result: `Review completed for PR #<n> with no findings from the available context.`

This avoids overclaiming real PR diff review and avoids exposing fallback reason or provider internals. Existing persisted local H2 records created before this patch may still contain their old summary strings; no data migration was added because Round 09 scope does not include schema/data migration.

## 14. Secret Handling Verification

Checks performed:

- `git status --short` was attempted but the workspace is not recognized as a git repository in this environment, so git-diff-based verification is unavailable.
- Repository file search was run across `README.md`, `backend-java`, `frontend`, `tasks/round-09`, and `handoff/round-09` excluding generated dependency/build directories.
- Application config uses `${MIMO_API_KEY:}` only.
- Backend test config leaves `api-key` blank.
- README examples use only `<local-secret-not-committed>`.
- `XiaomiMiMoProperties.toString()` masks the key as `***`.
- Runtime API responses for mock and fallback tasks did not include API key, headers, prompt, raw model output, provider message, fallback reason, or stack traces.
- MiMo missing-key log did not print any key or header value.

No real Xiaomi MiMo API key was found or printed during Codex validation.

## 15. Tests and Runtime Commands

Backend tests:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Result:

```text
Tests run: 68, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Frontend checks:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

Results:

```text
typecheck: PASS
build: PASS
Test Files 4 passed (4)
Tests 27 passed (27)
```

Mock runtime:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
curl -s http://localhost:8080/api/health
curl -s -X POST http://localhost:8080/api/review-tasks \
  -H 'Content-Type: application/json' \
  -d '{"repoUrl":"https://github.com/example/round-09-codex-mock","prNumber":9}'
curl -s http://localhost:8080/api/review-tasks/257
curl -s http://localhost:8080/api/review-tasks
```

Runtime evidence:

- health: `{"success":true,"message":"OK","data":{"status":"UP","service":"backend-java"}}`
- created mock task: `id=257`, 3 `MOCK` issues, all `OPEN`, `riskLevel=HIGH`, `issueSummary.riskLevel=HIGH`.

MiMo missing-key fallback:

```bash
env -u MIMO_API_KEY JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run -Dspring-boot.run.arguments=--codereviewx.review.provider=mimo
curl -s -X POST http://localhost:8080/api/review-tasks \
  -H 'Content-Type: application/json' \
  -d '{"repoUrl":"https://github.com/example/round-09-codex-mimo-fallback","prNumber":9}'
```

Runtime evidence:

- created fallback task: `id=289`, 3 `MOCK` issues, all `OPEN`, `riskLevel=HIGH`, `issueSummary.riskLevel=HIGH`.

Live MiMo:

- Not run. A non-printing `test -n "$MIMO_API_KEY"` check returned no local key in this shell environment.

## 16. Documentation Verification

Root README and backend README document:

- Round 09 introduces Xiaomi MiMo provider;
- default mode remains mock;
- MiMo mode is config-driven;
- `MIMO_API_KEY` is environment-only;
- key must never be committed;
- fallback behavior;
- public API remains unchanged;
- findings persist as `ReviewIssue` records;
- summary/risk computed from persisted issues;
- current limitation: no PR diff context yet;
- next direction: PR/diff context.

Codex patched `backend-java/README.md` to remove the stale "Mock review completed" response example.

No documentation claim was found that CodeReviewX fully reviews real GitHub pull requests or is production-ready PR automation. Root README has broad planned AI-service language, but Round 09 sections clearly state current limitations.

## 17. Remaining Risks

- Live Xiaomi MiMo provider success was not verified because no `MIMO_API_KEY` was present in the local shell environment.
- The MiMo client has no explicit timeout configuration yet; acceptable for this prototype, but should be hardened with live API validation.
- Existing local H2 rows created before Codex's summary patch still retain old persisted summary text. New tasks use the corrected summary.
- The frontend header/subtitle still says `ReviewTask Mock UI`; this predates Round 09 and was not changed because the task explicitly forbids redesign/route overhaul. Qoder may decide whether a tiny label update is warranted in a later UI copy pass.
- Git diff inspection was blocked because `/Users/liyi/projects/CodeReviewX` is not recognized as a git repository in this environment.

## 18. Recommendation for Qoder

Proceed to Qoder with `ROUND_09_CODEX_PATCHED_READY_FOR_QODER`.

Qoder should focus on:

- independent review of the summary-text patch;
- whether old persisted local rows need a non-code note or cleanup guidance;
- whether MiMo HTTP timeout configuration should be required before a live-key round;
- live MiMo verification once a local `MIMO_API_KEY` is supplied through the environment;
- confirming no provider internals leak through future UI copy or API additions.
