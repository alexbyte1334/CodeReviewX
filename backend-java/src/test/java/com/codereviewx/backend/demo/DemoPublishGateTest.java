package com.codereviewx.backend.demo;

import com.codereviewx.backend.review.persistence.entity.ReviewCommentPreviewEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewInputSnapshotEntity;
import com.codereviewx.backend.review.service.ReviewEvidenceValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemoPublishGateTest {
    private final DemoProperties properties = new DemoProperties();
    private final DemoStore store = mock(DemoStore.class);
    private final ReviewEvidenceValidator evidence = mock(ReviewEvidenceValidator.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private DemoPublishGate gate;

    @BeforeEach
    void setUp() {
        properties.setPrNumber(1);
        properties.setExpectedHeadSha("head-sha");
        when(store.jdbc()).thenReturn(jdbc);
        gate = new DemoPublishGate(properties, store, evidence);
    }

    @Test
    void acceptsOnlyPinnedTargetAndGroundedChangedLine() {
        when(evidence.isChangedLine(anyString(), anyString(), anyInt())).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Integer.class),
                any(), any(), any(), any()))
                .thenReturn(1);

        assertThatCode(() -> {
            gate.validateTarget(demo(), snapshot());
            gate.validatePreview(demo(), preview("SQL-1"));
        }).doesNotThrowAnyException();
    }

    @Test
    void blocksUnsafeMarkerAndMissingChangedLine() {
        assertThatThrownBy(() -> gate.validatePreview(demo(), preview("SQL-1 -->")))
                .isInstanceOf(DemoApiException.class)
                .hasMessageContaining("idempotency marker");

        when(evidence.isChangedLine(anyString(), anyString(), anyInt())).thenReturn(false);
        assertThatThrownBy(() -> gate.validatePreview(demo(), preview("SQL-1")))
                .isInstanceOf(DemoApiException.class)
                .hasMessageContaining("changed line");
    }

    @Test
    void blocksRepositoryOrPrDrift() {
        ReviewInputSnapshotEntity snapshot = snapshot();
        snapshot.setPrNumber(2);

        assertThatThrownBy(() -> gate.validateTarget(demo(), snapshot))
                .isInstanceOf(DemoApiException.class)
                .hasMessageContaining("pinned demo");
    }

    private DemoStore.DemoRow demo() {
        return new DemoStore.DemoRow(
                1, "public", properties.getScenarioId(), "idem", "hash",
                2, 3, "READY", null, null,
                "diff", null, LocalDateTime.now(), LocalDateTime.now(), null);
    }

    private ReviewInputSnapshotEntity snapshot() {
        ReviewInputSnapshotEntity snapshot = new ReviewInputSnapshotEntity();
        snapshot.setRepoUrl(properties.getRepoUrl());
        snapshot.setPrNumber(properties.getPrNumber());
        snapshot.setHeadSha(properties.getExpectedHeadSha());
        return snapshot;
    }

    private ReviewCommentPreviewEntity preview(String issueKey) {
        ReviewCommentPreviewEntity preview = new ReviewCommentPreviewEntity();
        preview.setReviewRunId(3L);
        preview.setReviewIssueId(4L);
        preview.setIssueKey(issueKey);
        preview.setFilePath("src/App.java");
        preview.setLineNumber(10);
        return preview;
    }
}
