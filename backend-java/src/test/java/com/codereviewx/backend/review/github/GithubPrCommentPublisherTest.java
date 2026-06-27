package com.codereviewx.backend.review.github;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GithubPrCommentPublisherTest {

    @Test
    void publish_withoutTokenFailsWithoutCallingClient() {
        GithubProperties properties = new GithubProperties();
        RecordingClient client = new RecordingClient(new GithubPrCommentHttpResponse(201, false, 123L));
        GithubPrCommentPublisher publisher = new GithubPrCommentPublisher(properties, client);

        GithubPrCommentPublishResult result = publisher.publish(request());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("GITHUB_TOKEN");
        assertThat(client.called).isFalse();
    }

    @Test
    void publish_createdWithCommentIdSucceeds() {
        GithubPrCommentPublisher publisher = new GithubPrCommentPublisher(
                propertiesWithToken(),
                new RecordingClient(new GithubPrCommentHttpResponse(201, false, 123L))
        );

        GithubPrCommentPublishResult result = publisher.publish(request());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getGithubCommentId()).isEqualTo(123L);
    }

    @Test
    void publish_authFailureReturnsSanitizedMessage() {
        GithubPrCommentPublishResult result = publishWithStatus(401, false);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("authentication failed");
    }

    @Test
    void publish_rateLimitReturnsRateLimitMessage() {
        GithubPrCommentPublishResult result = publishWithStatus(403, true);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("rate limit");
    }

    @Test
    void publish_unprocessableTargetReturnsActionableMessage() {
        GithubPrCommentPublishResult result = publishWithStatus(422, false);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("comment target");
    }

    private static GithubPrCommentPublishResult publishWithStatus(int status, boolean rateLimited) {
        GithubPrCommentPublisher publisher = new GithubPrCommentPublisher(
                propertiesWithToken(),
                new RecordingClient(new GithubPrCommentHttpResponse(status, rateLimited, null))
        );
        return publisher.publish(request());
    }

    private static GithubProperties propertiesWithToken() {
        GithubProperties properties = new GithubProperties();
        properties.setToken("test-token");
        return properties;
    }

    private static GithubPrCommentPublishRequest request() {
        return new GithubPrCommentPublishRequest(
                "example",
                "repo",
                12,
                "head-sha",
                "src/App.java",
                42,
                "RIGHT",
                "Draft comment"
        );
    }

    private static class RecordingClient implements GithubPrCommentHttpClient {
        private final GithubPrCommentHttpResponse response;
        private boolean called;

        private RecordingClient(GithubPrCommentHttpResponse response) {
            this.response = response;
        }

        @Override
        public GithubPrCommentHttpResponse publishPullRequestComment(String apiBaseUrl,
                                                                     GithubPrCommentPublishRequest publishRequest,
                                                                     String token,
                                                                     int timeoutSeconds) {
            called = true;
            return response;
        }
    }
}
