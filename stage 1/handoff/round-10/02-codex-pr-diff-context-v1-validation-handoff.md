# Round 10 / Codex Handoff: PR Diff Context v1 Validation

## Executive Summary

Codex independently validated Round 10 PR / Diff Context v1 against the task document and Cursor handoff.

Verdict: `CODEX_ROUND_10_READY_FOR_QODER`

The implementation is materially correct: `diffText` is optional, blank/whitespace input is normalized away, the 20000 character limit is enforced, `ReviewContext` carries normalized diff text, the MiMo prompt builder has diff and no-diff variants, mock behavior remains deterministic, MiMo missing-key fallback still succeeds, public API responses do not expose raw diff/prompt/model output, and the frontend supports optional pasted unified diff input.

One minimal documentation fix was applied in `README.md`: the "Out of Scope" bullet was clarified so it no longer reads as if all LLM calls are unimplemented while Xiaomi MiMo via `backend-java` is already supported.

## Cursor Claim Verification Table

| Claim | Result | Evidence |
|---|---|---|
| `diffText` optional on `POST /api/review-tasks` | Verified | `CreateReviewTaskRequest.diffText` has only `@Size`; no required annotation. Runtime create without diff succeeded with id `355`. |
| Blank / whitespace diff normalized to absent | Verified | `ReviewTaskService.normalizeDiffText()` trims and returns null for empty; controller/service tests cover blank and whitespace. |
| Max diff length is 20000 | Verified | DTO constant `MAX_DIFF_TEXT_LENGTH = 20000`; runtime oversized request returned HTTP 400 with the limit message. |
| `ReviewContext` carries optional diffText | Verified | `ReviewContext` has constructor/getter/`hasDiffText()`, and service passes normalized diff into it. |
| Prompt builder has diff/no-diff variants | Verified | `ReviewPromptBuilder.buildUserPrompt()` branches on `context.hasDiffText()`. Unit tests cover both paths. |
| Mock provider unchanged, 3 deterministic issues | Verified | `MockReviewProvider` still returns exactly ISSUE-1/2/3; backend runtime and browser both showed 3 MOCK issues. |
| MiMo fallback unchanged | Verified | Unit/integration tests cover missing key/client/parser fallback; runtime MiMo missing-key check on port `18080` fell back to MOCK. |
| `ReviewTaskEntity` persists optional diff text using `@Lob diff_text` | Verified | `ReviewTaskEntity.diffText` uses `@Lob` and `@Column(name = "diff_text")`; service test verifies persistence. |
| Public API response does not expose raw diff | Verified | `ReviewTaskResponse` has no `diffText`; runtime create/detail/list responses omitted `diffText`. |
| Frontend form has optional diff textarea and max length validation | Verified | Browser showed `Optional PR diff`; oversized client-side validation displayed expected error. |
| README and backend README updated | Verified with one minimal fix | Both document manual pasted diff, no automatic GitHub ingestion, max size, fallback, and response non-leakage. |

## Backend Validation Findings

- `CreateReviewTaskRequest` preserves existing `repoUrl` and `prNumber` validation and adds optional `diffText` with `@Size(max = 20000)`.
- Existing `repoUrl + prNumber` requests still work.
- `ReviewTaskService` normalizes diff once, persists the normalized value, builds `ReviewContext` with the same value, and maps responses without exposing it.
- Existing tasks without diff remain readable through list/detail.
- Validation error style remains the existing `ApiResponse` wrapper style: `success=false`, message prefixed by `Validation failed: ...`, `data=null`.

## Frontend Validation Findings

- `CreateReviewTaskCreateForm` renders `Optional PR diff` with the required helper copy.
- Submitting without diff sends only `repoUrl` and `prNumber`.
- Submitting with non-blank diff sends trimmed `diffText`.
- Whitespace-only diff is omitted client-side because `trimmedDiff` must be truthy.
- Diff length over 20000 is blocked client-side with `PR diff is too large. Maximum length is 20000 characters.`
- Existing list/detail/result rendering remains intact; browser smoke confirmed list updates, detail renders, summary content appears, and issue cards show `Source: MOCK`.

## Prompt Builder Validation

No-diff prompt:

- States actual PR diff is unavailable.
- Keeps strict JSON-only output and no markdown fences.
- Allows `[]`.
- Uses backend enum values for severity/category.

Diff prompt:

