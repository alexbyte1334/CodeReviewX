package com.codereviewx.backend.rag.retrieval;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.embedding.EmbeddingClient;
import com.pgvector.PGvector;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HybridRagRetrievalServiceIntegrationTest {

    private static final String TARGET_SHA = "a".repeat(40);
    private static final String OTHER_SHA = "b".repeat(40);
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");
    private JdbcTemplate jdbc;
    private RagProperties properties;
    private HybridRagRetrievalService service;
    private long repositoryId;

    @BeforeAll
    void startPostgres() {
        postgres.start();
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration", "classpath:db/rag/postgresql")
                .initSql("CREATE SCHEMA IF NOT EXISTS flyway_compat; "
                        + "DO $$ BEGIN CREATE DOMAIN flyway_compat.CLOB AS TEXT; "
                        + "EXCEPTION WHEN duplicate_object THEN NULL; END $$; "
                        + "SET search_path TO public, flyway_compat")
                .load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE rag_chunk, rag_document, rag_index_snapshot, rag_index_job, rag_repository "
                + "RESTART IDENTITY CASCADE");
        properties = new RagProperties();
        properties.setEnabled(false);
        properties.setEmbeddingModel("model-a");
        properties.setEmbeddingDimensions(1024);
        repositoryId = insertRepository();
        EmbeddingClient embeddings = inputs -> inputs.stream().map(ignored -> vector(0)).toList();
        service = new HybridRagRetrievalService(jdbc, embeddings, properties);
    }

    @Test
    void vectorAndLexicalTopFortyStayInsideExactSnapshotAndExcludeLegacyRows() {
        long targetSnapshot = insertReadySnapshot(TARGET_SHA, "model-a", 1024, 1);
        long otherCommitSnapshot = insertReadySnapshot(OTHER_SHA, "model-a", 1024, 1);
        long otherModelSnapshot = insertReadySnapshot(TARGET_SHA, "model-b", 1024, 1);
        for (int index = 0; index < 45; index++) {
            insertChunk(targetSnapshot, TARGET_SHA, "src/target/Needle" + index + ".java",
                    "needle exact target " + index, vector(index == 44 ? 0 : 1));
        }
        insertChunk(otherCommitSnapshot, OTHER_SHA, "src/wrong/OtherCommit.java",
                "needle exact target other commit", vector(0));
        insertChunk(otherModelSnapshot, TARGET_SHA, "src/wrong/OtherModel.java",
                "needle exact target other model", vector(0));
        insertLegacyChunk(TARGET_SHA, "src/wrong/Legacy.java", "needle exact target legacy", vector(0));

        HybridRagRetrievalService.Result result = service.retrieve(new HybridRagRetrievalService.Request(
                repositoryId, TARGET_SHA, "needle exact target", List.of("src/target/Needle44.java")));

        assertThat(result.status()).isEqualTo(HybridRagRetrievalService.Status.READY);
        assertThat(result.vectorCandidateCount()).isEqualTo(40);
        assertThat(result.lexicalCandidateCount()).isEqualTo(40);
        assertThat(result.matches()).extracting(HybridRagRetrievalService.Match::path)
                .allMatch(path -> path.startsWith("src/target/"))
                .doesNotContain("src/wrong/OtherCommit.java", "src/wrong/OtherModel.java", "src/wrong/Legacy.java");
        assertThat(result.matches()).extracting(HybridRagRetrievalService.Match::path)
                .contains("src/target/Needle44.java");
        assertThat(result.matches()).filteredOn(match -> match.path().equals("src/target/Needle44.java"))
                .extracting(HybridRagRetrievalService.Match::pathBoost).containsExactly(1.25);
        assertThat(result.matches()).filteredOn(match -> match.path().equals("src/target/Needle43.java"))
                .extracting(HybridRagRetrievalService.Match::pathBoost).containsExactly(1.10);
    }

    @Test
    void missingExactSnapshotReturnsTypedIndexNotReadyWithoutEmbeddingOrFallback() {
        insertReadySnapshot(OTHER_SHA, "model-a", 1024, 1);
        int[] embeddingCalls = {0};
        service = new HybridRagRetrievalService(jdbc, inputs -> {
            embeddingCalls[0]++;
            return List.of(vector(0));
        }, properties);

        HybridRagRetrievalService.Result result = service.retrieve(new HybridRagRetrievalService.Request(
                repositoryId, TARGET_SHA, "needle", List.of()));

        assertThat(result.status()).isEqualTo(HybridRagRetrievalService.Status.INDEX_NOT_READY);
        assertThat(result.matches()).isEmpty();
        assertThat(embeddingCalls[0]).isZero();
    }

    @Test
    void exactSnapshotMustAlsoMatchConfiguredModelDimensionsAndVersion() {
        insertReadySnapshot(TARGET_SHA, "model-b", 1024, 1);
        insertReadySnapshot(TARGET_SHA, "model-a", 1024, 2);

        HybridRagRetrievalService.Result result = service.retrieve(new HybridRagRetrievalService.Request(
                repositoryId, TARGET_SHA, "needle", List.of()));

        assertThat(result.status()).isEqualTo(HybridRagRetrievalService.Status.INDEX_NOT_READY);
    }

    @Test
    void emptyQueryStillFailsClosedWhenExactSnapshotIsMissing() {
        insertReadySnapshot(OTHER_SHA, "model-a", 1024, 1);

        HybridRagRetrievalService.Result result = service.retrieve(new HybridRagRetrievalService.Request(
                repositoryId, TARGET_SHA, " ", List.of()));

        assertThat(result.status()).isEqualTo(HybridRagRetrievalService.Status.INDEX_NOT_READY);
    }

    private long insertRepository() {
        return jdbc.queryForObject("""
                INSERT INTO rag_repository
                  (provider, owner_name, repository_name, clone_url, default_branch, active_commit_sha,
                   index_status, index_version, embedding_model, embedding_dimensions, created_at, updated_at)
                VALUES ('github','owner','repo','https://example/repo.git','main',?,'READY',1,'model-a',1024,?,?)
                RETURNING id
                """, Long.class, OTHER_SHA, now(), now());
    }

    private long insertReadySnapshot(String sha, String model, int dimensions, int version) {
        long jobId = jdbc.queryForObject("""
                INSERT INTO rag_index_job
                  (repository_id, requested_ref, resolved_commit_sha, trigger_type, status, attempt_count,
                   created_at, finished_at, embedding_model, embedding_dimensions, index_version)
                VALUES (?,?,?,'PR_REVIEW','READY',1,?,?,?, ?, ?) RETURNING id
                """, Long.class, repositoryId, sha, sha, now(), now(), model, dimensions, version);
        return jdbc.queryForObject("""
                INSERT INTO rag_index_snapshot
                  (repository_id, job_id, commit_sha, embedding_model, embedding_dimensions, index_version, created_at)
                VALUES (?,?,?,?,?,?,?) RETURNING id
                """, Long.class, repositoryId, jobId, sha, model, dimensions, version, now());
    }

    private void insertChunk(long snapshotId, String sha, String path, String content, float[] embedding) {
        long documentId = insertDocument(snapshotId, sha, path);
        jdbc.update("""
                INSERT INTO rag_chunk
                  (repository_id, snapshot_id, document_id, commit_sha, chunk_key, path, language, symbol_name,
                   start_line, end_line, content, token_count, content_hash, embedding, created_at)
                VALUES (?,?,?,?,?,?,?,?,1,3,?,3,?,?,?)
                """, repositoryId, snapshotId, documentId, sha, path + "#1", path, "JAVA", "needle",
                content, "hash-" + snapshotId + "-" + path, new PGvector(embedding), now());
    }

    private void insertLegacyChunk(String sha, String path, String content, float[] embedding) {
        long documentId = insertDocument(null, sha, path);
        jdbc.update("""
                INSERT INTO rag_chunk
                  (repository_id, snapshot_id, document_id, commit_sha, chunk_key, path, language, symbol_name,
                   start_line, end_line, content, token_count, content_hash, embedding, created_at)
                VALUES (?,NULL,?,?,?,?,?,?,1,3,?,3,?,?,?)
                """, repositoryId, documentId, sha, path + "#1", path, "JAVA", "needle", content,
                "legacy-hash", new PGvector(embedding), now());
    }

    private long insertDocument(Long snapshotId, String sha, String path) {
        return jdbc.queryForObject("""
                INSERT INTO rag_document
                  (repository_id, snapshot_id, commit_sha, path, language, content_hash, byte_size, created_at)
                VALUES (?,?,?,?, 'JAVA', ?, 10, ?) RETURNING id
                """, Long.class, repositoryId, snapshotId, sha, path, "doc-" + path, now());
    }

    private static float[] vector(int coordinate) {
        float[] vector = new float[1024];
        vector[Math.min(coordinate, vector.length - 1)] = 1.0f;
        return vector;
    }

    private static Timestamp now() {
        return Timestamp.valueOf(LocalDateTime.now());
    }
}
