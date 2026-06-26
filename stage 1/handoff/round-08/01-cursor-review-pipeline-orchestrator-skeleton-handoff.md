# Round 08 / Task 01 Handoff: Review Pipeline Orchestrator Skeleton

## 1. Summary

Round 08 implementation completed. The backend now routes review generation through an internal pipeline boundary:

```text
ReviewTaskService
  -> ReviewPipelineService.run(ReviewContext)
      -> MockReviewProvider.review(context)
          -> ReviewProviderResult (3 ReviewFinding records)
  -> map ReviewFinding -> ReviewIssueEntity
  -> persist issues
  -> compute issueSummary / riskLevel at response time
```

External API behavior is unchanged from Round 07. All 46 backend tests and 26 frontend tests pass. Runtime curl validation and restart persistence smoke test passed.

---

## 2. Files Changed

### New — Pipeline Core (`backend-java/src/main/java/com/codereviewx/backend/review/pipeline/`)

| File | Purpose |
|---|---|
| `ReviewContext.java` | Input context: `taskId`, `repoUrl`, `prNumber`, `createdAt` |
| `ReviewFinding.java` | Internal normalized finding model |
| `ReviewProvider.java` | Provider interface: `ReviewProviderResult review(ReviewContext context)` |
| `ReviewProviderResult.java` | Wrapper: `findings`, `providerName`, `successful`, `message` |
| `ReviewPipelineService.java` | Spring service that invokes the configured provider |

### New — Provider (`backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/`)

| File | Purpose |
|---|---|
| `MockReviewProvider.java` | Deterministic mock provider; moved mock issue content from `ReviewTaskService` |

### Modified

| File | Change |
|---|---|
| `review/service/ReviewTaskService.java` | Injects `ReviewPipelineService`; builds `ReviewContext`; maps `ReviewFinding` → `ReviewIssueEntity`; removed inline `buildMockIssueEntities()` |
| `README.md` | Documents Round 08 pipeline architecture, MockReviewProvider, 3–5 round roadmap |
| `backend-java/README.md` | Documents pipeline classes and review flow |

### New Tests

| File | Tests |
|---|---|
| `review/pipeline/provider/MockReviewProviderTest.java` | 7 tests |
| `review/pipeline/ReviewPipelineServiceTest.java` | 2 tests (uses fake provider) |

### Unchanged

- All API DTOs (`ReviewTaskResponse`, `ReviewIssueResponse`, `IssueSummaryResponse`, `CreateReviewTaskRequest`)
- All controllers and endpoints
- Persistence entities and repositories
- Frontend source (no changes required)

---

## 3. Architecture Implemented

```text
review/
├── controller/          (unchanged — public API)
├── dto/                 (unchanged — public contract)
├── enums/               (unchanged)
├── persistence/         (unchanged — JPA entities/repos)
├── pipeline/            (NEW — internal review orchestration)
│   ├── ReviewContext.java
│   ├── ReviewFinding.java
│   ├── ReviewProvider.java
│   ├── ReviewProviderResult.java
│   ├── ReviewPipelineService.java
│   └── provider/
│       └── MockReviewProvider.java
└── service/
    └── ReviewTaskService.java  (refactored — calls pipeline)
```

Key design decisions:

- `ReviewFinding` is internal only; not exposed via API
- `ReviewProviderResult` is internal only; not persisted
- `ReviewPipelineService` accepts `ReviewProvider` interface (Spring injects `MockReviewProvider`)
- No provider registry, no multi-provider orchestration, no async execution
- Future providers (`AIReviewProvider`, `StaticAnalysisProvider`, `GitHubContextProvider`) can implement `ReviewProvider` with minimal changes

---

## 4. Provider/Pipeline Flow

```text
POST /api/review-tasks
  -> ReviewTaskController.createTask(request)
  -> ReviewTaskService.createTask(request)
      1. save ReviewTaskEntity (status SUCCESS, mock summary)
      2. build ReviewContext(taskId, repoUrl, prNumber, createdAt)
      3. reviewPipelineService.run(context)
           -> MockReviewProvider.review(context)
           -> ReviewProviderResult(findings=[3], providerName="MockReviewProvider", successful=true)
      4. map each ReviewFinding -> ReviewIssueEntity (issueKey, severity, etc.)
      5. reviewIssueRepository.saveAll(issueEntities)
      6. load persisted issues, build ReviewTaskResponse (issueSummary computed, riskLevel derived)
      7. return ReviewTaskResponse (unchanged shape)
```

