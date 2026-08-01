package com.codereviewx.backend.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CreateReviewRequest {
    @NotBlank private String repositoryUrl;
    @Positive private Integer prNumber;
    @Size(max = CreateReviewTaskRequest.MAX_DIFF_TEXT_LENGTH) private String diffText;
    private String inputMode = "GITHUB_PR";

    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
    public Integer getPrNumber() { return prNumber; }
    public void setPrNumber(Integer prNumber) { this.prNumber = prNumber; }
    public String getDiffText() { return diffText; }
    public void setDiffText(String diffText) { this.diffText = diffText; }
    public String getInputMode() { return inputMode; }
    public void setInputMode(String inputMode) { this.inputMode = inputMode; }
}
