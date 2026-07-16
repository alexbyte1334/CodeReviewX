package com.codereviewx.backend.review.service;

import com.codereviewx.backend.rag.retrieval.RagContextAssembler;
import com.codereviewx.backend.rag.retrieval.RagEvidence;
import com.codereviewx.backend.rag.retrieval.RagEvidenceBundle;
import com.codereviewx.backend.review.enums.IssueCategory;
import com.codereviewx.backend.review.enums.IssueSeverity;
import com.codereviewx.backend.review.enums.IssueSource;
import com.codereviewx.backend.review.enums.IssueStatus;
import com.codereviewx.backend.review.github.GithubPrDiff;
import com.codereviewx.backend.review.github.GithubPrDiffFile;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewTaskServiceRagIntegrationTest {

    private final ReviewEvidenceValidator validator = new ReviewEvidenceValidator();
    private final GithubPrDiff diff = new GithubPrDiff(
            "diff --git a/src/App.ts b/src/App.ts\n@@ -9,2 +10,3 @@\n+const value = risky();\n",
            1, 80, false, List.of(new GithubPrDiffFile("src/App.ts", "modified", 1, 0, 1, 40, false)));
    private final RagEvidenceBundle bundle = new RagEvidenceBundle(
            List.of(new RagEvidence("C2", "src/App.ts", 8, 22, "head-sha", "const value = risky();", 0.9)),
            "evidence", RagEvidenceBundle.DegradedReason.NONE, RagContextAssembler.RetrievalHealth.HEALTHY);

    @Test
    void acceptsFindingGroundedByKnownMatchingEvidenceAndChangedLine() {
        assertThat(validator.isGrounded(finding("src/App.ts", 10, List.of("C2")), bundle, diff)).isTrue();
    }

    @Test
    void rejectsMissingUnknownConflictingAndOutOfRangeEvidence() {
        assertThat(validator.isGrounded(finding("src/App.ts", 10, List.of()), bundle, diff)).isFalse();
        assertThat(validator.isGrounded(finding("src/App.ts", 10, List.of("C9")), bundle, diff)).isFalse();
        assertThat(validator.isGrounded(finding("src/Other.ts", 10, List.of("C2")), bundle, diff)).isFalse();
        assertThat(validator.isGrounded(finding("src/App.ts", 80, List.of("C2")), bundle, diff)).isFalse();
    }

    private ReviewFinding finding(String path, int line, List<String> labels) {
        return new ReviewFinding("MIMO-1", IssueSeverity.HIGH, IssueCategory.BUG, IssueSource.MIMO,
                IssueStatus.OPEN, path, line, line, "title", "description", "recommendation", labels);
    }
}
