# CodeReviewX — Frontend

**Manual Diff-Grounded AI Code Review Agent MVP**

---

## Overview

CodeReviewX is a locally runnable AI-assisted code review agent prototype. The frontend lets you create review tasks with repository URL, PR number, and optional pasted PR diff context, then inspect structured agent findings with risk level and recommendations.

---

## Tech Stack

- React 18 + TypeScript + Vite
- Vitest + React Testing Library

---

## Prerequisites

- Node.js 18+
- `backend-java` running on port `8080`

---

## Install

```bash
npm install
```

---

## Dev

```bash
npm run dev
```

Opens at [http://localhost:5173](http://localhost:5173).

With explicit host binding:

```bash
npm run dev -- --host 127.0.0.1
```

---

## Build

```bash
npm run build
```

---

## Typecheck

```bash
npm run typecheck
```

---

## Test

```bash
npm test -- --run
```

---

## API Base URL Configuration

The frontend reads the backend base URL from:

```
VITE_API_BASE_URL=http://localhost:8080
```

If the variable is not set, it defaults to `http://localhost:8080`.

To override, create a `.env.local` file in `frontend/`:

```
VITE_API_BASE_URL=http://your-backend-host:8080
```

> **Important:** Do not set `VITE_API_BASE_URL` to `/api`. This would cause requests to become `/api/api/...` and fail. Always use the full `http://host:port` form.

---

## Required Backend

Start backend (mock mode default):

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

Health check:

```bash
curl http://localhost:8080/api/health
```

---

## Basic User Flow

1. Open [http://localhost:5173](http://localhost:5173).
2. Check backend status in the sidebar **Status** widget and the workspace toolbar chip.
3. Use sidebar nav or toolbar quick pills to expand **Run Review**, **Review History**, or **Findings** (panels start collapsed for a cleaner workspace).
4. Enter Repository URL and Pull Request Number.
5. Optionally paste a unified diff for diff-grounded review.
6. Click **Run Review**.
7. The created task appears in Review History; findings display when **Findings** is expanded.
8. Click any task to reload and inspect full review results.
9. Toggle light/dark mode from the sun/moon control in the top window chrome.

---

## Create Form Behavior

- **Repository URL** and **Pull Request Number** are required.
- **Optional PR Diff** accepts pasted unified diff text.
- Leave diff empty to run a metadata-only review.
- Whitespace-only diff is omitted on submit.
- Diff over 20,000 characters is blocked client-side (backend is source of truth).
- Character counter shows current diff length.

---

## Review Summary

The detail panel shows:

- **Review Summary** with risk level (HIGH / MEDIUM / LOW / NONE)
- **Findings** count and severity breakdown
- **Reviewed Target** (repo URL + PR number)
- **Provider Source** (Mock Provider or Xiaomi MiMo)
- Created timestamp

---

## Issue Cards

Each finding card displays:

- Severity, category, source, and status badges
- Title
- Location (file path + line range)
- Description
- Recommendation

Source labels:

- `MOCK` → Mock Provider
- `MIMO` → Xiaomi MiMo

---

## Provider Modes

- **Mock mode (default):** deterministic mock findings, no API key required.
- **MiMo mode:** configure backend with `MIMO_API_KEY` and `--codereviewx.review.provider=mimo`.

See [backend-java/README.md](../backend-java/README.md) for provider configuration.

---

## Known Limitations

- No automatic GitHub PR fetching
- No GitHub App integration
- No private repository access
- No repository clone
- No full repository analysis or production-grade review claim
- No PR comment write-back
- Mock provider is the safe default
- MiMo mode requires local environment configuration
- No visual diff viewer or syntax highlighting
- No RAG
- No MCP
- No Function Calling
- No Memory system
- No production auth/team model

---

## Post-MVP Roadmap (Future Work Only)

1. GitHub PR ingestion
2. Project rules / review policy
3. RAG / knowledge context
4. Function Calling / tool use
5. MCP integration
6. Memory system
7. PR comment workflow

These are documented as future directions only — not implemented in the MVP.

---

## Data Persistence

Review tasks and issues are persisted in the backend H2 database. Data survives backend restarts.
