package com.codereviewx.backend.demo;

import java.time.LocalDateTime;
import java.util.List;

public final class DemoDtos {
    private DemoDtos() {}

    public record CreateRequest(String scenarioId) {}
    public record CreateResponse(
            String runId, String status, String snapshotUrl, String eventsUrl, String mode
    ) {}
    public record DecisionRequest(String decision, List<Long> selectedPreviewIds) {}
    public record PublishRequest(List<Long> selectedPreviewIds) {}
    public record PublishResponse(String status, List<String> commentUrls) {}
    public record Step(
            String id, String label, String status, Long durationMs, String summary, String errorCode
    ) {}
    public record Finding(
            String issueKey, String severity, String category, String filePath, Integer line,
            String title, String description, String recommendation
    ) {}
    public record Evidence(
            String issueKey, String citationLabel, String path, Integer startLine, Integer endLine,
            String excerpt, Integer rank, Double score
    ) {}
    public record ToolTrace(
            long sequence, String toolName, String status, String inputSummary,
            String outputSummary, String errorCode, Long durationMs
    ) {}
    public record Preview(
            Long id, String issueKey, String filePath, Integer line, String severity, String category,
            String body, boolean selected, String publishStatus, String githubUrl
    ) {}
    public record Event(
            long sequence, String type, String step, String status, String summary,
            String errorCode, Long durationMs, LocalDateTime createdAt
    ) {}
    public record Snapshot(
            String runId, String scenarioId, String mode, String status, String decision,
            String replayReason, String diffText, List<Step> steps, List<Finding> findings,
            List<Evidence> evidence, List<ToolTrace> toolTrace, List<Preview> commentPreviews,
            List<Event> events, String publishedCommentUrl, LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