Mock findings preserved from Round 07:

| issueKey | severity | category | source | status |
|---|---|---|---|---|
| ISSUE-1 | HIGH | SECURITY | MOCK | OPEN |
| ISSUE-2 | MEDIUM | MAINTAINABILITY | MOCK | OPEN |
| ISSUE-3 | LOW | TEST | MOCK | OPEN |

---

## 5. API Contract Preservation Evidence

All Round 07 endpoints and response shapes preserved:

| Check | Result |
|---|---|
| `GET /api/health` | ✅ `{"success":true,"message":"OK","data":{"status":"UP","service":"backend-java"}}` |
| `POST /api/review-tasks` | ✅ Returns 3 issues, issueSummary, riskLevel |
| `GET /api/review-tasks` | ✅ List with full task payloads |
| `GET /api/review-tasks/{id}` | ✅ Detail with issues and summary |
| `ApiResponse<T>` wrapper | ✅ Unchanged |
| `ReviewTaskResponse` fields | ✅ All present |
| `ReviewIssueResponse` fields | ✅ All present |
| `IssueSummaryResponse` fields | ✅ All present |
| Pipeline internals exposed | ✅ None — no `ReviewFinding`, `ReviewProviderResult`, or provider name in API |
| `riskLevel == issueSummary.riskLevel` | ✅ Verified in create response |

Create response excerpt (task id 97, runtime verification):

```json
{
  "success": true,
  "data": {
    "riskLevel": "HIGH",
    "issues": [{"id":"ISSUE-1","source":"MOCK","status":"OPEN",...}, ...],
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

---

## 6. Persistence Preservation Evidence

- `ReviewTaskEntity` and `ReviewIssueEntity` unchanged — no new columns
- No `IssueSummaryEntity` introduced
- `riskLevel` still derived at response time from computed `issueSummary`
- Public issue id still `issueKey` → `ReviewIssueResponse.id` (e.g. `ISSUE-1`)
- Internal DB `id` still hidden from API

Restart persistence smoke (task id 97):

```text
1. Created task via POST (id=97)
2. Stopped backend (kill PID)
3. Restarted backend (mvn spring-boot:run)
4. GET /api/review-tasks/97
   -> task exists
   -> 3 issues persisted
   -> riskLevel=HIGH, issueSummary.riskLevel=HIGH (match)
   -> sources=[MOCK, MOCK, MOCK]
   -> ids=[ISSUE-1, ISSUE-2, ISSUE-3]
```

---

## 7. Tests Added or Updated

**Backend total: 46 tests, 0 failures — BUILD SUCCESS**

| Test Class | Count | Notes |
|---|---|---|
| `CodeReviewXBackendApplicationTests` | 1 | Unchanged |
| `MockReviewProviderTest` | 7 | **NEW** — 3 findings, severities, MOCK/OPEN, ISSUE-1/2/3, successful, provider name |
| `ReviewPipelineServiceTest` | 2 | **NEW** — invokes fake provider, preserves findings, no API DTOs/JPA entities |
| `ReviewTaskControllerTest` | 12 | Unchanged — all pass |
| `ReviewTaskServiceTest` | 24 | Unchanged — all pass (covers pipeline integration via createTask) |

`ReviewTaskServiceTest` continues to verify:

- 3 issues on create/get
- HIGH/MEDIUM/LOW severities
- source=MOCK, status=OPEN
- issue ids ISSUE-1/2/3
- issueSummary totals and riskLevel=HIGH
- riskLevel == issueSummary.riskLevel
- persistence to database
- missing task throws ReviewTaskNotFoundException

---

## 8. Validation Commands Run

### Backend

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Result:

```text
Tests run: 46, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 3.332 s
```

### Frontend

```bash
cd frontend
npm run typecheck   # clean
npm run build       # ✓ built in 224ms
npm test -- --run   # 26 tests passed (4 files)
```

---

## 9. Runtime Verification Evidence

Backend started with:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

### Health

```bash
curl http://localhost:8080/api/health
# {"success":true,"message":"OK","data":{"status":"UP","service":"backend-java"}}
```

### Create

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/round-08-pipeline-demo","prNumber":8}'
```

