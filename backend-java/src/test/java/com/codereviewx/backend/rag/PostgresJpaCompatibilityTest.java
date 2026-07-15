package com.codereviewx.backend.rag;

import com.codereviewx.backend.review.enums.ReviewMode;
import com.codereviewx.backend.review.enums.ReviewRunStatus;
import com.codereviewx.backend.review.enums.ReviewTaskStatus;
import com.codereviewx.backend.review.persistence.entity.ReviewInputSnapshotEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewRunEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewTaskEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewInputSnapshotRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewRunRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewTaskRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("postgres")
@SpringBootTest
class PostgresJpaCompatibilityTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private ReviewTaskRepository reviewTaskRepository;

    @Autowired
    private ReviewRunRepository reviewRunRepository;

    @Autowired
    private ReviewInputSnapshotRepository reviewInputSnapshotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsLongTextAsTextAcrossRepositoryTransactionBoundaries() {
        assertThat(flyway.info().applied())
                .extracting(info -> info.getVersion().getVersion())
                .containsExactly("1", "2", "3", "4", "5", "6", "7");
        assertThat(jdbcTemplate.queryForList("""
                        SELECT table_name || '.' || column_name || '=' || data_type || ':'
                               || COALESCE(domain_name, '')
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND (table_name, column_name) IN (
                              ('review_task', 'diff_text'),
                              ('review_input_snapshot', 'snapshot_json')
                          )
                        """, String.class))
                .containsExactlyInAnyOrder(
                        "review_task.diff_text=text:",
                        "review_input_snapshot.snapshot_json=text:"
                );

        String marker = "postgres-jpa-text-" + UUID.randomUUID();
        String diffText = ("diff --git a/example.txt b/example.txt\n+" + marker + "\n").repeat(120);
        String snapshotJson = ("{\"marker\":\"" + marker + "\",\"payload\":\"text-value\"}\n").repeat(120);
        LocalDateTime now = LocalDateTime.now();

        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setRepoUrl("https://github.com/example/postgres-jpa-compatibility");
        task.setPrNumber(42);
        task.setDiffText(diffText);
        task.setStatus(ReviewTaskStatus.PENDING);
        task.setReviewMode(ReviewMode.MANUAL_DIFF);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        Long taskId = reviewTaskRepository.saveAndFlush(task).getId();

        ReviewRunEntity run = new ReviewRunEntity();
        run.setReviewTaskId(taskId);
        run.setRunNumber(1);
        run.setReviewMode(ReviewMode.MANUAL_DIFF);
        run.setStatus(ReviewRunStatus.PENDING);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        Long runId = reviewRunRepository.saveAndFlush(run).getId();

        ReviewInputSnapshotEntity snapshot = new ReviewInputSnapshotEntity();
        snapshot.setReviewRunId(runId);
        snapshot.setRepoUrl(task.getRepoUrl());
        snapshot.setPrNumber(task.getPrNumber());
        snapshot.setSnapshotJson(snapshotJson);
        snapshot.setCreatedAt(now);
        reviewInputSnapshotRepository.saveAndFlush(snapshot);

        assertThat(reviewTaskRepository.findById(taskId).orElseThrow().getDiffText()).isEqualTo(diffText);
        assertThat(reviewInputSnapshotRepository.findByReviewRunId(runId).orElseThrow().getSnapshotJson())
                .isEqualTo(snapshotJson);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT CAST(diff_text AS text) FROM review_task WHERE id = ?", String.class, taskId))
                .isEqualTo(diffText);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT CAST(snapshot_json AS text) FROM review_input_snapshot WHERE review_run_id = ?",
                String.class, runId))
                .isEqualTo(snapshotJson);
    }
}
