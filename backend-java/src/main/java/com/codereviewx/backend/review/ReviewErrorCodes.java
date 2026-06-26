package com.codereviewx.backend.review;

public final class ReviewErrorCodes {

    public static final String GITHUB_RUNTIME_NOT_READY = "GITHUB_RUNTIME_NOT_READY";
    public static final String GITHUB_AUTH_MISSING = "GITHUB_AUTH_MISSING";
    public static final String GITHUB_AUTH_FAILED = "GITHUB_AUTH_FAILED";
    public static final String GITHUB_PR_NOT_FOUND = "GITHUB_PR_NOT_FOUND";
    public static final String GITHUB_RATE_LIMITED = "GITHUB_RATE_LIMITED";
    public static final String GITHUB_METADATA_LOAD_FAILED = "GITHUB_METADATA_LOAD_FAILED";
    public static final String GITHUB_DIFF_RUNTIME_NOT_READY = "GITHUB_DIFF_RUNTIME_NOT_READY";

    private ReviewErrorCodes() {
    }
}
