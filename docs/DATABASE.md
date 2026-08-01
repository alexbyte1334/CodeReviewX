# CodeReviewX Database

> Current persistence model for the H2 local development and PostgreSQL/pgvector production
> RAG profiles.

## 1. Runtime Database

The default RAG-disabled local development uses H2 file storage:

```text
jdbc:h2:file:./data/codereviewx;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
```

H2 tests use an isolated in-memory database:

```text
jdbc:h2:mem:testdb
```

The production RAG profile uses PostgreSQL 16 + pgvector. PostgreSQL is the only
real RAG retrieval database; H2 does not emulate vector or FTS behavior.

Flyway migrations live in:

```text
backend-java/src/main/resources/db/migration/
backend-java/src/main/resources/db/rag/postgresql/
```

`spring.jpa.hibernate.ddl-auto=validate` keeps entity mappings checked against
the Flyway schema.

## 2. Current Tables

| Table | Purpose |
|---|---|
| `review_api_run` | sole durable Review API aggregate root and workflow status |
| `review_issue` | normalized structured findings |
| `review_input_snapshot` | sanitized GitHub PR metadata and diff summary |
| `review_tool_trace` | ordered GitHub/tool/agent step timeline |
| `review_provider_trace` | provider request/used/hit summary |
| `review_comment_preview` | local draft comments and publish state |
| `rag_repository` | repository identity and active index state |
| `rag_index_job` | persistent leased indexing work and progress |
| `rag_index_snapshot` | immutable commit/model/index-version snapshot |
| `rag_document` | snapshot-scoped indexed source file |
| `rag_chunk` | bounded source chunk, FTS vector, and embedding |
| `rag_retrieval_trace` | sanitized retrieval metrics and degraded status |
| `review_issue_evidence` | validated bounded evidence attached to an issue |

## 3. `review_api_run`

Stores the user-visible Review API request and its single current execution.
Every child projection is owned by `review_api_run_id`. A fresh v1 database is
required; V12 intentionally replaces the legacy task/run tables and does not
provide an old-runtime data migration.

Important fields:

| Column | Meaning |
|---|---|
| `id` | internal aggregate id |
| `public_id` | Review API UUID exposed to clients |
| `idempotency_key` | unique request key |
| `repo_url` | GitHub repository URL supplied by user |
| `pr_number` | pull request number |
| `diff_text` | optional pasted manual diff; not returned by public APIs |
| `review_mode` | `MANUAL_DIFF` or `GITHUB_PR` |
| `status` | `PENDING`, `RUNNING`, `SUCCESS`, `FAILED` |
| `execution_status` | stage-level status such as `REVIEWING` or `BUILDING_PREVIEW` |
| `summary` | user-facing completion summary |
| `requested_provider` | currently `mimo` for new tasks |
| `provider_used` | provider that produced findings |
| `provider_hit` | whether requested provider was used |
| `error_message` | user-readable task failure message |

## 4. `review_issue`

Stores normalized issues generated from approved provider output.

Important fields:

| Column | Meaning |
|---|---|
| `review_api_run_id` | owning Review API aggregate |
| `issue_key` | public stable id such as `MIMO-ISSUE-1` |
| `severity` | `HIGH`, `MEDIUM`, `LOW` |
| `category` | bug/security/performance/maintainability/style/test |
| `source` | `MIMO`, `SEMGREP`, or `DEPENDENCY` finding provenance |
| `status` | currently `OPEN`; reserved for future workflows |
| `file_path` | target file path |
| `start_line` / `end_line` | target line range |
| `title` / `description` / `recommendation` | user-facing finding content |

`issueSummary` is not persisted as a separate table. It is computed from
persisted issues when responses are assembled.

## 5. `review_input_snapshot`

Stores sanitized GitHub PR metadata and diff summary for `GITHUB_PR` mode.

It includes owner, repo, PR number, title, author, refs, SHAs, changed file
counts, additions/deletions, and truncation flags.

The `snapshot_json` field stores a sanitized file summary. It intentionally
does not store GitHub tokens, Authorization headers, prompts, model output, or
raw full diff text.

