package com.codereviewx.backend.review.pipeline;

import com.codereviewx.backend.review.enums.IssueCategory;
import com.codereviewx.backend.review.enums.IssueSeverity;
import com.codereviewx.backend.review.enums.IssueSource;
import com.codereviewx.backend.review.enums.IssueStatus;
import com.codereviewx.backend.review.persistence.entity.ReviewIssueEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewTaskEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewPipelineServiceTest {

    @Test
    void run_invokesProviderAndReturnsResult() {
        ReviewFinding finding = new ReviewFinding(
                "ISSUE-TEST",
                IssueSeverity.HIGH,
                IssueCategory.SECURITY,
                IssueSource.MIMO,
                IssueStatus.OPEN,
                "src/Foo.java",
                1,
                2,
                "Test title",
                "Test description",
                "Test recommendation"
        );
        FakeReviewProvider fakeProvider = new FakeReviewProvider(
                new ReviewProviderResult(List.of(finding), "FakeProvider", true, null)
        );
        ReviewPipelineService pipeline = new ReviewPipelineService(fakeProvider);
        ReviewContext context = new ReviewContext(1L, "https://github.com/example/repo", 1, LocalDateTime.now());

        ReviewProviderResult result = pipeline.run(context);

        assertThat(fakeProvider.wasInvoked()).isTrue();
        assertThat(result.getFindings()).hasSize(1);
        assertThat(result.getFindings().get(0).getIssueKey()).isEqualTo("ISSUE-TEST");
        assertThat(result.getProviderName()).isEqualTo("FakeProvider");
    }

    @Test
    void run_doesNotExposeApiDtosOrJpaEntities() {
        FakeReviewProvider fakeProvider = new FakeReviewProvider(
                new ReviewProviderResult(List.of(), "FakeProvider", true, null)
        );
        ReviewPipelineService pipeline = new ReviewPipelineService(fakeProvider);

        ReviewProviderResult result = pipeline.run(
                new ReviewContext(1L, "https://github.com/example/repo", 1, LocalDateTime.now())
        );

        assertThat(result).isNotInstanceOf(ReviewTaskEntity.class);
        assertThat(result).isNotInstanceOf(ReviewIssueEntity.class);
        assertThat(result.getFindings()).isEmpty();
    }

    private static class FakeReviewProvider implements ReviewProvider {

        private final ReviewProviderResult result;
        private boolean invoked;

        FakeReviewProvider(ReviewProviderResult result) {
            this.result = result;
        }

        @Override
        public ReviewProviderResult review(ReviewContext context) {
            invoked = true;
            return result;
        }

        boolean wasInvoked() {
            return invoked;
        }
    }
}
