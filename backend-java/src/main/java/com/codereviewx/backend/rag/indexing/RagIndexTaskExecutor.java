package com.codereviewx.backend.rag.indexing;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RagIndexTaskExecutor extends ThreadPoolTaskExecutor {

    private final RagIndexLifecycleCoordinator coordinator;
    private final long graceMillis;
    private final AtomicBoolean shutdown = new AtomicBoolean();

    public RagIndexTaskExecutor(RagIndexLifecycleCoordinator coordinator, Duration grace) {
        if (grace == null || grace.isNegative() || grace.isZero()) {
            throw new IllegalArgumentException("RAG index shutdown grace must be positive");
        }
        this.coordinator = coordinator;
        this.graceMillis = grace.toMillis();
    }

    @Override
    public void initiateShutdown() {
        coordinator.stopAccepting();
        super.initiateShutdown();
    }

    @Override
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        coordinator.stopAccepting();
        ThreadPoolExecutor executor = getThreadPoolExecutor();
        executor.shutdown();
        if (awaitTermination(executor, graceMillis)) {
            return;
        }
        coordinator.requestCancellation();
        executor.shutdownNow();
        awaitTermination(executor, Math.min(graceMillis, 1_000L));
        coordinator.releaseTerminated();
    }

    private static boolean awaitTermination(ThreadPoolExecutor executor, long timeoutMillis) {
        try {
            return executor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
