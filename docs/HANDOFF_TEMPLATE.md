# Handoff Report Template

> This is the standard handoff report template for CodeReviewX.
> Every Agent that completes a task must output a report using this structure.
> Copy the template below and fill in each section.

---

## Template

```markdown
# Handoff Report: <Task Name>

## 1. Task Metadata

- Project: CodeReviewX
- Round: <e.g. Round 01>
- Task: <Task name>
- Target Agent: <Cursor / Codex / Qoder>
- Execution Date: <YYYY-MM-DD>
- Repository Branch: <e.g. main>

## 2. Execution Summary

Briefly summarize what was created or updated in this round.
2–4 sentences. State whether the goal was achieved.

## 3. Files Created

List all newly created files:

- `path/to/file1`
- `path/to/file2`

If no new files were created, state: _None._

## 4. Files Modified

List all modified files and what changed:

| File | Change Description |
|---|---|
| `path/to/file` | What was changed and why |

If all files in this round are newly created, state: _All files in this round were newly created. No pre-existing files were modified._

## 5. Scope Compliance

Confirm that the round stayed within allowed scope. Answer each item:

- [ ] No Spring Boot business code was created.
- [ ] No FastAPI business code was created.
- [ ] No frontend page code was created.
- [ ] No database migration files were created.
- [ ] No GitHub API integration was implemented.
- [ ] No Semgrep integration was implemented.
- [ ] No LLM integration was implemented.
- [ ] No secrets, API keys, tokens, or passwords were committed.
- [ ] No unapproved technology or dependencies were introduced.
- [ ] Only files within the allowed scope were modified.

## 6. Acceptance Criteria Checklist

Copy the acceptance criteria from the task document and mark each item:

- [x] Item that passed
- [ ] Item that did not pass (explain below if needed)

Notes on any unchecked items:

## 7. Checks Performed

List the commands or manual checks performed after completing the task:

```bash
find . -maxdepth 3 -type f | sort
find . -type f | sort
grep -R "sk-\|ghp_\|github_pat_\|AKIA\|BEGIN PRIVATE KEY" . || true
```

Paste or summarize the output if relevant.

## 8. Known Issues or Limitations

List any known issues, edge cases, or limitations introduced in this round.

If none: _None._

## 9. Deviations from Task

List any intentional or unintentional deviations from the task specification.
Explain the reason for each deviation.

If none: _None._

## 10. Recommended Next Step

State clearly what should happen next.

> Return this handoff report to **ChatGPT Architect** for review.
> Do not send the repository to Codex or Qoder directly.
> The next round and agent assignment must be decided by ChatGPT Architect.
```

---

## Round Transition Rule

```text
Cursor completes task
        ↓
Cursor submits handoff report to ChatGPT Architect
        ↓
ChatGPT Architect reviews and decides
        ↓
(If approved) ChatGPT assigns Codex validation task
        ↓
Codex submits handoff report to ChatGPT Architect
        ↓
(If needed) ChatGPT assigns Qoder review task
        ↓
Qoder submits review report to ChatGPT Architect
        ↓
ChatGPT Architect makes final round decision
```

No Agent hands off directly to another Agent without ChatGPT Architect approval.
