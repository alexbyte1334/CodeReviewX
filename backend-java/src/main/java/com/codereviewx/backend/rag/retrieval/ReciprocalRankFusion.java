package com.codereviewx.backend.rag.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReciprocalRankFusion {

    private static final int RANK_CONSTANT = 60;

    public List<FusedCandidate> fuse(List<Candidate> vectorRanking, List<Candidate> lexicalRanking) {
        Map<Long, MutableFusedCandidate> fused = new LinkedHashMap<>();
        addRoute(fused, vectorRanking);
        addRoute(fused, lexicalRanking);
        List<FusedCandidate> result = new ArrayList<>(fused.size());
        fused.values().forEach(item -> result.add(new FusedCandidate(item.candidate, item.score)));
        result.sort(Comparator.comparingDouble(FusedCandidate::score).reversed()
                .thenComparing(Comparator.comparingDouble(
                        (FusedCandidate item) -> item.candidate().pathBoost()).reversed())
                .thenComparing(item -> item.candidate().path())
                .thenComparingLong(item -> item.candidate().chunkId()));
        return List.copyOf(result);
    }

    private static void addRoute(Map<Long, MutableFusedCandidate> fused, List<Candidate> route) {
        if (route == null) {
            return;
        }
        for (int index = 0; index < route.size(); index++) {
            Candidate candidate = Objects.requireNonNull(route.get(index), "candidate");
            MutableFusedCandidate item = fused.computeIfAbsent(candidate.chunkId(),
                    ignored -> new MutableFusedCandidate(candidate));
            item.score += 1.0 / (RANK_CONSTANT + index + 1);
            if (candidate.pathBoost() > item.candidate.pathBoost()) {
                item.candidate = candidate;
            }
        }
    }

    public record Candidate(long chunkId, String path, String language, String symbolName,
                            int startLine, int endLine, String contentHash, String content, double pathBoost) {
        public Candidate {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(contentHash, "contentHash");
            Objects.requireNonNull(content, "content");
        }
    }

    public record FusedCandidate(Candidate candidate, double score) {
    }

    private static final class MutableFusedCandidate {
        private Candidate candidate;
        private double score;

        private MutableFusedCandidate(Candidate candidate) {
            this.candidate = candidate;
        }
    }
}
