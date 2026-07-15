package com.codereviewx.backend.rag.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class RagContextAssembler {

    private static final int MAX_RERANK_INPUT = 30;
    private static final int MAX_EVIDENCE = 12;
    private static final int MAX_PER_FILE = 3;
    private static final int MAX_CONTENT_CHARACTERS = 36_000;
    private static final double ADJACENT_OVERLAP_THRESHOLD = 0.85;
    private static final double EXACT_CHANGED_PATH_BOOST = 1.25;
    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[^A-Za-z0-9_]+");

    private final RerankClient rerankClient;

    public RagContextAssembler(RerankClient rerankClient) {
        this.rerankClient = Objects.requireNonNull(rerankClient, "rerankClient");
    }

    public RagEvidenceBundle assemble(String query, String commitSha,
                                      List<HybridRagRetrievalService.Match> candidates) {
        return assemble(query, commitSha, candidates, RetrievalHealth.HEALTHY);
    }

    public RagEvidenceBundle assemble(String query, String commitSha,
                                      List<HybridRagRetrievalService.Match> candidates,
                                      RetrievalHealth retrievalHealth) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(retrievalHealth, "retrievalHealth");
        if (retrievalHealth.requiresLegacyFallback()) {
            return new RagEvidenceBundle(List.of(), "", RagEvidenceBundle.DegradedReason.NONE, retrievalHealth);
        }
        List<HybridRagRetrievalService.Match> input = boundedInput(candidates);
        List<ScoredMatch> ranked;
        boolean rerankUnavailable = false;
        try {
            ranked = rerank(input, query);
        } catch (RuntimeException unavailable) {
            ranked = input.stream().map(match -> new ScoredMatch(match, match.fusedScore())).toList();
            rerankUnavailable = true;
        }
        List<ScoredMatch> deduplicated = removeAdjacentRedundancy(ranked);
        List<ScoredMatch> selected = select(deduplicated);
        List<RagEvidence> evidence = labelAndBound(selected, commitSha);
        String prompt = evidence.stream().map(this::format).reduce((left, right) -> left + "\n" + right).orElse("");
        return new RagEvidenceBundle(evidence, prompt,
                rerankUnavailable ? RagEvidenceBundle.DegradedReason.RERANK_UNAVAILABLE
                        : RagEvidenceBundle.DegradedReason.NONE,
                retrievalHealth);
    }

    private List<HybridRagRetrievalService.Match> boundedInput(List<HybridRagRetrievalService.Match> candidates) {
        List<HybridRagRetrievalService.Match> input = new ArrayList<>(candidates.subList(0,
                Math.min(MAX_RERANK_INPUT, candidates.size())));
        if (candidates.size() > input.size() && input.stream().noneMatch(this::isExactChanged)) {
            candidates.stream().skip(input.size()).filter(this::isExactChanged).findFirst().ifPresent(changed -> {
                input.remove(input.size() - 1);
                input.add(changed);
            });
        }
        return List.copyOf(input);
    }

    private List<ScoredMatch> rerank(List<HybridRagRetrievalService.Match> input, String query) {
        List<RerankCandidate> request = new ArrayList<>(input.size());
        Map<String, HybridRagRetrievalService.Match> byId = new HashMap<>();
        for (int index = 0; index < input.size(); index++) {
            String requestId = "R" + (index + 1);
            request.add(new RerankCandidate(requestId, input.get(index).content()));
            byId.put(requestId, input.get(index));
        }
        List<RerankedChunk> response = rerankClient.rerank(query, request);
        if (response == null || response.size() != request.size()) {
            throw new IllegalStateException("Rerank response is unavailable");
        }
        Set<String> seen = new HashSet<>();
        List<ScoredMatch> result = new ArrayList<>(response.size());
        for (RerankedChunk item : response) {
            String id = item.candidate().chunkId();
            HybridRagRetrievalService.Match match = byId.get(id);
            if (match == null || !seen.add(id)) {
                throw new IllegalStateException("Rerank response is invalid");
            }
            result.add(new ScoredMatch(match, item.score()));
        }
        return List.copyOf(result);
    }

    private List<ScoredMatch> removeAdjacentRedundancy(List<ScoredMatch> ranked) {
        if (ranked.size() < 2) {
            return ranked;
        }
        int protectedChanged = -1;
        for (int index = 0; index < ranked.size(); index++) {
            if (isExactChanged(ranked.get(index).match)) {
                protectedChanged = index;
                break;
            }
        }
        boolean[] removed = new boolean[ranked.size()];
        for (int index = 0; index < ranked.size() - 1; index++) {
            ScoredMatch left = ranked.get(index);
            ScoredMatch right = ranked.get(index + 1);
            if (jaccard(left.match.content(), right.match.content()) <= ADJACENT_OVERLAP_THRESHOLD) {
                continue;
            }
            if (index == protectedChanged) {
                removed[index + 1] = true;
            } else if (index + 1 == protectedChanged) {
                removed[index] = true;
            } else if (left.score >= right.score) {
                removed[index + 1] = true;
            } else {
                removed[index] = true;
            }
        }
        List<ScoredMatch> result = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            if (!removed[index]) {
                result.add(ranked.get(index));
            }
        }
        return List.copyOf(result);
    }

    private List<ScoredMatch> select(List<ScoredMatch> ranked) {
        List<ScoredMatch> selected = new ArrayList<>();
        Map<String, Integer> perFile = new LinkedHashMap<>();
        int characters = 0;
        for (ScoredMatch item : ranked) {
            String path = item.match.path();
            if (selected.size() >= MAX_EVIDENCE || perFile.getOrDefault(path, 0) >= MAX_PER_FILE) {
                continue;
            }
            int remaining = MAX_CONTENT_CHARACTERS - characters;
            if (remaining <= 0) {
                break;
            }
            int boundedLength = Math.min(item.match.content().length(), remaining);
            if (boundedLength == 0) {
                continue;
            }
            selected.add(new ScoredMatch(withContent(item.match, item.match.content().substring(0, boundedLength)), item.score));
            perFile.merge(path, 1, Integer::sum);
            characters += boundedLength;
        }
        if (selected.stream().noneMatch(item -> isExactChanged(item.match))
                && ranked.stream().anyMatch(item -> isExactChanged(item.match))) {
            ScoredMatch changed = ranked.stream().filter(item -> isExactChanged(item.match)).findFirst().orElseThrow();
            int replacement = -1;
            for (int index = selected.size() - 1; index >= 0; index--) {
                if (!isExactChanged(selected.get(index).match)) {
                    replacement = index;
                    break;
                }
            }
            if (replacement >= 0) {
                int usedWithoutReplacement = selected.stream()
                        .mapToInt(item -> item.match.content().length()).sum()
                        - selected.get(replacement).match.content().length();
                int remaining = MAX_CONTENT_CHARACTERS - usedWithoutReplacement;
                if (remaining > 0) {
                    String bounded = changed.match.content().substring(0,
                            Math.min(changed.match.content().length(), remaining));
                    selected.set(replacement, new ScoredMatch(withContent(changed.match, bounded), changed.score));
                    Map<Long, Integer> rankIndex = new HashMap<>();
                    for (int index = 0; index < ranked.size(); index++) {
                        rankIndex.put(ranked.get(index).match.chunkId(), index);
                    }
                    selected.sort(Comparator.comparingInt(item -> rankIndex.get(item.match.chunkId())));
                }
            }
        }
        return List.copyOf(selected);
    }

    private List<RagEvidence> labelAndBound(List<ScoredMatch> selected, String commitSha) {
        List<RagEvidence> result = new ArrayList<>(selected.size());
        for (int index = 0; index < selected.size(); index++) {
            HybridRagRetrievalService.Match match = selected.get(index).match;
            result.add(new RagEvidence("C" + (index + 1), match.path(), match.startLine(), match.endLine(),
                    commitSha, match.content(), selected.get(index).score));
        }
        return List.copyOf(result);
    }

    private String format(RagEvidence evidence) {
        return "[EVIDENCE " + evidence.label() + "]\n"
                + "path: " + evidence.path() + "\n"
                + "lines: " + evidence.startLine() + "-" + evidence.endLine() + "\n"
                + "commit: " + evidence.commitSha() + "\n"
                + "content:\n" + evidence.content() + "\n"
                + "[/EVIDENCE " + evidence.label() + "]";
    }

    private boolean isExactChanged(HybridRagRetrievalService.Match match) {
        return Double.compare(match.pathBoost(), EXACT_CHANGED_PATH_BOOST) == 0;
    }

    private static HybridRagRetrievalService.Match withContent(HybridRagRetrievalService.Match match, String content) {
        return new HybridRagRetrievalService.Match(match.chunkId(), match.path(), match.language(), match.symbolName(),
                match.startLine(), match.endLine(), match.contentHash(), content, match.pathBoost(), match.fusedScore());
    }

    private static double jaccard(String left, String right) {
        Set<String> first = tokens(left);
        Set<String> second = tokens(right);
        if (first.isEmpty() && second.isEmpty()) {
            return 1.0;
        }
        Set<String> intersection = new HashSet<>(first);
        intersection.retainAll(second);
        Set<String> union = new HashSet<>(first);
        union.addAll(second);
        return (double) intersection.size() / union.size();
    }

    private static Set<String> tokens(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (String token : TOKEN_SPLITTER.split(value.toLowerCase(Locale.ROOT))) {
            if (!token.isBlank()) {
                result.add(token);
            }
        }
        return result;
    }

    private record ScoredMatch(HybridRagRetrievalService.Match match, double score) {
    }

    public enum RetrievalHealth {
        HEALTHY,
        SINGLE_ROUTE_FAILED,
        EMBEDDING_FAILED,
        BOTH_ROUTES_FAILED;

        boolean requiresLegacyFallback() {
            return this == EMBEDDING_FAILED || this == BOTH_ROUTES_FAILED;
        }
    }
}
