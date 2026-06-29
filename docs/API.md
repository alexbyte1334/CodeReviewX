# CodeReviewX API

> Current frontend-to-backend REST API. Historical `backend-java -> ai-service`
> API sketches are no longer part of the active runtime.

## 1. Base URL and Envelope

Local backend:

```text
http://localhost:8080
```

All successful and failed responses use the same envelope:

```json
{
  "success": true,
  "message": "OK",
  "data": {}
}
```

On failures, `success` is `false`, `message` is user-readable, and `data` is
usually `null`.

## 2. Health

```http
GET /api/health
```

Response:

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "status": "UP",
    "service": "backend-java",
    "reviewProvider": "mimo",
    "mimoConfigured": true
  }
}
```

`mimoConfigured` reflects whether both planner and executor role keys are
available to the backend process.

## 3. Review Tasks

### Create Review Task

```http
POST /api/review-tasks
Content-Type: application/json
```

Request:

```json
{
  "repoUrl": "https://github.com/owner/repo",
  "prNumber": 42,
  "diffText": "diff --git a/src/App.java b/src/App.java\n+...",
  "provider": "mimo",
  "reviewMode": "MANUAL_DIFF"
}
```

Fields:

| Field | Required | Notes |
|---|---:|---|
| `repoUrl` | yes | GitHub repository URL |
| `prNumber` | yes | positive integer |
| `diffText` | no | optional unified diff, max 20,000 characters |
| `provider` | no | legacy field; only `mimo` is accepted |
| `reviewMode` | no | `MANUAL_DIFF` or `GITHUB_PR` |

Mode resolution:

1. Explicit `reviewMode` wins.
2. Non-blank `diffText` implies `MANUAL_DIFF`.
3. Otherwise the backend uses `GITHUB_PR`.

`MANUAL_DIFF` requires non-blank `diffText`. `GITHUB_PR` requires a configured
`GITHUB_TOKEN` so the backend can load PR metadata, bounded file patches, and
bounded changed-file repository context.

Response:

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": 385,
    "repoUrl": "https://github.com/owner/repo",
    "prNumber": 42,
    "status": "SUCCESS",
    "summary": "Review completed for PR #42 with generated findings.",
    "riskLevel": "HIGH",
    "errorMessage": null,
    "errorCode": null,
    "createdAt": "2026-06-27T10:00:00",
    "updatedAt": "2026-06-27T10:00:10",
    "requestedProvider": "mimo",
    "providerUsed": "mimo",
    "providerHit": true,
    "latestRunId": 257,
    "reviewMode": "GITHUB_PR",
    "commentPreviewCount": 2,
    "issueSummary": {
      "totalIssues": 2,
      "highCount": 1,
      "mediumCount": 1,
      "lowCount": 0,
      "riskLevel": "HIGH"
    },
    "issues": []
  }
}
```

The response does not expose raw `diffText`, GitHub token, raw full diff, raw
prompt, or raw model output.

Issue `source` values currently include:

| Source | Meaning |
|---|---|
| `MIMO` | MiMo dual-agent finding after gate approval |
| `SEMGREP` | request-time Semgrep-style changed-line finding |
| `DEPENDENCY` | request-time dependency hygiene finding from indexed files |

### List Review Tasks

```http
GET /api/review-tasks
```

Returns tasks ordered newest first. The current endpoint is not paginated.

### Get Review Task

```http
GET /api/review-tasks/{id}
```

Returns the same shape as create, including issues, ingestion summary, trace
summary, and comment preview count when available.

## 4. Review Runs

### Get Review Run

```http
GET /api/review-runs/{runId}
```

Response includes:

| Field | Meaning |
|---|---|
| `id` | review run id |
| `taskId` | owning review task |
| `status` | `PENDING`, `INGESTING`, `REVIEWING`, `BUILDING_PREVIEW`, `SUCCESS`, `FAILED` |
| `reviewMode` | `MANUAL_DIFF` or `GITHUB_PR` |
| `errorCode` / `errorMessage` | failure summary |
| `inputSnapshotSummary` | sanitized GitHub PR metadata summary when available |
| `providerSummary` | requested/used provider and finding count |

### Get Agent / Tool Trace

```http
GET /api/review-runs/{runId}/trace
```

Successful GitHub PR runs usually include:

```text
github.pr.metadata.load
github.pr.diff.load
repository.context.index
static.analysis.findings
mimo.ai1.plan
mimo.ai2.execute
mimo.ai1.gate
issue.generate
comment.preview.build
```

Trace responses expose status, timing, summary, and error code. They do not
expose secrets, raw prompts, raw model output, or raw full diff.

## 5. Comment Previews

### List Comment Previews

```http
GET /api/review-runs/{runId}/comment-previews
```

Returns local draft comments generated from persisted issues.

### Update Selection

```http
PATCH /api/review-runs/{runId}/comment-previews/selection
Content-Type: application/json
```

Request:

```json
{
  "selectedPreviewIds": [65, 66]
}
```

All selected ids must belong to the requested run.

### Publish One Preview

```http
POST /api/review-runs/{runId}/comment-previews/{previewId}/publish
Content-Type: application/json
```

Request:

```json
{
  "confirmed": true
}
```

The preview must already be selected. Publishing uses the stored GitHub input
snapshot, the preview file path, line, side, and draft body.

### Publish Selected Previews

```http
POST /api/review-runs/{runId}/comment-previews/publish-selected
Content-Type: application/json
```

Request:

```json
{
  "confirmed": true
}
```

At least one preview must be selected. Each preview transitions through:

```text
NOT_PUBLISHED -> PUBLISHING -> PUBLISHED | FAILED
```

## 6. Common Failure Codes

| Code | Meaning |
|---|---|
| `GITHUB_AUTH_MISSING` | `GITHUB_TOKEN` is not configured |
| `GITHUB_AUTH_FAILED` | GitHub rejected the configured token |
| `GITHUB_RATE_LIMITED` | GitHub API rate limit reached |
| `GITHUB_PR_NOT_FOUND` | PR or repository could not be found |
| `GITHUB_DIFF_LOAD_FAILED` | PR files patch could not be loaded |
| `GITHUB_DIFF_TOO_LARGE` | PR exceeded configured ingestion limits |
| `GITHUB_DIFF_UNAVAILABLE` | GitHub returned no textual patch to review |
| `MIMO_AUTH_MISSING` | planner/executor role keys are missing |
| `MIMO_PROVIDER_ERROR` | provider request or structured parsing failed |
| `MIMO_GATE_REJECTED` | AI-1 gate rejected AI-2 candidate review |

## 7. Removed Historical API Surface

The old document described a planned Python FastAPI `ai-service` API. That
service is not implemented and is not called by the current application. All
active review orchestration lives in `backend-java`.
