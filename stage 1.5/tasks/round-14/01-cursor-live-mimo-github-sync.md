# Cursor Task: Round 14 Live MiMo Verification + Git/GitHub Repository Sync

## 1. Task Metadata

```text
Project: CodeReviewX
Round: Round 14
Agent: Cursor
Task: Live MiMo verification, repository hygiene, Git initialization, GitHub sync
Input Document:
  stage 1.5/tasks/round-14/00-round-14-start.md
Expected Handoff:
  stage 1.5/handoff/round-14/01-cursor-live-mimo-github-sync-handoff.md
```

## 2. Architect Instructions

Architect has confirmed:

```text
MIMO_API_KEY is available locally.
GitHub repository is already created and named CodeReviewX.
Cursor is allowed to execute Git initialization and GitHub push.
```

Cursor owns concrete execution for this round.

Qoder will independently review the final state afterward.

## 3. Primary Objective

Make the productized Stage 1.5 MVP ready for real provider use and GitHub-backed development.

Target outcome:

```text
1. Xiaomi MiMo live path is verified safely with local MIMO_API_KEY.
2. Mock fallback remains safe.
3. Repository is cleanly initialized with Git.
4. Generated artifacts and secrets are excluded.
5. CodeReviewX is pushed to the GitHub repository.
6. A Stage 1/1.5 release marker exists through tags/branches.
```

## 4. Strict Security Rules

Do not violate any of these:

```text
Never commit MIMO_API_KEY
Never echo or print MIMO_API_KEY in terminal output
Never write MIMO_API_KEY into docs, code, handoff, commit message, or screenshots
Never commit .env or .env.local
Never commit backend-java/data/*.db
Never commit backend-java/target/
Never commit frontend/node_modules/
Never commit frontend/dist/
Never expose raw prompt or raw model output
Never expose raw diffText in public API response
Never add GitHub tokens to files or handoffs
```

If a command requires the API key, pass it through the existing local environment. Do not display its value.

## 5. Files to Inspect First

Read before changing anything:

```text
stage 1.5/start.md
stage 1.5/tasks/round-14/00-round-14-start.md
stage 1.5/handoff/round-13/03-qoder-product-ui-restyle-independent-review-handoff.md
README.md
backend-java/README.md
frontend/README.md
docs/AGENT_RULES.md
backend-java/src/main/resources/application.yml
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/ConfigurableReviewProvider.java
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoClient.java
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/XiaomiMiMoFindingParser.java
frontend/src/components/StatusWidget.tsx
frontend/src/types/ui.ts
```

## 6. Required Work

### 6.1 Repository Hygiene Preparation

Confirm or create/update:

```text
.gitignore
.env.example if useful
README provider/GitHub wording if stale
backend-java/README MiMo instructions if stale
frontend/README if provider/status wording is stale
```

`.gitignore` must exclude at minimum:

```text
.env
.env.*
!.env.example
frontend/node_modules/
frontend/dist/
backend-java/target/
backend-java/data/*.db
backend-java/data/*.mv.db
backend-java/data/*.trace.db
.DS_Store
*.log
```

Do not delete useful docs or handoffs.

### 6.2 Live MiMo Verification

Start backend in MiMo mode with the local key from environment.

Use the existing configuration mechanism:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run \
  -Dspring-boot.run.arguments="--codereviewx.review.provider=mimo"
```

Verify:

```text
GET /api/health works
POST /api/review-tasks metadata-only works
POST /api/review-tasks with diffText works
Successful MiMo responses, if returned, produce structured findings with source=MIMO
Zero findings, if returned, are handled without UI/API breakage
Failure fallback still returns safe mock findings with source=MOCK
Public API response does not include diffText, prompt, model output, or key
Logs do not print key, raw prompt, or raw model output
```

If the live MiMo provider returns unstable or invalid output:

```text
Apply only the smallest safe parser/config/error-handling fix.
Do not redesign provider architecture.
Do not change public API contract.
Document exact behavior and whether fallback occurred.
```

### 6.3 Local Validation

Run:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

Run:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

If tests fail, apply the smallest safe fix.

### 6.4 Runtime Frontend Smoke

With backend running:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Smoke:

```text
frontend loads
backend status visible
metadata-only create works
diff-grounded create works
review history/detail works
provider labels remain honest
no browser console errors
mobile viewport remains usable
```

### 6.5 Secret and Artifact Audit Before Git

Before `git add`, audit:

```text
No .env or .env.local tracked
No MIMO_API_KEY value in source/docs/handoffs
No frontend/node_modules
No frontend/dist
No backend-java/target
No backend-java/data database files
No raw prompt/model output logs
No GitHub token
```

Recommended commands:

```bash
find . -name '.env' -o -name '.env.local'
find frontend -maxdepth 2 -name node_modules -o -name dist
find backend-java -maxdepth 3 -name target -o -path '*/data/*'
git status --short
```

Use secret scanning patterns, but treat placeholder strings such as `<local-secret-not-committed>` as non-secret only if clearly placeholders.

### 6.6 Git Initialization and GitHub Sync

If this directory is not yet a Git repository:

```bash
git init
git branch -M main
```

Add remote:

```bash
git remote add origin <GitHub CodeReviewX repository URL>
```

If the remote already exists, verify it points to the CodeReviewX repository.

Commit strategy:

```text
Initial clean commit should represent the completed Stage 1.5 productized MVP state.
Commit message should be clear, for example:
  chore: initialize CodeReviewX productized MVP
```

Suggested tags:

```bash
git tag v0.1.0-stage1-mvp
git tag v0.2.0-productized-mvp
```

Push:

```bash
git push -u origin main
git push origin v0.1.0-stage1-mvp v0.2.0-productized-mvp
```

Branch setup:

```bash
git checkout -b stage-1-5-productization
git push -u origin stage-1-5-productization
```

If GitHub remote authentication fails, do not retry in a loop and do not print tokens. Document the exact non-secret error and stop for architect/user action.

## 7. Explicit Non-Goals

Do not implement:

```text
GitHub PR ingestion
GitHub OAuth
GitHub App installation
private repository access
repository clone
full repository analysis
PR comment write-back
RAG
MCP
Function Calling
Memory
auth/team model
production deployment
CI/CD pipeline
database migration
large backend architecture change
large frontend redesign
```

Round 14 is provider verification and repository synchronization only.

## 8. Expected Handoff

Create:

```text
stage 1.5/handoff/round-14/01-cursor-live-mimo-github-sync-handoff.md
```

Include:

```text
summary
files changed
commands run and exact pass/fail results
live MiMo verification result
fallback verification result
API response shape summary without secrets
browser/runtime smoke result
secret/artifact audit result
git init/commit/tag/branch/push evidence
remote repository URL without tokens
known limitations
confirmation no Stage 2 features were added
issues for Qoder to verify
```

Never include the real `MIMO_API_KEY`.