Verified:

- `success=true`
- `data.id` exists (97)
- `data.issues.length=3`
- `data.issueSummary.totalIssues=3`, `highCount=1`, `mediumCount=1`, `lowCount=1`
- `data.issueSummary.riskLevel=HIGH`, `data.riskLevel=HIGH`
- `data.riskLevel == data.issueSummary.riskLevel`
- all `data.issues[*].source=MOCK`, `status=OPEN`

### List / Detail

Both returned task 97 with full issue payloads and correct summary.

### Restart Persistence

Task 97 survived backend restart; issues and summary intact.

---

## 10. Frontend Validation Evidence

No frontend code changes were required.

```bash
cd frontend
npm run typecheck  # PASS
npm run build      # PASS
npm test -- --run  # 26/26 PASS
```

Browser smoke:

```text
NOT RUN
Reason: Automated browser smoke not executed in this session; no frontend changes were made.
Risk: Low — frontend contract unchanged and all 26 unit/component tests pass.
Recommended follow-up: Codex validation can run `npm run dev -- --host 127.0.0.1` and confirm UI renders as in Round 07.
```

---

## 11. README Updates

### Root `README.md`

- Current implementation section updated to Round 08
- Added "Review Pipeline (Round 08)" section with internal flow diagram
- Documents `MockReviewProvider`, deterministic mock findings, no real AI yet
- Notes 3–5 round completion target and likely Round 09 AI provider
- Round progress table: Round 08 ✅ Complete, Round 09 AI Review Provider v1 (planned)

### `backend-java/README.md`

- Status banner updated to Round 08
- Pipeline classes listed under Implemented
- Mock behavior section includes pipeline flow diagram
- Notes real AI review planned for near-term follow-up

---

## 12. Known Limitations

- Only `MockReviewProvider` exists; no real analysis
- No GitHub API, repository clone, PR diff ingestion
- No Semgrep, LLM, or `ai-service` integration
- No provider registry or multi-provider orchestration
- No async review execution or status workflow
- `ReviewContext` has minimal fields (no diff/changed-files model yet)
- `ReviewProviderResult.message` is always null for mock provider
- Browser smoke not run in this session

---

## 13. Risks / Follow-ups for Codex

Codex should independently validate:

1. `cd backend-java && JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test` — expect 46 tests, BUILD SUCCESS
2. `cd frontend && npm run typecheck && npm run build && npm test -- --run` — expect 26 tests pass
3. Start backend, curl all three review-task endpoints + health
4. Confirm pipeline classes exist and mock generation is NOT in `ReviewTaskService`
5. Confirm no pipeline internals in API responses
6. Confirm persistence model unchanged (no new tables/columns)
7. Restart persistence smoke for a newly created task
8. Optional browser smoke via `npm run dev`
9. Verify README accuracy (no claim of real AI review)
10. Verify no scope creep (GitHub/Semgrep/LLM/queue/auth)

---

## 14. Suggested Round 09 Direction

Implement **AI Review Provider v1**:

```text
ReviewTaskService
  -> ReviewPipelineService
      -> AIReviewProvider (new)
          -> calls ai-service (or mock LLM endpoint)
          -> maps structured JSON -> ReviewFinding[]
      -> (optional fallback) MockReviewProvider
  -> persist findings as today
```

Prerequisites for Round 09:

- Define `AIReviewProvider` implementing `ReviewProvider`
- Extend `ReviewContext` with diff/changed-files if needed
- Wire `ai-service` HTTP client (mock-first)
- Add `IssueSource.AI` or similar when real findings arrive
- Keep API contract stable; external behavior may evolve only with explicit contract update round

Target: first end-to-end path from PR input → real (or mock-LLM) analysis → persisted findings → unchanged frontend display.
