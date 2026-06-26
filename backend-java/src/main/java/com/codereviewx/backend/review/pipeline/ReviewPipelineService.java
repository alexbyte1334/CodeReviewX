package com.codereviewx.backend.review.pipeline;

import org.springframework.stereotype.Service;

/**
 * Orchestrates review execution by invoking the configured provider.
 * Round 09: provider selection via {@link com.codereviewx.backend.review.pipeline.provider.ConfigurableReviewProvider}.
 */
@Service
public class ReviewPipelineService {

    private final ReviewProvider reviewProvider;

    public ReviewPipelineService(ReviewProvider reviewProvider) {
        this.reviewProvider = reviewProvider;
    }

    public ReviewProviderResult run(ReviewContext context) {
        ReviewProviderResult result = reviewProvider.review(context);
        if (result == null || result.getFindings() == null) {
            throw new IllegalStateException("Review provider returned an invalid result");
        }
        return result;
    }
}
