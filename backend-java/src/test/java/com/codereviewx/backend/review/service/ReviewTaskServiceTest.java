package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.dto.CreateReviewTaskRequest;
import com.codereviewx.backend.review.dto.ReviewIssueResponse;
import com.codereviewx.backend.review.dto.ReviewTaskResponse;
import com.codereviewx.backend.review.enums.IssueCategory;
import com.codereviewx.backend.review.enums.IssueSeverity;
import com.codereviewx.backend.review.enums.IssueSource;
import com.codereviewx.backend.review.enums.IssueStatus;
import com.codereviewx.backend.review.enums.ReviewTaskStatus;
import com.codereviewx.backend.review.enums.RiskLevel;
import com.codereviewx.backend.review.exception.ReviewTaskNotFoundException;
import com.codereviewx.backend.review.persistence.repository.ReviewIssueRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ReviewTaskServiceTest {

    @Autowired
    private ReviewTaskService service;

    @Autowired
    private ReviewTaskRepository reviewTaskRepository;

    @Autowired
    private ReviewIssueRepository reviewIssueRepository;

    @BeforeEach
    void setUp() {
        reviewIssueRepository.deleteAll();
        reviewTaskRepository.deleteAll();
    }

    @Test
    void createTask_returnsTaskWithSuccessStatus() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(123);

        ReviewTaskResponse response = service.createTask(request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getRepoUrl()).isEqualTo("https://github.com/example/repo");
        assertThat(response.getPrNumber()).isEqualTo(123);
        assertThat(response.getStatus()).isEqualTo(ReviewTaskStatus.SUCCESS);
        assertThat(response.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(response.getSummary()).contains("Review completed for PR #123");
        assertThat(response.getSummary()).doesNotContainIgnoringCase("mock");
        assertThat(response.getErrorMessage()).isNull();
        assertThat(response.getRequestedProvider()).isEqualTo("mimo");
        assertThat(response.getProviderUsed()).isEqualTo("mock");
        assertThat(response.getProviderHit()).isFalse();
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
    }

    @Test
    void createTask_returnsTypedIssues() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(1);

        ReviewTaskResponse response = service.createTask(request);

        assertThat(response.getIssues()).isNotNull().hasSize(3);
    }

    @Test
    void createTask_issueContainsSeverity() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(1);

        ReviewTaskResponse response = service.createTask(request);
        ReviewIssueResponse first = response.getIssues().get(0);

        assertThat(first.getSeverity()).isEqualTo(IssueSeverity.HIGH);
    }

    @Test
    void createTask_issueContainsCategory() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(1);

        ReviewTaskResponse response = service.createTask(request);
        ReviewIssueResponse first = response.getIssues().get(0);

        assertThat(first.getCategory()).isEqualTo(IssueCategory.SECURITY);
    }

    @Test
    void createTask_issueContainsFilePath() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(1);

        ReviewTaskResponse response = service.createTask(request);
        ReviewIssueResponse first = response.getIssues().get(0);

        assertThat(first.getFilePath()).isNotBlank();
    }

    @Test
    void createTask_issueContainsStartLine() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(1);

        ReviewTaskResponse response = service.createTask(request);
        ReviewIssueResponse first = response.getIssues().get(0);

        assertThat(first.getStartLine()).isNotNull().isPositive();
    }

    @Test
    void createTask_issueContainsTitle() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(1);

        ReviewTaskResponse response = service.createTask(request);
        ReviewIssueResponse first = response.getIssues().get(0);

        assertThat(first.getTitle()).isNotBlank();
    }

    @Test
    void createTask_issueContainsDescription() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(1);

        ReviewTaskResponse response = service.createTask(request);
        ReviewIssueResponse first = response.getIssues().get(0);

        assertThat(first.getDescription()).isNotBlank();
    }

    @Test
    void createTask_issueContainsRecommendation() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(1);

        ReviewTaskResponse response = service.createTask(request);
        ReviewIssueResponse first = response.getIssues().get(0);

        assertThat(first.getRecommendation()).isNotBlank();
    }

    @Test
    void createTask_issuesCoverAllSeverityLevels() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(1);

        ReviewTaskResponse response = service.createTask(request);
        List<IssueSeverity> severities = response.getIssues().stream()
                .map(ReviewIssueResponse::getSeverity)
                .toList();

        assertThat(severities).contains(IssueSeverity.HIGH, IssueSeverity.MEDIUM, IssueSeverity.LOW);
    }

    @Test
    void createTask_generateUniqueIds() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(1);

        ReviewTaskResponse first = service.createTask(request);
        ReviewTaskResponse second = service.createTask(request);

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void listTasks_returnsEmptyWhenNoTasks() {
        List<ReviewTaskResponse> list = service.listTasks();
        assertThat(list).isEmpty();
    }

    @Test
    void listTasks_returnsCreatedTasks() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(42);

        service.createTask(request);
        List<ReviewTaskResponse> list = service.listTasks();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getPrNumber()).isEqualTo(42);
    }

    @Test
    void getTask_returnsTaskById() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(7);

        ReviewTaskResponse created = service.createTask(request);
        ReviewTaskResponse found = service.getTask(created.getId());

        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.getPrNumber()).isEqualTo(7);
    }

    @Test
    void getTask_returnsIssues() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(7);

        ReviewTaskResponse created = service.createTask(request);
        ReviewTaskResponse found = service.getTask(created.getId());

        assertThat(found.getIssues()).hasSize(3);
    }

    @Test
    void getTask_throwsNotFoundForMissingId() {
        assertThatThrownBy(() -> service.getTask(999L))
                .isInstanceOf(ReviewTaskNotFoundException.class);
    }

    @Test
    void createTask_hasIssueSummary() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(1);

        ReviewTaskResponse response = service.createTask(request);

        assertThat(response.getIssueSummary()).isNotNull();
        assertThat(response.getIssueSummary().getTotalIssues()).isEqualTo(3);
        assertThat(response.getIssueSummary().getHighCount()).isEqualTo(1);
        assertThat(response.getIssueSummary().getMediumCount()).isEqualTo(1);
        assertThat(response.getIssueSummary().getLowCount()).isEqualTo(1);
        assertThat(response.getIssueSummary().getRiskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void createTask_riskLevelConsistentWithIssueSummary() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(1);

        ReviewTaskResponse response = service.createTask(request);

        assertThat(response.getRiskLevel()).isEqualTo(response.getIssueSummary().getRiskLevel());
    }

    @Test
    void createTask_allIssuesHaveSourceMock() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(1);

        ReviewTaskResponse response = service.createTask(request);

        for (ReviewIssueResponse issue : response.getIssues()) {
            assertThat(issue.getSource()).isEqualTo(IssueSource.MOCK);
        }
    }

    @Test
    void createTask_allIssuesHaveStatusOpen() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(1);

        ReviewTaskResponse response = service.createTask(request);

        for (ReviewIssueResponse issue : response.getIssues()) {
            assertThat(issue.getStatus()).isEqualTo(IssueStatus.OPEN);
        }
    }

    @Test
    void createTask_persistsTaskToDatabase() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(42);

        ReviewTaskResponse response = service.createTask(request);

        assertThat(reviewTaskRepository.findById(response.getId())).isPresent();
    }

    @Test
    void createTask_persistsThreeIssuesToDatabase() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(42);

        ReviewTaskResponse response = service.createTask(request);

        List<?> issues = reviewIssueRepository.findByReviewTaskIdOrderByIdAsc(response.getId());
        assertThat(issues).hasSize(3);
    }

    @Test
    void createTask_issueKeyPreservesPublicId() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(1);

        ReviewTaskResponse response = service.createTask(request);

        assertThat(response.getIssues().get(0).getId()).isEqualTo("ISSUE-1");
        assertThat(response.getIssues().get(1).getId()).isEqualTo("ISSUE-2");
        assertThat(response.getIssues().get(2).getId()).isEqualTo("ISSUE-3");
    }

    @Test
    void getTask_issueSummaryComputedFromPersistedIssues() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(1);

        ReviewTaskResponse created = service.createTask(request);
        ReviewTaskResponse found = service.getTask(created.getId());

        assertThat(found.getIssueSummary()).isNotNull();
        assertThat(found.getIssueSummary().getTotalIssues()).isEqualTo(3);
        assertThat(found.getRiskLevel()).isEqualTo(found.getIssueSummary().getRiskLevel());
    }

    @Test
    void createTask_withDiffText_persistsDiffText() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(10);
        request.setDiffText("diff --git a/src/App.tsx b/src/App.tsx\n+const x = 1;\n");

        ReviewTaskResponse response = service.createTask(request);

        assertThat(response.getIssues()).hasSize(3);
        assertThat(reviewTaskRepository.findById(response.getId()))
                .isPresent()
                .get()
                .extracting(com.codereviewx.backend.review.persistence.entity.ReviewTaskEntity::getDiffText)
                .isEqualTo("diff --git a/src/App.tsx b/src/App.tsx\n+const x = 1;");
    }

    @Test
    void createTask_blankDiffText_notPersisted() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(10);
        request.setDiffText("   ");

        ReviewTaskResponse response = service.createTask(request);

        assertThat(reviewTaskRepository.findById(response.getId()))
                .isPresent()
                .get()
                .extracting(com.codereviewx.backend.review.persistence.entity.ReviewTaskEntity::getDiffText)
                .isNull();
    }

    @Test
    void createTask_whitespaceDiffText_notPersisted() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(10);
        request.setDiffText("\n\t  ");

        ReviewTaskResponse response = service.createTask(request);

        assertThat(reviewTaskRepository.findById(response.getId()))
                .isPresent()
                .get()
                .extracting(com.codereviewx.backend.review.persistence.entity.ReviewTaskEntity::getDiffText)
                .isNull();
    }

    @Test
    void createTask_withDiffText_stillReturnsThreeMockIssues() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(10);
        request.setDiffText("diff --git a/a.txt b/a.txt\n");

        ReviewTaskResponse response = service.createTask(request);

        assertThat(response.getIssues()).hasSize(3);
        assertThat(response.getRiskLevel()).isEqualTo(response.getIssueSummary().getRiskLevel());
    }
}
