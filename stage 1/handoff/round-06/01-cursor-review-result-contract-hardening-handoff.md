# Round 06 Cursor Handoff: Review Result Contract Hardening

## 1. Summary

Round 06 completes the review result contract hardening across backend and frontend. The backend now computes and returns a unified `IssueSummaryResponse` alongside the existing `issues` list, making the backend the authoritative source for risk level and issue aggregation. The frontend has been updated to prefer `task.issueSummary` from the backend response and fall back to a local computation only when absent. Issue cards now display `source` and `status` badges. The `IssueType` legacy enum was deleted (no live references). README files document the current/planned boundary clearly.

---

## 2. Files Changed

### Backend

| File | Change |
|---|---|
| `backend-java/src/main/java/com/codereviewx/backend/review/enums/RiskLevel.java` | Added `NONE` value |
| `backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueSource.java` | Updated to `MOCK, SEMGREP, LLM, MANUAL` |
| `backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueStatus.java` | **New** — `OPEN, RESOLVED, FALSE_POSITIVE` |
| `backend-java/src/main/java/com/codereviewx/backend/review/dto/IssueSummaryResponse.java` | **New** — aggregated summary DTO |
| `backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewIssueResponse.java` | Added `source` and `status` fields |
| `backend-java/src/main/java/com/codereviewx/backend/review/dto/ReviewTaskResponse.java` | Added `issueSummary` field |
| `backend-java/src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java` | Added `buildIssueSummary`, set `source`/`status` on mock issues, removed hardcoded `RiskLevel.HIGH`, populate `issueSummary` in `toResponse` |
| `backend-java/src/main/java/com/codereviewx/backend/review/enums/IssueType.java` | **Deleted** — no references found |
| `backend-java/src/test/java/com/codereviewx/backend/review/controller/ReviewTaskControllerTest.java` | Added 4 new test cases for `issueSummary`, `source`/`status`, and riskLevel consistency |
| `backend-java/src/test/java/com/codereviewx/backend/review/service/ReviewTaskServiceTest.java` | Added 4 new test cases for `issueSummary`, `source`, and `status` |

### Frontend

| File | Change |
|---|---|
| `frontend/src/types/reviewTask.ts` | Added `RiskLevel` (with `NONE`), `IssueSource`, `IssueStatus`, `IssueSummary`; updated `ReviewIssue` with `source`/`status`; updated `ReviewTask` with optional `issueSummary` |
| `frontend/src/utils/reviewSummary.ts` | **New** — `computeIssueSummaryFromIssues` and `getIssueSummary` utility |
| `frontend/src/components/ReviewTaskDetail.tsx` | Replaced inline `computeRiskLabel`/`computeRiskClass` logic with `getIssueSummary`; `IssueSummaryPanel` now accepts `IssueSummary`; added source and status badges to `IssueCard`; updated demo label |
| `frontend/src/test/ReviewTaskDetail.test.tsx` | Updated mock fixtures with `source`/`status`; added tests for source badges, status badges, backend summary priority, and fallback behavior |

### Documentation

| File | Change |
|---|---|
| `README.md` | Updated "Current Status" → "Current Implementation", added "Current Review Result Contract", "Planned Architecture", "Out of Scope" sections; updated Round Progress table |
| `frontend/README.md` | Updated title/round; added "Current Implementation (Round 06)" section with summary source-of-truth, issue card badge info, and full demo/mock data notice |

---

## 3. Backend Contract Result

### `ReviewTaskResponse.issueSummary`

```json
{
  "issueSummary": {
    "totalIssues": 3,
    "highCount": 1,
    "mediumCount": 1,
    "lowCount": 1,
    "riskLevel": "HIGH"
  }
}
```

### `ReviewIssueResponse.source` and `.status`

```json
{
  "source": "MOCK",
  "status": "OPEN"
}
```

### `ReviewTaskResponse.riskLevel` consistency

`ReviewTaskResponse.riskLevel` is set from `buildIssueSummary(issues).getRiskLevel()` during task creation, and `toResponse` recomputes the same `issueSummary` dynamically. Both fields are always equal.

Verified via curl:

```
data.riskLevel = "HIGH"
data.issueSummary.riskLevel = "HIGH"
```

---

## 4. Frontend Result

### Summary panel source-of-truth

`ReviewTaskDetail` calls `getIssueSummary(task)` which returns:
- `task.issueSummary` if present (backend data)
- `computeIssueSummaryFromIssues(task.issues)` as fallback