- Includes actual `diffText` between `--- PR DIFF START ---` and `--- PR DIFF END ---`.
- States the PR diff is the primary review context.
- Instructs review of changed lines and nearby context.
- Covers security, reliability, maintainability, performance, test, style, and bug risks.
- Restricts the model to files/code present in the diff and tells it not to invent files.
- Prefers changed-hunk line numbers where possible.
- Requires strict JSON only and no markdown fences.
- Uses schema-aligned enum values: `HIGH | MEDIUM | LOW` and `BUG | SECURITY | PERFORMANCE | MAINTAINABILITY | STYLE | TEST`.

## Provider/Fallback Validation

- Mock provider returns exactly 3 deterministic findings and ignores diff content.
- Xiaomi MiMo provider calls `ReviewPromptBuilder.buildUserPrompt(context)`, so it uses diff-aware or no-diff prompt based on `ReviewContext.hasDiffText()`.
- `ConfigurableReviewProvider` falls back to mock for missing API key, client failure, parser failure, and unexpected runtime failure.
- Valid `[]` from MiMo remains successful zero findings via `XiaomiMiMoReviewProviderTest.review_handlesValidEmptyArray`.
- Runtime MiMo missing-key fallback was verified with an isolated Spring Boot process on `localhost:18080`, temp H2 file `/private/tmp/codereviewx-round10-mimo`, and a request with diff. Response succeeded with 3 MOCK issues and `riskLevel == issueSummary.riskLevel`.

## API Contract Validation

Preserved endpoints verified:

- `GET /api/health`: HTTP 200, `{"success":true,"message":"OK","data":{"status":"UP","service":"backend-java"}}`
- `POST /api/review-tasks` without diff: `success=true`, 3 issues, all source `MOCK`, `riskLevel=HIGH`.
- `POST /api/review-tasks` with diff: `success=true`, 3 issues, all source `MOCK`, no `diffText` in response.
- `POST /api/review-tasks` oversized diff: HTTP 400, `success=false`, message includes `diffText is too large. Maximum length is 20000 characters.`
- `GET /api/review-tasks`: returned list including ids `356` and `355`, no raw diff field.
- `GET /api/review-tasks/356`: returned detail with 3 issues and no raw diff field.

Response compatibility:

- `ReviewTaskResponse` shape remains stable.
- `ReviewIssueResponse` shape remains stable.
- `IssueSummaryResponse` shape remains stable.
- No public `diffText`, prompt, or raw model output fields observed.
- Runtime responses preserved `ReviewTaskResponse.riskLevel == ReviewTaskResponse.issueSummary.riskLevel`.

## Persistence Validation

- Optional `diffText` is persisted on `ReviewTaskEntity` using `@Lob` and `diff_text`.
- Persisted diff is not copied to public DTOs.
- Service tests verify non-blank diff is persisted after trimming and blank/whitespace diff is stored as null.
- No unnecessary persistence entities were introduced.

## Documentation Validation

- Root README documents Round 10 optional pasted PR diff context, max size, mock default, MiMo behavior, fallback, non-exposure of raw diff/prompt/model output, and no automatic GitHub ingestion.
- Backend README documents the same backend contract and module boundary.
- Minimal fix applied: clarified root README Out of Scope text to say `ai-service` LLM calls or automatic GitHub ingestion are not implemented, while Xiaomi MiMo via `backend-java` and manual pasted diff are supported.
- No shipped-current documentation claim says CodeReviewX automatically fetches GitHub PR diffs or fully integrates with GitHub.

## Test Results With Exact Commands

Backend:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Result:

```text
Tests run: 84, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Frontend:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

Results:

```text
typecheck: pass
build: pass, 35 modules transformed
vitest: 4 files passed, 31 tests passed
```

## Runtime Smoke Results

Initial default backend start attempt:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

Result: failed because an existing Java backend process already held the file H2 database lock:

```text
Database may be already in use: ".../backend-java/data/codereviewx.mv.db"
```

The existing backend was confirmed on port `8080`:

```bash
curl -i http://localhost:8080/api/health
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

Evidence:

```text
HTTP/1.1 200
{"success":true,"message":"OK","data":{"status":"UP","service":"backend-java"}}
java 19683 liyi ... TCP *:8080 (LISTEN)
```

Mock mode without diff:

```bash
curl -s -X POST http://localhost:8080/api/review-tasks \
  -H 'Content-Type: application/json' \
  -d '{"repoUrl":"https://github.com/example/round-10-no-diff","prNumber":10}'
```

Result: `success=true`, id `355`, 3 issues, all `source=MOCK`, `riskLevel=HIGH`, `issueSummary.riskLevel=HIGH`, no `diffText`.

Mock mode with diff:

```bash
curl -s -X POST http://localhost:8080/api/review-tasks \
  -H 'Content-Type: application/json' \
  -d '{"repoUrl":"https://github.com/example/round-10-with-diff","prNumber":10,"diffText":"diff --git a/src/App.tsx b/src/App.tsx\n+const password = request.query.password;\n"}'
```

