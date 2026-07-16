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

## RAG retrieval gates

Run the deterministic retrieval benchmark with `node scripts/run-rag-evals.mjs`. Cases under `evals/rag/cases/` use a fixed sample corpus and fake embeddings, so CI is reproducible without credentials. The runner reports Recall@5/10, MRR@10, nDCG@10, forbidden-hit rate, context budget violations, cross-commit contamination, selected chunks, and p95 latency. `RAG_LIVE_EVAL=1` performs authenticated POST health calls to `RAG_EMBEDDING_URL` and `RAG_RERANK_URL`; set both corresponding `*_API_KEY` and `*_MODEL` variables. Responses must be OpenAI-compatible embedding (`data`) and rerank (`results`) shapes; secrets and request bodies are never logged.
