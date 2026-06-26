# Qoder Task: Round 14 Live MiMo + GitHub Sync Independent Review

## 1. Task Metadata

```text
Project: CodeReviewX
Round: Round 14
Agent: Qoder
Task: Independent review of live MiMo verification and Git/GitHub synchronization
Input Documents:
  stage 1.5/tasks/round-14/00-round-14-start.md
  stage 1.5/tasks/round-14/01-cursor-live-mimo-github-sync.md
  stage 1.5/handoff/round-14/01-cursor-live-mimo-github-sync-handoff.md
Expected Handoff:
  stage 1.5/handoff/round-14/02-qoder-live-mimo-github-sync-independent-review-handoff.md
Target Final Verdict:
  QODER_ROUND_14_APPROVED_LIVE_MIMO_GITHUB_SYNC
```

## 2. Primary Objective

Independently decide whether Round 14 can be closed.

Final decision must answer:

```text
Is CodeReviewX safely verified with live MiMo where possible, protected against secret/artifact exposure, initialized in Git, and synchronized to the GitHub CodeReviewX repository without introducing Stage 2 features?
```

Approved verdict:

```text
QODER_ROUND_14_APPROVED_LIVE_MIMO_GITHUB_SYNC
```

Blocked verdict:

```text
QODER_ROUND_14_BLOCKED
```

with exact blockers and minimum required fixes.

## 3. Review Scope

Qoder should independently review and verify.

Allowed:

```text
inspect docs
inspect code/config changes
run tests if practical
run safe runtime smoke if practical
review Cursor's live MiMo evidence
review secret/artifact audit
review Git status, tags, branches, and remote configuration
verify GitHub push result if credentials/remote access are available
produce independent handoff
```

Avoid:

```text
feature development
large code changes
provider redesign
GitHub PR ingestion
Stage 2 implementation
```

## 4. Required Inputs

Read:

```text
stage 1.5/start.md
stage 1.5/tasks/round-14/00-round-14-start.md
stage 1.5/tasks/round-14/01-cursor-live-mimo-github-sync.md
stage 1.5/handoff/round-14/01-cursor-live-mimo-github-sync-handoff.md
README.md
backend-java/README.md
frontend/README.md
docs/AGENT_RULES.md
```

Inspect as needed:

```text
.gitignore
.env.example
backend-java/src/main/resources/application.yml
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/ConfigurableReviewProvider.java
backend-java/src/main/java/com/codereviewx/backend/review/pipeline/provider/mimo/*
frontend provider/status UI files if changed
git status / git remote / git tag / git branch
```

## 5. Acceptance Questions

Live MiMo:

```text
Was MIMO_API_KEY used only from environment?
Was the key never printed or committed?
Did MiMo mode start correctly?
Did metadata-only and diff-grounded review paths behave safely?
If MiMo succeeded, were findings structured and source=MIMO?
If MiMo failed, did fallback return safe Mock findings with source=MOCK?
Were raw prompt/model output/diff/key kept out of public API/UI?
```

Repository hygiene:

```text
Does .gitignore exclude secrets and generated artifacts?
Are node_modules, target, dist, H2 database files, and .env files untracked?
Are only intended source/docs/task/handoff files committed?
```

Git/GitHub:

```text
Is Git initialized cleanly?
Does main exist?
Does origin point to the GitHub CodeReviewX repository?
Was main pushed successfully?
Were intended tags created and pushed?
Was the stage-1-5-productization branch created/pushed if assigned?
```

Scope:

```text
No GitHub PR ingestion
No OAuth/App installation
No repository clone
No RAG/MCP/Function Calling/Memory
No auth/team model
No backend API contract change beyond minimal safe fixes
No production deployment/CI expansion unless explicitly assigned
```

## 6. Recommended Validation Commands

If practical:

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test
```

If practical:

```bash
cd frontend
npm run typecheck
npm run build
npm test -- --run
```

Git checks:

```bash
git status --short
git remote -v
git branch --all
git tag --list
git log --oneline --decorate --max-count=5
```

Secret/artifact checks:

```bash
find . -name '.env' -o -name '.env.local'
find frontend -maxdepth 2 -name node_modules -o -name dist
find backend-java -maxdepth 3 -name target -o -path '*/data/*'
```

Do not print real secrets.

## 7. Final Handoff Requirements

Create:

```text
stage 1.5/handoff/round-14/02-qoder-live-mimo-github-sync-independent-review-handoff.md
```

Include:

```text
final verdict
evidence reviewed
commands run or evidence relied on
live MiMo judgment
fallback judgment
security/secret judgment
repository hygiene judgment
Git/GitHub sync judgment
scope compliance judgment
blocking issues, if any
non-blocking recommendations, if any
architect recommendation
```

Never include the real `MIMO_API_KEY`.

