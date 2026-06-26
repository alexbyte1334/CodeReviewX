# CodeReviewX

> **Manual Diff-Grounded AI Code Review Agent MVP**

CodeReviewX is a locally runnable AI-assisted code review agent prototype. It supports manual review task creation with repository URL, PR number, and optional pasted PR diff context.

---

## Current Implementation

**Round 14: Live MiMo Verification + Git/GitHub Repository Sync**

Round 14 verifies live Xiaomi MiMo with a local `MIMO_API_KEY`, preserves mock fallback safety, and synchronizes the productized Stage 1.5 MVP to GitHub with secret/artifact protection.

**Round 12: Final Hardening + Demo Readiness**

Round 12 verifies, hardens, and documents the Manual Diff-Grounded AI Code Review Agent MVP for local demo and handoff. No new Stage 2 capabilities were added. See [tasks/round-12/demo-script.md](tasks/round-12/demo-script.md) for a concise demo walkthrough.

**Round 11: Frontend Agent Result Presentation v1**

Round 11 polishes the frontend into a demo-ready review agent MVP. The UI clearly communicates review agent capabilities, limitations, and structured findings presentation. Backend/API behavior from Round 10 is preserved unchanged.

**Round 10: PR / Diff Context v1**

CodeReviewX supports optional pasted PR diff context for AI review. Users can submit `repoUrl + prNumber` as before, or optionally include `diffText` to ground Xiaomi MiMo prompts in actual code changes. Default local behavior remains mock mode. MiMo mode is enabled through configuration and `MIMO_API_KEY`.

The backend exposes Spring Boot APIs for creating, listing, and reading review tasks. Review tasks and review issues are persisted to a file-based H2 database. Data survives backend restarts. Review findings are generated through an internal pipeline with configuration-driven provider selection and persisted as `ReviewIssue` records.

Round 10 introduces optional manual pasted diff context while preserving the complete Round 09 API contract, persistence model, and mock fallback safety.

Round 09 introduced the first real AI provider path. Round 08 introduced the internal review pipeline architecture. Round 06 introduced a backend-computed issue summary. The backend is the authoritative source for review result aggregation and risk level. The frontend prefers `issueSummary` from the backend and only computes a local summary as a compatibility fallback.

Issue sources: `MOCK` (default), `MIMO` (when MiMo mode succeeds). Current issue status is `OPEN`.

### Review Agent Flow (Round 10)

```text
ReviewTaskService
  -> ReviewPipelineService.run(ReviewContext with optional diffText)
      -> ConfigurableReviewProvider
          -> MockReviewProvider (default / fallback)
          OR
          -> XiaomiMiMoReviewProvider
              -> ReviewPromptBuilder (diff-aware prompt when diffText present)
              -> XiaomiMiMoClient
              -> XiaomiMiMoFindingParser
  -> map ReviewFinding -> ReviewIssueEntity
  -> persist issues
  -> compute issueSummary and riskLevel at response time
```

- **Default:** `MockReviewProvider` returns 3 deterministic mock findings (`source: MOCK`).
- **MiMo mode:** enabled via `codereviewx.review.provider=mimo` and `MIMO_API_KEY` environment variable.
- **Diff context:** optional `diffText` on create request; MiMo uses it as primary review context when provided.
- **Fallback:** missing key, API failure, or parse failure safely falls back to mock without exposing internals.
- **Live MiMo verification:** performed in Round 14 when `MIMO_API_KEY` is configured locally; see Round 14 handoff for results.
- **Manual pasted diff only:** automatic GitHub PR fetching is not implemented.
- **Maximum diff size:** 20000 characters.
- Public API responses do not expose raw `diffText`, prompts, or model output.
- GitHub integration, Semgrep, and repository cloning are **not** implemented yet.

### Database Persistence v1

- `ReviewTask` is persisted to a file-based H2 database (`backend-java/data/codereviewx.mv.db`).
- `ReviewIssue` is persisted to the same database, linked to its parent `ReviewTask`.
- Data is not lost when the backend restarts.
- `issueSummary` is computed at response time from persisted `ReviewIssue` records — it is not stored as an independent table.
- `riskLevel` in `ReviewTaskResponse` is derived from `issueSummary.riskLevel` at response assembly time, not stored independently.

