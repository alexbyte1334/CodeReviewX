package com.codereviewx.backend.review.github;

import com.codereviewx.backend.review.ReviewErrorCodes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GithubPrDiffLoaderTest {

    @Test
    void load_withoutToken_returnsAuthMissingWithoutCallingClient() {
        GithubProperties properties = new GithubProperties();
        RecordingClient client = new RecordingClient(new GithubPrDiffHttpResponse(200, false, List.of(file("src/App.ts"))));
        GithubPrDiffLoader loader = new GithubPrDiffLoader(properties, client);

        GithubPrDiffLoadResult result = loader.load(metadata(1));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ReviewErrorCodes.GITHUB_AUTH_MISSING);
        assertThat(result.getErrorMessage()).contains("GITHUB_TOKEN");
        assertThat(client.called).isFalse();
    }

    @Test
    void load_success_returnsBoundedDiffTextAndFileSummaries() {
        RecordingClient client = new RecordingClient(new GithubPrDiffHttpResponse(200, false, List.of(file("src/App.ts"))));
        GithubPrDiffLoader loader = new GithubPrDiffLoader(propertiesWithToken(), client);

        GithubPrDiffLoadResult result = loader.load(metadata(1));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDiff().diffText()).contains("diff --git a/src/App.ts b/src/App.ts");
        assertThat(result.getDiff().diffText()).contains("+const password = request.query.password;");
        assertThat(result.getDiff().fileCount()).isEqualTo(1);
        assertThat(result.getDiff().diffTruncated()).isFalse();
        assertThat(result.getDiff().files()).singleElement().satisfies(file -> {
            assertThat(file.filename()).isEqualTo("src/App.ts");
            assertThat(file.patchTruncated()).isFalse();
        });
        assertThat(client.capturedToken).isEqualTo("test-token");
        assertThat(client.capturedMaxFilesToFetch).isEqualTo(51);
    }

    @Test
    void load_whenMetadataChangedFilesExceedsLimit_failsBeforeCallingClient() {
        GithubProperties properties = propertiesWithToken();
        properties.setMaxChangedFiles(2);
        RecordingClient client = new RecordingClient(new GithubPrDiffHttpResponse(200, false, List.of(file("src/App.ts"))));
        GithubPrDiffLoader loader = new GithubPrDiffLoader(properties, client);

        GithubPrDiffLoadResult result = loader.load(metadata(3));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ReviewErrorCodes.GITHUB_DIFF_TOO_LARGE);
        assertThat(client.called).isFalse();
    }

    @Test
    void load_whenFetchedFilesExceedLimit_failsAsTooLarge() {
        GithubProperties properties = propertiesWithToken();
        properties.setMaxChangedFiles(1);
        GithubPrDiffLoader loader = new GithubPrDiffLoader(
                properties,
                new RecordingClient(new GithubPrDiffHttpResponse(200, false, List.of(file("a.txt"), file("b.txt"))))
        );

        GithubPrDiffLoadResult result = loader.load(metadata(1));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ReviewErrorCodes.GITHUB_DIFF_TOO_LARGE);
    }

    @Test
    void load_truncatesLargeFilePatchWithoutFailing() {
        GithubProperties properties = propertiesWithToken();
        properties.setPerFilePatchMaxBytes(32);
        GithubPrDiffFileResponse file = file("src/Large.ts");
        file.setPatch("@@ -1 +1 @@\n+" + "x".repeat(200));
        GithubPrDiffLoader loader = new GithubPrDiffLoader(
                properties,
                new RecordingClient(new GithubPrDiffHttpResponse(200, false, List.of(file)))
        );

        GithubPrDiffLoadResult result = loader.load(metadata(1));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDiff().diffTruncated()).isTrue();
        assertThat(result.getDiff().diffText()).contains("[CodeReviewX: file patch truncated]");
        assertThat(result.getDiff().files()).singleElement()
                .satisfies(summary -> assertThat(summary.patchTruncated()).isTrue());
    }

    @Test
    void load_truncatesTotalDiffWithinConfiguredByteLimit() {
        GithubProperties properties = propertiesWithToken();
        properties.setMaxDiffBytes(80);
        GithubPrDiffFileResponse file = file("src/Large.ts");
        file.setPatch("@@ -1 +1 @@\n+" + "x".repeat(200));
        GithubPrDiffLoader loader = new GithubPrDiffLoader(
                properties,
                new RecordingClient(new GithubPrDiffHttpResponse(200, false, List.of(file)))
        );

        GithubPrDiffLoadResult result = loader.load(metadata(1));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDiff().diffTruncated()).isTrue();
        assertThat(result.getDiff().diffBytes()).isLessThanOrEqualTo(80);
    }

    @Test
    void load_withoutTextualPatch_mapsToDiffUnavailable() {
        GithubPrDiffFileResponse file = file("assets/logo.png");
        file.setPatch(null);
        GithubPrDiffLoader loader = new GithubPrDiffLoader(
                propertiesWithToken(),
                new RecordingClient(new GithubPrDiffHttpResponse(200, false, List.of(file)))
        );

        GithubPrDiffLoadResult result = loader.load(metadata(1));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ReviewErrorCodes.GITHUB_DIFF_UNAVAILABLE);
    }

    @Test
    void load_authStatus_mapsToAuthFailed() {
        GithubPrDiffLoadResult result = loadWithStatus(401, false);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ReviewErrorCodes.GITHUB_AUTH_FAILED);
    }

    @Test
    void load_rateLimit_mapsToRateLimited() {
        GithubPrDiffLoadResult result = loadWithStatus(403, true);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ReviewErrorCodes.GITHUB_RATE_LIMITED);
    }

    @Test
    void load_notFound_mapsToPrNotFound() {
        GithubPrDiffLoadResult result = loadWithStatus(404, false);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ReviewErrorCodes.GITHUB_PR_NOT_FOUND);
    }

    @Test
    void load_serverError_mapsToDiffLoadFailed() {
        GithubPrDiffLoadResult result = loadWithStatus(500, false);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ReviewErrorCodes.GITHUB_DIFF_LOAD_FAILED);
    }

    private static GithubPrDiffLoadResult loadWithStatus(int status, boolean rateLimited) {
        GithubPrDiffLoader loader = new GithubPrDiffLoader(
                propertiesWithToken(),
                new RecordingClient(new GithubPrDiffHttpResponse(status, rateLimited, null))
        );
        return loader.load(metadata(1));
    }

    private static GithubProperties propertiesWithToken() {
        GithubProperties properties = new GithubProperties();
        properties.setToken("test-token");
        return properties;
    }

    private static GithubPrMetadata metadata(int changedFiles) {
        return new GithubPrMetadata(
                "example",
                "repo",
                12,
                "Improve review flow",
                "octocat",
                "main",
                "feature",
                "base-sha",
                "head-sha",
                "open",
                "2026-06-26T01:00:00Z",
                "2026-06-26T02:00:00Z",
                changedFiles,
                120,
                40
        );
    }

    private static GithubPrDiffFileResponse file(String filename) {
        GithubPrDiffFileResponse file = new GithubPrDiffFileResponse();
        file.setFilename(filename);
        file.setStatus("modified");
        file.setAdditions(1);
        file.setDeletions(0);
        file.setChanges(1);
        file.setPatch("@@ -1 +1 @@\n+const password = request.query.password;\n");
        return file;
    }

    private static class RecordingClient implements GithubPrDiffHttpClient {
        private final GithubPrDiffHttpResponse response;
        private boolean called;
        private String capturedToken;
        private int capturedMaxFilesToFetch;

        private RecordingClient(GithubPrDiffHttpResponse response) {
            this.response = response;
        }

        @Override
        public GithubPrDiffHttpResponse fetchPullRequestFiles(String apiBaseUrl,
                                                              GithubRepositoryRef repository,
                                                              int prNumber,
                                                              String token,
                                                              int timeoutSeconds,
                                                              int maxFilesToFetch) {
            called = true;
            capturedToken = token;
            capturedMaxFilesToFetch = maxFilesToFetch;
            return response;
        }
    }
}
