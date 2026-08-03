package com.codereviewx.backend.review.service;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.review.enums.ToolTraceStatus;
import com.codereviewx.backend.review.exception.ReviewRequestInvalidException;
import com.codereviewx.backend.review.persistence.entity.ReviewCommentPreviewEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewToolTraceEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewToolTraceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagEvidencePublishGateTest {
    @Test void successfulAssemblyRequiresEvidenceValidationTrace() {
        RagProperties properties = new RagProperties(); properties.setRequireEvidence(true); properties.setEnabled(true);
        ReviewToolTraceRepository traces = mock(ReviewToolTraceRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RagEvidencePublishGate gate = new RagEvidencePublishGate(properties, traces, jdbc);
        ReviewCommentPreviewEntity preview = preview("MIMO", 42L, 9L);
        when(traces.findByReviewApiRunIdAndToolName(9L, "rag.context.assemble")).thenReturn(List.of(success("assembled")));
        when(traces.findByReviewApiRunIdAndToolName(9L, "evidence.validate")).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(42L))).thenReturn(0);
        assertThatThrownBy(() -> gate.validate(preview)).isInstanceOf(ReviewRequestInvalidException.class);
        when(traces.findByReviewApiRunIdAndToolName(9L, "evidence.validate")).thenReturn(List.of(success("validated")));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(42L))).thenReturn(1);
        assertThatCode(() -> gate.validate(preview)).doesNotThrowAnyException();
    }

    @Test void disabledRagBlocksModelCommentPublishing() {
        RagProperties properties = new RagProperties(); properties.setRequireEvidence(true); properties.setEnabled(false);
        ReviewToolTraceRepository traces = mock(ReviewToolTraceRepository.class);
        RagEvidencePublishGate gate = new RagEvidencePublishGate(properties, traces, mock(JdbcTemplate.class));
        ReviewCommentPreviewEntity preview = preview("MIMO", 7L, 7L);
        assertThatThrownBy(() -> gate.validate(preview)).isInstanceOf(ReviewRequestInvalidException.class)
                .hasMessageContaining("RAG is not configured");
    }

    @Test void disabledEvidenceRequirementAndNonModelBypass() {
        RagProperties properties = new RagProperties(); properties.setRequireEvidence(false);
        RagEvidencePublishGate gate = new RagEvidencePublishGate(properties, mock(ReviewToolTraceRepository.class), mock(JdbcTemplate.class));
        assertThatCode(() -> gate.validate(preview("MIMO", 1L, 1L))).doesNotThrowAnyException();
        properties.setRequireEvidence(true);
        assertThatCode(() -> gate.validate(preview("SEMGREP", 1L, 1L))).doesNotThrowAnyException();
    }

    private static ReviewToolTraceEntity success(String summary) {
        ReviewToolTraceEntity trace = new ReviewToolTraceEntity(); trace.setStatus(ToolTraceStatus.SUCCESS);
        trace.setOutputSummary(summary); return trace;
    }
    private static ReviewCommentPreviewEntity preview(String source, Long issueId, Long runId) {
        ReviewCommentPreviewEntity preview = new ReviewCommentPreviewEntity(); preview.setSource(source);
        preview.setReviewIssueId(issueId); preview.setReviewApiRunId(runId); return preview;
    }
}
