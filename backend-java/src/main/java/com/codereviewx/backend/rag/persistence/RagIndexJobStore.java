package com.codereviewx.backend.rag.persistence;

import com.codereviewx.backend.rag.service.RagIndexJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "codereviewx.rag", name = "enabled", havingValue = "true")
public class RagIndexJobStore {

    private final JdbcTemplate jdbc;

    public RagIndexJobStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long create(long repositoryId, String requestedRef, String triggerType, String embeddingModel,
                       int embeddingDimensions, int indexVersion) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO rag_index_job
                      (repository_id, requested_ref, trigger_type, status, embedding_model,
                       embedding_dimensions, index_version, created_at)
                    VALUES (?, ?, ?, 'QUEUED', ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, repositoryId);
            statement.setString(2, requestedRef);
            statement.setString(3, triggerType);
            statement.setString(4, embeddingModel);
            statement.setInt(5, embeddingDimensions);
            statement.setInt(6, indexVersion);
            statement.setTimestamp(7, Timestamp.valueOf(now()));
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    public Optional<RagIndexJob> get(long id) {
        return query("SELECT * FROM rag_index_job WHERE id=?", id).stream().findFirst();
    }

    public Optional<RagIndexJob> findReadySnapshot(long repositoryId, String commitSha, String embeddingModel,
                                                   int embeddingDimensions, int indexVersion) {
        return query("""
                SELECT job.*
                FROM rag_index_snapshot snapshot
                JOIN rag_index_job job ON job.id=snapshot.job_id
                WHERE snapshot.repository_id=? AND snapshot.commit_sha=?
                  AND snapshot.embedding_model=? AND snapshot.embedding_dimensions=?
                  AND snapshot.index_version=? AND job.status='READY'
                LIMIT 1
                """, repositoryId, commitSha, embeddingModel, embeddingDimensions, indexVersion)
                .stream().findFirst();
    }

    public long createSnapshot(long jobId, long repositoryId, String commitSha, String embeddingModel,
                               int embeddingDimensions, int indexVersion) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO rag_index_snapshot
                  (repository_id, job_id, commit_sha, embedding_model, embedding_dimensions,
                   index_version, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, new String[]{"id"});
            statement.setLong(1, repositoryId);
            statement.setLong(2, jobId);
            statement.setString(3, commitSha);
            statement.setString(4, embeddingModel);
            statement.setInt(5, embeddingDimensions);
            statement.setInt(6, indexVersion);
            statement.setTimestamp(7, Timestamp.valueOf(now()));
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    public Optional<SnapshotRecord> findSnapshot(long repositoryId, String commitSha, String embeddingModel,
                                                 int embeddingDimensions, int indexVersion) {
        return jdbc.query("""
                SELECT id, job_id, repository_id, commit_sha, embedding_model,
                       embedding_dimensions, index_version
                FROM rag_index_snapshot
                WHERE repository_id=? AND commit_sha=? AND embedding_model=?
                  AND embedding_dimensions=? AND index_version=?
                """, (result, row) -> new SnapshotRecord(result.getLong("id"), result.getLong("job_id"),
                        result.getLong("repository_id"), result.getString("commit_sha"),
                        result.getString("embedding_model"), result.getInt("embedding_dimensions"),
                        result.getInt("index_version")), repositoryId, commitSha, embeddingModel,
                embeddingDimensions, indexVersion).stream().findFirst();
    }

    public Optional<RagIndexJob> claimNextQueued() {
        List<RagIndexJob> claimed = jdbc.query("""
                WITH candidate AS (
                    SELECT queued.id
                    FROM rag_index_job queued
                    JOIN rag_repository repository ON repository.id=queued.repository_id
                    WHERE queued.status='QUEUED'
                      AND NOT EXISTS (
                          SELECT 1 FROM rag_index_job running
                          WHERE running.repository_id=queued.repository_id AND running.status='RUNNING'
                    )
                    ORDER BY queued.created_at, queued.id
                    FOR UPDATE OF queued, repository SKIP LOCKED
                    LIMIT 1
                )
                UPDATE rag_index_job job
                SET status='RUNNING', attempt_count=attempt_count+1, started_at=?,
                    finished_at=NULL, error_code=NULL, error_message=NULL
                FROM candidate
                WHERE job.id=candidate.id
                RETURNING job.*
                """, (result, row) -> map(result), now());
        return claimed.stream().findFirst();
    }

