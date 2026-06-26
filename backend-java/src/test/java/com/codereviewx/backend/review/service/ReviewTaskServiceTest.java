package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.dto.CreateReviewTaskRequest;
import com.codereviewx.backend.review.dto.ReviewIssueResponse;
import com.codereviewx.backend.review.dto.ReviewTaskResponse;
import com.codereviewx.backend.review.enums.IssueCategory;
import com.codereviewx.backend.review.enums.IssueSeverity;
import com.codereviewx.backend.review.enums.IssueSource;
import com.codereviewx.backend.review.enums.IssueStatus;
import com.codereviewx.backend.review.enums.ReviewMode;
import com.codereviewx.backend.review.enums.ReviewTaskStatus;
import com.codereviewx.backend.review.enums.RiskLevel;
import com.codereviewx.backend.review.exception.ReviewTaskNotFoundException;
import com.codereviewx.backend.review.persistence.entity.ReviewIssueEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewCommentPreviewRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewInputSnapshotRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewIssueRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewProviderTraceRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewRunRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewTaskRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewToolTraceRepository;
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

    @Autowired
    private ReviewCommentPreviewRepository commentPreviewRepository;

    @Autowired
    private ReviewToolTraceRepository toolTraceRepository;

    @Autowired
    private ReviewProviderTraceRepository providerTraceRepository;

    @Autowired
    private ReviewInputSnapshotRepository inputSnapshotRepository;

    @Autowired
    private ReviewRunRepository reviewRunRepository;

    @BeforeEach
    void setUp() {
        commentPreviewRepository.deleteAll();
        toolTraceRepository.deleteAll();
        providerTraceRepository.deleteAll();
        inputSnapshotRepository.deleteAll();
        reviewRunRepository.deleteAll();
        reviewIssueRepository.deleteAll();
        reviewTaskRepository.deleteAll();
    }

    private static final String SAMPLE_DIFF = "diff --git a/a.txt b/a.txt\n";

    private CreateReviewTaskRequest manualDiffRequest(int prNumber) {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(prNumber);
        request.setDiffText(SAMPLE_DIFF);
        return request;
    }

    @Test
    void createTask_returnsTaskWithSuccessStatus() {
        ReviewTaskResponse response = service.createTask(manualDiffRequest(123));

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
        assertThat(response.getLatestRunId()).isNotNull();
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
    }

    @Test
    void createTask_returnsTypedIssues() {
        CreateReviewTaskRequest request = manualDiffRequest(1);

        ReviewTaskResponse response = service.createTask(request);

        assertThat(response.getIssues()).isNotNull().hasSize(3);
    }

    @Test
    void createTask_issueContainsSeverity() {
        CreateReviewTaskRequest request = manualDiffRequest(1);

        ReviewTaskResponse response = service.createTask(request);
        ReviewIssueResponse first = response.getIssues().get(0);

        assertThat(first.getSeverity()).isEqualTo(IssueSeverity.HIGH);
    }

    @Test
    void createTask_issueContainsCategory() {
        CreateReviewTaskRequest request = manualDiffRequest(1);

        ReviewTaskResponse response = service.createTask(request);
        ReviewIssueResponse first = response.getIssues().get(0);

        assertThat(first.getCategory()).isEqualTo(IssueCategory.SECURITY);
    }

    @Test
    void createTask_issueContainsFilePath() {
        CreateReviewTaskRequest request = manualDiffRequest(1);

        ReviewTaskResponse response = service.createTask(request);
        ReviewIssueResponse first = response.getIssues().get(0);

        assertThat(first.getFilePath()).isNotBlank();
    }

    @Test
    void createTask_issueContainsStartLine() {
        CreateReviewTaskRequest request = manualDiffRequest(1);

        ReviewTaskResponse response = service.createTask(request);
        ReviewIssueResponse first = response.getIssues().get(0);

        assertThat(first.getStartLine()).isNotNull().isPositive();
    }

    @Test
    void createTask_issueContainsTitle() {
        CreateReviewTaskRequest request = manualDiffRequest(1);

        ReviewTaskResponse response = service.createTask(request);
        ReviewIssueResponse first = response.getIssues().get(0);

        assertThat(first.getTitle()).isNotBlank();
    }

    @Test
    void createTask_issueContainsDescription() {
        CreateReviewTaskRequest request = manualDiffRequest(1);

        ReviewTaskResponse response = service.createTask(request);
        ReviewIssueResponse first = response.getIssues().get(0);

        assertThat(first.getDescription()).isNotBlank();
    }

    @Test
    void createTask_issueContainsRecommendation() {
        CreateReviewTaskRequest request = manualDiffRequest(1);

        ReviewTaskResponse response = service.createTask(request);
        ReviewIssueResponse first = response.getIssues().get(0);

        assertThat(first.getRecommendation()).isNotBlank();
    }

    @Test
    void createTask_issuesCoverAllSeverityLevels() {
        CreateReviewTaskRequest request = manualDiffRequest(1);

        ReviewTaskResponse response = service.createTask(request);
        List<IssueSeverity> severities = response.getIssues().stream()
                .map(ReviewIssueResponse::getSeverity)
                .toList();

        assertThat(severities).contains(IssueSeverity.HIGH, IssueSeverity.MEDIUM, IssueSeverity.LOW);
    }

    @Test
    void createTask_generateUniqueIds() {
        ReviewTaskResponse first = service.createTask(manualDiffRequest(1));
        ReviewTaskResponse second = service.createTask(manualDiffRequest(1));

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void listTasks_returnsEmptyWhenNoTasks() {
        List<ReviewTaskResponse> list = service.listTasks();
        assertThat(list).isEmpty();
    }

    @Test
    void listTasks_returnsCreatedTasks() {
        service.createTask(manualDiffRequest(42));
        List<ReviewTaskResponse> list = service.listTasks();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getPrNumber()).isEqualTo(42);
    }

    @Test
    void getTask_returnsTaskById() {
        ReviewTaskResponse created = service.createTask(manualDiffRequest(7));
        ReviewTaskResponse found = service.getTask(created.getId());

        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.getPrNumber()).isEqualTo(7);
    }

    @Test
    void getTask_returnsIssues() {
        ReviewTaskResponse created = service.createTask(manualDiffRequest(7));
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
        CreateReviewTaskRequest request = manualDiffRequest(1);

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
        CreateReviewTaskRequest request = manualDiffRequest(1);

        ReviewTaskResponse response = service.createTask(request);

        assertThat(response.getRiskLevel()).isEqualTo(response.getIssueSummary().getRiskLevel());
    }

    @Test
    void createTask_allIssuesHaveSourceMock() {
        CreateReviewTaskRequest request = manualDiffRequest(1);

        ReviewTaskResponse response = service.createTask(request);

        for (ReviewIssueResponse issue : response.getIssues()) {
            assertThat(issue.getSource()).isEqualTo(IssueSource.MOCK);
        }
    }

    @Test
    void createTask_allIssuesHaveStatusOpen() {
        CreateReviewTaskRequest request = manualDiffRequest(1);

        ReviewTaskResponse response = service.createTask(request);

        for (ReviewIssueResponse issue : response.getIssues()) {
            assertThat(issue.getStatus()).isEqualTo(IssueStatus.OPEN);
        }
    }

    @Test
    void createTask_persistsTaskToDatabase() {
        ReviewTaskResponse response = service.createTask(manualDiffRequest(42));

        assertThat(reviewTaskRepository.findById(response.getId())).isPresent();
    }

    @Test
    void createTask_persistsThreeIssuesToDatabase() {
        ReviewTaskResponse response = service.createTask(manualDiffRequest(42));

        List<?> issues = reviewIssueRepository.findByReviewTaskIdOrderByIdAsc(response.getId());
        assertThat(issues).hasSize(3);
    }

    @Test
    void createTask_issueKeyPreservesPublicId() {
        CreateReviewTaskRequest request = manualDiffRequest(1);

        ReviewTaskResponse response = service.createTask(request);

        assertThat(response.getIssues().get(0).getId()).isEqualTo("ISSUE-1");
        assertThat(response.getIssues().get(1).getId()).isEqualTo("ISSUE-2");
        assertThat(response.getIssues().get(2).getId()).isEqualTo("ISSUE-3");
    }

    @Test
    void getTask_issueSummaryComputedFromPersistedIssues() {
        ReviewTaskResponse created = service.createTask(manualDiffRequest(1));
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

        assertThat(response.getStatus()).isEqualTo(ReviewTaskStatus.FAILED);
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

        assertThat(response.getStatus()).isEqualTo(ReviewTaskStatus.FAILED);
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

    @Test
    void createTask_withoutDiffText_returnsGithubAuthMissing() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(10);

        ReviewTaskResponse response = service.createTask(request);

        assertThat(response.getReviewMode()).isEqualTo(ReviewMode.GITHUB_PR);
        assertThat(response.getLatestRunId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ReviewTaskStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo("GITHUB_AUTH_MISSING");
        assertThat(response.getErrorMessage()).contains("GITHUB_TOKEN");
        assertThat(response.getIssues()).isEmpty();
        assertThat(response.getTraceSummary().getToolCount()).isEqualTo(1);
        assertThat(response.getTraceSummary().getFailedToolCount()).isEqualTo(1);
        assertThat(response.getCommentPreviewCount()).isEqualTo(0);
    }

    @Test
    void createTask_manualDiff_linksIssuesToRun() {
        ReviewTaskResponse response = service.createTask(manualDiffRequest(10));

        assertThat(response.getLatestRunId()).isNotNull();
        List<ReviewIssueEntity> issues = reviewIssueRepository.findByReviewTaskIdOrderByIdAsc(response.getId());
        assertThat(issues).hasSize(3);
        assertThat(issues.get(0).getReviewRunId()).isEqualTo(response.getLatestRunId());
    }

    @Test
    void createTask_withDiffText_setsManualDiffReviewMode() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(10);
        request.setDiffText("diff --git a/a.txt b/a.txt\n");

        ReviewTaskResponse response = service.createTask(request);

        assertThat(response.getReviewMode()).isEqualTo(ReviewMode.MANUAL_DIFF);
    }
}
