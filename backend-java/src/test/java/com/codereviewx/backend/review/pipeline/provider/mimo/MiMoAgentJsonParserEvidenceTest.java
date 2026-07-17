package com.codereviewx.backend.review.pipeline.provider.mimo;

import com.codereviewx.backend.rag.retrieval.*;
import com.codereviewx.backend.review.ReviewErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class MiMoAgentJsonParserEvidenceTest {
    private final MiMoAgentJsonParser parser = new MiMoAgentJsonParser(new ObjectMapper());
    private final RagEvidenceBundle bundle = new RagEvidenceBundle(List.of(
            new RagEvidence("C1", "src/A", 1, 2, "sha", "sensitive source evidence with multiple tokens", 1.0),
            new RagEvidence("C2", "src/A", 3, 4, "sha", "other text", 0.9)), "prompt",
            RagEvidenceBundle.DegradedReason.NONE, RagRetrievalHealth.HEALTHY);

    @Test void rejectsMissingUnknownAndVerbatimLabels() {
        for (String pair : List.of("[]|safe", "[\"C9\"]|safe")) {
            String[] values = pair.split("\\|", 2);
            assertThatThrownBy(() -> parser.parseCandidateReview(json(values[0], values[1]), bundle))
                    .isInstanceOf(MiMoAgentException.class).extracting("errorCode")
                    .isEqualTo(ReviewErrorCodes.MIMO_REVIEW_INVALID);
        }
    }

    @Test void rejectsNormalizedEvidenceAndMarkersAcrossEveryModelAuthoredSurface() {
        String normalizedCopy = "sensitive\n source   evidence with multiple tokens";
        for (String surface : List.of("summary", "title", "description", "recommendation")) {
            assertThatThrownBy(() -> parser.parseCandidateReview(jsonSurface(surface, normalizedCopy), bundle))
                    .isInstanceOf(MiMoAgentException.class).extracting("errorCode")
                    .isEqualTo(ReviewErrorCodes.MIMO_REVIEW_INVALID);
            assertThatThrownBy(() -> parser.parseCandidateReview(jsonSurface(surface, "[  EVIDENCE C1 ]"), bundle))
                    .isInstanceOf(MiMoAgentException.class).extracting("errorCode")
                    .isEqualTo(ReviewErrorCodes.MIMO_REVIEW_INVALID);
        }
    }

    @Test void doesNotRejectOrdinaryShortCoincidentalEvidenceText() {
        RagEvidenceBundle shortEvidence = new RagEvidenceBundle(List.of(
                new RagEvidence("C1", "src/A", 1, 2, "sha", "null check", 1.0)), "prompt",
                RagEvidenceBundle.DegradedReason.NONE, RagRetrievalHealth.HEALTHY);
        assertThat(parser.parseCandidateReview(jsonSurface("description", "Add a null check before access."), shortEvidence)
                .getFindings()).hasSize(1);
    }

    @Test void rejectsCompatibilityMarkersAcrossEveryModelAuthoredSurface() {
        for (String surface : List.of("summary", "title", "description", "recommendation")) {
            assertThatThrownBy(() -> parser.parseCandidateReview(
                    jsonSurface(surface, "[ＥＶＩＤＥＮＣＥ C1]"), bundle))
                    .isInstanceOf(MiMoAgentException.class).extracting("errorCode")
                    .isEqualTo(ReviewErrorCodes.MIMO_REVIEW_INVALID);
        }
    }

    @Test void rejectsAuthoredFieldWhoseEvidenceMarkerOrCopyAppearsAfterSafeBound() {
        String prefix = "a".repeat(40_000);
        for (String suffix : List.of("[EVIDENCE C1]", "sensitive source evidence with multiple tokens")) {
            assertThatThrownBy(() -> parser.parseCandidateReview(
                    jsonSurface("recommendation", prefix + suffix), bundle))
                    .isInstanceOf(MiMoAgentException.class).extracting("errorCode")
                    .isEqualTo(ReviewErrorCodes.MIMO_REVIEW_INVALID);
        }
    }

    @Test void acceptsValidMultipleKnownLabels() {
        CandidateReview review = parser.parseCandidateReview(json("[\"C1\",\"C2\"]", "safe"), bundle);
        assertThat(review.getFindings().get(0).getEvidenceChunkIds()).containsExactly("C1", "C2");
    }

    private String json(String labels, String description) {
        return "{\"summary\":\"s\",\"findings\":[{\"severity\":\"HIGH\",\"category\":\"BUG\","
                + "\"filePath\":\"src/A\",\"startLine\":1,\"endLine\":1,\"title\":\"t\","
                + "\"description\":\"" + description + "\",\"recommendation\":\"r\","
                + "\"evidenceChunkIds\":" + labels + "}]}";
    }

    private String jsonSurface(String surface, String value) {
        String summary = surface.equals("summary") ? value : "safe summary";
        String title = surface.equals("title") ? value : "safe title";
        String description = surface.equals("description") ? value : "safe description";
        String recommendation = surface.equals("recommendation") ? value : "safe recommendation";
        return "{\"summary\":" + quote(summary) + ",\"findings\":[{\"severity\":\"HIGH\",\"category\":\"BUG\","
                + "\"filePath\":\"src/A\",\"startLine\":1,\"endLine\":1,\"title\":" + quote(title) + ","
                + "\"description\":" + quote(description) + ",\"recommendation\":" + quote(recommendation) + ","
                + "\"evidenceChunkIds\":[\"C1\"]}]}";
    }

    private String quote(String value) {
        try { return new ObjectMapper().writeValueAsString(value); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
