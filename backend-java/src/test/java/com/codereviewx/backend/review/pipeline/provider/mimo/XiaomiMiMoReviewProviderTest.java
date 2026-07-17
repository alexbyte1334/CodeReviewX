package com.codereviewx.backend.review.pipeline.provider.mimo;

import com.codereviewx.backend.review.ReviewErrorCodes;
import com.codereviewx.backend.review.enums.IssueCategory;
import com.codereviewx.backend.review.enums.IssueSeverity;
import com.codereviewx.backend.review.enums.IssueSource;
import com.codereviewx.backend.review.enums.IssueStatus;
import com.codereviewx.backend.review.enums.ToolTraceStatus;
import com.codereviewx.backend.review.pipeline.ReviewContext;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import com.codereviewx.backend.rag.retrieval.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class XiaomiMiMoReviewProviderTest {

    private XiaomiMiMoClient client;
    private XiaomiMiMoProperties properties;
    private XiaomiMiMoReviewProvider provider;
    private ReviewContext context;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        ReviewPromptBuilder promptBuilder = new ReviewPromptBuilder();
        client = mock(XiaomiMiMoClient.class);
        properties = new XiaomiMiMoProperties();
        properties.setPlannerApiKey("planner-key");
        properties.setExecutorApiKey("executor-key");
        provider = new XiaomiMiMoReviewProvider(
                promptBuilder,
                client,
                properties,
                new MiMoAgentJsonParser(objectMapper),
                new MiMoIssueGenerator(),
                objectMapper
        );
        context = new ReviewContext(1L, "https://github.com/example/repo", 9, LocalDateTime.now(),
                "diff --git a/a.txt b/a.txt\n+const x = 1;\n");
    }

    @Test
    void review_runsPlannerExecutorGatekeeperAndGeneratesMimoFindings() {
        TestMiMoAgentResponses.stubSuccessfulReview(client);

        ReviewProviderResult result = provider.review(context);

        verify(client).complete(eq(ReviewPromptBuilder.PLANNER_SYSTEM_PROMPT), anyString(), eq("planner-key"));
        verify(client).complete(eq(ReviewPromptBuilder.EXECUTOR_SYSTEM_PROMPT), anyString(), eq("executor-key"));
        verify(client).complete(eq(ReviewPromptBuilder.GATEKEEPER_SYSTEM_PROMPT), anyString(), eq("planner-key"));
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getProviderName()).isEqualTo(XiaomiMiMoReviewProvider.PROVIDER_NAME);
        assertThat(result.getFindings()).hasSize(3);

        ReviewFinding finding = result.getFindings().get(0);
        assertThat(finding.getIssueKey()).isEqualTo("MIMO-ISSUE-1");
        assertThat(finding.getSeverity()).isEqualTo(IssueSeverity.HIGH);
        assertThat(finding.getCategory()).isEqualTo(IssueCategory.SECURITY);
        assertThat(finding.getSource()).isEqualTo(IssueSource.MIMO);
        assertThat(finding.getStatus()).isEqualTo(IssueStatus.OPEN);
        assertThat(context.getAgentSteps())
                .extracting("stepName")
                .containsExactly("mimo.ai1.plan", "mimo.ai2.execute", "mimo.ai1.gate", "issue.generate");
        assertThat(context.getAgentSteps())
                .allSatisfy(step -> {
                    assertThat(step.getStatus()).isEqualTo(ToolTraceStatus.SUCCESS);
                    assertThat(step.getOutputSummary()).doesNotContain("diff --git");
                    assertThat(step.getOutputSummary()).doesNotContain("planner-key");
                });
    }

    @Test
    void review_failsFastWhenRoleKeysMissing() {
        properties.setPlannerApiKey("");

        assertThatThrownBy(() -> provider.review(context))
                .isInstanceOf(MiMoAgentException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCodes.MIMO_AUTH_MISSING);
    }

    @Test
    void review_rejectsInvalidPlannerJson() {
        org.mockito.Mockito.when(client.complete(eq(ReviewPromptBuilder.PLANNER_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn("[]");

        assertThatThrownBy(() -> provider.review(context))
                .isInstanceOf(MiMoAgentException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCodes.MIMO_PLAN_INVALID);
        assertThat(context.getAgentSteps()).hasSize(1);
        assertThat(context.getAgentSteps().get(0).getStepName()).isEqualTo("mimo.ai1.plan");
        assertThat(context.getAgentSteps().get(0).getStatus()).isEqualTo(ToolTraceStatus.FAILED);
        assertThat(context.getAgentSteps().get(0).getErrorCode()).isEqualTo(ReviewErrorCodes.MIMO_PLAN_INVALID);
    }

    @Test
    void review_rejectsInvalidExecutorJson() {
        org.mockito.Mockito.when(client.complete(eq(ReviewPromptBuilder.PLANNER_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn(TestMiMoAgentResponses.taskPlanJson());
        org.mockito.Mockito.when(client.complete(eq(ReviewPromptBuilder.EXECUTOR_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn("[]");

        assertThatThrownBy(() -> provider.review(context))
                .isInstanceOf(MiMoAgentException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCodes.MIMO_REVIEW_INVALID);
    }

    @Test
    void review_rejectsGatekeeperRejection() {
        org.mockito.Mockito.when(client.complete(eq(ReviewPromptBuilder.PLANNER_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn(TestMiMoAgentResponses.taskPlanJson());
        org.mockito.Mockito.when(client.complete(eq(ReviewPromptBuilder.EXECUTOR_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn(TestMiMoAgentResponses.candidateReviewJson());
        org.mockito.Mockito.when(client.complete(eq(ReviewPromptBuilder.GATEKEEPER_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn("""
                        {
                          "approved": false,
                          "reason": "Ungrounded finding.",
                          "requiredChanges": ["Remove ungrounded finding."]
                        }
                        """);

        assertThatThrownBy(() -> provider.review(context))
                .isInstanceOf(MiMoAgentException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCodes.MIMO_GATE_REJECTED);
    }

    @Test
    void reviewRejectsMissingUnknownAndVerbatimEvidenceBeforeGatekeeper() {
        context = ragContext();
        for (String finding : List.of(
                findingJson("[]", "safe description"),
                findingJson("[\"C9\"]", "safe description"),
                findingJson("[\"C1\"]", "bounded evidence content with enough unique source tokens"))) {
            org.mockito.Mockito.reset(client);
            org.mockito.Mockito.when(client.complete(eq(ReviewPromptBuilder.PLANNER_SYSTEM_PROMPT), anyString(), anyString()))
                    .thenReturn(TestMiMoAgentResponses.taskPlanJson());
            org.mockito.Mockito.when(client.complete(eq(ReviewPromptBuilder.EXECUTOR_SYSTEM_PROMPT), anyString(), anyString()))
                    .thenReturn("{\"summary\":\"x\",\"findings\":[" + finding + "]}");
            assertThatThrownBy(() -> provider.review(context)).isInstanceOf(MiMoAgentException.class)
                    .extracting("errorCode").isEqualTo(ReviewErrorCodes.MIMO_REVIEW_INVALID);
            verify(client, never()).complete(eq(ReviewPromptBuilder.GATEKEEPER_SYSTEM_PROMPT), anyString(), anyString());
        }
    }

    @Test
    void reviewAcceptsValidMultipleKnownEvidenceLabelsAndPassesAllowedSetToGatekeeper() {
        context = ragContext();
        org.mockito.Mockito.when(client.complete(eq(ReviewPromptBuilder.PLANNER_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn(TestMiMoAgentResponses.taskPlanJson());
        org.mockito.Mockito.when(client.complete(eq(ReviewPromptBuilder.EXECUTOR_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn("{\"summary\":\"x\",\"findings\":[" + findingJson("[\"C1\",\"C2\"]", "safe description") + "]}");
        org.mockito.ArgumentCaptor<String> gatePrompt = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.when(client.complete(eq(ReviewPromptBuilder.GATEKEEPER_SYSTEM_PROMPT), gatePrompt.capture(), anyString()))
                .thenReturn(TestMiMoAgentResponses.approvedGateJson());

        assertThat(provider.review(context).getFindings()).singleElement()
                .extracting(ReviewFinding::getEvidenceChunkIds).isEqualTo(List.of("C1", "C2"));
        assertThat(gatePrompt.getValue()).contains("allowedEvidenceLabels: [C1, C2]");
    }

    private ReviewContext ragContext() {
        RagEvidenceBundle bundle = new RagEvidenceBundle(List.of(
                new RagEvidence("C1", "src/A.java", 1, 2, "head",
                        "bounded evidence content with enough unique source tokens", 0.9),
                new RagEvidence("C2", "src/A.java", 3, 4, "head", "other evidence", 0.8)),
                "prompt", RagEvidenceBundle.DegradedReason.NONE, RagRetrievalHealth.HEALTHY);
        return new ReviewContext(1L, "https://github.com/example/repo", 9, LocalDateTime.now(),
                "diff --git a/src/A.java b/src/A.java\n@@ -1 +1,4 @@\n context\n+added", "mimo",
                com.codereviewx.backend.review.enums.ReviewMode.GITHUB_PR, bundle);
    }

    private String findingJson(String labels, String description) {
        return "{\"severity\":\"HIGH\",\"category\":\"BUG\",\"filePath\":\"src/A.java\","
                + "\"startLine\":1,\"endLine\":1,\"title\":\"title\",\"description\":\"" + description
                + "\",\"recommendation\":\"fix\",\"evidenceChunkIds\":" + labels + "}";
    }
}
