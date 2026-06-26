# Task: Independent Architecture Review v1

**Suggested File Path:** `tasks/round-01/03-qoder-independent-review.md`  
**Target Agent:** Qoder  
**Project:** CodeReviewX  
**Round:** Round 01: Repository Foundation v1  
**Task Type:** Independent architecture and repository review  
**Execution Mode:** Review only  
**Code Modification Permission:** Not allowed  
**Business Logic Permission:** Not allowed  

---

## 1. Task Metadata

| Field | Value |
|---|---|
| Task Name | Independent Architecture Review v1 |
| Target Agent | Qoder |
| Project Name | CodeReviewX |
| Current Round | Round 01: Repository Foundation v1 |
| Previous Agents | Cursor, Codex |
| Previous Handoffs | `handoff/round-01/01-cursor-handoff.md`, `handoff/round-01/02-codex-handoff.md` |
| Executor Role | Independent architecture and repository reviewer |
| Reviewer After Completion | ChatGPT Architect |
| Next Possible Round | Round 02, only after ChatGPT Architect approval |
| Output Format | Markdown review report |
| Scope Type | Review only, no file modification |

---

## 2. Role Definition

You are Qoder, the independent architecture and repository review Agent for CodeReviewX.

Your responsibility is to independently review the Round 01 repository foundation after Cursor implementation and Codex validation.

You must not modify repository files.

You must not implement code.

You must not introduce new tools, frameworks, dependencies, services, or runtime behavior.

Your output should be a Markdown review report that helps ChatGPT Architect decide whether CodeReviewX can enter Round 02.

---

## 3. Project Background

CodeReviewX is an intelligent code review and repair suggestion Agent for GitHub Pull Requests.

The planned MVP flow is:

```text
User inputs repoUrl + prNumber
        ↓
backend-java creates ReviewTask
        ↓
backend-java calls ai-service
        ↓
ai-service fetches GitHub PR diff
        ↓
ai-service parses changed files
        ↓
ai-service runs Semgrep
        ↓
ai-service calls mock or real LLM
        ↓
ai-service returns structured Review JSON
        ↓
backend-java saves result
        ↓
frontend displays Review report
```

Planned module responsibilities:

### `backend-java`

Planned stack:

- Spring Boot 3
- Java 17
- MyBatis-Plus
- MySQL

Planned responsibilities:

- ReviewTask lifecycle management.
- REST API for frontend.
- Data persistence.
- Calling `ai-service`.
- Task status transitions.

Not responsible for:

- LLM prompt design.
- Semgrep execution.
- GitHub diff deep parsing.
- AI review generation.

### `ai-service`

Planned stack:

- Python
- FastAPI
- Semgrep
- LLM API

Planned responsibilities:

- Fetch GitHub PR diff.
- Parse PR file changes.
- Run Semgrep.
- Call mock or real LLM.
- Return structured Review JSON.

Not responsible for:

- MySQL persistence.
- Frontend page rendering.
- ReviewTask lifecycle management.

### `frontend`

Planned stack:

- Vue 3 or React, not finalized in Round 01.

Planned responsibilities:

- Create ReviewTask.
- Display task list.
- Display task detail.
- Display review summary, risk level, and issue list.

---

## 4. Round 01 Scope

Round 01 is named:

```text
Round 01: Repository Foundation v1
```

Round 01 only includes:

1. Repository structure.
2. Markdown documentation.
3. Agent collaboration rules.
4. Handoff template.
5. Safe `.env.example`.
6. Safe `.gitignore`.
7. Placeholder `docker-compose.yml`.
8. Placeholder GitHub Actions CI.

Round 01 explicitly does not include:

1. Spring Boot business code.
2. FastAPI business code.
3. Frontend page code.
4. Database migration.
5. GitHub API integration.
6. Semgrep integration.
7. LLM integration.
8. Real Docker service definitions.
9. Real CI builds.
10. Redis.
11. MQ.
12. RAG.
13. Vector database.
14. Kubernetes.
15. Authentication.
16. GitHub App installation flow.
17. Round 02 implementation.

---

## 5. Review Objectives

Your review must answer the following questions:

1. Is the Round 01 repository foundation structurally complete?
2. Are the documents sufficient to guide Round 02 implementation?
3. Are module boundaries clear enough?
4. Is the Agent collaboration model clear and enforceable?
5. Are the Cursor and Codex Handoff Reports credible and consistent?
6. Did Cursor or Codex introduce any scope creep?
7. Are placeholder files safe and correctly scoped?
8. Are there any architectural ambiguities that should be resolved before Round 02?
9. Are there any repository hygiene issues that should be cleaned before Round 02?
10. Should ChatGPT Architect approve entry into Round 02?

---

## 6. Files to Review

Review at least the following files.

### Required Round 01 files

