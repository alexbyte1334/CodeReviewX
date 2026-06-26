package com.codereviewx.backend.review.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_provider_trace")
public class ReviewProviderTraceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_run_id", nullable = false)
    private Long reviewRunId;

    @Column(name = "requested_provider", length = 32)
    private String requestedProvider;

    @Column(name = "provider_used", length = 32)
    private String providerUsed;

    @Column(name = "provider_hit")
    private Boolean providerHit;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "input_summary", length = 1000)
    private String inputSummary;

    @Column(name = "output_summary", length = 2000)
    private String outputSummary;

    @Column(name = "finding_count")
    private Integer findingCount;

    @Column(name = "normalization_summary", length = 1000)
    private String normalizationSummary;

    @Column(name = "fallback_reason", length = 1000)
    private String fallbackReason;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReviewRunId() {
        return reviewRunId;
    }

    public void setReviewRunId(Long reviewRunId) {
        this.reviewRunId = reviewRunId;
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

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public void setInputSummary(String inputSummary) {
        this.inputSummary = inputSummary;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public void setOutputSummary(String outputSummary) {
        this.outputSummary = outputSummary;
    }

    public Integer getFindingCount() {
        return findingCount;
    }

    public void setFindingCount(Integer findingCount) {
        this.findingCount = findingCount;
    }

    public String getNormalizationSummary() {
        return normalizationSummary;
    }

    public void setNormalizationSummary(String normalizationSummary) {
        this.normalizationSummary = normalizationSummary;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
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

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
