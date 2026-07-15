package com.codereviewx.backend.rag.model;

import java.util.Objects;

public record RepositoryFile(String path, Language language, String content, long byteSize, String contentHash) {

    public RepositoryFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(contentHash, "contentHash");
        if (path.isBlank() || path.startsWith("/") || path.contains("\\") || path.equals("..")
                || path.startsWith("../") || path.contains("/../") || byteSize < 0) {
            throw new IllegalArgumentException("Repository file metadata is invalid");
        }
    }
}
