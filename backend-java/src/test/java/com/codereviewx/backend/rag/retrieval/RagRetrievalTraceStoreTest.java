package com.codereviewx.backend.rag.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagRetrievalTraceStoreTest {
    @Test void persistsSafeSummaryAndHashedQueryWithoutSourceOrToken() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RagRetrievalTraceStore store = new RagRetrievalTraceStore(jdbc, new ObjectMapper());
        var match = new RagRetrievedChunk(7L, "src/A.java", "java", "A", 2, 4,
                "hash", "SECRET_SOURCE_TOKEN", 1.0, .8);
        var result = new RagRetrievalResult(RagRetrievalResult.Status.READY, 3L, 2, 1,
                List.of(match), RagRetrievalHealth.HEALTHY);
        store.save(9L, 2L, "commit", "private query token", result, 1, 42, 12L);
        var captor = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), captor.capture());
        Object[] args = captor.getValue();
        String queryHash = String.valueOf(args[3]);
        String summary = String.valueOf(args[11]);
        assertThat(queryHash).hasSize(64).doesNotContain("private", "token");
        assertThat(summary).contains("src/A.java", "chunkId").doesNotContain("SECRET_SOURCE_TOKEN", "private query");
        assertThat(summary).doesNotContain("content");
    }

    @Test void propagatesTraceInsertFailure() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("db down"));
        RagRetrievalTraceStore store = new RagRetrievalTraceStore(jdbc, new ObjectMapper());
        var result = new RagRetrievalResult(RagRetrievalResult.Status.READY, 3L, 0, 0,
                List.of(), RagRetrievalHealth.HEALTHY);
        assertThatThrownBy(() -> store.save(1L, 2L, "sha", "query", result, 0, 0, 1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("persist");
    }
}
