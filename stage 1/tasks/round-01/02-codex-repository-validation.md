Task: Repository Validation v1

Suggested File Path: tasks/round-01/02-codex-repository-validation.md
Target Agent: Codex
Project: CodeReviewX
Round: Round 01: Repository Foundation v1
Task Type: Repository-level validation / minimal correction
Execution Mode: Validate first, fix minimally only when necessary
Business Logic Permission: Not allowed

⸻

1. Task Metadata

Field	Value
Task Name	Repository Validation v1
Target Agent	Codex
Project Name	CodeReviewX
Current Round	Round 01: Repository Foundation v1
Previous Agent	Cursor
Previous Handoff	handoff/round-01/01-cursor-handoff.md
Executor Role	Repository-level validator and minimal fixer
Reviewer After Completion	ChatGPT Architect
Next Possible Agent	Qoder, only after ChatGPT approval
Output Format	Markdown handoff report
Scope Type	Validation and minimal correction only

⸻

2. Role Definition

You are Codex, the repository-level validation and minimal-fix Agent for CodeReviewX.

Your responsibility in this round is to verify whether Cursor correctly completed Round 01: Repository Foundation v1.

You should validate repository structure, documentation completeness, placeholder configuration, scope compliance, and safety.

You may perform minimal corrections only if they are clearly required to satisfy Round 01 acceptance criteria.

You must not implement business logic, introduce new frameworks, create source code, or expand the project scope.

⸻

3. Project Background

CodeReviewX is an intelligent code review and repair suggestion Agent for GitHub Pull Requests.

The final MVP will include:

1. backend-java: Spring Boot 3 + Java 17 backend service.
2. ai-service: Python + FastAPI AI review service.
3. frontend: Vue 3 or React frontend.
4. Semgrep integration.
5. LLM-generated structured review result.
6. Docker Compose local startup.
7. GitHub Actions CI.

However, Round 01 does not implement runtime functionality.

Round 01 only establishes:

1. Repository structure.
2. Markdown documentation system.
3. Agent collaboration rules.
4. Handoff template.
5. Safe .env.example.
6. Safe .gitignore.
7. Placeholder docker-compose.yml.
8. Placeholder GitHub Actions CI.

⸻

4. Current Round Goal

Validate the repository foundation created by Cursor.

Your goal is to determine whether the repository is ready for ChatGPT Architect review and possible Qoder independent review.

You must check:

1. Whether all required Round 01 files exist.
2. Whether the documentation covers the required project scope.
3. Whether placeholder configuration files are safe and syntactically valid.
4. Whether no business code was introduced.
5. Whether no unapproved technology stack was introduced.
6. Whether no secrets or credentials were committed.
7. Whether the Cursor Handoff Report is consistent with the actual repository state.

⸻

5. Required Target Repository Structure

The required Round 01 structure is:

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

The following files may also exist as collaboration artifacts:

tasks/round-01/01-cursor-repository-foundation.md
tasks/round-01/02-codex-repository-validation.md
handoff/round-01/01-cursor-handoff.md

The following pre-existing root planning files may exist and should be treated carefully:

structure.md
CodeReviewX_PRD_v1.0.md
CodeReviewX_ARCHITECTURE_v1.0.md

Do not delete, move, or modify these pre-existing planning files unless absolutely necessary. Prefer reporting them as repository hygiene observations.

⸻

6. Allowed Scope

You may read and validate the entire repository.

You may create one Codex handoff report:

handoff/round-01/02-codex-handoff.md

You may minimally modify existing Round 01 files only if needed to fix clear acceptance criteria violations.

Allowed files for minimal correction:

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
handoff/round-01/02-codex-handoff.md

Minimal correction examples:

1. Fix broken Markdown headings.
2. Add missing “planned, not implemented” status.
3. Fix invalid placeholder YAML syntax.
4. Remove accidental real build steps from placeholder CI.
5. Remove accidental real service definitions from placeholder Docker Compose.
6. Fix unsafe .env.example values if any real-looking secrets are found.
7. Correct inaccurate documentation statements that imply Round 01 is already operational.

⸻

7. Forbidden Actions

Do not perform any of the following:

1. Do not create Spring Boot source code.
2. Do not create FastAPI source code.
3. Do not create frontend source code.
4. Do not create Maven, Gradle, npm, pnpm, pip, or requirements files.
5. Do not create database migration files.
6. Do not implement GitHub API integration.
7. Do not implement Semgrep integration.
8. Do not implement LLM integration.
9. Do not introduce Redis, MQ, RAG, vector database, Kubernetes, authentication, or GitHub App installation flow.
10. Do not add dependencies.
11. Do not run package installation commands.
12. Do not convert placeholder Docker Compose into real services.
13. Do not convert placeholder CI into real build/test jobs.
14. Do not remove pre-existing root planning files unless explicitly instructed by ChatGPT Architect.
15. Do not send the repository to Qoder directly.
16. Do not proceed to Round 02.
17. Do not modify files outside the allowed correction scope, except for creating your own handoff report.

