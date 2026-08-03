package com.codereviewx.backend.review.pipeline.provider;

/** Provider-neutral contract for the bounded planner/executor/gatekeeper calls. */
public interface ReviewModelProvider {
    String complete(String systemPrompt, String userPrompt);
}
