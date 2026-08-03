package com.codereviewx.backend.review.github;

/** Short-lived credential metadata. The secret is never serialized or logged. */
public record GithubCredential(String accessToken, Mode mode, Long installationId) {
    public enum Mode { PERSONAL_PAT }

    public GithubCredential {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("GitHub access credential is required");
        }
    }
}
