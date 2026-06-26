package com.codereviewx.backend.review.dto;

public class InputSnapshotSummaryResponse {

    private String repoUrl;
    private String owner;
    private String repo;
    private Integer prNumber;
    private String baseRef;
    private String headRef;
    private String baseSha;
    private String headSha;
    private String prTitle;
    private String prAuthor;
    private Integer changedFiles;
    private Integer additions;
    private Integer deletions;
    private Boolean diffTruncated;
    private Boolean contextTruncated;

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getRepo() {
        return repo;
    }

    public void setRepo(String repo) {
        this.repo = repo;
    }

    public Integer getPrNumber() {
        return prNumber;
    }

    public void setPrNumber(Integer prNumber) {
        this.prNumber = prNumber;
    }

    public String getBaseRef() {
        return baseRef;
    }

    public void setBaseRef(String baseRef) {
        this.baseRef = baseRef;
    }

    public String getHeadRef() {
        return headRef;
    }

    public void setHeadRef(String headRef) {
        this.headRef = headRef;
    }

    public String getBaseSha() {
        return baseSha;
    }

    public void setBaseSha(String baseSha) {
        this.baseSha = baseSha;
    }

    public String getHeadSha() {
        return headSha;
    }

    public void setHeadSha(String headSha) {
        this.headSha = headSha;
    }

    public String getPrTitle() {
        return prTitle;
    }

    public void setPrTitle(String prTitle) {
        this.prTitle = prTitle;
    }

    public String getPrAuthor() {
        return prAuthor;
    }

    public void setPrAuthor(String prAuthor) {
        this.prAuthor = prAuthor;
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

    public Boolean getDiffTruncated() {
        return diffTruncated;
    }

    public void setDiffTruncated(Boolean diffTruncated) {
        this.diffTruncated = diffTruncated;
    }

    public Boolean getContextTruncated() {
        return contextTruncated;
    }

    public void setContextTruncated(Boolean contextTruncated) {
        this.contextTruncated = contextTruncated;
    }
}
