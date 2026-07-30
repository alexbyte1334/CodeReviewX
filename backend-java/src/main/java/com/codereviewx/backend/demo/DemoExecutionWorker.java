package com.codereviewx.backend.demo;

import com.codereviewx.backend.review.dto.ReviewTaskResponse;
import com.codereviewx.backend.review.service.ReviewTaskService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;

@Component
public class DemoExecutionWorker {
    private final DemoStore store;
    private final DemoProperties properties;
    private final ReviewTaskService reviewTasks;
    private final ExecutorService executor;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName();

    public DemoExecutionWorker(DemoStore store, DemoProperties properties,
                               ReviewTaskService reviewTasks, ExecutorService demoWorkerExecutor) {
        this.store = store;
        this.properties = properties;
        this.reviewTasks = reviewTasks;
        this.executor = demoWorkerExecutor;
    }

    @Scheduled(fixedDelayString = "${codereviewx.demo.poll-interval-ms:500}")
    public void poll() {
        if (!properties.isEnabled()) return;
        for (int i = 0; i < properties.getGlobalConcurrency(); i++) {
            store.claimNext(workerId, LocalDateTime.now().plus(properties.getLeaseDuration()),
                    properties.getGlobalConcurrency()).ifPresent(job -> executor.submit(() -> execute(job)));
        }
    }

    private void execute(DemoStore.Job job) {
        long started = System.nanoTime();
        if (job.attempts() > 1) {
            store.resetForRetry(job);
        }
        store.markRunning(job);
        try {
            ReviewTaskResponse response =
                    reviewTasks.executeExistingGithubTask(job.taskId(), job.reviewRunId());
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            if (elapsedMs > properties.getExecutionDeadline().toMillis()) {
                store.markFailed(job, "DEMO_DEADLINE_EXCEEDED",
                        "Live model deadline exceeded " + properties.getExecutionDeadline().toSeconds() + " seconds.");
            } else if (response.getStatus() == null || !"SUCCESS".equals(response.getStatus().name())) {
                store.markFailed(job, "REVIEW_FAILED", "Review pipeline did not complete successfully.");
            } else {
                store.markSucceeded(job);
            }
        } catch (Exception ex) {
            String code = ex instanceof DemoApiException demo ? demo.getCode() : "DEMO_EXECUTION_FAILED";
            store.markFailed(job, code, ex.getMessage() == null ? "Live execution failed." : ex.getMessage());
        }
    }
}
