package com.codereviewx.backend.review.service;

import com.codereviewx.backend.rag.persistence.ReviewIssueEvidenceStore;
import com.codereviewx.backend.rag.retrieval.*;
import com.codereviewx.backend.review.enums.*;
import com.codereviewx.backend.review.persistence.entity.ReviewIssueEntity;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewIssueEvidencePersisterTest {
    @Test void associatesReorderedSavedIssuesByUniqueIssueKey() {
        ReviewIssueEvidenceStore store = mock(ReviewIssueEvidenceStore.class);
        ReviewIssueEvidencePersister persister = new ReviewIssueEvidencePersister(store);
        ReviewFinding first = finding("M1", "C1"); ReviewFinding second = finding("M2", "C2");
        ReviewIssueEntity savedSecond = issue(22L, "M2"); ReviewIssueEntity savedFirst = issue(11L, "M1");
        RagEvidenceBundle bundle = bundle();
        persister.persist(List.of(first, second), List.of(first, second), List.of(savedSecond, savedFirst), bundle);
        verify(store).save(savedFirst, first, bundle);
        verify(store).save(savedSecond, second, bundle);
    }

    @Test void rejectsDuplicateOrMissingKeysBeforePersistence() {
        ReviewIssueEvidenceStore store = mock(ReviewIssueEvidenceStore.class);
        ReviewIssueEvidencePersister persister = new ReviewIssueEvidencePersister(store);
        List<ReviewFinding> duplicates = List.of(finding("M1", "C1"), finding("M1", "C2"));
        assertThatThrownBy(() -> persister.persist(duplicates, duplicates,
                List.of(issue(1L, "M1"), issue(2L, "M1")), bundle()))
                .isInstanceOf(IllegalStateException.class).hasMessage("Review issue evidence association is invalid");
        verifyNoInteractions(store);
    }
    private ReviewFinding finding(String key, String label) { return new ReviewFinding(key, IssueSeverity.HIGH,
            IssueCategory.BUG, IssueSource.MIMO, IssueStatus.OPEN, "p", 1, 1, "t", "d", "r", List.of(label)); }
    private ReviewIssueEntity issue(Long id, String key) { ReviewIssueEntity issue = new ReviewIssueEntity(); issue.setId(id); issue.setIssueKey(key); return issue; }
    private RagEvidenceBundle bundle() { return new RagEvidenceBundle(List.of(
            new RagEvidence("C1", "p", 1, 1, "s", "content one", 1),
            new RagEvidence("C2", "p", 1, 1, "s", "content two", 1)), "",
            RagEvidenceBundle.DegradedReason.NONE, RagRetrievalHealth.HEALTHY); }
}
