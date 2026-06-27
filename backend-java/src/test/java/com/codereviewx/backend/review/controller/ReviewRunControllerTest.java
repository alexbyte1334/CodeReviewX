package com.codereviewx.backend.review.controller;

import com.codereviewx.backend.review.enums.PublishStatus;
import com.codereviewx.backend.review.enums.ReviewMode;
import com.codereviewx.backend.review.enums.ReviewRunStatus;
import com.codereviewx.backend.review.enums.ToolTraceStatus;
import com.codereviewx.backend.review.github.GithubPrCommentPublishRequest;
import com.codereviewx.backend.review.github.GithubPrCommentPublishResult;
import com.codereviewx.backend.review.github.GithubPrCommentPublisher;
import com.codereviewx.backend.review.persistence.entity.ReviewCommentPreviewEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewInputSnapshotEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewProviderTraceEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewRunEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewTaskEntity;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.mockito.ArgumentCaptor;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

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

    @Autowired
    private ReviewIssueRepository reviewIssueRepository;

    @Autowired
    private ReviewTaskRepository reviewTaskRepository;

    @MockBean
    private GithubPrCommentPublisher githubPrCommentPublisher;

    private Long seededRunId;
    private Long seededPreviewId;

    @BeforeEach
    void setUp() {
        commentPreviewRepository.deleteAll();
        toolTraceRepository.deleteAll();
        providerTraceRepository.deleteAll();
        inputSnapshotRepository.deleteAll();
        reviewRunRepository.deleteAll();
        reviewIssueRepository.deleteAll();
        reviewTaskRepository.deleteAll();
        SeededRun seededRun = seedStage2Run();
        seededRunId = seededRun.runId();
        seededPreviewId = seededRun.previewId();
    }

    @Test
    void getRun_returnsBoundedSummary() throws Exception {
        mockMvc.perform(get("/api/review-runs/" + seededRunId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(seededRunId.intValue())))
                .andExpect(jsonPath("$.data.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.reviewMode", is("GITHUB_PR")))
                .andExpect(jsonPath("$.data.inputSnapshotSummary.headSha", is("abc123")))
                .andExpect(jsonPath("$.data.providerSummary.providerUsed", is("mimo")))
                .andExpect(jsonPath("$.data.inputSnapshotSummary.snapshotJson").doesNotExist())
                .andExpect(jsonPath("$.data.providerSummary.inputSummary").doesNotExist());
    }

    @Test
    void getTrace_returnsToolTimelineWithoutSecrets() throws Exception {
        mockMvc.perform(get("/api/review-runs/" + seededRunId + "/trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].toolName", is("github.pr.metadata.load")))
                .andExpect(jsonPath("$.data.items[0].outputSummary", notNullValue()))
                .andExpect(jsonPath("$.data.items[0].inputSummary").doesNotExist());
    }

    @Test
    void getCommentPreviews_returnsNotPublishedOnly() throws Exception {
        mockMvc.perform(get("/api/review-runs/" + seededRunId + "/comment-previews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].issueId", is("ISSUE-1")))
                .andExpect(jsonPath("$.data.items[0].publishStatus", is("NOT_PUBLISHED")))
                .andExpect(jsonPath("$.data.items[0].draftBody", notNullValue()));
    }

    @Test
    void updateCommentPreviewSelection_marksSelectedPreview() throws Exception {
        mockMvc.perform(patch("/api/review-runs/" + seededRunId + "/comment-previews/selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedPreviewIds\":[" + seededPreviewId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].selectedForPublish", is(true)));
    }

    @Test
    void publishCommentPreview_rejectsUnselectedPreview() throws Exception {
        mockMvc.perform(post("/api/review-runs/" + seededRunId
                        + "/comment-previews/" + seededPreviewId + "/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Comment preview must be selected before publishing")));
    }

    @Test
    void publishCommentPreview_requiresHumanConfirmation() throws Exception {
        selectSeededPreview();

        mockMvc.perform(post("/api/review-runs/" + seededRunId
                        + "/comment-previews/" + seededPreviewId + "/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("confirmed")));
    }

    @Test
    void publishCommentPreview_successPersistsGithubCommentId() throws Exception {
        selectSeededPreview();
        when(githubPrCommentPublisher.publish(any()))
                .thenReturn(GithubPrCommentPublishResult.success(98765L));

        mockMvc.perform(post("/api/review-runs/" + seededRunId
                        + "/comment-previews/" + seededPreviewId + "/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishStatus", is("PUBLISHED")))
                .andExpect(jsonPath("$.data.githubCommentId", is(98765)))
                .andExpect(jsonPath("$.data.publishErrorMessage").doesNotExist());

        ReviewCommentPreviewEntity preview = commentPreviewRepository.findById(seededPreviewId).orElseThrow();
        assertThat(preview.getPublishStatus()).isEqualTo(PublishStatus.PUBLISHED);
        assertThat(preview.getGithubCommentId()).isEqualTo(98765L);
        assertThat(preview.getPublishedAt()).isNotNull();

        ArgumentCaptor<GithubPrCommentPublishRequest> requestCaptor =
                ArgumentCaptor.forClass(GithubPrCommentPublishRequest.class);
        verify(githubPrCommentPublisher).publish(requestCaptor.capture());
        assertThat(requestCaptor.getValue().owner()).isEqualTo("example");
        assertThat(requestCaptor.getValue().repo()).isEqualTo("repo");
        assertThat(requestCaptor.getValue().prNumber()).isEqualTo(42);
        assertThat(requestCaptor.getValue().commitId()).isEqualTo("abc123");
        assertThat(requestCaptor.getValue().path()).isEqualTo("src/App.java");
        assertThat(requestCaptor.getValue().line()).isEqualTo(42);
        assertThat(requestCaptor.getValue().body()).contains("checking null");
    }

    @Test
    void publishSelectedCommentPreviews_failurePersistsErrorMessage() throws Exception {
        selectSeededPreview();
        when(githubPrCommentPublisher.publish(any()))
                .thenReturn(GithubPrCommentPublishResult.failure("GitHub rejected the comment target."));

        mockMvc.perform(post("/api/review-runs/" + seededRunId + "/comment-previews/publish-selected")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].publishStatus", is("FAILED")))
                .andExpect(jsonPath("$.data.items[0].publishErrorMessage", is("GitHub rejected the comment target.")));
    }

    @Test
    void getRun_notFound_returnsError() throws Exception {
        mockMvc.perform(get("/api/review-runs/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Review run not found")));
    }

    private void selectSeededPreview() {
        ReviewCommentPreviewEntity preview = commentPreviewRepository.findById(seededPreviewId).orElseThrow();
        preview.setSelectedForPublish(true);
        preview.setUpdatedAt(LocalDateTime.now());
        commentPreviewRepository.save(preview);
    }

    private SeededRun seedStage2Run() {
        LocalDateTime now = LocalDateTime.now();

        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setRepoUrl("https://github.com/example/repo");
        task.setPrNumber(42);
        task.setStatus(com.codereviewx.backend.review.enums.ReviewTaskStatus.SUCCESS);
        task.setReviewMode(ReviewMode.GITHUB_PR);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        ReviewTaskEntity savedTask = reviewTaskRepository.save(task);

        ReviewRunEntity run = new ReviewRunEntity();
        run.setReviewTaskId(savedTask.getId());
        run.setRunNumber(1);
        run.setReviewMode(ReviewMode.GITHUB_PR);
        run.setStatus(ReviewRunStatus.SUCCESS);
        run.setRequestedProvider("mimo");
        run.setProviderUsed("mimo");
        run.setProviderHit(true);
        run.setStartedAt(now);
        run.setFinishedAt(now);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        ReviewRunEntity savedRun = reviewRunRepository.save(run);

        savedTask.setLatestRunId(savedRun.getId());
        reviewTaskRepository.save(savedTask);

        ReviewInputSnapshotEntity snapshot = new ReviewInputSnapshotEntity();
        snapshot.setReviewRunId(savedRun.getId());
        snapshot.setRepoUrl(savedTask.getRepoUrl());
        snapshot.setOwner("example");
        snapshot.setRepo("repo");
        snapshot.setPrNumber(42);
        snapshot.setBaseSha("def456");
        snapshot.setHeadSha("abc123");
        snapshot.setChangedFiles(8);
        snapshot.setAdditions(120);
        snapshot.setDeletions(40);
        snapshot.setDiffTruncated(false);
        snapshot.setContextTruncated(false);
        snapshot.setSnapshotJson("{\"sanitized\":true,\"rawDiff\":\"should not appear in API\"}");
        snapshot.setCreatedAt(now);
        inputSnapshotRepository.save(snapshot);

        ReviewToolTraceEntity toolTrace = new ReviewToolTraceEntity();
        toolTrace.setReviewRunId(savedRun.getId());
        toolTrace.setSequenceNumber(1);
        toolTrace.setToolName("github.pr.metadata.load");
        toolTrace.setStatus(ToolTraceStatus.SUCCESS);
        toolTrace.setInputSummary("Authorization: Bearer ghp_secret should not be returned");
        toolTrace.setOutputSummary("Loaded PR title, head/base refs, and changed file counts.");
        toolTrace.setStartedAt(now);
        toolTrace.setFinishedAt(now);
        toolTrace.setCreatedAt(now);
        toolTraceRepository.save(toolTrace);

        ReviewProviderTraceEntity providerTrace = new ReviewProviderTraceEntity();
        providerTrace.setReviewRunId(savedRun.getId());
        providerTrace.setRequestedProvider("mimo");
        providerTrace.setProviderUsed("mimo");
        providerTrace.setProviderHit(true);
        providerTrace.setModelName("mimo-v2.5-pro");
        providerTrace.setInputSummary("raw prompt should not be returned");
        providerTrace.setOutputSummary("3 findings normalized from provider response.");
        providerTrace.setFindingCount(3);
        providerTrace.setFallbackReason(null);
        providerTrace.setStartedAt(now);
        providerTrace.setFinishedAt(now);
        providerTrace.setCreatedAt(now);
        providerTraceRepository.save(providerTrace);

        ReviewCommentPreviewEntity preview = new ReviewCommentPreviewEntity();
        preview.setReviewRunId(savedRun.getId());
        preview.setIssueKey("ISSUE-1");
        preview.setFilePath("src/App.java");
        preview.setLineNumber(42);
        preview.setDraftBody("Consider checking null before dereference.");
        preview.setSelectedForPublish(false);
        preview.setPublishStatus(PublishStatus.NOT_PUBLISHED);
        preview.setCreatedAt(now);
        preview.setUpdatedAt(now);
        ReviewCommentPreviewEntity savedPreview = commentPreviewRepository.save(preview);

        return new SeededRun(savedRun.getId(), savedPreview.getId());
    }

    private record SeededRun(Long runId, Long previewId) {
    }
}
