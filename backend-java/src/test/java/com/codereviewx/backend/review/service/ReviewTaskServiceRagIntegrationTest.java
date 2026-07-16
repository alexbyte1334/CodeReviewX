package com.codereviewx.backend.review.service;

import com.codereviewx.backend.rag.retrieval.RagContextAssembler;
import com.codereviewx.backend.rag.retrieval.RagEvidence;
import com.codereviewx.backend.rag.retrieval.RagEvidenceBundle;
import com.codereviewx.backend.review.enums.IssueCategory;
import com.codereviewx.backend.review.enums.IssueSeverity;
import com.codereviewx.backend.review.enums.IssueSource;
import com.codereviewx.backend.review.enums.IssueStatus;
import com.codereviewx.backend.review.github.GithubPrDiff;
import com.codereviewx.backend.review.github.GithubPrDiffFile;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import com.codereviewx.backend.review.dto.*;
import com.codereviewx.backend.review.github.*;
import com.codereviewx.backend.review.persistence.repository.*;
import com.codereviewx.backend.review.persistence.entity.ReviewToolTraceEntity;
import com.codereviewx.backend.review.pipeline.provider.mimo.*;
import com.codereviewx.backend.rag.service.RagReviewContextFacade;
import com.codereviewx.backend.rag.service.RagIndexService;
import com.codereviewx.backend.rag.service.RagIndexResolution;
import com.codereviewx.backend.rag.retrieval.HybridRagRetrievalService;
import com.codereviewx.backend.rag.retrieval.RerankClient;
import com.codereviewx.backend.rag.retrieval.RerankedChunk;
import com.codereviewx.backend.rag.persistence.ReviewIssueEvidenceStore;
import com.codereviewx.backend.rag.indexing.RagIndexWorker;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
@TestPropertySource(properties = {
        "codereviewx.github.token=test-token",
        "codereviewx.rag.enabled=true",
        "codereviewx.rag.embedding-base-url=http://127.0.0.1/embedding",
        "codereviewx.rag.embedding-api-key=test-embedding-key",
        "codereviewx.rag.rerank-base-url=http://127.0.0.1/rerank",
        "codereviewx.rag.rerank-api-key=test-rerank-key"
})
class ReviewTaskServiceRagIntegrationTest {

    @Autowired private ReviewEvidenceValidator validator;
    @Autowired private ReviewTaskService service;
    @Autowired private RagReviewContextFacade ragFacade;
    @Autowired private ReviewToolTraceRepository toolTraceRepository;
    @Autowired private ReviewCommentPreviewRepository previewRepository;
    @Autowired private ReviewProviderTraceRepository providerTraceRepository;
    @Autowired private ReviewInputSnapshotRepository snapshotRepository;
    @Autowired private ReviewRunRepository runRepository;
    @Autowired private ReviewIssueRepository issueRepository;
    @Autowired private ReviewTaskRepository taskRepository;
    @MockBean private GithubPrMetadataLoader metadataLoader;
    @MockBean private GithubPrDiffLoader diffLoader;
    @MockBean private XiaomiMiMoClient mimoClient;
    @MockBean private RagIndexService ragIndexService;
    @MockBean private HybridRagRetrievalService retrievalService;
    @MockBean private RerankClient rerankClient;
    @MockBean private ReviewIssueEvidenceStore evidenceStore;
    @MockBean private RagIndexWorker ragIndexWorker;
    private final GithubPrDiff diff = new GithubPrDiff(
            "diff --git a/src/App.ts b/src/App.ts\n@@ -9,2 +10,3 @@\n+const value = risky();\n",
            1, 80, false, List.of(new GithubPrDiffFile("src/App.ts", "modified", 1, 0, 1, 40, false)));
    private final RagEvidenceBundle bundle = new RagEvidenceBundle(
            List.of(new RagEvidence("C2", "src/App.ts", 8, 22, "head-sha", "const value = risky();", 0.9)),
            "evidence", RagEvidenceBundle.DegradedReason.NONE, RagContextAssembler.RetrievalHealth.HEALTHY);

    @BeforeEach
    void clean() {
        previewRepository.deleteAll(); toolTraceRepository.deleteAll(); providerTraceRepository.deleteAll();
        snapshotRepository.deleteAll(); runRepository.deleteAll(); issueRepository.deleteAll(); taskRepository.deleteAll();
    }

