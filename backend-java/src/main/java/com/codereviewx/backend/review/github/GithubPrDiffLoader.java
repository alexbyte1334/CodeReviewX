package com.codereviewx.backend.review.github;

import com.codereviewx.backend.review.ReviewErrorCodes;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class GithubPrDiffLoader {

    public static final String TOOL_NAME = "github.pr.diff.load";
    public static final String AUTH_MISSING_MESSAGE =
            "Local GitHub token is not configured. Set GITHUB_TOKEN to enable GitHub PR diff ingestion.";
    public static final String AUTH_FAILED_MESSAGE =
            "GitHub authentication failed while loading PR diff.";
    public static final String DIFF_FAILED_MESSAGE =
            "GitHub PR diff could not be loaded.";
    public static final String DIFF_TOO_LARGE_MESSAGE =
            "GitHub PR diff exceeds the configured safe ingestion limits.";
    public static final String DIFF_UNAVAILABLE_MESSAGE =
            "GitHub PR diff does not include a textual patch that can be reviewed.";

    private final GithubProperties properties;
    private final GithubPrDiffHttpClient httpClient;

    public GithubPrDiffLoader(GithubProperties properties, GithubPrDiffHttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public GithubPrDiffLoadResult load(GithubPrMetadata metadata) {
        if (!properties.hasToken()) {
            return GithubPrDiffLoadResult.failure(
                    ReviewErrorCodes.GITHUB_AUTH_MISSING,
                    AUTH_MISSING_MESSAGE
            );
        }

        int maxChangedFiles = Math.max(1, properties.getMaxChangedFiles());
        if (metadata.changedFiles() != null && metadata.changedFiles() > maxChangedFiles) {
            return GithubPrDiffLoadResult.failure(
                    ReviewErrorCodes.GITHUB_DIFF_TOO_LARGE,
                    DIFF_TOO_LARGE_MESSAGE
            );
        }

        GithubRepositoryRef repository = new GithubRepositoryRef(metadata.owner(), metadata.repo());
        try {
            GithubPrDiffHttpResponse response = httpClient.fetchPullRequestFiles(
                    properties.getApiBaseUrl(),
                    repository,
                    metadata.prNumber(),
                    properties.getToken(),
                    properties.getTimeoutSeconds(),
                    maxChangedFiles + 1
            );
            return toResult(response, maxChangedFiles);
        } catch (GithubPrMetadataClientException ex) {
            return GithubPrDiffLoadResult.failure(
                    ReviewErrorCodes.GITHUB_DIFF_LOAD_FAILED,
                    DIFF_FAILED_MESSAGE
            );
        }
    }

    private GithubPrDiffLoadResult toResult(GithubPrDiffHttpResponse response, int maxChangedFiles) {
        int status = response.statusCode();
        if (status == 401 || status == 403 && !response.rateLimited()) {
            return GithubPrDiffLoadResult.failure(
                    ReviewErrorCodes.GITHUB_AUTH_FAILED,
                    AUTH_FAILED_MESSAGE
            );
        }
        if (status == 403 && response.rateLimited()) {
            return GithubPrDiffLoadResult.failure(
                    ReviewErrorCodes.GITHUB_RATE_LIMITED,
                    GithubPrMetadataLoader.RATE_LIMITED_MESSAGE
            );
        }
        if (status == 404) {
            return GithubPrDiffLoadResult.failure(
                    ReviewErrorCodes.GITHUB_PR_NOT_FOUND,
                    GithubPrMetadataLoader.PR_NOT_FOUND_MESSAGE
            );
        }
        if (status < 200 || status >= 300 || response.files() == null) {
            return GithubPrDiffLoadResult.failure(
                    ReviewErrorCodes.GITHUB_DIFF_LOAD_FAILED,
                    DIFF_FAILED_MESSAGE
            );
        }
        if (response.files().size() > maxChangedFiles) {
            return GithubPrDiffLoadResult.failure(
                    ReviewErrorCodes.GITHUB_DIFF_TOO_LARGE,
                    DIFF_TOO_LARGE_MESSAGE
            );
        }

        GithubPrDiff diff = buildBoundedDiff(response.files());
        if (diff.diffText().isBlank()) {
            return GithubPrDiffLoadResult.failure(
                    ReviewErrorCodes.GITHUB_DIFF_UNAVAILABLE,
                    DIFF_UNAVAILABLE_MESSAGE
            );
        }
        return GithubPrDiffLoadResult.success(diff);
    }

    private GithubPrDiff buildBoundedDiff(List<GithubPrDiffFileResponse> responses) {
        int maxDiffBytes = Math.max(1, properties.getMaxDiffBytes());
        int perFilePatchMaxBytes = Math.max(1, properties.getPerFilePatchMaxBytes());
        StringBuilder diffBuilder = new StringBuilder();
        List<GithubPrDiffFile> files = new ArrayList<>();
        boolean diffTruncated = false;

        for (GithubPrDiffFileResponse response : responses) {
            String patch = response.getPatch();
            if (patch == null || patch.isBlank()) {
                files.add(toFileSummary(response, 0, false));
                continue;
            }

            BoundedText boundedPatch = boundText(patch, perFilePatchMaxBytes);
            String fileBlock = buildFileBlock(response, boundedPatch.text(), boundedPatch.truncated());
            int currentBytes = byteLength(diffBuilder.toString());
            int fileBlockBytes = byteLength(fileBlock);
            int remainingBytes = maxDiffBytes - currentBytes;

            if (remainingBytes <= 0) {
                diffTruncated = true;
                files.add(toFileSummary(response, byteLength(boundedPatch.text()), true));
                break;
            }

            if (fileBlockBytes > remainingBytes) {
                String notice = "\n[CodeReviewX: diff truncated at " + maxDiffBytes + " bytes]\n";
                int fileBlockLimit = Math.max(0, remainingBytes - byteLength(notice));
                BoundedText boundedFileBlock = boundText(fileBlock, fileBlockLimit);
                diffBuilder.append(boundedFileBlock.text());
                if (!diffBuilder.isEmpty() && !diffBuilder.toString().endsWith("\n")) {
                    diffBuilder.append('\n');
                }
                int noticeLimit = maxDiffBytes - byteLength(diffBuilder.toString());
                diffBuilder.append(boundText(notice, Math.max(0, noticeLimit)).text());
                diffTruncated = true;
                files.add(toFileSummary(response, byteLength(boundedPatch.text()), true));
                break;
            }

            diffBuilder.append(fileBlock);
            files.add(toFileSummary(response, byteLength(boundedPatch.text()), boundedPatch.truncated()));
            diffTruncated = diffTruncated || boundedPatch.truncated();
        }

        return new GithubPrDiff(
                diffBuilder.toString(),
                responses.size(),
                byteLength(diffBuilder.toString()),
                diffTruncated,
                files
        );
    }

    private static String buildFileBlock(GithubPrDiffFileResponse response, String patch, boolean patchTruncated) {
        String filename = response.getFilename() == null ? "unknown" : response.getFilename();
        StringBuilder builder = new StringBuilder();
        builder.append("diff --git a/")
                .append(filename)
                .append(" b/")
                .append(filename)
                .append('\n');
        builder.append("# status=")
                .append(response.getStatus())
                .append(", additions=")
                .append(response.getAdditions())
                .append(", deletions=")
                .append(response.getDeletions())
                .append('\n');
        builder.append(patch);
        if (!patch.endsWith("\n")) {
            builder.append('\n');
        }
        if (patchTruncated) {
            builder.append("[CodeReviewX: file patch truncated]\n");
        }
        return builder.toString();
    }

    private static GithubPrDiffFile toFileSummary(GithubPrDiffFileResponse response,
                                                  int patchBytes,
                                                  boolean patchTruncated) {
        return new GithubPrDiffFile(
                response.getFilename(),
                response.getStatus(),
                response.getAdditions(),
                response.getDeletions(),
                response.getChanges(),
                patchBytes,
                patchTruncated
        );
    }

    private static BoundedText boundText(String text, int maxBytes) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return new BoundedText(text, false);
        }
        int end = Math.min(text.length(), maxBytes);
        while (byteLength(text.substring(0, end)) > maxBytes && end > 0) {
            end--;
        }
        return new BoundedText(text.substring(0, end), true);
    }

    private static int byteLength(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private record BoundedText(String text, boolean truncated) {
    }
}