```text
README.md
docs/PRD.md
docs/ARCHITECTURE.md
docs/API.md
docs/DATABASE.md
docs/AGENT_RULES.md
docs/HANDOFF_TEMPLATE.md
backend-java/README.md
ai-service/README.md
frontend/README.md
.env.example
.gitignore
docker-compose.yml
.github/workflows/ci.yml
```

### Task documents

```text
tasks/round-01/01-cursor-repository-foundation.md
tasks/round-01/02-codex-repository-validation.md
tasks/round-01/03-qoder-independent-review.md
```

### Handoff reports

```text
handoff/round-01/01-cursor-handoff.md
handoff/round-01/02-codex-handoff.md
```

If any listed file is missing, report it clearly.

---

## 7. Required Review Areas

### 7.1 Repository Structure

Check whether the repository matches the intended Round 01 structure:

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

Also note collaboration artifacts under:

```text
tasks/round-01/
handoff/round-01/
```

These are acceptable and should not be treated as business implementation.

---

### 7.2 Documentation Quality

Review whether the documentation provides enough guidance for Round 02.

Check especially:

1. Whether PRD defines product positioning, target users, MVP scope, out-of-scope items, user flow, issue categories, and success criteria.
2. Whether ARCHITECTURE defines system overview, module boundaries, core flow, and ReviewTask status flow.
3. Whether API defines planned endpoints and marks them as not implemented.
4. Whether DATABASE defines logical schema only and avoids implying real migrations.
5. Whether AGENT_RULES clearly defines ChatGPT, Cursor, Codex, and Qoder roles.
6. Whether HANDOFF_TEMPLATE is reusable and complete.
7. Whether module READMEs clearly state current non-implementation status.

---

### 7.3 Architecture Boundary Review

Review whether the module boundaries are clean.

Expected boundaries:

| Module | Owns | Must Not Own |
|---|---|---|
| `backend-java` | Task lifecycle, persistence, frontend-facing REST API, calling `ai-service` | LLM prompt, Semgrep execution, GitHub diff deep parsing |
| `ai-service` | GitHub diff fetch, file-change parsing, Semgrep, LLM/mock LLM, Review JSON generation | MySQL persistence, frontend rendering, task lifecycle |
| `frontend` | Task creation UI, task list UI, task detail/report UI | Business workflow orchestration, persistence, AI review generation |

Flag any ambiguity that may cause implementation conflict in Round 02.

---

### 7.4 API Contract Review

Review whether the documented planned APIs are reasonable for the MVP:

```text
POST /api/review-tasks
GET /api/review-tasks
GET /api/review-tasks/{id}
POST /review
```

Check whether:

1. The API names are consistent across README, ARCHITECTURE, and API docs.
2. Request and response examples match the ReviewTask and Review JSON model.
3. API docs clearly state that endpoints are planned and not implemented in Round 01.
4. The backend-to-ai-service boundary is clear.

Do not propose a large API redesign unless a serious architectural issue exists.

---

### 7.5 Data Model Review

Review whether the planned data model is sufficient for the MVP:

```text
review_task
review_file_change
review_issue
```

Check whether these fields are documented:

#### `review_task`

```text
id
repo_url
pr_number
status
summary
risk_level
error_message
created_at
updated_at
```

#### `review_file_change`

```text
id
task_id
file_path
change_type
additions
deletions
patch
created_at
```

#### `review_issue`

```text
id
task_id
file_path
line_number
type
severity
title
description
suggestion
source
created_at
```

Check whether the data model supports:

1. Task creation and status tracking.
2. File change persistence.
3. Issue persistence.
4. Error reporting.
5. Frontend report rendering.

Do not request advanced modeling such as users, tenants, audit logs, queues, or vector storage in Round 01.

---

### 7.6 Configuration and Security Review

Review:

1. `.env.example` contains placeholders only.
2. `.gitignore` prevents accidental commit of `.env`.
3. Docker Compose is placeholder-only.
4. CI is placeholder-only.
5. No secret-looking values are present.
6. No unnecessary dependency files are present.

---

### 7.7 Agent Process Review

Review whether the process is being followed:

```text
Cursor executes Repository Foundation v1
        ↓
Cursor returns Handoff Report
        ↓
ChatGPT Architect approves Codex validation
        ↓
Codex validates repository and applies minimal fixes
        ↓
Codex returns Handoff Report
        ↓
ChatGPT Architect decides whether to enter Qoder review
        ↓
Qoder performs independent review
        ↓
ChatGPT Architect makes final Round 01 decision
```

Check whether Cursor and Codex stayed within their roles.

---

## 8. Forbidden Actions

You must not:

1. Modify repository files.
2. Create new source code.
3. Create or update configuration files.
4. Add dependencies.
5. Run destructive commands.
6. Delete files.
7. Move files.
8. Initialize Git unless explicitly instructed.
9. Create branches.
10. Create commits.
11. Proceed to Round 02.
12. Assign work to Cursor or Codex directly.
13. Introduce new architecture not approved by ChatGPT Architect.

