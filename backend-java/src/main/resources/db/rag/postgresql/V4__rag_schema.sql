CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE rag_repository (
    id                      BIGSERIAL     PRIMARY KEY,
    provider                VARCHAR(32)   NOT NULL,
    owner_name              VARCHAR(255)  NOT NULL,
    repository_name         VARCHAR(255)  NOT NULL,
    clone_url               VARCHAR(1000) NOT NULL,
    default_branch          VARCHAR(255),
    active_commit_sha       VARCHAR(64),
    index_status            VARCHAR(32)   NOT NULL,
    index_version           INT           NOT NULL DEFAULT 1,
    embedding_model         VARCHAR(255)  NOT NULL,
    embedding_dimensions    INT           NOT NULL,
    last_indexed_at         TIMESTAMP,
    created_at              TIMESTAMP     NOT NULL,
    updated_at              TIMESTAMP     NOT NULL,
    CONSTRAINT uq_rag_repository_provider_owner_name_repository_name
        UNIQUE (provider, owner_name, repository_name),
    CONSTRAINT ck_rag_repository_embedding_dimensions CHECK (embedding_dimensions = 1024)
);

CREATE TABLE rag_index_job (
    id                      BIGSERIAL     PRIMARY KEY,
    repository_id           BIGINT        NOT NULL,
    requested_ref           VARCHAR(255)  NOT NULL,
    resolved_commit_sha     VARCHAR(64),
    trigger_type            VARCHAR(32)   NOT NULL,
    status                  VARCHAR(32)   NOT NULL,
    attempt_count           INT           NOT NULL DEFAULT 0,
    discovered_file_count   INT           NOT NULL DEFAULT 0,
    indexed_file_count      INT           NOT NULL DEFAULT 0,
    indexed_chunk_count     INT           NOT NULL DEFAULT 0,
    skipped_file_count      INT           NOT NULL DEFAULT 0,
    error_code              VARCHAR(64),
    error_message           VARCHAR(1000),
    started_at              TIMESTAMP,
    finished_at             TIMESTAMP,
    created_at              TIMESTAMP     NOT NULL,
    CONSTRAINT fk_rag_index_job_repository
        FOREIGN KEY (repository_id) REFERENCES rag_repository (id)
);

CREATE TABLE rag_document (
    id              BIGSERIAL     PRIMARY KEY,
    repository_id   BIGINT        NOT NULL,
    commit_sha      VARCHAR(64)   NOT NULL,
    path            VARCHAR(1000) NOT NULL,
    language        VARCHAR(64)   NOT NULL,
    content_hash    VARCHAR(64)   NOT NULL,
    byte_size       BIGINT        NOT NULL,
    created_at      TIMESTAMP     NOT NULL,
    CONSTRAINT fk_rag_document_repository
        FOREIGN KEY (repository_id) REFERENCES rag_repository (id),
    CONSTRAINT uq_rag_document_repository_commit_path UNIQUE (repository_id, commit_sha, path)
);

CREATE TABLE rag_chunk (
    id              BIGSERIAL     PRIMARY KEY,
    repository_id   BIGINT        NOT NULL,
    document_id     BIGINT        NOT NULL,
    commit_sha      VARCHAR(64)   NOT NULL,
    chunk_key       VARCHAR(96)   NOT NULL,
    path            VARCHAR(1000) NOT NULL,
    language        VARCHAR(64)   NOT NULL,
    symbol_name     VARCHAR(500),
    start_line      INT           NOT NULL,
    end_line        INT           NOT NULL,
    content         TEXT          NOT NULL,
    token_count     INT           NOT NULL,
    content_hash    VARCHAR(64)   NOT NULL,
    embedding       vector(1024)  NOT NULL,
    search_vector   TSVECTOR GENERATED ALWAYS AS (
                        to_tsvector('simple', coalesce(path, '') || ' ' ||
                                              coalesce(symbol_name, '') || ' ' || content)
                    ) STORED,
    created_at      TIMESTAMP     NOT NULL,
    CONSTRAINT fk_rag_chunk_repository
        FOREIGN KEY (repository_id) REFERENCES rag_repository (id),
    CONSTRAINT fk_rag_chunk_document
        FOREIGN KEY (document_id) REFERENCES rag_document (id) ON DELETE CASCADE,
    CONSTRAINT uq_rag_chunk_repository_commit_key UNIQUE (repository_id, commit_sha, chunk_key)
);

CREATE INDEX idx_rag_chunk_embedding_hnsw
    ON rag_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_rag_chunk_search_vector_gin
    ON rag_chunk USING gin (search_vector);
CREATE INDEX idx_rag_chunk_snapshot
    ON rag_chunk (repository_id, commit_sha, path);

CREATE TABLE rag_retrieval_trace (
    id                          BIGSERIAL   PRIMARY KEY,
    review_run_id               BIGINT      NOT NULL,
    repository_id               BIGINT      NOT NULL,
    commit_sha                  VARCHAR(64) NOT NULL,
    query_hash                  VARCHAR(64) NOT NULL,
    vector_candidate_count      INT         NOT NULL,
    lexical_candidate_count     INT         NOT NULL,
    reranked_count              INT         NOT NULL,
    selected_count              INT         NOT NULL,
    context_char_count          INT         NOT NULL,
    degraded                    BOOLEAN     NOT NULL,
    latency_ms                  BIGINT      NOT NULL,
    result_summary_json         TEXT        NOT NULL,
    created_at                  TIMESTAMP   NOT NULL,
    CONSTRAINT fk_rag_retrieval_trace_review_run
        FOREIGN KEY (review_run_id) REFERENCES review_run (id),
    CONSTRAINT fk_rag_retrieval_trace_repository
        FOREIGN KEY (repository_id) REFERENCES rag_repository (id)
);

CREATE TABLE review_issue_evidence (
    id                  BIGSERIAL       PRIMARY KEY,
    review_issue_id     BIGINT          NOT NULL,
    rag_chunk_id        BIGINT,
    citation_label      VARCHAR(32)     NOT NULL,
    path                VARCHAR(1000)   NOT NULL,
    start_line          INT             NOT NULL,
    end_line            INT             NOT NULL,
    content_hash        VARCHAR(64)     NOT NULL,
    evidence_excerpt    VARCHAR(2000)   NOT NULL,
    retrieval_rank      INT             NOT NULL,
    retrieval_score     DOUBLE PRECISION NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    CONSTRAINT fk_review_issue_evidence_issue
        FOREIGN KEY (review_issue_id) REFERENCES review_issue (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_issue_evidence_chunk
        FOREIGN KEY (rag_chunk_id) REFERENCES rag_chunk (id) ON DELETE SET NULL,
    CONSTRAINT uq_review_issue_evidence_issue_citation_label
        UNIQUE (review_issue_id, citation_label)
);