    public void transition(long id, RagIndexJob.Status expected, RagIndexJob.Status target) {
        boolean valid = expected == RagIndexJob.Status.QUEUED && target == RagIndexJob.Status.RUNNING
                || expected == RagIndexJob.Status.RUNNING
                && (target == RagIndexJob.Status.READY || target == RagIndexJob.Status.FAILED);
        if (!valid || jdbc.update("UPDATE rag_index_job SET status=? WHERE id=? AND status=?",
                target.name(), id, expected.name()) != 1) {
            throw new IllegalStateException("Invalid RAG index job transition");
        }
    }

    public void complete(long id, String resolvedCommitSha, int discoveredFiles, int indexedFiles,
                         int indexedChunks, int skippedFiles) {
        int updated = jdbc.update("""
                UPDATE rag_index_job
                SET status='READY', resolved_commit_sha=?, discovered_file_count=?, indexed_file_count=?,
                    indexed_chunk_count=?, skipped_file_count=?, finished_at=?
                WHERE id=? AND status='RUNNING'
                """, resolvedCommitSha, discoveredFiles, indexedFiles, indexedChunks, skippedFiles, now(), id);
        if (updated != 1) {
            throw new IllegalStateException("Invalid RAG index job transition");
        }
    }

    public void fail(long id, String errorCode, String errorMessage) {
        int updated = jdbc.update("""
                UPDATE rag_index_job
                SET status='FAILED', error_code=?, error_message=?, finished_at=?
                WHERE id=? AND status='RUNNING'
                """, errorCode, truncate(errorMessage), now(), id);
        if (updated != 1) {
            throw new IllegalStateException("Invalid RAG index job transition");
        }
    }

    public int recoverStale(Duration age, LocalDateTime currentTime) {
        LocalDateTime cutoff = currentTime.minus(age);
        int queued = jdbc.update("""
                UPDATE rag_index_job
                SET status='QUEUED', started_at=NULL, error_code='STALE_RECOVERED', error_message=NULL
                WHERE status='RUNNING' AND started_at < ? AND attempt_count < 3
                """, cutoff);
        int failed = jdbc.update("""
                UPDATE rag_index_job
                SET status='FAILED', finished_at=?, error_code='ATTEMPTS_EXHAUSTED',
                    error_message='Index job exceeded recovery attempt limit'
                WHERE status='RUNNING' AND started_at < ? AND attempt_count >= 3
                """, currentTime, cutoff);
        return queued + failed;
    }

    private List<RagIndexJob> query(String sql, Object... arguments) {
        return jdbc.query(sql, (result, row) -> map(result), arguments);
    }

    private static RagIndexJob map(java.sql.ResultSet result) throws java.sql.SQLException {
        Timestamp started = result.getTimestamp("started_at");
        Timestamp finished = result.getTimestamp("finished_at");
        return new RagIndexJob(result.getLong("id"), result.getLong("repository_id"),
                result.getString("requested_ref"), result.getString("resolved_commit_sha"),
                RagIndexJob.Status.valueOf(result.getString("status")), result.getInt("attempt_count"),
                started == null ? null : started.toLocalDateTime(), finished == null ? null : finished.toLocalDateTime(),
                result.getString("error_code"), result.getString("error_message"),
                result.getString("embedding_model"), result.getInt("embedding_dimensions"),
                result.getInt("index_version"));
    }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "Indexing failed";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public record SnapshotRecord(long id, long jobId, long repositoryId, String commitSha,
                                 String embeddingModel, int embeddingDimensions, int indexVersion) {
    }
}
