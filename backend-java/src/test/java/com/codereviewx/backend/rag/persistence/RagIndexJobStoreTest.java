package com.codereviewx.backend.rag.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagIndexJobStoreTest {

    @Test
    void createOrGetActiveStopsAfterThreeUnresolvedConflicts() {
        AtomicInteger insertAttempts = new AtomicInteger();
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override
            public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
                if (sql.contains("INSERT INTO rag_index_job") && insertAttempts.incrementAndGet() > 3) {
                    throw new AssertionError("createOrGetActive attempted a fourth insert");
                }
                return List.of();
            }
        };

        RagIndexJobStore store = new RagIndexJobStore(jdbc);

        assertThatThrownBy(() -> store.createOrGetActive(7L, "main", "API", "test-model", 1024, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to resolve active index job");
        assertThat(insertAttempts).hasValue(3);
    }
}
