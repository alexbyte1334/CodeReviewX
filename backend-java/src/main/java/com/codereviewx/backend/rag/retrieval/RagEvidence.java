package com.codereviewx.backend.rag.retrieval;

import java.util.Objects;

public record RagEvidence(String label, String path, int startLine, int endLine, String commitSha,
                          String content, double score) {
    public RagEvidence {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(content, "content");
    }
}
