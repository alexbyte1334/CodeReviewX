package com.codereviewx.backend.review.pipeline.provider.mimo;

import com.codereviewx.backend.review.ReviewErrorCodes;
import com.codereviewx.backend.review.enums.IssueCategory;
import com.codereviewx.backend.review.enums.IssueSeverity;
import com.codereviewx.backend.review.enums.IssueSource;
import com.codereviewx.backend.review.enums.IssueStatus;
import com.codereviewx.backend.review.pipeline.ReviewContext;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
}
