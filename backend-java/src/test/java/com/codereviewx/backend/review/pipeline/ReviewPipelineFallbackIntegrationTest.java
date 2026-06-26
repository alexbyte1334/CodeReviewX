package com.codereviewx.backend.review.pipeline;

import com.codereviewx.backend.review.dto.CreateReviewTaskRequest;
import com.codereviewx.backend.review.dto.ReviewTaskResponse;
import com.codereviewx.backend.review.enums.IssueSource;
import com.codereviewx.backend.review.enums.RiskLevel;
import com.codereviewx.backend.review.service.ReviewTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "codereviewx.review.provider=mimo",
        "codereviewx.ai.mimo.api-key="
})
class ReviewPipelineFallbackIntegrationTest {

    @Autowired
    private ReviewTaskService reviewTaskService;

    @BeforeEach
    void setUp(@Autowired com.codereviewx.backend.review.persistence.repository.ReviewIssueRepository issueRepository,
               @Autowired com.codereviewx.backend.review.persistence.repository.ReviewTaskRepository taskRepository) {
        issueRepository.deleteAll();
        taskRepository.deleteAll();
    }

    @Test
    void createTask_mimoModeWithoutKeyFallsBackToMockAndSucceeds() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/fallback");
        request.setPrNumber(9);

        ReviewTaskResponse response = reviewTaskService.createTask(request);

        assertThat(response.getStatus().name()).isEqualTo("SUCCESS");
        assertThat(response.getIssues()).hasSize(3);
        assertThat(response.getIssues()).allMatch(issue -> issue.getSource() == IssueSource.MOCK);
        assertThat(response.getIssueSummary().getTotalIssues()).isEqualTo(3);
        assertThat(response.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(response.getRiskLevel()).isEqualTo(response.getIssueSummary().getRiskLevel());
        assertThat(response.getErrorMessage()).isNull();
    }

    @Test
    void createTask_mimoModeWithoutKeyWithDiffFallsBackToMockAndSucceeds() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/fallback");
        request.setPrNumber(9);
        request.setDiffText("diff --git a/src/App.tsx b/src/App.tsx\n+const password = request.query.password;\n");

        ReviewTaskResponse response = reviewTaskService.createTask(request);

        assertThat(response.getStatus().name()).isEqualTo("SUCCESS");
        assertThat(response.getIssues()).hasSize(3);
        assertThat(response.getIssues()).allMatch(issue -> issue.getSource() == IssueSource.MOCK);
        assertThat(response.getRiskLevel()).isEqualTo(response.getIssueSummary().getRiskLevel());
    }
}
