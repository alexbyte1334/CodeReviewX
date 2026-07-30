package com.codereviewx.backend.demo;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class DemoRetentionService {
    private final DemoStore store;

    public DemoRetentionService(DemoStore store) {
        this.store = store;
    }

    @Scheduled(cron = "${codereviewx.demo.retention-cron:0 30 2 * * *}")
    @Transactional
    public void cleanup() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        var jdbc = store.jdbc();
        jdbc.update("""
                DELETE FROM review_run_event WHERE demo_run_id IN
                  (SELECT id FROM demo_run WHERE created_at < ?)
                """, sevenDaysAgo);
        jdbc.update("""
                DELETE FROM review_execution_job WHERE demo_run_id IN
                  (SELECT id FROM demo_run WHERE created_at < ?)
                """, sevenDaysAgo);
        jdbc.update("DELETE FROM demo_run WHERE created_at < ?", sevenDaysAgo);
        jdbc.update("DELETE FROM demo_request_bucket WHERE updated_at < ?", oneDayAgo);
    }
}
