package com.codereviewx.backend.review.pipeline.provider.mimo;

import com.codereviewx.backend.review.ReviewErrorCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.codereviewx.backend.rag.retrieval.RagEvidenceBundle;
import java.util.Set;
import java.util.stream.Collectors;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class MiMoAgentJsonParser {

    private static final int MIN_EVIDENCE_COPY_LENGTH = 32;
    private static final int MAX_COPY_CHECK_LENGTH = 40_000;
    private static final Pattern EVIDENCE_MARKER = Pattern.compile("(?i)\\[\\s*/?\\s*evidence\\b");

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
        rejectCopiedEvidence(review.getSummary(), evidenceBundle);
        for (CandidateReview.CandidateFinding finding : review.getFindings()) {
            if (finding.getEvidenceChunkIds() == null || finding.getEvidenceChunkIds().isEmpty()
                    || finding.getEvidenceChunkIds().stream().anyMatch(label -> !allowed.contains(label))) {
                throw new MiMoAgentException(ReviewErrorCodes.MIMO_REVIEW_INVALID,
                        "MiMo executor returned missing or unknown evidence labels");
            }
            rejectCopiedEvidence(finding.getTitle(), evidenceBundle);
            rejectCopiedEvidence(finding.getDescription(), evidenceBundle);
            rejectCopiedEvidence(finding.getRecommendation(), evidenceBundle);
        }
        return review;
    }

    private void rejectCopiedEvidence(String authoredText, RagEvidenceBundle evidenceBundle) {
        String bounded = bounded(authoredText);
        if (EVIDENCE_MARKER.matcher(bounded).find()) {
            throw copiedEvidence();
        }
        String normalizedAuthored = normalize(bounded);
        boolean copied = evidenceBundle.evidence().stream().map(evidence -> normalize(bounded(evidence.content())))
                .filter(content -> content.length() >= MIN_EVIDENCE_COPY_LENGTH)
                .anyMatch(normalizedAuthored::contains);
        if (copied) throw copiedEvidence();
    }

    private MiMoAgentException copiedEvidence() {
        return new MiMoAgentException(ReviewErrorCodes.MIMO_REVIEW_INVALID,
                "MiMo executor copied evidence content verbatim");
    }

    private String bounded(String value) {
        if (value == null) return "";
        return value.substring(0, Math.min(value.length(), MAX_COPY_CHECK_LENGTH));
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ").trim();
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
