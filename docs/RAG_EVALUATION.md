# RAG Evaluation Contract

`node scripts/run-rag-evals.mjs` runs deterministic offline retrieval and
mutation gates; `--self-test` verifies forbidden hits, cross-commit isolation,
budget/latency limits, dropped/removed/corrupted findings, and rerank coverage.
The corpus is intentionally larger than K and includes eligible old-commit
negative controls. Reports are written to `evals/rag/reports/latest.{json,md}`.

Required gates are Recall@10 >= 0.85, MRR@10 >= 0.70, nDCG@10 >= 0.75,
forbidden-hit and cross-commit contamination equal zero, evidence validation
pass rate >= 0.95, grounded precision at least the recorded baseline, p95
retrieval/rerank <= 3,000 ms, <=30 rerank candidates, <=12 final chunks and
<=36,000 context characters. Live mode additionally validates HTTP timeout,
embedding dimensions/count, and exact unique rerank coverage.

Evaluate changes to corpus, query, model, reranker, or selection budget as a
single report; compare against the committed baseline and record mutations that
must fail. An eval report is evidence, not a substitute for PostgreSQL compose
smoke or CI.
