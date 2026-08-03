package com.codereviewx.backend.review.github;

import org.springframework.stereotype.Component;

/** Server-side fallback. It deliberately ignores repository input because the PAT is deployment-scoped. */
@Component
public class PatGithubCredentialProvider implements GithubCredentialProvider {
    private final GithubProperties properties;

    public PatGithubCredentialProvider(GithubProperties properties) {
        this.properties = properties;
    }

    @Override
    public GithubCredential resolveForRepository(String owner, String repo) {
        if (!properties.hasToken()) {
            throw new GithubAccessException("GITHUB_AUTH_REQUIRED", "GitHub access is required for this repository.");
        }
        return new GithubCredential(properties.getToken(), GithubCredential.Mode.PERSONAL_PAT, null);
    }
}
