package com.codereviewx.backend.review.pipeline.provider.mimo;

import com.codereviewx.backend.review.ReviewErrorCodes;
import com.codereviewx.backend.review.enums.ToolTraceStatus;
import com.codereviewx.backend.review.pipeline.ReviewAgentStep;
import com.codereviewx.backend.review.pipeline.ReviewContext;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import com.codereviewx.backend.review.pipeline.ReviewProvider;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Xiaomi MiMo dual-agent review provider.
 */
@Component
public class XiaomiMiMoReviewProvider implements ReviewProvider {

    public static final String PROVIDER_NAME = "XiaomiMiMoReviewProvider";

    private final ReviewPromptBuilder promptBuilder;
    private final XiaomiMiMoClient client;
    private final XiaomiMiMoProperties properties;
    private final MiMoAgentJsonParser parser;
    private final MiMoIssueGenerator issueGenerator;
    private final ObjectMapper objectMapper;

    public XiaomiMiMoReviewProvider(ReviewPromptBuilder promptBuilder,
                                    XiaomiMiMoClient client,
                                    XiaomiMiMoProperties properties,
                                    MiMoAgentJsonParser parser,
                                    MiMoIssueGenerator issueGenerator,
                                    ObjectMapper objectMapper) {
        this.promptBuilder = promptBuilder;
        this.client = client;
        this.properties = properties;
        this.parser = parser;
        this.issueGenerator = issueGenerator;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReviewProviderResult review(ReviewContext context) {
        if (!properties.hasRoleApiKeys()) {
            recordFailedStep(context, "mimo.auth.check", ReviewErrorCodes.MIMO_AUTH_MISSING,
                    "MiMo planner and executor API keys are required");
            throw new MiMoAgentException(ReviewErrorCodes.MIMO_AUTH_MISSING,
                    "MiMo planner and executor API keys are required");
        }

        try {
            TaskPlan taskPlan = recordStep(context, "mimo.ai1.plan", () -> {
                String planOutput = client.complete(
                        ReviewPromptBuilder.PLANNER_SYSTEM_PROMPT,
                        promptBuilder.buildPlannerPrompt(context),
                        properties.getPlannerApiKey()
                );
                return parser.parseTaskPlan(planOutput);
            }, ignored -> "Planner produced a structured task plan.");
            String taskPlanJson = toJson(taskPlan);

            CandidateReview candidateReview = recordStep(context, "mimo.ai2.execute", () -> {
                String candidateOutput = client.complete(
                        ReviewPromptBuilder.EXECUTOR_SYSTEM_PROMPT,
                        promptBuilder.buildExecutorPrompt(context, taskPlanJson),
                        properties.getExecutorApiKey()
                );
                return parser.parseCandidateReview(candidateOutput);
            }, review -> "Executor produced a candidate review with "
                    + review.getFindings().size() + " finding(s).");
            String candidateReviewJson = toJson(candidateReview);

            GateDecision gateDecision = recordStep(context, "mimo.ai1.gate", () -> {
                String gateOutput = client.complete(
                        ReviewPromptBuilder.GATEKEEPER_SYSTEM_PROMPT,
                        promptBuilder.buildGatekeeperPrompt(taskPlanJson, candidateReviewJson),
                        properties.getPlannerApiKey()
                );
                GateDecision decision = parser.parseGateDecision(gateOutput);
                if (!Boolean.TRUE.equals(decision.getApproved())) {
                    throw new MiMoAgentException(ReviewErrorCodes.MIMO_GATE_REJECTED,
                            "MiMo gatekeeper rejected candidate review");
                }
                return decision;
            }, ignored -> "Gatekeeper approved the candidate review.");
            if (!Boolean.TRUE.equals(gateDecision.getApproved())) {
                throw new MiMoAgentException(ReviewErrorCodes.MIMO_GATE_REJECTED,
                        "MiMo gatekeeper rejected candidate review");
            }

            List<ReviewFinding> findings = recordStep(context, "issue.generate",
                    () -> issueGenerator.generate(candidateReview),
                    generatedFindings -> "IssueGenerator mapped approved review to "
                            + generatedFindings.size() + " issue(s).");
            return new ReviewProviderResult(findings, PROVIDER_NAME, true, null);
        } catch (MiMoAgentException ex) {
            throw ex;
        } catch (XiaomiMiMoClientException ex) {
            throw new MiMoAgentException(ReviewErrorCodes.MIMO_PROVIDER_ERROR,
                    "MiMo provider request failed", ex);
        } catch (RuntimeException ex) {
            throw new MiMoAgentException(ReviewErrorCodes.MIMO_PROVIDER_ERROR,
                    "Unexpected MiMo provider failure", ex);
        }
    }

    private <T> T recordStep(ReviewContext context,
                             String stepName,
                             Supplier<T> action,
                             Function<T, String> successSummary) {
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            T result = action.get();
            LocalDateTime finishedAt = LocalDateTime.now();
            context.addAgentStep(new ReviewAgentStep(
                    stepName,
                    ToolTraceStatus.SUCCESS,
                    startedAt,
                    finishedAt,
                    successSummary.apply(result),
                    null,
                    null
            ));
            return result;
        } catch (MiMoAgentException ex) {
            LocalDateTime finishedAt = LocalDateTime.now();
            context.addAgentStep(new ReviewAgentStep(
                    stepName,
                    ToolTraceStatus.FAILED,
                    startedAt,
                    finishedAt,
                    ex.getMessage(),
                    ex.getErrorCode(),
                    ex.getMessage()
            ));
            throw ex;
        } catch (RuntimeException ex) {
            LocalDateTime finishedAt = LocalDateTime.now();
            context.addAgentStep(new ReviewAgentStep(
                    stepName,
                    ToolTraceStatus.FAILED,
                    startedAt,
                    finishedAt,
                    "MiMo provider step failed before producing a valid structured result.",
                    ReviewErrorCodes.MIMO_PROVIDER_ERROR,
                    "MiMo provider step failed"
            ));
            throw ex;
        }
    }

    private void recordFailedStep(ReviewContext context, String stepName, String errorCode, String message) {
        LocalDateTime now = LocalDateTime.now();
        context.addAgentStep(new ReviewAgentStep(
                stepName,
                ToolTraceStatus.FAILED,
                now,
                now,
                message,
                errorCode,
                message
        ));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new MiMoAgentException(ReviewErrorCodes.MIMO_PROVIDER_ERROR,
                    "Failed to serialize MiMo agent payload", ex);
        }
    }
}
