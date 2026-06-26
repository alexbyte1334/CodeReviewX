package com.codereviewx.backend.review.pipeline.provider.mimo;

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
    void buildUserPrompt_withoutDiff_statesDiffUnavailable() {
        String prompt = promptBuilder.buildUserPrompt(contextWithoutDiff());

        assertThat(prompt).contains("does not include the actual PR diff yet");
        assertThat(prompt).doesNotContain("--- PR DIFF START ---");
        assertThat(prompt).contains("\"issueKey\": \"MIMO-ISSUE-1\"");
        assertThat(prompt).contains("Return only JSON.");
    }

    @Test
    void buildUserPrompt_withDiff_includesDiffAsPrimaryContext() {
        String diff = "diff --git a/src/App.tsx b/src/App.tsx\n+const password = request.query.password;\n";
        String prompt = promptBuilder.buildUserPrompt(contextWithDiff(diff));

        assertThat(prompt).contains("The following PR diff is provided and should be used as the primary review context.");
        assertThat(prompt).contains("--- PR DIFF START ---");
        assertThat(prompt).contains(diff);
        assertThat(prompt).contains("--- PR DIFF END ---");
        assertThat(prompt).doesNotContain("does not include the actual PR diff yet");
    }

    @Test
    void buildUserPrompt_withDiff_instructsNotToInventFiles() {
        String prompt = promptBuilder.buildUserPrompt(contextWithDiff("diff --git a/a.txt b/a.txt\n"));

        assertThat(prompt).contains("Do not invent files");
        assertThat(prompt).contains("Use only files and code present in the provided diff");
        assertThat(prompt).contains("Prefer changed-hunk line numbers when possible");
    }

    @Test
    void buildUserPrompt_withDiff_usesStrictJsonSchema() {
        String prompt = promptBuilder.buildUserPrompt(contextWithDiff("diff --git a/a.txt b/a.txt\n"));

        assertThat(prompt).contains("\"severity\": \"HIGH|MEDIUM|LOW\"");
        assertThat(prompt).contains("\"category\": \"BUG|SECURITY|PERFORMANCE|MAINTAINABILITY|STYLE|TEST\"");
        assertThat(prompt).contains("Do not wrap JSON in markdown fences");
        assertThat(prompt).contains("If there are no meaningful findings, return []");
    }

    @Test
    void buildUserPrompt_blankDiffText_usesNoDiffPrompt() {
        ReviewContext context = new ReviewContext(1L, "https://github.com/example/repo", 10, LocalDateTime.now(), "   ");
        String prompt = promptBuilder.buildUserPrompt(context);

        assertThat(prompt).contains("does not include the actual PR diff yet");
        assertThat(prompt).doesNotContain("--- PR DIFF START ---");
    }
}
