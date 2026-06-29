package com.codereviewx.backend.review.pipeline;

import com.codereviewx.backend.review.ReviewErrorCodes;
import com.codereviewx.backend.review.dto.CreateReviewTaskRequest;
import com.codereviewx.backend.review.dto.ReviewTaskResponse;
import com.codereviewx.backend.review.enums.ReviewTaskStatus;
import com.codereviewx.backend.review.service.ReviewTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "codereviewx.ai.mimo.planner-api-key=",
        "codereviewx.ai.mimo.executor-api-key="
})
class ReviewPipelineFailFastIntegrationTest {

    @Autowired
    private ReviewTaskService reviewTaskService;

    @BeforeEach
    void setUp(@Autowired com.codereviewx.backend.review.persistence.repository.ReviewCommentPreviewRepository commentPreviewRepository,
               @Autowired com.codereviewx.backend.review.persistence.repository.ReviewToolTraceRepository toolTraceRepository,
               @Autowired com.codereviewx.backend.review.persistence.repository.ReviewProviderTraceRepository providerTraceRepository,
               @Autowired com.codereviewx.backend.review.persistence.repository.ReviewInputSnapshotRepository inputSnapshotRepository,
               @Autowired com.codereviewx.backend.review.persistence.repository.ReviewIssueRepository issueRepository,
               @Autowired com.codereviewx.backend.review.persistence.repository.ReviewRunRepository runRepository,
               @Autowired com.codereviewx.backend.review.persistence.repository.ReviewTaskRepository taskRepository) {
        commentPreviewRepository.deleteAll();
        toolTraceRepository.deleteAll();
        providerTraceRepository.deleteAll();
        inputSnapshotRepository.deleteAll();
        issueRepository.deleteAll();
        runRepository.deleteAll();
        taskRepository.deleteAll();
    }

    @Test
    void createTask_mimoWithoutRoleKeysFailsFastWithoutMockFallback() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/fallback");
        request.setPrNumber(9);
        request.setDiffText("diff --git a/a.txt b/a.txt\n");

        ReviewTaskResponse response = reviewTaskService.createTask(request);

        assertThat(response.getStatus()).isEqualTo(ReviewTaskStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo(ReviewErrorCodes.MIMO_AUTH_MISSING);
        assertThat(response.getRequestedProvider()).isEqualTo("mimo");
        assertThat(response.getProviderUsed()).isNull();
        assertThat(response.getProviderHit()).isFalse();
        assertThat(response.getIssues()).isEmpty();
        assertThat(response.getIssueSummary().getTotalIssues()).isZero();
    }
}
