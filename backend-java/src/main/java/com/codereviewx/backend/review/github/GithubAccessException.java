package com.codereviewx.backend.review.github;

public class GithubAccessException extends RuntimeException {
    private final String errorCode;

    public GithubAccessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
