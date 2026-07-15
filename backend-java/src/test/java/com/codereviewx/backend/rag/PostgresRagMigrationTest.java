package com.codereviewx.backend.rag;

import org.flywaydb.core.Flyway;
import org.assertj.core.api.SoftAssertions;
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
                SoftAssertions softly = new SoftAssertions();

                softly.assertThat(queryNames(statement, """
                        SELECT tablename
                        FROM pg_tables
                        WHERE schemaname = 'public'
                          AND tablename IN ('rag_repository', 'rag_index_job', 'rag_document',
                                            'rag_chunk', 'rag_retrieval_trace', 'review_issue_evidence')
                        """))
                        .containsExactlyInAnyOrderElementsOf(EXPECTED_RAG_TABLES);

                for (Map.Entry<String, Set<String>> expectedColumns : EXPECTED_COLUMNS.entrySet()) {
                    softly.assertThat(queryNames(statement, """
                            SELECT column_name
                            FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = '%s'
                            """.formatted(expectedColumns.getKey())))
                            .as("columns for %s", expectedColumns.getKey())
                            .containsExactlyInAnyOrderElementsOf(expectedColumns.getValue());
                }

                softly.assertThat(queryNames(statement, """
                        SELECT extname
                        FROM pg_extension
                        WHERE extname = 'vector'
                        """))
                        .containsExactly("vector");

                softly.assertThat(queryNames(statement, """
                        SELECT table_name
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name IN ('rag_repository', 'rag_index_job', 'rag_document',
                                             'rag_chunk', 'rag_retrieval_trace', 'review_issue_evidence')
                          AND column_name = 'id'
                          AND data_type = 'bigint'
                          AND is_nullable = 'NO'
                          AND column_default LIKE 'nextval(%'
                        """))
                        .as("BIGSERIAL id columns")
                        .containsExactlyInAnyOrderElementsOf(EXPECTED_RAG_TABLES);

                softly.assertThat(queryNames(statement, """
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN ('rag_repository', 'rag_index_job', 'rag_document',
                                             'rag_chunk', 'rag_retrieval_trace', 'review_issue_evidence')
                          AND pg_get_serial_sequence('public.' || table_name, 'id') IS NOT NULL
                        """))
                        .as("serial sequences")
                        .containsExactlyInAnyOrderElementsOf(EXPECTED_RAG_TABLES);

                softly.assertThat(queryNames(statement, """
                        SELECT conrelid::regclass::text
                        FROM pg_constraint
                        WHERE contype = 'p'
                          AND pg_get_constraintdef(oid) = 'PRIMARY KEY (id)'
                          AND conrelid IN ('rag_repository'::regclass, 'rag_index_job'::regclass,
                                           'rag_document'::regclass, 'rag_chunk'::regclass,
                                           'rag_retrieval_trace'::regclass,
                                           'review_issue_evidence'::regclass)
                        """))
                        .as("id primary keys")
                        .containsExactlyInAnyOrderElementsOf(EXPECTED_RAG_TABLES);

                softly.assertThat(queryNames(statement, """
                        SELECT table_name || '.' || column_name || '=' || column_default
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name IN ('rag_repository', 'rag_index_job', 'rag_document',
                                             'rag_chunk', 'rag_retrieval_trace', 'review_issue_evidence')
                          AND column_default IS NOT NULL
                          AND is_generated = 'NEVER'
                        """))
                        .as("column defaults")
                        .containsExactlyInAnyOrder(
                                "rag_repository.id=nextval('rag_repository_id_seq'::regclass)",
                                "rag_repository.index_version=1",
                                "rag_index_job.id=nextval('rag_index_job_id_seq'::regclass)",
                                "rag_index_job.attempt_count=0",
                                "rag_index_job.discovered_file_count=0",
                                "rag_index_job.indexed_file_count=0",
                                "rag_index_job.indexed_chunk_count=0",
                                "rag_index_job.skipped_file_count=0",
                                "rag_document.id=nextval('rag_document_id_seq'::regclass)",
                                "rag_chunk.id=nextval('rag_chunk_id_seq'::regclass)",
                                "rag_retrieval_trace.id=nextval('rag_retrieval_trace_id_seq'::regclass)",
                                "review_issue_evidence.id=nextval('review_issue_evidence_id_seq'::regclass)"
                        );

                softly.assertThat(queryNames(statement, """
                        SELECT table_name || '.' || column_name
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name IN ('rag_repository', 'rag_index_job', 'rag_document',
                                             'rag_chunk', 'rag_retrieval_trace', 'review_issue_evidence')
                          AND is_nullable = 'YES'
                        """))
                        .as("nullable columns")
                        .containsExactlyInAnyOrder(
                                "rag_repository.default_branch",
                                "rag_repository.active_commit_sha",
                                "rag_repository.last_indexed_at",
                                "rag_index_job.resolved_commit_sha",
                                "rag_index_job.error_code",
                                "rag_index_job.error_message",
                                "rag_index_job.started_at",
                                "rag_index_job.finished_at",
                                "rag_chunk.symbol_name",
                                "rag_chunk.search_vector",
                                "review_issue_evidence.rag_chunk_id"
                        );

                assertColumnsOfType(softly, statement, "bigint",
                        "rag_repository.id",
                        "rag_index_job.id", "rag_index_job.repository_id",
                        "rag_document.id", "rag_document.repository_id", "rag_document.byte_size",
                        "rag_chunk.id", "rag_chunk.repository_id", "rag_chunk.document_id",
                        "rag_retrieval_trace.id", "rag_retrieval_trace.review_run_id",
                        "rag_retrieval_trace.repository_id", "rag_retrieval_trace.latency_ms",
                        "review_issue_evidence.id", "review_issue_evidence.review_issue_id",
                        "review_issue_evidence.rag_chunk_id");
                assertColumnsOfType(softly, statement, "integer",
                        "rag_repository.index_version", "rag_repository.embedding_dimensions",
                        "rag_index_job.attempt_count", "rag_index_job.discovered_file_count",
                        "rag_index_job.indexed_file_count", "rag_index_job.indexed_chunk_count",
                        "rag_index_job.skipped_file_count",
                        "rag_chunk.start_line", "rag_chunk.end_line", "rag_chunk.token_count",
                        "rag_retrieval_trace.vector_candidate_count",
                        "rag_retrieval_trace.lexical_candidate_count", "rag_retrieval_trace.reranked_count",
                        "rag_retrieval_trace.selected_count", "rag_retrieval_trace.context_char_count",
                        "review_issue_evidence.start_line", "review_issue_evidence.end_line",
                        "review_issue_evidence.retrieval_rank");
                assertColumnsOfType(softly, statement, "character varying(32)",
                        "rag_repository.provider", "rag_repository.index_status",
                        "rag_index_job.trigger_type", "rag_index_job.status",
                        "review_issue_evidence.citation_label");
                assertColumnsOfType(softly, statement, "character varying(64)",
                        "rag_repository.active_commit_sha", "rag_index_job.resolved_commit_sha",
                        "rag_index_job.error_code", "rag_document.commit_sha", "rag_document.language",
                        "rag_document.content_hash", "rag_chunk.commit_sha", "rag_chunk.language",
                        "rag_chunk.content_hash", "rag_retrieval_trace.commit_sha",
                        "rag_retrieval_trace.query_hash", "review_issue_evidence.content_hash");
                assertColumnsOfType(softly, statement, "character varying(96)", "rag_chunk.chunk_key");
                assertColumnsOfType(softly, statement, "character varying(255)",
                        "rag_repository.owner_name", "rag_repository.repository_name",
                        "rag_repository.default_branch", "rag_repository.embedding_model",
                        "rag_index_job.requested_ref");
                assertColumnsOfType(softly, statement, "character varying(500)", "rag_chunk.symbol_name");
                assertColumnsOfType(softly, statement, "character varying(1000)",
                        "rag_repository.clone_url", "rag_index_job.error_message", "rag_document.path",
                        "rag_chunk.path", "review_issue_evidence.path");
                assertColumnsOfType(softly, statement, "character varying(2000)",
                        "review_issue_evidence.evidence_excerpt");
                assertColumnsOfType(softly, statement, "text",
                        "rag_chunk.content", "rag_retrieval_trace.result_summary_json");
                softly.assertThat(queryNames(statement, """
                        SELECT table_name || '.' || column_name
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND (table_name, column_name) IN (
                              ('review_task', 'diff_text'),
                              ('review_input_snapshot', 'snapshot_json')
                          )
                          AND data_type = 'text'
                          AND domain_name IS NULL
                        """))
                        .as("legacy CLOB columns converted to base TEXT")
                        .containsExactlyInAnyOrder(
                                "review_task.diff_text",
                                "review_input_snapshot.snapshot_json"
                        );
                assertColumnsOfType(softly, statement, "timestamp without time zone",
                        "rag_repository.last_indexed_at", "rag_repository.created_at", "rag_repository.updated_at",
                        "rag_index_job.started_at", "rag_index_job.finished_at", "rag_index_job.created_at",
                        "rag_document.created_at", "rag_chunk.created_at", "rag_retrieval_trace.created_at",
                        "review_issue_evidence.created_at");
                assertColumnsOfType(softly, statement, "boolean", "rag_retrieval_trace.degraded");
                assertColumnsOfType(softly, statement, "double precision",
                        "review_issue_evidence.retrieval_score");
                assertColumnsOfType(softly, statement, "vector(1024)", "rag_chunk.embedding");
                assertColumnsOfType(softly, statement, "tsvector", "rag_chunk.search_vector");

                softly.assertThat(querySingle(statement, """
                        SELECT format_type(a.atttypid, a.atttypmod)
                        FROM pg_attribute a
                        WHERE a.attrelid = 'rag_chunk'::regclass
                          AND a.attname = 'embedding'
                        """))
                        .as("embedding type")
                        .isEqualTo("vector(1024)");
                softly.assertThat(querySingle(statement, """
                        SELECT format_type(a.atttypid, a.atttypmod) || ':' || a.attnotnull
                        FROM pg_attribute a
                        WHERE a.attrelid = 'review_issue_evidence'::regclass
                          AND a.attname = 'retrieval_score'
                        """))
                        .as("retrieval score type and nullability")
                        .isEqualTo("double precision:true");
                softly.assertThat(querySingle(statement, """
                        SELECT a.attgenerated::text || ':' || pg_get_expr(d.adbin, d.adrelid)
                        FROM pg_attribute a
                        JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
                        WHERE a.attrelid = 'rag_chunk'::regclass
                          AND a.attname = 'search_vector'
                        """))
                        .as("generated search vector")
                        .startsWith("s:")
                        .contains(
                                "to_tsvector('simple'::regconfig",
                                "path",
                                "symbol_name",
                                "content"
                        );

                softly.assertThat(queryNames(statement, """
                        SELECT constraint_definition.conname || ':' ||
                               constraint_definition.source_table || ':' ||
                               constraint_definition.source_columns || ':' ||
                               constraint_definition.referenced_table || ':' ||
                               constraint_definition.referenced_columns || ':' ||
                               constraint_definition.delete_action
                        FROM (
                            SELECT c.conname,
                                   c.conrelid::regclass::text AS source_table,
                                   (
                                       SELECT string_agg(a.attname, ',' ORDER BY key_column.position)
                                       FROM unnest(c.conkey) WITH ORDINALITY key_column(attnum, position)
                                       JOIN pg_attribute a
                                         ON a.attrelid = c.conrelid AND a.attnum = key_column.attnum
                                   ) AS source_columns,
                                   c.confrelid::regclass::text AS referenced_table,
                                   (
                                       SELECT string_agg(a.attname, ',' ORDER BY key_column.position)
                                       FROM unnest(c.confkey) WITH ORDINALITY key_column(attnum, position)
                                       JOIN pg_attribute a
                                         ON a.attrelid = c.confrelid AND a.attnum = key_column.attnum
                                   ) AS referenced_columns,
                                   c.confdeltype::text AS delete_action
                            FROM pg_constraint c
                            WHERE c.contype = 'f'
                              AND c.conrelid IN ('rag_index_job'::regclass, 'rag_document'::regclass,
                                                'rag_chunk'::regclass, 'rag_retrieval_trace'::regclass,
                                                'review_issue_evidence'::regclass)
                        ) constraint_definition
                        """))
                        .as("foreign key relationships and delete actions")
                        .containsExactlyInAnyOrder(
                                "fk_rag_index_job_repository:rag_index_job:repository_id:rag_repository:id:a",
                                "fk_rag_document_repository:rag_document:repository_id:rag_repository:id:a",
                                "fk_rag_chunk_repository:rag_chunk:repository_id:rag_repository:id:a",
                                "fk_rag_chunk_document:rag_chunk:document_id:rag_document:id:c",
                                "fk_rag_retrieval_trace_review_run:rag_retrieval_trace:review_run_id:review_run:id:a",
                                "fk_rag_retrieval_trace_repository:rag_retrieval_trace:repository_id:rag_repository:id:a",
                                "fk_review_issue_evidence_issue:review_issue_evidence:review_issue_id:review_issue:id:c",
                                "fk_review_issue_evidence_chunk:review_issue_evidence:rag_chunk_id:rag_chunk:id:n"
                        );

                softly.assertThat(queryNames(statement, """
                        SELECT conrelid::regclass::text || ':' || pg_get_constraintdef(oid)
                        FROM pg_constraint
                        WHERE contype = 'u'
                          AND conrelid IN ('rag_repository'::regclass, 'rag_document'::regclass,
                                           'rag_chunk'::regclass, 'review_issue_evidence'::regclass)
                        """))
                        .as("unique constraints")
                        .containsExactlyInAnyOrder(
                                "rag_repository:UNIQUE (provider, owner_name, repository_name)",
                                "rag_document:UNIQUE (repository_id, commit_sha, path)",
                                "rag_chunk:UNIQUE (repository_id, commit_sha, chunk_key)",
                                "review_issue_evidence:UNIQUE (review_issue_id, citation_label)"
                        );

                softly.assertThat(queryNames(statement, """
                        SELECT conrelid::regclass::text || ':' || conname
                        FROM pg_constraint
                        WHERE contype = 'c'
                          AND conrelid IN ('rag_repository'::regclass, 'rag_index_job'::regclass,
                                           'rag_document'::regclass, 'rag_chunk'::regclass,
                                           'rag_retrieval_trace'::regclass,
                                           'review_issue_evidence'::regclass)
                        """))
                        .as("check constraints")
                        .containsExactly("rag_repository:ck_rag_repository_embedding_dimensions");
                softly.assertThat(querySingle(statement, """
                        SELECT pg_get_constraintdef(oid)
                        FROM pg_constraint
                        WHERE conname = 'ck_rag_repository_embedding_dimensions'
                        """))
                        .as("embedding dimensions check")
                        .contains("embedding_dimensions = 1024");

                softly.assertThat(queryNames(statement, """
                        SELECT ci.relname || ':' || am.amname
                        FROM pg_index i
                        JOIN pg_class ci ON ci.oid = i.indexrelid
                        JOIN pg_class ct ON ct.oid = i.indrelid
                        JOIN pg_am am ON am.oid = ci.relam
                        WHERE ct.relnamespace = 'public'::regnamespace
                          AND ct.relname IN ('rag_repository', 'rag_index_job', 'rag_document',
                                             'rag_chunk', 'rag_retrieval_trace', 'review_issue_evidence')
                          AND NOT EXISTS (
                              SELECT 1 FROM pg_constraint c WHERE c.conindid = i.indexrelid
                          )
                        """))
                        .as("planned indexes and access methods")
                        .containsExactlyInAnyOrder(
                                "idx_rag_chunk_embedding_hnsw:hnsw",
                                "idx_rag_chunk_search_vector_gin:gin",
                                "idx_rag_chunk_snapshot:btree"
                        );

                softly.assertThat(queryNames(statement, """
                        SELECT ci.relname || ':' || opc.opcname
                        FROM pg_index i
                        JOIN pg_class ci ON ci.oid = i.indexrelid
                        JOIN unnest(i.indclass) WITH ORDINALITY classes(opclass_oid, position) ON TRUE
                        JOIN pg_opclass opc ON opc.oid = classes.opclass_oid
                        WHERE ci.relname IN ('idx_rag_chunk_embedding_hnsw',
                                             'idx_rag_chunk_search_vector_gin')
                        """))
                        .as("search index operator classes")
                        .containsExactlyInAnyOrder(
                                "idx_rag_chunk_embedding_hnsw:vector_cosine_ops",
                                "idx_rag_chunk_search_vector_gin:tsvector_ops"
                        );

                softly.assertThat(queryNames(statement, """
                        SELECT pg_get_indexdef('idx_rag_chunk_snapshot'::regclass, position, true)
                        FROM generate_series(1, 4) position
                        WHERE pg_get_indexdef('idx_rag_chunk_snapshot'::regclass, position, true) <> ''
                        """))
                        .as("snapshot index columns")
                        .containsExactlyInAnyOrder("repository_id", "commit_sha", "path");

                softly.assertAll();
            }
        }
    }

    @Test
    void backfillsOnlySnapshotWhoseLegacyTupleCanBeProven() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")) {
            postgres.start();
            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration", "classpath:db/rag/postgresql")
                    .target("4")
                    .initSql("CREATE SCHEMA IF NOT EXISTS flyway_compat; "
                            + "DO $$ BEGIN CREATE DOMAIN flyway_compat.CLOB AS TEXT; "
                            + "EXCEPTION WHEN duplicate_object THEN NULL; END $$; "
                            + "SET search_path TO public, flyway_compat")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO rag_repository
                          (provider, owner_name, repository_name, clone_url, default_branch, active_commit_sha,
                           index_status, index_version, embedding_model, embedding_dimensions,
                           last_indexed_at, created_at, updated_at)
                        VALUES ('github', 'owner', 'repo', 'https://github.com/owner/repo.git', 'main',
                                '2222222222222222222222222222222222222222', 'READY', 1,
                                'current-model', 1024, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """);
                statement.executeUpdate("""
                        INSERT INTO rag_index_job
                          (repository_id, requested_ref, resolved_commit_sha, trigger_type, status,
                           attempt_count, finished_at, created_at)
                        SELECT id, '1111111111111111111111111111111111111111',
                               '1111111111111111111111111111111111111111', 'PULL_REQUEST', 'READY', 1,
                               CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        FROM rag_repository
                        """);
                statement.executeUpdate("""
                        INSERT INTO rag_index_job
                          (repository_id, requested_ref, resolved_commit_sha, trigger_type, status,
                           attempt_count, finished_at, created_at)
                        SELECT id, '2222222222222222222222222222222222222222',
                               '2222222222222222222222222222222222222222', 'PULL_REQUEST', 'READY', 1,
                               CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        FROM rag_repository
                        """);
            }

            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration", "classpath:db/rag/postgresql")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement()) {
                assertThat(queryNames(statement, "SELECT commit_sha FROM rag_index_snapshot"))
                        .containsExactly("2222222222222222222222222222222222222222");
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

    private String querySingle(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            String value = resultSet.getString(1);
            assertThat(resultSet.next()).isFalse();
            return value;
        }
    }

    private void assertColumnsOfType(
            SoftAssertions softly,
            Statement statement,
            String dataType,
            String... expectedColumns
    ) throws Exception {
        softly.assertThat(queryNames(statement, """
                SELECT c.relname || '.' || a.attname
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                WHERE c.relnamespace = 'public'::regnamespace
                  AND c.relname IN ('rag_repository', 'rag_index_job', 'rag_document',
                                    'rag_chunk', 'rag_retrieval_trace', 'review_issue_evidence')
                  AND a.attnum > 0
                  AND NOT a.attisdropped
                  AND format_type(a.atttypid, a.atttypmod) = '%s'
                """.formatted(dataType)))
                .as("columns of type %s", dataType)
                .containsExactlyInAnyOrder(expectedColumns);
    }
}
