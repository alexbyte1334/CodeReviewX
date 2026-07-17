package com.codereviewx.backend.rag.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class RagMetricsService {
    private final Counter chunks, degraded, embedding, rerank, jobs;
    private final Timer indexDuration, retrievalDuration;
    private final java.util.concurrent.atomic.AtomicInteger contextChars = new java.util.concurrent.atomic.AtomicInteger();
    public RagMetricsService(MeterRegistry registry) {
        chunks = registry.counter("rag_chunks_total");
        degraded = registry.counter("rag_retrieval_degraded_total");
        embedding = registry.counter("rag_embedding_requests_total");
        rerank = registry.counter("rag_rerank_requests_total");
        jobs = registry.counter("rag_index_jobs_total");
        indexDuration = registry.timer("rag_index_duration_seconds");
        retrievalDuration = registry.timer("rag_retrieval_duration_seconds");
        registry.gauge("rag_context_chars", contextChars);
    }
    public void recordIndexedChunks(int count) { chunks.increment(count); }
    public void recordRetrieval(boolean isDegraded) { if (isDegraded) degraded.increment(); }
    public void recordEmbeddingRequest() { embedding.increment(); }
    public void recordRerankRequest() { rerank.increment(); }
    public void recordIndexJob() { jobs.increment(); }
    public void recordContextChars(int count) { contextChars.set(Math.max(0, count)); }
    public Timer indexDuration() { return indexDuration; }
    public Timer retrievalDuration() { return retrievalDuration; }
}
