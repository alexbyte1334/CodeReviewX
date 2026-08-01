package com.codereviewx.backend.review.service;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.review.exception.ReviewRequestInvalidException;
import com.codereviewx.backend.review.persistence.entity.ReviewCommentPreviewEntity;
import com.codereviewx.backend.review.enums.ToolTraceStatus;
import com.codereviewx.backend.review.persistence.repository.ReviewToolTraceRepository;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
public class RagEvidencePublishGate {
    private final RagProperties properties;
    private final ReviewToolTraceRepository traces;
    private final JdbcTemplate jdbc;

    public RagEvidencePublishGate(RagProperties properties, ReviewToolTraceRepository traces, JdbcTemplate jdbc) {
        this.properties = properties;
        this.traces = traces;
        this.jdbc = jdbc;
    }

    public void validate(ReviewCommentPreviewEntity preview) {
        if (!properties.isRequireEvidence() || !isModelFinding(preview.getSource())) return;
        if (preview.getReviewApiRunId() == null || !successfulRagRun(preview.getReviewApiRunId())) return;
        if (preview.getReviewIssueId() == null) throw missingEvidence();
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM review_issue_evidence WHERE review_issue_id = ?",
                Integer.class, preview.getReviewIssueId());
        if (count == null || count < 1) throw missingEvidence();
    }

    private boolean successfulRagRun(Long runId) {
        return traces.findByReviewApiRunIdAndToolName(runId, "rag.context.assemble").stream()
                .anyMatch(trace -> trace.getStatus() == ToolTraceStatus.SUCCESS
                        && trace.getOutputSummary() != null);
    }

    private boolean isModelFinding(String source) {
        return "MIMO".equals(source) || "LLM".equals(source);
    }

    private ReviewRequestInvalidException missingEvidence() {
        return new ReviewRequestInvalidException("RAG model comment requires persisted evidence before publishing");
    }
}
