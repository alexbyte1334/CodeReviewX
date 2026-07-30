package com.codereviewx.backend.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "codereviewx.demo.enabled=true",
        "codereviewx.demo.pr-number=42",
        "codereviewx.demo.expected-head-sha=abc123",
        "codereviewx.demo.admin-token=test-admin",
        "codereviewx.demo.global-concurrency=10",
        "codereviewx.demo.concurrent-per-ip=10"
})
@AutoConfigureMockMvc
class DemoRunControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @MockBean DemoExecutionWorker worker;

    @BeforeEach
    void cleanDemoState() {
        jdbc.update("DELETE FROM review_run_event");
        jdbc.update("DELETE FROM review_execution_job");
        jdbc.update("DELETE FROM demo_run");
        jdbc.update("DELETE FROM demo_request_bucket");
    }

    @AfterEach
    void cleanDemoStateAfterTest() {
        cleanDemoState();
    }

    @Test
    void createIsAsyncOpaqueAndIdempotent() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = "{\"scenarioId\":\"sql-injection-pr\"}";
        String first = mvc.perform(post("/api/demo-runs")
                        .header("Idempotency-Key", key)
                        .header("X-Forwarded-For", "203.0.113.10")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId", matchesPattern(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
                .andExpect(jsonPath("$.status", is("QUEUED")))
                .andExpect(jsonPath("$.mode", is("LIVE")))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/demo-runs")
                        .header("Idempotency-Key", key)
                        .header("X-Forwarded-For", "203.0.113.10")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId", is(
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(first).path("runId").asText())));
    }

    @Test
    void rejectsUnknownScenarioBeforeCreatingTask() throws Exception {
        mvc.perform(post("/api/demo-runs")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioId\":\"arbitrary-repository\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("SCENARIO_NOT_ALLOWED")));
    }

    @Test
    void rejectsMalformedOpaqueRunId() throws Exception {
        mvc.perform(get("/api/demo-runs/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("DEMO_RUN_NOT_FOUND")));
    }

    @Test
    void enforcesThreeRequestsPerHourPerHashedIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/demo-runs")
                            .header("Idempotency-Key", UUID.randomUUID())
                            .header("X-Forwarded-For", "203.0.113.20")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"scenarioId\":\"sql-injection-pr\"}"))
                    .andExpect(status().isAccepted());
        }
        mvc.perform(post("/api/demo-runs")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .header("X-Forwarded-For", "203.0.113.20")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioId\":\"sql-injection-pr\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("DEMO_RATE_LIMITED")));
    }

    @Test
    void forgedPreviewDecisionDoesNotPartiallyClearOwnedSelection() throws Exception {
        SeededDemo demo = seedReadyDemo("203.0.113.30");
        jdbc.update("UPDATE review_comment_preview SET selected_for_publish=TRUE WHERE id=?",
                demo.previewId());

        mvc.perform(post("/api/demo-runs/" + demo.publicId() + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE_PREVIEW\",\"selectedPreviewIds\":["
                                + demo.previewId() + ",999999]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("PREVIEW_OWNERSHIP_MISMATCH")));

        assertThat(jdbc.queryForObject("""
                SELECT selected_for_publish FROM review_comment_preview WHERE id=?
                """, Boolean.class, demo.previewId())).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT decision FROM demo_run WHERE public_id=?
                """, String.class, demo.publicId())).isNull();
    }

    @Test
    void anonymousRejectClearsSelectionsWithoutPublishing() throws Exception {
        SeededDemo demo = seedReadyDemo("203.0.113.31");
        jdbc.update("UPDATE review_comment_preview SET selected_for_publish=TRUE WHERE id=?",
                demo.previewId());

        mvc.perform(post("/api/demo-runs/" + demo.publicId() + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\",\"selectedPreviewIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("REJECT")));

        assertThat(jdbc.queryForObject("""
                SELECT selected_for_publish FROM review_comment_preview WHERE id=?
                """, Boolean.class, demo.previewId())).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT published_comment_url FROM demo_run WHERE public_id=?
                """, String.class, demo.publicId())).isNull();
    }

    @Test
    void rejectsNonUuidIdempotencyKeyBeforeCreatingTask() throws Exception {
        mvc.perform(post("/api/demo-runs")
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioId\":\"sql-injection-pr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("INVALID_IDEMPOTENCY_KEY")));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_run", Integer.class)).isZero();
    }

    private SeededDemo seedReadyDemo(String ip) throws Exception {
        String response = mvc.perform(post("/api/demo-runs")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioId\":\"sql-injection-pr\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String publicId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).path("runId").asText();
        Long reviewRunId = jdbc.queryForObject(
                "SELECT review_run_id FROM demo_run WHERE public_id=?",
                Long.class, publicId);
        jdbc.update("UPDATE demo_run SET status='READY' WHERE public_id=?", publicId);
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO review_comment_preview(
                  review_run_id,issue_key,file_path,line_number,side,draft_body,
                  severity,category,source,selected_for_publish,publish_status,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,FALSE,'NOT_PUBLISHED',?,?)
                """, reviewRunId, "SQL-1", "src/App.java", 1, "RIGHT",
                "Use a bound parameter.", "HIGH", "SECURITY", "MIMO", now, now);
        Long previewId = jdbc.queryForObject("""
                SELECT id FROM review_comment_preview WHERE review_run_id=?
                """, Long.class, reviewRunId);
        return new SeededDemo(publicId, previewId);
    }

    private record SeededDemo(String publicId, Long previewId) {}
}
