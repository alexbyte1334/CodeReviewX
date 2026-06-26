package com.codereviewx.backend.review.pipeline.provider.mimo;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public final class TestMiMoAgentResponses {

    private TestMiMoAgentResponses() {
    }

    public static void stubSuccessfulReview(XiaomiMiMoClient client) {
        when(client.complete(eq(ReviewPromptBuilder.PLANNER_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn(taskPlanJson());
        when(client.complete(eq(ReviewPromptBuilder.EXECUTOR_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn(candidateReviewJson());
        when(client.complete(eq(ReviewPromptBuilder.GATEKEEPER_SYSTEM_PROMPT), anyString(), anyString()))
                .thenReturn(approvedGateJson());
    }

    public static String taskPlanJson() {
        return """
                {
                  "taskId": 1,
                  "repoUrl": "https://github.com/example/repo",
                  "prNumber": 10,
                  "reviewMode": "MANUAL_DIFF",
                  "query": "Review provided context for security, reliability, and maintainability risks.",
                  "focusAreas": ["SECURITY", "BUG", "MAINTAINABILITY"],
                  "constraints": ["Use only available context.", "Do not invent files."]
                }
                """;
    }

    public static String candidateReviewJson() {
        return """
                {
                  "summary": "Review completed with generated findings.",
                  "findings": [
                    {
                      "severity": "HIGH",
                      "category": "SECURITY",
                      "filePath": "src/main/java/com/codereviewx/backend/review/controller/ReviewTaskController.java",
                      "startLine": 42,
                      "endLine": 48,
                      "title": "Potential missing authorization check",
                      "description": "A sensitive endpoint should explicitly check authorization before processing the request.",
                      "recommendation": "Add an authorization guard before the business logic and cover it with a controller test."
                    },
                    {
                      "severity": "MEDIUM",
                      "category": "MAINTAINABILITY",
                      "filePath": "src/main/java/com/codereviewx/backend/review/service/ReviewTaskService.java",
                      "startLine": 76,
                      "endLine": 93,
                      "title": "Service method is doing too much work",
                      "description": "The service method combines validation, state transition, and response mapping.",
                      "recommendation": "Extract validation and response mapping into smaller private methods."
                    },
                    {
                      "severity": "LOW",
                      "category": "TEST",
                      "filePath": "src/test/java/com/codereviewx/backend/review/service/ReviewTaskServiceTest.java",
                      "startLine": 21,
                      "endLine": 21,
                      "title": "Missing negative-path coverage",
                      "description": "Validation and not-found scenarios should be covered explicitly.",
                      "recommendation": "Add tests for invalid request payloads and missing ReviewTask IDs."
                    }
                  ]
                }
                """;
    }

    public static String approvedGateJson() {
        return """
                {
                  "approved": true,
                  "reason": "Candidate review follows the task plan and schema.",
                  "requiredChanges": []
                }
                """;
    }
}
