package com.codereviewx.backend.rag.persistence;

import com.codereviewx.backend.rag.model.CodeChunk;
import com.pgvector.PGvector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
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

    public Map<ChunkSignature, Long> findReusableChunks(long repositoryId, String commitSha, String path) {
        Map<ChunkSignature, Long> reusable = new HashMap<>();
        jdbc.query("""
                SELECT id, start_line, end_line, content_hash
                FROM rag_chunk
                WHERE repository_id=? AND commit_sha=? AND path=?
                """, (result, row) -> Map.entry(new ChunkSignature(result.getInt("start_line"),
                        result.getInt("end_line"), result.getString("content_hash")), result.getLong("id")),
                repositoryId, commitSha, path).forEach(entry -> reusable.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(reusable);
    }

    public int copyChunks(long repositoryId, List<Long> sourceChunkIds, long targetDocumentId,
                          String targetCommitSha) {
        if (sourceChunkIds.isEmpty()) {
            return 0;
        }
        if (sourceChunkIds.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("RAG chunk copy batch exceeds 100 entries");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(sourceChunkIds.size(), "?"));
        List<Object> parameters = new java.util.ArrayList<>();
        parameters.add(targetDocumentId);
        parameters.add(targetCommitSha);
        parameters.add(LocalDateTime.now(ZoneOffset.UTC));
        parameters.add(repositoryId);
        parameters.addAll(sourceChunkIds);
        return jdbc.update("""
                INSERT INTO rag_chunk
                  (repository_id, document_id, commit_sha, chunk_key, path, language, symbol_name,
                   start_line, end_line, content, token_count, content_hash, embedding, created_at)
                SELECT repository_id, ?, ?, chunk_key, path, language, symbol_name,
                       start_line, end_line, content, token_count, content_hash, embedding, ?
                FROM rag_chunk
                WHERE repository_id=? AND id IN (%s)
                """.formatted(placeholders), parameters.toArray());
    }

    public void insertBatch(long repositoryId, long documentId, String commitSha, List<EmbeddedChunk> values) {
        if (values.isEmpty()) {
            return;
        }
        if (values.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("RAG chunk batch exceeds 100 entries");
        }
        jdbc.batchUpdate("""
                INSERT INTO rag_chunk
                  (repository_id, document_id, commit_sha, chunk_key, path, language, symbol_name,
                   start_line, end_line, content, token_count, content_hash, embedding, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, values, values.size(), (statement, value) -> {
            CodeChunk chunk = value.chunk();
            statement.setLong(1, repositoryId);
            statement.setLong(2, documentId);
            statement.setString(3, commitSha);
            statement.setString(4, chunk.chunkKey());
            statement.setString(5, chunk.path());
            statement.setString(6, chunk.language().name());
            statement.setString(7, chunk.symbolName());
            statement.setInt(8, chunk.startLine());
            statement.setInt(9, chunk.endLine());
            statement.setString(10, chunk.content());
            statement.setInt(11, chunk.tokenCount());
            statement.setString(12, chunk.contentHash());
            statement.setObject(13, new PGvector(value.embedding()));
            statement.setTimestamp(14, Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)));
        });
    }

    public record EmbeddedChunk(CodeChunk chunk, float[] embedding) {
    }

    public record ChunkSignature(int startLine, int endLine, String contentHash) {
        public static ChunkSignature of(CodeChunk chunk) {
            return new ChunkSignature(chunk.startLine(), chunk.endLine(), chunk.contentHash());
        }
    }
}