### Current Review Result Contract

The `ReviewTaskResponse` includes:

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

Each `ReviewIssueResponse` includes:

```json
{
  "source": "MOCK",
  "status": "OPEN"
}
```

`ReviewTaskResponse.riskLevel` is always consistent with `issueSummary.riskLevel`.

### Demo / Mock Data Notice

**Default mode uses deterministic mock data.** MiMo mode calls Xiaomi MiMo when configured. When `diffText` is provided, MiMo uses the pasted diff as primary review context. When `diffText` is absent, MiMo still operates with limited `repoUrl + prNumber` context only. Specifically:

- Default mock findings come from `MockReviewProvider` — not the result of repository analysis.
- MiMo mode with diff sends pasted unified diff plus repo metadata to Xiaomi MiMo.
- MiMo mode without diff sends limited context (`repoUrl`, `prNumber`) only.
- No GitHub API is called and no PR is fetched automatically.
- No repository is cloned or parsed.
- No Semgrep is executed.
- No `ai-service` integration exists yet.
- No status update workflow is implemented (resolve / false-positive are not implemented).
- No human reviewer workflow is implemented.

When MiMo mode fails, the system silently falls back to mock findings. Data is persisted to a local H2 database file. The frontend presents structured review summary and findings with provider source labels (Mock Provider / Xiaomi MiMo).

### Provider Configuration

```bash
# Default mock mode (no API key required)
cd backend-java && JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run

# Xiaomi MiMo mode (API key from environment only — never commit)
export MIMO_API_KEY="<local-secret-not-committed>"
cd backend-java && JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run \
  -Dspring-boot.run.arguments="--codereviewx.review.provider=mimo"
```

See [backend-java/README.md](backend-java/README.md) for full provider and fallback documentation.

See [tasks/round-12/demo-script.md](tasks/round-12/demo-script.md) for a step-by-step local demo script.

### Run Locally

Backend (mock mode default):

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev -- --host 127.0.0.1
```

Open [http://localhost:5173](http://localhost:5173). Create a review task with or without pasted diff.

### Known MVP Limitations

- No automatic GitHub PR fetching or GitHub App integration
- No private repository access
- No repository clone
- No full repository analysis
- No production-grade security scanning claims
- Mock provider is the safe default
- MiMo mode requires local `MIMO_API_KEY` configuration
- No PR comment write-back, RAG, MCP, Function Calling, or memory system
- No visual diff viewer or syntax highlighting
- No production auth/team model

### Post-MVP Roadmap (Future Work Only)

1. GitHub PR ingestion
2. Project rules / review policy
3. RAG / knowledge context
4. Function Calling / tool use
5. MCP integration
6. Memory system
7. PR comment workflow

### Database Configuration

Local/runtime (file-based H2, survives restarts):
```
jdbc:h2:file:./data/codereviewx
```

Tests (in-memory H2, isolated per test run):
```
jdbc:h2:mem:testdb
```

H2 console is available at `http://localhost:8080/h2-console` during local development.

### Mock API Quick Check

Create a demo review task:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/demo",
    "prNumber": 1
  }'
```

Create a review task with optional pasted diff:

```bash
curl -X POST http://localhost:8080/api/review-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/example/demo",
    "prNumber": 10,
    "diffText": "diff --git a/src/App.tsx b/src/App.tsx\n+const password = request.query.password;\n"
  }'
