package com.codereviewx.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void flywayMigratesSuccessfully() {
        assertThat(flyway.info().applied()).hasSizeGreaterThanOrEqualTo(2);
    }
}
