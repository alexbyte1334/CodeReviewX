package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.dto.CreateReviewTaskRequest;
import com.codereviewx.backend.review.dto.ReviewTaskResponse;
import com.codereviewx.backend.review.enums.ReviewMode;
import com.codereviewx.backend.review.enums.ReviewTaskStatus;
import com.codereviewx.backend.review.enums.ToolTraceStatus;
import com.codereviewx.backend.review.github.GithubPrMetadata;
import com.codereviewx.backend.review.github.GithubPrMetadataLoadResult;
import com.codereviewx.backend.review.github.GithubPrMetadataLoader;
import com.codereviewx.backend.review.persistence.entity.ReviewInputSnapshotEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewToolTraceEntity;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = "codereviewx.github.token=test-token")
class ReviewTaskServiceGithubPrTest {

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

    @MockBean
    private GithubPrMetadataLoader githubPrMetadataLoader;

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

    @Test
    void createTask_githubPr_metadataSuccessRunsMockReviewAndBuildsPreviews() {
        when(githubPrMetadataLoader.load("https://github.com/example/repo", 18))
                .thenReturn(GithubPrMetadataLoadResult.success(metadata()));

        ReviewTaskResponse response = service.createTask(githubPrRequest());

        assertThat(response.getStatus()).isEqualTo(ReviewTaskStatus.SUCCESS);
        assertThat(response.getReviewMode()).isEqualTo(ReviewMode.GITHUB_PR);
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getErrorMessage()).isNull();
        assertThat(response.getIssues()).hasSize(3);
        assertThat(response.getLatestRunId()).isNotNull();
        assertThat(response.getIngestionSummary()).isNotNull();
        assertThat(response.getIngestionSummary().getHeadSha()).isEqualTo("head-sha");
        assertThat(response.getIngestionSummary().getBaseSha()).isEqualTo("base-sha");
        assertThat(response.getIngestionSummary().getChangedFiles()).isEqualTo(3);
        assertThat(response.getTraceSummary().getToolCount()).isEqualTo(1);
        assertThat(response.getTraceSummary().getFailedToolCount()).isEqualTo(0);
        assertThat(response.getTraceSummary().isProviderFallback()).isTrue();
        assertThat(response.getCommentPreviewCount()).isEqualTo(3);

        List<ReviewToolTraceEntity> traces =
                toolTraceRepository.findByReviewRunIdOrderBySequenceNumberAsc(response.getLatestRunId());
        assertThat(traces).hasSize(1);
        assertThat(traces.get(0).getToolName()).isEqualTo(GithubPrMetadataLoader.TOOL_NAME);
        assertThat(traces.get(0).getStatus()).isEqualTo(ToolTraceStatus.SUCCESS);
        assertThat(traces.get(0).getInputSummary()).contains("tokenConfigured=true");
        assertThat(traces.get(0).getInputSummary()).doesNotContain("test-token");
        assertThat(traces.get(0).getInputSummary()).doesNotContainIgnoringCase("Authorization");

        ReviewInputSnapshotEntity snapshot = inputSnapshotRepository.findByReviewRunId(response.getLatestRunId())
                .orElseThrow();
        assertThat(snapshot.getOwner()).isEqualTo("example");
        assertThat(snapshot.getRepo()).isEqualTo("repo");
        assertThat(snapshot.getPrTitle()).isEqualTo("Improve review flow");
        assertThat(snapshot.getPrAuthor()).isEqualTo("octocat");
        assertThat(snapshot.getSnapshotJson()).contains("\"owner\":\"example\"");
        assertThat(snapshot.getSnapshotJson()).doesNotContain("test-token");
        assertThat(snapshot.getSnapshotJson()).doesNotContainIgnoringCase("Authorization");

        assertThat(providerTraceRepository.findByReviewRunId(response.getLatestRunId()))
                .isPresent()
                .get()
                .satisfies(trace -> {
                    assertThat(trace.getRequestedProvider()).isEqualTo("mimo");
                    assertThat(trace.getProviderUsed()).isEqualTo("mock");
                    assertThat(trace.getProviderHit()).isFalse();
                    assertThat(trace.getFindingCount()).isEqualTo(3);
                });
        assertThat(commentPreviewRepository.findByReviewRunIdOrderByIdAsc(response.getLatestRunId()))
                .hasSize(3)
                .allSatisfy(preview -> assertThat(preview.getDraftBody()).contains("Suggestion:"));
    }

    private static CreateReviewTaskRequest githubPrRequest() {
        CreateReviewTaskRequest request = new CreateReviewTaskRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setPrNumber(18);
        return request;
    }

    private static GithubPrMetadata metadata() {
        return new GithubPrMetadata(
                "example",
                "repo",
                18,
                "Improve review flow",
                "octocat",
                "main",
                "feature",
                "base-sha",
                "head-sha",
                "open",
                "2026-06-26T01:00:00Z",
                "2026-06-26T02:00:00Z",
                3,
                120,
                40
        );
    }
}
