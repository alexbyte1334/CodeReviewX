package com.codereviewx.backend.review.service;

import java.util.ArrayList;
import java.util.List;

final class RagFindingQualityMetrics {
    static final double MIN_EVIDENCE_VALIDATION_PASS_RATE = 0.95;
    static final double MIN_GROUNDED_FINDING_PRECISION = 1.0;

    private final List<CaseResult> cases;
    private final double evidenceValidationPassRate;
    private final double groundedFindingPrecision;
    private final double expectedFindingPassRate;
    private final List<String> failures;

    private RagFindingQualityMetrics(List<CaseResult> cases, double evidenceValidationPassRate,
                                     double groundedFindingPrecision, double expectedFindingPassRate,
                                     List<String> failures) {
        this.cases = List.copyOf(cases);
        this.evidenceValidationPassRate = evidenceValidationPassRate;
        this.groundedFindingPrecision = groundedFindingPrecision;
        this.expectedFindingPassRate = expectedFindingPassRate;
        this.failures = List.copyOf(failures);
    }

    static RagFindingQualityMetrics from(List<CaseResult> cases) {
        long positiveCases = cases.stream().filter(CaseResult::expectedFinding).count();
        long validatedCases = cases.stream().filter(CaseResult::expectedFinding)
                .filter(CaseResult::evidenceValidated).count();
        int producedFindings = cases.stream().mapToInt(CaseResult::producedFindings).sum();
        int groundedFindings = cases.stream().mapToInt(CaseResult::groundedFindings).sum();
        long passedCases = cases.stream().filter(CaseResult::expectedFindingPassed).count();

        double evidenceRate = positiveCases == 0 ? 1.0 : (double) validatedCases / positiveCases;
        double groundedPrecision = producedFindings == 0 ? 1.0 : (double) groundedFindings / producedFindings;
        double expectedRate = cases.isEmpty() ? 0.0 : (double) passedCases / cases.size();
        List<String> failures = new ArrayList<>();
        if (evidenceRate < MIN_EVIDENCE_VALIDATION_PASS_RATE) failures.add("evidenceValidationPassRate");
        if (groundedPrecision < MIN_GROUNDED_FINDING_PRECISION) failures.add("groundedFindingPrecision");
        if (expectedRate < 1.0) failures.add("expectedFindingPassRate");
        return new RagFindingQualityMetrics(cases, evidenceRate, groundedPrecision, expectedRate, failures);
    }

    List<CaseResult> cases() { return cases; }
    double evidenceValidationPassRate() { return evidenceValidationPassRate; }
    double groundedFindingPrecision() { return groundedFindingPrecision; }
    double expectedFindingPassRate() { return expectedFindingPassRate; }
    List<String> failures() { return failures; }

    record CaseResult(String id, boolean expectedFinding, boolean evidenceValidated, boolean grounded,
                      boolean expectedFindingPassed, int producedFindings, int groundedFindings) {
    }
}
