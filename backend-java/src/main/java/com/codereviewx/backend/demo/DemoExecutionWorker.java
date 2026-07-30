package com.codereviewx.backend.demo;

import com.codereviewx.backend.review.dto.ReviewTaskResponse;
import com.codereviewx.backend.review.service.ReviewTaskService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class DemoExecutionWorker {
    private final DemoStore store;
    private final DemoProperties properties;
    private final ReviewTaskService reviewTasks;
    private final ExecutorService dispatcher;
    private final ExecutorService pipeline;
    private final ScheduledExecutorService heartbeats;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName();

    public DemoExecutionWorker(DemoStore store, DemoProperties properties,
                               ReviewTaskService reviewTasks,
                               @Qualifier("demoWorkerExecutor") ExecutorService demoWorkerExecutor,
                               @Qualifier("demoPipelineExecutor") ExecutorService demoPipelineExecutor,
                               @Qualifier("demoHeartbeatExecutor")
                               ScheduledExecutorService demoHeartbeatExecutor) {
        this.store = store;
        this.properties = properties;
        this.reviewTasks = reviewTasks;
        this.dispatcher = demoWorkerExecutor;
        this.pipeline = demoPipelineExecutor;
        this.heartbeats = demoHeartbeatExecutor;
    }

    @Scheduled(fixedDelayString = "${codereviewx.demo.poll-interval-ms:500}")
    public void poll() {
        if (!properties.isEnabled()) return;
        for (int i = 0; i < properties.getGlobalConcurrency(); i++) {
            store.claimNext(workerId, LocalDateTime.now().plus(properties.getLeaseDuration()),
                    properties.getGlobalConcurrency()).ifPresent(
                            job -> dispatcher.submit(() -> execute(job)));
        }
    }

    void execute(DemoStore.Job job) {
        if (job.attempts() > 1) {
            store.resetForRetry(job);
        }
        store.markRunning(job);
        long heartbeatPeriodMs = Math.max(
                1_000, properties.getLeaseDuration().toMillis() / 3);
        ScheduledFuture<?> heartbeat = heartbeats.scheduleAtFixedRate(
                () -> store.heartbeat(job.id(), workerId,
                        LocalDateTime.now().plus(properties.getLeaseDuration())),
                heartbeatPeriodMs, heartbeatPeriodMs, TimeUnit.MILLISECONDS);
        Future<ReviewTaskResponse> review = pipeline.submit(
                () -> reviewTasks.executeExistingGithubTask(job.taskId(), job.reviewRunId()));
        boolean cancellationRequested = false;
        try {
            ReviewTaskResponse response = review.get(
                    properties.getExecutionDeadline().toMillis(), TimeUnit.MILLISECONDS);
            if (response.getStatus() == null || !"SUCCESS".equals(response.getStatus().name())) {
                store.markFailed(job, "REVIEW_FAILED", "Review pipeline did not complete successfully.");
            } else {
                store.markSucceeded(job);
            }
        } catch (TimeoutException ex) {
            review.cancel(true);
            cancellationRequested = true;
            store.markFailed(job, "DEMO_DEADLINE_EXCEEDED",
                    "Live model deadline exceeded "
                            + properties.getExecutionDeadline().toSeconds() + " seconds.");
        } catch (ExecutionException ex) {
            markExecutionFailure(job, ex.getCause());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            review.cancel(true);
            cancellationRequested = true;
            store.markFailed(job, "DEMO_EXECUTION_INTERRUPTED",
                    "Live execution was interrupted.");
        } catch (Exception ex) {
            markExecutionFailure(job, ex);
        } finally {
            heartbeat.cancel(false);
            if (!cancellationRequested && !review.isDone()) review.cancel(true);
        }
    }

    private void markExecutionFailure(DemoStore.Job job, Throwable error) {
        String code = error instanceof DemoApiException demo
                ? demo.getCode() : "DEMO_EXECUTION_FAILED";
        String message = error == null || error.getMessage() == null
                ? "Live execution failed." : error.getMessage();
        store.markFailed(job, code, message);
    }
}
