package com.codereviewx.backend.rag.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Idempotent bounded retention for immutable RAG snapshots. */
@Service
public class RagRetentionService {
    private final JdbcTemplate jdbc;
    public RagRetentionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Scheduled(cron = "${codereviewx.rag.retention.cleanup-cron:0 15 2 * * *}")
    @Transactional
    public int cleanup() {
        // Keep the latest five commits and anything newer than thirty days. READY only;
        // RUNNING jobs and the repository's active commit are explicitly protected.
        return jdbc.update("""
            DELETE FROM rag_index_snapshot snapshot
            WHERE snapshot.created_at < CURRENT_TIMESTAMP - INTERVAL '30 days'
              AND NOT EXISTS (SELECT 1 FROM rag_repository r
                              WHERE r.id=snapshot.repository_id
                                AND r.active_commit_sha=snapshot.commit_sha)
              AND NOT EXISTS (SELECT 1 FROM rag_index_job j
                              WHERE j.id=snapshot.job_id AND j.status IN ('RUNNING','QUEUED'))
              AND snapshot.id NOT IN (
                SELECT id FROM (SELECT s.id, ROW_NUMBER() OVER
                  (PARTITION BY s.repository_id ORDER BY s.created_at DESC, s.id DESC) AS rn
                  FROM rag_index_snapshot s JOIN rag_index_job j ON j.id=s.job_id
                  WHERE j.status='READY') retained WHERE rn <= 5)
            """);
    }
}
