package com.codereviewx.backend.rag.persistence;

import com.codereviewx.backend.rag.model.CodeChunk;
import com.pgvector.PGvector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@ConditionalOnProperty(prefix = "codereviewx.rag", name = "enabled", havingValue = "true")
public class RagChunkStore {

    public static final int MAX_BATCH_SIZE = 100;

    private final JdbcTemplate jdbc;

    public RagChunkStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Long> findDocumentChunkIds(long repositoryId, long documentId, long afterId) {
        return jdbc.queryForList("""
                SELECT id FROM rag_chunk
                WHERE repository_id=? AND document_id=? AND id>?
                ORDER BY id
                LIMIT 100
                """, Long.class, repositoryId, documentId, afterId);
    }

    public Map<String, List<Long>> findReusableChunks(long snapshotId, String path) {
        Map<String, List<Long>> reusable = new LinkedHashMap<>();
        jdbc.query("""
                SELECT id, start_line, end_line, content_hash
                FROM rag_chunk
                WHERE snapshot_id=? AND path=?
                ORDER BY start_line, end_line, id
                """, (result, row) -> Map.entry(result.getString("content_hash"), result.getLong("id")),
                snapshotId, path).forEach(entry -> reusable
                .computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(entry.getValue()));
        Map<String, List<Long>> immutable = new LinkedHashMap<>();
        reusable.forEach((hash, ids) -> immutable.put(hash, List.copyOf(ids)));
        return Map.copyOf(immutable);
    }

    public int copyChunks(long repositoryId, long targetSnapshotId, List<Long> sourceChunkIds, long targetDocumentId,
                          String targetCommitSha) {
        if (sourceChunkIds.isEmpty()) {
            return 0;
        }
        if (sourceChunkIds.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("RAG chunk copy batch exceeds 100 entries");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(sourceChunkIds.size(), "?"));
        List<Object> parameters = new java.util.ArrayList<>();
        parameters.add(targetSnapshotId);
        parameters.add(targetDocumentId);
        parameters.add(targetCommitSha);
        parameters.add(LocalDateTime.now(ZoneOffset.UTC));
        parameters.add(repositoryId);
        parameters.addAll(sourceChunkIds);
        return jdbc.update("""
                INSERT INTO rag_chunk
                  (repository_id, snapshot_id, document_id, commit_sha, chunk_key, path, language, symbol_name,
                   start_line, end_line, content, token_count, content_hash, embedding, created_at)
                SELECT repository_id, ?, ?, ?, chunk_key, path, language, symbol_name,
                       start_line, end_line, content, token_count, content_hash, embedding, ?
                FROM rag_chunk
                WHERE repository_id=? AND id IN (%s)
                """.formatted(placeholders), parameters.toArray());
    }

    public void insertBatch(long repositoryId, long snapshotId, long documentId, String commitSha,
                            List<EmbeddedChunk> values) {
        if (values.isEmpty()) {
            return;
        }
        if (values.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("RAG chunk batch exceeds 100 entries");
        }
        jdbc.batchUpdate("""
                INSERT INTO rag_chunk
                  (repository_id, snapshot_id, document_id, commit_sha, chunk_key, path, language, symbol_name,
                   start_line, end_line, content, token_count, content_hash, embedding, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, values, values.size(), (statement, value) -> {
            CodeChunk chunk = value.chunk();
            statement.setLong(1, repositoryId);
            statement.setLong(2, snapshotId);
            statement.setLong(3, documentId);
            statement.setString(4, commitSha);
            statement.setString(5, chunk.chunkKey());
            statement.setString(6, chunk.path());
            statement.setString(7, chunk.language().name());
            statement.setString(8, chunk.symbolName());
            statement.setInt(9, chunk.startLine());
            statement.setInt(10, chunk.endLine());
            statement.setString(11, chunk.content());
            statement.setInt(12, chunk.tokenCount());
            statement.setString(13, chunk.contentHash());
            statement.setObject(14, new PGvector(value.embedding()));
            statement.setTimestamp(15, Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)));
        });
    }

    public void insertReusedBatch(long repositoryId, long snapshotId, long documentId, String commitSha,
                                  List<ReusedChunk> values) {
        if (values.isEmpty()) {
            return;
        }
        if (values.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("RAG reused chunk batch exceeds 100 entries");
        }
        jdbc.batchUpdate("""
                INSERT INTO rag_chunk
                  (repository_id, snapshot_id, document_id, commit_sha, chunk_key, path, language, symbol_name,
                   start_line, end_line, content, token_count, content_hash, embedding, created_at)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, source.embedding, ?
                FROM rag_chunk source
                WHERE source.id=? AND source.repository_id=?
                """, values, values.size(), (statement, value) -> {
            CodeChunk chunk = value.chunk();
            statement.setLong(1, repositoryId);
            statement.setLong(2, snapshotId);
            statement.setLong(3, documentId);
            statement.setString(4, commitSha);
            statement.setString(5, chunk.chunkKey());
            statement.setString(6, chunk.path());
            statement.setString(7, chunk.language().name());
            statement.setString(8, chunk.symbolName());
            statement.setInt(9, chunk.startLine());
            statement.setInt(10, chunk.endLine());
            statement.setString(11, chunk.content());
            statement.setInt(12, chunk.tokenCount());
            statement.setString(13, chunk.contentHash());
            statement.setTimestamp(14, Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)));
            statement.setLong(15, value.sourceChunkId());
            statement.setLong(16, repositoryId);
        });
    }

    public record EmbeddedChunk(CodeChunk chunk, float[] embedding) {
    }

    public record ReusedChunk(CodeChunk chunk, long sourceChunkId) {
    }
}