---

## 9. Suggested Read-Only Checks

You may run read-only commands such as:

```bash
find . -type f | grep -v '/.git/' | sort
```

```bash
find . -name "*.java" -o -name "*.py" -o -name "*.js" -o -name "*.ts" -o -name "*.vue" -o -name "*.jsx" -o -name "*.tsx"
```

```bash
find . \( -name "pom.xml" -o -name "build.gradle" -o -name "build.gradle.kts" -o -name "package.json" -o -name "requirements.txt" -o -name "pyproject.toml" \)
```

```bash
grep -R "sk-\|ghp_\|github_pat_\|AKIA\|BEGIN PRIVATE KEY\|OPENAI_API_KEY=\|GITHUB_TOKEN=gh" . \
  --exclude-dir=.git \
  --exclude-dir=node_modules \
  --exclude-dir=.venv \
  --exclude-dir=venv || true
```

```bash
cat docker-compose.yml
```

```bash
cat .github/workflows/ci.yml
```

Do not run install, build, migration, Docker startup, or code generation commands.

---

## 10. Severity Classification

Classify findings as follows:

### Blocking

A finding is blocking if it prevents Round 01 from being accepted.

Examples:

1. Required file missing.
2. Business source code exists.
3. Real secrets exist.
4. Real Docker services were introduced.
5. Real CI build/deploy pipeline was introduced.
6. Documentation omits critical architecture boundaries.
7. Agent role rules are missing or contradictory.

### Non-blocking

A finding is non-blocking if it should be improved but does not prevent Round 01 acceptance.

Examples:

1. Minor wording inconsistency.
2. Small API naming inconsistency.
3. Missing cross-link between docs.
4. Some examples could be clearer.
5. Placeholder comments could be more explicit.

### Repository Hygiene Note

A finding is repository hygiene if it concerns cleanliness but not Round 01 validity.

Examples:

1. Git not initialized.
2. GitHub Actions not executed remotely.
3. Old planning docs remain at root.
4. Task and handoff artifacts need future archival policy.

---

## 11. Required Output

Return a Markdown review report.

Recommended file:

```text
handoff/round-01/03-qoder-review.md
```

Use exactly this structure:

```markdown
# Review Report: Qoder Independent Review v1

## 1. Review Metadata

- Project:
- Round:
- Review Task:
- Target Agent:
- Previous Agents:
- Review Date:
- Repository Branch:

## 2. Executive Summary

Use one of:

- APPROVE_ROUND_01
- APPROVE_WITH_NOTES
- REQUEST_FIXES
- REJECT

Briefly explain the decision.

## 3. Files Reviewed

List reviewed files by category:

- Required Round 01 files
- Task documents
- Handoff reports
- Other files observed

## 4. Architecture Review

Assess module boundaries, responsibility split, and Round 02 readiness.

## 5. Documentation Review

Assess README, PRD, Architecture, API, Database, Agent Rules, Handoff Template, and module READMEs.

## 6. Scope Compliance Review

State whether any business logic, dependency, runtime implementation, real Docker service, real CI build, or unapproved technology was introduced.

## 7. Handoff Consistency Review

Assess Cursor and Codex Handoff Reports.

Mention any inconsistencies, especially:

- Cursor mentioned old root planning files.
- Codex reported they were not present in the current workspace.
- Git is not initialized.

## 8. Findings

Group findings by severity:

### 8.1 Blocking Findings

### 8.2 Non-blocking Findings

### 8.3 Repository Hygiene Notes

## 9. Recommendations Before Round 02

Give practical recommendations only.

Do not recommend large architecture expansion.

Focus on what should be clarified before implementation begins.

## 10. Final Recommendation

Choose one:

- Recommend ChatGPT Architect approve Round 01 completion and enter Round 02.
- Recommend ChatGPT Architect request targeted fixes before Round 02.
- Recommend ChatGPT Architect reject Round 01 and return to Cursor/Codex.

Do not proceed to Round 02 directly.
```

---

## 12. Acceptance Criteria

Your Qoder review is accepted only if:

- [ ] You do not modify repository files.
- [ ] You review all required Round 01 files.
- [ ] You review Cursor and Codex handoff reports.
- [ ] You identify blocking, non-blocking, and hygiene findings separately.
- [ ] You evaluate whether Round 01 scope was respected.
- [ ] You evaluate whether documentation is sufficient for Round 02.
- [ ] You evaluate whether architecture boundaries are clear.
- [ ] You provide a clear final recommendation.
- [ ] You do not assign work to Cursor, Codex, or Round 02 directly.
- [ ] You return the report to ChatGPT Architect.

---

## 13. Final Instruction

Execute this review-only task.

Do not modify files.

Do not implement code.

Do not introduce dependencies.

Do not proceed to Round 02.

Return the complete Markdown review report to the user for ChatGPT Architect review.