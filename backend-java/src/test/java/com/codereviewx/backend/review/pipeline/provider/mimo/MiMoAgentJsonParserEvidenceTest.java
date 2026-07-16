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
            new RagEvidence("C1", "src/A", 1, 2, "sha", "source text", 1.0),
            new RagEvidence("C2", "src/A", 3, 4, "sha", "other text", 0.9)), "prompt",
            RagEvidenceBundle.DegradedReason.NONE, RagContextAssembler.RetrievalHealth.HEALTHY);

    @Test void rejectsMissingUnknownAndVerbatimLabels() {
        for (String pair : List.of("[]|safe", "[\"C9\"]|safe", "[\"C1\"]|source text")) {
            String[] values = pair.split("\\|", 2);
            assertThatThrownBy(() -> parser.parseCandidateReview(json(values[0], values[1]), bundle))
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
}
