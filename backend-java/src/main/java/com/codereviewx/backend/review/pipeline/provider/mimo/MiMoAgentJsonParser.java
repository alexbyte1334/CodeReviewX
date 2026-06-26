package com.codereviewx.backend.review.pipeline.provider.mimo;

import com.codereviewx.backend.review.ReviewErrorCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class MiMoAgentJsonParser {

    private final ObjectMapper objectMapper;

    public MiMoAgentJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TaskPlan parseTaskPlan(String modelOutput) {
        TaskPlan plan = parseObject(modelOutput, TaskPlan.class, ReviewErrorCodes.MIMO_PLAN_INVALID);
        if (plan.getTaskId() == null || isBlank(plan.getRepoUrl()) || plan.getPrNumber() == null
                || isBlank(plan.getQuery())) {
            throw new MiMoAgentException(ReviewErrorCodes.MIMO_PLAN_INVALID,
                    "MiMo planner returned an incomplete task plan");
        }
        return plan;
    }

    public CandidateReview parseCandidateReview(String modelOutput) {
        CandidateReview review = parseObject(modelOutput, CandidateReview.class, ReviewErrorCodes.MIMO_REVIEW_INVALID);
        if (review.getFindings() == null) {
            throw new MiMoAgentException(ReviewErrorCodes.MIMO_REVIEW_INVALID,
                    "MiMo executor returned a candidate review without findings");
        }
        return review;
    }

    public GateDecision parseGateDecision(String modelOutput) {
        GateDecision decision = parseObject(modelOutput, GateDecision.class, ReviewErrorCodes.MIMO_GATE_INVALID);
        if (decision.getApproved() == null) {
            throw new MiMoAgentException(ReviewErrorCodes.MIMO_GATE_INVALID,
                    "MiMo gatekeeper returned a decision without approval status");
        }
        return decision;
    }

    private <T> T parseObject(String modelOutput, Class<T> type, String errorCode) {
        if (modelOutput == null || modelOutput.isBlank()) {
            throw new MiMoAgentException(errorCode, "MiMo model output is empty");
        }
        String trimmed = modelOutput.trim();
        if (!trimmed.startsWith("{")) {
            throw new MiMoAgentException(errorCode, "MiMo model output is not a JSON object");
        }
        try {
            return objectMapper.readValue(trimmed, type);
        } catch (JsonProcessingException ex) {
            throw new MiMoAgentException(errorCode, "MiMo model output is not valid JSON", ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
