package com.codereviewx.backend.rag.model;

import java.util.Objects;

public record CodeChunk(
        String chunkKey,
        String path,
        Language language,
        String symbolName,
        int startLine,
        int endLine,
        String content,
        String contentHash,
        int tokenCount
) {
    public CodeChunk {
        Objects.requireNonNull(chunkKey, "chunkKey");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(contentHash, "contentHash");
        if (startLine < 1 || endLine < startLine || tokenCount < 0) {
            throw new IllegalArgumentException("Code chunk metadata is invalid");
        }
    }
}
