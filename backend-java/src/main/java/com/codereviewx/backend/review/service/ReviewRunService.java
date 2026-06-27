package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.dto.CommentPreviewItemResponse;
import com.codereviewx.backend.review.dto.CommentPreviewListResponse;
import com.codereviewx.backend.review.dto.InputSnapshotSummaryResponse;
import com.codereviewx.backend.review.dto.PublishCommentPreviewRequest;
import com.codereviewx.backend.review.dto.ProviderSummaryResponse;
import com.codereviewx.backend.review.dto.ReviewRunResponse;
import com.codereviewx.backend.review.dto.ToolTraceItemResponse;
import com.codereviewx.backend.review.dto.ToolTraceListResponse;
import com.codereviewx.backend.review.dto.UpdateCommentPreviewSelectionRequest;
import com.codereviewx.backend.review.enums.PublishStatus;
import com.codereviewx.backend.review.exception.CommentPreviewNotFoundException;
import com.codereviewx.backend.review.exception.ReviewRunNotFoundException;
import com.codereviewx.backend.review.exception.ReviewRequestInvalidException;
import com.codereviewx.backend.review.github.GithubPrCommentPublishRequest;
import com.codereviewx.backend.review.github.GithubPrCommentPublishResult;
import com.codereviewx.backend.review.github.GithubPrCommentPublisher;
import com.codereviewx.backend.review.persistence.entity.ReviewCommentPreviewEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewInputSnapshotEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewProviderTraceEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewRunEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewToolTraceEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewCommentPreviewRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewInputSnapshotRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewProviderTraceRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewRunRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewToolTraceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReviewRunService {

    private final ReviewRunRepository reviewRunRepository;
    private final ReviewInputSnapshotRepository inputSnapshotRepository;
    private final ReviewProviderTraceRepository providerTraceRepository;
    private final ReviewToolTraceRepository toolTraceRepository;
    private final ReviewCommentPreviewRepository commentPreviewRepository;
    private final GithubPrCommentPublisher githubPrCommentPublisher;

    public ReviewRunService(ReviewRunRepository reviewRunRepository,
                            ReviewInputSnapshotRepository inputSnapshotRepository,
                            ReviewProviderTraceRepository providerTraceRepository,
                            ReviewToolTraceRepository toolTraceRepository,
                            ReviewCommentPreviewRepository commentPreviewRepository,
                            GithubPrCommentPublisher githubPrCommentPublisher) {
        this.reviewRunRepository = reviewRunRepository;
        this.inputSnapshotRepository = inputSnapshotRepository;
        this.providerTraceRepository = providerTraceRepository;
        this.toolTraceRepository = toolTraceRepository;
        this.commentPreviewRepository = commentPreviewRepository;
        this.githubPrCommentPublisher = githubPrCommentPublisher;
    }

    @Transactional(readOnly = true)
    public ReviewRunResponse getRun(Long runId) {
        ReviewRunEntity run = reviewRunRepository.findById(runId)
                .orElseThrow(() -> new ReviewRunNotFoundException(runId));
        return toRunResponse(run);
    }

    @Transactional(readOnly = true)
    public ToolTraceListResponse getTrace(Long runId) {
        requireRunExists(runId);
        List<ToolTraceItemResponse> items = toolTraceRepository
                .findByReviewRunIdOrderBySequenceNumberAsc(runId)
                .stream()
                .map(this::toToolTraceItem)
                .collect(Collectors.toList());
        return new ToolTraceListResponse(items);
    }

    @Transactional(readOnly = true)
    public CommentPreviewListResponse getCommentPreviews(Long runId) {
        requireRunExists(runId);
        List<CommentPreviewItemResponse> items = commentPreviewRepository
                .findByReviewRunIdOrderByIdAsc(runId)
                .stream()
                .map(this::toCommentPreviewItem)
                .collect(Collectors.toList());
        return new CommentPreviewListResponse(items);
    }

    @Transactional
    public CommentPreviewListResponse updateCommentPreviewSelection(Long runId,
                                                                    UpdateCommentPreviewSelectionRequest request) {
        requireRunExists(runId);
        List<ReviewCommentPreviewEntity> previews = commentPreviewRepository.findByReviewRunIdOrderByIdAsc(runId);
        Set<Long> availableIds = previews.stream()
                .map(ReviewCommentPreviewEntity::getId)
                .collect(Collectors.toSet());
        Set<Long> selectedIds = new HashSet<>(request.getSelectedPreviewIds());
        if (!availableIds.containsAll(selectedIds)) {
            throw new ReviewRequestInvalidException("selectedPreviewIds must belong to the review run");
        }

        LocalDateTime now = LocalDateTime.now();
        for (ReviewCommentPreviewEntity preview : previews) {
            preview.setSelectedForPublish(selectedIds.contains(preview.getId()));
            preview.setUpdatedAt(now);
        }
        commentPreviewRepository.saveAll(previews);
        return toCommentPreviewList(previews);
    }

    @Transactional
    public CommentPreviewItemResponse publishCommentPreview(Long runId,
                                                            Long previewId,
                                                            PublishCommentPreviewRequest request) {
        requireRunExists(runId);
        ReviewCommentPreviewEntity preview = commentPreviewRepository.findByIdAndReviewRunId(previewId, runId)
                .orElseThrow(() -> new CommentPreviewNotFoundException(previewId, runId));
        return toCommentPreviewItem(publishPreview(runId, preview));
    }

    @Transactional
    public CommentPreviewListResponse publishSelectedCommentPreviews(Long runId,
                                                                     PublishCommentPreviewRequest request) {
        requireRunExists(runId);
        List<ReviewCommentPreviewEntity> selectedPreviews =
                commentPreviewRepository.findByReviewRunIdAndSelectedForPublishTrueOrderByIdAsc(runId);
        if (selectedPreviews.isEmpty()) {
            throw new ReviewRequestInvalidException("Select at least one comment preview before publishing");
        }

        selectedPreviews.forEach(preview -> publishPreview(runId, preview));
        return toCommentPreviewList(commentPreviewRepository.findByReviewRunIdOrderByIdAsc(runId));
    }

    private void requireRunExists(Long runId) {
        if (!reviewRunRepository.existsById(runId)) {
            throw new ReviewRunNotFoundException(runId);
        }
    }

    private ReviewCommentPreviewEntity publishPreview(Long runId, ReviewCommentPreviewEntity preview) {
        if (!Boolean.TRUE.equals(preview.getSelectedForPublish())) {
            throw new ReviewRequestInvalidException("Comment preview must be selected before publishing");
        }
        if (preview.getPublishStatus() == PublishStatus.PUBLISHED) {
            return preview;
        }

        ReviewInputSnapshotEntity snapshot = inputSnapshotRepository.findByReviewRunId(runId)
                .orElseThrow(() -> new ReviewRequestInvalidException(
                        "GitHub input snapshot is required before publishing PR comments"
                ));
        validatePublishTarget(snapshot, preview);

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

    private CommentPreviewListResponse toCommentPreviewList(List<ReviewCommentPreviewEntity> previews) {
        List<CommentPreviewItemResponse> items = previews.stream()
                .map(this::toCommentPreviewItem)
                .collect(Collectors.toList());
        return new CommentPreviewListResponse(items);
    }

    private ReviewRunResponse toRunResponse(ReviewRunEntity run) {
        ReviewRunResponse response = new ReviewRunResponse();
        response.setId(run.getId());
        response.setTaskId(run.getReviewTaskId());
        response.setStatus(run.getStatus());
        response.setReviewMode(run.getReviewMode());
        response.setStartedAt(run.getStartedAt());
        response.setFinishedAt(run.getFinishedAt());
        response.setErrorCode(run.getErrorCode());
        response.setErrorMessage(run.getErrorMessage());

        inputSnapshotRepository.findByReviewRunId(run.getId())
                .ifPresent(snapshot -> response.setInputSnapshotSummary(toInputSnapshotSummary(snapshot)));

        providerTraceRepository.findByReviewRunId(run.getId())
                .ifPresent(trace -> response.setProviderSummary(toProviderSummary(trace)));

        return response;
    }

    private InputSnapshotSummaryResponse toInputSnapshotSummary(ReviewInputSnapshotEntity snapshot) {
        InputSnapshotSummaryResponse summary = new InputSnapshotSummaryResponse();
        summary.setRepoUrl(snapshot.getRepoUrl());
        summary.setOwner(snapshot.getOwner());
        summary.setRepo(snapshot.getRepo());
        summary.setPrNumber(snapshot.getPrNumber());
        summary.setBaseRef(snapshot.getBaseRef());
        summary.setHeadRef(snapshot.getHeadRef());
        summary.setBaseSha(snapshot.getBaseSha());
        summary.setHeadSha(snapshot.getHeadSha());
        summary.setPrTitle(snapshot.getPrTitle());
        summary.setPrAuthor(snapshot.getPrAuthor());
        summary.setChangedFiles(snapshot.getChangedFiles());
        summary.setAdditions(snapshot.getAdditions());
        summary.setDeletions(snapshot.getDeletions());
        summary.setDiffTruncated(snapshot.getDiffTruncated());
        summary.setContextTruncated(snapshot.getContextTruncated());
        return summary;
    }

    private ProviderSummaryResponse toProviderSummary(ReviewProviderTraceEntity trace) {
        ProviderSummaryResponse summary = new ProviderSummaryResponse();
        summary.setRequestedProvider(trace.getRequestedProvider());
        summary.setProviderUsed(trace.getProviderUsed());
        summary.setProviderHit(trace.getProviderHit());
        summary.setModelName(trace.getModelName());
        summary.setOutputSummary(trace.getOutputSummary());
        summary.setFindingCount(trace.getFindingCount());
        summary.setFallbackReason(trace.getFallbackReason());
        return summary;
    }

    private ToolTraceItemResponse toToolTraceItem(ReviewToolTraceEntity entity) {
        ToolTraceItemResponse item = new ToolTraceItemResponse();
        item.setId(entity.getId());
        item.setToolName(entity.getToolName());
        item.setStatus(entity.getStatus());
        item.setStartedAt(entity.getStartedAt());
        item.setFinishedAt(entity.getFinishedAt());
        item.setDurationMs(entity.getDurationMs());
        item.setOutputSummary(entity.getOutputSummary());
        item.setErrorCode(entity.getErrorCode());
        return item;
    }

    private CommentPreviewItemResponse toCommentPreviewItem(ReviewCommentPreviewEntity entity) {
        CommentPreviewItemResponse item = new CommentPreviewItemResponse();
        item.setId(entity.getId());
        item.setIssueId(entity.getIssueKey());
        item.setFilePath(entity.getFilePath());
        item.setLine(entity.getLineNumber());
        item.setDraftBody(entity.getDraftBody());
        item.setSelectedForPublish(entity.getSelectedForPublish());
        item.setPublishStatus(entity.getPublishStatus());
        item.setGithubCommentId(entity.getGithubCommentId());
        item.setPublishErrorMessage(entity.getPublishErrorMessage());
        return item;
    }
}
