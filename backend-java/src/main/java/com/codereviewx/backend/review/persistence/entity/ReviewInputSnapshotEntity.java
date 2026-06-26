package com.codereviewx.backend.review.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_input_snapshot")
public class ReviewInputSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_run_id", nullable = false)
    private Long reviewRunId;

    @Column(name = "repo_url", nullable = false, length = 500)
    private String repoUrl;

    @Column(length = 128)
    private String owner;

    @Column(length = 128)
    private String repo;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(name = "base_ref", length = 255)
    private String baseRef;

    @Column(name = "head_ref", length = 255)
    private String headRef;

    @Column(name = "base_sha", length = 64)
    private String baseSha;

    @Column(name = "head_sha", length = 64)
    private String headSha;

    @Column(name = "pr_title", length = 500)
    private String prTitle;

    @Column(name = "pr_author", length = 255)
    private String prAuthor;

    @Column(name = "changed_files")
    private Integer changedFiles;

    private Integer additions;

    private Integer deletions;

    @Column(name = "diff_truncated", nullable = false)
    private Boolean diffTruncated = false;

    @Column(name = "context_truncated", nullable = false)
    private Boolean contextTruncated = false;

    @Lob
    @Column(name = "snapshot_json", nullable = false)
    private String snapshotJson;

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

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
