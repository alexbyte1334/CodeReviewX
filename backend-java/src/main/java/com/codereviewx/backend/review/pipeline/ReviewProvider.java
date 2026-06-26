package com.codereviewx.backend.review.pipeline;

/**
 * A provider that analyzes a ReviewContext and returns normalized findings.
 */
public interface ReviewProvider {

    ReviewProviderResult review(ReviewContext context);
}
