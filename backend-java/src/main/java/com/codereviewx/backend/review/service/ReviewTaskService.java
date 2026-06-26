package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.dto.CreateReviewTaskRequest;
import com.codereviewx.backend.review.dto.IssueSummaryResponse;
import com.codereviewx.backend.review.dto.ReviewIssueResponse;
import com.codereviewx.backend.review.dto.ReviewTaskResponse;
import com.codereviewx.backend.review.enums.IssueSeverity;
import com.codereviewx.backend.review.enums.ReviewTaskStatus;
import com.codereviewx.backend.review.enums.RiskLevel;
import com.codereviewx.backend.review.exception.ReviewTaskNotFoundException;
import com.codereviewx.backend.review.persistence.entity.ReviewIssueEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewTaskEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewIssueRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewTaskRepository;
import com.codereviewx.backend.review.pipeline.ReviewContext;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import com.codereviewx.backend.review.pipeline.ReviewPipelineService;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReviewTaskService {

    private final ReviewTaskRepository reviewTaskRepository;
    private final ReviewIssueRepository reviewIssueRepository;
    private final ReviewPipelineService reviewPipelineService;

    public ReviewTaskService(ReviewTaskRepository reviewTaskRepository,
                             ReviewIssueRepository reviewIssueRepository,
                             ReviewPipelineService reviewPipelineService) {
        this.reviewTaskRepository = reviewTaskRepository;
        this.reviewIssueRepository = reviewIssueRepository;
        this.reviewPipelineService = reviewPipelineService;
    }

    /**
     * Creates and persists a ReviewTask, runs the review pipeline, and persists findings as issues.
     * Round 08: review generation routed through ReviewPipelineService -> MockReviewProvider.
     */
    @Transactional
    public ReviewTaskResponse createTask(CreateReviewTaskRequest request) {
        LocalDateTime now = LocalDateTime.now();
        String normalizedDiffText = normalizeDiffText(request.getDiffText());
        String normalizedProvider = normalizeProvider(request.getProvider());

        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setRepoUrl(request.getRepoUrl());
        task.setPrNumber(request.getPrNumber());
        task.setDiffText(normalizedDiffText);
        task.setStatus(ReviewTaskStatus.PENDING);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        task.setStatus(ReviewTaskStatus.RUNNING);
        ReviewTaskEntity savedTask = reviewTaskRepository.save(task);

        ReviewContext context = new ReviewContext(
                savedTask.getId(),
                savedTask.getRepoUrl(),
                savedTask.getPrNumber(),
                savedTask.getCreatedAt(),
                normalizedDiffText,
                normalizedProvider
        );
        ReviewProviderResult providerResult = reviewPipelineService.run(context);

        savedTask.setStatus(ReviewTaskStatus.SUCCESS);
        savedTask.setSummary(buildSummary(request.getPrNumber(), providerResult));
        savedTask.setRequestedProvider(providerResult.getRequestedProvider());
        savedTask.setProviderUsed(providerResult.getProviderUsed());
        savedTask.setProviderHit(providerResult.isProviderHit());
        savedTask.setErrorMessage(null);
        savedTask.setUpdatedAt(LocalDateTime.now());
        ReviewTaskEntity completedTask = reviewTaskRepository.save(savedTask);

        List<ReviewIssueEntity> issueEntities = providerResult.getFindings().stream()
                .map(finding -> toIssueEntity(finding, completedTask, now))
                .collect(Collectors.toList());
        reviewIssueRepository.saveAll(issueEntities);

        List<ReviewIssueEntity> persistedIssues = reviewIssueRepository.findByReviewTaskIdOrderByIdAsc(completedTask.getId());
        return toResponse(completedTask, persistedIssues);
    }

    private String normalizeDiffText(String diffText) {
        if (diffText == null) {
            return null;
        }
        String trimmed = diffText.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        return provider.trim().toLowerCase();
    }

    private String buildSummary(int prNumber, ReviewProviderResult providerResult) {
        if (providerResult.getFindings().isEmpty()) {
            return "Review completed for PR #" + prNumber + " with no findings from the available context.";
        }
        return "Review completed for PR #" + prNumber + " with generated findings.";
    }

    @Transactional(readOnly = true)
    public List<ReviewTaskResponse> listTasks() {
        return reviewTaskRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(task -> {
                    List<ReviewIssueEntity> issues = reviewIssueRepository.findByReviewTaskIdOrderByIdAsc(task.getId());
                    return toResponse(task, issues);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReviewTaskResponse getTask(Long id) {
        ReviewTaskEntity task = reviewTaskRepository.findById(id)
                .orElseThrow(() -> new ReviewTaskNotFoundException(id));
        List<ReviewIssueEntity> issues = reviewIssueRepository.findByReviewTaskIdOrderByIdAsc(id);
        return toResponse(task, issues);
    }

    private ReviewIssueEntity toIssueEntity(ReviewFinding finding, ReviewTaskEntity task, LocalDateTime now) {
        ReviewIssueEntity entity = new ReviewIssueEntity();
        entity.setReviewTask(task);
        entity.setIssueKey(finding.getIssueKey());
        entity.setSeverity(finding.getSeverity());
        entity.setCategory(finding.getCategory());
        entity.setSource(finding.getSource());
        entity.setStatus(finding.getStatus());
        entity.setFilePath(finding.getFilePath());
        entity.setStartLine(finding.getStartLine());
        entity.setEndLine(finding.getEndLine());
        entity.setTitle(finding.getTitle());
        entity.setDescription(finding.getDescription());
        entity.setRecommendation(finding.getRecommendation());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private IssueSummaryResponse buildIssueSummary(List<ReviewIssueResponse> issues) {
        int highCount = 0;
        int mediumCount = 0;
        int lowCount = 0;

        for (ReviewIssueResponse issue : issues) {
            if (issue.getSeverity() == IssueSeverity.HIGH) {
                highCount++;
            } else if (issue.getSeverity() == IssueSeverity.MEDIUM) {
                mediumCount++;
            } else if (issue.getSeverity() == IssueSeverity.LOW) {
                lowCount++;
            }
        }

        RiskLevel riskLevel;
        if (highCount > 0) {
            riskLevel = RiskLevel.HIGH;
        } else if (mediumCount > 0) {
            riskLevel = RiskLevel.MEDIUM;
        } else if (lowCount > 0) {
            riskLevel = RiskLevel.LOW;
        } else {
            riskLevel = RiskLevel.NONE;
        }

        return new IssueSummaryResponse(issues.size(), highCount, mediumCount, lowCount, riskLevel);
    }

    private ReviewIssueResponse toIssueResponse(ReviewIssueEntity entity) {
        ReviewIssueResponse response = new ReviewIssueResponse();
        response.setId(entity.getIssueKey());
        response.setSeverity(entity.getSeverity());
        response.setCategory(entity.getCategory());
        response.setSource(entity.getSource());
        response.setStatus(entity.getStatus());
        response.setFilePath(entity.getFilePath());
        response.setStartLine(entity.getStartLine());
        response.setEndLine(entity.getEndLine());
        response.setTitle(entity.getTitle());
        response.setDescription(entity.getDescription());
        response.setRecommendation(entity.getRecommendation());
        return response;
    }

    private ReviewTaskResponse toResponse(ReviewTaskEntity task, List<ReviewIssueEntity> issueEntities) {
        List<ReviewIssueResponse> issueResponses = issueEntities.stream()
                .map(this::toIssueResponse)
                .collect(Collectors.toList());

        IssueSummaryResponse issueSummary = buildIssueSummary(issueResponses);

        ReviewTaskResponse response = new ReviewTaskResponse();
        response.setId(task.getId());
        response.setRepoUrl(task.getRepoUrl());
        response.setPrNumber(task.getPrNumber());
        response.setStatus(task.getStatus());
        response.setSummary(task.getSummary());
        response.setErrorMessage(task.getErrorMessage());
        response.setCreatedAt(task.getCreatedAt());
        response.setUpdatedAt(task.getUpdatedAt());
        response.setIssues(issueResponses);
        response.setIssueSummary(issueSummary);
        response.setRiskLevel(issueSummary.getRiskLevel());
        response.setRequestedProvider(task.getRequestedProvider());
        response.setProviderUsed(task.getProviderUsed());
        response.setProviderHit(task.getProviderHit());
        return response;
    }
}
