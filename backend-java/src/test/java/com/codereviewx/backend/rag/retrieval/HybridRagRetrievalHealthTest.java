package com.codereviewx.backend.rag.retrieval;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.embedding.EmbeddingClient;
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
    @Test void reportsHealthyWhenBothRoutesSucceed() { assertHealth(vectorOk(), lexicalOk(), embeddings(), RagContextAssembler.RetrievalHealth.HEALTHY); }
    @Test void survivesVectorFailure() { assertHealth(vectorFailing(), lexicalOk(), embeddings(), RagContextAssembler.RetrievalHealth.SINGLE_ROUTE_FAILED); }
    @Test void survivesLexicalFailure() { assertHealth(vectorOk(), lexicalFailing(), embeddings(), RagContextAssembler.RetrievalHealth.SINGLE_ROUTE_FAILED); }
    @Test void reportsBothRouteFailure() { assertHealth(vectorFailing(), lexicalFailing(), embeddings(), RagContextAssembler.RetrievalHealth.BOTH_ROUTES_FAILED); }
    @Test void reportsEmbeddingFailure() { assertHealth(vectorOk(), lexicalOk(), inputs -> { throw new IllegalStateException("down"); }, RagContextAssembler.RetrievalHealth.EMBEDDING_FAILED); }

    private void assertHealth(HybridRagRetrievalService.VectorRoute vector,
                              HybridRagRetrievalService.LexicalRoute lexical,
                              EmbeddingClient embeddings,
                              RagContextAssembler.RetrievalHealth expected) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(Long.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of(7L));
        RagProperties properties = new RagProperties();
        properties.setEmbeddingDimensions(1024);
        HybridRagRetrievalService service = new HybridRagRetrievalService(jdbc, embeddings, properties, vector, lexical);

        HybridRagRetrievalService.Result result = service.retrieve(new HybridRagRetrievalService.Request(1L,
                "a".repeat(40), new PrRetrievalQueryBuilder.PrQuery("query", List.of(), List.of(), List.of(), List.of())));

        assertThat(result.retrievalHealth()).isEqualTo(expected);
        assertThat(result.legacyFallbackRequired()).isEqualTo(expected == RagContextAssembler.RetrievalHealth.EMBEDDING_FAILED
                || expected == RagContextAssembler.RetrievalHealth.BOTH_ROUTES_FAILED);
    }

    private HybridRagRetrievalService.VectorRoute vectorOk() { return (snapshot, embedding, paths) -> List.of(candidate()); }
    private HybridRagRetrievalService.LexicalRoute lexicalOk() { return (snapshot, query, paths) -> List.of(candidate()); }
    private HybridRagRetrievalService.VectorRoute vectorFailing() { return (snapshot, embedding, paths) -> { throw new HybridRagRetrievalService.RouteUnavailableException("vector"); }; }
    private HybridRagRetrievalService.LexicalRoute lexicalFailing() { return (snapshot, query, paths) -> { throw new HybridRagRetrievalService.RouteUnavailableException("lexical"); }; }
    private EmbeddingClient embeddings() { return inputs -> List.of(new float[1024]); }
    private ReciprocalRankFusion.Candidate candidate() { return new ReciprocalRankFusion.Candidate(1, "src/A.java", "JAVA", "a", 1, 2, "hash", "content", 1.0); }
}
