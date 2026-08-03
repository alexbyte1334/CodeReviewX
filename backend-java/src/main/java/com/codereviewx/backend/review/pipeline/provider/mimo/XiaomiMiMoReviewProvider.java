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
 * Legacy compatibility implementation for the structured OpenAI-compatible review workflow.
 */
@Component
public class XiaomiMiMoReviewProvider implements ReviewProvider {

    public static final String PROVIDER_NAME = "XiaomiMiMoReviewProvider";
    private static final String STRUCTURED_REPAIR_INSTRUCTION = """

            The previous response failed local structured-output validation.
            Retry once. Return exactly one compact JSON object matching the requested schema.
            Include every required field with the requested JSON data type.
            Do not include markdown, comments, trailing commas, or additional prose.
            """;

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
                    "A model API key is required");
            throw new MiMoAgentException(ReviewErrorCodes.MIMO_AUTH_MISSING,
                    "A model API key is required");
        }

        try {
            TaskPlan taskPlan = recordStep(context, "mimo.ai1.plan", () -> {
                return requestStructured(
                        ReviewPromptBuilder.PLANNER_SYSTEM_PROMPT,
                        promptBuilder.buildPlannerPrompt(context),
                        properties.getPlannerApiKey(),
                        parser::parseTaskPlan
                );
            }, ignored -> "Planner produced a structured task plan.");
            String taskPlanJson = toJson(taskPlan);

            CandidateReview candidateReview = recordStep(context, "mimo.ai2.execute", () -> {
                return requestStructured(
                        ReviewPromptBuilder.EXECUTOR_SYSTEM_PROMPT,
                        promptBuilder.buildExecutorPrompt(context, taskPlanJson),
                        properties.getExecutorApiKey(),
                        output -> parser.parseCandidateReview(output, context.getRagEvidenceBundle())
                );
            }, review -> "Executor produced a candidate review with "
                    + review.getFindings().size() + " finding(s).");
            String candidateReviewJson = toJson(candidateReview);

            GateDecision gateDecision = recordStep(context, "mimo.ai1.gate", () -> {
                GateDecision decision = requestStructured(
                        ReviewPromptBuilder.GATEKEEPER_SYSTEM_PROMPT,
                        promptBuilder.buildGatekeeperPrompt(taskPlanJson, candidateReviewJson, context),
                        properties.getPlannerApiKey(),
                        parser::parseGateDecision
                );
                if (!Boolean.TRUE.equals(decision.getApproved())) {
                    throw new MiMoAgentException(ReviewErrorCodes.MIMO_GATE_REJECTED,
                            "Model evidence gate rejected candidate review");
                }
                return decision;
            }, ignored -> "Gatekeeper approved the candidate review.");
            if (!Boolean.TRUE.equals(gateDecision.getApproved())) {
                throw new MiMoAgentException(ReviewErrorCodes.MIMO_GATE_REJECTED,
                    "Model evidence gate rejected candidate review");
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
                    "Model provider request failed", ex);
        } catch (RuntimeException ex) {
            throw new MiMoAgentException(ReviewErrorCodes.MIMO_PROVIDER_ERROR,
                    "Unexpected model provider failure", ex);
        }
    }

    private <T> T requestStructured(String systemPrompt,
                                    String userPrompt,
                                    String apiKey,
                                    Function<String, T> parserFunction) {
        try {
            return parserFunction.apply(client.complete(systemPrompt, userPrompt, apiKey));
        } catch (MiMoAgentException firstFailure) {
            if (!isRepairableStructuredFailure(firstFailure)) {
                throw firstFailure;
            }
            String repairPrompt = userPrompt + STRUCTURED_REPAIR_INSTRUCTION;
            return parserFunction.apply(client.complete(systemPrompt, repairPrompt, apiKey));
        }
    }

    private boolean isRepairableStructuredFailure(MiMoAgentException failure) {
        return ReviewErrorCodes.MIMO_PLAN_INVALID.equals(failure.getErrorCode())
                || ReviewErrorCodes.MIMO_REVIEW_INVALID.equals(failure.getErrorCode())
                || ReviewErrorCodes.MIMO_GATE_INVALID.equals(failure.getErrorCode());
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
        } catch (XiaomiMiMoClientException ex) {
            LocalDateTime finishedAt = LocalDateTime.now();
            context.addAgentStep(new ReviewAgentStep(
                    stepName,
                    ToolTraceStatus.FAILED,
                    startedAt,
                    finishedAt,
                    ex.getMessage(),
                    ReviewErrorCodes.MIMO_PROVIDER_ERROR,
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
                    "Model provider step failed before producing a valid structured result.",
                    ReviewErrorCodes.MIMO_PROVIDER_ERROR,
                    "Model provider step failed"
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
                    "Failed to serialize structured model payload", ex);
        }
    }
}
