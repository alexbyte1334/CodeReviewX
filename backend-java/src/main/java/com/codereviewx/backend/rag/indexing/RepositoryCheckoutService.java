package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.rag.model.CheckedOutRepository;
import com.codereviewx.backend.review.github.GithubPrMetadata;

public interface RepositoryCheckoutService {
    CheckedOutRepository checkout(GithubPrMetadata metadata);
}
