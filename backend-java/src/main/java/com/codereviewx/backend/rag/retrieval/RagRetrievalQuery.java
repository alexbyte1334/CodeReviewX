package com.codereviewx.backend.rag.retrieval;

import java.util.List;
import java.util.Objects;

public record RagRetrievalQuery(String title, List<String> changedPaths, List<String> diffHunkHeaders,
                                List<String> changedSymbols, List<String> changedLines) {

    public RagRetrievalQuery {
        Objects.requireNonNull(title, "title");
        changedPaths = List.copyOf(Objects.requireNonNull(changedPaths, "changedPaths"));
        diffHunkHeaders = List.copyOf(Objects.requireNonNull(diffHunkHeaders, "diffHunkHeaders"));
        changedSymbols = List.copyOf(Objects.requireNonNull(changedSymbols, "changedSymbols"));
        changedLines = List.copyOf(Objects.requireNonNull(changedLines, "changedLines"));
    }
}