⸻

8. Validation Checklist

8.1 Repository Structure Validation

Verify that all required files exist:

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

Also check whether the following expected collaboration artifacts exist:

tasks/round-01/01-cursor-repository-foundation.md
handoff/round-01/01-cursor-handoff.md

If they do not exist, report it. Do not create missing Cursor artifacts unless required for your own validation.

⸻

8.2 Cursor Handoff Validation

Read:

handoff/round-01/01-cursor-handoff.md

Validate whether it accurately reports:

1. Files created.
2. Files modified.
3. Scope compliance.
4. Acceptance criteria checklist.
5. Checks performed.
6. Known issues.
7. Deviations and corrections.
8. Recommended next step.

Specifically verify that the Handoff does not contradict the actual repository state.

⸻

8.3 Documentation Validation

Validate the following documents.

README.md

Must include:

1. Project name: CodeReviewX.
2. One-sentence description.
3. MVP goal.
4. Current round status: Round 01: Repository Foundation v1.
5. Module overview:
    * backend-java
    * ai-service
    * frontend
6. Repository structure.
7. Documentation index.
8. Development principles:
    * Documentation first.
    * MVP first.
    * Mock first, real integration later.
    * One coding Agent modifies files at a time.
9. Explicit statement that business logic is not implemented in Round 01.

docs/PRD.md

Must include:

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

docs/ARCHITECTURE.md

Must include:

1. System overview.
2. Module responsibilities.
3. Core processing flow.
4. ReviewTask status flow:

PENDING -> RUNNING -> SUCCESS
PENDING -> RUNNING -> FAILED

5. Boundary rules.
6. Round 01 architecture status.

docs/API.md

Must include planned APIs marked as not implemented:

POST /api/review-tasks
GET /api/review-tasks
GET /api/review-tasks/{id}
POST /review

Each API should have purpose, request/response example where applicable, and current status.

docs/DATABASE.md

Must include logical schema design only for:

review_task
review_file_change
review_issue

No SQL migration should be created in Round 01.

docs/AGENT_RULES.md

Must include:

1. ChatGPT role.
2. Cursor role.
3. Codex role.
4. Qoder role.
5. Collaboration principles.
6. Round transition rule.

docs/HANDOFF_TEMPLATE.md

Must include this 10-section structure:

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

Module READMEs

Validate:

backend-java/README.md
ai-service/README.md
frontend/README.md

Each must clearly state:

1. Planned module responsibility.
2. Current Round 01 status.
3. No business or page implementation in Round 01.

⸻

8.4 Configuration Validation

.env.example

Validate that it contains placeholders only.

Expected fields:

APP_ENV=local
BACKEND_PORT=8080
AI_SERVICE_PORT=8000
AI_SERVICE_BASE_URL=http://localhost:8000
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=codereviewx
MYSQL_USER=codereviewx
MYSQL_PASSWORD=change_me
GITHUB_TOKEN=replace_with_your_github_token
LLM_PROVIDER=mock
LLM_API_KEY=replace_with_your_llm_api_key

No real secrets may appear.

.gitignore

Validate that it protects:

1. .env
2. .env.*
3. !.env.example
4. IDE files.
5. Java build outputs.
6. Python cache and virtual environments.
7. Node build outputs.
8. Logs.
9. OS-generated files.

docker-compose.yml

Validate:

1. It is syntactically valid YAML.
2. It is placeholder only.
3. It does not define real application services.
4. It may use:

services: {}

.github/workflows/ci.yml

Validate:

1. It is syntactically valid YAML.
2. It runs on push to main and pull requests.
3. It does not perform real Maven, Gradle, pytest, npm, pnpm, Docker build, or deployment steps.
4. It only performs placeholder repository structure validation.

⸻

9. Suggested Commands

Run these commands from repository root.

9.1 Required File Existence Check

for f in README.md docs/PRD.md docs/ARCHITECTURE.md docs/API.md docs/DATABASE.md \
  docs/AGENT_RULES.md docs/HANDOFF_TEMPLATE.md backend-java/README.md \
  ai-service/README.md frontend/README.md .env.example .gitignore \
  docker-compose.yml .github/workflows/ci.yml; do
  [ -f "$f" ] && echo "✅ $f" || echo "❌ MISSING: $f"
done

9.2 Full File Listing

find . -type f | grep -v '/.git/' | sort

9.3 Business Code Scope Check

find . -name "*.java" -o -name "*.py" -o -name "*.js" -o -name "*.ts" -o -name "*.vue" -o -name "*.jsx" -o -name "*.tsx"

Expected result: no business source files.

If this returns files inside node_modules, .venv, or other ignored generated directories, report that the workspace is dirty and do not inspect generated dependency folders unless necessary.

9.4 Build/Dependency File Check

find . \( -name "pom.xml" -o -name "build.gradle" -o -name "build.gradle.kts" -o -name "package.json" -o -name "requirements.txt" -o -name "pyproject.toml" \)

Expected result: empty.

9.5 Secret Scan

