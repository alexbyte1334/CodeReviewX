package com.codereviewx.backend.review.pipeline.provider.mimo;

import com.codereviewx.backend.review.ReviewErrorCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.codereviewx.backend.rag.retrieval.RagEvidenceBundle;
import java.util.Set;
import java.util.stream.Collectors;

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

    public CandidateReview parseCandidateReview(String modelOutput, RagEvidenceBundle evidenceBundle) {
        CandidateReview review = parseCandidateReview(modelOutput);
        if (evidenceBundle == null) return review;
        Set<String> allowed = evidenceBundle.evidence().stream().map(e -> e.label()).collect(Collectors.toSet());
        for (CandidateReview.CandidateFinding finding : review.getFindings()) {
            if (finding.getEvidenceChunkIds() == null || finding.getEvidenceChunkIds().isEmpty()
                    || finding.getEvidenceChunkIds().stream().anyMatch(label -> !allowed.contains(label))) {
                throw new MiMoAgentException(ReviewErrorCodes.MIMO_REVIEW_INVALID,
                        "MiMo executor returned missing or unknown evidence labels");
            }
            String description = finding.getDescription() == null ? "" : finding.getDescription();
            boolean copied = evidenceBundle.evidence().stream()
                    .filter(e -> finding.getEvidenceChunkIds().contains(e.label()))
                    .map(e -> e.content().trim()).filter(content -> !content.isEmpty())
                    .anyMatch(description::contains);
            if (copied || description.contains("[EVIDENCE ")) {
                throw new MiMoAgentException(ReviewErrorCodes.MIMO_REVIEW_INVALID,
                        "MiMo executor copied evidence content verbatim");
            }
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
