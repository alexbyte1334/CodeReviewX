package com.codereviewx.backend.review.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagFindingQualityMetricsTest {

    @Test
    void passingCasesMeetProductionFindingAndEvidenceGates() {
        RagFindingQualityMetrics metrics = RagFindingQualityMetrics.from(List.of(
                result("positive", true, true, true, true, 1, 1),
                result("negative", false, true, true, true, 0, 0)));

        assertThat(metrics.evidenceValidationPassRate()).isEqualTo(1.0);
        assertThat(metrics.groundedFindingPrecision()).isEqualTo(1.0);
        assertThat(metrics.expectedFindingPassRate()).isEqualTo(1.0);
        assertThat(metrics.failures()).isEmpty();
    }

    @Test
    void mutationsFailMissingEvidenceWrongIdentityAndUngroundedFindingGates() {
        assertThat(metrics(result("missing", true, false, false, false, 0, 0)).failures())
                .contains("evidenceValidationPassRate", "expectedFindingPassRate");
        assertThat(metrics(result("wrong-chunk", true, true, false, false, 1, 0)).failures())
                .contains("groundedFindingPrecision", "expectedFindingPassRate");
        assertThat(metrics(result("wrong-path", true, true, false, false, 1, 0)).failures())
                .contains("groundedFindingPrecision", "expectedFindingPassRate");
        assertThat(metrics(result("wrong-commit", true, true, false, false, 1, 0)).failures())
                .contains("groundedFindingPrecision", "expectedFindingPassRate");
        assertThat(metrics(result("ungrounded", true, true, false, false, 1, 0)).failures())
                .contains("groundedFindingPrecision", "expectedFindingPassRate");
    }

    private RagFindingQualityMetrics metrics(RagFindingQualityMetrics.CaseResult result) {
        return RagFindingQualityMetrics.from(List.of(result));
    }

    private RagFindingQualityMetrics.CaseResult result(String id, boolean expectedFinding,
                                                        boolean evidenceValidated, boolean grounded,
                                                        boolean expectedFindingPassed,
                                                        int producedFindings, int groundedFindings) {
        return new RagFindingQualityMetrics.CaseResult(id, expectedFinding, evidenceValidated, grounded,
                expectedFindingPassed, producedFindings, groundedFindings);
    }
}
