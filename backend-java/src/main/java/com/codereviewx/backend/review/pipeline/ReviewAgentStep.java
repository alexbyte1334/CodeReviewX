package com.codereviewx.backend.review.pipeline;

import com.codereviewx.backend.review.enums.ToolTraceStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Sanitized provider step telemetry. It must not contain prompts or raw model output.
 */
public class ReviewAgentStep {

    private final String stepName;
    private final ToolTraceStatus status;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final Long durationMs;
    private final String outputSummary;
    private final String errorCode;
    private final String errorMessage;

    public ReviewAgentStep(String stepName,
                           ToolTraceStatus status,
                           LocalDateTime startedAt,
                           LocalDateTime finishedAt,
                           String outputSummary,
                           String errorCode,
                           String errorMessage) {
        this.stepName = stepName;
        this.status = status;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.durationMs = finishedAt == null ? null : ChronoUnit.MILLIS.between(startedAt, finishedAt);
        this.outputSummary = outputSummary;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getStepName() {
        return stepName;
    }

    public ToolTraceStatus getStatus() {
        return status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
