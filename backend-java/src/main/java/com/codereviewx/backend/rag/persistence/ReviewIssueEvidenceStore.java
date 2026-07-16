package com.codereviewx.backend.rag.persistence;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.retrieval.RagEvidence;
import com.codereviewx.backend.rag.retrieval.RagEvidenceBundle;
import com.codereviewx.backend.review.persistence.entity.ReviewIssueEntity;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class ReviewIssueEvidenceStore {
    private final JdbcTemplate jdbc; private final RagProperties properties;
    public ReviewIssueEvidenceStore(JdbcTemplate jdbc, RagProperties properties) { this.jdbc = jdbc; this.properties = properties; }
    public void save(ReviewIssueEntity issue, ReviewFinding finding, RagEvidenceBundle bundle) {
        if (!properties.isEnabled() || bundle == null) return;
        Map<String, RagEvidence> byLabel = bundle.evidence().stream().collect(Collectors.toMap(RagEvidence::label, Function.identity()));
        int rank = 1;
        for (String label : finding.getEvidenceChunkIds()) {
            RagEvidence evidence = byLabel.get(label); if (evidence == null) continue;
            String excerpt = evidence.content().substring(0, Math.min(2000, evidence.content().length()));
            jdbc.update("INSERT INTO review_issue_evidence(review_issue_id,rag_chunk_id,citation_label,path,start_line,end_line,content_hash,evidence_excerpt,retrieval_rank,retrieval_score,created_at) VALUES (?,NULL,?,?,?,?,?,?,?,?,?)",
                    issue.getId(), label, evidence.path(), evidence.startLine(), evidence.endLine(), hash(evidence.content()), excerpt,
                    rank++, evidence.score(), LocalDateTime.now());
        }
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
