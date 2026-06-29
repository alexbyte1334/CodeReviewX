package com.codereviewx.backend.review.service;

import java.util.Collections;
import java.util.List;

public record RepositoryContextIndexResult(
        List<RepositoryContextFile> files,
        Integer fileCount,
        Integer contextBytes,
        Boolean truncated,
        String contextText
) {
    public static RepositoryContextIndexResult empty() {
        return new RepositoryContextIndexResult(Collections.emptyList(), 0, 0, false, "");
    }

    public boolean hasContext() {
        return contextText != null && !contextText.isBlank();
    }
}
