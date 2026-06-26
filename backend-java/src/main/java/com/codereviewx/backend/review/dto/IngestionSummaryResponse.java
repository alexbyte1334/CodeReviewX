package com.codereviewx.backend.review.dto;

public class IngestionSummaryResponse {

    private String headSha;
    private String baseSha;
    private Integer changedFiles;
    private Integer additions;
    private Integer deletions;
    private Boolean truncated;

    public IngestionSummaryResponse() {
    }

    public IngestionSummaryResponse(String headSha, String baseSha, Integer changedFiles,
                                    Integer additions, Integer deletions, Boolean truncated) {
        this.headSha = headSha;
        this.baseSha = baseSha;
        this.changedFiles = changedFiles;
        this.additions = additions;
        this.deletions = deletions;
        this.truncated = truncated;
    }

    public String getHeadSha() {
        return headSha;
    }

    public void setHeadSha(String headSha) {
        this.headSha = headSha;
    }

    public String getBaseSha() {
        return baseSha;
    }

    public void setBaseSha(String baseSha) {
        this.baseSha = baseSha;
    }

    public Integer getChangedFiles() {
        return changedFiles;
    }

    public void setChangedFiles(Integer changedFiles) {
        this.changedFiles = changedFiles;
    }

    public Integer getAdditions() {
        return additions;
    }

    public void setAdditions(Integer additions) {
        this.additions = additions;
    }

    public Integer getDeletions() {
        return deletions;
    }

    public void setDeletions(Integer deletions) {
        this.deletions = deletions;
    }

    public Boolean getTruncated() {
        return truncated;
    }

    public void setTruncated(Boolean truncated) {
        this.truncated = truncated;
    }
}