    @Test
    void githubPrReadyRunsExactGroundedTraceWithoutLegacyIndexAndExcludesUngroundedPreview() {
        assertThat(org.mockito.Mockito.mockingDetails(ragFacade).isMock()).isFalse();
        GithubPrMetadata metadata = new GithubPrMetadata("example", "repo", 18, "Review", "octocat", "main",
                "feature", "b".repeat(40), "a".repeat(40), "open", "x", "x", 1, 1, 1);
        GithubPrDiff reviewDiff = new GithubPrDiff("diff --git a/src/App.ts b/src/App.ts\n@@ -1 +1 @@\n+const password = request.query.password;",
                1, 80, false, List.of(new GithubPrDiffFile("src/App.ts", "modified", 1, 0, 1, 20, false)));
        when(metadataLoader.load(anyString(), eq(18))).thenReturn(GithubPrMetadataLoadResult.success(metadata));
        when(diffLoader.load(metadata)).thenReturn(GithubPrDiffLoadResult.success(reviewDiff));
        when(ragIndexService.ensureIndexed(metadata)).thenReturn(new RagIndexResolution(
                1L, 2L, metadata.headSha(), RagIndexResolution.Status.READY));
        HybridRagRetrievalService.Match match = new HybridRagRetrievalService.Match(77L, "src/App.ts", "TS",
                "password", 1, 2, "source-hash", "supporting source", 1.25, 0.9);
        when(retrievalService.retrieve(any())).thenReturn(new HybridRagRetrievalService.Result(
                HybridRagRetrievalService.Status.READY, 3L, 1, 1, List.of(match),
                RagContextAssembler.RetrievalHealth.HEALTHY));
        when(rerankClient.rerank(anyString(), anyList())).thenAnswer(invocation -> {
            List<com.codereviewx.backend.rag.retrieval.RerankCandidate> candidates = invocation.getArgument(1);
            return candidates.stream().map(candidate -> new RerankedChunk(candidate, 0.95)).toList();
        });
        when(mimoClient.complete(eq(ReviewPromptBuilder.PLANNER_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn(TestMiMoAgentResponses.taskPlanJson());
        when(mimoClient.complete(eq(ReviewPromptBuilder.EXECUTOR_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn("""
                        {"summary":"review","findings":[
                          {"severity":"HIGH","category":"BUG","filePath":"src/App.ts","startLine":1,"endLine":1,
                           "title":"grounded","description":"A grounded issue.","recommendation":"fix","evidenceChunkIds":["C1"]},
                          {"severity":"LOW","category":"BUG","filePath":"src/Other.ts","startLine":1,"endLine":1,
                           "title":"ungrounded","description":"Wrong path.","recommendation":"fix","evidenceChunkIds":["C1"]}
                        ]}
                        """);
        when(mimoClient.complete(eq(ReviewPromptBuilder.GATEKEEPER_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn(TestMiMoAgentResponses.approvedGateJson());
        CreateReviewTaskRequest request = new CreateReviewTaskRequest(); request.setRepoUrl("https://github.com/example/repo"); request.setPrNumber(18);

        ReviewTaskResponse response = service.createTask(request);

        List<ReviewToolTraceEntity> traces = toolTraceRepository.findByReviewRunIdOrderBySequenceNumberAsc(response.getLatestRunId());
        assertThat(traces).extracting(ReviewToolTraceEntity::getToolName).containsExactly(
                "github.pr.metadata.load", "github.pr.diff.load", "rag.index.ensure", "rag.query.build",
                "rag.retrieve.hybrid", "rag.rerank", "rag.context.assemble", "static.analysis.findings",
                "mimo.ai1.plan", "mimo.ai2.execute", "mimo.ai1.gate", "issue.generate", "evidence.validate",
                "comment.preview.build");
        assertThat(traces).extracting(ReviewToolTraceEntity::getToolName).doesNotContain("repository.context.index");
        assertThat(response.getIssues()).hasSize(2).anySatisfy(issue -> assertThat(issue.getTitle()).isEqualTo("grounded"))
                .anySatisfy(issue -> assertThat(issue.getSource()).isEqualTo(com.codereviewx.backend.review.enums.IssueSource.SEMGREP));
        assertThat(response.getIssues()).noneSatisfy(issue -> assertThat(issue.getTitle()).isEqualTo("ungrounded"));
        assertThat(response.getCommentPreviewCount()).isEqualTo(2);
        assertThat(response.toString()).doesNotContain("supporting source", "source-hash");
    }

    @Test
    void acceptsFindingGroundedByKnownMatchingEvidenceAndChangedLine() {
        assertThat(validator.isGrounded(finding("src/App.ts", 10, List.of("C2")), bundle, diff)).isTrue();
    }

    @Test
    void rejectsMissingUnknownConflictingAndOutOfRangeEvidence() {
        assertThat(validator.isGrounded(finding("src/App.ts", 10, List.of()), bundle, diff)).isFalse();
        assertThat(validator.isGrounded(finding("src/App.ts", 10, List.of("C9")), bundle, diff)).isFalse();
        assertThat(validator.isGrounded(finding("src/Other.ts", 10, List.of("C2")), bundle, diff)).isFalse();
        assertThat(validator.isGrounded(finding("src/App.ts", 80, List.of("C2")), bundle, diff)).isFalse();
        assertThat(validator.isGrounded(finding("src/App.ts", 10, java.util.Arrays.asList("C2", null)), bundle, diff)).isFalse();
        assertThat(validator.isGrounded(finding("src/App.ts", 10, List.of("C2", "C2")), bundle, diff)).isFalse();
    }

    @Test
    void groundsOnlyExactFileNewSideContextAndAddedLinesAcrossFindingRange() {
        GithubPrDiff multi = new GithubPrDiff("""
                diff --git a/src/A.java b/src/A.java
                --- a/src/A.java
                +++ b/src/A.java
                @@ -10,3 +10,3 @@
                 context
                -deleted
                +added
                 tail
                diff --git a/src/B.java b/src/B.java
                --- a/src/B.java
                +++ b/src/B.java
                @@ -40 +40 @@
                +other
                """, 2, 200, false, List.of());
        RagEvidenceBundle evidence = bundle("C1", "src/A.java", 1, 50);
        assertThat(validator.isGrounded(finding("src/A.java", 10, 12, List.of("C1")), evidence, multi)).isTrue();
        assertThat(validator.isGrounded(finding("src/A.java", 10, 40, List.of("C1")), evidence, multi)).isFalse();
        assertThat(validator.isGrounded(finding("src/A.java", 40, 40, List.of("C1")), evidence, multi)).isFalse();
    }

    @Test
    void rejectsZeroCountDeletedHunkAndSupportsPathsWithSpaces() {
        GithubPrDiff special = new GithubPrDiff("""
                diff --git a/src/Old.java b/src/Old.java
                --- a/src/Old.java
                +++ /dev/null
                @@ -5,2 +5,0 @@
                -gone
                -gone too
                diff --git a/src/space name.java b/src/space name.java
                --- a/src/space name.java
                +++ b/src/space name.java
                @@ -1 +1 @@
                +kept
                """, 2, 200, false, List.of());
        assertThat(validator.isGrounded(finding("src/Old.java", 5, 5, List.of("C1")),
                bundle("C1", "src/Old.java", 1, 10), special)).isFalse();
        assertThat(validator.isGrounded(finding("src/space name.java", 1, 1, List.of("C1")),
                bundle("C1", "src/space name.java", 1, 2), special)).isTrue();
    }

    private ReviewFinding finding(String path, int line, List<String> labels) {
        return finding(path, line, line, labels);
    }

    private ReviewFinding finding(String path, int start, int end, List<String> labels) {
        return new ReviewFinding("MIMO-1", IssueSeverity.HIGH, IssueCategory.BUG, IssueSource.MIMO,
                IssueStatus.OPEN, path, start, end, "title", "description", "recommendation", labels);
    }

    private RagEvidenceBundle bundle(String label, String path, int start, int end) {
        return new RagEvidenceBundle(List.of(new RagEvidence(label, path, start, end, "head", "content", 0.9)),
                "evidence", RagEvidenceBundle.DegradedReason.NONE, RagContextAssembler.RetrievalHealth.HEALTHY);
    }
}
