package com.codereviewx.backend.demo;

import com.codereviewx.backend.demo.DemoDtos.Event;
import com.codereviewx.backend.review.persistence.entity.ReviewToolTraceEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DemoStore {
    private final JdbcTemplate jdbc;

    public DemoStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public DemoRow create(String scenarioId, String idempotencyKey, String ipHash, long taskId, long runId) {
        String publicId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO demo_run(public_id,scenario_id,idempotency_key,requester_ip_hash,review_task_id,review_run_id,
                  status,created_at,updated_at)
                VALUES (?,?,?,?,?,?,'QUEUED',?,?)
                """, publicId, scenarioId, idempotencyKey, ipHash, taskId, runId, now, now);
        long demoId = jdbc.queryForObject(
                "SELECT id FROM demo_run WHERE public_id=?", Long.class, publicId);
        jdbc.update("""
                INSERT INTO review_execution_job(demo_run_id,status,attempt_count,next_retry_at,created_at)
                VALUES (?,'QUEUED',0,?,?)
                """, demoId, now, now);
        appendEvent(demoId, "RUN_QUEUED", "PR_INGEST", "QUEUED",
                "Live review accepted and queued.", null, null);
        return findByPublicId(publicId).orElseThrow();
    }

    public Optional<DemoRow> findByPublicId(String publicId) {
        List<DemoRow> rows = jdbc.query("""
                SELECT id,public_id,scenario_id,idempotency_key,requester_ip_hash,review_task_id,review_run_id,status,
                  decision,replay_reason,safe_diff_text,published_comment_url,created_at,updated_at,finished_at
                FROM demo_run WHERE public_id=?
                """, (rs, row) -> new DemoRow(
                rs.getLong("id"), rs.getString("public_id"), rs.getString("scenario_id"),
                rs.getString("idempotency_key"), rs.getString("requester_ip_hash"), rs.getLong("review_task_id"),
                rs.getLong("review_run_id"), rs.getString("status"), rs.getString("decision"),
                rs.getString("replay_reason"), rs.getString("safe_diff_text"),
                rs.getString("published_comment_url"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime(),
                toLocal(rs.getTimestamp("finished_at"))
        ), publicId);
        return rows.stream().findFirst();
    }

    public Optional<DemoRow> findByIdempotencyKey(String key) {
        List<String> ids = jdbc.query("SELECT public_id FROM demo_run WHERE idempotency_key=?",
                (rs, row) -> rs.getString(1), key);
        return ids.stream().findFirst().flatMap(this::findByPublicId);
    }

    public Optional<DemoRow> findByReviewRunId(long runId) {
        List<String> ids = jdbc.query("SELECT public_id FROM demo_run WHERE review_run_id=?",
                (rs, row) -> rs.getString(1), runId);
        return ids.stream().findFirst().flatMap(this::findByPublicId);
    }

    @Transactional
    public Optional<Job> claimNext(String workerId, LocalDateTime leaseExpiresAt, int globalLimit) {
        Integer running = jdbc.queryForObject(
                "SELECT COUNT(*) FROM review_execution_job WHERE status='RUNNING' AND lease_expires_at > ?",
                Integer.class, LocalDateTime.now());
        if (running != null && running >= globalLimit) return Optional.empty();
        List<Long> ids = jdbc.query("""
                SELECT id FROM review_execution_job
                WHERE (status='QUEUED' OR (status='RUNNING' AND lease_expires_at < ?))
                  AND (next_retry_at IS NULL OR next_retry_at <= ?)
                ORDER BY created_at LIMIT 1
                """, (rs, row) -> rs.getLong(1), LocalDateTime.now(), LocalDateTime.now());
        if (ids.isEmpty()) return Optional.empty();
        long id = ids.get(0);
        int changed = jdbc.update("""
                UPDATE review_execution_job SET status='RUNNING', attempt_count=attempt_count+1,
                  lease_owner=?,heartbeat_at=?,lease_expires_at=?,started_at=COALESCE(started_at,?)
                WHERE id=? AND (status='QUEUED' OR lease_expires_at < ?)
                """, workerId, LocalDateTime.now(), leaseExpiresAt, LocalDateTime.now(), id, LocalDateTime.now());
        if (changed != 1) return Optional.empty();
        return Optional.of(jdbc.queryForObject("""
                SELECT j.id,j.demo_run_id,j.attempt_count,d.public_id,d.review_task_id,d.review_run_id
                FROM review_execution_job j JOIN demo_run d ON d.id=j.demo_run_id WHERE j.id=?
                """, (rs, row) -> new Job(rs.getLong(1), rs.getLong(2), rs.getInt(3),
                rs.getString(4), rs.getLong(5), rs.getLong(6)), id));
    }

    public void heartbeat(long jobId, String workerId, LocalDateTime leaseExpiresAt) {
        jdbc.update("""
                UPDATE review_execution_job SET heartbeat_at=?,lease_expires_at=?
                WHERE id=? AND lease_owner=? AND status='RUNNING'
                """, LocalDateTime.now(), leaseExpiresAt, jobId, workerId);
    }

    @Transactional
    public void resetForRetry(Job job) {
        // A stale lease means the previous process may have stopped between any two
        // idempotent projections. Clear that run's projections before re-executing.
        try {
            jdbc.update("""
                    DELETE FROM review_issue_evidence WHERE review_issue_id IN
                      (SELECT id FROM review_issue WHERE review_run_id=?)
                    """, job.reviewRunId());
        } catch (DataAccessException ignored) {
            // The local H2 profile intentionally does not install PostgreSQL RAG tables.
        }
        jdbc.update("DELETE FROM review_comment_preview WHERE review_run_id=?", job.reviewRunId());
        jdbc.update("DELETE FROM review_issue WHERE review_run_id=?", job.reviewRunId());
        jdbc.update("DELETE FROM review_provider_trace WHERE review_run_id=?", job.reviewRunId());
        jdbc.update("DELETE FROM review_tool_trace WHERE review_run_id=?", job.reviewRunId());
        jdbc.update("DELETE FROM review_input_snapshot WHERE review_run_id=?", job.reviewRunId());
        jdbc.update("""
                UPDATE review_run SET status='PENDING',requested_provider=NULL,provider_used=NULL,
                  provider_hit=NULL,error_code=NULL,error_message=NULL,finished_at=NULL,updated_at=?
                WHERE id=?
                """, LocalDateTime.now(), job.reviewRunId());
        jdbc.update("""
                UPDATE review_task SET status='PENDING',summary=NULL,requested_provider=NULL,
                  provider_used=NULL,provider_hit=NULL,error_message=NULL,updated_at=? WHERE id=?
                """, LocalDateTime.now(), job.taskId());
        jdbc.update("UPDATE demo_run SET safe_diff_text=NULL,replay_reason=NULL,updated_at=? WHERE id=?",
                LocalDateTime.now(), job.demoRunId());
        appendEvent(job.demoRunId(), "LEASE_RECOVERED", "PR_INGEST", "RETRYING",
                "Expired worker lease was recovered; partial projections were reset idempotently.",
                null, null);
    }

    @Transactional
    public void markRunning(Job job) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("UPDATE demo_run SET status='RUNNING',updated_at=? WHERE id=?",
                now, job.demoRunId());
        appendEvent(job.demoRunId(), "RUN_STARTED", "PR_INGEST", "RUNNING",
                "Worker claimed the durable execution lease.", null, null);
    }

    @Transactional
    public void markSucceeded(Job job) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("UPDATE demo_run SET status='READY',updated_at=?,finished_at=? WHERE id=?",
                now, now, job.demoRunId());
        jdbc.update("""
                UPDATE review_execution_job SET status='SUCCEEDED',finished_at=?,lease_expires_at=NULL
                WHERE id=?
                """, now, job.id());
        appendEvent(job.demoRunId(), "RUN_READY", "HUMAN_REVIEW", "READY",
                "Evidence-backed comment previews are ready for human review.", null, null);
    }

    @Transactional
    public void markFailed(Job job, String code, String message) {
        LocalDateTime now = LocalDateTime.now();
        String safeMessage = truncate(message, 900);
        jdbc.update("""
                UPDATE demo_run SET status='FAILED',replay_reason=?,updated_at=?,finished_at=? WHERE id=?
                """, safeMessage, now, now, job.demoRunId());
        jdbc.update("""
                UPDATE review_execution_job SET status='FAILED',error_code=?,error_message=?,
                  finished_at=?,lease_expires_at=NULL WHERE id=?
                """, code, safeMessage, now, job.id());
        appendEvent(job.demoRunId(), "RUN_FAILED", null, "FAILED",
                "Live execution failed. The client must switch to explicit Replay Mode.", code, null);
    }

    public void captureDiff(long reviewRunId, String diffText) {
        jdbc.update("UPDATE demo_run SET safe_diff_text=?,updated_at=? WHERE review_run_id=?",
                truncate(diffText, 100_000), LocalDateTime.now(), reviewRunId);
    }

    @Transactional
    public Event appendTrace(ReviewToolTraceEntity trace) {
        DemoRow demo = findByReviewRunId(trace.getReviewRunId()).orElse(null);
        if (demo == null) return null;
        return appendEvent(demo.id(), "TOOL_COMPLETED", mapStep(trace.getToolName()),
                trace.getStatus().name(), DemoRedactor.sanitize(trace.getOutputSummary(), 1900),
                trace.getErrorCode(), trace.getDurationMs());
    }

    @Transactional
    public Event appendEvent(long demoId, String type, String step, String status,
                             String summary, String errorCode, Long durationMs) {
        Long sequence = jdbc.queryForObject("""
                SELECT COALESCE(MAX(sequence_number),0)+1 FROM review_run_event WHERE demo_run_id=?
                """, Long.class, demoId);
        LocalDateTime now = LocalDateTime.now();
        try {
            jdbc.update("""
                    INSERT INTO review_run_event(demo_run_id,sequence_number,event_type,step,status,
                      summary,error_code,duration_ms,created_at) VALUES (?,?,?,?,?,?,?,?,?)
                    """, demoId, sequence, type, step, status, truncate(summary, 1900),
                    errorCode, durationMs, now);
        } catch (DuplicateKeyException ex) {
            return appendEvent(demoId, type, step, status, summary, errorCode, durationMs);
        }
        return new Event(sequence, type, step, status, truncate(summary, 1900),
                errorCode, durationMs, now);
    }

    public List<Event> events(long demoId, long after) {
        return jdbc.query("""
                SELECT sequence_number,event_type,step,status,summary,error_code,duration_ms,created_at
                FROM review_run_event WHERE demo_run_id=? AND sequence_number>?
                ORDER BY sequence_number
                """, (rs, row) -> new Event(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6),
                (Long) rs.getObject(7), rs.getTimestamp(8).toLocalDateTime()), demoId, after);
    }

    public int activeRuns() {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM demo_run WHERE status IN ('QUEUED','RUNNING')", Integer.class);
        return value == null ? 0 : value;
    }

    public int activeRunsForIp(String ipHash) {
        Integer value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM demo_run
                WHERE requester_ip_hash=? AND status IN ('QUEUED','RUNNING')
                """, Integer.class, ipHash);
        return value == null ? 0 : value;
    }

    @Transactional
    public int incrementRateBucket(String ipHash, LocalDateTime windowStart) {
        int changed = jdbc.update("""
                UPDATE demo_request_bucket SET request_count=request_count+1,updated_at=?
                WHERE ip_hash=? AND window_start=?
                """, LocalDateTime.now(), ipHash, windowStart);
        if (changed == 0) {
            jdbc.update("""
                    INSERT INTO demo_request_bucket(ip_hash,window_start,request_count,updated_at)
                    VALUES (?,?,1,?)
                    """, ipHash, windowStart, LocalDateTime.now());
        }
        return jdbc.queryForObject("""
                SELECT request_count FROM demo_request_bucket WHERE ip_hash=? AND window_start=?
                """, Integer.class, ipHash, windowStart);
    }

    public void setDecision(long demoId, String decision) {
        jdbc.update("UPDATE demo_run SET decision=?,updated_at=? WHERE id=?",
                decision, LocalDateTime.now(), demoId);
        appendEvent(demoId, "DECISION_UPDATED", "HUMAN_REVIEW", decision,
                "Anonymous user updated the preview decision; no GitHub write was performed.", null, null);
    }

    public JdbcTemplate jdbc() { return jdbc; }

    private String mapStep(String tool) {
        if (tool == null) return "AI_REVIEW";
        if (tool.startsWith("github.")) return "PR_INGEST";
        if (tool.contains("index")) return "REPOSITORY_INDEX";
        if (tool.startsWith("rag.")) return "HYBRID_RAG";
        if (tool.contains("plan")) return "AI_PLAN";
        if (tool.contains("gate") || tool.contains("evidence")) return "EVIDENCE_GATE";
        if (tool.contains("preview")) return "HUMAN_REVIEW";
        return "AI_REVIEW";
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max) + "…";
    }
    private LocalDateTime toLocal(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }

    public record DemoRow(long id, String publicId, String scenarioId, String idempotencyKey, String requesterIpHash,
                          long taskId, long reviewRunId, String status, String decision,
                          String replayReason, String safeDiffText, String publishedCommentUrl,
                          LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime finishedAt) {}
    public record Job(long id, long demoRunId, int attempts, String publicId, long taskId, long reviewRunId) {}
}
