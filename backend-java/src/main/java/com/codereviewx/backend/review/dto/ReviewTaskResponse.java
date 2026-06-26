package com.codereviewx.backend.review.dto;

import com.codereviewx.backend.review.enums.ReviewTaskStatus;
import com.codereviewx.backend.review.enums.RiskLevel;

import java.time.LocalDateTime;
import java.util.List;

public class ReviewTaskResponse {

    private Long id;
    private String repoUrl;
    private Integer prNumber;
    private ReviewTaskStatus status;
    private String summary;
    private RiskLevel riskLevel;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ReviewIssueResponse> issues;
    private IssueSummaryResponse issueSummary;
    private String requestedProvider;
    private String providerUsed;
    private Boolean providerHit;

    public ReviewTaskResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public Integer getPrNumber() {
        return prNumber;
    }

    public void setPrNumber(Integer prNumber) {
        this.prNumber = prNumber;
    }

    public ReviewTaskStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewTaskStatus status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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

    public List<ReviewIssueResponse> getIssues() {
        return issues;
    }

    public void setIssues(List<ReviewIssueResponse> issues) {
        this.issues = issues;
    }

    public IssueSummaryResponse getIssueSummary() {
        return issueSummary;
    }

    public void setIssueSummary(IssueSummaryResponse issueSummary) {
        this.issueSummary = issueSummary;
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
}
