package com.codereviewx.backend.rag.service;

import com.codereviewx.backend.review.github.GithubPrMetadata;

public interface RagIndexService {
    RagIndexResolution ensureIndexed(GithubPrMetadata metadata);

    RagIndexJob getJob(long jobId);
}
