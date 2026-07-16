# RAG Operations Runbook

## Configuration and rollout

Set `RAG_ENABLED`, `RAG_REVIEW_PERCENTAGE` (`0|10|50|100` operational gates),
`RAG_FALLBACK_ENABLED`, and `RAG_REQUIRE_EVIDENCE`. Model settings are
`RAG_EMBEDDING_BASE_URL`, `RAG_EMBEDDING_API_KEY`, `RAG_EMBEDDING_MODEL`,
`RAG_EMBEDDING_DIMENSIONS` (V1: 1024), `RAG_EMBEDDING_BATCH_SIZE`,
`RAG_RERANK_BASE_URL`, `RAG_RERANK_API_KEY`, `RAG_RERANK_MODEL`,
`RAG_MODEL_TIMEOUT_SECONDS`, and `RAG_MODEL_MAX_RETRIES`. Resource limits are
`RAG_FETCH_DEPTH`, `RAG_MAX_FILE_BYTES`, `RAG_MAX_FILES`, `RAG_MAX_TEXT_BYTES`,
`RAG_MAX_SCANNED_ENTRIES`, `RAG_MAX_SCANNED_BYTES`, and
`RAG_SHUTDOWN_GRACE_SECONDS`; retention scheduling uses
`RAG_RETENTION_CLEANUP_CRON`. `RAG_WORK_ROOT` defaults to the user home in a
local profile and is `/var/lib/codereviewx/rag-work` in Compose. Never put keys
in URLs, traces, or database rows.

The implementation accepts any integer 0–100 technically, but operators may
advance only through these gates:

1. Set `RAG_ENABLED=true`, `RAG_FALLBACK_ENABLED=true`,
   `RAG_REQUIRE_EVIDENCE=true`, and percentage `0`; capture baseline review
   metrics and trace evidence.
2. Set percentage `10`; observe at least 20 completed reviews from the review
   run/tool-trace database and eval report. Advance only when there are zero P0
   or P1 findings attributable to rollout, no evidence failures, and degraded
   rate/latency remain within SLO.
3. Set percentage `50`; observe at least 50 completed reviews using the same
   trace/report sources and criteria. Abort immediately on any P0/P1, evidence
   gate regression, contamination, or SLO breach.
4. Set percentage `100` only after the 50% gate sign-off. Intermediate values
   are technically supported for emergency sampling but are not approved
   release gates and require the same explicit observation evidence.

## Index lifecycle

1. **Initial index:** create the repository, call normal index, poll job until
   `READY`, and verify commit/model/dimension and indexed counts.
2. **Force reindex:** use `reindex`; keep the previous READY snapshot serving
   until the new job completes, then atomically switch active commit.
3. **Model/dimension upgrade:** provision a new `index_version`, run a full
   reindex with the new model. V1 dimension must remain 1024; do not mutate
   existing vectors in place. Roll back by keeping the old active version.
4. **Failed job:** inspect opaque `error_code`, fix source/model/limits, retry
   with a new job. Do not mark a partial snapshot READY.

## Capacity and recovery

Monitor database size, vector index size, job age, failed jobs, retrieval
latency, degraded count, and evidence validation rate. If disk is high, stop
new indexing, retain the active snapshot, archive/expire old snapshots after a
verified PostgreSQL backup, then vacuum/reindex during a maintenance window.
Use `pg_dump`/`pg_restore` (including `vector` extension and Flyway metadata);
restore to a separate database, run migrations/checks, then switch traffic.

## Deletion and provider changes

For repository deletion, disable indexing and wait for active jobs; export audit
metadata first. In one FK-safe transaction delete associated issue-evidence rows
or preserve them with `rag_chunk_id` set NULL (the FK is `ON DELETE SET NULL`),
then delete `rag_chunk`, `rag_document`, `rag_index_snapshot`,
`rag_index_job`, and `rag_retrieval_trace`, and finally `rag_repository`.
Retain review/audit rows required by policy; never delete an active snapshot
before replacement or leave queued/running jobs. For embedding/rerank vendor changes, deploy keys and
endpoints, run a shadow/full index, compare eval metrics, switch only after the
same dimension/contract passes, and retain the previous provider for rollback.

## Incident degradation and rollback

Immediately set `RAG_REVIEW_PERCENTAGE=0` and restart; this deterministically
restores bounded legacy context. Keep `RAG_FALLBACK_ENABLED=true` for graceful
index/model degradation, or set it false to fail closed when evidence is
required. Do not run a V4 down migration or delete index data. If PostgreSQL is
unavailable, stop new RAG reviews, preserve existing data, and keep the H2 demo
profile available. Restore the last known-good application image/config after
the incident. Rollback is complete when new runs contain no RAG retrieval trace,
legacy context is present, and publish behavior remains unchanged. Re-enable
10% only after a fresh baseline and the 20-review observation gate; abort and
return to 0% on any P0/P1, evidence failure, contamination, or SLO breach.

## Troubleshooting

`REPOSITORY_NOT_FOUND` means check the GitHub URL; `SHALLOW_CLONE_UNAVAILABLE`,
`CHECKOUT_FAILED`, or `CHUNKING_FAILED` means inspect checkout/limits and retry;
`EMBEDDING_UNAVAILABLE` means check the configured provider; and
`INDEX_LIMIT_EXCEEDED` means reduce repository size or raise an approved limit.
`INDEX_JOB_FAILED`/`INDEXING_FAILED` require inspecting the job trace before a
new retry. `RETRIEVAL_DEGRADED` is observable and follows the fallback policy.
All publish paths still require `confirmed=true`.
