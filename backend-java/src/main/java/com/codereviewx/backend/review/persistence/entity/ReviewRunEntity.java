package com.codereviewx.backend.review.persistence.entity;

import com.codereviewx.backend.review.enums.ReviewMode;
import com.codereviewx.backend.review.enums.ReviewRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_run")
public class ReviewRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_task_id", nullable = false)
    private Long reviewTaskId;

    @Column(name = "run_number", nullable = false)
    private Integer runNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_mode", nullable = false, length = 32)
    private ReviewMode reviewMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReviewRunStatus status;

    @Column(name = "requested_provider", length = 32)
    private String requestedProvider;

    @Column(name = "provider_used", length = 32)
    private String providerUsed;

    @Column(name = "provider_hit")
    private Boolean providerHit;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReviewTaskId() {
        return reviewTaskId;
    }

    public void setReviewTaskId(Long reviewTaskId) {
        this.reviewTaskId = reviewTaskId;
    }

    public Integer getRunNumber() {
        return runNumber;
    }

    public void setRunNumber(Integer runNumber) {
        this.runNumber = runNumber;
    }

    public ReviewMode getReviewMode() {
        return reviewMode;
    }

    public void setReviewMode(ReviewMode reviewMode) {
        this.reviewMode = reviewMode;
    }

    public ReviewRunStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewRunStatus status) {
        this.status = status;
    }

    public String getRequestedProvider() {
        return requestedProvider;
    }

    public void setRequestedProvider(String requestedProvider) {
        this.requestedProvider = requestedProvider;
    }

    public String getProviderUsed() {
        return providerUsed;
    }

    public void setProviderUsed(String providerUsed) {
        this.providerUsed = providerUsed;
    }

    public Boolean getProviderHit() {
        return providerHit;
    }

    public void setProviderHit(Boolean providerHit) {
        this.providerHit = providerHit;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
