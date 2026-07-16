package com.codereviewx.backend.rag.retrieval;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class RagEvidenceBundleInvariantTest {
    @Test void rejectsNullBlankAndDuplicateLabelsDeterministically() {
        RagEvidence valid = evidence("C1");
        assertThatThrownBy(() -> bundle(List.of(valid, evidence("C1"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Evidence labels must be unique and non-blank");
        assertThatThrownBy(() -> bundle(List.of(evidence(" "))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Evidence label must be non-blank");
        assertThatThrownBy(() -> new RagEvidence(null, "p", 1, 1, "s", "c", 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Evidence label must be non-blank");
    }
    private RagEvidence evidence(String label) { return new RagEvidence(label, "p", 1, 1, "s", "content", 1); }
    private RagEvidenceBundle bundle(List<RagEvidence> evidence) { return new RagEvidenceBundle(evidence, "", RagEvidenceBundle.DegradedReason.NONE, RagContextAssembler.RetrievalHealth.HEALTHY); }
}
