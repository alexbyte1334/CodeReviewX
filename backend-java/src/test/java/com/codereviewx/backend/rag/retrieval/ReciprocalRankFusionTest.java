package com.codereviewx.backend.rag.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReciprocalRankFusionTest {

    private final ReciprocalRankFusion fusion = new ReciprocalRankFusion();

    @Test
    void usesOneBasedRanksAndDeduplicatesChunksAcrossRoutes() {
        ReciprocalRankFusion.Candidate shared = candidate(2, "src/Shared.java", 1.25);

        List<ReciprocalRankFusion.FusedCandidate> result = fusion.fuse(
                List.of(candidate(1, "src/Vector.java", 1.0), shared),
                List.of(shared, candidate(3, "src/Lexical.java", 1.0)));

        assertThat(result).extracting(item -> item.candidate().chunkId()).containsExactly(2L, 1L, 3L);
        assertThat(result.get(0).score()).isEqualTo(1.0 / 62.0 + 1.0 / 61.0);
        assertThat(result).filteredOn(item -> item.candidate().chunkId() == 2L).hasSize(1);
    }

    @Test
    void breaksEqualScoreTiesByChangedFileBoostThenPathThenChunkId() {
        List<ReciprocalRankFusion.FusedCandidate> result = fusion.fuse(
                List.of(candidate(9, "src/Z.java", 1.0), candidate(4, "src/B.java", 1.25),
                        candidate(3, "src/A.java", 1.25)),
                List.of());

        assertThat(result).extracting(item -> item.candidate().chunkId()).containsExactly(9L, 4L, 3L);

        List<ReciprocalRankFusion.FusedCandidate> tied = fusion.fuse(
                List.of(candidate(9, "src/Z.java", 1.0)),
                List.of(candidate(4, "src/B.java", 1.25)));
        assertThat(tied).extracting(item -> item.candidate().chunkId()).containsExactly(4L, 9L);

        List<ReciprocalRankFusion.FusedCandidate> pathTie = fusion.fuse(
                List.of(candidate(8, "src/A.java", 1.0)),
                List.of(candidate(7, "src/A.java", 1.0)));
        assertThat(pathTie).extracting(item -> item.candidate().chunkId()).containsExactly(7L, 8L);
    }

    private static ReciprocalRankFusion.Candidate candidate(long id, String path, double pathBoost) {
        return new ReciprocalRankFusion.Candidate(id, path, "JAVA", "symbol", 1, 3,
                "hash-" + id, "content-" + id, pathBoost);
    }
}
