ALTER TABLE rag_index_job
    ADD COLUMN heartbeat_at TIMESTAMP;

UPDATE rag_index_job
SET heartbeat_at = started_at
WHERE status = 'RUNNING';

WITH duplicate AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY repository_id, requested_ref, embedding_model,
                            embedding_dimensions, index_version
               ORDER BY created_at, id
           ) AS duplicate_rank
    FROM rag_index_job
    WHERE status IN ('QUEUED', 'RUNNING')
)
UPDATE rag_index_job job
SET status = 'FAILED',
    error_code = 'DUPLICATE_ACTIVE_JOB',
    error_message = 'Superseded while installing active job identity',
    finished_at = CURRENT_TIMESTAMP,
    heartbeat_at = NULL
FROM duplicate
WHERE duplicate.id = job.id
  AND duplicate.duplicate_rank > 1;

CREATE UNIQUE INDEX uq_rag_index_job_active_identity
    ON rag_index_job (
        repository_id, requested_ref, embedding_model, embedding_dimensions, index_version
    )
    WHERE status IN ('QUEUED', 'RUNNING');
