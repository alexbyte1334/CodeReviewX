package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.dto.IngestionSummaryResponse;
import com.codereviewx.backend.review.dto.IssueSummaryResponse;
import com.codereviewx.backend.review.dto.ReviewIssueResponse;
import com.codereviewx.backend.review.dto.ReviewTaskResponse;
import com.codereviewx.backend.review.dto.TraceSummaryResponse;
import com.codereviewx.backend.review.enums.IssueSeverity;
import com.codereviewx.backend.review.enums.RiskLevel;
import com.codereviewx.backend.review.enums.ToolTraceStatus;
import com.codereviewx.backend.review.persistence.entity.ReviewIssueEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewInputSnapshotEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewProviderTraceEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewRunEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewTaskEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewCommentPreviewRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewInputSnapshotRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewProviderTraceRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewRunRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewToolTraceRepository;
import com.codereviewx.backend.review.persistence.repository.RunCountProjection;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ReviewTaskResponseAssembler {

    private final ReviewRunRepository reviewRunRepository;
    private final ReviewInputSnapshotRepository inputSnapshotRepository;
    private final ReviewToolTraceRepository toolTraceRepository;
    private final ReviewProviderTraceRepository providerTraceRepository;
    private final ReviewCommentPreviewRepository commentPreviewRepository;

    public ReviewTaskResponseAssembler(ReviewRunRepository reviewRunRepository,
                                       ReviewInputSnapshotRepository inputSnapshotRepository,
                                       ReviewToolTraceRepository toolTraceRepository,
                                       ReviewProviderTraceRepository providerTraceRepository,
                                       ReviewCommentPreviewRepository commentPreviewRepository) {
        this.reviewRunRepository = reviewRunRepository;
        this.inputSnapshotRepository = inputSnapshotRepository;
        this.toolTraceRepository = toolTraceRepository;
        this.providerTraceRepository = providerTraceRepository;
        this.commentPreviewRepository = commentPreviewRepository;
    }

    public ReviewTaskResponse toResponse(ReviewTaskEntity task, List<ReviewIssueEntity> issueEntities) {
        return toResponse(task, issueEntities, loadStage2SummaryContext(Collections.singletonList(task)));
    }

    public List<ReviewTaskResponse> toResponses(List<ReviewTaskEntity> tasks,
                                                Map<Long, List<ReviewIssueEntity>> issuesByTaskId) {
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }
        Stage2SummaryContext context = loadStage2SummaryContext(tasks);
        return tasks.stream()
                .map(task -> toResponse(
                        task,
                        issuesByTaskId.getOrDefault(task.getId(), Collections.emptyList()),
                        context))
                .collect(Collectors.toList());
    }

    private ReviewTaskResponse toResponse(ReviewTaskEntity task,
                                          List<ReviewIssueEntity> issueEntities,
                                          Stage2SummaryContext context) {
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
        response.setLatestRunId(task.getLatestRunId());
        response.setReviewMode(task.getReviewMode());
        populateRunErrorCode(response, task, context);
        populateStage2Summaries(response, task, context);
        return response;
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

    private void populateRunErrorCode(ReviewTaskResponse response,
                                      ReviewTaskEntity task,
                                      Stage2SummaryContext context) {
        Long latestRunId = task.getLatestRunId();
        if (latestRunId == null) {
            return;
        }
        ReviewRunEntity run = context.runsById.get(latestRunId);
        if (run != null) {
            response.setErrorCode(run.getErrorCode());
        }
    }

    private void populateStage2Summaries(ReviewTaskResponse response,
                                         ReviewTaskEntity task,
                                         Stage2SummaryContext context) {
        Long latestRunId = task.getLatestRunId();
        if (latestRunId == null) {
            response.setCommentPreviewCount(0);
            return;
        }

        response.setCommentPreviewCount(toInt(context.commentPreviewCountsByRunId.get(latestRunId)));

        ReviewInputSnapshotEntity snapshot = context.snapshotsByRunId.get(latestRunId);
        if (snapshot != null) {
            IngestionSummaryResponse ingestion = new IngestionSummaryResponse(
                    snapshot.getHeadSha(),
                    snapshot.getBaseSha(),
                    snapshot.getChangedFiles(),
                    snapshot.getAdditions(),
                    snapshot.getDeletions(),
                    Boolean.TRUE.equals(snapshot.getDiffTruncated())
                            || Boolean.TRUE.equals(snapshot.getContextTruncated())
            );
            response.setIngestionSummary(ingestion);
        }

        int toolCount = toInt(context.toolCountsByRunId.get(latestRunId));
        int failedToolCount = toInt(context.failedToolCountsByRunId.get(latestRunId));
        ReviewProviderTraceEntity providerTrace = context.providerTracesByRunId.get(latestRunId);
        boolean providerFallback = providerTrace != null
                && providerTrace.getProviderHit() != null
                && !providerTrace.getProviderHit();
        response.setTraceSummary(new TraceSummaryResponse(toolCount, failedToolCount, providerFallback));
    }

    private Stage2SummaryContext loadStage2SummaryContext(List<ReviewTaskEntity> tasks) {
        List<Long> runIds = tasks.stream()
                .map(ReviewTaskEntity::getLatestRunId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (runIds.isEmpty()) {
            return Stage2SummaryContext.empty();
        }

        Map<Long, ReviewRunEntity> runsById = reviewRunRepository.findAllById(runIds)
                .stream()
                .collect(Collectors.toMap(ReviewRunEntity::getId, Function.identity()));
        Map<Long, ReviewInputSnapshotEntity> snapshotsByRunId = inputSnapshotRepository.findByReviewRunIdIn(runIds)
                .stream()
                .collect(Collectors.toMap(ReviewInputSnapshotEntity::getReviewRunId, Function.identity()));
        Map<Long, ReviewProviderTraceEntity> providerTracesByRunId = providerTraceRepository.findByReviewRunIdIn(runIds)
                .stream()
                .collect(Collectors.toMap(ReviewProviderTraceEntity::getReviewRunId, Function.identity()));
        Map<Long, Long> commentPreviewCountsByRunId =
                toCountMap(commentPreviewRepository.countByReviewRunIds(runIds));
        Map<Long, Long> toolCountsByRunId = toCountMap(toolTraceRepository.countByReviewRunIds(runIds));
        Map<Long, Long> failedToolCountsByRunId = toCountMap(
                toolTraceRepository.countByReviewRunIdsAndStatus(runIds, ToolTraceStatus.FAILED));
        return new Stage2SummaryContext(
                runsById,
                snapshotsByRunId,
                providerTracesByRunId,
                commentPreviewCountsByRunId,
                toolCountsByRunId,
                failedToolCountsByRunId
        );
    }

    private Map<Long, Long> toCountMap(List<RunCountProjection> counts) {
        return counts.stream()
                .collect(Collectors.toMap(RunCountProjection::getReviewRunId, RunCountProjection::getItemCount));
    }

    private int toInt(Long value) {
        if (value == null) {
            return 0;
        }
        return Math.toIntExact(value);
    }

    private record Stage2SummaryContext(
            Map<Long, ReviewRunEntity> runsById,
            Map<Long, ReviewInputSnapshotEntity> snapshotsByRunId,
            Map<Long, ReviewProviderTraceEntity> providerTracesByRunId,
            Map<Long, Long> commentPreviewCountsByRunId,
            Map<Long, Long> toolCountsByRunId,
            Map<Long, Long> failedToolCountsByRunId
    ) {
        private static Stage2SummaryContext empty() {
            return new Stage2SummaryContext(
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap()
            );
        }
    }
}