grep -R "sk-\|ghp_\|github_pat_\|AKIA\|BEGIN PRIVATE KEY\|OPENAI_API_KEY=\|GITHUB_TOKEN=gh" . \
  --exclude-dir=.git \
  --exclude-dir=node_modules \
  --exclude-dir=.venv \
  --exclude-dir=venv || true

Expected result: no real secrets.

Placeholder values such as replace_with_your_github_token and replace_with_your_llm_api_key are acceptable.

9.6 Docker Compose Placeholder Check

cat docker-compose.yml

Confirm that it does not define real services.

9.7 CI Placeholder Check

cat .github/workflows/ci.yml

Confirm that it does not run real builds.

⸻

10. Minimal Fix Policy

If you find a small issue, fix it only when all conditions are true:

1. The issue is clearly within Round 01 scope.
2. The fix does not require new technology.
3. The fix does not require business logic.
4. The fix only touches allowed files.
5. The fix is smaller than a redesign.
6. The fix improves compliance with the existing task.

Examples of allowed minimal fixes:

1. Add missing “not implemented in Round 01” sentence.
2. Correct a Markdown link.
3. Change docker-compose.yml back to services: {}.
4. Remove real build commands from placeholder CI.
5. Replace suspicious .env.example value with a placeholder.
6. Add missing planned API status.

Examples of forbidden fixes:

1. Creating Spring Boot files.
2. Creating FastAPI files.
3. Creating frontend app files.
4. Adding SQL migrations.
5. Installing dependencies.
6. Reorganizing the entire documentation system.
7. Moving old planning files into docs/archive/ without ChatGPT Architect approval.

⸻

11. Acceptance Criteria

Your Codex task is accepted only if:

Validation Completeness

* All required Round 01 files are checked.
* Cursor Handoff Report is checked against actual repository state.
* Documentation coverage is checked.
* Placeholder configuration is checked.
* Business-code absence is checked.
* Secret safety is checked.
* Pre-existing out-of-scope files are reported if present.

Scope Compliance

* No Spring Boot business code is created.
* No FastAPI business code is created.
* No frontend page code is created.
* No database migration files are created.
* No GitHub API integration is implemented.
* No Semgrep integration is implemented.
* No LLM integration is implemented.
* No real Docker services are introduced.
* No real CI build jobs are introduced.
* No new dependencies are introduced.

Correction Discipline

* Any modification is minimal and justified.
* Any modification is listed in the Codex Handoff Report.
* If no modification is needed, the Handoff Report clearly says so.
* No out-of-scope files are modified.

Handoff Quality

* The Codex Handoff Report is written in Markdown.
* It clearly states pass/fail status.
* It lists commands executed and results.
* It lists any files modified by Codex.
* It lists known issues or repository hygiene concerns.
* It recommends returning to ChatGPT Architect.

⸻

12. Required Handoff Format

After validation, create and return a Markdown handoff report.

Recommended file:

handoff/round-01/02-codex-handoff.md

Use exactly this structure:

# Handoff Report: Codex Repository Validation v1
## 1. Task Metadata
- Project:
- Round:
- Task:
- Target Agent:
- Previous Agent:
- Execution Date:
- Repository Branch:
## 2. Validation Summary
State whether Round 01 repository foundation passed validation.
Use one of:
- PASS
- PASS_WITH_NOTES
- FAIL
## 3. Files Checked
List files checked by category:
- Required Round 01 files
- Collaboration artifacts
- Pre-existing out-of-scope files, if any
## 4. Files Modified by Codex
List every file modified by Codex.
If none, write:
`None. Validation only; no repository files were modified.`
## 5. Validation Results
Include subsections:
### 5.1 Repository Structure
### 5.2 Documentation Coverage
### 5.3 Configuration Safety
### 5.4 Scope Compliance
### 5.5 Cursor Handoff Consistency
### 5.6 Pre-existing File Hygiene
## 6. Commands Executed
List each command executed and summarize its result.
## 7. Issues Found
Classify each issue as:
- Blocking
- Non-blocking
- Repository hygiene note
If no issues are found, state:
`No blocking issues found.`
## 8. Minimal Fixes Applied
If fixes were applied, list:
- File
- Problem
- Fix
- Reason
If none, write:
`None.`
## 9. Remaining Risks or Limitations
Mention any remaining concerns, including but not limited to:
- Git not initialized.
- CI not actually executed on GitHub.
- Pre-existing planning files remain at root.
- Placeholder services are intentionally not runnable.
## 10. Recommendation
Choose one:
- Recommend ChatGPT Architect approve entry into Qoder review.
- Recommend returning to Cursor for correction.
- Recommend ChatGPT Architect decide on repository hygiene cleanup first.
Do not proceed to Qoder directly.

⸻

13. Final Instruction

Execute only this validation task.

Do not expand scope.

Do not implement business logic.

Do not introduce dependencies.

Do not proceed to Qoder.

After completion, return the full Markdown Handoff Report to the user so it can be reviewed by ChatGPT Architect.