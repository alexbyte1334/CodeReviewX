package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.enums.ToolTraceStatus;
import com.codereviewx.backend.review.github.GithubPrDiffLoadResult;
import com.codereviewx.backend.review.github.GithubPrDiffLoader;
import com.codereviewx.backend.review.github.GithubPrMetadataLoadResult;
import com.codereviewx.backend.review.github.GithubPrMetadataLoader;
import com.codereviewx.backend.review.github.GithubProperties;
import com.codereviewx.backend.review.persistence.entity.ReviewProviderTraceEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewTaskEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewToolTraceEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewProviderTraceRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewToolTraceRepository;
import com.codereviewx.backend.review.pipeline.ReviewAgentStep;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ReviewTraceRecorder {

    private final ReviewToolTraceRepository toolTraceRepository;
    private final ReviewProviderTraceRepository providerTraceRepository;
    private final GithubProperties githubProperties;

    public ReviewTraceRecorder(ReviewToolTraceRepository toolTraceRepository,
                               ReviewProviderTraceRepository providerTraceRepository,
                               GithubProperties githubProperties) {
        this.toolTraceRepository = toolTraceRepository;
        this.providerTraceRepository = providerTraceRepository;
        this.githubProperties = githubProperties;
    }

    public int countToolTraces(Long runId) {
        return toolTraceRepository.countByReviewRunId(runId);
    }

    public void recordMetadataLoad(Long runId,
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

    public void recordDiffLoad(Long runId,
                               ReviewTaskEntity task,
                               GithubPrDiffLoadResult result,
                               LocalDateTime startedAt,
                               LocalDateTime finishedAt) {
        ReviewToolTraceEntity trace = new ReviewToolTraceEntity();
        trace.setReviewRunId(runId);
        trace.setSequenceNumber(2);
        trace.setToolName(GithubPrDiffLoader.TOOL_NAME);
        trace.setStatus(result.isSuccess() ? ToolTraceStatus.SUCCESS : ToolTraceStatus.FAILED);
        trace.setInputSummary("repoUrl=" + task.getRepoUrl()
                + ", prNumber=" + task.getPrNumber()
                + ", tokenConfigured=" + githubProperties.hasToken()
                + ", maxChangedFiles=" + githubProperties.getMaxChangedFiles()
                + ", maxDiffBytes=" + githubProperties.getMaxDiffBytes()
                + ", perFilePatchMaxBytes=" + githubProperties.getPerFilePatchMaxBytes());
        trace.setOutputSummary(result.isSuccess()
                ? "Loaded bounded PR diff: fileCount=" + result.getDiff().fileCount()
                + ", diffBytes=" + result.getDiff().diffBytes()
                + ", diffTruncated=" + result.getDiff().diffTruncated()
                : result.getErrorMessage());
        trace.setErrorCode(result.getErrorCode());
        trace.setErrorMessage(result.getErrorMessage());
        trace.setStartedAt(startedAt);
        trace.setFinishedAt(finishedAt);
        trace.setDurationMs(ChronoUnit.MILLIS.between(startedAt, finishedAt));
        trace.setCreatedAt(startedAt);
        toolTraceRepository.save(trace);
    }

    public void recordAgentSteps(Long runId, List<ReviewAgentStep> steps, int startSequenceNumber) {
        int sequence = startSequenceNumber;
        for (ReviewAgentStep step : steps) {
            recordToolTrace(runId,
                    sequence++,
                    step.getStepName(),
                    step.getStatus(),
                    step.getOutputSummary(),
                    step.getErrorCode(),
                    step.getErrorMessage(),
                    step.getStartedAt(),
                    step.getFinishedAt());
        }
    }

    public void recordToolTrace(Long runId,
                                int sequenceNumber,
                                String toolName,
                                ToolTraceStatus status,
                                String outputSummary,
                                String errorCode,
                                String errorMessage,
                                LocalDateTime startedAt,
                                LocalDateTime finishedAt) {
        ReviewToolTraceEntity trace = new ReviewToolTraceEntity();
        trace.setReviewRunId(runId);
        trace.setSequenceNumber(sequenceNumber);
        trace.setToolName(toolName);
        trace.setStatus(status);
        trace.setInputSummary(null);
        trace.setOutputSummary(outputSummary);
        trace.setErrorCode(errorCode);
        trace.setErrorMessage(errorMessage);
        trace.setStartedAt(startedAt);
        trace.setFinishedAt(finishedAt);
        trace.setDurationMs(finishedAt == null ? null : ChronoUnit.MILLIS.between(startedAt, finishedAt));
        trace.setCreatedAt(startedAt);
        toolTraceRepository.save(trace);
    }

    public void recordProviderTrace(Long runId,
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
            trace.setFallbackReason(buildProviderMissReason(providerResult));
        }
        trace.setStartedAt(startedAt);
        trace.setFinishedAt(finishedAt);
        trace.setDurationMs(ChronoUnit.MILLIS.between(startedAt, finishedAt));
        trace.setCreatedAt(startedAt);
        providerTraceRepository.save(trace);
    }

    private String buildProviderMissReason(ReviewProviderResult providerResult) {
        String providerUsed = providerResult.getProviderUsed();
        if (providerUsed == null || providerUsed.isBlank()) {
            return "Requested provider was not fulfilled; provider used is unknown.";
        }
        return "Requested provider fell back to " + providerUsed + " provider.";
    }
}
