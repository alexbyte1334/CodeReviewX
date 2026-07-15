package com.codereviewx.backend.rag.retrieval;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RagContextAssemblerTest {

    @Test
    void capsRerankInputAtThirtyAndFinalEvidenceAtTwelve() {
        CapturingRerankClient reranker = new CapturingRerankClient();
        List<HybridRagRetrievalService.Match> matches = new ArrayList<>();
        for (int index = 1; index <= 35; index++) {
            matches.add(match(index, "src/File" + index + ".java", index, index,
                    "unique content token " + index, 1.0));
        }

        RagEvidenceBundle result = new RagContextAssembler(reranker)
                .assemble("review query", "head-sha", matches);

        assertThat(reranker.received).hasSize(30);
        assertThat(result.evidence()).hasSize(12);
        assertThat(result.evidence()).extracting(RagEvidence::label)
                .containsExactly("C1", "C2", "C3", "C4", "C5", "C6",
                        "C7", "C8", "C9", "C10", "C11", "C12");
    }

    @Test
    void retainsOnlyHigherScoredAdjacentCandidateAboveJaccardThreshold() {
        RerankClient reranker = (query, candidates) -> List.of(
                new RerankedChunk(candidates.get(1), 0.9),
                new RerankedChunk(candidates.get(0), 0.8),
                new RerankedChunk(candidates.get(2), 0.7));
        List<HybridRagRetrievalService.Match> matches = List.of(
                match(1, "src/A.java", 1, 10, words(100, "alpha"), 1.0),
                match(2, "src/A.java", 11, 20, words(95, "alpha") + " beta gamma delta epsilon zeta", 1.0),
                match(3, "src/B.java", 1, 10, "independent tokens stay here", 1.0));

        RagEvidenceBundle result = new RagContextAssembler(reranker)
                .assemble("query", "head-sha", matches);

        assertThat(result.evidence()).extracting(RagEvidence::startLine).containsExactly(11, 1);
    }

    @Test
    void limitsEachFileToThreeChunksAndPreservesChangedFileEvidence() {
        List<HybridRagRetrievalService.Match> matches = new ArrayList<>();
        for (int index = 1; index <= 15; index++) {
            matches.add(match(index, "src/Common.java", index, index, "common unique " + index, 1.0));
        }
        matches.add(match(99, "src/Changed.java", 42, 78, "changed file evidence", 1.25));

        RagEvidenceBundle result = new RagContextAssembler(new CapturingRerankClient())
                .assemble("query", "head-sha", matches);

        assertThat(result.evidence()).filteredOn(item -> item.path().equals("src/Common.java")).hasSize(3);
        assertThat(result.evidence()).extracting(RagEvidence::path).contains("src/Changed.java");
    }

    @Test
    void reservesChangedFileCandidateWhenItFallsOutsideFirstThirtyRrfItems() {
        CapturingRerankClient reranker = new CapturingRerankClient();
        List<HybridRagRetrievalService.Match> matches = new ArrayList<>();
        for (int index = 1; index <= 31; index++) {
            matches.add(match(index, "src/File" + index + ".java", index, index, "content " + index, 1.0));
        }
        matches.add(match(32, "src/Changed.java", 5, 8, "changed tail", 1.25));

        RagEvidenceBundle result = new RagContextAssembler(reranker)
                .assemble("query", "head-sha", matches);

        assertThat(reranker.received).hasSize(30);
        assertThat(reranker.received).extracting(RerankCandidate::text).contains("changed tail");
        assertThat(result.evidence()).extracting(RagEvidence::path).contains("src/Changed.java");
    }

    @Test
    void capsTotalContentAtThirtySixThousandCharactersDeterministically() {
        List<HybridRagRetrievalService.Match> matches = List.of(
                match(1, "src/A.java", 1, 2, "a".repeat(20_000), 1.0),
                match(2, "src/B.java", 3, 4, "b".repeat(20_000), 1.0),
                match(3, "src/C.java", 5, 6, "c".repeat(20_000), 1.0));
        RagContextAssembler assembler = new RagContextAssembler(new CapturingRerankClient());

        RagEvidenceBundle first = assembler.assemble("query", "head-sha", matches);
        RagEvidenceBundle second = assembler.assemble("query", "head-sha", matches);

        assertThat(first.evidence().stream().mapToInt(item -> item.content().length()).sum()).isEqualTo(36_000);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void changedFilePreservationStillHonorsTotalContentBudget() {
        List<HybridRagRetrievalService.Match> matches = List.of(
                match(1, "src/A.java", 1, 2, "a".repeat(36_000), 1.0),
                match(2, "src/Changed.java", 3, 4, "b".repeat(50_000), 1.25));

        RagEvidenceBundle result = new RagContextAssembler(new CapturingRerankClient())
                .assemble("query", "head-sha", matches);

        assertThat(result.evidence()).extracting(RagEvidence::path).contains("src/Changed.java");
        assertThat(result.evidence().stream().mapToInt(item -> item.content().length()).sum())
                .isLessThanOrEqualTo(36_000);
    }

    @Test
    void rerankFailureUsesRrfOrderAndMarksDegradedWithoutLegacyFallback() {
        RerankClient unavailable = (query, candidates) -> {
            throw new IllegalStateException("Rerank request failed");
        };
        List<HybridRagRetrievalService.Match> matches = List.of(
                match(7, "src/Z.java", 1, 2, "first", 1.0),
                match(8, "src/A.java", 3, 4, "second", 1.0));

        RagEvidenceBundle result = new RagContextAssembler(unavailable)
                .assemble("query", "head-sha", matches);

        assertThat(result.evidence()).extracting(RagEvidence::path)
                .containsExactly("src/Z.java", "src/A.java");
        assertThat(result.degraded()).isTrue();
        assertThat(result.reason()).isEqualTo(RagEvidenceBundle.DegradedReason.RERANK_UNAVAILABLE);
        assertThat(result.legacyFallbackRequired()).isFalse();
    }

    @Test
    void formatsExactPromptBlocksWithoutExposingDatabaseIds() {
        CapturingRerankClient reranker = new CapturingRerankClient();
        HybridRagRetrievalService.Match match = match(918273645, "src/main/java/example/AuthService.java",
                42, 78, "bounded code", 1.25);

        RagEvidenceBundle result = new RagContextAssembler(reranker)
                .assemble("query", "head-sha", List.of(match));

        assertThat(result.promptBlock()).isEqualTo("""
                [EVIDENCE C1]
                path: src/main/java/example/AuthService.java
                lines: 42-78
                commit: head-sha
                content:
                bounded code
                [/EVIDENCE C1]""");
        assertThat(result.promptBlock()).doesNotContain("918273645");
        assertThat(result.evidence().get(0).toString()).doesNotContain("918273645");
        assertThat(reranker.received).extracting(RerankCandidate::chunkId).doesNotContain("918273645");
    }

    private static HybridRagRetrievalService.Match match(long id, String path, int startLine, int endLine,
                                                           String content, double pathBoost) {
        return new HybridRagRetrievalService.Match(id, path, "JAVA", "symbol", startLine, endLine,
                "hash-" + id, content, pathBoost, 1.0 / (60 + id));
    }

    private static String words(int count, String prefix) {
        Set<String> words = new HashSet<>();
        for (int index = 0; index < count; index++) {
            words.add(prefix + index);
        }
        return String.join(" ", words);
    }

    private static final class CapturingRerankClient implements RerankClient {
        private List<RerankCandidate> received = List.of();

        @Override
        public List<RerankedChunk> rerank(String query, List<RerankCandidate> candidates) {
            received = List.copyOf(candidates);
            List<RerankedChunk> result = new ArrayList<>();
            for (int index = 0; index < candidates.size(); index++) {
                result.add(new RerankedChunk(candidates.get(index), candidates.size() - index));
            }
            return result;
        }
    }
}
