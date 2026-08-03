package com.codereviewx.backend.review.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ReviewApiEventRecorder {
    private final JdbcTemplate jdbc;

    public ReviewApiEventRecorder(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(long runId, String type, String status, String summary) {
        jdbc.update("""
                INSERT INTO review_api_event(review_api_run_id, sequence_number, event_type, status, summary, created_at)
                SELECT ?, COALESCE(MAX(sequence_number), 0) + 1, ?, ?, ?, ? FROM review_api_event WHERE review_api_run_id=?
                """, runId, type, status, summary, LocalDateTime.now(), runId);
    }
}