## 6. `review_tool_trace`

Stores ordered execution events such as:

```text
github.pr.metadata.load
github.pr.diff.load
rag.index.ensure
rag.query.build
rag.retrieve.hybrid
rag.rerank
rag.context.assemble
static.analysis.findings
mimo.ai1.plan
mimo.ai2.execute
mimo.ai1.gate
issue.generate
evidence.validate
comment.preview.build
```

RAG-disabled/degraded reviews record `repository.context.index` instead of the
five `rag.*` steps. Each row stores safe input/output summaries, status, error
code, and timing, never raw prompts, full source, or credentials.

## 7. `review_provider_trace`

Stores provider-level observability:

| Column | Meaning |
|---|---|
| `requested_provider` | requested provider |
| `provider_used` | provider used |
| `provider_hit` | match flag |
| `model_name` | model name when recorded |
| `finding_count` | number of normalized findings |
| `normalization_summary` | safe summary of mapping |
| `fallback_reason` | reserved; current MiMo-only path should not fallback |

## 8. `review_comment_preview`

Stores local draft comments generated from issues.

Important fields:

| Column | Meaning |
|---|---|
| `review_api_run_id` | owning Review API aggregate |
| `review_issue_id` / `issue_key` | source issue |
| `file_path` / `line_number` / `side` | GitHub review comment target |
| `draft_body` | local comment body |
| `selected_for_publish` | user selection flag |
| `publish_status` | `NOT_PUBLISHED`, `PUBLISHING`, `PUBLISHED`, `FAILED` |
| `github_comment_id` | GitHub id after successful publish |
| `publish_error_message` | safe failure summary |
| `published_at` | successful publish timestamp |

Publishing requires a stored input snapshot with GitHub owner, repo, PR number,
and head SHA.

## 9. Production RAG Tables and Retention

V4 creates repository, job, document, chunk, retrieval-trace, and issue-evidence
tables. V5 adds `rag_index_snapshot` and backfills only a provable active READY
snapshot. V6 scopes documents/chunks and their foreign keys and uniqueness to a
snapshot. V7 adds worker heartbeat/lease fields and prevents multiple active
jobs for the same repository. A deployed service must also provide managed
secrets, backups, retention monitoring, and migration controls.

Snapshot identity includes repository, commit SHA, embedding model, dimensions,
and index version, preventing cross-commit or mixed-model retrieval. A repository
is READY only when the current contract points to an existing compatible READY
snapshot; an orphaned retained job is not reusable. `rag_chunk` uses
`vector(1024)` HNSW plus generated `TSVECTOR` GIN lexical search. Retrieval
always filters the immutable snapshot before fusion/rerank.

`rag_retrieval_trace` contains safe counts, timings, budget and degraded/error
metadata, not query text, raw source, prompts, tokens, or credentials.
`review_issue_evidence` stores the validated chunk identity, commit, path, line
range, hash, and an excerpt capped at 2,000 characters. Its chunk foreign key
uses `ON DELETE SET NULL` so evidence provenance survives snapshot cleanup;
evidence rows are deleted with their owning issue.

Model/dimension upgrades create a new immutable snapshot; index version 1
requires 1024 dimensions and there is no destructive down migration. Retention
runs on `RAG_RETENTION_CLEANUP_CRON` (default `0 15 2 * * *`) and preserves READY
snapshots newer than 30 days plus the latest five per repository. It does not
delete retrieval traces. Back up PostgreSQL before cleanup and never delete the
active snapshot before a replacement is READY.

## 10. Index Job and Snapshot Lifecycle

```text
QUEUED -> RUNNING -> READY
                  -> FAILED
```

Workers claim jobs with database locking, heartbeat the lease, and recover stale
RUNNING jobs up to the configured attempt limit. `requested_ref` is retained for
audit; branch/default refs are resolved to a 40-character SHA before the
SHA-only checkout boundary, and the resolved SHA is persisted on the job,
snapshot, and repository state. Repeating the same repository/commit/model/
dimension/version tuple reuses the existing READY snapshot without re-embedding.
