package com.codereviewx.backend.review.pipeline;

import java.time.LocalDateTime;

/**
 * Input context for a review pipeline run.
 * Round 10: optional pasted PR diff text for AI review grounding.
 */
public class ReviewContext {

    private final Long taskId;
    private final String repoUrl;
    private final Integer prNumber;
    private final LocalDateTime createdAt;
    private final String diffText;

    public ReviewContext(Long taskId, String repoUrl, Integer prNumber, LocalDateTime createdAt) {
        this(taskId, repoUrl, prNumber, createdAt, null);
    }

    public ReviewContext(Long taskId, String repoUrl, Integer prNumber, LocalDateTime createdAt, String diffText) {
        this.taskId = taskId;
        this.repoUrl = repoUrl;
        this.prNumber = prNumber;
        this.createdAt = createdAt;
        this.diffText = diffText;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public Integer getPrNumber() {
        return prNumber;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getDiffText() {
        return diffText;
    }

    public boolean hasDiffText() {
        return diffText != null && !diffText.isBlank();
    }
}
