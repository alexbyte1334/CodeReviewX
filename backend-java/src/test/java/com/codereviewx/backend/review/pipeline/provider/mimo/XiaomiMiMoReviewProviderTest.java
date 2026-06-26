package com.codereviewx.backend.review.pipeline.provider.mimo;

import com.codereviewx.backend.review.enums.IssueCategory;
import com.codereviewx.backend.review.enums.IssueSeverity;
import com.codereviewx.backend.review.enums.IssueSource;
import com.codereviewx.backend.review.enums.IssueStatus;
import com.codereviewx.backend.review.pipeline.ReviewContext;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class XiaomiMiMoReviewProviderTest {

    private ReviewPromptBuilder promptBuilder;
    private XiaomiMiMoClient client;
    private XiaomiMiMoFindingParser parser;
    private XiaomiMiMoReviewProvider provider;
    private ReviewContext context;

    @BeforeEach
    void setUp() {
        promptBuilder = new ReviewPromptBuilder();
        client = mock(XiaomiMiMoClient.class);
        parser = new XiaomiMiMoFindingParser(new com.fasterxml.jackson.databind.ObjectMapper());
        provider = new XiaomiMiMoReviewProvider(promptBuilder, client, parser);
        context = new ReviewContext(1L, "https://github.com/example/repo", 9, LocalDateTime.now());
    }

    @Test
    void review_buildsPromptCallsClientAndParsesFindings() {
        String modelOutput = """
                [
                  {
                    "issueKey": "MIMO-ISSUE-1",
                    "severity": "HIGH",
                    "category": "SECURITY",
                    "filePath": "src/Main.java",
                    "startLine": 10,
                    "endLine": 12,
                    "title": "Missing auth",
                    "description": "Endpoint lacks auth.",
                    "recommendation": "Add auth guard."
                  }
                ]
                """;

        when(client.complete(eq(ReviewPromptBuilder.SYSTEM_PROMPT), anyString())).thenReturn(modelOutput);

        ReviewProviderResult result = provider.review(context);

        verify(client).complete(eq(ReviewPromptBuilder.SYSTEM_PROMPT), anyString());
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getProviderName()).isEqualTo(XiaomiMiMoReviewProvider.PROVIDER_NAME);
        assertThat(result.getFindings()).hasSize(1);

        ReviewFinding finding = result.getFindings().get(0);
        assertThat(finding.getIssueKey()).isEqualTo("MIMO-ISSUE-1");
        assertThat(finding.getSeverity()).isEqualTo(IssueSeverity.HIGH);
        assertThat(finding.getCategory()).isEqualTo(IssueCategory.SECURITY);
        assertThat(finding.getSource()).isEqualTo(IssueSource.MIMO);
        assertThat(finding.getStatus()).isEqualTo(IssueStatus.OPEN);
    }

    @Test
    void review_handlesValidEmptyArray() {
        when(client.complete(eq(ReviewPromptBuilder.SYSTEM_PROMPT), anyString())).thenReturn("[]");

        ReviewProviderResult result = provider.review(context);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getFindings()).isEmpty();
    }

    @Test
    void review_generatesDeterministicIssueKeysWhenMissing() {
        String modelOutput = """
                [
                  {
                    "severity": "LOW",
                    "category": "TEST",
                    "filePath": "src/Test.java",
                    "startLine": 1,
                    "endLine": 1,
                    "title": "Add tests",
                    "description": "Coverage gap.",
                    "recommendation": "Add unit tests."
                  }
                ]
                """;

        when(client.complete(eq(ReviewPromptBuilder.SYSTEM_PROMPT), anyString())).thenReturn(modelOutput);

        ReviewProviderResult result = provider.review(context);

        assertThat(result.getFindings()).extracting(ReviewFinding::getIssueKey)
                .containsExactly("MIMO-ISSUE-1");
    }

    @Test
    void review_withDiffText_usesDiffPrompt() {
        ReviewContext diffContext = new ReviewContext(
                1L,
                "https://github.com/example/repo",
                9,
                LocalDateTime.now(),
                "diff --git a/src/App.tsx b/src/App.tsx\n+const password = request.query.password;\n"
        );
        when(client.complete(eq(ReviewPromptBuilder.SYSTEM_PROMPT), anyString())).thenReturn("[]");

        provider.review(diffContext);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).complete(eq(ReviewPromptBuilder.SYSTEM_PROMPT), promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("The following PR diff is provided and should be used as the primary review context.")
                .contains("--- PR DIFF START ---")
                .contains("+const password = request.query.password;");
    }

    @Test
    void review_withoutDiffText_usesNoDiffPrompt() {
        when(client.complete(eq(ReviewPromptBuilder.SYSTEM_PROMPT), anyString())).thenReturn("[]");

        provider.review(context);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).complete(eq(ReviewPromptBuilder.SYSTEM_PROMPT), promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("does not include the actual PR diff yet")
                .doesNotContain("--- PR DIFF START ---");
    }
}
