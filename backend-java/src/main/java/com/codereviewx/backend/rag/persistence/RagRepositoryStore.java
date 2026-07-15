package com.codereviewx.backend.rag.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "codereviewx.rag", name = "enabled", havingValue = "true")
public class RagRepositoryStore {

    private final JdbcTemplate jdbc;

    public RagRepositoryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RepositoryRecord ensure(String provider, String owner, String name, String cloneUrl,
                                   String defaultBranch, String embeddingModel, int dimensions, int indexVersion) {
        Optional<RepositoryRecord> existing = find(provider, owner, name);
        if (existing.isPresent()) {
            jdbc.update("""
                    UPDATE rag_repository
                    SET clone_url=?, default_branch=?, updated_at=?
                    WHERE id=?
                    """, cloneUrl, defaultBranch, now(), existing.get().id());
            return get(existing.get().id()).orElseThrow();
        }
        LocalDateTime now = now();
        jdbc.update("""
                INSERT INTO rag_repository
                  (provider, owner_name, repository_name, clone_url, default_branch, index_status,
                   index_version, embedding_model, embedding_dimensions, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'QUEUED', ?, ?, ?, ?, ?)
                ON CONFLICT (provider, owner_name, repository_name) DO NOTHING
                """, provider, owner, name, cloneUrl, defaultBranch, indexVersion, embeddingModel,
                dimensions, now, now);
        RepositoryRecord record = find(provider, owner, name).orElseThrow();
        jdbc.update("UPDATE rag_repository SET clone_url=?, default_branch=?, updated_at=? WHERE id=?",
                cloneUrl, defaultBranch, now(), record.id());
        return get(record.id()).orElseThrow();
    }

    public Optional<RepositoryRecord> find(String provider, String owner, String name) {
        List<RepositoryRecord> records = jdbc.query("""
                SELECT id, provider, owner_name, repository_name, clone_url, default_branch,
                       active_commit_sha, index_status, index_version, embedding_model, embedding_dimensions
                FROM rag_repository WHERE provider=? AND owner_name=? AND repository_name=?
                """, (result, row) -> map(result), provider, owner, name);
        return records.stream().findFirst();
    }

    public Optional<RepositoryRecord> get(long id) {
        return jdbc.query("""
                SELECT id, provider, owner_name, repository_name, clone_url, default_branch,
                       active_commit_sha, index_status, index_version, embedding_model, embedding_dimensions
                FROM rag_repository WHERE id=?
                """, (result, row) -> map(result), id).stream().findFirst();
    }

    public void activate(long id, String commitSha, String embeddingModel, int dimensions, int indexVersion) {
        int updated = jdbc.update("""
                UPDATE rag_repository
                SET active_commit_sha=?, index_status='READY', index_version=?, embedding_model=?,
                    embedding_dimensions=?, last_indexed_at=?, updated_at=?
                WHERE id=?
                """, commitSha, indexVersion, embeddingModel, dimensions, now(), now(), id);
        if (updated != 1) {
            throw new IllegalStateException("RAG repository no longer exists");
        }
    }

    public void markInitialFailure(long id) {
        jdbc.update("""
                UPDATE rag_repository SET index_status='FAILED', updated_at=?
                WHERE id=? AND active_commit_sha IS NULL
                """, now(), id);
    }

    private static RepositoryRecord map(java.sql.ResultSet result) throws java.sql.SQLException {
        return new RepositoryRecord(result.getLong("id"), result.getString("provider"),
                result.getString("owner_name"), result.getString("repository_name"),
                result.getString("clone_url"), result.getString("default_branch"),
                result.getString("active_commit_sha"), result.getString("index_status"),
                result.getInt("index_version"), result.getString("embedding_model"),
                result.getInt("embedding_dimensions"));
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public record RepositoryRecord(long id, String provider, String owner, String name, String cloneUrl,
                                   String defaultBranch, String activeCommitSha, String status,
                                   int indexVersion, String embeddingModel, int embeddingDimensions) {
        public boolean isReadyFor(String commitSha, String model, int dimensions, int version) {
            return "READY".equals(status) && commitSha.equals(activeCommitSha) && model.equals(embeddingModel)
                    && dimensions == embeddingDimensions && version == indexVersion;
        }
    }
}
