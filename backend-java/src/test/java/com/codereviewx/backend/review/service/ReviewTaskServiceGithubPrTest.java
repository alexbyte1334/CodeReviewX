package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.dto.CreateReviewTaskRequest;
import com.codereviewx.backend.review.dto.ReviewTaskResponse;
import com.codereviewx.backend.review.enums.ReviewMode;
import com.codereviewx.backend.review.enums.ReviewTaskStatus;
import com.codereviewx.backend.review.enums.ToolTraceStatus;
import com.codereviewx.backend.review.github.GithubPrDiff;
import com.codereviewx.backend.review.github.GithubPrDiffFile;
import com.codereviewx.backend.review.github.GithubPrDiffLoadResult;
import com.codereviewx.backend.review.github.GithubPrDiffLoader;
import com.codereviewx.backend.review.github.GithubPrMetadata;
import com.codereviewx.backend.review.github.GithubPrMetadataLoadResult;
import com.codereviewx.backend.review.github.GithubPrMetadataLoader;
import com.codereviewx.backend.review.persistence.entity.ReviewInputSnapshotEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewTaskEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewToolTraceEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewCommentPreviewRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewInputSnapshotRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewIssueRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewProviderTraceRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewRunRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewTaskRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewToolTraceRepository;
import com.codereviewx.backend.review.pipeline.provider.mimo.TestMiMoAgentResponses;
import com.codereviewx.backend.review.pipeline.provider.mimo.ReviewPromptBuilder;
import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

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

    @MockBean
    private GithubPrDiffLoader githubPrDiffLoader;

    @MockBean
    private XiaomiMiMoClient xiaomiMiMoClient;

    @BeforeEach
    void setUp() {
        TestMiMoAgentResponses.stubSuccessfulReview(xiaomiMiMoClient);
        commentPreviewRepository.deleteAll();
        toolTraceRepository.deleteAll();
        providerTraceRepository.deleteAll();
        inputSnapshotRepository.deleteAll();
        reviewRunRepository.deleteAll();
        reviewIssueRepository.deleteAll();
        reviewTaskRepository.deleteAll();
    }

    @Test
    void createTask_githubPr_metadataSuccessRunsMimoReviewAndBuildsPreviews() {
        when(githubPrMetadataLoader.load("https://github.com/example/repo", 18))
                .thenReturn(GithubPrMetadataLoadResult.success(metadata()));
        when(githubPrDiffLoader.load(metadata()))
                .thenReturn(GithubPrDiffLoadResult.success(diff()));

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
        assertThat(response.getTraceSummary().getToolCount()).isEqualTo(7);
        assertThat(response.getTraceSummary().getFailedToolCount()).isEqualTo(0);
        assertThat(response.getTraceSummary().isProviderFallback()).isFalse();
        assertThat(response.getCommentPreviewCount()).isEqualTo(3);

        List<ReviewToolTraceEntity> traces =
                toolTraceRepository.findByReviewRunIdOrderBySequenceNumberAsc(response.getLatestRunId());
        assertThat(traces).hasSize(7);
        assertThat(traces.get(0).getToolName()).isEqualTo(GithubPrMetadataLoader.TOOL_NAME);
        assertThat(traces.get(0).getStatus()).isEqualTo(ToolTraceStatus.SUCCESS);
        assertThat(traces.get(0).getInputSummary()).contains("tokenConfigured=true");
        assertThat(traces.get(0).getInputSummary()).doesNotContain("test-token");
        assertThat(traces.get(0).getInputSummary()).doesNotContainIgnoringCase("Authorization");
        assertThat(traces.get(1).getToolName()).isEqualTo(GithubPrDiffLoader.TOOL_NAME);
        assertThat(traces.get(1).getStatus()).isEqualTo(ToolTraceStatus.SUCCESS);
        assertThat(traces.get(1).getOutputSummary()).contains("fileCount=1");
        assertThat(traces.get(1).getInputSummary()).doesNotContain("test-token");
        assertThat(traces.get(2).getToolName()).isEqualTo("mimo.ai1.plan");
        assertThat(traces.get(3).getToolName()).isEqualTo("mimo.ai2.execute");
        assertThat(traces.get(4).getToolName()).isEqualTo("mimo.ai1.gate");
        assertThat(traces.get(5).getToolName()).isEqualTo("issue.generate");
        assertThat(traces.get(6).getToolName()).isEqualTo("comment.preview.build");
        assertThat(traces.subList(2, 7))
                .allSatisfy(trace -> {
                    assertThat(trace.getStatus()).isEqualTo(ToolTraceStatus.SUCCESS);
                    assertThat(trace.getInputSummary()).isNull();
                    assertThat(trace.getOutputSummary()).doesNotContain("request.query.password");
                    assertThat(trace.getOutputSummary()).doesNotContain("test-token");
                    assertThat(trace.getDurationMs()).isNotNull();
                });

        ReviewInputSnapshotEntity snapshot = inputSnapshotRepository.findByReviewRunId(response.getLatestRunId())
                .orElseThrow();
        assertThat(snapshot.getOwner()).isEqualTo("example");
        assertThat(snapshot.getRepo()).isEqualTo("repo");
        assertThat(snapshot.getPrTitle()).isEqualTo("Improve review flow");
        assertThat(snapshot.getPrAuthor()).isEqualTo("octocat");
        assertThat(snapshot.getDiffTruncated()).isFalse();
        assertThat(snapshot.getSnapshotJson()).contains("\"owner\":\"example\"");
        assertThat(snapshot.getSnapshotJson()).contains("\"diffFileCount\":1");
        assertThat(snapshot.getSnapshotJson()).contains("\"filename\":\"src/App.ts\"");
        assertThat(snapshot.getSnapshotJson()).doesNotContain("request.query.password");
        assertThat(snapshot.getSnapshotJson()).doesNotContain("test-token");
        assertThat(snapshot.getSnapshotJson()).doesNotContainIgnoringCase("Authorization");

        ReviewTaskEntity task = reviewTaskRepository.findById(response.getId()).orElseThrow();
        assertThat(task.getDiffText()).isNull();

        assertThat(providerTraceRepository.findByReviewRunId(response.getLatestRunId()))
                .isPresent()
                .get()
                .satisfies(trace -> {
                    assertThat(trace.getRequestedProvider()).isEqualTo("mimo");
                    assertThat(trace.getProviderUsed()).isEqualTo("mimo");
                    assertThat(trace.getProviderHit()).isTrue();
                    assertThat(trace.getFindingCount()).isEqualTo(3);
                });
        assertThat(commentPreviewRepository.findByReviewRunIdOrderByIdAsc(response.getLatestRunId()))
                .hasSize(3)
                .allSatisfy(preview -> assertThat(preview.getDraftBody()).contains("Suggestion:"));

        ArgumentCaptor<String> executorPrompt = ArgumentCaptor.forClass(String.class);
        verify(xiaomiMiMoClient).complete(eq(ReviewPromptBuilder.EXECUTOR_SYSTEM_PROMPT),
                executorPrompt.capture(), eq("test-executor-key"));
        assertThat(executorPrompt.getValue()).contains("request.query.password");
        assertThat(executorPrompt.getValue()).contains("--- PR DIFF START ---");
    }

    @Test
    void createTask_githubPr_diffFailurePersistsFailedDiffTrace() {
        when(githubPrMetadataLoader.load("https://github.com/example/repo", 18))
                .thenReturn(GithubPrMetadataLoadResult.success(metadata()));
        when(githubPrDiffLoader.load(metadata()))
                .thenReturn(GithubPrDiffLoadResult.failure(
                        com.codereviewx.backend.review.ReviewErrorCodes.GITHUB_DIFF_TOO_LARGE,
                        GithubPrDiffLoader.DIFF_TOO_LARGE_MESSAGE
                ));

        ReviewTaskResponse response = service.createTask(githubPrRequest());

        assertThat(response.getStatus()).isEqualTo(ReviewTaskStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo(com.codereviewx.backend.review.ReviewErrorCodes.GITHUB_DIFF_TOO_LARGE);
        assertThat(response.getIssues()).isEmpty();
        assertThat(inputSnapshotRepository.findByReviewRunId(response.getLatestRunId())).isEmpty();

        List<ReviewToolTraceEntity> traces =
                toolTraceRepository.findByReviewRunIdOrderBySequenceNumberAsc(response.getLatestRunId());
        assertThat(traces).hasSize(2);
        assertThat(traces.get(0).getStatus()).isEqualTo(ToolTraceStatus.SUCCESS);
        assertThat(traces.get(1).getToolName()).isEqualTo(GithubPrDiffLoader.TOOL_NAME);
        assertThat(traces.get(1).getStatus()).isEqualTo(ToolTraceStatus.FAILED);
        assertThat(traces.get(1).getErrorCode())
                .isEqualTo(com.codereviewx.backend.review.ReviewErrorCodes.GITHUB_DIFF_TOO_LARGE);
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

    private static GithubPrDiff diff() {
        return new GithubPrDiff(
                "diff --git a/src/App.ts b/src/App.ts\n"
                        + "@@ -1 +1 @@\n"
                        + "+const password = request.query.password;\n",
                1,
                92,
                false,
                List.of(new GithubPrDiffFile(
                        "src/App.ts",
                        "modified",
                        1,
                        0,
                        1,
                        51,
                        false
                ))
        );
    }
}
