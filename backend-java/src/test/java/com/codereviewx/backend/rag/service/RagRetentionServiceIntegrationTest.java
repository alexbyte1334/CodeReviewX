package com.codereviewx.backend.rag.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RagRetentionServiceIntegrationTest {
    @Test void cleanupIsIdempotentAndUsesProtectedRetentionQuery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doReturn(2, 0).when(jdbc).update(anyString());
        RagRetentionService service = new RagRetentionService(jdbc);
        assertEquals(2, service.cleanup());
        assertEquals(0, service.cleanup());
        verify(jdbc, times(2)).update(argThat((String sql) -> sql.contains("RUNNING") && sql.contains("active_commit_sha") && sql.contains("rn <= 5") && sql.contains("30 days")));
    }
}
