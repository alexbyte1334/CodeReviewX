package com.codereviewx.backend.review.github;

public class GithubRepositoryParseException extends RuntimeException {

    public GithubRepositoryParseException(String message) {
        super(message);
    }

    public GithubRepositoryParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
