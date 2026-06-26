package com.codereviewx.backend.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CreateReviewTaskRequest {

    public static final int MAX_DIFF_TEXT_LENGTH = 20000;

    @NotBlank
    private String repoUrl;

    @Positive
    private Integer prNumber;

    @Size(max = MAX_DIFF_TEXT_LENGTH, message = "diffText is too large. Maximum length is 20000 characters.")
    private String diffText;

    /**
     * Optional per-request provider override: {@code mock} or {@code mimo}.
     * When omitted, the server default from configuration is used.
     */
    @Pattern(regexp = "^(?i)(mock|mimo)$", message = "provider must be mock or mimo")
    private String provider;

    public CreateReviewTaskRequest() {
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

    public String getDiffText() {
        return diffText;
    }

    public void setDiffText(String diffText) {
        this.diffText = diffText;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