Result: `success=true`, id `356`, 3 issues, all `source=MOCK`, `riskLevel=HIGH`, `issueSummary.riskLevel=HIGH`, no `diffText`.

Oversized diff:

```bash
curl -i -s -X POST http://localhost:8080/api/review-tasks \
  -H 'Content-Type: application/json' \
  --data-binary @/private/tmp/codereviewx-round10-oversized.json
```

Result:

```text
HTTP/1.1 400
{"success":false,"message":"Validation failed: diffText: diffText is too large. Maximum length is 20000 characters.","data":null}
```

MiMo mode without key and with diff:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run \
  -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:h2:file:/private/tmp/codereviewx-round10-mimo --codereviewx.review.provider=mimo --codereviewx.ai.mimo.api-key='
```

Then:

```bash
curl -s -X POST http://localhost:18080/api/review-tasks \
  -H 'Content-Type: application/json' \
  -d '{"repoUrl":"https://github.com/example/round-10-mimo-fallback-with-diff","prNumber":10,"diffText":"diff --git a/src/App.tsx b/src/App.tsx\n+const password = request.query.password;\n"}'
```

Result: `success=true`, id `1`, 3 issues, all `source=MOCK`, `riskLevel=HIGH`, no `diffText`, no stack trace in response. Server log showed:

```text
Review provider mode is mimo but MIMO_API_KEY is missing; falling back to mock provider
```

## Browser Smoke Result

Frontend dev server:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Browser smoke at `http://127.0.0.1:5173/`:

- Backend status showed UP.
- Create form rendered `Repository URL`, `PR Number`, `Optional PR diff`, and required helper copy.
- Created without diff via UI: task id `357`, list updated, detail rendered.
- Created with diff via UI: task id `358`, list updated, detail rendered.
- Detail showed repository URL, `Risk Level HIGH`, total issues summary, issue cards, and `Source: MOCK`.
- Oversized diff client-side validation showed `PR diff is too large. Maximum length is 20000 characters.`
- Browser console error check returned `[]`.

## Scope Creep Assessment

No forbidden backend scope creep found:

- No `ChangedFileEntity`
- No `FileDiffEntity`
- No `ExecutionTraceEntity`
- No `ProviderInputEntity`
- No `RawPromptEntity`
- No `RawModelOutputEntity`
- No GitHub OAuth
- No automatic GitHub fetching
- No repository clone logic

No forbidden frontend scope creep found:

- No new UI library
- No route overhaul
- No dashboard redesign
- No Monaco editor
- No syntax highlighting package
- No visual diff viewer
- No chart library

## Security/Leakage Assessment

- `MIMO_API_KEY` is read from configuration/environment and was not exposed in public responses.
- Public responses do not expose raw `diffText`, raw prompt, or raw model output.
- Fallback reason is logged server-side only and not returned to clients.
- Persisting raw pasted diff in local H2 is intentional for this round, but it is not exposed through public DTOs.

## Agent Structure and Flow

```text
Input -> Context -> Pipeline -> Provider Selection -> Provider -> Prompt -> Model Client -> Parser -> Finding -> Persistence -> API DTO -> Frontend
```

Implementation match:

```text
Input:
  repoUrl + prNumber + optional diffText

Context:
  ReviewContext with optional diffText

Orchestrator:
  ReviewPipelineService

Provider Selection:
  ConfigurableReviewProvider

Provider:
  MockReviewProvider or XiaomiMiMoReviewProvider

Prompt:
  ReviewPromptBuilder includes diff when available

Model:
  Xiaomi MiMo API through XiaomiMiMoClient

Parser:
  XiaomiMiMoFindingParser

Finding:
  ReviewFinding[]

Persistence:
  ReviewTaskEntity + ReviewIssueEntity

Presentation:
  ReviewTaskResponse + frontend issue cards
```

The implementation matches the expected Round 10 structure.

## Open Issues, If Any

- No blocking product or contract issues found.
- Environment note: repo root is not recognized as a git repository by `git status`, consistent with prior rounds.
- Environment note: default backend start collided with an already-running backend using the same file H2 database. Runtime validation proceeded against the live `8080` instance and an isolated `18080` MiMo-mode instance.

## Required Fixes, If Any

Applied:

- `README.md`: clarified current Out of Scope wording around `ai-service` LLM calls versus already-supported Xiaomi MiMo via `backend-java`.

No Cursor fixes required.

## Recommendation for Qoder

Proceed to Qoder independent review.

Final verdict: `CODEX_ROUND_10_READY_FOR_QODER`
