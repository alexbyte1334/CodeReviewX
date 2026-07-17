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
        RagProperties properties = new RagProperties(); properties.setRequireEvidence(true);
        ReviewToolTraceRepository traces = mock(ReviewToolTraceRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RagEvidencePublishGate gate = new RagEvidencePublishGate(properties, traces, jdbc);
        ReviewCommentPreviewEntity preview = preview("MIMO", 42L, 9L);
        when(traces.findByReviewRunIdAndToolName(9L, "rag.context.assemble")).thenReturn(List.of(success("assembled")));
        when(traces.findByReviewRunIdAndToolName(9L, "evidence.validate")).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(42L))).thenReturn(0);
        assertThatThrownBy(() -> gate.validate(preview)).isInstanceOf(ReviewRequestInvalidException.class);
        when(traces.findByReviewRunIdAndToolName(9L, "evidence.validate")).thenReturn(List.of(success("validated")));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(42L))).thenReturn(1);
        assertThatCode(() -> gate.validate(preview)).doesNotThrowAnyException();
    }

    @Test void legacyFallbackAndExcludedRunKeepOldPublishSemantics() {
        RagProperties properties = new RagProperties(); properties.setRequireEvidence(true);
        ReviewToolTraceRepository traces = mock(ReviewToolTraceRepository.class);
        RagEvidencePublishGate gate = new RagEvidencePublishGate(properties, traces, mock(JdbcTemplate.class));
        ReviewCommentPreviewEntity preview = preview("MIMO", 7L, 7L);
        when(traces.findByReviewRunIdAndToolName(7L, "rag.context.assemble")).thenReturn(List.of());
        assertThatCode(() -> gate.validate(preview)).doesNotThrowAnyException();
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
        preview.setReviewIssueId(issueId); preview.setReviewRunId(runId); return preview;
    }
}
