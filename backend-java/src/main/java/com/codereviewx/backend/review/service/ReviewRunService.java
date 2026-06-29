package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.dto.CommentPreviewItemResponse;
import com.codereviewx.backend.review.dto.CommentPreviewListResponse;
import com.codereviewx.backend.review.dto.PublishCommentPreviewRequest;
import com.codereviewx.backend.review.dto.ReviewRunResponse;
import com.codereviewx.backend.review.dto.ToolTraceItemResponse;
import com.codereviewx.backend.review.dto.ToolTraceListResponse;
import com.codereviewx.backend.review.dto.UpdateCommentPreviewSelectionRequest;
import com.codereviewx.backend.review.exception.ReviewRunNotFoundException;
import com.codereviewx.backend.review.exception.ReviewRequestInvalidException;
import com.codereviewx.backend.review.persistence.entity.ReviewCommentPreviewEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewRunEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewCommentPreviewRepository;
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
    private final ReviewToolTraceRepository toolTraceRepository;
    private final ReviewCommentPreviewRepository commentPreviewRepository;
    private final CommentPreviewPublishService commentPreviewPublishService;
    private final ReviewRunResponseAssembler responseAssembler;

    public ReviewRunService(ReviewRunRepository reviewRunRepository,
                            ReviewToolTraceRepository toolTraceRepository,
                            ReviewCommentPreviewRepository commentPreviewRepository,
                            CommentPreviewPublishService commentPreviewPublishService,
                            ReviewRunResponseAssembler responseAssembler) {
        this.reviewRunRepository = reviewRunRepository;
        this.toolTraceRepository = toolTraceRepository;
        this.commentPreviewRepository = commentPreviewRepository;
        this.commentPreviewPublishService = commentPreviewPublishService;
        this.responseAssembler = responseAssembler;
    }

    @Transactional(readOnly = true)
    public ReviewRunResponse getRun(Long runId) {
        ReviewRunEntity run = reviewRunRepository.findById(runId)
                .orElseThrow(() -> new ReviewRunNotFoundException(runId));
        return responseAssembler.toRunResponse(run);
    }

    @Transactional(readOnly = true)
    public ToolTraceListResponse getTrace(Long runId) {
        requireRunExists(runId);
        List<ToolTraceItemResponse> items = toolTraceRepository
                .findByReviewRunIdOrderBySequenceNumberAsc(runId)
                .stream()
                .map(responseAssembler::toToolTraceItem)
                .collect(Collectors.toList());
        return new ToolTraceListResponse(items);
    }

    @Transactional(readOnly = true)
    public CommentPreviewListResponse getCommentPreviews(Long runId) {
        requireRunExists(runId);
        List<CommentPreviewItemResponse> items = commentPreviewRepository
                .findByReviewRunIdOrderByIdAsc(runId)
                .stream()
                .map(responseAssembler::toCommentPreviewItem)
                .collect(Collectors.toList());
        return new CommentPreviewListResponse(items);
    }

    @Transactional
    public CommentPreviewListResponse updateCommentPreviewSelection(Long runId,
                                                                    UpdateCommentPreviewSelectionRequest request) {
        requireSelectionRequest(request);
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
        return responseAssembler.toCommentPreviewList(previews);
    }

    private void requireSelectionRequest(UpdateCommentPreviewSelectionRequest request) {
        if (request == null || request.getSelectedPreviewIds() == null) {
            throw new ReviewRequestInvalidException("selectedPreviewIds is required");
        }
    }

    @Transactional
    public CommentPreviewItemResponse publishCommentPreview(Long runId,
                                                            Long previewId,
                                                            PublishCommentPreviewRequest request) {
        requirePublishConfirmation(request);
        requireRunExists(runId);
        return responseAssembler.toCommentPreviewItem(commentPreviewPublishService.publishOne(runId, previewId));
    }

    @Transactional
    public CommentPreviewListResponse publishSelectedCommentPreviews(Long runId,
                                                                     PublishCommentPreviewRequest request) {
        requirePublishConfirmation(request);
        requireRunExists(runId);
        return responseAssembler.toCommentPreviewList(commentPreviewPublishService.publishSelected(runId));
    }

    private void requirePublishConfirmation(PublishCommentPreviewRequest request) {
        if (request == null || !request.isConfirmed()) {
            throw new ReviewRequestInvalidException("confirmed must be true before publishing GitHub comments");
        }
    }

    private void requireRunExists(Long runId) {
        if (!reviewRunRepository.existsById(runId)) {
            throw new ReviewRunNotFoundException(runId);
        }
    }
}
