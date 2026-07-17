package com.codereviewx.backend.rag.persistence;

import com.codereviewx.backend.rag.model.RepositoryFile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "codereviewx.rag", name = "enabled", havingValue = "true")
public class RagDocumentStore {

    private final JdbcTemplate jdbc;

    public RagDocumentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<DocumentRecord> find(long snapshotId, String path, String contentHash) {
        List<DocumentRecord> rows = jdbc.query("""
                SELECT id, content_hash FROM rag_document
                WHERE snapshot_id=? AND path=? AND content_hash=?
                """, (result, row) -> new DocumentRecord(result.getLong("id"), result.getString("content_hash")),
                snapshotId, path, contentHash);
        return rows.stream().findFirst();
    }

    public long insert(long repositoryId, long snapshotId, String commitSha, RepositoryFile file) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO rag_document
                      (repository_id, snapshot_id, commit_sha, path, language, content_hash, byte_size, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, repositoryId);
            statement.setLong(2, snapshotId);
            statement.setString(3, commitSha);
            statement.setString(4, file.path());
            statement.setString(5, file.language().name());
            statement.setString(6, file.contentHash());
            statement.setLong(7, file.byteSize());
            statement.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)));
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    public record DocumentRecord(long id, String contentHash) {
    }
}
