package com.codereviewx.backend.review.pipeline.provider.mimo;

import com.codereviewx.backend.review.ReviewErrorCodes;
import com.codereviewx.backend.review.pipeline.ReviewContext;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import com.codereviewx.backend.review.pipeline.ReviewProvider;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

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
            throw new MiMoAgentException(ReviewErrorCodes.MIMO_AUTH_MISSING,
                    "MiMo planner and executor API keys are required");
        }

        try {
            String planOutput = client.complete(
                    ReviewPromptBuilder.PLANNER_SYSTEM_PROMPT,
                    promptBuilder.buildPlannerPrompt(context),
                    properties.getPlannerApiKey()
            );
            TaskPlan taskPlan = parser.parseTaskPlan(planOutput);
            String taskPlanJson = toJson(taskPlan);

            String candidateOutput = client.complete(
                    ReviewPromptBuilder.EXECUTOR_SYSTEM_PROMPT,
                    promptBuilder.buildExecutorPrompt(context, taskPlanJson),
                    properties.getExecutorApiKey()
            );
            CandidateReview candidateReview = parser.parseCandidateReview(candidateOutput);
            String candidateReviewJson = toJson(candidateReview);

            String gateOutput = client.complete(
                    ReviewPromptBuilder.GATEKEEPER_SYSTEM_PROMPT,
                    promptBuilder.buildGatekeeperPrompt(taskPlanJson, candidateReviewJson),
                    properties.getPlannerApiKey()
            );
            GateDecision gateDecision = parser.parseGateDecision(gateOutput);
            if (!Boolean.TRUE.equals(gateDecision.getApproved())) {
                throw new MiMoAgentException(ReviewErrorCodes.MIMO_GATE_REJECTED,
                        "MiMo gatekeeper rejected candidate review");
            }

            List<ReviewFinding> findings = issueGenerator.generate(candidateReview);
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new MiMoAgentException(ReviewErrorCodes.MIMO_PROVIDER_ERROR,
                    "Failed to serialize MiMo agent payload", ex);
        }
    }
}
