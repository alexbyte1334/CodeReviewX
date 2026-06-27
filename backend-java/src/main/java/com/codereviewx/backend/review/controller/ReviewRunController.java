package com.codereviewx.backend.review.controller;

import com.codereviewx.backend.common.ApiResponse;
import com.codereviewx.backend.review.dto.CommentPreviewItemResponse;
import com.codereviewx.backend.review.dto.CommentPreviewListResponse;
import com.codereviewx.backend.review.dto.PublishCommentPreviewRequest;
import com.codereviewx.backend.review.dto.ReviewRunResponse;
import com.codereviewx.backend.review.dto.ToolTraceListResponse;
import com.codereviewx.backend.review.dto.UpdateCommentPreviewSelectionRequest;
import com.codereviewx.backend.review.service.ReviewRunService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review-runs")
public class ReviewRunController {

    private final ReviewRunService reviewRunService;

    public ReviewRunController(ReviewRunService reviewRunService) {
        this.reviewRunService = reviewRunService;
    }

    @GetMapping("/{runId}")
    public ApiResponse<ReviewRunResponse> getRun(@PathVariable Long runId) {
        return ApiResponse.success(reviewRunService.getRun(runId));
    }

    @GetMapping("/{runId}/trace")
    public ApiResponse<ToolTraceListResponse> getTrace(@PathVariable Long runId) {
        return ApiResponse.success(reviewRunService.getTrace(runId));
    }

    @GetMapping("/{runId}/comment-previews")
    public ApiResponse<CommentPreviewListResponse> getCommentPreviews(@PathVariable Long runId) {
        return ApiResponse.success(reviewRunService.getCommentPreviews(runId));
    }

    @PatchMapping("/{runId}/comment-previews/selection")
    public ApiResponse<CommentPreviewListResponse> updateCommentPreviewSelection(
            @PathVariable Long runId,
            @Valid @RequestBody UpdateCommentPreviewSelectionRequest request) {
        return ApiResponse.success(reviewRunService.updateCommentPreviewSelection(runId, request));
    }

    @PostMapping("/{runId}/comment-previews/{previewId}/publish")
    public ApiResponse<CommentPreviewItemResponse> publishCommentPreview(
            @PathVariable Long runId,
            @PathVariable Long previewId,
            @Valid @RequestBody PublishCommentPreviewRequest request) {
        return ApiResponse.success(reviewRunService.publishCommentPreview(runId, previewId, request));
    }

    @PostMapping("/{runId}/comment-previews/publish-selected")
    public ApiResponse<CommentPreviewListResponse> publishSelectedCommentPreviews(
            @PathVariable Long runId,
            @Valid @RequestBody PublishCommentPreviewRequest request) {
        return ApiResponse.success(reviewRunService.publishSelectedCommentPreviews(runId, request));
    }
}
