package com.codereviewx.backend.rag.indexing;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class RagIndexShutdownLifecycleTest {

    @Test
    void closeKeepsHeartbeatAliveUntilActiveIndexTaskCompletesWithinGrace() throws Exception {
        CountDownLatch releaseTask = new CountDownLatch(1);
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch heartbeatDuringClose = new CountDownLatch(1);
        AtomicBoolean closing = new AtomicBoolean();
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
        RagIndexLifecycleCoordinator coordinator = new RagIndexLifecycleCoordinator((jobId, attempt) -> true);
        AnnotationConfigApplicationContext context = context(Duration.ofSeconds(2), coordinator, heartbeat);
        RagIndexTaskExecutor executor = context.getBean(RagIndexTaskExecutor.class);
        executor.execute(() -> {
            heartbeat.scheduleAtFixedRate(() -> {
                if (closing.get()) {
                    heartbeatDuringClose.countDown();
                }
            }, 0, 10, TimeUnit.MILLISECONDS);
            taskStarted.countDown();
            await(releaseTask);
        });
        assertThat(taskStarted.await(1, TimeUnit.SECONDS)).isTrue();

        closing.set(true);
        Thread closer = new Thread(context::close);
        closer.start();

        assertThat(heartbeatDuringClose.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(heartbeat.isShutdown()).isFalse();
        releaseTask.countDown();
        closer.join(2_000);
        assertThat(closer.isAlive()).isFalse();
        assertThat(heartbeat.isShutdown()).isTrue();
    }

    @Test
    void closeRequeuesAndInterruptsTaskThatExceedsGraceBeforeHeartbeatStops() throws Exception {
        CountDownLatch releasedLease = new CountDownLatch(1);
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch blocker = new CountDownLatch(1);
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
        RagIndexLifecycleCoordinator coordinator = new RagIndexLifecycleCoordinator((jobId, attempt) -> {
            releasedLease.countDown();
            return true;
        });
        AnnotationConfigApplicationContext context = context(Duration.ofMillis(100), coordinator, heartbeat);
        RagIndexTaskExecutor executor = context.getBean(RagIndexTaskExecutor.class);
        executor.execute(() -> {
            coordinator.register(99L, 2);
            taskStarted.countDown();
            try {
                await(blocker);
            } finally {
                coordinator.unregister(99L, 2);
            }
        });
        assertThat(taskStarted.await(1, TimeUnit.SECONDS)).isTrue();

        context.close();

        assertThat(releasedLease.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(coordinator.activeCount()).isZero();
        assertThat(heartbeat.isShutdown()).isTrue();
    }

    private static AnnotationConfigApplicationContext context(
            Duration grace,
            RagIndexLifecycleCoordinator coordinator,
            ScheduledExecutorService heartbeat
    ) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean("ragHeartbeatExecutor", ScheduledExecutorService.class, () -> heartbeat,
                definition -> definition.setDestroyMethodName("shutdown"));
        context.registerBean(RagIndexLifecycleCoordinator.class, () -> coordinator);
        context.registerBean(RagIndexTaskExecutor.class, () -> {
            RagIndexTaskExecutor executor = new RagIndexTaskExecutor(coordinator, grace);
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(20);
            executor.setWaitForTasksToCompleteOnShutdown(true);
            return executor;
        }, definition -> definition.setDependsOn("ragHeartbeatExecutor"));
        context.refresh();
        return context;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
