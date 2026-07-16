package com.codereviewx.backend.rag.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class RagMetricsService {
    private final Counter indexedChunks;
    private final Counter retrievals;
    private final Counter degradedRetrievals;
    public RagMetricsService(MeterRegistry registry) {
        indexedChunks = registry.counter("codereviewx.rag.indexed_chunks_total");
        retrievals = registry.counter("codereviewx.rag.retrievals_total");
        degradedRetrievals = registry.counter("codereviewx.rag.degraded_retrievals_total");
    }
    public void recordIndexedChunks(int count) { indexedChunks.increment(count); }
    public void recordRetrieval(boolean degraded) { retrievals.increment(); if (degraded) degradedRetrievals.increment(); }
}
