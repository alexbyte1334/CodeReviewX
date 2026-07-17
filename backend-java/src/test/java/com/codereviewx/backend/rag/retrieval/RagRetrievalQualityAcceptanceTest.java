package com.codereviewx.backend.rag.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RagRetrievalQualityAcceptanceTest {

    @Test
    void productionRetrievalMeetsCommittedQualityGate() throws Exception {
        RagRetrievalProductionEvaluation.Result result = RagRetrievalProductionEvaluation.run();

        assertThat(result.engine()).isEqualTo("java-production");
        assertThat(result.metrics().failures()).isEmpty();
        assertThat(result.jsonReport()).exists();
        assertThat(result.markdownReport()).exists();
        JsonNode report = new ObjectMapper().readTree(result.jsonReport().toFile());
        List<JsonNode> cases = new ArrayList<>();
        report.path("cases").forEach(cases::add);
        assertThat(cases).allSatisfy(item -> {
            assertThat(item.path("forbiddenCandidateKeys")).isNotEmpty();
            assertThat(item.path("selectedChunkKeys")).allSatisfy(key ->
                    assertThat(key.asText()).contains("#"));
            assertThat(item.path("selectedPaths")).isNotEmpty();
            assertThat(item.path("metrics").path("recallAt10").asDouble()).isBetween(0.0, 1.0);
            assertThat(item.path("metrics").path("mrrAt10").asDouble()).isBetween(0.0, 1.0);
            assertThat(item.path("metrics").path("ndcgAt10").asDouble()).isBetween(0.0, 1.0);
        });
    }

    @Test
    void qualityGateRejectsWrongRelevantKeyAndMissedThresholds() {
        RagRetrievalQualityMetrics passing = RagRetrievalQualityMetrics.from(List.of(
                new RagRetrievalQualityMetrics.CaseResult("case", List.of("src/api.ts#1"),
                        List.of("src/api.ts#1"), List.of(), "target-commit", List.of("target-commit"), 120)));
        RagRetrievalQualityMetrics mutated = RagRetrievalQualityMetrics.from(List.of(
                new RagRetrievalQualityMetrics.CaseResult("case", List.of("wrong.ts#1"),
                        List.of("src/api.ts#1"), List.of(), "target-commit", List.of("target-commit"), 120)));

        assertThat(passing.failures()).isEmpty();
        assertThat(mutated.failures()).contains("recallAt10", "mrrAt10", "ndcgAt10");
    }

    @Test
    void safetyGatesRejectForbiddenCrossCommitAndBothContextBudgets() {
        RagRetrievalQualityMetrics forbidden = metrics(
                List.of("src/api.ts#1", "docs/unrelated.md#1"),
                List.of("target-commit", "target-commit"), List.of("docs/unrelated.md#1"), 120);
        RagRetrievalQualityMetrics crossCommit = metrics(
                List.of("src/api.ts#1"), List.of("old-commit"), List.of(), 120);
        RagRetrievalQualityMetrics tooManyChunks = metrics(
                IntStream.range(0, 13).mapToObj(index -> index == 0 ? "src/api.ts#1" : "filler-" + index).toList(),
                IntStream.range(0, 13).mapToObj(ignored -> "target-commit").toList(), List.of(), 120);
        RagRetrievalQualityMetrics tooManyCharacters = metrics(
                List.of("src/api.ts#1"), List.of("target-commit"), List.of(), 36_001);

        assertThat(forbidden.failures()).containsExactly("forbiddenHits");
        assertThat(crossCommit.failures()).containsExactly("crossCommitContamination");
        assertThat(tooManyChunks.failures()).containsExactly("contextBudgetViolations");
        assertThat(tooManyCharacters.failures()).containsExactly("contextBudgetViolations");
    }

    @Test
    void chunkMetricsDeduplicateIdentityAndRejectOtherChunksFromTheSamePath() {
        RagRetrievalQualityMetrics duplicateRelevantChunk = RagRetrievalQualityMetrics.from(List.of(
                new RagRetrievalQualityMetrics.CaseResult("duplicate", List.of("src/api.ts#2"),
                        List.of("src/api.ts#2", "src/api.ts#2", "src/api.ts#1"), List.of(),
                        "target-commit", List.of("target-commit", "target-commit", "target-commit"), 120)));
        RagRetrievalQualityMetrics wrongLineSegments = RagRetrievalQualityMetrics.from(List.of(
                new RagRetrievalQualityMetrics.CaseResult("wrong-segment", List.of("src/api.ts#2"),
                        List.of("src/api.ts#1", "src/api.ts#3"), List.of(),
                        "target-commit", List.of("target-commit", "target-commit"), 120)));

        assertThat(duplicateRelevantChunk.cases()).singleElement().satisfies(metrics -> {
            assertThat(metrics.recallAt10()).isEqualTo(1.0);
            assertThat(metrics.mrrAt10()).isEqualTo(1.0);
            assertThat(metrics.ndcgAt10()).isEqualTo(1.0);
        });
        assertThat(wrongLineSegments.failures()).contains("recallAt10", "mrrAt10", "ndcgAt10");
    }

    @Test
    void qualityMetricsUseStableTwelveDecimalPrecision() {
        RagRetrievalQualityMetrics metrics = RagRetrievalQualityMetrics.from(List.of(
                new RagRetrievalQualityMetrics.CaseResult("stable-ndcg",
                        List.of("src/api.ts#1", "src/service.ts#1"),
                        List.of("src/api.ts#1", "src/filler.ts#1", "src/service.ts#1"),
                        List.of(), "target-commit",
                        List.of("target-commit", "target-commit", "target-commit"), 120)));

        assertThat(metrics.metrics().get("ndcgAt10")).isEqualTo(0.919720789148);
        assertThat(metrics.cases()).singleElement().satisfies(item ->
                assertThat(item.ndcgAt10()).isEqualTo(0.919720789148));
    }

    @Test
    void aggregateMetricsUseStablePrecisionWithoutMaskingThresholdFailures() {
        RagRetrievalQualityMetrics metrics = RagRetrievalQualityMetrics.from(List.of(
                new RagRetrievalQualityMetrics.CaseResult("first-rank", List.of("relevant#1"),
                        List.of("relevant#1"), List.of(), "target-commit",
                        List.of("target-commit"), 120),
                new RagRetrievalQualityMetrics.CaseResult("third-rank", List.of("relevant#1"),
                        List.of("filler#1", "filler#2", "relevant#1"), List.of(), "target-commit",
                        List.of("target-commit", "target-commit", "target-commit"), 120)));

        assertThat(metrics.metrics().get("mrrAt10")).isEqualTo(0.666666666667);
        assertThat(metrics.failures()).containsExactly("mrrAt10");
    }

    private static RagRetrievalQualityMetrics metrics(List<String> selectedKeys, List<String> selectedCommits,
                                                      List<String> forbiddenKeys, int contextCharacters) {
        return RagRetrievalQualityMetrics.from(List.of(new RagRetrievalQualityMetrics.CaseResult(
                "case", List.of("src/api.ts#1"), selectedKeys, forbiddenKeys, "target-commit", selectedCommits,
                contextCharacters)));
    }
}
