package com.codereviewx.backend.rag.persistence;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.retrieval.*;
import com.codereviewx.backend.review.enums.*;
import com.codereviewx.backend.review.persistence.entity.ReviewIssueEntity;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import com.pgvector.PGvector;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReviewIssueEvidenceStorePostgresTest {
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");
    private JdbcTemplate jdbc;
    private ReviewIssueEvidenceStore store;

    @BeforeAll void start() {
        postgres.start();
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration", "classpath:db/rag/postgresql")
                .initSql("CREATE SCHEMA IF NOT EXISTS flyway_compat; DO $$ BEGIN CREATE DOMAIN flyway_compat.CLOB AS TEXT; EXCEPTION WHEN duplicate_object THEN NULL; END $$; SET search_path TO public, flyway_compat")
                .load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        RagProperties properties = new RagProperties(); properties.setEnabled(true);
        store = new ReviewIssueEvidenceStore(jdbc, properties);
    }

    @BeforeEach void clean() { jdbc.execute("TRUNCATE review_issue_evidence, review_issue, review_run, review_task, rag_chunk, rag_document, rag_index_snapshot, rag_index_job, rag_repository RESTART IDENTITY CASCADE"); }

    @Test void persistsAfterIssueWithSourceFkHashAndBoundedExcerptAndRollsBackAtomically() {
        long chunkId = sourceChunk();
        long issueId = issue();
        save(issueId, chunkId);
        assertThat(jdbc.queryForMap("SELECT * FROM review_issue_evidence")).satisfies(row -> {
            assertThat(row.get("rag_chunk_id")).isEqualTo(chunkId);
            assertThat(row.get("content_hash")).isEqualTo("original-content-hash");
            assertThat(row.get("evidence_excerpt").toString()).hasSize(2000);
        });

        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            long secondIssue = issue();
            save(secondIssue, chunkId);
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review_issue_evidence", Integer.class)).isEqualTo(1);
    }

    private void save(long issueId, long chunkId) {
        ReviewIssueEntity issue = new ReviewIssueEntity(); issue.setId(issueId);
        ReviewFinding finding = new ReviewFinding("M1", IssueSeverity.HIGH, IssueCategory.BUG, IssueSource.MIMO,
                IssueStatus.OPEN, "src/A.java", 1, 1, "t", "d", "r", List.of("C1"));
        RagEvidence evidence = new RagEvidence("C1", "src/A.java", 1, 2, "sha", "x".repeat(2500), 0.9,
                false, false, new RagEvidenceSourceIdentity(chunkId, "original-content-hash"));
        store.save(issue, finding, new RagEvidenceBundle(List.of(evidence), "prompt",
                RagEvidenceBundle.DegradedReason.NONE, RagRetrievalHealth.HEALTHY));
    }

    private long issue() {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        long task = jdbc.queryForObject("INSERT INTO review_task(repo_url,pr_number,status,created_at,updated_at,review_mode) VALUES ('x',1,'RUNNING',?,?,'GITHUB_PR') RETURNING id", Long.class, now, now);
        long run = jdbc.queryForObject("INSERT INTO review_run(review_task_id,run_number,review_mode,status,created_at,updated_at) VALUES (?,1,'GITHUB_PR','REVIEWING',?,?) RETURNING id", Long.class, task, now, now);
        return jdbc.queryForObject("INSERT INTO review_issue(review_task_id,review_run_id,issue_key,severity,category,source,status,file_path,start_line,end_line,title,description,recommendation,created_at,updated_at) VALUES (?,?,'M','HIGH','BUG','MIMO','OPEN','src/A.java',1,1,'t','d','r',?,?) RETURNING id", Long.class, task, run, now, now);
    }

    private long sourceChunk() {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        long repo = jdbc.queryForObject("INSERT INTO rag_repository(provider,owner_name,repository_name,clone_url,index_status,index_version,embedding_model,embedding_dimensions,created_at,updated_at) VALUES ('github','o','r','https://x','READY',1,'m',1024,?,?) RETURNING id", Long.class, now, now);
        long document = jdbc.queryForObject("INSERT INTO rag_document(repository_id,commit_sha,path,language,content_hash,byte_size,created_at) VALUES (?,'sha','src/A.java','JAVA','doc-hash',10,?) RETURNING id", Long.class, repo, now);
        return jdbc.queryForObject("INSERT INTO rag_chunk(repository_id,document_id,commit_sha,chunk_key,path,language,start_line,end_line,content,token_count,content_hash,embedding,created_at) VALUES (?,?,'sha','key','src/A.java','JAVA',1,2,'source',1,'original-content-hash',?,?) RETURNING id", Long.class, repo, document, new PGvector(new float[1024]), now);
    }
}
