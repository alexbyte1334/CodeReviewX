package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.dto.CommentPreviewItemResponse;
import com.codereviewx.backend.review.dto.CommentPreviewListResponse;
import com.codereviewx.backend.review.dto.InputSnapshotSummaryResponse;
import com.codereviewx.backend.review.dto.ProviderSummaryResponse;
import com.codereviewx.backend.review.dto.ReviewRunResponse;
import com.codereviewx.backend.review.dto.ToolTraceItemResponse;
import com.codereviewx.backend.review.enums.PublishStatus;
import com.codereviewx.backend.review.persistence.entity.ReviewCommentPreviewEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewInputSnapshotEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewProviderTraceEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewRunEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewToolTraceEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewInputSnapshotRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewProviderTraceRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ReviewRunResponseAssembler {

    private final ReviewInputSnapshotRepository inputSnapshotRepository;
    private final ReviewProviderTraceRepository providerTraceRepository;

    public ReviewRunResponseAssembler(ReviewInputSnapshotRepository inputSnapshotRepository,
                                      ReviewProviderTraceRepository providerTraceRepository) {
        this.inputSnapshotRepository = inputSnapshotRepository;
        this.providerTraceRepository = providerTraceRepository;
    }

    public ReviewRunResponse toRunResponse(ReviewRunEntity run) {
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

    public ToolTraceItemResponse toToolTraceItem(ReviewToolTraceEntity entity) {
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

    public CommentPreviewListResponse toCommentPreviewList(List<ReviewCommentPreviewEntity> previews) {
        List<CommentPreviewItemResponse> items = previews.stream()
                .map(this::toCommentPreviewItem)
                .collect(Collectors.toList());
        return new CommentPreviewListResponse(items);
    }

    public CommentPreviewItemResponse toCommentPreviewItem(ReviewCommentPreviewEntity entity) {
        CommentPreviewItemResponse item = new CommentPreviewItemResponse();
        item.setId(entity.getId());
        item.setIssueId(entity.getIssueKey());
        item.setFilePath(entity.getFilePath());
        item.setLine(entity.getLineNumber());
        item.setDraftBody(entity.getDraftBody());
        item.setSelectedForPublish(entity.getSelectedForPublish());
        item.setPublishStatus(entity.getPublishStatus() == null ? PublishStatus.NOT_PUBLISHED : entity.getPublishStatus());
        item.setGithubCommentId(entity.getGithubCommentId());
        item.setPublishErrorMessage(entity.getPublishErrorMessage());
        return item;
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
}
