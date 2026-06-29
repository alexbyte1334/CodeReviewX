package com.codereviewx.backend.review.github;

public interface GithubRepositoryContentHttpClient {

    GithubRepositoryFileContentHttpResponse fetchFileContent(
            String apiBaseUrl,
            GithubRepositoryRef repository,
            String path,
            String ref,
            String token,
            int timeoutSeconds,
            int maxBytes
    );
}
