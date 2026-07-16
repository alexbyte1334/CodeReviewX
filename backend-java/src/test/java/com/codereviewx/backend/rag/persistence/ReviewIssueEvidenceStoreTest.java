package com.codereviewx.backend.rag.persistence;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.retrieval.*;
import com.codereviewx.backend.review.enums.*;
import com.codereviewx.backend.review.persistence.entity.ReviewIssueEntity;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ReviewIssueEvidenceStoreTest {
    @Test
    void persistsOriginalChunkIdentityAndHashWithBoundedExcerpt() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        RagProperties properties = new RagProperties(); properties.setEnabled(true);
        ReviewIssueEvidenceStore store = new ReviewIssueEvidenceStore(jdbc, properties);
        ReviewIssueEntity issue = new ReviewIssueEntity(); issue.setId(44L);
        ReviewFinding finding = new ReviewFinding("M1", IssueSeverity.HIGH, IssueCategory.BUG, IssueSource.MIMO,
                IssueStatus.OPEN, "src/A.java", 1, 1, "t", "d", "r", List.of("C1"));
        RagEvidence evidence = new RagEvidence("C1", "src/A.java", 1, 2, "sha", "x".repeat(2500), 0.9,
                false, false, new RagEvidenceSourceIdentity(987L, "original-hash"));
        RagEvidenceBundle bundle = new RagEvidenceBundle(List.of(evidence), "prompt",
                RagEvidenceBundle.DegradedReason.NONE, RagContextAssembler.RetrievalHealth.HEALTHY);

        store.save(issue, finding, bundle);

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), arguments.capture());
        assertThat(arguments.getValue()).contains(44L, 987L, "original-hash");
        assertThat(arguments.getValue()[7].toString()).hasSize(2000);
    }

    @Test void rejectsUnknownSourceIdentityWithoutLeakingEvidence() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RagProperties properties = new RagProperties(); properties.setEnabled(true);
        ReviewIssueEvidenceStore store = new ReviewIssueEvidenceStore(jdbc, properties);
        ReviewIssueEntity issue = new ReviewIssueEntity(); issue.setId(44L);
        ReviewFinding finding = new ReviewFinding("M1", IssueSeverity.HIGH, IssueCategory.BUG, IssueSource.MIMO,
                IssueStatus.OPEN, "src/A.java", 1, 1, "t", "d", "r", List.of("C1"));
        RagEvidenceBundle bundle = new RagEvidenceBundle(List.of(new RagEvidence("C1", "src/A.java", 1, 2,
                "sha", "super-secret-source", 0.9)), "prompt", RagEvidenceBundle.DegradedReason.NONE,
                RagContextAssembler.RetrievalHealth.HEALTHY);
        assertThatThrownBy(() -> store.save(issue, finding, bundle)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Verified evidence source identity is incomplete")
                .hasMessageNotContaining("secret").hasMessageNotContaining("C1").hasMessageNotContaining("44");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
