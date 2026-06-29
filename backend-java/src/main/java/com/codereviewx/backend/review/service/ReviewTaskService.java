package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.dto.CreateReviewTaskRequest;
import com.codereviewx.backend.review.dto.ReviewTaskResponse;
import com.codereviewx.backend.review.enums.ReviewMode;
import com.codereviewx.backend.review.enums.ReviewRunStatus;
import com.codereviewx.backend.review.enums.ReviewTaskStatus;
import com.codereviewx.backend.review.enums.ToolTraceStatus;
import com.codereviewx.backend.review.exception.ReviewRequestInvalidException;
import com.codereviewx.backend.review.exception.ReviewTaskNotFoundException;
import com.codereviewx.backend.review.github.GithubPrDiff;
import com.codereviewx.backend.review.github.GithubPrMetadataLoadResult;
import com.codereviewx.backend.review.github.GithubPrDiffLoadResult;
import com.codereviewx.backend.review.github.GithubPrDiffLoader;
import com.codereviewx.backend.review.github.GithubPrMetadataLoader;
import com.codereviewx.backend.review.persistence.entity.ReviewIssueEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewRunEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewTaskEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewIssueRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewRunRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewTaskRepository;
import com.codereviewx.backend.review.pipeline.ReviewContext;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import com.codereviewx.backend.review.pipeline.ReviewPipelineService;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import com.codereviewx.backend.review.pipeline.provider.mimo.MiMoAgentException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReviewTaskService {

    private final ReviewTaskRepository reviewTaskRepository;
    private final ReviewIssueRepository reviewIssueRepository;
    private final ReviewRunRepository reviewRunRepository;
    private final ReviewPipelineService reviewPipelineService;
    private final GithubPrMetadataLoader githubPrMetadataLoader;
    private final GithubPrDiffLoader githubPrDiffLoader;
    private final ReviewTraceRecorder reviewTraceRecorder;
    private final ReviewInputSnapshotService reviewInputSnapshotService;
    private final CommentPreviewBuilder commentPreviewBuilder;
    private final ReviewTaskResponseAssembler responseAssembler;
    private final RepositoryContextIndexService repositoryContextIndexService;
    private final ReviewStaticAnalysisService staticAnalysisService;

    public ReviewTaskService(ReviewTaskRepository reviewTaskRepository,
                             ReviewIssueRepository reviewIssueRepository,
                             ReviewRunRepository reviewRunRepository,
                             ReviewPipelineService reviewPipelineService,
                             GithubPrMetadataLoader githubPrMetadataLoader,
                             GithubPrDiffLoader githubPrDiffLoader,
                             ReviewTraceRecorder reviewTraceRecorder,
                             ReviewInputSnapshotService reviewInputSnapshotService,
                             CommentPreviewBuilder commentPreviewBuilder,
                             ReviewTaskResponseAssembler responseAssembler,
                             RepositoryContextIndexService repositoryContextIndexService,
                             ReviewStaticAnalysisService staticAnalysisService) {
        this.reviewTaskRepository = reviewTaskRepository;
        this.reviewIssueRepository = reviewIssueRepository;
        this.reviewRunRepository = reviewRunRepository;
        this.reviewPipelineService = reviewPipelineService;
        this.githubPrMetadataLoader = githubPrMetadataLoader;
        this.githubPrDiffLoader = githubPrDiffLoader;
        this.reviewTraceRecorder = reviewTraceRecorder;
        this.reviewInputSnapshotService = reviewInputSnapshotService;
        this.commentPreviewBuilder = commentPreviewBuilder;
        this.responseAssembler = responseAssembler;
        this.repositoryContextIndexService = repositoryContextIndexService;
        this.staticAnalysisService = staticAnalysisService;
    }

    /**
     * Creates review_task and review_run, then executes MANUAL_DIFF provider path or bounded GITHUB_PR ingestion.
     */
    @Transactional
    public ReviewTaskResponse createTask(CreateReviewTaskRequest request) {
        validateCreateRequest(request);
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

        List<ReviewFinding> staticFindings = staticAnalysisService.analyze(
                new GithubPrDiff(normalizedDiffText, null, null, false, Collections.emptyList()),
                RepositoryContextIndexResult.empty()
        );
        return completeProviderReview(savedTask, savedRun, normalizedDiffText, normalizedProvider, staticFindings);
    }

    private void validateCreateRequest(CreateReviewTaskRequest request) {
        if (request == null) {
            throw new ReviewRequestInvalidException("request is required");
        }
        if (request.getRepoUrl() == null || request.getRepoUrl().trim().isEmpty()) {
            throw new ReviewRequestInvalidException("repoUrl is required");
        }
        if (request.getPrNumber() == null || request.getPrNumber() <= 0) {
            throw new ReviewRequestInvalidException("prNumber must be positive");
        }
        if (request.getDiffText() != null
                && request.getDiffText().length() > CreateReviewTaskRequest.MAX_DIFF_TEXT_LENGTH) {
            throw new ReviewRequestInvalidException(
                    "diffText is too large. Maximum length is "
                            + CreateReviewTaskRequest.MAX_DIFF_TEXT_LENGTH + " characters.");
        }
        if (request.getProvider() != null
                && !request.getProvider().trim().isEmpty()
                && !"mimo".equalsIgnoreCase(request.getProvider().trim())) {
            throw new ReviewRequestInvalidException("provider must be mimo");
        }
        if (request.getReviewMode() == ReviewMode.MANUAL_DIFF
                && normalizeDiffText(request.getDiffText()) == null) {
            throw new ReviewRequestInvalidException("MANUAL_DIFF requires non-blank diffText");
        }
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
        reviewTraceRecorder.recordMetadataLoad(run.getId(), task, result, ingestionStartedAt, ingestionFinishedAt);

        if (!result.isSuccess()) {
            return completeFailedGithubPrIngestion(task, run, result.getErrorCode(), result.getErrorMessage(),
                    ingestionFinishedAt);
        }

        GithubPrDiffLoadResult diffResult = githubPrDiffLoader.load(result.getMetadata());
        LocalDateTime diffFinishedAt = LocalDateTime.now();
        reviewTraceRecorder.recordDiffLoad(run.getId(), task, diffResult, ingestionFinishedAt, diffFinishedAt);

        if (!diffResult.isSuccess()) {
            return completeFailedGithubPrIngestion(task, run, diffResult.getErrorCode(), diffResult.getErrorMessage(),
                    diffFinishedAt);
        }

        reviewInputSnapshotService.persistGithubPrSnapshot(
                run.getId(), task, result.getMetadata(), diffResult.getDiff(), diffFinishedAt);

        LocalDateTime contextStartedAt = LocalDateTime.now();
        RepositoryContextIndexResult repositoryContext =
                repositoryContextIndexService.index(result.getMetadata(), diffResult.getDiff());
        LocalDateTime contextFinishedAt = LocalDateTime.now();
        reviewTraceRecorder.recordToolTrace(run.getId(),
                reviewTraceRecorder.countToolTraces(run.getId()) + 1,
                RepositoryContextIndexService.TOOL_NAME,
                ToolTraceStatus.SUCCESS,
                "Indexed " + repositoryContext.fileCount()
                        + " repository context file(s), contextBytes=" + repositoryContext.contextBytes()
                        + ", truncated=" + repositoryContext.truncated() + ".",
                null,
                null,
                contextStartedAt,
                contextFinishedAt);

        LocalDateTime staticStartedAt = LocalDateTime.now();
        List<ReviewFinding> staticFindings = staticAnalysisService.analyze(diffResult.getDiff(), repositoryContext);
        LocalDateTime staticFinishedAt = LocalDateTime.now();
        reviewTraceRecorder.recordToolTrace(run.getId(),
                reviewTraceRecorder.countToolTraces(run.getId()) + 1,
                ReviewStaticAnalysisService.TOOL_NAME,
                ToolTraceStatus.SUCCESS,
                "Static analysis produced " + staticFindings.size()
                        + " Semgrep/dependency finding(s).",
                null,
                null,
                staticStartedAt,
                staticFinishedAt);

        return completeProviderReview(
                task,
                run,
                augmentReviewContext(diffResult.getDiff().diffText(), repositoryContext),
                normalizedProvider,
                staticFindings
        );
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

        return responseAssembler.toResponse(completedTask, Collections.emptyList());
    }

    private ReviewTaskResponse completeProviderReview(ReviewTaskEntity task,
                                                      ReviewRunEntity run,
                                                      String normalizedDiffText,
                                                      String normalizedProvider,
                                                      List<ReviewFinding> supplementalFindings) {
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
                normalizedProvider,
                task.getReviewMode()
        );
        ReviewProviderResult providerResult;
        int agentStepStartSequence = reviewTraceRecorder.countToolTraces(run.getId()) + 1;
        try {
            providerResult = reviewPipelineService.run(context);
        } catch (MiMoAgentException ex) {
            LocalDateTime failedAt = LocalDateTime.now();
            reviewTraceRecorder.recordAgentSteps(run.getId(), context.getAgentSteps(), agentStepStartSequence);
            return completeFailedProviderReview(task, run, ex.getErrorCode(), ex.getMessage(), failedAt);
        }
        LocalDateTime reviewFinishedAt = LocalDateTime.now();
        reviewTraceRecorder.recordAgentSteps(run.getId(), context.getAgentSteps(), agentStepStartSequence);
        reviewTraceRecorder.recordProviderTrace(run.getId(), task, providerResult, reviewStartedAt, reviewFinishedAt);

        run.setRequestedProvider(providerResult.getRequestedProvider());
        run.setProviderUsed(providerResult.getProviderUsed());
        run.setProviderHit(providerResult.isProviderHit());
        run.setUpdatedAt(reviewFinishedAt);
        reviewRunRepository.save(run);

        List<ReviewFinding> allFindings = new ArrayList<>(providerResult.getFindings());
        allFindings.addAll(supplementalFindings == null ? Collections.emptyList() : supplementalFindings);

        task.setStatus(ReviewTaskStatus.SUCCESS);
        task.setSummary(buildSummary(task.getPrNumber(), allFindings));
        task.setRequestedProvider(providerResult.getRequestedProvider());
        task.setProviderUsed(providerResult.getProviderUsed());
        task.setProviderHit(providerResult.isProviderHit());
        task.setErrorMessage(null);
        task.setUpdatedAt(reviewFinishedAt);
        ReviewTaskEntity completedTask = reviewTaskRepository.save(task);

        Long runId = run.getId();
        List<ReviewIssueEntity> issueEntities = allFindings.stream()
                .map(finding -> toIssueEntity(finding, completedTask, runId, reviewFinishedAt))
                .collect(Collectors.toList());
        reviewIssueRepository.saveAll(issueEntities);

        LocalDateTime previewStartedAt = LocalDateTime.now();
        run.setStatus(ReviewRunStatus.BUILDING_PREVIEW);
        run.setUpdatedAt(previewStartedAt);
        reviewRunRepository.save(run);

        List<ReviewIssueEntity> persistedIssues =
                reviewIssueRepository.findByReviewTaskIdOrderByIdAsc(completedTask.getId());
        commentPreviewBuilder.buildForRun(runId, persistedIssues, previewStartedAt);
        LocalDateTime previewFinishedAt = LocalDateTime.now();
        reviewTraceRecorder.recordToolTrace(runId,
                reviewTraceRecorder.countToolTraces(runId) + 1,
                "comment.preview.build",
                ToolTraceStatus.SUCCESS,
                "Built " + persistedIssues.size() + " local comment preview(s).",
                null,
                null,
                previewStartedAt,
                previewFinishedAt);

        LocalDateTime completedAt = LocalDateTime.now();
        run.setStatus(ReviewRunStatus.SUCCESS);
        run.setFinishedAt(completedAt);
        run.setUpdatedAt(completedAt);
        reviewRunRepository.save(run);

        return responseAssembler.toResponse(completedTask, persistedIssues);
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

        return responseAssembler.toResponse(failedTask, Collections.emptyList());
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

    private String augmentReviewContext(String diffText, RepositoryContextIndexResult repositoryContext) {
        if (repositoryContext == null || !repositoryContext.hasContext()) {
            return diffText;
        }
        return diffText + "\n\n" + repositoryContext.contextText();
    }

    private ReviewMode resolveReviewMode(CreateReviewTaskRequest request, String normalizedDiffText) {
        if (request.getReviewMode() != null) {
            return request.getReviewMode();
        }
        return normalizedDiffText != null ? ReviewMode.MANUAL_DIFF : ReviewMode.GITHUB_PR;
    }

    private String buildSummary(int prNumber, List<ReviewFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return "Review completed for PR #" + prNumber + " with no findings from the available context.";
        }
        return "Review completed for PR #" + prNumber + " with generated findings.";
    }

    @Transactional(readOnly = true)
    public List<ReviewTaskResponse> listTasks() {
        List<ReviewTaskEntity> tasks = reviewTaskRepository.findAllByOrderByCreatedAtDesc();
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> taskIds = tasks.stream()
                .map(ReviewTaskEntity::getId)
                .collect(Collectors.toList());
        Map<Long, List<ReviewIssueEntity>> issuesByTaskId = reviewIssueRepository
                .findAllByReviewTaskIdsOrderByTaskIdAndId(taskIds)
                .stream()
                .collect(Collectors.groupingBy(issue -> issue.getReviewTask().getId()));

        return responseAssembler.toResponses(tasks, issuesByTaskId);
    }

    @Transactional(readOnly = true)
    public ReviewTaskResponse getTask(Long id) {
        ReviewTaskEntity task = reviewTaskRepository.findById(id)
                .orElseThrow(() -> new ReviewTaskNotFoundException(id));
        List<ReviewIssueEntity> issues = reviewIssueRepository.findByReviewTaskIdOrderByIdAsc(id);
        return responseAssembler.toResponse(task, issues);
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

}
