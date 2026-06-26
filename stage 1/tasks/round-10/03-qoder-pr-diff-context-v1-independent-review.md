# tasks/round-10/03-qoder-pr-diff-context-v1-independent-review.md

# Round 10 / Qoder Task: PR Diff Context v1 Independent Review

## 1. Role

You are Qoder, the independent review agent for CodeReviewX Round 10.

Cursor implemented **PR / Diff Context v1**.  
Codex independently validated it and returned:

```text
CODEX_ROUND_10_READY_FOR_QODER
```

Your job is to make the final independent judgment for Round 10:

```text
Can Round 10 be closed?
Is the implementation architecturally sound?
Does it materially improve the review agent?
Is it safe to proceed to Round 11?
```

Do not merely repeat Cursor or Codex. Inspect the implementation and judge it independently.

---

## 2. Round 10 Goal

Round 10 should add optional pasted PR diff context to CodeReviewX.

Before Round 10:

```text
Input:
  repoUrl + prNumber
```

After Round 10:

```text
Input:
  repoUrl + prNumber + optional diffText
```

Expected behavior:

```text
User submits repoUrl + prNumber + optional diffText
  -> ReviewTaskService creates task
  -> ReviewContext carries optional diffText
  -> ReviewPipelineService runs
  -> ConfigurableReviewProvider selects Mock or Xiaomi MiMo
  -> ReviewPromptBuilder includes diffText when present
  -> Xiaomi MiMo provider can review actual changed code context
  -> Parser returns ReviewFinding[]
  -> Findings persist as ReviewIssueEntity
  -> API response remains stable
  -> Frontend renders existing review result UI
```

Round 10 is **manual pasted diff context**, not automatic GitHub integration.

---

## 3. Inputs to Consider

You should consider:

```text
tasks/round-10/00-round-10-start.md
tasks/round-10/01-cursor-pr-diff-context-v1-handoff.md
tasks/round-10/02-codex-pr-diff-context-v1-validation-handoff.md
```

Then inspect the actual codebase.

Do not rely solely on handoff claims.

---

## 4. Core Review Questions

Answer these clearly:

```text
Did Round 10 successfully add optional diff context?
Does existing repoUrl + prNumber flow still work?
Does diffText reach ReviewContext and MiMo prompt correctly?
Is no-diff behavior preserved?
Is Mock mode unchanged and deterministic?
Is MiMo fallback still safe?
Is the public API response stable?
Is raw diff/prompt/model output kept out of public responses?
Is the frontend change minimal and correct?
Is the documentation accurate?
Was GitHub integration scope creep avoided?
Can Round 10 close?
```

---

## 5. Backend Architecture Review

Inspect and judge:

```text
CreateReviewTaskRequest
ReviewTaskEntity
ReviewTaskService
ReviewContext
ReviewPipelineService
ConfigurableReviewProvider
MockReviewProvider
XiaomiMiMoReviewProvider
ReviewPromptBuilder
XiaomiMiMoFindingParser
ReviewTaskResponse
ReviewIssueResponse
IssueSummaryResponse
```

Verify:

```text
diffText is optional.
diffText max length is 20000 characters.
Blank/whitespace-only diffText is normalized away.
repoUrl/prNumber validation remains unchanged.
ReviewContext carries normalized diffText.
The same normalized value is used for persistence and pipeline context.
Persisted diffText is not exposed in public DTOs.
No null-pointer-prone provider flow was introduced.
No unnecessary persistence model expansion was introduced.
```

Reject or flag if you find:

```text
ChangedFileEntity
FileDiffEntity
ExecutionTraceEntity
ProviderInputEntity
RawPromptEntity
RawModelOutputEntity
GitHub OAuth
automatic GitHub fetching
repository clone logic
```

---

## 6. Prompt Quality Review

Review `ReviewPromptBuilder` critically.

For no-diff prompt, verify:

```text
It does not pretend actual changed code is available.
It preserves conservative Round 09 behavior.
It still enforces strict JSON.
It uses valid backend enum values.
```

For diff prompt, verify:

```text
It includes the actual diffText.
It clearly states the diff is the primary review context.
It asks the model to review changed lines and nearby context.
It covers bug, security, performance, maintainability, style, and test risks.
It tells the model not to invent files outside the diff.
It tells the model to prefer changed-hunk line numbers where possible.
It requires JSON only.
It forbids markdown fences.
It allows [] when no meaningful findings exist.
It matches parser and DTO expectations.
```

Assess whether the prompt is strong enough for a useful review-agent prototype.

---

## 7. Provider and Fallback Review

Verify Mock provider:

```text
Still returns exactly 3 deterministic mock issues.
Works with diffText present.
Works without diffText.
Does not accidentally depend on diff content.
```

Verify Xiaomi MiMo path:

```text
Uses diff-aware prompt when diffText is present.
Uses no-diff prompt when diffText is absent.
Keeps parser behavior unchanged.
Allows valid [] as successful zero findings.
```

Verify fallback:

```text
provider=mimo + missing key -> mock fallback
provider=mimo + client failure -> mock fallback
provider=mimo + parser failure -> mock fallback
unexpected runtime failure -> mock fallback if previously supported
fallback works when diffText is present
fallback reason is not leaked to public API
MIMO_API_KEY is not leaked
```

---

## 8. Public API Contract Review

Preserved endpoints:

```text
GET  /api/health
POST /api/review-tasks
GET  /api/review-tasks
GET  /api/review-tasks/{id}
```

Verify response stability:

```text
ReviewTaskResponse shape remains stable.
ReviewIssueResponse shape remains stable.
IssueSummaryResponse shape remains stable.
No diffText in public response.
No raw prompt in public response.
No raw model output in public response.
ReviewTaskResponse.riskLevel == ReviewTaskResponse.issueSummary.riskLevel.
```

