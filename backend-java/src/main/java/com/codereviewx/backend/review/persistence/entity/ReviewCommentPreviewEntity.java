package com.codereviewx.backend.review.persistence.entity;

import com.codereviewx.backend.review.enums.PublishStatus;
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
@Table(name = "review_comment_preview")
public class ReviewCommentPreviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_run_id", nullable = false)
    private Long reviewRunId;

    @Column(name = "review_issue_id")
    private Long reviewIssueId;

    @Column(name = "issue_key", length = 255)
    private String issueKey;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(length = 16)
    private String side;

    @Column(name = "draft_body", nullable = false, length = 4000)
    private String draftBody;

    @Column(length = 16)
    private String severity;

    @Column(length = 32)
    private String category;

    @Column(length = 32)
    private String source;

    @Column(name = "selected_for_publish", nullable = false)
    private Boolean selectedForPublish = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "publish_status", nullable = false, length = 32)
    private PublishStatus publishStatus = PublishStatus.NOT_PUBLISHED;

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

    public Long getReviewRunId() {
        return reviewRunId;
    }

    public void setReviewRunId(Long reviewRunId) {
        this.reviewRunId = reviewRunId;
    }

    public Long getReviewIssueId() {
        return reviewIssueId;
    }

    public void setReviewIssueId(Long reviewIssueId) {
        this.reviewIssueId = reviewIssueId;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public void setIssueKey(String issueKey) {
        this.issueKey = issueKey;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getDraftBody() {
        return draftBody;
    }

    public void setDraftBody(String draftBody) {
        this.draftBody = draftBody;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Boolean getSelectedForPublish() {
        return selectedForPublish;
    }

    public void setSelectedForPublish(Boolean selectedForPublish) {
        this.selectedForPublish = selectedForPublish;
    }

    public PublishStatus getPublishStatus() {
        return publishStatus;
    }

    public void setPublishStatus(PublishStatus publishStatus) {
        this.publishStatus = publishStatus;
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
