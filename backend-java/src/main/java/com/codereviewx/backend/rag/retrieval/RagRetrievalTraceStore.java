package com.codereviewx.backend.rag.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class RagRetrievalTraceStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public RagRetrievalTraceStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public void save(long reviewRunId, long repositoryId, String commitSha, String query,
                     HybridRagRetrievalService.Result result, int selectedCount, int contextChars,
                     long latencyMs) {
        try {
            String summary = mapper.writeValueAsString(result.matches().stream().map(match -> Map.of(
                    "chunkId", match.chunkId(), "path", match.path(), "startLine", match.startLine(),
                    "endLine", match.endLine(), "score", match.fusedScore())).toList());
            jdbc.update("""
                    INSERT INTO rag_retrieval_trace
                    (review_run_id, repository_id, commit_sha, query_hash,
                     vector_candidate_count, lexical_candidate_count, reranked_count,
                     selected_count, context_char_count, degraded, latency_ms,
                     result_summary_json, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, reviewRunId, repositoryId, commitSha, sha256(query),
                    result.vectorCandidateCount(), result.lexicalCandidateCount(), result.matches().size(),
                    selectedCount, contextChars, result.status() != HybridRagRetrievalService.Status.READY
                            || result.retrievalHealth() != RagContextAssembler.RetrievalHealth.HEALTHY,
                    Math.max(0, latencyMs), summary, LocalDateTime.now());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist RAG retrieval trace", exception);
        }
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash retrieval query", exception);
        }
    }
}
