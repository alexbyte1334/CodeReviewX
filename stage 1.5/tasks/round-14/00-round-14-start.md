# Round 14 Start: Live MiMo Verification + Git/GitHub Repository Sync

## 1. Round Metadata

```text
Project: CodeReviewX
Round: Round 14
Theme: Live MiMo Verification + Git/GitHub Repository Sync
Task Type: Stage 1.5 provider enablement and repository hygiene
Previous Round:
  Round 13: Product UI Restyle
Required Previous Verdict:
  QODER_ROUND_13_APPROVED_PRODUCT_UI_RESTYLE
```

Architect confirmation:

```text
Round 13 final status:
  ARCHITECT_ROUND_13_CLOSED

Live MIMO_API_KEY:
  Available locally

GitHub repository:
  CodeReviewX

Git/GitHub execution permission:
  Cursor and Qoder are allowed to execute Git initialization and GitHub push within this round's safety rules.

Architect role from Round 14 onward:
  Architect coordinates, assigns tasks, reviews handoffs, and makes final decisions.
  Cursor and Qoder perform concrete execution/review work.
```

## 2. Round 14 Purpose

Round 14 should make the productized MVP truly ready for external repository-based development.

Goals:

```text
1. Verify Xiaomi MiMo with a real local MIMO_API_KEY if available
2. Preserve safe fallback behavior
3. Improve provider status/documentation if needed
4. Ensure .env.example and .gitignore protect secrets/generated artifacts
5. Initialize local Git repository if still absent
6. Prepare clean commit/tag/branch strategy
7. Push to GitHub only after a clean secret/artifact audit
```

## 3. Hard Security Rules

```text
Never commit MIMO_API_KEY
Never write real key into docs or handoffs
Never commit .env
Never expose raw prompt/model output
Never expose raw diffText in public API response
Never print secrets in logs
Never commit backend-java/data/*.db
Never commit frontend/node_modules/
Never commit backend-java/target/
Never commit frontend/dist/
```

## 4. Expected Agent Sequence

```text
Cursor:
  live MiMo verification, provider docs/status/gitignore preparation, Git initialization, clean commit/tag/branch setup, and GitHub push

Qoder:
  independent release-readiness review, secret/artifact audit, remote repository verification, and final Round 14 approval/block decision

Architect:
  reviews Cursor and Qoder handoffs, decides whether Round 14 closes, and assigns Round 15 only after approval
```

## 5. Detailed Task Docs

Detailed Cursor/Codex/Qoder task files for Round 14 should be generated only after Round 13 is closed and the architect confirms:

```text
GitHub target repository name
Whether live MIMO_API_KEY is available locally
Whether remote push should be performed by agent or by user
Preferred initial branch/tag naming
```

Confirmed. Detailed task files:

```text
stage 1.5/tasks/round-14/01-cursor-live-mimo-github-sync.md
stage 1.5/tasks/round-14/02-qoder-live-mimo-github-sync-independent-review.md
```
