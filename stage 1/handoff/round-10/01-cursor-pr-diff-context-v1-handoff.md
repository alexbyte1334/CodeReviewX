# Round 10 / Cursor Handoff: PR Diff Context v1

## Summary of Implemented Changes

Round 10 adds optional pasted PR diff context to CodeReviewX. Users can submit `repoUrl + prNumber` as before, or include optional `diffText` so the Xiaomi MiMo provider can ground review prompts in actual unified diff content.

Key behavior:

- `diffText` is optional on `POST /api/review-tasks`
- Blank or whitespace-only `diffText` is normalized to absent
- Maximum `diffText` length is 20000 characters
- `ReviewContext` carries optional `diffText`
- `ReviewPromptBuilder` produces diff-aware and no-diff prompt variants
- Mock provider behavior is unchanged (3 deterministic issues)
- MiMo fallback behavior is unchanged
- Public API response shape is unchanged and does not expose raw `diffText`

## Backend Files Changed

- `backend-java/src/main/java/com/codereviewx/backend/review/dto/CreateReviewTaskRequest.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/ReviewContext.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/persistence/entity/ReviewTaskEntity.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java`
- `backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/ReviewPromptBuilder.java`
- `backend-java/src/test/java/com/codereviewx/backend/review/controller/ReviewTaskControllerTest.java`
- `backend-java/src/test/java/com/codereviewx/backend/review/service/ReviewTaskServiceTest.java`
- `backend-java/src/test/java/com/codereviewx/backend/review/pipeline/provider/mimo/ReviewPromptBuilderTest.java`
- `backend-java/src/test/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoReviewProviderTest.java`
- `backend-java/src/test/java/com/codereviewx/backend/review/pipeline/ReviewPipelineFallbackIntegrationTest.java`

## Frontend Files Changed

- `frontend/src/types/reviewTask.ts`
- `frontend/src/components/ReviewTaskCreateForm.tsx`
- `frontend/src/styles/app.css`
- `frontend/src/test/ReviewTaskCreateForm.test.tsx`

## Persistence Decision

**Option A implemented:** optional `diffText` is persisted on `ReviewTaskEntity` using `@Lob` column `diff_text`.

Reason:

- Fits existing H2/JPA setup with minimal schema change
- Allows future re-run / audit of submitted diff context without exposing it publicly
- Hibernate auto-updates schema in local file DB

Public API responses intentionally omit `diffText`.

## Validation Rules

- `repoUrl`: required, unchanged
- `prNumber`: required positive integer, unchanged
- `diffText`: optional
- blank / whitespace-only `diffText`: normalized to absent in service layer
- `diffText` length > 20000: validation error
  - Message: `diffText is too large. Maximum length is 20000 characters.`
- Frontend blocks submit when `diffText.length > 20000`

## Prompt Behavior With Diff

When `ReviewContext.hasDiffText()` is true, `ReviewPromptBuilder` includes:

- Statement that the provided PR diff is the primary review context
- Wrapped diff block between `--- PR DIFF START ---` and `--- PR DIFF END ---`
- Instructions to review changed lines and nearby context
- Instructions to identify security, reliability, maintainability, performance, test, style, and bug risks
- Instructions to use only files/code present in the diff and not invent files
- Strict JSON schema and no-markdown rules

## Prompt Behavior Without Diff

When diff is absent, Round 09 limited-context wording is preserved:

- Prompt states actual PR diff is not available
- Prompt asks for conservative synthetic findings only when meaningful risks can be inferred from repo metadata
- Strict JSON schema and no-markdown rules remain

## Mock Behavior

`MockReviewProvider` is unchanged:

- Returns the same 3 deterministic mock issues
- Ignores `diffText`
- Works with or without diff present

## MiMo Fallback Behavior

Unchanged from Round 09:

- `provider=mimo` + missing key -> mock fallback
- `provider=mimo` + client failure -> mock fallback
- `provider=mimo` + parser failure -> mock fallback
- valid `[]` from MiMo -> successful zero findings

Verified with integration test including diff present and missing API key.

No leakage of:

- `MIMO_API_KEY`
- raw prompt
- raw model output
- raw `diffText`
- fallback internals

## API Compatibility Status

Preserved endpoints:

- `GET /api/health`
- `POST /api/review-tasks`
- `GET /api/review-tasks`
- `GET /api/review-tasks/{id}`

Preserved invariants:

- `ReviewTaskResponse` shape unchanged
- `ReviewTaskResponse.riskLevel == ReviewTaskResponse.issueSummary.riskLevel`
- No `diffText` field in public response

## Test Results

Backend:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Result: **84 tests, 0 failures**

Frontend:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

Result:

- typecheck: pass
- build: pass
- tests: **31 tests, 0 failures**

## Runtime Smoke Results

Mock mode without diff:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/round-10-no-diff","prNumber":10}'
```

Confirmed:

- `success=true`
- `issues.length=3`
- `source=MOCK`
- `riskLevel=HIGH`
- `riskLevel == issueSummary.riskLevel`
- no `diffText` in response

Mock mode with diff:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/round-10-with-diff","prNumber":10,"diffText":"diff --git a/src/App.tsx b/src/App.tsx\n+const password = request.query.password;\n"}'
```

Confirmed:

- `success=true`
- `issues.length=3`
- `source=MOCK`
- response shape unchanged
- `riskLevel == issueSummary.riskLevel`
- no `diffText` in response

Oversized diff validation:

- `success=false`
- message contains `diffText is too large`

MiMo missing-key fallback with diff:

- Covered by `ReviewPipelineFallbackIntegrationTest.createTask_mimoModeWithoutKeyWithDiffFallsBackToMockAndSucceeds`

Browser smoke:

- Not manually executed in this session; frontend build/tests pass and create form now includes optional diff textarea with client validation

## README Updates

Updated:

- `README.md`
- `backend-java/README.md`

Documented:

- Round 10 optional pasted PR diff context
- repoUrl + prNumber flow still works
- diffText optional
- MiMo prompt uses diffText when provided
- mock mode default and stable
- MiMo fallback unchanged
- public response does not expose raw prompt/model output/diff
- manual pasted diff supported
- automatic GitHub ingestion not implemented
- maximum diffText length 20000 characters

## Agent Structure and Flow

```text
Input -> Context -> Pipeline -> Provider Selection -> Provider -> Prompt -> Model Client -> Parser -> Finding -> Persistence -> API DTO -> Frontend
```

Round 10 structure:

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

## Known Limitations

- No automatic GitHub PR fetching
- No repository clone or file tree analysis
- Mock provider ignores diff content
- Diff normalization trims leading/trailing whitespace (including trailing newline)
- Browser runtime smoke not manually re-run in this session
- MiMo live API smoke with real key not executed in this session

## Recommendation for Codex Validation

Codex should verify:

1. Backend tests pass (`mvn test`)
2. Frontend typecheck/build/tests pass
3. Create task without diff still returns 3 MOCK issues
4. Create task with diff succeeds and does not expose `diffText`
5. Oversized diff returns validation error
6. MiMo prompt builder diff/no-diff behavior via unit tests
7. Missing-key fallback with diff still succeeds
8. README claims match implemented behavior and do not overstate GitHub integration

## Final Verdict

`CURSOR_ROUND_10_READY_FOR_CODEX`
