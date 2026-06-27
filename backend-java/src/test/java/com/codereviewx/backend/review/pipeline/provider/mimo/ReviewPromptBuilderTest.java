package com.codereviewx.backend.review.pipeline.provider.mimo;

import com.codereviewx.backend.review.enums.ReviewMode;
import com.codereviewx.backend.review.pipeline.ReviewContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewPromptBuilderTest {

    private final ReviewPromptBuilder promptBuilder = new ReviewPromptBuilder();

    private ReviewContext contextWithoutDiff() {
        return new ReviewContext(1L, "https://github.com/example/repo", 10, LocalDateTime.now());
    }

    private ReviewContext contextWithDiff(String diffText) {
        return new ReviewContext(1L, "https://github.com/example/repo", 10, LocalDateTime.now(), diffText);
    }

    @Test
    void buildPlannerPrompt_returnsTaskPlanSchema() {
        String prompt = promptBuilder.buildPlannerPrompt(contextWithoutDiff());

        assertThat(prompt).contains("Decompose this ReviewTask");
        assertThat(prompt).contains("\"query\": \"string\"");
        assertThat(prompt).contains("\"focusAreas\"");
        assertThat(prompt).contains("Return only JSON.");
    }

    @Test
    void buildPlannerPrompt_withGithubPrDiffKeepsGithubPrReviewMode() {
        ReviewContext context = new ReviewContext(
                1L,
                "https://github.com/example/repo",
                10,
                LocalDateTime.now(),
                "diff --git a/src/App.ts b/src/App.ts\n+const x = 1;\n",
                "mimo",
                ReviewMode.GITHUB_PR
        );

        String prompt = promptBuilder.buildPlannerPrompt(context);

        assertThat(prompt).contains("reviewMode: GITHUB_PR");
        assertThat(prompt).contains("contextAvailable: PR diff text");
    }

    @Test
    void buildExecutorPrompt_withDiff_includesDiffAndCandidateSchema() {
        String diff = "diff --git a/src/App.tsx b/src/App.tsx\n+const password = request.query.password;\n";
        String prompt = promptBuilder.buildExecutorPrompt(contextWithDiff(diff), TestMiMoAgentResponses.taskPlanJson());

        assertThat(prompt).contains("--- TASK PLAN JSON START ---");
        assertThat(prompt).contains("--- PR DIFF START ---");
        assertThat(prompt).contains(diff);
        assertThat(prompt).contains("\"findings\"");
        assertThat(prompt).contains("Do not include issueKey");
    }

    @Test
    void buildExecutorPrompt_withoutDiff_statesDiffUnavailable() {
        String prompt = promptBuilder.buildExecutorPrompt(contextWithoutDiff(), TestMiMoAgentResponses.taskPlanJson());

        assertThat(prompt).contains("No PR diff is available yet");
        assertThat(prompt).doesNotContain("--- PR DIFF START ---");
    }

    @Test
    void buildGatekeeperPrompt_containsBothJsonPayloadsAndDecisionSchema() {
        String prompt = promptBuilder.buildGatekeeperPrompt(
                TestMiMoAgentResponses.taskPlanJson(),
                TestMiMoAgentResponses.candidateReviewJson()
        );

        assertThat(prompt).contains("--- TASK PLAN JSON START ---");
        assertThat(prompt).contains("--- CANDIDATE REVIEW JSON START ---");
        assertThat(prompt).contains("\"approved\": true");
        assertThat(prompt).contains("Reject if the review invents files");
    }
}
