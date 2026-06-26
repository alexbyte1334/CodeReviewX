# CodeReviewX Agent Collaboration Rules v1.0

> This document defines the role boundaries, collaboration principles, file conventions,
> and round transition rules for all Agents participating in CodeReviewX.
> All Agents must read and follow this document.

---

## 1. Agent Role Boundaries

| Agent | Role | Core Responsibilities |
|---|---|---|
| **ChatGPT** | Project Architect | Requirements, architecture, review standards, final decisions |
| **Cursor** | Primary coding execution Agent | Single file/module code generation, small bug fixes, individual page creation |
| **Codex** | Repository-level validation | Repository-wide modifications, running tests, fixing CI, minimal targeted fixes |
| **Qoder** | Independent reviewer | Architecture review, code review, risk identification, comparing approaches |

**Important:** Cursor is the primary execution Agent for feature coding rounds. Codex validates but does not lead feature implementation. Qoder reviews but does not modify code.

---

## 2. Collaboration Principles

1. **Documentation first.** No business code is written before the PRD section, API design, and database design exist and are reviewed.
2. **MVP first.** No features outside the defined MVP scope are introduced without a PRD update.
3. **Mock first, real LLM later.** ai-service must first work with mock LLM before real LLM is integrated.
4. **One coding Agent modifies files at a time.** Concurrent modifications to the same module are not allowed.
5. **Architecture changes update documentation first.** Any change to module boundaries or API contracts requires updating the relevant document before code changes.
6. **No unapproved scope expansion.** Agents must not add features, dependencies, or modules not defined in the current PRD.
7. **All handoff files use Markdown.**
8. **Agents do not hand off directly to each other.** ChatGPT Architect decides the next Agent for every round.

---

## 3. Round Transition Rule

Every round follows this handoff sequence:

```text
Cursor completes task
        ↓
Cursor submits Handoff Report to ChatGPT Architect
        ↓
ChatGPT Architect reviews Handoff Report and decides
        ↓
(If approved) ChatGPT assigns Codex validation task
        ↓
Codex submits Handoff Report to ChatGPT Architect
        ↓
(If needed) ChatGPT assigns Qoder review task
        ↓
Qoder submits Review Report to ChatGPT Architect
        ↓
ChatGPT Architect makes final round decision
        ↓
Next round begins (if approved)
```

**No Agent hands off directly to another Agent without ChatGPT Architect approval.**

---

## 4. Allowed File Scope per Agent

### Cursor (per-task scope, defined in task document)

Typically allowed:
- Single controller / service / mapper / entity / DTO files
- Single frontend page components
- Single test files
- Specific `docs/` files as listed in the task

Typically not allowed without explicit task permission:
- `docker-compose.yml`
- `.github/workflows/ci.yml`
- Database migration files
- Files unrelated to the current task

### Codex

- Repository-wide modifications as explicitly defined in the task
- Running tests and fixing failures
- `docker-compose.yml` and CI files when explicitly assigned

Codex must not:
- Delete existing working logic
- Introduce unapproved technology stacks
- Modify `docs/PRD.md` or `docs/ARCHITECTURE.md` (only ChatGPT Architect updates these)

### Qoder

- Read-only review
- Outputs review reports only, does not modify code

---

## 5. File and Format Conventions

All Agent-to-Agent files must use Markdown format.

| Use | Format |
|---|---|
| PRD, architecture design, API design, database design | Markdown |
| Task prompt documents | Markdown |
| Handoff reports | Markdown |
| Final archive for external reading | PDF (manually exported, not for Agent use) |

**Naming conventions:**

| File Type | Pattern | Example |
|---|---|---|
| Task document | `tasks/round-<NN>/<NN>-<agent>-<description>.md` | `tasks/round-02/02-codex-backend-skeleton.md` |
| Handoff report | `handoff/round-<NN>/<NN>-<agent>-handoff.md` | `handoff/round-01/01-cursor-handoff.md` |

---

## 6. Change Management

### What counts as a requirement change

The following require a PRD update before any code change:

1. Adding new features
2. Removing defined features
3. Changing module responsibilities
4. Changing core database structure
5. Changing API contracts
6. Introducing new middleware
7. Changing deployment approach
8. Changing Agent assignments
9. Changing Agent file format conventions

**Change process:**

```text
Propose change → ChatGPT Architect evaluates → Update PRD or architecture doc → Then code
```

### What requires Architect approval before coding

- Adding a message queue (RabbitMQ, Kafka)
- Adding Redis
- Adding Kubernetes
- Adding a vector database
- Adding microservices
- Changing Java/Python service boundary
- Changing LLM invocation approach
- Changing database technology

---

## 7. Security Rules

1. GitHub Token must never be hardcoded in source files.
2. LLM API Key must never be committed to the repository.
3. `.env` must never be committed (`.gitignore` is configured to exclude it).
4. Only `.env.example` with placeholders may be committed.
5. Log output must never include complete tokens or API keys.
6. No credentials may appear in code comments.
