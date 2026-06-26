package com.codereviewx.backend.review.pipeline.provider.mimo;

import com.codereviewx.backend.review.pipeline.ReviewContext;
import org.springframework.stereotype.Component;

@Component
public class ReviewPromptBuilder {

    public static final String SYSTEM_PROMPT =
            "You are CodeReviewX, an AI code review agent. "
                    + "You identify security, maintainability, reliability, performance, test, style, and documentation risks. "
                    + "Return only strict JSON. Do not wrap output in markdown.";

    private static final String JSON_SCHEMA = """
            [
              {
                "issueKey": "MIMO-ISSUE-1",
                "severity": "HIGH|MEDIUM|LOW",
                "category": "BUG|SECURITY|PERFORMANCE|MAINTAINABILITY|STYLE|TEST",
                "filePath": "string",
                "startLine": 1,
                "endLine": 1,
                "title": "string",
                "description": "string",
                "recommendation": "string"
              }
            ]""";

    public String buildUserPrompt(ReviewContext context) {
        if (context.hasDiffText()) {
            return buildWithDiffPrompt(context);
        }
        return buildNoDiffPrompt(context);
    }

    private String buildNoDiffPrompt(ReviewContext context) {
        return """
                Review this pull request context.

                repoUrl: %s
                prNumber: %d

                Current available context does not include the actual PR diff yet.
                Return findings only if you can identify meaningful risks from the provided context.
                For demo mode, you may return conservative synthetic findings clearly framed as review suggestions.

                Return only a JSON array with objects using this schema:
                %s

                Rules:
                - Return only JSON.
                - Do not wrap JSON in markdown fences.
                - Do not include prose before or after JSON.
                - Use only allowed enum values.
                - If there are no meaningful findings, return [].
                """.formatted(context.getRepoUrl(), context.getPrNumber(), JSON_SCHEMA);
    }

    private String buildWithDiffPrompt(ReviewContext context) {
        return """
                Review this pull request context.

                repoUrl: %s
                prNumber: %d

                The following PR diff is provided and should be used as the primary review context.

                --- PR DIFF START ---
                %s
                --- PR DIFF END ---

                Review changed lines and nearby context.
                Identify security, reliability, maintainability, performance, test, style, and bug risks.
                Use only files and code present in the provided diff.
                Do not invent files that are not present in the diff.
                Prefer changed-hunk line numbers when possible.

                Return only a JSON array with objects using this schema:
                %s

                Rules:
                - Return only JSON.
                - Do not wrap JSON in markdown fences.
                - Do not include prose before or after JSON.
                - Use only allowed enum values.
                - Use only files/code present in the provided diff.
                - Do not invent files outside the diff.
                - If there are no meaningful findings, return [].
                """.formatted(context.getRepoUrl(), context.getPrNumber(), context.getDiffText(), JSON_SCHEMA);
    }
}
