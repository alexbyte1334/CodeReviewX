# CodeReviewX Evals

Small offline benchmark for the CodeReviewX review agent.

Run:

```bash
node scripts/run-evals.mjs
```

The runner reads cases from `evals/cases/` and writes:

```text
evals/reports/latest.json
evals/reports/latest.md
```

By default it evaluates each case's committed `baselineFindings`, so it can run without API keys. To evaluate live or externally captured agent output, write a file named `evals/actual/<case-id>.json`:

```json
{
  "gateRejected": false,
  "findings": [
    {
      "severity": "HIGH",
      "category": "SECURITY",
      "filePath": "src/UserRepository.java",
      "startLine": 22,
      "title": "SQL query concatenates request input",
      "description": "The query is built with request input.",
      "recommendation": "Use a parameterized query."
    }
  ]
}
```

Tracked metrics:

- schema pass rate
- expected finding hit rate
- severity match rate
- category match rate
- issue count delta
- false positive count
- gate rejection count
