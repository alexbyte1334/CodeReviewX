package com.codereviewx.backend.rag.retrieval;

import java.util.Objects;

public record RagEvidence(String label, String path, int startLine, int endLine, String commitSha,
                          String content, double score, boolean truncated, boolean escaped) {
    public RagEvidence(String label, String path, int startLine, int endLine, String commitSha,
                       String content, double score) {
        this(label, path, startLine, endLine, commitSha, content, score, false, false);
    }

    public RagEvidence {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(content, "content");
    }
}
