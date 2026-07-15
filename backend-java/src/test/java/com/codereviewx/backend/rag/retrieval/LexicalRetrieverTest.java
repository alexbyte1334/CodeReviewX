package com.codereviewx.backend.rag.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class LexicalRetrieverTest {

    @Test
    void convertsSignalLinesToBoundedCompleteWebsearchAlternatives() {
        PrRetrievalQueryBuilder.PrQuery input = new PrRetrievalQueryBuilder.PrQuery(
                "Review authorization behavior", List.of(), List.of(), List.of(),
                IntStream.range(0, 400)
                        .mapToObj(index -> "unique changed signal " + index + " " + "x".repeat(40))
                        .toList());
        String builtQuery = new PrRetrievalQueryBuilder().build(input);

        String lexicalQuery = LexicalRetriever.toWebsearchQuery(builtQuery);

        assertThat(builtQuery.length()).isLessThanOrEqualTo(PrRetrievalQueryBuilder.MAX_QUERY_CHARS);
        assertThat(lexicalQuery.length()).isLessThanOrEqualTo(PrRetrievalQueryBuilder.MAX_QUERY_CHARS);
        assertThat(lexicalQuery).startsWith("Review authorization behavior OR unique changed signal 0")
                .doesNotEndWith(" OR ");
        assertThat(lexicalQuery.split(" OR ")).allMatch(signal -> !signal.isBlank());
    }
}
