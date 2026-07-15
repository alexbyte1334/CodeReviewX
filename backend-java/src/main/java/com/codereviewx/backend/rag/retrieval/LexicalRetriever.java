package com.codereviewx.backend.rag.retrieval;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

public final class LexicalRetriever {

    public static final int MAX_CANDIDATES = 40;
    private final NamedParameterJdbcTemplate jdbc;

    public LexicalRetriever(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ReciprocalRankFusion.Candidate> retrieve(HybridRagRetrievalService.SnapshotIdentity snapshot,
                                                         String query, List<String> changedPaths) {
        MapSqlParameterSource parameters = snapshot.parameters()
                .addValue("query", query.replace("\n", " OR "))
                .addValue("changedPaths", String.join("\n", changedPaths))
                .addValue("changedDirectories", String.join("\n", PathBoost.directories(changedPaths)));
        return jdbc.query("""
                WITH changed_path(path) AS (
                    SELECT unnest(string_to_array(:changedPaths, E'\\n'))
                ), changed_directory(directory) AS (
                    SELECT unnest(string_to_array(:changedDirectories, E'\\n'))
                ), requested_query AS (
                    SELECT websearch_to_tsquery('simple', :query) AS value
                ), scoped AS (
                    SELECT chunk.id, chunk.path, chunk.language, chunk.symbol_name, chunk.start_line, chunk.end_line,
                           chunk.content_hash, chunk.content,
                           CASE WHEN EXISTS (SELECT 1 FROM changed_path WHERE changed_path.path=chunk.path) THEN 1.25
                                WHEN EXISTS (SELECT 1 FROM changed_directory
                                             WHERE starts_with(chunk.path, changed_directory.directory || '/')) THEN 1.10
                                ELSE 1.0 END AS path_boost,
                           ts_rank_cd(chunk.search_vector, requested_query.value) AS lexical_score
                    FROM rag_chunk chunk
                    JOIN rag_index_snapshot snapshot ON snapshot.id=chunk.snapshot_id
                    JOIN rag_index_job job ON job.id=snapshot.job_id AND job.status='READY'
                    CROSS JOIN requested_query
                    WHERE chunk.repository_id=:repositoryId
                      AND chunk.commit_sha=:commitSha
                      AND snapshot.repository_id=:repositoryId
                      AND snapshot.commit_sha=:commitSha
                      AND snapshot.embedding_model=:embeddingModel
                      AND snapshot.embedding_dimensions=:embeddingDimensions
                      AND snapshot.index_version=:indexVersion
                      AND chunk.search_vector @@ requested_query.value
                )
                SELECT * FROM scoped
                ORDER BY lexical_score * path_boost DESC, path, id
                LIMIT 40
                """, parameters, (result, row) -> new ReciprocalRankFusion.Candidate(
                result.getLong("id"), result.getString("path"), result.getString("language"),
                result.getString("symbol_name"), result.getInt("start_line"), result.getInt("end_line"),
                result.getString("content_hash"), result.getString("content"), result.getDouble("path_boost")));
    }
}

final class PathBoost {
    private PathBoost() {
    }

    static List<String> directories(List<String> paths) {
        return paths.stream().map(path -> {
            int separator = path.lastIndexOf('/');
            return separator < 0 ? "" : path.substring(0, separator);
        }).filter(directory -> !directory.isBlank()).distinct().toList();
    }
}
