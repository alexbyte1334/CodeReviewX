# CodeReviewX Frontend

React + TypeScript + Vite workspace for the local CodeReviewX review agent.

## Current Role

The frontend is a thin review console. It calls `backend-java` only and never
talks directly to GitHub, Xiaomi MiMo, or any LLM provider.

Main capabilities:

- Check backend health and MiMo readiness.
- Create review tasks from `repoUrl + prNumber` with optional pasted unified
  diff.
- List review history and load task details.
- Display risk level, issue summary, structured findings, provider hit state,
  agent trace, and comment previews.
- Let the user select comment previews and confirm publishing to GitHub.

## Tech Stack

- React 18
- TypeScript
- Vite
- Vitest + React Testing Library

## Configuration

The backend base URL is read from:

```text
VITE_API_BASE_URL=http://localhost:8080
```

When unset, the frontend defaults to `http://localhost:8080`.

Do not set `VITE_API_BASE_URL` to `/api`; requests already include `/api/...`.

## Local Development

Start backend first:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

Start frontend:

```bash
cd frontend
npm install
npm run dev -- --host 127.0.0.1
```

Open:

```text
http://localhost:5173
```

## User Flow

1. Open the workspace.
2. Confirm backend status and MiMo readiness.
3. Enter repository URL and PR number.
4. Optionally paste unified diff.
5. Run review.
6. Inspect summary, findings, trace, and comment previews.
7. Select previews to publish.
8. Confirm publish action.

If `diffText` is blank, the backend uses `GITHUB_PR` mode and requires
`GITHUB_TOKEN` to load PR metadata and bounded file patches.

## Scripts

```bash
npm run typecheck
npm run build
npm test -- --run
```

## API Surface Used

- `GET /api/health`
- `POST /api/review-tasks`
- `GET /api/review-tasks`
- `GET /api/review-tasks/{id}`
- `GET /api/review-runs/{runId}/trace`
- `GET /api/review-runs/{runId}/comment-previews`
- `PATCH /api/review-runs/{runId}/comment-previews/selection`
- `POST /api/review-runs/{runId}/comment-previews/publish-selected`

## Limits

- No direct GitHub or provider calls from the browser.
- No user login or team model.
- No visual diff viewer yet.
- No route-based deep linking yet.
- No direct raw prompt, raw model output, or raw full diff display.
