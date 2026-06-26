package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.ReviewErrorCodes;
import com.codereviewx.backend.review.dto.CreateReviewTaskRequest;
import com.codereviewx.backend.review.dto.IngestionSummaryResponse;
import com.codereviewx.backend.review.dto.IssueSummaryResponse;
import com.codereviewx.backend.review.dto.ReviewIssueResponse;
import com.codereviewx.backend.review.dto.ReviewTaskResponse;
import com.codereviewx.backend.review.dto.TraceSummaryResponse;
import com.codereviewx.backend.review.enums.IssueSeverity;
import com.codereviewx.backend.review.enums.PublishStatus;
import com.codereviewx.backend.review.enums.ReviewMode;
import com.codereviewx.backend.review.enums.ReviewRunStatus;
import com.codereviewx.backend.review.enums.ReviewTaskStatus;
import com.codereviewx.backend.review.enums.RiskLevel;
import com.codereviewx.backend.review.enums.ToolTraceStatus;
import com.codereviewx.backend.review.exception.ReviewTaskNotFoundException;
import com.codereviewx.backend.review.github.GithubPrMetadata;
import com.codereviewx.backend.review.github.GithubPrMetadataLoadResult;
import com.codereviewx.backend.review.github.GithubPrMetadataLoader;
import com.codereviewx.backend.review.github.GithubProperties;
import com.codereviewx.backend.review.persistence.entity.ReviewCommentPreviewEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewInputSnapshotEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewIssueEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewProviderTraceEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewRunEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewTaskEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewToolTraceEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewCommentPreviewRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewInputSnapshotRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewIssueRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewProviderTraceRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewRunRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewTaskRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewToolTraceRepository;
import com.codereviewx.backend.review.pipeline.ReviewContext;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import com.codereviewx.backend.review.pipeline.ReviewPipelineService;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import com.codereviewx.backend.review.pipeline.provider.mimo.MiMoAgentException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReviewTaskService {

    private final ReviewTaskRepository reviewTaskRepository;
    private final ReviewIssueRepository reviewIssueRepository;
    private final ReviewRunRepository reviewRunRepository;
    private final ReviewPipelineService reviewPipelineService;
    private final ReviewInputSnapshotRepository inputSnapshotRepository;
    private final ReviewToolTraceRepository toolTraceRepository;
    private final ReviewProviderTraceRepository providerTraceRepository;
    private final ReviewCommentPreviewRepository commentPreviewRepository;
    private final GithubPrMetadataLoader githubPrMetadataLoader;
    private final GithubProperties githubProperties;
    private final ObjectMapper objectMapper;

    public ReviewTaskService(ReviewTaskRepository reviewTaskRepository,
                             ReviewIssueRepository reviewIssueRepository,
                             ReviewRunRepository reviewRunRepository,
                             ReviewPipelineService reviewPipelineService,
                             ReviewInputSnapshotRepository inputSnapshotRepository,
                             ReviewToolTraceRepository toolTraceRepository,
                             ReviewProviderTraceRepository providerTraceRepository,
                             ReviewCommentPreviewRepository commentPreviewRepository,
                             GithubPrMetadataLoader githubPrMetadataLoader,
                             GithubProperties githubProperties,
                             ObjectMapper objectMapper) {
        this.reviewTaskRepository = reviewTaskRepository;
        this.reviewIssueRepository = reviewIssueRepository;
        this.reviewRunRepository = reviewRunRepository;
        this.reviewPipelineService = reviewPipelineService;
        this.inputSnapshotRepository = inputSnapshotRepository;
        this.toolTraceRepository = toolTraceRepository;
        this.providerTraceRepository = providerTraceRepository;
        this.commentPreviewRepository = commentPreviewRepository;
        this.githubPrMetadataLoader = githubPrMetadataLoader;
        this.githubProperties = githubProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates review_task and review_run, then executes MANUAL_DIFF provider path or bounded GITHUB_PR ingestion.
     */
    @Transactional
    public ReviewTaskResponse createTask(CreateReviewTaskRequest request) {
        LocalDateTime now = LocalDateTime.now();
        String normalizedDiffText = normalizeDiffText(request.getDiffText());
        String normalizedProvider = normalizeProvider(request.getProvider());
        ReviewMode reviewMode = resolveReviewMode(request, normalizedDiffText);

        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setRepoUrl(request.getRepoUrl());
        task.setPrNumber(request.getPrNumber());
        task.setDiffText(normalizedDiffText);
        task.setReviewMode(reviewMode);
        task.setStatus(ReviewTaskStatus.PENDING);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        ReviewTaskEntity savedTask = reviewTaskRepository.save(task);

        ReviewRunEntity run = newInitialRun(savedTask.getId(), reviewMode, now);
        ReviewRunEntity savedRun = reviewRunRepository.save(run);

        savedTask.setLatestRunId(savedRun.getId());
        savedTask.setStatus(ReviewTaskStatus.RUNNING);
        savedTask = reviewTaskRepository.save(savedTask);

        if (reviewMode == ReviewMode.GITHUB_PR) {
            return completeGithubPrIngestion(savedTask, savedRun, normalizedProvider);
        }

        return completeProviderReview(savedTask, savedRun, normalizedDiffText, normalizedProvider);
    }

    private ReviewRunEntity newInitialRun(Long taskId, ReviewMode reviewMode, LocalDateTime now) {
        ReviewRunEntity run = new ReviewRunEntity();
        run.setReviewTaskId(taskId);
        run.setRunNumber(1);
        run.setReviewMode(reviewMode);
        run.setStatus(ReviewRunStatus.PENDING);
        run.setStartedAt(now);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        return run;
    }

    private ReviewTaskResponse completeGithubPrIngestion(ReviewTaskEntity task,
                                                         ReviewRunEntity run,
                                                         String normalizedProvider) {
        LocalDateTime ingestionStartedAt = LocalDateTime.now();
        run.setStatus(ReviewRunStatus.INGESTING);
        run.setUpdatedAt(ingestionStartedAt);
        reviewRunRepository.save(run);

        GithubPrMetadataLoadResult result = githubPrMetadataLoader.load(task.getRepoUrl(), task.getPrNumber());
        LocalDateTime ingestionFinishedAt = LocalDateTime.now();
        persistMetadataToolTrace(run.getId(), task, result, ingestionStartedAt, ingestionFinishedAt);

        if (!result.isSuccess()) {
            return completeFailedGithubPrIngestion(task, run, result.getErrorCode(), result.getErrorMessage(),
                    ingestionFinishedAt);
        }

        persistInputSnapshot(run.getId(), task, result.getMetadata(), ingestionFinishedAt);
        return completeProviderReview(task, run, null, normalizedProvider);
    }

    private ReviewTaskResponse completeFailedGithubPrIngestion(ReviewTaskEntity task,
                                                               ReviewRunEntity run,
                                                               String errorCode,
                                                               String errorMessage,
                                                               LocalDateTime now) {
        run.setStatus(ReviewRunStatus.FAILED);
        run.setErrorCode(errorCode);
        run.setErrorMessage(errorMessage);
        run.setFinishedAt(now);
        run.setUpdatedAt(now);
        reviewRunRepository.save(run);

        task.setStatus(ReviewTaskStatus.FAILED);
        task.setErrorMessage(errorMessage);
        task.setSummary(null);
        task.setRequestedProvider(null);
        task.setProviderUsed(null);
        task.setProviderHit(null);
        task.setUpdatedAt(now);
        ReviewTaskEntity completedTask = reviewTaskRepository.save(task);

        return toResponse(completedTask, Collections.emptyList());
    }

    private void persistMetadataToolTrace(Long runId,
                                          ReviewTaskEntity task,
                                          GithubPrMetadataLoadResult result,
                                          LocalDateTime startedAt,
                                          LocalDateTime finishedAt) {
        ReviewToolTraceEntity trace = new ReviewToolTraceEntity();
        trace.setReviewRunId(runId);
        trace.setSequenceNumber(1);
        trace.setToolName(GithubPrMetadataLoader.TOOL_NAME);
        trace.setStatus(result.isSuccess() ? ToolTraceStatus.SUCCESS : ToolTraceStatus.FAILED);
        trace.setInputSummary("repoUrl=" + task.getRepoUrl()
                + ", prNumber=" + task.getPrNumber()
                + ", tokenConfigured=" + githubProperties.hasToken());
        trace.setOutputSummary(result.isSuccess()
                ? "Loaded PR title, head/base refs, and changed file counts."
                : result.getErrorMessage());
        trace.setErrorCode(result.getErrorCode());
        trace.setErrorMessage(result.getErrorMessage());
        trace.setStartedAt(startedAt);
        trace.setFinishedAt(finishedAt);
        trace.setDurationMs(ChronoUnit.MILLIS.between(startedAt, finishedAt));
        trace.setCreatedAt(startedAt);
        toolTraceRepository.save(trace);
    }

    private void persistInputSnapshot(Long runId,
                                      ReviewTaskEntity task,
                                      GithubPrMetadata metadata,
                                      LocalDateTime now) {
        ReviewInputSnapshotEntity snapshot = new ReviewInputSnapshotEntity();
        snapshot.setReviewRunId(runId);
        snapshot.setRepoUrl(task.getRepoUrl());
        snapshot.setOwner(metadata.owner());
        snapshot.setRepo(metadata.repo());
        snapshot.setPrNumber(metadata.prNumber());
        snapshot.setBaseRef(metadata.baseRef());
        snapshot.setHeadRef(metadata.headRef());
        snapshot.setBaseSha(metadata.baseSha());
        snapshot.setHeadSha(metadata.headSha());
        snapshot.setPrTitle(metadata.title());
        snapshot.setPrAuthor(metadata.authorLogin());
        snapshot.setChangedFiles(metadata.changedFiles());
        snapshot.setAdditions(metadata.additions());
        snapshot.setDeletions(metadata.deletions());
        snapshot.setDiffTruncated(false);
        snapshot.setContextTruncated(false);
        snapshot.setSnapshotJson(toSnapshotJson(metadata));
        snapshot.setCreatedAt(now);
        inputSnapshotRepository.save(snapshot);
    }

    private String toSnapshotJson(GithubPrMetadata metadata) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("owner", metadata.owner());
        snapshot.put("repo", metadata.repo());
        snapshot.put("prNumber", metadata.prNumber());
        snapshot.put("title", metadata.title());
        snapshot.put("authorLogin", metadata.authorLogin());
        snapshot.put("baseRef", metadata.baseRef());
        snapshot.put("headRef", metadata.headRef());
        snapshot.put("baseSha", metadata.baseSha());
        snapshot.put("headSha", metadata.headSha());
        snapshot.put("state", metadata.state());
        snapshot.put("createdAt", metadata.createdAt());
        snapshot.put("updatedAt", metadata.updatedAt());
        snapshot.put("changedFiles", metadata.changedFiles());
        snapshot.put("additions", metadata.additions());
        snapshot.put("deletions", metadata.deletions());
        snapshot.put("diffTruncated", false);
        snapshot.put("contextTruncated", false);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            return "{\"metadata\":\"sanitized\"}";
        }
    }

    private ReviewTaskResponse completeProviderReview(ReviewTaskEntity task,
                                                      ReviewRunEntity run,
                                                      String normalizedDiffText,
                                                      String normalizedProvider) {
        LocalDateTime reviewStartedAt = LocalDateTime.now();
        run.setStatus(ReviewRunStatus.REVIEWING);
        run.setUpdatedAt(reviewStartedAt);
        reviewRunRepository.save(run);

        ReviewContext context = new ReviewContext(
                task.getId(),
                task.getRepoUrl(),
                task.getPrNumber(),
                task.getCreatedAt(),
                normalizedDiffText,
                normalizedProvider
        );
        ReviewProviderResult providerResult;
        try {
            providerResult = reviewPipelineService.run(context);
        } catch (MiMoAgentException ex) {
            LocalDateTime failedAt = LocalDateTime.now();
            return completeFailedProviderReview(task, run, ex.getErrorCode(), ex.getMessage(), failedAt);
        }
        LocalDateTime reviewFinishedAt = LocalDateTime.now();
        persistProviderTrace(run.getId(), task, providerResult, reviewStartedAt, reviewFinishedAt);

        run.setRequestedProvider(providerResult.getRequestedProvider());
        run.setProviderUsed(providerResult.getProviderUsed());
        run.setProviderHit(providerResult.isProviderHit());
        run.setUpdatedAt(reviewFinishedAt);
        reviewRunRepository.save(run);

        task.setStatus(ReviewTaskStatus.SUCCESS);
        task.setSummary(buildSummary(task.getPrNumber(), providerResult));
        task.setRequestedProvider(providerResult.getRequestedProvider());
        task.setProviderUsed(providerResult.getProviderUsed());
        task.setProviderHit(providerResult.isProviderHit());
        task.setErrorMessage(null);
        task.setUpdatedAt(reviewFinishedAt);
        ReviewTaskEntity completedTask = reviewTaskRepository.save(task);

        Long runId = run.getId();
        List<ReviewIssueEntity> issueEntities = providerResult.getFindings().stream()
                .map(finding -> toIssueEntity(finding, completedTask, runId, reviewFinishedAt))
                .collect(Collectors.toList());
        reviewIssueRepository.saveAll(issueEntities);

        LocalDateTime previewStartedAt = LocalDateTime.now();
        run.setStatus(ReviewRunStatus.BUILDING_PREVIEW);
        run.setUpdatedAt(previewStartedAt);
        reviewRunRepository.save(run);

        List<ReviewIssueEntity> persistedIssues =
                reviewIssueRepository.findByReviewTaskIdOrderByIdAsc(completedTask.getId());
        persistCommentPreviews(runId, persistedIssues, previewStartedAt);

        LocalDateTime completedAt = LocalDateTime.now();
        run.setStatus(ReviewRunStatus.SUCCESS);
        run.setFinishedAt(completedAt);
        run.setUpdatedAt(completedAt);
        reviewRunRepository.save(run);

        return toResponse(completedTask, persistedIssues);
    }

    private ReviewTaskResponse completeFailedProviderReview(ReviewTaskEntity task,
                                                            ReviewRunEntity run,
                                                            String errorCode,
                                                            String errorMessage,
                                                            LocalDateTime now) {
        run.setStatus(ReviewRunStatus.FAILED);
        run.setRequestedProvider("mimo");
        run.setProviderUsed(null);
        run.setProviderHit(false);
        run.setErrorCode(errorCode);
        run.setErrorMessage(errorMessage);
        run.setFinishedAt(now);
        run.setUpdatedAt(now);
        reviewRunRepository.save(run);

        task.setStatus(ReviewTaskStatus.FAILED);
        task.setSummary(null);
        task.setRequestedProvider("mimo");
        task.setProviderUsed(null);
        task.setProviderHit(false);
        task.setErrorMessage(errorMessage);
        task.setUpdatedAt(now);
        ReviewTaskEntity failedTask = reviewTaskRepository.save(task);

        return toResponse(failedTask, Collections.emptyList());
    }

    private void persistProviderTrace(Long runId,
                                      ReviewTaskEntity task,
                                      ReviewProviderResult providerResult,
                                      LocalDateTime startedAt,
                                      LocalDateTime finishedAt) {
        ReviewProviderTraceEntity trace = new ReviewProviderTraceEntity();
        trace.setReviewRunId(runId);
        trace.setRequestedProvider(providerResult.getRequestedProvider());
        trace.setProviderUsed(providerResult.getProviderUsed());
        trace.setProviderHit(providerResult.isProviderHit());
        trace.setInputSummary("repoUrl=" + task.getRepoUrl()
                + ", prNumber=" + task.getPrNumber()
                + ", reviewMode=" + task.getReviewMode());
        trace.setOutputSummary(providerResult.getFindings().size()
                + " findings normalized from provider response.");
        trace.setFindingCount(providerResult.getFindings().size());
        trace.setNormalizationSummary("Approved MiMo candidate review mapped by IssueGenerator.");
        if (!providerResult.isProviderHit()) {
            trace.setFallbackReason("Requested provider fell back to "
                    + providerResult.getProviderUsed() + " provider.");
        }
        trace.setStartedAt(startedAt);
        trace.setFinishedAt(finishedAt);
        trace.setDurationMs(ChronoUnit.MILLIS.between(startedAt, finishedAt));
        trace.setCreatedAt(startedAt);
        providerTraceRepository.save(trace);
    }

    private void persistCommentPreviews(Long runId,
                                        List<ReviewIssueEntity> issues,
                                        LocalDateTime now) {
        List<ReviewCommentPreviewEntity> previews = issues.stream()
                .map(issue -> toCommentPreview(runId, issue, now))
                .collect(Collectors.toList());
        commentPreviewRepository.saveAll(previews);
    }

    private ReviewCommentPreviewEntity toCommentPreview(Long runId,
                                                        ReviewIssueEntity issue,
                                                        LocalDateTime now) {
        ReviewCommentPreviewEntity preview = new ReviewCommentPreviewEntity();
        preview.setReviewRunId(runId);
        preview.setReviewIssueId(issue.getId());
        preview.setIssueKey(issue.getIssueKey());
        preview.setFilePath(issue.getFilePath());
        preview.setLineNumber(issue.getStartLine());
        preview.setSide("RIGHT");
        preview.setDraftBody(buildDraftComment(issue));
        preview.setSeverity(issue.getSeverity().name());
        preview.setCategory(issue.getCategory().name());
        preview.setSource(issue.getSource().name());
        preview.setSelectedForPublish(false);
        preview.setPublishStatus(PublishStatus.NOT_PUBLISHED);
        preview.setCreatedAt(now);
        preview.setUpdatedAt(now);
        return preview;
    }

    private String buildDraftComment(ReviewIssueEntity issue) {
        return "Severity: " + issue.getSeverity()
                + "\nCategory: " + issue.getCategory()
                + "\n\n" + issue.getTitle()
                + "\n\n" + issue.getDescription()
                + "\n\nSuggestion: " + issue.getRecommendation();
    }

    private String normalizeDiffText(String diffText) {
        if (diffText == null) {
            return null;
        }
        String trimmed = diffText.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeProvider(String provider) {
        return "mimo";
    }

    private ReviewMode resolveReviewMode(CreateReviewTaskRequest request, String normalizedDiffText) {
        if (request.getReviewMode() != null) {
            return request.getReviewMode();
        }
        return normalizedDiffText != null ? ReviewMode.MANUAL_DIFF : ReviewMode.GITHUB_PR;
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

    private ReviewIssueEntity toIssueEntity(ReviewFinding finding,
                                            ReviewTaskEntity task,
                                            Long reviewRunId,
                                            LocalDateTime now) {
        ReviewIssueEntity entity = new ReviewIssueEntity();
        entity.setReviewTask(task);
        entity.setReviewRunId(reviewRunId);
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
        response.setLatestRunId(task.getLatestRunId());
        response.setReviewMode(task.getReviewMode());
        populateRunErrorCode(response, task);
        populateStage2Summaries(response, task);
        return response;
    }

    private void populateRunErrorCode(ReviewTaskResponse response, ReviewTaskEntity task) {
        Long latestRunId = task.getLatestRunId();
        if (latestRunId == null) {
            return;
        }
        reviewRunRepository.findById(latestRunId).ifPresent(run -> response.setErrorCode(run.getErrorCode()));
    }

    private void populateStage2Summaries(ReviewTaskResponse response, ReviewTaskEntity task) {
        Long latestRunId = task.getLatestRunId();
        if (latestRunId == null) {
            response.setCommentPreviewCount(0);
            return;
        }

        response.setCommentPreviewCount(commentPreviewRepository.countByReviewRunId(latestRunId));

        inputSnapshotRepository.findByReviewRunId(latestRunId).ifPresent(snapshot -> {
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
        });

        int toolCount = toolTraceRepository.countByReviewRunId(latestRunId);
        int failedToolCount = toolTraceRepository.countByReviewRunIdAndStatus(latestRunId, ToolTraceStatus.FAILED);
        boolean providerFallback = providerTraceRepository.findByReviewRunId(latestRunId)
                .map(trace -> trace.getProviderHit() != null && !trace.getProviderHit())
                .orElse(false);
        response.setTraceSummary(new TraceSummaryResponse(toolCount, failedToolCount, providerFallback));
    }
}
