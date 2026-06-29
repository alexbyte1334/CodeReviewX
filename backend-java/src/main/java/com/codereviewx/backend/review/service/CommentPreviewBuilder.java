package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.enums.PublishStatus;
import com.codereviewx.backend.review.persistence.entity.ReviewCommentPreviewEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewIssueEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewCommentPreviewRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CommentPreviewBuilder {

    private final ReviewCommentPreviewRepository commentPreviewRepository;

    public CommentPreviewBuilder(ReviewCommentPreviewRepository commentPreviewRepository) {
        this.commentPreviewRepository = commentPreviewRepository;
    }

    public void buildForRun(Long runId, List<ReviewIssueEntity> issues, LocalDateTime now) {
        List<ReviewCommentPreviewEntity> previews = issues.stream()
                .map(issue -> toCommentPreview(runId, issue, now))
                .collect(Collectors.toList());
        commentPreviewRepository.saveAll(previews);
    }

    private ReviewCommentPreviewEntity toCommentPreview(Long runId,
                                                        ReviewIssueEntity issue,
                                                        LocalDateTime now) {
        ReviewCommentPreviewEntity preview = new ReviewCommentPreviewEntity();
        preview.setReviewRunId(runId);
        preview.setReviewIssueId(issue.getId());
        preview.setIssueKey(issue.getIssueKey());
        preview.setFilePath(issue.getFilePath());
        preview.setLineNumber(issue.getStartLine());
        preview.setSide("RIGHT");
        preview.setDraftBody(buildDraftComment(issue));
        preview.setSeverity(issue.getSeverity().name());
        preview.setCategory(issue.getCategory().name());
        preview.setSource(issue.getSource().name());
        preview.setSelectedForPublish(false);
        preview.setPublishStatus(PublishStatus.NOT_PUBLISHED);
        preview.setCreatedAt(now);
        preview.setUpdatedAt(now);
        return preview;
    }

    private String buildDraftComment(ReviewIssueEntity issue) {
        return "Severity: " + issue.getSeverity()
                + "\nCategory: " + issue.getCategory()
                + "\n\n" + issue.getTitle()
                + "\n\n" + issue.getDescription()
                + "\n\nSuggestion: " + issue.getRecommendation();
    }
}
