package com.codereviewx.backend.rag;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresRagMigrationTest {

    private static final Set<String> EXPECTED_RAG_TABLES = Set.of(
            "rag_repository",
            "rag_index_job",
            "rag_document",
            "rag_chunk",
            "rag_retrieval_trace",
            "review_issue_evidence"
    );

    private static final Map<String, Set<String>> EXPECTED_COLUMNS = Map.of(
            "rag_repository", Set.of(
                    "id", "provider", "owner_name", "repository_name", "clone_url", "default_branch",
                    "active_commit_sha", "index_status", "index_version", "embedding_model",
                    "embedding_dimensions", "last_indexed_at", "created_at", "updated_at"
            ),
            "rag_index_job", Set.of(
                    "id", "repository_id", "requested_ref", "resolved_commit_sha", "trigger_type", "status",
                    "attempt_count", "discovered_file_count", "indexed_file_count", "indexed_chunk_count",
                    "skipped_file_count", "error_code", "error_message", "started_at", "finished_at", "created_at"
            ),
            "rag_document", Set.of(
                    "id", "repository_id", "commit_sha", "path", "language", "content_hash", "byte_size",
                    "created_at"
            ),
            "rag_chunk", Set.of(
                    "id", "repository_id", "document_id", "commit_sha", "chunk_key", "path", "language",
                    "symbol_name", "start_line", "end_line", "content", "token_count", "content_hash",
                    "embedding", "search_vector", "created_at"
            ),
            "rag_retrieval_trace", Set.of(
                    "id", "review_run_id", "repository_id", "commit_sha", "query_hash",
                    "vector_candidate_count", "lexical_candidate_count", "reranked_count", "selected_count",
                    "context_char_count", "degraded", "latency_ms", "result_summary_json", "created_at"
            ),
            "review_issue_evidence", Set.of(
                    "id", "review_issue_id", "rag_chunk_id", "citation_label", "path", "start_line", "end_line",
                    "content_hash", "evidence_excerpt", "retrieval_rank", "retrieval_score", "created_at"
            )
    );

    @Test
    void migratesPostgresRagSchemaWithVectorAndSearchIndexes() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")) {
            postgres.start();

            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration", "classpath:db/rag/postgresql")
                    .initSql("CREATE SCHEMA IF NOT EXISTS flyway_compat; "
                            + "DO $$ BEGIN CREATE DOMAIN flyway_compat.CLOB AS TEXT; "
                            + "EXCEPTION WHEN duplicate_object THEN NULL; END $$; "
                            + "SET search_path TO public, flyway_compat")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement()) {
                assertThat(queryNames(statement, """
                        SELECT tablename
                        FROM pg_tables
                        WHERE schemaname = 'public'
                          AND tablename IN ('rag_repository', 'rag_index_job', 'rag_document',
                                            'rag_chunk', 'rag_retrieval_trace', 'review_issue_evidence')
                        """))
                        .containsExactlyInAnyOrderElementsOf(EXPECTED_RAG_TABLES);

                for (Map.Entry<String, Set<String>> expectedColumns : EXPECTED_COLUMNS.entrySet()) {
                    assertThat(queryNames(statement, """
                            SELECT column_name
                            FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = '%s'
                            """.formatted(expectedColumns.getKey())))
                            .as("columns for %s", expectedColumns.getKey())
                            .containsExactlyInAnyOrderElementsOf(expectedColumns.getValue());
                }

                assertThat(queryNames(statement, """
                        SELECT extname
                        FROM pg_extension
                        WHERE extname = 'vector'
                        """))
                        .containsExactly("vector");

                assertThat(queryNames(statement, """
                        SELECT indexname
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND indexname IN ('idx_rag_chunk_embedding_hnsw',
                                            'idx_rag_chunk_search_vector_gin',
                                            'idx_rag_chunk_snapshot')
                        """))
                        .containsExactlyInAnyOrder(
                                "idx_rag_chunk_embedding_hnsw",
                                "idx_rag_chunk_search_vector_gin",
                                "idx_rag_chunk_snapshot"
                        );
            }
        }
    }

    private Set<String> queryNames(Statement statement, String sql) throws Exception {
        Set<String> names = new TreeSet<>();
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                names.add(resultSet.getString(1));
            }
        }
        return names;
    }
}
