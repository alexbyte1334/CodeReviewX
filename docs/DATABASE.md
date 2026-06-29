# CodeReviewX Database

> Current persistence model for the local Spring Boot implementation.

## 1. Runtime Database

Local runtime uses H2 file storage:

```text
jdbc:h2:file:./data/codereviewx;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
```

Tests use an isolated in-memory H2 database:

```text
jdbc:h2:mem:testdb
```

Flyway migrations live in:

```text
backend-java/src/main/resources/db/migration/
```

`spring.jpa.hibernate.ddl-auto=validate` keeps entity mappings checked against
the Flyway schema.

## 2. Current Tables

| Table | Purpose |
|---|---|
| `review_task` | user-created review target and latest task-level status |
| `review_issue` | normalized structured findings |
| `review_run` | one execution attempt for a task |
| `review_input_snapshot` | sanitized GitHub PR metadata and diff summary |
| `review_tool_trace` | ordered GitHub/tool/agent step timeline |
| `review_provider_trace` | provider request/used/hit summary |
| `review_comment_preview` | local draft comments and publish state |

## 3. `review_task`

Stores the user-visible review task.

Important fields:

| Column | Meaning |
|---|---|
| `id` | internal task id |
| `repo_url` | GitHub repository URL supplied by user |
| `pr_number` | pull request number |
| `diff_text` | optional pasted manual diff; not returned by public APIs |
| `review_mode` | `MANUAL_DIFF` or `GITHUB_PR` |
| `latest_run_id` | latest associated `review_run` |
| `status` | `PENDING`, `RUNNING`, `SUCCESS`, `FAILED` |
| `summary` | user-facing completion summary |
| `requested_provider` | currently `mimo` for new tasks |
| `provider_used` | provider that produced findings |
| `provider_hit` | whether requested provider was used |
| `error_message` | user-readable task failure message |

## 4. `review_run`

Stores execution-level state for a task run.

Important fields:

| Column | Meaning |
|---|---|
| `review_task_id` | owning task |
| `run_number` | run sequence per task |
| `review_mode` | `MANUAL_DIFF` or `GITHUB_PR` |
| `status` | run stage: ingesting, reviewing, building preview, success, failed |
| `requested_provider` | requested provider |
| `provider_used` | provider used |
| `provider_hit` | provider match flag |
| `error_code` | stable failure code |
| `error_message` | safe failure message |
| `started_at` / `finished_at` | run timing |

Current implementation creates one run per task. The schema is ready for later
retry/re-run support.

## 5. `review_issue`

Stores normalized issues generated from approved provider output.

Important fields:

| Column | Meaning |
|---|---|
| `review_task_id` | owning task |
| `review_run_id` | run that produced the issue |
| `issue_key` | public stable id such as `MIMO-ISSUE-1` |
| `severity` | `HIGH`, `MEDIUM`, `LOW` |
| `category` | bug/security/performance/maintainability/style/test |
| `source` | currently `MIMO` for new AI findings |
| `status` | currently `OPEN`; reserved for future workflows |
| `file_path` | target file path |
| `start_line` / `end_line` | target line range |
| `title` / `description` / `recommendation` | user-facing finding content |

`issueSummary` is not persisted as a separate table. It is computed from
persisted issues when responses are assembled.

## 6. `review_input_snapshot`

Stores sanitized GitHub PR metadata and diff summary for `GITHUB_PR` mode.

It includes owner, repo, PR number, title, author, refs, SHAs, changed file
counts, additions/deletions, and truncation flags.

The `snapshot_json` field stores a sanitized file summary. It intentionally
does not store GitHub tokens, Authorization headers, prompts, model output, or
raw full diff text.

## 7. `review_tool_trace`

Stores ordered execution events such as:

```text
github.pr.metadata.load
github.pr.diff.load
mimo.ai1.plan
mimo.ai2.execute
mimo.ai1.gate
issue.generate
comment.preview.build
```

Each row stores safe input/output summaries, status, error code, and timing.

## 8. `review_provider_trace`

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

## 9. `review_comment_preview`

Stores local draft comments generated from issues.

Important fields:

| Column | Meaning |
|---|---|
| `review_run_id` | owning run |
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

## 10. Production Database Note

H2 is intentionally used for local demonstration. A production deployment should
move to PostgreSQL or MySQL, add managed secret storage, and revisit indexing,
retention, and migration policies.
