package com.codereviewx.backend.review.github;

public class GithubPrMetadataLoadResult {

    private final boolean success;
    private final GithubPrMetadata metadata;
    private final String errorCode;
    private final String errorMessage;

    private GithubPrMetadataLoadResult(boolean success, GithubPrMetadata metadata,
                                       String errorCode, String errorMessage) {
        this.success = success;
        this.metadata = metadata;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static GithubPrMetadataLoadResult success(GithubPrMetadata metadata) {
        return new GithubPrMetadataLoadResult(true, metadata, null, null);
    }

    public static GithubPrMetadataLoadResult failure(String errorCode, String errorMessage) {
        return new GithubPrMetadataLoadResult(false, null, errorCode, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public GithubPrMetadata getMetadata() {
        return metadata;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
