ALTER TABLE rag_retrieval_trace
    RENAME COLUMN review_run_id TO review_api_run_id;

ALTER TABLE rag_retrieval_trace
    DROP CONSTRAINT IF EXISTS fk_rag_retrieval_trace_review_run;

ALTER TABLE rag_retrieval_trace
    ADD CONSTRAINT fk_rag_retrieval_trace_review_run
        FOREIGN KEY (review_api_run_id) REFERENCES review_api_run(id) ON DELETE CASCADE;

ALTER TABLE review_issue_evidence
    ADD CONSTRAINT fk_review_issue_evidence_chunk
        FOREIGN KEY (rag_chunk_id) REFERENCES rag_chunk(id) ON DELETE SET NULL;
