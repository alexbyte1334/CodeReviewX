package com.codereviewx.backend.review.pipeline.provider;

import com.codereviewx.backend.review.enums.IssueCategory;
import com.codereviewx.backend.review.enums.IssueSeverity;
import com.codereviewx.backend.review.enums.IssueSource;
import com.codereviewx.backend.review.enums.IssueStatus;
import com.codereviewx.backend.review.pipeline.ReviewContext;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import com.codereviewx.backend.review.pipeline.ReviewProvider;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deterministic mock review provider.
 * Returns fixed demo findings; no GitHub, Semgrep, LLM, or network calls.
 */
@Component
public class MockReviewProvider implements ReviewProvider {

    public static final String PROVIDER_NAME = "MockReviewProvider";

    @Override
    public ReviewProviderResult review(ReviewContext context) {
        List<ReviewFinding> findings = List.of(
                new ReviewFinding(
                        "ISSUE-1",
                        IssueSeverity.HIGH,
                        IssueCategory.SECURITY,
                        IssueSource.MOCK,
                        IssueStatus.OPEN,
                        "src/main/java/com/codereviewx/backend/review/controller/ReviewTaskController.java",
                        42,
                        48,
                        "Potential missing authorization check",
                        "This demo issue indicates that a sensitive endpoint should explicitly check authorization before processing the request.",
                        "Add an authorization guard before the business logic and cover the behavior with a controller test."
                ),
                new ReviewFinding(
                        "ISSUE-2",
                        IssueSeverity.MEDIUM,
                        IssueCategory.MAINTAINABILITY,
                        IssueSource.MOCK,
                        IssueStatus.OPEN,
                        "src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java",
                        76,
                        93,
                        "Service method is doing too much work",
                        "This demo issue suggests the service method combines validation, state transition, and response mapping in one place.",
                        "Extract validation and response mapping into smaller private methods to improve readability and testability."
                ),
                new ReviewFinding(
                        "ISSUE-3",
                        IssueSeverity.LOW,
                        IssueCategory.TEST,
                        IssueSource.MOCK,
                        IssueStatus.OPEN,
                        "src/test/java/com/codereviewx/backend/review/service/ReviewTaskServiceTest.java",
                        21,
                        21,
                        "Missing negative-path coverage",
                        "This demo issue highlights that validation and not-found scenarios should be covered explicitly.",
                        "Add tests for invalid request payloads and missing ReviewTask IDs."
                )
        );

        return new ReviewProviderResult(findings, PROVIDER_NAME, true, null);
    }
}
