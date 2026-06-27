package com.codereviewx.backend.review.pipeline;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.codereviewx.backend.review.enums.ReviewMode;

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
    private final String requestedProvider;
    private final ReviewMode reviewMode;
    private final List<ReviewAgentStep> agentSteps = new ArrayList<>();

    public ReviewContext(Long taskId, String repoUrl, Integer prNumber, LocalDateTime createdAt) {
        this(taskId, repoUrl, prNumber, createdAt, null, null, ReviewMode.GITHUB_PR);
    }

    public ReviewContext(Long taskId, String repoUrl, Integer prNumber, LocalDateTime createdAt, String diffText) {
        this(taskId, repoUrl, prNumber, createdAt, diffText, null, ReviewMode.MANUAL_DIFF);
    }

    public ReviewContext(Long taskId,
                         String repoUrl,
                         Integer prNumber,
                         LocalDateTime createdAt,
                         String diffText,
                         String requestedProvider) {
        this(taskId, repoUrl, prNumber, createdAt, diffText, requestedProvider,
                diffText == null || diffText.isBlank() ? ReviewMode.GITHUB_PR : ReviewMode.MANUAL_DIFF);
    }

    public ReviewContext(Long taskId,
                         String repoUrl,
                         Integer prNumber,
                         LocalDateTime createdAt,
                         String diffText,
                         String requestedProvider,
                         ReviewMode reviewMode) {
        this.taskId = taskId;
        this.repoUrl = repoUrl;
        this.prNumber = prNumber;
        this.createdAt = createdAt;
        this.diffText = diffText;
        this.requestedProvider = requestedProvider;
        this.reviewMode = reviewMode;
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

    public String getRequestedProvider() {
        return requestedProvider;
    }

    public ReviewMode getReviewMode() {
        return reviewMode;
    }

    public void addAgentStep(ReviewAgentStep step) {
        agentSteps.add(step);
    }

    public List<ReviewAgentStep> getAgentSteps() {
        return Collections.unmodifiableList(agentSteps);
    }
}
