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
        String doomed = """
            SELECT snapshot.id FROM rag_index_snapshot snapshot
            WHERE snapshot.created_at < CURRENT_TIMESTAMP - INTERVAL '30 days'
              AND NOT EXISTS (SELECT 1 FROM rag_repository r
                              WHERE r.id=snapshot.repository_id
                                AND r.active_commit_sha=snapshot.commit_sha)
              AND NOT EXISTS (SELECT 1 FROM rag_index_job j
                              WHERE j.id=snapshot.job_id AND j.status IN ('RUNNING','QUEUED'))
              AND snapshot.id NOT IN (
                SELECT s.id FROM rag_index_snapshot s
                JOIN rag_index_job j ON j.id=s.job_id
                JOIN (SELECT repository_id, commit_sha,
                             DENSE_RANK() OVER (PARTITION BY repository_id
                               ORDER BY newest_snapshot DESC, commit_sha DESC) AS commit_rank
                      FROM (SELECT ready.repository_id, ready.commit_sha,
                                   MAX(ready.created_at) AS newest_snapshot
                            FROM rag_index_snapshot ready
                            JOIN rag_index_job ready_job ON ready_job.id=ready.job_id
                            WHERE ready_job.status='READY'
                            GROUP BY ready.repository_id, ready.commit_sha) commits) retained
                  ON retained.repository_id=s.repository_id AND retained.commit_sha=s.commit_sha
                WHERE j.status='READY' AND retained.commit_rank <= 5)
            """;
        // Explicit child-first cleanup keeps this correct on schemas without ON DELETE CASCADE.
        jdbc.update("DELETE FROM rag_chunk WHERE snapshot_id IN (" + doomed + ")");
        jdbc.update("DELETE FROM rag_document WHERE snapshot_id IN (" + doomed + ")");
        return jdbc.update("DELETE FROM rag_index_snapshot WHERE id IN (" + doomed + ")");
    }
}
