Task: Repository Foundation v1

Suggested File Path: tasks/round-01/01-cursor-repository-foundation.md
Target Agent: Cursor
Project: CodeReviewX
Round: Round 01: Repository Foundation v1
Task Type: Repository foundation / documentation scaffold
Execution Mode: File creation and repository initialization only
Coding Permission: Limited to repository structure and placeholder files
Business Logic Permission: Not allowed

⸻

1. Task Metadata

Field	Value
Task Name	Repository Foundation v1
Target Agent	Cursor
Project Name	CodeReviewX
Current Round	Round 01
Executor Role	Primary programming execution agent
Reviewer After Completion	ChatGPT Architect
Next Possible Agent	Codex, only after ChatGPT approval
Output Format	Markdown handoff report
Scope Type	Repository structure and documentation only

⸻

2. Role Definition

You are Cursor, the primary coding execution Agent for CodeReviewX.

Your responsibility in this round is to create the initial repository foundation for the project. You should create directories, Markdown documents, placeholder configuration files, and collaboration rules according to the architecture context.

You must not implement backend, AI service, frontend, database migration, GitHub API integration, Semgrep execution, LLM invocation, or real CI build logic in this round.

This round is about establishing a clean, reviewable, and extensible project skeleton.

⸻

3. Project Background

CodeReviewX is an intelligent code review and repair suggestion Agent for GitHub Pull Requests.

The final MVP goal is:

1. User inputs a GitHub repository URL and PR number.
2. backend-java creates a review task.
3. backend-java calls ai-service.
4. ai-service fetches GitHub PR diff.
5. ai-service parses changed files.
6. ai-service runs Semgrep.
7. ai-service calls mock or real LLM.
8. ai-service returns structured Review JSON.
9. backend-java stores task result.
10. frontend displays review summary, risk level, and issue list.

However, this round does not implement the above runtime logic.

This round only prepares the repository foundation and Markdown documentation system.

⸻

4. Current Round Goal

Create the first version of the CodeReviewX repository foundation.

The repository must contain:

1. A clear root-level README.md.
2. A complete docs/ documentation structure.
3. Basic module directories:
    * backend-java/
    * ai-service/
    * frontend/
4. Agent collaboration rules.
5. A handoff report template.
6. Safe environment variable example file.
7. Safe .gitignore.
8. Placeholder docker-compose.yml.
9. Placeholder GitHub Actions workflow.

The result should be suitable for architectural review before any business code is written.

⸻

5. Required Target Repository Structure

Create the following structure exactly:

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

If the repository already contains some files, update them only if they are part of this required structure and only to align them with this task.

⸻

6. Allowed Scope

You may create or update only the following:

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

You may create missing directories required for those files.

You may write descriptive Markdown content.

You may create placeholder configuration content, but it must be clearly marked as placeholder.

⸻

7. Forbidden Actions

Do not perform any of the following:

1. Do not create Spring Boot source code.
2. Do not create FastAPI source code.
3. Do not create frontend app source code.
4. Do not create database migration scripts.
5. Do not implement GitHub API calls.
6. Do not implement Semgrep execution.
7. Do not implement LLM calls.
8. Do not add Redis, MQ, RAG, vector database, Kubernetes, or authentication.
9. Do not introduce unapproved dependencies.
10. Do not create real Docker service images.
11. Do not create a real production-ready CI pipeline.
12. Do not add secrets, API keys, tokens, passwords, or private credentials.
13. Do not modify files outside the allowed scope.
14. Do not split this task into smaller subtasks.
15. Do not hand off to Codex or Qoder directly.

⸻

8. Required File Content Guidelines

8.1 Root README.md

The root README.md should include:

1. Project name: CodeReviewX.
2. One-sentence description.
3. MVP goal.
4. Current round status: Round 01: Repository Foundation v1.
5. Planned module overview:
    * backend-java
    * ai-service
    * frontend
6. Current repository structure.
7. Documentation index linking to files in docs/.
8. Development principles:
    * Documentation first.
    * MVP first.
    * Mock first, real integration later.
    * One coding Agent modifies files at a time.
9. Explicit statement that business logic is not implemented in Round 01.

⸻

8.2 docs/PRD.md

Create a concise Product Requirements Document.

It should include:

1. Product positioning.
2. Target users.
3. MVP problem statement.
4. MVP scope.
5. Out-of-scope items.
6. Core user flow.
7. Review issue categories:
    * Bug
    * Security
    * Performance
    * Test
    * Style
8. Success criteria for MVP.

Do not include implementation code.

⸻

8.3 docs/ARCHITECTURE.md

Create the initial architecture design document.

It should include:

1. System overview.
2. Module responsibilities:
    * backend-java
    * ai-service
    * frontend
3. Core processing flow.
4. ReviewTask status flow:

PENDING -> RUNNING -> SUCCESS
PENDING -> RUNNING -> FAILED

5. Boundary rules:
    * backend-java manages task lifecycle and persistence.
    * ai-service handles GitHub diff, Semgrep, and LLM review.
    * frontend handles user interaction and report display.