```

---

## Planned Architecture

Future rounds may introduce real repository ingestion, static analysis (Semgrep), LLM-assisted review, and human review workflows. These are not part of the current implementation.

### Out of Scope for Current Version

The following are not yet implemented:

- GitHub API integration
- Repository cloning or parsing
- Semgrep execution
- `ai-service` LLM calls or automatic GitHub ingestion (Xiaomi MiMo via `backend-java` and manual pasted diff are supported)
- Authentication / Spring Security
- Status update / resolve / false-positive workflow
- Production database (MySQL / PostgreSQL)

---

## Project Overview (Planned Product Vision)

> The following section describes the future product direction, not the current implementation.

CodeReviewX may evolve into a GitHub Pull Request code review system. In that future product direction, a user provides a repository URL and PR number, the system fetches the PR diff, runs static analysis with Semgrep, generates a structured review report via LLM, and displays the results in a web interface. These capabilities are not implemented in the current Manual Diff-Grounded AI Code Review Agent MVP.

### Future Product Goal

1. A user would input a GitHub repository URL and PR number.
2. `backend-java` would create a review task.
3. `backend-java` would call `ai-service`.
4. `ai-service` would fetch the GitHub PR diff.
5. `ai-service` would run Semgrep.
6. `ai-service` would call mock or real LLM review.
7. `ai-service` would return structured Review JSON.
8. `backend-java` would store the result.
9. `frontend` would display summary, risk level, and issue list.

---

## Planned Module Overview

| Module | Technology | Planned Responsibilities |
|---|---|---|
| `backend-java` | Spring Boot 3 + Java 17 | REST API, task lifecycle management, database persistence, calling ai-service |
| `ai-service` | Python + FastAPI | GitHub diff fetch, Semgrep execution, LLM review, structured JSON output |
| `frontend` | React + TypeScript + Vite | Task creation form, task list, task detail and review report display |
| `database` | H2 (current) / MySQL (planned) | Persistence of tasks and review issues |

---

## Repository Structure

```text
CodeReviewX/
├── README.md
├── docs/
│   ├── PRD.md
│   ├── ARCHITECTURE.md
│   ├── API.md
│   ├── DATABASE.md
│   ├── AGENT_RULES.md
│   └── HANDOFF_TEMPLATE.md
├── backend-java/
│   └── README.md
├── ai-service/
│   └── README.md
├── frontend/
│   └── README.md
├── .env.example
├── .gitignore
├── docker-compose.yml
└── .github/
    └── workflows/
        └── ci.yml
```

---

## Documentation Index

| Document | Description |
|---|---|
| [docs/PRD.md](docs/PRD.md) | Product Requirements Document |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System architecture and module design |
| [docs/API.md](docs/API.md) | REST API specification (planned) |
| [docs/DATABASE.md](docs/DATABASE.md) | Database schema design (planned) |
| [docs/AGENT_RULES.md](docs/AGENT_RULES.md) | Agent collaboration rules and role boundaries |
| [docs/HANDOFF_TEMPLATE.md](docs/HANDOFF_TEMPLATE.md) | Handoff report template for all Agents |

---

## Development Principles

1. **Documentation first.** No business code is written before the relevant PRD section, API design, and database design are documented and reviewed.
2. **MVP first.** No features outside the defined MVP scope are introduced.
3. **Mock first, real integration later.** The ai-service must work with a mock LLM before a real LLM is integrated.
4. **One coding Agent modifies files at a time.** Concurrent modifications to the same module by multiple Agents are not allowed.
5. **Architecture changes update documentation first.** Any change to module boundaries or API contracts requires updating the relevant document before any code change.
6. **All handoff files use Markdown.**

---

## Round Progress

| Round | Title | Status |
|---|---|---|
| Round 01 | Repository Foundation v1 | ✅ Complete |
| Round 02 | backend-java skeleton | ✅ Complete |
| Round 03 | backend-java mock API | ✅ Complete |
| Round 04 | Frontend ReviewTask Mock UI v1 | ✅ Complete |
| Round 05 | Review Result Visualization Mock v1 | ✅ Complete |
| Round 06 | Review Result Contract Hardening | ✅ Complete |
| Round 07 | Database Persistence v1 | ✅ Complete |
| Round 08 | Review Pipeline Orchestrator Skeleton | ✅ Complete |
| Round 09 | Xiaomi MiMo AI Review Provider v1 | ✅ Complete |
| Round 10 | PR/Diff Context v1 | ✅ Complete |
| Round 11 | Frontend Agent Result Presentation v1 | ✅ Complete |
| Round 12 | Final Hardening + Demo Readiness | ✅ Complete |
