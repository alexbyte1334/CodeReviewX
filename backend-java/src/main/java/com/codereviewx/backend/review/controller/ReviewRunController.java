package com.codereviewx.backend.review.controller;

import com.codereviewx.backend.common.ApiResponse;
import com.codereviewx.backend.review.dto.CommentPreviewListResponse;
import com.codereviewx.backend.review.dto.ReviewRunResponse;
import com.codereviewx.backend.review.dto.ToolTraceListResponse;
import com.codereviewx.backend.review.service.ReviewRunService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
