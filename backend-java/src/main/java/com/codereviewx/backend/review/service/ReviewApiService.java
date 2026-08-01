package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.dto.CreateReviewRequest;
import com.codereviewx.backend.review.dto.CreateReviewTaskRequest;
import com.codereviewx.backend.review.dto.ReviewApiSnapshot;
import com.codereviewx.backend.review.dto.ReviewTaskResponse;
import com.codereviewx.backend.review.exception.ReviewRequestInvalidException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class ReviewApiService {
    private final JdbcTemplate jdbc;
    private final ReviewTaskService tasks;
    private final ConcurrentMap<Long, Boolean> active = new ConcurrentHashMap<>();

    public ReviewApiService(JdbcTemplate jdbc, ReviewTaskService tasks) {
        this.jdbc = jdbc;
        this.tasks = tasks;
    }

    @Transactional
    public ReviewApiSnapshot create(CreateReviewRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new ReviewRequestInvalidException("Idempotency-Key is required and must be <=128 characters");
        }
        if (request == null || request.getInputMode() == null
                || !("GITHUB_PR".equalsIgnoreCase(request.getInputMode())
                || "MANUAL_DIFF".equalsIgnoreCase(request.getInputMode()))) {
            throw new ReviewRequestInvalidException("inputMode must be GITHUB_PR or MANUAL_DIFF");
        }
        var existing = findByIdempotency(idempotencyKey);
        if (existing != null) return existing;
        CreateReviewTaskRequest taskRequest = new CreateReviewTaskRequest();
        taskRequest.setRepoUrl(request.getRepositoryUrl());
        taskRequest.setPrNumber(request.getPrNumber());
        taskRequest.setDiffText(request.getDiffText());
        if ("MANUAL_DIFF".equalsIgnoreCase(request.getInputMode())) {
            taskRequest.setReviewMode(com.codereviewx.backend.review.enums.ReviewMode.MANUAL_DIFF);
        }
        var pending = tasks.createPendingTask(taskRequest);
        String publicId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        long apiId;
        try {
            jdbc.update("""
                INSERT INTO review_api_run(public_id,idempotency_key,review_task_id,review_run_id,status,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?)""", publicId, idempotencyKey, pending.task().getId(), pending.run().getId(), "QUEUED", now, now);
            apiId = jdbc.queryForObject("SELECT id FROM review_api_run WHERE public_id=?", Long.class, publicId);
        } catch (DuplicateKeyException ex) {
            return findByIdempotency(idempotencyKey);
        }
        appendEvent(apiId, "RUN_QUEUED", "QUEUED", "Review accepted and queued.", null);
        enqueueAfterCommit(apiId, publicId, pending.task().getId(), pending.run().getId());
        return snapshot(publicId);
    }

    public CompletableFuture<Void> runAsync(long apiId, String publicId, long taskId, long runId) {
        if (active.putIfAbsent(apiId, Boolean.TRUE) != null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
          try {
            mark(apiId, "RUNNING", null, null);
            appendEvent(apiId, "RUN_STARTED", "RUNNING", "Worker started the review pipeline.", null);
            ReviewTaskResponse response = tasks.executeExistingTask(taskId, runId);
            if (response.getStatus() == null || !"SUCCESS".equals(response.getStatus().name())) {
                mark(apiId, "FAILED", response.getErrorCode(), response.getErrorMessage());
                appendEvent(apiId, "RUN_FAILED", "FAILED", "Review pipeline failed.", response.getErrorCode());
            } else {
                mark(apiId, "SUCCEEDED", null, null);
                appendEvent(apiId, "RUN_SUCCEEDED", "SUCCEEDED", "Review completed with evidence-backed results.", null);
            }
          } catch (Exception ex) {
            mark(apiId, "FAILED", "REVIEW_EXECUTION_FAILED", safe(ex.getMessage()));
            appendEvent(apiId, "RUN_FAILED", "FAILED", "Review execution failed.", "REVIEW_EXECUTION_FAILED");
          }
          finally { active.remove(apiId); }
        });
    }

    /** Recovers queued or abandoned API runs after a process restart. */
    @Scheduled(fixedDelayString = "${codereviewx.review-api.recovery-interval-ms:5000}")
    public void recoverPending() {
        jdbc.query("""
            SELECT id,public_id,review_task_id,review_run_id FROM review_api_run
            WHERE status='QUEUED' OR (status='RUNNING' AND updated_at < ?)
            ORDER BY created_at LIMIT 4""", (rs, n) -> new Object[] {rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getLong(4)},
                LocalDateTime.now().minusMinutes(2)).forEach(row ->
                runAsync(((Number) row[0]).longValue(), (String) row[1],
                        ((Number) row[2]).longValue(), ((Number) row[3]).longValue()));
    }

    @Transactional
    public ReviewApiSnapshot retry(String publicId) {
        var row = row(publicId);
        if (!"FAILED".equals(row.status())) throw new ReviewRequestInvalidException("Only failed runs can be retried");
        resetProjections(row);
        mark(row.apiId(), "QUEUED", null, null);
        appendEvent(row.apiId(), "RUN_RETRY_QUEUED", "QUEUED", "Retry requested by the user.", null);
        enqueueAfterCommit(row.apiId(), publicId, row.taskId(), row.runId());
        return snapshot(publicId);
    }

    private void enqueueAfterCommit(long apiId, String publicId, long taskId, long runId) {
        Runnable enqueue = () -> runAsync(apiId, publicId, taskId, runId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { enqueue.run(); }
            });
        } else {
            enqueue.run();
        }
    }

    @Transactional(readOnly = true)
    public ReviewApiSnapshot snapshot(String publicId) {
        Row row = row(publicId);
        ReviewTaskResponse review = tasks.getTask(row.taskId());
        List<ReviewApiSnapshot.ReviewApiEvent> events = jdbc.query("""
            SELECT sequence_number,event_type,status,summary,error_code FROM review_api_event
            WHERE review_api_run_id=? ORDER BY sequence_number""", (rs, n) ->
            new ReviewApiSnapshot.ReviewApiEvent(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)), row.apiId());
        String path = "/api/reviews/" + row.publicId();
        return new ReviewApiSnapshot(row.publicId(), row.status(), "LIVE", row.repoUrl(), row.prNumber(),
                row.taskId(), row.runId(), path, path + "/events", review, events, row.errorCode(), row.errorMessage());
    }

    public List<ReviewApiSnapshot.ReviewApiEvent> events(String publicId, long after) {
        Row row = row(publicId);
        return jdbc.query("""
            SELECT sequence_number,event_type,status,summary,error_code FROM review_api_event
            WHERE review_api_run_id=? AND sequence_number>? ORDER BY sequence_number""", (rs, n) ->
            new ReviewApiSnapshot.ReviewApiEvent(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)), row.apiId(), after);
    }

    private ReviewApiSnapshot findByIdempotency(String key) {
        List<String> ids = jdbc.query("SELECT public_id FROM review_api_run WHERE idempotency_key=?", (rs, n) -> rs.getString(1), key);
        return ids.isEmpty() ? null : snapshot(ids.get(0));
    }
    private void mark(long id, String status, String code, String message) {
        jdbc.update("UPDATE review_api_run SET status=?,error_code=?,error_message=?,updated_at=? WHERE id=?", status, code, safe(message), LocalDateTime.now(), id);
    }
    private void resetProjections(Row row) {
        try { jdbc.update("DELETE FROM review_issue_evidence WHERE review_issue_id IN (SELECT id FROM review_issue WHERE review_run_id=?)", row.runId()); }
        catch (RuntimeException ignored) { }
        jdbc.update("DELETE FROM review_comment_preview WHERE review_run_id=?", row.runId());
        jdbc.update("DELETE FROM review_issue WHERE review_run_id=?", row.runId());
        jdbc.update("DELETE FROM review_provider_trace WHERE review_run_id=?", row.runId());
        jdbc.update("DELETE FROM review_tool_trace WHERE review_run_id=?", row.runId());
        jdbc.update("DELETE FROM review_input_snapshot WHERE review_run_id=?", row.runId());
        jdbc.update("UPDATE review_run SET status='PENDING',error_code=NULL,error_message=NULL,finished_at=NULL,updated_at=? WHERE id=?", LocalDateTime.now(), row.runId());
        jdbc.update("UPDATE review_task SET status='RUNNING',summary=NULL,error_message=NULL,updated_at=? WHERE id=?", LocalDateTime.now(), row.taskId());
    }
    private void appendEvent(long id, String type, String status, String summary, String code) {
        Long next = jdbc.queryForObject("SELECT COALESCE(MAX(sequence_number),0)+1 FROM review_api_event WHERE review_api_run_id=?", Long.class, id);
        jdbc.update("INSERT INTO review_api_event(review_api_run_id,sequence_number,event_type,status,summary,error_code,created_at) VALUES (?,?,?,?,?,?,?)", id, next, type, status, summary, code, LocalDateTime.now());
    }
    private Row row(String publicId) {
        try {
            return jdbc.queryForObject("SELECT r.id,r.public_id,r.status,r.error_code,r.error_message,r.review_task_id,r.review_run_id,t.repo_url,t.pr_number FROM review_api_run r JOIN review_task t ON t.id=r.review_task_id WHERE r.public_id=?", (rs, n) -> new Row(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getLong(6),rs.getLong(7),rs.getString(8),rs.getInt(9)), publicId);
        } catch (Exception ex) { throw new ReviewRequestInvalidException("Review run not found"); }
    }
    private String safe(String value) { return value == null ? null : value.length() > 900 ? value.substring(0, 900) : value; }
    private record Row(long apiId, String publicId, String status, String errorCode, String errorMessage, long taskId, long runId, String repoUrl, int prNumber) {}
}
