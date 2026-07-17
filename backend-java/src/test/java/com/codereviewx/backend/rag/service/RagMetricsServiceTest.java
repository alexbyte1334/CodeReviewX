package com.codereviewx.backend.rag.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RagMetricsServiceTest {
    @Test void exposesExactBoundedRagMeters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RagMetricsService metrics = new RagMetricsService(registry);
        metrics.recordIndexedChunks(7);
        metrics.recordIndexJob();
        metrics.recordEmbeddingRequest();
        metrics.recordRerankRequest();
        metrics.recordRetrieval(true);
        metrics.recordContextChars(321);
        metrics.indexDuration().record(Duration.ofMillis(10));
        metrics.retrievalDuration().record(Duration.ofMillis(5));

        assertThat(registry.counter("rag_chunks_total").count()).isEqualTo(7);
        assertThat(registry.counter("rag_index_jobs_total").count()).isEqualTo(1);
        assertThat(registry.counter("rag_embedding_requests_total").count()).isEqualTo(1);
        assertThat(registry.counter("rag_rerank_requests_total").count()).isEqualTo(1);
        assertThat(registry.counter("rag_retrieval_degraded_total").count()).isEqualTo(1);
        assertThat(registry.get("rag_context_chars").gauge().value()).isEqualTo(321);
        assertThat(registry.timer("rag_index_duration_seconds").count()).isEqualTo(1);
        assertThat(registry.timer("rag_retrieval_duration_seconds").count()).isEqualTo(1);
        assertThat(registry.getMeters()).allMatch(meter -> meter.getId().getTags().isEmpty());
    }
}
