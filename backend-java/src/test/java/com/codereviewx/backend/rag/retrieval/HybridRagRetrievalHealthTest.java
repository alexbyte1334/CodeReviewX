package com.codereviewx.backend.rag.retrieval;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.embedding.EmbeddingClient;
import com.codereviewx.backend.rag.service.RagMetricsService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridRagRetrievalHealthTest {
    @Test void reportsHealthyWhenBothRoutesSucceed() { assertHealth(vectorOk(), lexicalOk(), embeddings(), RagRetrievalHealth.HEALTHY); }
    @Test void survivesVectorFailure() { assertHealth(vectorFailing(), lexicalOk(), embeddings(), RagRetrievalHealth.SINGLE_ROUTE_FAILED); }
    @Test void survivesLexicalFailure() { assertHealth(vectorOk(), lexicalFailing(), embeddings(), RagRetrievalHealth.SINGLE_ROUTE_FAILED); }
    @Test void reportsBothRouteFailure() { assertHealth(vectorFailing(), lexicalFailing(), embeddings(), RagRetrievalHealth.BOTH_ROUTES_FAILED); }
    @Test void reportsEmbeddingFailure() { assertHealth(vectorOk(), lexicalOk(), inputs -> { throw new IllegalStateException("down"); }, RagRetrievalHealth.EMBEDDING_FAILED); }

    @Test void embeddingFailureIncrementsDegradedMetricExactlyOnce() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(Long.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of(7L));
        RagProperties properties = new RagProperties();
        properties.setEmbeddingDimensions(1024);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HybridRagRetrievalService service = new HybridRagRetrievalService(jdbc,
                inputs -> { throw new IllegalStateException("provider secret"); }, properties,
                new RagMetricsService(registry));

        RagRetrievalResult result = service.retrieve(new RagRetrievalRequest(1L,
                "a".repeat(40), new RagRetrievalQuery("query", List.of(), List.of(), List.of(), List.of())));

        assertThat(result.retrievalHealth()).isEqualTo(RagRetrievalHealth.EMBEDDING_FAILED);
        assertThat(registry.counter("rag_retrieval_degraded_total").count()).isEqualTo(1);
    }

    private void assertHealth(HybridRagRetrievalService.VectorRoute vector,
                              HybridRagRetrievalService.LexicalRoute lexical,
                              EmbeddingClient embeddings,
                              RagRetrievalHealth expected) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(Long.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of(7L));
        RagProperties properties = new RagProperties();
        properties.setEmbeddingDimensions(1024);
        HybridRagRetrievalService service = new HybridRagRetrievalService(jdbc, embeddings, properties, vector, lexical);

        RagRetrievalResult result = service.retrieve(new RagRetrievalRequest(1L,
                "a".repeat(40), new RagRetrievalQuery("query", List.of(), List.of(), List.of(), List.of())));

        assertThat(result.retrievalHealth()).isEqualTo(expected);
        assertThat(result.legacyFallbackRequired()).isEqualTo(expected == RagRetrievalHealth.EMBEDDING_FAILED
                || expected == RagRetrievalHealth.BOTH_ROUTES_FAILED);
    }

    private HybridRagRetrievalService.VectorRoute vectorOk() { return (snapshot, embedding, paths) -> List.of(candidate()); }
    private HybridRagRetrievalService.LexicalRoute lexicalOk() { return (snapshot, query, paths) -> List.of(candidate()); }
    private HybridRagRetrievalService.VectorRoute vectorFailing() { return (snapshot, embedding, paths) -> { throw new HybridRagRetrievalService.RouteUnavailableException("vector"); }; }
    private HybridRagRetrievalService.LexicalRoute lexicalFailing() { return (snapshot, query, paths) -> { throw new HybridRagRetrievalService.RouteUnavailableException("lexical"); }; }
    private EmbeddingClient embeddings() { return inputs -> List.of(new float[1024]); }
    private ReciprocalRankFusion.Candidate candidate() { return new ReciprocalRankFusion.Candidate(1, "src/A.java", "JAVA", "a", 1, 2, "hash", "content", 1.0); }
}
