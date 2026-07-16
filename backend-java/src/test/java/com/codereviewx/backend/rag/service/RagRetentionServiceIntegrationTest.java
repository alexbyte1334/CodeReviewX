package com.codereviewx.backend.rag.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RagRetentionServiceIntegrationTest {
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");
    private JdbcTemplate jdbc;
    @BeforeAll void start() {
        postgres.start();
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration", "classpath:db/rag/postgresql")
                .initSql("CREATE SCHEMA IF NOT EXISTS flyway_compat; DO $$ BEGIN CREATE DOMAIN flyway_compat.CLOB AS TEXT; EXCEPTION WHEN duplicate_object THEN NULL; END $$; SET search_path TO public, flyway_compat")
                .load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }
    @AfterAll void stop() { postgres.stop(); }
    @BeforeEach void clean() { jdbc.execute("TRUNCATE review_issue_evidence, rag_chunk, rag_document, rag_index_snapshot, rag_index_job, rag_repository RESTART IDENTITY CASCADE"); }

    @Test void cleanupRetainsLatestFiveRecentActiveAndInFlightAndIsIdempotent() {
        long repo = repo("active");
        for (int i=1;i<=8;i++) snapshot(repo, "c"+i, "READY", i<=5 ? 40 : 10, null);
        snapshot(repo,"active","READY",60,null);
        snapshot(repo,"running","RUNNING",60,null);
        snapshot(repo,"queued","QUEUED",60,null);
        long doomed = jdbc.queryForObject("SELECT id FROM rag_index_snapshot WHERE commit_sha='c6'",Long.class);
        long doc = jdbc.queryForObject("INSERT INTO rag_document(repository_id,commit_sha,path,language,content_hash,byte_size,created_at,snapshot_id) VALUES (?, 'c6','x','JAVA','h',1,CURRENT_TIMESTAMP,?) RETURNING id",Long.class,repo,doomed);
        jdbc.update("INSERT INTO rag_chunk(repository_id,document_id,commit_sha,chunk_key,path,language,start_line,end_line,content,token_count,content_hash,embedding,created_at,snapshot_id) VALUES (?,?,'c6','k','x','JAVA',1,1,'x',1,'h',? ,CURRENT_TIMESTAMP,?)",repo,doc,new com.pgvector.PGvector(new float[1024]),doomed);
        int removed = new RagRetentionService(jdbc).cleanup();
        assertThat(removed).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_index_snapshot WHERE commit_sha IN ('c6','c7','c8')",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_document WHERE snapshot_id=?",Integer.class,doomed)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_chunk WHERE snapshot_id=?",Integer.class,doomed)).isZero();
        assertThat(new RagRetentionService(jdbc).cleanup()).isZero();
    }
    private long repo(String active) { Timestamp now=Timestamp.valueOf(LocalDateTime.now()); return jdbc.queryForObject("INSERT INTO rag_repository(provider,owner_name,repository_name,clone_url,active_commit_sha,index_status,index_version,embedding_model,embedding_dimensions,created_at,updated_at) VALUES ('github','o','r','x',?,'READY',1,'m',1024,?,?) RETURNING id",Long.class,active,now,now); }
    private void snapshot(long repo,String sha,String status,int ageDays,String ignored) { Timestamp now=Timestamp.valueOf(LocalDateTime.now().minusDays(ageDays)); long job=jdbc.queryForObject("INSERT INTO rag_index_job(repository_id,requested_ref,trigger_type,status,created_at,finished_at) VALUES (?, 'main','MANUAL',?,?,?) RETURNING id",Long.class,repo,status,now, status.equals("READY")?now:null); jdbc.update("INSERT INTO rag_index_snapshot(repository_id,job_id,commit_sha,embedding_model,embedding_dimensions,index_version,created_at) VALUES (?,? ,?,'m',1024,1,?)",repo,job,sha,now); }
}
