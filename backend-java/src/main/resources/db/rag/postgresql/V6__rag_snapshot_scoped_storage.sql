ALTER TABLE rag_index_job
    ADD COLUMN embedding_model VARCHAR(255),
    ADD COLUMN embedding_dimensions INT,
    ADD COLUMN index_version INT;

UPDATE rag_index_job job
SET embedding_model = snapshot.embedding_model,
    embedding_dimensions = snapshot.embedding_dimensions,
    index_version = snapshot.index_version
FROM rag_index_snapshot snapshot
WHERE snapshot.job_id = job.id;

UPDATE rag_index_job job
SET embedding_model = repository.embedding_model,
    embedding_dimensions = repository.embedding_dimensions,
    index_version = repository.index_version
FROM rag_repository repository
WHERE repository.id = job.repository_id
  AND job.embedding_model IS NULL;

ALTER TABLE rag_index_job
    ALTER COLUMN embedding_model SET NOT NULL,
    ALTER COLUMN embedding_dimensions SET NOT NULL,
    ALTER COLUMN index_version SET NOT NULL,
    ADD CONSTRAINT ck_rag_index_job_embedding_dimensions CHECK (embedding_dimensions = 1024);

ALTER TABLE rag_document
    ADD COLUMN snapshot_id BIGINT;

ALTER TABLE rag_chunk
    ADD COLUMN snapshot_id BIGINT;

WITH provable_snapshot AS (
    SELECT snapshot.repository_id, snapshot.commit_sha, snapshot.id AS snapshot_id
    FROM rag_index_snapshot snapshot
    JOIN rag_repository repository ON repository.id = snapshot.repository_id
    WHERE snapshot.commit_sha = repository.active_commit_sha
      AND snapshot.embedding_model = repository.embedding_model
      AND snapshot.embedding_dimensions = repository.embedding_dimensions
      AND snapshot.index_version = repository.index_version
)
UPDATE rag_document document
SET snapshot_id = snapshot.snapshot_id
FROM provable_snapshot snapshot
WHERE snapshot.repository_id = document.repository_id
  AND snapshot.commit_sha = document.commit_sha;

UPDATE rag_chunk chunk
SET snapshot_id = document.snapshot_id
FROM rag_document document
WHERE document.id = chunk.document_id
  AND document.snapshot_id IS NOT NULL;

ALTER TABLE rag_document
    DROP CONSTRAINT uq_rag_document_repository_commit_path,
    ADD CONSTRAINT fk_rag_document_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES rag_index_snapshot (id),
    ADD CONSTRAINT uq_rag_document_snapshot_path UNIQUE (snapshot_id, path);

ALTER TABLE rag_chunk
    DROP CONSTRAINT uq_rag_chunk_repository_commit_key,
    ADD CONSTRAINT fk_rag_chunk_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES rag_index_snapshot (id),
    ADD CONSTRAINT uq_rag_chunk_snapshot_key UNIQUE (snapshot_id, chunk_key);

CREATE INDEX idx_rag_document_snapshot
    ON rag_document (snapshot_id, path);

CREATE INDEX idx_rag_chunk_snapshot_identity
    ON rag_chunk (snapshot_id, path, start_line);
