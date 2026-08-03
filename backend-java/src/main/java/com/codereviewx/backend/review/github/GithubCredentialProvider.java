package com.codereviewx.backend.review.github;

public interface GithubCredentialProvider {
    GithubCredential resolveForRepository(String owner, String repo);
}
