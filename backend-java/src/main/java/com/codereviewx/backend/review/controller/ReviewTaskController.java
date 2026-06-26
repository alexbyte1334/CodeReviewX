package com.codereviewx.backend.review.controller;

import com.codereviewx.backend.common.ApiResponse;
import com.codereviewx.backend.review.dto.CreateReviewTaskRequest;
import com.codereviewx.backend.review.dto.ReviewTaskResponse;
import com.codereviewx.backend.review.service.ReviewTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/review-tasks")
public class ReviewTaskController {

    private final ReviewTaskService reviewTaskService;

    public ReviewTaskController(ReviewTaskService reviewTaskService) {
        this.reviewTaskService = reviewTaskService;
    }

    @PostMapping
    public ApiResponse<ReviewTaskResponse> createTask(@Valid @RequestBody CreateReviewTaskRequest request) {
        ReviewTaskResponse response = reviewTaskService.createTask(request);
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<List<ReviewTaskResponse>> listTasks() {
        return ApiResponse.success(reviewTaskService.listTasks());
    }

    @GetMapping("/{id}")
    public ApiResponse<ReviewTaskResponse> getTask(@PathVariable Long id) {
        return ApiResponse.success(reviewTaskService.getTask(id));
    }
}
