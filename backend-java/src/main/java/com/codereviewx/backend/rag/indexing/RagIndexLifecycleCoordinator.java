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
    private final Map<LeaseKey, Thread> cancellationRequested = new ConcurrentHashMap<>();

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
        LeaseKey key = new LeaseKey(jobId, attempt);
        Thread current = Thread.currentThread();
        if (cancellationRequested.containsKey(key)) {
            releaseWhenTerminated(key, current);
            return;
        }
        active.remove(key, current);
    }

    void stopAccepting() {
        accepting.set(false);
    }

    void requestCancellation() {
        active.forEach((lease, thread) -> {
            cancellationRequested.putIfAbsent(lease, thread);
            thread.interrupt();
        });
    }

    boolean isCancellationRequested(long jobId, int attempt) {
        return cancellationRequested.containsKey(new LeaseKey(jobId, attempt));
    }

    void releaseTerminated() {
        cancellationRequested.forEach((lease, thread) -> {
            if (!thread.isAlive()) {
                releaseIfTracked(lease, thread);
            }
        });
    }

    int activeCount() {
        return active.size();
    }

    private void releaseWhenTerminated(LeaseKey lease, Thread thread) {
        Thread watcher = new Thread(() -> {
            try {
                thread.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            releaseIfTracked(lease, thread);
        }, "rag-index-lease-release");
        watcher.setDaemon(true);
        watcher.start();
    }

    private void releaseIfTracked(LeaseKey lease, Thread thread) {
        if (cancellationRequested.remove(lease, thread)) {
            active.remove(lease, thread);
            leaseReleaser.release(lease.jobId(), lease.attempt());
        }
    }

    @FunctionalInterface
    interface LeaseReleaser {
        boolean release(long jobId, int attempt);
    }

    private record LeaseKey(long jobId, int attempt) {
    }
}