6. Round 01 architecture status:
    * Documentation and placeholders only.
    * No runtime implementation yet.

⸻

8.4 docs/API.md

Create an initial API design placeholder.

It should include the planned MVP API list, but mark all APIs as planned and not implemented.

Planned APIs:

POST /api/review-tasks
GET /api/review-tasks
GET /api/review-tasks/{id}

For each API, include:

1. Purpose.
2. Request example if applicable.
3. Response example if applicable.
4. Current status: Planned, not implemented in Round 01.

Also include planned backend-to-ai-service API:

POST /review

Mark it as planned and not implemented.

⸻

8.5 docs/DATABASE.md

Create an initial database design document.

It should include planned tables:

1. review_task
2. review_file_change
3. review_issue

For each table, include field names and purpose.

Use the following fields.

review_task

id
repo_url
pr_number
status
summary
risk_level
error_message
created_at
updated_at

review_file_change

id
task_id
file_path
change_type
additions
deletions
patch
created_at

review_issue

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

Do not create SQL migration files in this round.

Clearly mark this document as logical schema design only.

⸻

8.6 docs/AGENT_RULES.md

Create the Agent collaboration rules document.

It must include:

1. Agent role boundaries:
    * ChatGPT: architect, planner, reviewer, decision maker.
    * Cursor: primary coding execution Agent.
    * Codex: repository-level validation, testing, and minimal fixes.
    * Qoder: independent architecture and code reviewer.
2. Collaboration principles:
    * Documentation first.
    * MVP first.
    * Mock first, real LLM later.
    * One coding Agent modifies files at a time.
    * Architecture changes must update documentation first.
    * No unapproved scope expansion.
    * All handoff files use Markdown.
3. Round transition rule:
    * Cursor completes task.
    * Cursor submits handoff report.
    * ChatGPT decides whether to enter Codex validation.
    * Codex submits handoff report.
    * ChatGPT decides whether to enter Qoder review.
    * Qoder submits review report.
    * ChatGPT makes final round decision.

⸻

8.7 docs/HANDOFF_TEMPLATE.md

Create a reusable handoff report template.

It must include the following sections:

# Handoff Report
## 1. Task Metadata
## 2. Execution Summary
## 3. Files Created
## 4. Files Modified
## 5. Scope Compliance
## 6. Acceptance Criteria Checklist
## 7. Checks Performed
## 8. Known Issues or Limitations
## 9. Deviations from Task
## 10. Recommended Next Step

Include checklist examples under relevant sections.

⸻

8.8 backend-java/README.md

Create a module README.

It should state:

1. This module will be the Spring Boot 3 + Java 17 backend service.
2. Planned responsibilities:
    * ReviewTask task management.
    * REST API for frontend.
    * Persistence through MySQL.
    * Calling ai-service.
3. Explicitly state that no backend business code is implemented in Round 01.

⸻

8.9 ai-service/README.md

Create a module README.

It should state:

1. This module will be the Python + FastAPI AI review service.
2. Planned responsibilities:
    * Fetch GitHub PR diff.
    * Parse changed files.
    * Run Semgrep.
    * Call mock or real LLM.
    * Return structured Review JSON.
3. Explicitly state that no AI service business code is implemented in Round 01.

⸻

8.10 frontend/README.md

Create a module README.

It should state:

1. This module will be the frontend application.
2. Framework is not strictly finalized yet: Vue 3 or React.
3. Planned responsibilities:
    * Create review task.
    * Display task list.
    * Display task details.
    * Display summary, risk level, and issue list.
4. Explicitly state that no frontend page implementation is included in Round 01.

⸻

8.11 .env.example

Create a safe environment example file.

It may include placeholders only:

# Application
APP_ENV=local
# Backend Java
BACKEND_PORT=8080
# AI Service
AI_SERVICE_PORT=8000
AI_SERVICE_BASE_URL=http://localhost:8000
# Database
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=codereviewx
MYSQL_USER=codereviewx
MYSQL_PASSWORD=change_me
# GitHub
GITHUB_TOKEN=replace_with_your_github_token
# LLM
LLM_PROVIDER=mock
LLM_API_KEY=replace_with_your_llm_api_key

Do not add real secrets.

⸻

8.12 .gitignore

Create a safe .gitignore for a multi-module Java, Python, frontend, and local environment project.

It should include at least:

# Environment
.env
.env.*
!.env.example
# IDE
.idea/
.vscode/
*.iml
# Java / Maven / Gradle
target/
build/
.gradle/
out/
# Python
__pycache__/
*.py[cod]
.venv/
venv/
.pytest_cache/
.mypy_cache/
# Node / Frontend
node_modules/
dist/
coverage/
# Logs
*.log
logs/
# OS
.DS_Store
Thumbs.db

⸻

8.13 docker-compose.yml

Create a placeholder Docker Compose file.

Requirements:

1. Use a valid Compose YAML structure.
2. Do not define real application services yet.
3. Include comments explaining that real services will be added in later rounds.
4. It may contain a placeholder network or commented examples only.

Acceptable direction:

