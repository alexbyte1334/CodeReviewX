package com.codereviewx.backend.review.pipeline.provider;

import com.codereviewx.backend.review.enums.IssueSeverity;
import com.codereviewx.backend.review.enums.IssueSource;
import com.codereviewx.backend.review.enums.IssueStatus;
import com.codereviewx.backend.review.pipeline.ReviewContext;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockReviewProviderTest {

    private final MockReviewProvider provider = new MockReviewProvider();

    @Test
    void review_returnsExactlyThreeFindings() {
        ReviewProviderResult result = provider.review(sampleContext());

        assertThat(result.getFindings()).hasSize(3);
    }

    @Test
    void review_includesOneHighOneMediumOneLow() {
        ReviewProviderResult result = provider.review(sampleContext());
        List<IssueSeverity> severities = result.getFindings().stream()
                .map(ReviewFinding::getSeverity)
                .toList();

        assertThat(severities).containsExactly(
                IssueSeverity.HIGH,
                IssueSeverity.MEDIUM,
                IssueSeverity.LOW
        );
    }

    @Test
    void review_allFindingsHaveSourceMock() {
        ReviewProviderResult result = provider.review(sampleContext());

        assertThat(result.getFindings())
                .allMatch(finding -> finding.getSource() == IssueSource.MOCK);
    }

    @Test
    void review_allFindingsHaveStatusOpen() {
        ReviewProviderResult result = provider.review(sampleContext());

        assertThat(result.getFindings())
                .allMatch(finding -> finding.getStatus() == IssueStatus.OPEN);
    }

    @Test
    void review_publicIssueIdsAreDeterministic() {
        ReviewProviderResult result = provider.review(sampleContext());
        List<String> issueKeys = result.getFindings().stream()
                .map(ReviewFinding::getIssueKey)
                .toList();

        assertThat(issueKeys).containsExactly("ISSUE-1", "ISSUE-2", "ISSUE-3");
    }

    @Test
    void review_resultIsSuccessful() {
        ReviewProviderResult result = provider.review(sampleContext());

        assertThat(result.isSuccessful()).isTrue();
    }

    @Test
    void review_providerNameIsPresent() {
        ReviewProviderResult result = provider.review(sampleContext());

        assertThat(result.getProviderName()).isEqualTo(MockReviewProvider.PROVIDER_NAME);
    }

    private ReviewContext sampleContext() {
        return new ReviewContext(1L, "https://github.com/example/repo", 8, LocalDateTime.now());
    }
}
