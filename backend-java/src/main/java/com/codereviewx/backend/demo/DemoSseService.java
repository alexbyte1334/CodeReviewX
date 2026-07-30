package com.codereviewx.backend.demo;

import com.codereviewx.backend.demo.DemoDtos.Event;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;

@Service
public class DemoSseService {
    private static final Set<String> TERMINAL = Set.of("READY", "FAILED");
    private final DemoStore store;
    private final DemoRunService runs;
    private final ExecutorService clients = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "demo-sse-client");
        thread.setDaemon(true);
        return thread;
    });

    public DemoSseService(DemoStore store, DemoRunService runs) {
        this.store = store;
        this.runs = runs;
    }

    public SseEmitter stream(String publicId, long afterSequence) {
        DemoStore.DemoRow demo = runs.requireRun(publicId);
        SseEmitter emitter = new SseEmitter(120_000L);
        clients.submit(() -> pump(emitter, demo.id(), publicId, afterSequence));
        return emitter;
    }

    private void pump(SseEmitter emitter, long demoId, String publicId, long afterSequence) {
        long cursor = afterSequence;
        try {
            while (true) {
                for (Event event : store.events(demoId, cursor)) {
                    emitter.send(SseEmitter.event()
                            .id(Long.toString(event.sequence()))
                            .data(event));
                    cursor = event.sequence();
                }
                DemoStore.DemoRow current = runs.requireRun(publicId);
                if (TERMINAL.contains(current.status())) {
                    emitter.send(SseEmitter.event().name("stream-complete").data(current.status()));
                    emitter.complete();
                    return;
                }
                Thread.sleep(750);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (IOException | IllegalStateException ex) {
            emitter.completeWithError(ex);
        }
    }

    @PreDestroy
    void shutdown() {
        clients.shutdownNow();
    }
}
