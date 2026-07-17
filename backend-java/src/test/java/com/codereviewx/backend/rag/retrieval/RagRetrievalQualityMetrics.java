package com.codereviewx.backend.rag.retrieval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RagRetrievalQualityMetrics {

    static final Map<String, Double> THRESHOLDS = thresholds();

    private final List<CaseMetrics> cases;
    private final Map<String, Double> metrics;
    private final List<String> failures;

    private RagRetrievalQualityMetrics(List<CaseMetrics> cases, Map<String, Double> metrics,
                                       List<String> failures) {
        this.cases = List.copyOf(cases);
        this.metrics = Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
        this.failures = List.copyOf(failures);
    }

    static RagRetrievalQualityMetrics from(List<CaseResult> results) {
        List<CaseMetrics> cases = results.stream().map(RagRetrievalQualityMetrics::calculate).toList();
        List<CaseMetrics> positives = cases.stream().filter(item -> !item.relevantKeys().isEmpty()).toList();
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("recallAt10", average(positives, CaseMetrics::recallAt10));
        metrics.put("mrrAt10", average(positives, CaseMetrics::mrrAt10));
        metrics.put("ndcgAt10", average(positives, CaseMetrics::ndcgAt10));
        metrics.put("forbiddenHits", cases.stream().mapToDouble(CaseMetrics::forbiddenHits).sum());
        metrics.put("crossCommitContamination",
                cases.stream().filter(CaseMetrics::crossCommitContamination).count() * 1.0);
        metrics.put("contextBudgetViolations", cases.stream().filter(CaseMetrics::contextBudgetViolation).count() * 1.0);

        List<String> failures = new ArrayList<>();
        THRESHOLDS.forEach((name, threshold) -> {
            double actual = metrics.get(name);
            boolean zeroGate = threshold == 0.0;
            if (zeroGate ? actual != 0.0 : actual < threshold) {
                failures.add(name);
            }
        });
        return new RagRetrievalQualityMetrics(cases, metrics, failures);
    }

    List<CaseMetrics> cases() {
        return cases;
    }

    Map<String, Double> metrics() {
        return metrics;
    }

    List<String> failures() {
        return failures;
    }

    private static CaseMetrics calculate(CaseResult result) {
        List<String> topTen = result.selectedKeys().stream().limit(10).toList();
        Set<String> relevant = new LinkedHashSet<>(result.relevantKeys());
        long hits = topTen.stream().filter(relevant::contains).distinct().count();
        int firstRelevant = -1;
        for (int index = 0; index < topTen.size(); index++) {
            if (relevant.contains(topTen.get(index))) {
                firstRelevant = index;
                break;
            }
        }
        double recall = relevant.isEmpty() ? 0.0 : (double) hits / relevant.size();
        double mrr = firstRelevant < 0 ? 0.0 : 1.0 / (firstRelevant + 1);
        double dcg = 0.0;
        Set<String> scoredRelevant = new LinkedHashSet<>();
        for (int index = 0; index < topTen.size(); index++) {
            if (relevant.contains(topTen.get(index)) && scoredRelevant.add(topTen.get(index))) {
                dcg += 1.0 / log2(index + 2);
            }
        }
        double idealDcg = 0.0;
        for (int index = 0; index < Math.min(10, relevant.size()); index++) {
            idealDcg += 1.0 / log2(index + 2);
        }
        double ndcg = idealDcg == 0.0 ? 0.0 : dcg / idealDcg;
        int forbiddenHits = (int) result.selectedKeys().stream().filter(result.forbiddenKeys()::contains).count();
        boolean crossCommit = result.selectedCommits().stream()
                .anyMatch(commit -> !result.targetCommit().equals(commit));
        boolean budgetViolation = result.selectedKeys().size() > 12 || result.contextCharacters() > 36_000;
        return new CaseMetrics(result.id(), result.relevantKeys(), result.selectedKeys(), result.forbiddenKeys(),
                recall, mrr, ndcg, forbiddenHits, crossCommit, result.contextCharacters(), budgetViolation);
    }

    private static double average(List<CaseMetrics> values, MetricValue metric) {
        return values.isEmpty() ? 0.0 : values.stream().mapToDouble(metric::get).average().orElseThrow();
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    private static Map<String, Double> thresholds() {
        Map<String, Double> thresholds = new LinkedHashMap<>();
        thresholds.put("recallAt10", 0.85);
        thresholds.put("mrrAt10", 0.70);
        thresholds.put("ndcgAt10", 0.75);
        thresholds.put("forbiddenHits", 0.0);
        thresholds.put("crossCommitContamination", 0.0);
        thresholds.put("contextBudgetViolations", 0.0);
        return Collections.unmodifiableMap(thresholds);
    }

    @FunctionalInterface
    private interface MetricValue {
        double get(CaseMetrics metrics);
    }

    record CaseResult(String id, List<String> relevantKeys, List<String> selectedKeys,
                      List<String> forbiddenKeys, String targetCommit, List<String> selectedCommits,
                      int contextCharacters) {
        CaseResult {
            relevantKeys = List.copyOf(relevantKeys);
            selectedKeys = List.copyOf(selectedKeys);
            forbiddenKeys = List.copyOf(forbiddenKeys);
            selectedCommits = List.copyOf(selectedCommits);
        }
    }

    record CaseMetrics(String id, List<String> relevantKeys, List<String> selectedKeys,
                       List<String> forbiddenKeys, double recallAt10, double mrrAt10, double ndcgAt10,
                       int forbiddenHits, boolean crossCommitContamination, int contextCharacters,
                       boolean contextBudgetViolation) {
    }
}
