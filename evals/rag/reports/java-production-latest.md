# Java production RAG retrieval quality

Engine: `java-production`

Result: **PASS**

Runtime: PostgreSQL 16 with pgvector (`pgvector/pgvector:pg16`).

External fixtures: deterministic token-hash embedding and deterministic query/content token-overlap rerank. Production Java performs snapshot scoping, vector/FTS retrieval, RRF, and context selection.

## Metrics

- recallAt10: 1.000 (threshold 0.850)
- mrrAt10: 0.833 (threshold 0.700)
- ndcgAt10: 0.871 (threshold 0.750)
- forbiddenHits: 0.000 (threshold 0.000)
- crossCommitContamination: 0.000 (threshold 0.000)
- contextBudgetViolations: 0.000 (threshold 0.000)

## Cases

- `rag-001-cross-file-call`: selected=[src/api.ts#1, test/api.test.ts#1, src/service.ts#1, src/feature.ts#1, src/health.ts#1, src/routes.ts#1, src/metrics.ts#1, src/storage.ts#1, src/cache.ts#1, src/notifications.ts#1, src/search.ts#1, src/format.ts#1], Recall@10=1.000, MRR@10=1.000, nDCG@10=0.920, forbidden=0, cross-commit=false, context-chars=1778
- `rag-002-config-security`: selected=[src/config.ts#1, src/feature.ts#1, src/health.ts#1, src/routes.ts#1, src/metrics.ts#1, src/storage.ts#1, src/cache.ts#1, src/notifications.ts#1, src/search.ts#1, src/format.ts#1, src/queue.ts#1, src/telemetry.ts#1], Recall@10=1.000, MRR@10=1.000, nDCG@10=1.000, forbidden=0, cross-commit=false, context-chars=1710
- `rag-003-test-impact`: selected=[src/service.ts#1, test/api.test.ts#1, src/api.ts#1, src/feature.ts#1, src/health.ts#1, src/routes.ts#1, src/metrics.ts#1, src/storage.ts#1, src/cache.ts#1, src/notifications.ts#1, src/search.ts#1, src/format.ts#1], Recall@10=1.000, MRR@10=0.500, nDCG@10=0.693, forbidden=0, cross-commit=false, context-chars=1778
- `rag-004-negative-control`: selected=[docs/unrelated.md#1, legacy/notes.md#1, src/feature.ts#1, src/health.ts#1, src/routes.ts#1, src/metrics.ts#1, src/storage.ts#1, src/cache.ts#1, src/notifications.ts#1, legacy/old-api.ts#1, src/format.ts#1, src/queue.ts#1], Recall@10=0.000, MRR@10=0.000, nDCG@10=0.000, forbidden=0, cross-commit=false, context-chars=1587

## Failures

None.


## Excludes

This gate does not cover MiMo finding generation, finding quality, evidence-validation pass rate, grounded finding precision, or network model latency.
