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
import com.codereviewx.backend.review.persistence.entity.ReviewApiRunEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewApiRunEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewIssueRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewApiRunRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewApiRunRepository;
import com.codereviewx.backend.review.pipeline.ReviewContext;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import com.codereviewx.backend.review.pipeline.ReviewPipelineService;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import com.codereviewx.backend.review.pipeline.provider.mimo.MiMoAgentException;
import com.codereviewx.backend.rag.retrieval.RagEvidenceBundle;
import com.codereviewx.backend.rag.service.RagReviewContextFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReviewWorkflowService {

    private final ReviewIssueRepository reviewIssueRepository;
    private final ReviewApiRunRepository reviewApiRunRepository;
    private final ReviewPipelineService reviewPipelineService;
    private final GithubPrMetadataLoader githubPrMetadataLoader;
    private final GithubPrDiffLoader githubPrDiffLoader;
    private final ReviewTraceRecorder reviewTraceRecorder;
    private final ReviewInputSnapshotService reviewInputSnapshotService;
    private final CommentPreviewBuilder commentPreviewBuilder;
    private final ReviewTaskResponseAssembler responseAssembler;
    private final ReviewStaticAnalysisService staticAnalysisService;
    private final RagReviewContextFacade ragReviewContextFacade;
    private final ReviewEvidenceValidator evidenceValidator;
    private final ReviewIssueEvidencePersister evidencePersister;
    private ReviewApiEventRecorder apiEvents;

    public ReviewWorkflowService(ReviewIssueRepository reviewIssueRepository,
                             ReviewApiRunRepository reviewApiRunRepository,
                             ReviewPipelineService reviewPipelineService,
                             GithubPrMetadataLoader githubPrMetadataLoader,
                             GithubPrDiffLoader githubPrDiffLoader,
                             ReviewTraceRecorder reviewTraceRecorder,
                             ReviewInputSnapshotService reviewInputSnapshotService,
                             CommentPreviewBuilder commentPreviewBuilder,
                             ReviewTaskResponseAssembler responseAssembler,
                             ReviewStaticAnalysisService staticAnalysisService,
                             RagReviewContextFacade ragReviewContextFacade,
                             ReviewEvidenceValidator evidenceValidator,
                             ReviewIssueEvidencePersister evidencePersister) {
        this.reviewIssueRepository = reviewIssueRepository;
        this.reviewApiRunRepository = reviewApiRunRepository;
        this.reviewPipelineService = reviewPipelineService;
        this.githubPrMetadataLoader = githubPrMetadataLoader;
        this.githubPrDiffLoader = githubPrDiffLoader;
        this.reviewTraceRecorder = reviewTraceRecorder;
        this.reviewInputSnapshotService = reviewInputSnapshotService;
        this.commentPreviewBuilder = commentPreviewBuilder;
        this.responseAssembler = responseAssembler;
        this.staticAnalysisService = staticAnalysisService;
        this.ragReviewContextFacade = ragReviewContextFacade;
        this.evidenceValidator = evidenceValidator;
        this.evidencePersister = evidencePersister;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setApiEvents(ReviewApiEventRecorder apiEvents) { this.apiEvents = apiEvents; }

    private void stage(Long runId, String type, String summary) {
        if (apiEvents != null && runId != null) apiEvents.record(runId, type, "RUNNING", summary);
    }

    /**
     * Creates the review_api_run aggregate, then executes MANUAL_DIFF provider path or bounded GITHUB_PR ingestion.
     */
    @Transactional
    public ReviewTaskResponse createTask(CreateReviewTaskRequest request) {
        validateCreateRequest(request);
        PendingTask pending = createPendingTask(request);
        ReviewApiRunEntity savedTask = pending.task();
        ReviewApiRunEntity savedRun = pending.run();
        String normalizedDiffText = pending.normalizedDiffText();
        String normalizedProvider = normalizeProvider(request.getProvider());

        if (savedTask.getReviewMode() == ReviewMode.GITHUB_PR) {
            return completeGithubPrIngestion(savedTask, savedRun, normalizedProvider);
        }

        List<ReviewFinding> staticFindings = staticAnalysisService.analyze(
                new GithubPrDiff(normalizedDiffText, null, null, false, Collections.emptyList()),
                RepositoryContextIndexResult.empty()
        );
        return completeProviderReview(savedTask, savedRun, normalizedDiffText, normalizedProvider,
                staticFindings, null, null);
    }

    /** Creates only the durable task/run rows; callers may execute them asynchronously. */
    @Transactional
    public PendingTask createPendingTask(CreateReviewTaskRequest request) {
        return createPendingTask(request, java.util.UUID.randomUUID().toString(), "internal-" + java.util.UUID.randomUUID());
    }

    @Transactional
    public PendingTask createPendingTask(CreateReviewTaskRequest request, String publicId, String idempotencyKey) {
        validateCreateRequest(request);
        LocalDateTime now = LocalDateTime.now();
        String normalizedDiffText = normalizeDiffText(request.getDiffText());
        ReviewMode reviewMode = resolveReviewMode(request, normalizedDiffText);
        ReviewApiRunEntity task = new ReviewApiRunEntity();
        task.setPublicId(publicId);
        task.setIdempotencyKey(idempotencyKey);
        task.setRepoUrl(request.getRepoUrl());
        task.setPrNumber(request.getPrNumber());
        task.setDiffText(normalizedDiffText);
        task.setReviewMode(reviewMode);
        task.setStatus(ReviewTaskStatus.PENDING);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setRunNumber(1);
        task.setExecutionStatus(ReviewRunStatus.PENDING);
        task.setStatus(ReviewTaskStatus.RUNNING);
        ReviewApiRunEntity saved = reviewApiRunRepository.save(task);
        return new PendingTask(saved, saved, normalizedDiffText);
    }

    /** Executes a persisted review outside the request transaction. */
    public ReviewTaskResponse executeExistingGithubTask(Long taskId, Long runId) {
        ReviewApiRunEntity task = reviewApiRunRepository.findById(taskId)
                .orElseThrow(() -> new ReviewTaskNotFoundException(taskId));
        ReviewApiRunEntity run = reviewApiRunRepository.findById(runId)
                .orElseThrow(() -> new ReviewRequestInvalidException("Review run not found"));
        if (!taskId.equals(run.getId()) || !runId.equals(task.getId())) {
            throw new ReviewRequestInvalidException("Review task/run ownership mismatch");
        }
        if (task.getReviewMode() != ReviewMode.GITHUB_PR || run.getReviewMode() != ReviewMode.GITHUB_PR) {
            throw new ReviewRequestInvalidException("Execution requires GITHUB_PR");
        }
        task.setStatus(ReviewTaskStatus.RUNNING);
        task.setUpdatedAt(LocalDateTime.now());
        reviewApiRunRepository.save(task);
        return completeGithubPrIngestion(task, run, "mimo");
    }

    /** Executes a previously created task without holding the HTTP request transaction. */
    public ReviewTaskResponse executeExistingTask(Long taskId, Long runId) {
        ReviewApiRunEntity task = reviewApiRunRepository.findById(taskId)
                .orElseThrow(() -> new ReviewTaskNotFoundException(taskId));
        ReviewApiRunEntity run = reviewApiRunRepository.findById(runId)
                .orElseThrow(() -> new ReviewRequestInvalidException("Review run not found"));
        if (!taskId.equals(run.getId()) || !runId.equals(task.getId())) {
            throw new ReviewRequestInvalidException("Review task/run ownership mismatch");
        }
        task.setStatus(ReviewTaskStatus.RUNNING);
        task.setUpdatedAt(LocalDateTime.now());
        reviewApiRunRepository.save(task);
        if (task.getReviewMode() == ReviewMode.GITHUB_PR) {
            return completeGithubPrIngestion(task, run, "mimo");
        }
        String diff = normalizeDiffText(task.getDiffText());
        List<ReviewFinding> staticFindings = staticAnalysisService.analyze(
                new GithubPrDiff(diff, null, null, false, Collections.emptyList()),
                RepositoryContextIndexResult.empty());
        return completeProviderReview(task, run, diff, "mimo", staticFindings, null, null);
    }

    public record PendingTask(ReviewApiRunEntity task, ReviewApiRunEntity run, String normalizedDiffText) {}

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
                && !isSupportedProvider(request.getProvider().trim())) {
            throw new ReviewRequestInvalidException("provider must be a supported OpenAI-compatible provider");
        }
        if (request.getReviewMode() == ReviewMode.MANUAL_DIFF
                && normalizeDiffText(request.getDiffText()) == null) {
            throw new ReviewRequestInvalidException("MANUAL_DIFF requires non-blank diffText");
        }
    }

    private boolean isSupportedProvider(String provider) {
        return List.of("mimo", "openai", "deepseek", "qwen", "moonshot", "zhipu",
                        "custom", "openai-compatible", "open_ai_compatible")
                .contains(provider.toLowerCase());
    }

    private ReviewTaskResponse completeGithubPrIngestion(ReviewApiRunEntity task,
                                                         ReviewApiRunEntity run,
                                                         String normalizedProvider) {
        LocalDateTime ingestionStartedAt = LocalDateTime.now();
        run.setExecutionStatus(ReviewRunStatus.INGESTING);
        run.setUpdatedAt(ingestionStartedAt);
        reviewApiRunRepository.save(run);
        stage(run.getId(), "STAGE_INGEST", "Loading GitHub PR metadata and diff.");

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
        stage(run.getId(), "STAGE_INDEX", "Preparing repository context and commit-scoped index.");

        RagReviewContextFacade.PreparedContext prepared =
                ragReviewContextFacade.prepare(result.getMetadata(), diffResult.getDiff(), task.getId(), run.getId());
        RepositoryContextIndexResult repositoryContext = prepared.legacyContext();

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
        stage(run.getId(), "STAGE_RETRIEVE", prepared.evidenceBundle() == null
                ? "RAG is unavailable; using bounded fallback context." : "Repository evidence retrieved.");

        return completeProviderReview(
                task,
                run,
                augmentReviewContext(diffResult.getDiff().diffText(), repositoryContext),
                normalizedProvider,
                staticFindings,
                prepared.evidenceBundle(),
                diffResult.getDiff()
        );
    }

    private ReviewTaskResponse completeFailedGithubPrIngestion(ReviewApiRunEntity task,
                                                               ReviewApiRunEntity run,
                                                               String errorCode,
                                                               String errorMessage,
                                                               LocalDateTime now) {
        run.setExecutionStatus(ReviewRunStatus.FAILED);
        run.setErrorCode(errorCode);
        run.setErrorMessage(errorMessage);
        run.setFinishedAt(now);
        run.setUpdatedAt(now);
        reviewApiRunRepository.save(run);

        task.setStatus(ReviewTaskStatus.FAILED);
        task.setErrorMessage(errorMessage);
        task.setSummary(null);
        task.setRequestedProvider(null);
        task.setProviderUsed(null);
        task.setProviderHit(null);
        task.setUpdatedAt(now);
        ReviewApiRunEntity completedTask = reviewApiRunRepository.save(task);

        return responseAssembler.toResponse(completedTask, Collections.emptyList());
    }

    private ReviewTaskResponse completeProviderReview(ReviewApiRunEntity task,
                                                      ReviewApiRunEntity run,
                                                      String normalizedDiffText,
                                                      String normalizedProvider,
                                                      List<ReviewFinding> supplementalFindings,
                                                      RagEvidenceBundle evidenceBundle,
                                                      GithubPrDiff githubDiff) {
        LocalDateTime reviewStartedAt = LocalDateTime.now();
        run.setExecutionStatus(ReviewRunStatus.REVIEWING);
        run.setUpdatedAt(reviewStartedAt);
        reviewApiRunRepository.save(run);
        stage(run.getId(), "STAGE_PLAN", "Planning bounded review steps.");

        ReviewContext context = new ReviewContext(
                task.getId(),
                task.getRepoUrl(),
                task.getPrNumber(),
                task.getCreatedAt(),
                normalizedDiffText,
                normalizedProvider,
                task.getReviewMode(),
                evidenceBundle
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
        stage(run.getId(), "STAGE_EXECUTE", "Review model completed bounded execution.");

        run.setRequestedProvider(providerResult.getRequestedProvider());
        run.setProviderUsed(providerResult.getProviderUsed());
        run.setProviderHit(providerResult.isProviderHit());
        run.setUpdatedAt(reviewFinishedAt);
        reviewApiRunRepository.save(run);

        List<ReviewFinding> providerFindings = providerResult.getFindings();
        if (evidenceBundle != null) {
            providerFindings = providerFindings.stream()
                    .filter(finding -> evidenceValidator.isGrounded(finding, evidenceBundle, githubDiff)).toList();
        }
        stage(run.getId(), "STAGE_EVIDENCE", evidenceBundle == null
                ? "No Evidence available; publishing will be blocked." : "Evidence validated for findings.");
        List<ReviewFinding> allFindings = new ArrayList<>(providerFindings);
        allFindings.addAll(supplementalFindings == null ? Collections.emptyList() : supplementalFindings);

        task.setStatus(ReviewTaskStatus.SUCCESS);
        task.setSummary(buildSummary(task.getPrNumber(), allFindings));
        task.setRequestedProvider(providerResult.getRequestedProvider());
        task.setProviderUsed(providerResult.getProviderUsed());
        task.setProviderHit(providerResult.isProviderHit());
        task.setErrorMessage(null);
        task.setUpdatedAt(reviewFinishedAt);
        ReviewApiRunEntity completedTask = reviewApiRunRepository.save(task);

        Long runId = run.getId();
        List<ReviewIssueEntity> issueEntities = allFindings.stream()
                .map(finding -> toIssueEntity(finding, completedTask, runId, reviewFinishedAt))
                .collect(Collectors.toList());
        List<ReviewIssueEntity> savedIssues = reviewIssueRepository.saveAll(issueEntities);

        if (evidenceBundle != null) {
            evidencePersister.persist(providerFindings, allFindings, savedIssues, evidenceBundle);
            LocalDateTime evidenceFinishedAt = LocalDateTime.now();
            reviewTraceRecorder.recordToolTrace(runId, reviewTraceRecorder.countToolTraces(runId) + 1,
                    "evidence.validate", ToolTraceStatus.SUCCESS,
                    "Validated " + providerFindings.size() + " grounded AI finding(s).", null, null,
                    reviewFinishedAt, evidenceFinishedAt);
        }

        LocalDateTime previewStartedAt = LocalDateTime.now();
        run.setExecutionStatus(ReviewRunStatus.BUILDING_PREVIEW);
        run.setUpdatedAt(previewStartedAt);
        reviewApiRunRepository.save(run);
        stage(run.getId(), "STAGE_PREVIEW", "Building local comment previews.");

        List<ReviewIssueEntity> persistedIssues =
                reviewIssueRepository.findByReviewApiRunIdOrderByIdAsc(completedTask.getId());
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
        run.setExecutionStatus(ReviewRunStatus.SUCCESS);
        run.setFinishedAt(completedAt);
        run.setUpdatedAt(completedAt);
        reviewApiRunRepository.save(run);

        return responseAssembler.toResponse(completedTask, persistedIssues);
    }

    private ReviewTaskResponse completeFailedProviderReview(ReviewApiRunEntity task,
                                                            ReviewApiRunEntity run,
                                                            String errorCode,
                                                            String errorMessage,
                                                            LocalDateTime now) {
        run.setExecutionStatus(ReviewRunStatus.FAILED);
        run.setRequestedProvider("mimo");
        run.setProviderUsed(null);
        run.setProviderHit(false);
        run.setErrorCode(errorCode);
        run.setErrorMessage(errorMessage);
        run.setFinishedAt(now);
        run.setUpdatedAt(now);
        reviewApiRunRepository.save(run);

        task.setStatus(ReviewTaskStatus.FAILED);
        task.setSummary(null);
        task.setRequestedProvider("mimo");
        task.setProviderUsed(null);
        task.setProviderHit(false);
        task.setErrorMessage(errorMessage);
        task.setUpdatedAt(now);
        ReviewApiRunEntity failedTask = reviewApiRunRepository.save(task);

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
        return provider == null || provider.isBlank() ? "openai-compatible" : provider.trim().toLowerCase();
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
        List<ReviewApiRunEntity> tasks = reviewApiRunRepository.findAllByOrderByCreatedAtDesc();
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> taskIds = tasks.stream()
                .map(ReviewApiRunEntity::getId)
                .collect(Collectors.toList());
        Map<Long, List<ReviewIssueEntity>> issuesByTaskId = reviewIssueRepository
                .findAllByReviewApiRunIdsOrderByTaskIdAndId(taskIds)
                .stream()
                .collect(Collectors.groupingBy(issue -> issue.getReviewApiRun().getId()));

        return responseAssembler.toResponses(tasks, issuesByTaskId);
    }

    @Transactional(readOnly = true)
    public ReviewTaskResponse getTask(Long id) {
        ReviewApiRunEntity task = reviewApiRunRepository.findById(id)
                .orElseThrow(() -> new ReviewTaskNotFoundException(id));
        List<ReviewIssueEntity> issues = reviewIssueRepository.findByReviewApiRunIdOrderByIdAsc(id);
        return responseAssembler.toResponse(task, issues);
    }

    private ReviewIssueEntity toIssueEntity(ReviewFinding finding,
                                            ReviewApiRunEntity task,
                                            Long reviewApiRunId,
                                            LocalDateTime now) {
        ReviewIssueEntity entity = new ReviewIssueEntity();
        entity.setReviewApiRun(task);
        entity.setReviewApiRunId(reviewApiRunId);
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
