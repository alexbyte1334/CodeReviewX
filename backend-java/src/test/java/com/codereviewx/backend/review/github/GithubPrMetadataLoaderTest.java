package com.codereviewx.backend.review.github;

import com.codereviewx.backend.review.ReviewErrorCodes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GithubPrMetadataLoaderTest {

    @Test
    void load_withoutToken_returnsAuthMissingWithoutCallingClient() {
        GithubProperties properties = new GithubProperties();
        RecordingClient client = new RecordingClient(new GithubPrMetadataHttpResponse(200, false, response()));
        GithubPrMetadataLoader loader = new GithubPrMetadataLoader(properties, client);

        GithubPrMetadataLoadResult result = loader.load("https://github.com/example/repo", 12);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ReviewErrorCodes.GITHUB_AUTH_MISSING);
        assertThat(result.getErrorMessage()).contains("GITHUB_TOKEN");
        assertThat(client.called).isFalse();
    }

    @Test
    void load_success_returnsBoundedMetadata() {
        GithubProperties properties = propertiesWithToken();
        RecordingClient client = new RecordingClient(new GithubPrMetadataHttpResponse(200, false, response()));
        GithubPrMetadataLoader loader = new GithubPrMetadataLoader(properties, client);

        GithubPrMetadataLoadResult result = loader.load("https://github.com/example/repo.git", 12);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMetadata().owner()).isEqualTo("example");
        assertThat(result.getMetadata().repo()).isEqualTo("repo");
        assertThat(result.getMetadata().title()).isEqualTo("Improve review flow");
        assertThat(result.getMetadata().authorLogin()).isEqualTo("octocat");
        assertThat(result.getMetadata().baseRef()).isEqualTo("main");
        assertThat(result.getMetadata().headRef()).isEqualTo("feature");
        assertThat(result.getMetadata().changedFiles()).isEqualTo(3);
        assertThat(client.capturedToken).isEqualTo("test-token");
    }

    @Test
    void load_sshStyleUrl_parsesOwnerAndRepo() {
        GithubProperties properties = propertiesWithToken();
        RecordingClient client = new RecordingClient(new GithubPrMetadataHttpResponse(200, false, response()));
        GithubPrMetadataLoader loader = new GithubPrMetadataLoader(properties, client);

        GithubPrMetadataLoadResult result = loader.load("git@github.com:example/repo.git", 12);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMetadata().owner()).isEqualTo("example");
        assertThat(result.getMetadata().repo()).isEqualTo("repo");
    }

    @Test
    void load_authStatus_mapsToAuthFailed() {
        GithubPrMetadataLoadResult result = loadWithStatus(401, false);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ReviewErrorCodes.GITHUB_AUTH_FAILED);
    }

    @Test
    void load_forbiddenRateLimited_mapsToRateLimited() {
        GithubPrMetadataLoadResult result = loadWithStatus(403, true);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ReviewErrorCodes.GITHUB_RATE_LIMITED);
    }

    @Test
    void load_notFound_mapsToPrNotFound() {
        GithubPrMetadataLoadResult result = loadWithStatus(404, false);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ReviewErrorCodes.GITHUB_PR_NOT_FOUND);
    }

    @Test
    void load_serverError_mapsToMetadataLoadFailed() {
        GithubPrMetadataLoadResult result = loadWithStatus(500, false);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ReviewErrorCodes.GITHUB_METADATA_LOAD_FAILED);
    }

    @Test
    void load_invalidRepoUrl_mapsToMetadataLoadFailed() {
        GithubProperties properties = propertiesWithToken();
        RecordingClient client = new RecordingClient(new GithubPrMetadataHttpResponse(200, false, response()));
        GithubPrMetadataLoader loader = new GithubPrMetadataLoader(properties, client);

        GithubPrMetadataLoadResult result = loader.load("https://example.com/example/repo", 12);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ReviewErrorCodes.GITHUB_METADATA_LOAD_FAILED);
        assertThat(client.called).isFalse();
    }

    private static GithubPrMetadataLoadResult loadWithStatus(int status, boolean rateLimited) {
        GithubProperties properties = propertiesWithToken();
        GithubPrMetadataLoader loader = new GithubPrMetadataLoader(
                properties,
                new RecordingClient(new GithubPrMetadataHttpResponse(status, rateLimited, null))
        );
        return loader.load("https://github.com/example/repo", 12);
    }

    private static GithubProperties propertiesWithToken() {
        GithubProperties properties = new GithubProperties();
        properties.setToken("test-token");
        return properties;
    }

    private static GithubPullRequestResponse response() {
        GithubPullRequestResponse response = new GithubPullRequestResponse();
        response.setTitle("Improve review flow");
        response.setState("open");
        response.setCreatedAt("2026-06-26T01:00:00Z");
        response.setUpdatedAt("2026-06-26T02:00:00Z");
        response.setChangedFiles(3);
        response.setAdditions(120);
        response.setDeletions(40);

        GithubPullRequestResponse.User user = new GithubPullRequestResponse.User();
        user.setLogin("octocat");
        response.setUser(user);

        GithubPullRequestResponse.Ref base = new GithubPullRequestResponse.Ref();
        base.setRef("main");
        base.setSha("base-sha");
        response.setBase(base);

        GithubPullRequestResponse.Ref head = new GithubPullRequestResponse.Ref();
        head.setRef("feature");
        head.setSha("head-sha");
        response.setHead(head);
        return response;
    }

    private static class RecordingClient implements GithubPrMetadataHttpClient {
        private final GithubPrMetadataHttpResponse response;
        private boolean called;
        private String capturedToken;

        private RecordingClient(GithubPrMetadataHttpResponse response) {
            this.response = response;
        }

        @Override
        public GithubPrMetadataHttpResponse fetchPullRequest(String apiBaseUrl,
                                                             GithubRepositoryRef repository,
                                                             int prNumber,
                                                             String token,
                                                             int timeoutSeconds) {
            called = true;
            capturedToken = token;
            return response;
        }
    }
}
