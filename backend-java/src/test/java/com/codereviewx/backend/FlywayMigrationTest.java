package com.codereviewx.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayMigratesSuccessfully() {
        assertThat(flyway.info().applied()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name='REVIEW_API_RUN'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN ('REVIEW_TASK','REVIEW_RUN')", Integer.class)).isZero();
    }
}
