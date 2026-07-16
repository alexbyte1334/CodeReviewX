package com.codereviewx.backend.rag.retrieval;

import java.util.Objects;

public record RagEvidence(String label, String path, int startLine, int endLine, String commitSha,
                          String content, double score, boolean truncated, boolean escaped,
                          RagEvidenceSourceIdentity sourceIdentity) {
    public RagEvidence(String label, String path, int startLine, int endLine, String commitSha,
                       String content, double score) {
        this(label, path, startLine, endLine, commitSha, content, score, false, false,
                RagEvidenceSourceIdentity.unknown());
    }

    public RagEvidence(String label, String path, int startLine, int endLine, String commitSha,
                       String content, double score, boolean truncated, boolean escaped) {
        this(label, path, startLine, endLine, commitSha, content, score, truncated, escaped,
                RagEvidenceSourceIdentity.unknown());
    }

    public RagEvidence {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(sourceIdentity, "sourceIdentity");
    }

    @Override
    public String toString() {
        return "RagEvidence[label=" + label + ", path=" + path + ", startLine=" + startLine
                + ", endLine=" + endLine + ", commitSha=" + commitSha + ", content=" + content
                + ", score=" + score + ", truncated=" + truncated + ", escaped=" + escaped + "]";
    }
}
