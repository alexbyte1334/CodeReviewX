package com.codereviewx.backend.rag.controller;

import com.codereviewx.backend.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RetrievalEvidenceControllerTest {
    JdbcTemplate jdbc;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        mvc = mvc(new RetrievalEvidenceController(jdbc));
    }

    @Test
    void positiveTraceMapsSafePublicFieldsAndUsesParameterizedDeterministicQuery() throws Exception {
        stubTrace(false, 42L, 8, 3);

        MvcResult result = mvc.perform(get("/api/review-runs/19/retrieval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.degraded").value(false))
                .andExpect(jsonPath("$.data.degradedReason").isEmpty())
                .andExpect(jsonPath("$.data.latencyMs").value(42))
                .andExpect(jsonPath("$.data.candidateCount").value(8))
                .andExpect(jsonPath("$.data.selectedCount").value(3))
                .andExpect(jsonPath("$.data.model").isEmpty())
                .andExpect(jsonPath("$.data.evidence").isArray())
                .andReturn();
        assertSafe(result);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq(19L));
        assertThat(sql.getValue()).contains("review_run_id=?", "ORDER BY created_at DESC,id DESC", "LIMIT 1")
                .doesNotContain("19", "commit_sha", "query_hash", "result_summary_json", "repository_id");
    }

    @Test
    void degradedTraceMapsStableReason() throws Exception {
        stubTrace(true, 91L, 5, 1);
        mvc.perform(get("/api/review-runs/20/retrieval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.degraded").value(true))
                .andExpect(jsonPath("$.data.degradedReason").value("RETRIEVAL_DEGRADED"));
    }

    @Test
    void unknownTraceReturnsSafe404() throws Exception {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(99L))).thenReturn(List.of());
        mvc.perform(get("/api/review-runs/99/retrieval"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Not found"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void ownedIssueEvidenceMapsSafeFieldsAndUsesParameterizedDeterministicQuery() throws Exception {
        stubEvidence();
        MvcResult result = mvc.perform(get("/api/review-tasks/7/issues/ISS-1/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].citationLabel").value("E1"))
                .andExpect(jsonPath("$.data[0].path").value("src/App.java"))
                .andExpect(jsonPath("$.data[0].startLine").value(10))
                .andExpect(jsonPath("$.data[0].endLine").value(14))
                .andExpect(jsonPath("$.data[0].excerpt").value("safe excerpt"))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].score").value(0.91))
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                .andExpect(jsonPath("$.data[0].contentHash").doesNotExist())
                .andReturn();
        assertSafe(result);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq(7L), eq("ISS-1"));
        assertThat(sql.getValue()).contains("i.review_task_id=?", "i.issue_key=?",
                        "ORDER BY e.retrieval_rank,e.citation_label")
                .doesNotContain("content_hash", "rag_chunk_id", "SELECT e.id");
    }

    @Test
    void unknownOrMismatchedIssueOwnershipReturns404() throws Exception {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(7L), eq("OTHER"))).thenReturn(List.of());
        mvc.perform(get("/api/review-tasks/7/issues/OTHER/evidence"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Not found"));
    }

    @Test
    void disabledEndpointsReturn503WithoutJdbcAccess() throws Exception {
        MockMvc disabled = mvc(new RetrievalEvidenceController(null, false));
        disabled.perform(get("/api/review-runs/1/retrieval")).andExpect(status().isServiceUnavailable());
        disabled.perform(get("/api/review-tasks/1/issues/ISS-1/evidence")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void invalidRunAndTaskIdsReturn400WithoutJdbcAccess() throws Exception {
        mvc.perform(get("/api/review-runs/0/retrieval")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/review-runs/-1/retrieval")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/review-runs/not-a-number/retrieval")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/review-tasks/0/issues/ISS-1/evidence")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/review-tasks/-1/issues/ISS-1/evidence")).andExpect(status().isBadRequest());
        verifyNoInteractions(jdbc);
    }

    @Test
    void invalidIssueKeysReturn400WithoutJdbcAccess() throws Exception {
        mvc.perform(get("/api/review-tasks/1/issues/bad!key/evidence")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/review-tasks/1/issues/" + "x".repeat(65) + "/evidence")).andExpect(status().isBadRequest());
        verifyNoInteractions(jdbc);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubTrace(boolean degraded, long latency, int candidates, int selected) throws Exception {
        when(jdbc.query(anyString(), any(RowMapper.class), anyLong())).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getBoolean(1)).thenReturn(degraded);
            when(rs.getLong(2)).thenReturn(latency);
            when(rs.getInt(3)).thenReturn(candidates);
            when(rs.getInt(4)).thenReturn(selected);
            return List.of(mapper.mapRow(rs, 0));
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubEvidence() throws Exception {
        when(jdbc.query(anyString(), any(RowMapper.class), anyLong(), anyString())).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString(1)).thenReturn("E1");
            when(rs.getString(2)).thenReturn("src/App.java");
            when(rs.getInt(3)).thenReturn(10);
            when(rs.getInt(4)).thenReturn(14);
            when(rs.getString(5)).thenReturn("safe excerpt");
            when(rs.getInt(6)).thenReturn(1);
            when(rs.getDouble(7)).thenReturn(0.91);
            return List.of(mapper.mapRow(rs, 0));
        });
    }

    private static MockMvc mvc(RetrievalEvidenceController controller) {
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static void assertSafe(MvcResult result) throws Exception {
        assertThat(result.getResponse().getContentAsString()).doesNotContain("repositoryId", "reviewRunId",
                "reviewIssueId", "ragChunkId", "queryHash", "contentHash", "commitSha", "rawContent", "prompt");
    }
}
