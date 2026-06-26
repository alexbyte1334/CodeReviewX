package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.dto.CommentPreviewItemResponse;
import com.codereviewx.backend.review.dto.CommentPreviewListResponse;
import com.codereviewx.backend.review.dto.InputSnapshotSummaryResponse;
import com.codereviewx.backend.review.dto.ProviderSummaryResponse;
import com.codereviewx.backend.review.dto.ReviewRunResponse;
import com.codereviewx.backend.review.dto.ToolTraceItemResponse;
import com.codereviewx.backend.review.dto.ToolTraceListResponse;
import com.codereviewx.backend.review.exception.ReviewRunNotFoundException;
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

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReviewRunService {

    private final ReviewRunRepository reviewRunRepository;
    private final ReviewInputSnapshotRepository inputSnapshotRepository;
    private final ReviewProviderTraceRepository providerTraceRepository;
    private final ReviewToolTraceRepository toolTraceRepository;
    private final ReviewCommentPreviewRepository commentPreviewRepository;

    public ReviewRunService(ReviewRunRepository reviewRunRepository,
                            ReviewInputSnapshotRepository inputSnapshotRepository,
                            ReviewProviderTraceRepository providerTraceRepository,
                            ReviewToolTraceRepository toolTraceRepository,
                            ReviewCommentPreviewRepository commentPreviewRepository) {
        this.reviewRunRepository = reviewRunRepository;
        this.inputSnapshotRepository = inputSnapshotRepository;
        this.providerTraceRepository = providerTraceRepository;
        this.toolTraceRepository = toolTraceRepository;
        this.commentPreviewRepository = commentPreviewRepository;
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

    private void requireRunExists(Long runId) {
        if (!reviewRunRepository.existsById(runId)) {
            throw new ReviewRunNotFoundException(runId);
        }
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
        return item;
    }
}