services: {}
# Real services will be added in later rounds:
# - backend-java
# - ai-service
# - frontend
# - mysql

Make sure the file is syntactically valid.

⸻

8.14 .github/workflows/ci.yml

Create a placeholder GitHub Actions workflow.

Requirements:

1. It should be valid YAML.
2. It should run on pull requests and pushes to main.
3. It should not attempt real builds yet.
4. It should contain a placeholder validation step, such as checking repository structure or echoing current round status.

Example direction:

name: CI
on:
  push:
    branches:
      - main
  pull_request:
jobs:
  placeholder:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Repository foundation placeholder
        run: |
          echo "Round 01: Repository Foundation v1"
          test -f README.md
          test -f docs/PRD.md
          test -f docs/ARCHITECTURE.md
          test -f docs/API.md
          test -f docs/DATABASE.md
          test -f docs/AGENT_RULES.md
          test -f docs/HANDOFF_TEMPLATE.md

⸻

9. Review JSON Standard to Document

The architecture or API documentation should include the planned standard Review JSON shape:

{
  "summary": "This PR introduces several potential issues.",
  "riskLevel": "LOW | MEDIUM | HIGH",
  "files": [
    {
      "filePath": "src/main/java/example/UserService.java",
      "changeType": "modified",
      "additions": 20,
      "deletions": 5,
      "patch": "@@ -1,5 +1,10 @@"
    }
  ],
  "issues": [
    {
      "type": "BUG | SECURITY | PERFORMANCE | TEST | STYLE",
      "severity": "LOW | MEDIUM | HIGH",
      "filePath": "src/main/java/example/UserService.java",
      "line": 42,
      "title": "Potential null pointer exception",
      "description": "The variable may be null before use.",
      "suggestion": "Add a null check before accessing the field.",
      "source": "LLM | SEMGREP"
    }
  ]
}

Mark this as planned contract, not implemented in Round 01.

⸻

10. Acceptance Criteria

The task is accepted only if all of the following are true:

Repository Structure

* Root README.md exists.
* docs/PRD.md exists.
* docs/ARCHITECTURE.md exists.
* docs/API.md exists.
* docs/DATABASE.md exists.
* docs/AGENT_RULES.md exists.
* docs/HANDOFF_TEMPLATE.md exists.
* backend-java/README.md exists.
* ai-service/README.md exists.
* frontend/README.md exists.
* .env.example exists.
* .gitignore exists.
* docker-compose.yml exists.
* .github/workflows/ci.yml exists.

Scope Control

* No Spring Boot business code is created.
* No FastAPI business code is created.
* No frontend page code is created.
* No database migration files are created.
* No GitHub API integration is implemented.
* No Semgrep integration is implemented.
* No LLM integration is implemented.
* No secrets are committed.
* No unapproved technology is introduced.

Documentation Quality

* Root README clearly explains the project and current round.
* PRD defines MVP scope and out-of-scope items.
* Architecture document defines module boundaries.
* API document marks all APIs as planned.
* Database document marks schema as logical design only.
* Agent rules document defines role boundaries and handoff rules.
* Handoff template is reusable by future Agents.
* Module README files clearly state planned responsibilities and current non-implementation status.

Configuration Quality

* .env.example contains placeholders only.
* .gitignore protects local secrets and generated files.
* docker-compose.yml is syntactically valid placeholder YAML.
* ci.yml is syntactically valid placeholder workflow YAML.

⸻

11. Suggested Checks

After completing the task, perform these checks manually or through shell commands:

find . -maxdepth 3 -type f | sort

Verify that the required files exist.

Check that no unexpected source files were created:

find . -type f | sort

Confirm that no secrets were added:

grep -R "sk-\|ghp_\|github_pat_\|AKIA\|BEGIN PRIVATE KEY" . || true

Optional YAML syntax checks may be performed if tools are available, but do not introduce new dependencies just for this round.

⸻

12. Required Handoff Format

After completion, output a Markdown handoff report using this structure:

# Handoff Report: Repository Foundation v1
## 1. Task Metadata
- Project:
- Round:
- Task:
- Target Agent:
- Execution Date:
- Repository Branch:
## 2. Execution Summary
Briefly summarize what was created or updated.
## 3. Files Created
List all created files.
## 4. Files Modified
List all modified files. If none existed before and all were newly created, state that clearly.
## 5. Scope Compliance
Confirm that no business logic was implemented.
## 6. Acceptance Criteria Checklist
Copy the acceptance criteria checklist and mark each item as completed or not completed.
## 7. Checks Performed
List commands or manual checks performed.
## 8. Known Issues or Limitations
List any known issues. If none, state `None`.
## 9. Deviations from Task
List any deviations. If none, state `None`.
## 10. Recommended Next Step
Recommend returning the handoff report to ChatGPT Architect for review.

Do not send the repository to Codex directly. The next step must be decided by ChatGPT Architect.

⸻

13. Final Instruction

Execute only this task.

Do not expand the scope.

Do not implement business logic.

Do not create extra modules.

Do not introduce new tools or dependencies.

After completion, provide the required Markdown handoff report.