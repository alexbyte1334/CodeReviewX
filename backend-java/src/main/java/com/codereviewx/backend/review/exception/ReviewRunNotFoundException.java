package com.codereviewx.backend.review.exception;

public class ReviewRunNotFoundException extends RuntimeException {

    private final Long runId;

    public ReviewRunNotFoundException(Long runId) {
        super("Review run not found: " + runId);
        this.runId = runId;
    }

    public Long getRunId() {
        return runId;
    }
}
