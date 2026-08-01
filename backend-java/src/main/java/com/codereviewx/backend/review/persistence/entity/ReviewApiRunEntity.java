package com.codereviewx.backend.review.persistence.entity;

import com.codereviewx.backend.review.enums.ReviewMode;
import com.codereviewx.backend.review.enums.ReviewRunStatus;
import com.codereviewx.backend.review.enums.ReviewTaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.Length;

import java.time.LocalDateTime;

/** The sole durable aggregate root for a public review request and its execution. */
@Entity
@Table(name = "review_api_run")
public class ReviewApiRunEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "repo_url", nullable = false)
    private String repoUrl;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(name = "diff_text", columnDefinition = "TEXT")
    private String diffText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReviewTaskStatus status;

    @Column(length = 1000)
    private String summary;

    @Column(name = "requested_provider", length = 32)
    private String requestedProvider;

    @Column(name = "provider_used", length = 32)
    private String providerUsed;

    @Column(name = "provider_hit")
    private Boolean providerHit;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_mode", nullable = false, length = 32)
    private ReviewMode reviewMode;

    @Column(name = "run_number", nullable = false)
    private Integer runNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 32)
    private ReviewRunStatus executionStatus;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public Integer getPrNumber() { return prNumber; }
    public void setPrNumber(Integer prNumber) { this.prNumber = prNumber; }
    public String getDiffText() { return diffText; }
    public void setDiffText(String diffText) { this.diffText = diffText; }
    public ReviewTaskStatus getStatus() { return status; }
    public void setStatus(ReviewTaskStatus status) { this.status = status; }
    public void setStatus(ReviewRunStatus status) { this.executionStatus = status; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getRequestedProvider() { return requestedProvider; }
    public void setRequestedProvider(String requestedProvider) { this.requestedProvider = requestedProvider; }
    public String getProviderUsed() { return providerUsed; }
    public void setProviderUsed(String providerUsed) { this.providerUsed = providerUsed; }
    public Boolean getProviderHit() { return providerHit; }
    public void setProviderHit(Boolean providerHit) { this.providerHit = providerHit; }
    public ReviewMode getReviewMode() { return reviewMode; }
    public void setReviewMode(ReviewMode reviewMode) { this.reviewMode = reviewMode; }
    public Integer getRunNumber() { return runNumber; }
    public void setRunNumber(Integer runNumber) { this.runNumber = runNumber; }
    public ReviewRunStatus getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(ReviewRunStatus executionStatus) { this.executionStatus = executionStatus; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
