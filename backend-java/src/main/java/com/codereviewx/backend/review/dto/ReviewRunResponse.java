package com.codereviewx.backend.review.dto;

import com.codereviewx.backend.review.enums.ReviewMode;
import com.codereviewx.backend.review.enums.ReviewRunStatus;

import java.time.LocalDateTime;

public class ReviewRunResponse {

    private Long id;
    private Long taskId;
    private ReviewRunStatus status;
    private ReviewMode reviewMode;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorCode;
    private String errorMessage;
    private InputSnapshotSummaryResponse inputSnapshotSummary;
    private ProviderSummaryResponse providerSummary;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public ReviewRunStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewRunStatus status) {
        this.status = status;
    }

    public ReviewMode getReviewMode() {
        return reviewMode;
    }

    public void setReviewMode(ReviewMode reviewMode) {
        this.reviewMode = reviewMode;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public InputSnapshotSummaryResponse getInputSnapshotSummary() {
        return inputSnapshotSummary;
    }

    public void setInputSnapshotSummary(InputSnapshotSummaryResponse inputSnapshotSummary) {
        this.inputSnapshotSummary = inputSnapshotSummary;
    }

    public ProviderSummaryResponse getProviderSummary() {
        return providerSummary;
    }

    public void setProviderSummary(ProviderSummaryResponse providerSummary) {
        this.providerSummary = providerSummary;
    }
}
