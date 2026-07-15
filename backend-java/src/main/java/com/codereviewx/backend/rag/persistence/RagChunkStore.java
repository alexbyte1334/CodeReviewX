package com.codereviewx.backend.rag.persistence;

import com.codereviewx.backend.rag.model.CodeChunk;
import com.pgvector.PGvector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
@ConditionalOnProperty(prefix = "codereviewx.rag", name = "enabled", havingValue = "true")
public class RagChunkStore {

    public static final int MAX_BATCH_SIZE = 100;

    private final JdbcTemplate jdbc;

    public RagChunkStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int copyDocumentChunks(long repositoryId, long sourceDocumentId, long targetDocumentId,
                                  String targetCommitSha) {
        return jdbc.update("""
                INSERT INTO rag_chunk
                  (repository_id, document_id, commit_sha, chunk_key, path, language, symbol_name,
                   start_line, end_line, content, token_count, content_hash, embedding, created_at)
                SELECT repository_id, ?, ?, chunk_key, path, language, symbol_name,
                       start_line, end_line, content, token_count, content_hash, embedding, ?
                FROM rag_chunk WHERE repository_id=? AND document_id=?
                """, targetDocumentId, targetCommitSha, LocalDateTime.now(ZoneOffset.UTC),
                repositoryId, sourceDocumentId);
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
}
