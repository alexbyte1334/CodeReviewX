CREATE TABLE rag_index_snapshot (
    id                      BIGSERIAL     PRIMARY KEY,
    repository_id           BIGINT        NOT NULL,
    job_id                  BIGINT        NOT NULL,
    commit_sha              VARCHAR(64)   NOT NULL,
    embedding_model         VARCHAR(255)  NOT NULL,
    embedding_dimensions    INT           NOT NULL,
    index_version           INT           NOT NULL,
    created_at              TIMESTAMP     NOT NULL,
    CONSTRAINT fk_rag_index_snapshot_repository
        FOREIGN KEY (repository_id) REFERENCES rag_repository (id),
    CONSTRAINT fk_rag_index_snapshot_job
        FOREIGN KEY (job_id) REFERENCES rag_index_job (id),
    CONSTRAINT uq_rag_index_snapshot_job UNIQUE (job_id),
    CONSTRAINT uq_rag_index_snapshot_identity
        UNIQUE (repository_id, commit_sha, embedding_model, embedding_dimensions, index_version),
    CONSTRAINT ck_rag_index_snapshot_embedding_dimensions CHECK (embedding_dimensions = 1024)
);

CREATE INDEX idx_rag_index_snapshot_identity
    ON rag_index_snapshot (repository_id, commit_sha, embedding_model, embedding_dimensions, index_version);

INSERT INTO rag_index_snapshot
    (repository_id, job_id, commit_sha, embedding_model, embedding_dimensions, index_version, created_at)
SELECT job.repository_id, job.id, job.resolved_commit_sha, repository.embedding_model,
       repository.embedding_dimensions, repository.index_version, job.finished_at
FROM rag_index_job job
JOIN rag_repository repository ON repository.id = job.repository_id
WHERE job.status = 'READY'
  AND job.resolved_commit_sha IS NOT NULL
  AND job.resolved_commit_sha = repository.active_commit_sha
  AND job.finished_at IS NOT NULL
ON CONFLICT DO NOTHING;
