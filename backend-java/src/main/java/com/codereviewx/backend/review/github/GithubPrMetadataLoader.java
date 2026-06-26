package com.codereviewx.backend.review.github;

import com.codereviewx.backend.review.ReviewErrorCodes;
import org.springframework.stereotype.Component;

@Component
public class GithubPrMetadataLoader {

    public static final String TOOL_NAME = "github.pr.metadata.load";
    public static final String AUTH_MISSING_MESSAGE =
            "Local GitHub token is not configured. Set GITHUB_TOKEN to enable GitHub PR metadata ingestion.";
    public static final String AUTH_FAILED_MESSAGE =
            "GitHub authentication failed while loading PR metadata.";
    public static final String PR_NOT_FOUND_MESSAGE =
            "GitHub pull request was not found or is not accessible.";
    public static final String RATE_LIMITED_MESSAGE =
            "GitHub API rate limit was reached while loading PR metadata.";
    public static final String METADATA_FAILED_MESSAGE =
            "GitHub PR metadata could not be loaded.";

    private final GithubProperties properties;
    private final GithubPrMetadataHttpClient httpClient;

    public GithubPrMetadataLoader(GithubProperties properties, GithubPrMetadataHttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public GithubPrMetadataLoadResult load(String repoUrl, int prNumber) {
        GithubRepositoryRef repository;
        try {
            repository = GithubRepositoryParser.parse(repoUrl);
        } catch (GithubRepositoryParseException ex) {
            return GithubPrMetadataLoadResult.failure(
                    ReviewErrorCodes.GITHUB_METADATA_LOAD_FAILED,
                    METADATA_FAILED_MESSAGE
            );
        }

        if (!properties.hasToken()) {
            return GithubPrMetadataLoadResult.failure(
                    ReviewErrorCodes.GITHUB_AUTH_MISSING,
                    AUTH_MISSING_MESSAGE
            );
        }

        try {
            GithubPrMetadataHttpResponse response = httpClient.fetchPullRequest(
                    properties.getApiBaseUrl(),
                    repository,
                    prNumber,
                    properties.getToken(),
                    properties.getTimeoutSeconds()
            );
            return toResult(repository, prNumber, response);
        } catch (GithubPrMetadataClientException ex) {
            return GithubPrMetadataLoadResult.failure(
                    ReviewErrorCodes.GITHUB_METADATA_LOAD_FAILED,
                    METADATA_FAILED_MESSAGE
            );
        }
    }

    private GithubPrMetadataLoadResult toResult(GithubRepositoryRef repository,
                                                int prNumber,
                                                GithubPrMetadataHttpResponse response) {
        int status = response.statusCode();
        if (status == 401 || status == 403 && !response.rateLimited()) {
            return GithubPrMetadataLoadResult.failure(
                    ReviewErrorCodes.GITHUB_AUTH_FAILED,
                    AUTH_FAILED_MESSAGE
            );
        }
        if (status == 403 && response.rateLimited()) {
            return GithubPrMetadataLoadResult.failure(
                    ReviewErrorCodes.GITHUB_RATE_LIMITED,
                    RATE_LIMITED_MESSAGE
            );
        }
        if (status == 404) {
            return GithubPrMetadataLoadResult.failure(
                    ReviewErrorCodes.GITHUB_PR_NOT_FOUND,
                    PR_NOT_FOUND_MESSAGE
            );
        }
        if (status < 200 || status >= 300 || response.body() == null) {
            return GithubPrMetadataLoadResult.failure(
                    ReviewErrorCodes.GITHUB_METADATA_LOAD_FAILED,
                    METADATA_FAILED_MESSAGE
            );
        }

        GithubPullRequestResponse body = response.body();
        GithubPrMetadata metadata = new GithubPrMetadata(
                repository.owner(),
                repository.repo(),
                prNumber,
                body.getTitle(),
                body.getUser() == null ? null : body.getUser().getLogin(),
                body.getBase() == null ? null : body.getBase().getRef(),
                body.getHead() == null ? null : body.getHead().getRef(),
                body.getBase() == null ? null : body.getBase().getSha(),
                body.getHead() == null ? null : body.getHead().getSha(),
                body.getState(),
                body.getCreatedAt(),
                body.getUpdatedAt(),
                body.getChangedFiles(),
                body.getAdditions(),
                body.getDeletions()
        );
        return GithubPrMetadataLoadResult.success(metadata);
    }
}
