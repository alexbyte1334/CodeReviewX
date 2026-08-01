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
@Tag("postgres")
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
        for (int i=1;i<=8;i++) snapshot(repo, "c"+i, "READY", 50-i, null);
        snapshot(repo,"recent","READY",10,null);
        snapshot(repo,"active","READY",60,null);
        snapshot(repo,"running","RUNNING",60,null);
        snapshot(repo,"queued","QUEUED",60,null);
        long doomed = jdbc.queryForObject("SELECT id FROM rag_index_snapshot WHERE commit_sha='c1'",Long.class);
        long doc = jdbc.queryForObject("INSERT INTO rag_document(repository_id,commit_sha,path,language,content_hash,byte_size,created_at,snapshot_id) VALUES (?, 'c1','src/X.java','JAVA','document-hash',1,CURRENT_TIMESTAMP,?) RETURNING id",Long.class,repo,doomed);
        long chunk = jdbc.queryForObject("INSERT INTO rag_chunk(repository_id,document_id,commit_sha,chunk_key,path,language,start_line,end_line,content,token_count,content_hash,embedding,created_at,snapshot_id) VALUES (?,?,'c1','k','src/X.java','JAVA',7,9,'secret-free excerpt',3,'chunk-hash',? ,CURRENT_TIMESTAMP,?) RETURNING id",Long.class,repo,doc,new com.pgvector.PGvector(new float[1024]),doomed);
        long evidence = evidence(chunk);
        int removed = new RagRetentionService(jdbc).cleanup();
        assertThat(removed).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_index_snapshot WHERE commit_sha IN ('c1','c2','c3','c4')",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_index_snapshot WHERE commit_sha IN ('c5','c6','c7','c8','recent','active','running','queued')",Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_document WHERE snapshot_id=?",Integer.class,doomed)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_chunk WHERE snapshot_id=?",Integer.class,doomed)).isZero();
        var preservedEvidence = jdbc.queryForMap("SELECT rag_chunk_id,citation_label,path,start_line,end_line,content_hash,evidence_excerpt,retrieval_rank,retrieval_score,created_at FROM review_issue_evidence WHERE id=?",evidence);
        assertThat(preservedEvidence)
                .containsEntry("rag_chunk_id", null)
                .containsEntry("citation_label", "E1")
                .containsEntry("path", "src/X.java")
                .containsEntry("start_line", 7)
                .containsEntry("end_line", 9)
                .containsEntry("content_hash", "chunk-hash")
                .containsEntry("evidence_excerpt", "immutable evidence snapshot")
                .containsEntry("retrieval_rank", 1)
                .containsEntry("retrieval_score", 0.75d);
        assertThat(preservedEvidence.get("created_at")).isNotNull();
        assertThat(new RagRetentionService(jdbc).cleanup()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review_issue_evidence WHERE id=? AND rag_chunk_id IS NULL",Integer.class,evidence)).isOne();
    }
    private long repo(String active) { Timestamp now=Timestamp.valueOf(LocalDateTime.now()); return jdbc.queryForObject("INSERT INTO rag_repository(provider,owner_name,repository_name,clone_url,active_commit_sha,index_status,index_version,embedding_model,embedding_dimensions,created_at,updated_at) VALUES ('github','o','r','x',?,'READY',1,'m',1024,?,?) RETURNING id",Long.class,active,now,now); }
    private void snapshot(long repo,String sha,String status,int ageDays,String ignored) { Timestamp now=Timestamp.valueOf(LocalDateTime.now().minusDays(ageDays)); long job=jdbc.queryForObject("INSERT INTO rag_index_job(repository_id,requested_ref,trigger_type,status,embedding_model,embedding_dimensions,index_version,created_at,finished_at) VALUES (?,?,'MANUAL',?,'m',1024,1,?,?) RETURNING id",Long.class,repo,sha,status,now, status.equals("READY")?now:null); jdbc.update("INSERT INTO rag_index_snapshot(repository_id,job_id,commit_sha,embedding_model,embedding_dimensions,index_version,created_at) VALUES (?,? ,?,'m',1024,1,?)",repo,job,sha,now); }

    private long evidence(long chunk) {
        Timestamp now=Timestamp.valueOf(LocalDateTime.now());
        long task=jdbc.queryForObject("INSERT INTO review_api_run(repo_url,pr_number,status,created_at,updated_at,review_mode) VALUES ('x',1,'RUNNING',?,?,'GITHUB_PR') RETURNING id",Long.class,now,now);
        long run=jdbc.queryForObject("INSERT INTO review_api_run(review_api_run_id,run_number,review_mode,status,created_at,updated_at) VALUES (?,1,'GITHUB_PR','REVIEWING',?,?) RETURNING id",Long.class,task,now,now);
        long issue=jdbc.queryForObject("INSERT INTO review_issue(review_api_run_id,review_api_run_id,issue_key,severity,category,source,status,file_path,start_line,end_line,title,description,recommendation,created_at,updated_at) VALUES (?,?,'R1','HIGH','BUG','MIMO','OPEN','src/X.java',7,9,'title','description','recommendation',?,?) RETURNING id",Long.class,task,run,now,now);
        return jdbc.queryForObject("INSERT INTO review_issue_evidence(review_issue_id,rag_chunk_id,citation_label,path,start_line,end_line,content_hash,evidence_excerpt,retrieval_rank,retrieval_score,created_at) VALUES (?,?,'E1','src/X.java',7,9,'chunk-hash','immutable evidence snapshot',1,0.75,?) RETURNING id",Long.class,issue,chunk,now);
    }
}
