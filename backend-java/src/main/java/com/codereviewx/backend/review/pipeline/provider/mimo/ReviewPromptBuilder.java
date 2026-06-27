package com.codereviewx.backend.review.pipeline.provider.mimo;

import com.codereviewx.backend.review.pipeline.ReviewContext;
import org.springframework.stereotype.Component;

@Component
public class ReviewPromptBuilder {

    public static final String SYSTEM_PROMPT =
            "You are CodeReviewX, an AI code review agent. "
                    + "Return only strict JSON. Do not wrap output in markdown.";
    public static final String PLANNER_SYSTEM_PROMPT =
            "You are CodeReviewX AI-1 Planner and Gatekeeper. "
                    + SYSTEM_PROMPT;
    public static final String EXECUTOR_SYSTEM_PROMPT =
            "You are CodeReviewX AI-2 Review Executor. "
                    + SYSTEM_PROMPT;
    public static final String GATEKEEPER_SYSTEM_PROMPT =
            "You are CodeReviewX AI-1 Quality Gatekeeper. "
                    + SYSTEM_PROMPT;

    private static final String TASK_PLAN_SCHEMA = """
            {
              "taskId": 1,
              "repoUrl": "https://github.com/owner/repo",
              "prNumber": 12,
              "reviewMode": "MANUAL_DIFF|GITHUB_PR",
              "query": "string",
              "focusAreas": ["SECURITY", "BUG", "MAINTAINABILITY"],
              "constraints": ["string"]
            }""";

    private static final String CANDIDATE_REVIEW_SCHEMA = """
            {
              "summary": "string",
              "findings": [
                {
                  "severity": "HIGH|MEDIUM|LOW",
                  "category": "BUG|SECURITY|PERFORMANCE|MAINTAINABILITY|STYLE|TEST",
                  "filePath": "string",
                  "startLine": 1,
                  "endLine": 1,
                  "title": "string",
                  "description": "string",
                  "recommendation": "string"
                }
              ]
            }""";

    private static final String GATE_DECISION_SCHEMA = """
            {
              "approved": true,
              "reason": "string",
              "requiredChanges": []
            }""";

    public String buildUserPrompt(ReviewContext context) {
        return buildPlannerPrompt(context);
    }

    public String buildPlannerPrompt(ReviewContext context) {
        return """
                Decompose this ReviewTask and rewrite it into a precise code-review query.

                taskId: %d
                repoUrl: %s
                prNumber: %d
                reviewMode: %s
                contextAvailable: %s

                Return only one JSON object using this schema:
                %s

                Rules:
                - Return only JSON.
                - Do not wrap JSON in markdown fences.
                - Do not include prose before or after JSON.
                - The query must instruct AI-2 to use only available context.
                - Include constraints that forbid invented files and ungrounded claims.
                """.formatted(
                context.getTaskId(),
                context.getRepoUrl(),
                context.getPrNumber(),
                context.getReviewMode(),
                context.hasDiffText() ? "PR diff text" : "bounded PR metadata only",
                TASK_PLAN_SCHEMA
        );
    }

    public String buildExecutorPrompt(ReviewContext context, String taskPlanJson) {
        String reviewContext = context.hasDiffText()
                ? """
                --- PR DIFF START ---
                %s
                --- PR DIFF END ---
                """.formatted(context.getDiffText())
                : "No PR diff is available yet. Use only repoUrl, prNumber, and bounded metadata.";

        return """
                Execute the review using this approved TaskPlan JSON.

                --- TASK PLAN JSON START ---
                %s
                --- TASK PLAN JSON END ---

                repoUrl: %s
                prNumber: %d

                Review context:
                %s

                Return only one CandidateReview JSON object using this schema:
                %s

                Rules:
                - Return only JSON.
                - Do not wrap JSON in markdown fences.
                - Do not include prose before or after JSON.
                - Use only allowed enum values.
                - Do not include issueKey; IssueGenerator will assign final keys.
                - If there are no meaningful findings, return an empty findings array.
                """.formatted(taskPlanJson, context.getRepoUrl(), context.getPrNumber(), reviewContext, CANDIDATE_REVIEW_SCHEMA);
    }

    public String buildGatekeeperPrompt(String taskPlanJson, String candidateReviewJson) {
        return """
                Check whether the CandidateReview follows the TaskPlan, schema, and grounding rules.

                --- TASK PLAN JSON START ---
                %s
                --- TASK PLAN JSON END ---

                --- CANDIDATE REVIEW JSON START ---
                %s
                --- CANDIDATE REVIEW JSON END ---

                Return only one GateDecision JSON object using this schema:
                %s

                Rules:
                - Approve only if findings are grounded and use the allowed schema.
                - Reject if the review invents files, uses unsupported enum values, or ignores constraints.
                - Return only JSON with no markdown.
                """.formatted(taskPlanJson, candidateReviewJson, GATE_DECISION_SCHEMA);
    }
}
