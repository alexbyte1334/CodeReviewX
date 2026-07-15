package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.rag.persistence.RagIndexJobStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "codereviewx.rag", name = "enabled", havingValue = "true")
public final class RagIndexLifecycleCoordinator {

    private final LeaseReleaser leaseReleaser;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final Map<LeaseKey, Thread> active = new ConcurrentHashMap<>();

    @Autowired
    public RagIndexLifecycleCoordinator(RagIndexJobStore jobs) {
        this(jobs::releaseForShutdown);
    }

    RagIndexLifecycleCoordinator(LeaseReleaser leaseReleaser) {
        this.leaseReleaser = leaseReleaser;
    }

    boolean isAccepting() {
        return accepting.get();
    }

    boolean register(long jobId, int attempt) {
        if (!accepting.get()) {
            return false;
        }
        LeaseKey key = new LeaseKey(jobId, attempt);
        active.put(key, Thread.currentThread());
        if (!accepting.get()) {
            active.remove(key, Thread.currentThread());
            return false;
        }
        return true;
    }

    void unregister(long jobId, int attempt) {
        active.remove(new LeaseKey(jobId, attempt), Thread.currentThread());
    }

    void stopAccepting() {
        accepting.set(false);
    }

    void cancelAndReleaseActive() {
        active.forEach((lease, thread) -> {
            leaseReleaser.release(lease.jobId(), lease.attempt());
            thread.interrupt();
        });
    }

    int activeCount() {
        return active.size();
    }

    @FunctionalInterface
    interface LeaseReleaser {
        boolean release(long jobId, int attempt);
    }

    private record LeaseKey(long jobId, int attempt) {
    }
}
