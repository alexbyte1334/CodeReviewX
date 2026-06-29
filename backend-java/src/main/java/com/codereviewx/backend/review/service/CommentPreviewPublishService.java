package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.enums.PublishStatus;
import com.codereviewx.backend.review.exception.CommentPreviewNotFoundException;
import com.codereviewx.backend.review.exception.ReviewRequestInvalidException;
import com.codereviewx.backend.review.github.GithubPrCommentPublishRequest;
import com.codereviewx.backend.review.github.GithubPrCommentPublishResult;
import com.codereviewx.backend.review.github.GithubPrCommentPublisher;
import com.codereviewx.backend.review.persistence.entity.ReviewCommentPreviewEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewInputSnapshotEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewCommentPreviewRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewInputSnapshotRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CommentPreviewPublishService {

    private final ReviewInputSnapshotRepository inputSnapshotRepository;
    private final ReviewCommentPreviewRepository commentPreviewRepository;
    private final GithubPrCommentPublisher githubPrCommentPublisher;

    public CommentPreviewPublishService(ReviewInputSnapshotRepository inputSnapshotRepository,
                                        ReviewCommentPreviewRepository commentPreviewRepository,
                                        GithubPrCommentPublisher githubPrCommentPublisher) {
        this.inputSnapshotRepository = inputSnapshotRepository;
        this.commentPreviewRepository = commentPreviewRepository;
        this.githubPrCommentPublisher = githubPrCommentPublisher;
    }

    public ReviewCommentPreviewEntity publishOne(Long runId, Long previewId) {
        ReviewCommentPreviewEntity preview = commentPreviewRepository.findByIdAndReviewRunId(previewId, runId)
                .orElseThrow(() -> new CommentPreviewNotFoundException(previewId, runId));
        requireSelectedForPublish(preview);
        if (preview.getPublishStatus() == PublishStatus.PUBLISHED) {
            return preview;
        }
        ReviewInputSnapshotEntity snapshot = loadPublishSnapshot(runId);
        validatePublishTarget(snapshot, preview);
        return publishPreview(snapshot, preview);
    }

    public List<ReviewCommentPreviewEntity> publishSelected(Long runId) {
        List<ReviewCommentPreviewEntity> selectedPreviews =
                commentPreviewRepository.findByReviewRunIdAndSelectedForPublishTrueOrderByIdAsc(runId);
        if (selectedPreviews.isEmpty()) {
            throw new ReviewRequestInvalidException("Select at least one comment preview before publishing");
        }

        ReviewInputSnapshotEntity snapshot = loadPublishSnapshot(runId);
        selectedPreviews.stream()
                .filter(preview -> preview.getPublishStatus() != PublishStatus.PUBLISHED)
                .forEach(preview -> validatePublishTarget(snapshot, preview));
        selectedPreviews.forEach(preview -> publishPreview(snapshot, preview));
        return commentPreviewRepository.findByReviewRunIdOrderByIdAsc(runId);
    }

    private ReviewInputSnapshotEntity loadPublishSnapshot(Long runId) {
        return inputSnapshotRepository.findByReviewRunId(runId)
                .orElseThrow(() -> new ReviewRequestInvalidException(
                        "GitHub input snapshot is required before publishing PR comments"
                ));
    }

    private ReviewCommentPreviewEntity publishPreview(ReviewInputSnapshotEntity snapshot,
                                                      ReviewCommentPreviewEntity preview) {
        requireSelectedForPublish(preview);
        if (preview.getPublishStatus() == PublishStatus.PUBLISHED) {
            return preview;
        }

        LocalDateTime startedAt = LocalDateTime.now();
        preview.setPublishStatus(PublishStatus.PUBLISHING);
        preview.setPublishErrorMessage(null);
        preview.setUpdatedAt(startedAt);
        commentPreviewRepository.save(preview);

        GithubPrCommentPublishResult result = githubPrCommentPublisher.publish(toPublishRequest(snapshot, preview));
        LocalDateTime finishedAt = LocalDateTime.now();
        if (result.isSuccess()) {
            preview.setPublishStatus(PublishStatus.PUBLISHED);
            preview.setGithubCommentId(result.getGithubCommentId());
            preview.setPublishErrorMessage(null);
            preview.setPublishedAt(finishedAt);
        } else {
            preview.setPublishStatus(PublishStatus.FAILED);
            preview.setGithubCommentId(null);
            preview.setPublishErrorMessage(result.getErrorMessage());
        }
        preview.setUpdatedAt(finishedAt);
        return commentPreviewRepository.save(preview);
    }

    private void requireSelectedForPublish(ReviewCommentPreviewEntity preview) {
        if (!Boolean.TRUE.equals(preview.getSelectedForPublish())) {
            throw new ReviewRequestInvalidException("Comment preview must be selected before publishing");
        }
    }

    private void validatePublishTarget(ReviewInputSnapshotEntity snapshot, ReviewCommentPreviewEntity preview) {
        if (snapshot.getOwner() == null || snapshot.getOwner().isBlank()
                || snapshot.getRepo() == null || snapshot.getRepo().isBlank()
                || snapshot.getPrNumber() == null
                || snapshot.getHeadSha() == null || snapshot.getHeadSha().isBlank()) {
            throw new ReviewRequestInvalidException(
                    "GitHub PR metadata is incomplete; cannot publish comment preview"
            );
        }
        if (preview.getFilePath() == null || preview.getFilePath().isBlank()
                || preview.getLineNumber() == null) {
            throw new ReviewRequestInvalidException(
                    "Comment preview must include file path and line before publishing"
            );
        }
    }

    private GithubPrCommentPublishRequest toPublishRequest(ReviewInputSnapshotEntity snapshot,
                                                           ReviewCommentPreviewEntity preview) {
        return new GithubPrCommentPublishRequest(
                snapshot.getOwner(),
                snapshot.getRepo(),
                snapshot.getPrNumber(),
                snapshot.getHeadSha(),
                preview.getFilePath(),
                preview.getLineNumber(),
                preview.getSide(),
                preview.getDraftBody()
        );
    }
}
