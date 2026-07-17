package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.review.github.GithubPrMetadata;

public interface RepositoryCheckoutService {
    default String resolveCommit(GithubPrMetadata metadata, String requestedRef) {
        return requestedRef;
    }

    CheckedOutRepository checkout(GithubPrMetadata metadata);
}
