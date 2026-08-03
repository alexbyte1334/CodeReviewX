package com.codereviewx.backend.review.dto;

import java.util.List;

public record ReviewApiSnapshot(String runId, String status, String mode, String repositoryUrl,
                                Integer prNumber,
                                String snapshotUrl, String eventsUrl,
                                ReviewTaskResponse review, List<ReviewApiEvent> events,
                                String errorCode, String errorMessage) {
    public record ReviewApiEvent(long sequence, String type, String status, String summary, String errorCode, String createdAt) {}
}