The `IssueSummaryPanel` component receives an `IssueSummary` object and renders from it — no local computation inside the panel.

### Fallback behavior

If `task.issueSummary` is `undefined`, the fallback computes counts from `task.issues`. Covered by test `fallback summary works when issueSummary is missing`.

### Source/status badges

`IssueCard` now renders four badges in order: `[severity] [category] [source] [status]`. Current values for mock data: `MOCK` and `OPEN`.

---

## 5. Tests Run

### Backend

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Result: **33 tests — 0 failures, 0 errors, 0 skipped. BUILD SUCCESS.**

### Frontend

```bash
cd frontend
npm run typecheck
```
Result: **0 type errors.**

```bash
npm run build
```
Result: **BUILD SUCCESS — 3 output files, 0 errors.**

```bash
npm test
```
Result: **26 tests across 4 test files — all passed.**

---

## 6. Runtime Verification

### Health

```bash
curl http://localhost:8080/api/health
```
```json
{"success":true,"message":"OK","data":{"status":"UP","service":"backend-java"}}
```

### Create

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/example/round-06-demo","prNumber":6}'
```

Confirmed:
- `success = true`
- `data.issues.length = 3`
- `data.issueSummary.totalIssues = 3`
- `data.issueSummary.highCount = 1`
- `data.issueSummary.mediumCount = 1`
- `data.issueSummary.lowCount = 1`
- `data.issueSummary.riskLevel = "HIGH"`
- `data.riskLevel = "HIGH"`
- `data.issues[0].source = "MOCK"`
- `data.issues[0].status = "OPEN"`
- `data.issues[1].source = "MOCK"`
- `data.issues[1].status = "OPEN"`
- `data.issues[2].source = "MOCK"`
- `data.issues[2].status = "OPEN"`

### List

```bash
curl http://localhost:8080/api/review-tasks
```

Confirmed: list response includes `issueSummary`, `issues[].source`, `issues[].status` on each task.

### Detail

Detail endpoint (via service test + controller test): returns `issueSummary`, `issues[].source`, `issues[].status`.

---

## 7. IssueType Handling

`IssueType` was deleted.

Pre-deletion grep:

```bash
grep -R "IssueType" backend-java/src
```

Result: only `IssueType.java` itself was found — no references in any other file. The file was safely deleted.

---

## 8. Scope Compliance

Confirmed not introduced:

- **database** — not introduced; still in-memory `ConcurrentHashMap`
- **persistence** — not introduced
- **GitHub API** — not called
- **Semgrep** — not executed
- **LLM / AI service** — not called
- **auth** — not introduced
- **component library** (Ant Design / Material UI) — not introduced
- **complex state management** (Redux / MobX / React Query) — not introduced
- **chart library** (Chart.js / ECharts / D3) — not introduced
- **Next.js / SSR** — not introduced
- **status update API** — not implemented
- **resolve / false-positive workflow** — not implemented
- **Round 07 work** — not started

---

## 9. Known Issues / Follow-ups

1. `toResponse` recomputes `issueSummary` by calling `buildIssueSummary(issues)` every time. This is correct but slightly redundant when the task was just created (the summary was already computed then). This is a non-issue for in-memory data but worth noting before persistence is introduced.

2. The `ReviewTask` in-memory model does not yet store `IssueSummaryResponse` — it is recomputed on each read. For Round 07 persistence, the summary can either be stored as a computed column or recomputed at read time. Both are valid.

3. The `IssueStatus` and `IssueSource` enums are in contract but no update API exists yet. Status update workflow is explicitly out of scope for Round 06.

4. Frontend `app.css` has no specific styles for `.badge-source` and `.badge-status` — they fall through to the base `.badge` style. Visual differentiation could be added in Round 07 if desired.

---

## 10. Recommendation for Codex

Please verify the following in your review:

1. **`issueSummary` contract** — `GET /api/review-tasks/{id}` returns correct `issueSummary` with `totalIssues`, `highCount`, `mediumCount`, `lowCount`, `riskLevel`.

2. **Risk source-of-truth** — `data.riskLevel == data.issueSummary.riskLevel` in all three endpoints (create / list / detail).

3. **`source`/`status` fields** — all 3 mock issues have `source: "MOCK"` and `status: "OPEN"` in all three endpoints.

4. **Frontend fallback behavior** — render `ReviewTaskDetail` with `issueSummary: undefined` and verify summary panel still works via local computation.

5. **README current/planned boundary** — README clearly separates current in-memory mock implementation from planned database/GitHub/Semgrep/LLM architecture.