Assess whether adding `diffText` as optional request-only input is a clean API evolution.

---

## 9. Frontend Review

Inspect:

```text
frontend/src/types/reviewTask.ts
frontend/src/components/ReviewTaskCreateForm.tsx
frontend/src/styles/app.css
frontend/src/test/ReviewTaskCreateForm.test.tsx
```

Verify:

```text
Optional PR diff textarea exists.
Helper copy is accurate.
Submitting without diff works.
Submitting with diff sends diffText.
Whitespace-only diff is omitted or safely normalized.
diffText > 20000 characters is blocked client-side if implemented.
Existing list/detail/result rendering remains unchanged.
No redesign happened.
```

Reject scope creep if you find:

```text
new UI library
route overhaul
dashboard redesign
Monaco editor
syntax highlighting package
visual diff viewer
chart library
```

---

## 10. Documentation Review

Inspect:

```text
README.md
backend-java/README.md
```

Verify documentation says:

```text
Round 10 adds optional pasted PR diff context.
repoUrl + prNumber flow still works.
diffText is optional.
Maximum diffText length is 20000 characters.
MiMo prompt uses diffText when provided.
Mock mode remains stable default.
MiMo fallback behavior is unchanged.
Public API does not expose raw diff/prompt/model output.
Manual pasted diff is supported.
Automatic GitHub ingestion is not implemented.
```

Flag overclaiming if docs imply:

```text
automatic GitHub PR fetching
private repo support
full GitHub integration
repository clone-based review
production-ready PR review platform
```

unless actually implemented.

---

## 11. Test and Runtime Review

You may rerun tests if needed.

Expected validation baseline from Codex:

```text
Backend:
  mvn test
  84 tests, 0 failures

Frontend:
  npm run typecheck
  npm run build
  npm test -- --run
  31 tests, 0 failures

Runtime:
  Mock without diff succeeded
  Mock with diff succeeded
  Oversized diff returned HTTP 400
  MiMo missing-key with diff fell back to MOCK
  Browser smoke succeeded
```

If you rerun anything, record exact commands and results.

If you do not rerun, state that your review is based on source inspection plus Cursor/Codex validation evidence.

---

## 12. Security and Leakage Review

Judge whether Round 10 introduces new risk.

Check:

```text
Raw pasted diff is persisted intentionally.
Raw pasted diff is not exposed publicly.
Raw prompt is not exposed publicly.
Raw model output is not exposed publicly.
MIMO_API_KEY is not exposed.
Fallback internals are not exposed to clients.
Validation prevents very large pasted diffs.
```

Comment on remaining risk:

```text
Persisted diff may contain sensitive code.
This is acceptable for local prototype if documented.
Future production version should add retention, access control, and redaction policy.
```

---

## 13. Agent Structure and Flow

Your handoff must include:

```markdown
## Agent Structure and Flow
```

Use this chain:

```text
Input -> Context -> Pipeline -> Provider Selection -> Provider -> Prompt -> Model Client -> Parser -> Finding -> Persistence -> API DTO -> Frontend
```

Expected Round 10 structure:

```text
Input:
  repoUrl + prNumber + optional diffText

Context:
  ReviewContext with optional diffText

Orchestrator:
  ReviewPipelineService

Provider Selection:
  ConfigurableReviewProvider

Provider:
  MockReviewProvider or XiaomiMiMoReviewProvider

Prompt:
  ReviewPromptBuilder includes diff when available

Model:
  Xiaomi MiMo API through XiaomiMiMoClient

Parser:
  XiaomiMiMoFindingParser

Finding:
  ReviewFinding[]

Persistence:
  ReviewTaskEntity + ReviewIssueEntity

Presentation:
  ReviewTaskResponse + frontend issue cards
```

State whether implementation matches this structure.

---

## 14. Final Judgment Requirements

Your final handoff must answer:

```text
Is Round 10 closeable?
What exactly did Round 10 add to the agent?
What is still not implemented?
What should Round 11 do next?
```

Recommended Round 11 direction if Round 10 closes:

```text
Frontend UX polish + agent result presentation cleanup + provider copy cleanup
```

But if live MiMo is still not verified with a real key, include this in Round 11:

```text
Live MiMo verification with environment-only MIMO_API_KEY
HTTP timeout configuration
Sanitized provider failure classification
Final copy cleanup from mock/prototype wording to review-agent wording
```

---

## 15. Required Output

Create:

```text
tasks/round-10/03-qoder-pr-diff-context-v1-independent-review-handoff.md
```

The handoff must include:

```text
Executive summary.
Independent architecture judgment.
Cursor/Codex claim assessment.
Backend review findings.
Frontend review findings.
Prompt quality assessment.
Provider/fallback assessment.
API contract assessment.
Persistence/security assessment.
Documentation assessment.
Scope creep assessment.
Test/runtime evidence reviewed.
Agent Structure and Flow.
Open issues.
Required fixes, if any.
Round 10 close recommendation.
Round 11 recommendation.
```

Final verdict must be one of:

```text
QODER_ROUND_10_APPROVED_CLOSE
QODER_ROUND_10_NEEDS_FIXES
QODER_ROUND_10_BLOCKED
```

Use:

```text
QODER_ROUND_10_APPROVED_CLOSE
```

only if Round 10 is architecturally sound, materially improves the review agent, preserves compatibility, avoids leakage, and does not introduce major scope creep.

Use:

```text
QODER_ROUND_10_NEEDS_FIXES
```

if there are concrete implementation issues that should be fixed before closing.

Use:

```text
QODER_ROUND_10_BLOCKED
```

only if independent review cannot be performed due to environment or repository state.

---