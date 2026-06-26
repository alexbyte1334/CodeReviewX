package com.codereviewx.backend.review.dto;

import com.codereviewx.backend.review.enums.PublishStatus;

public class CommentPreviewItemResponse {

    private Long id;
    private String issueId;
    private String filePath;
    private Integer line;
    private String draftBody;
    private Boolean selectedForPublish;
    private PublishStatus publishStatus;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIssueId() {
        return issueId;
    }

    public void setIssueId(String issueId) {
        this.issueId = issueId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Integer getLine() {
        return line;
    }

    public void setLine(Integer line) {
        this.line = line;
    }

    public String getDraftBody() {
        return draftBody;
    }

    public void setDraftBody(String draftBody) {
        this.draftBody = draftBody;
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
}
