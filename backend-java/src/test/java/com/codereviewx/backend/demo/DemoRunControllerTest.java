package com.codereviewx.backend.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void anonymousAdminPublishIsAlwaysRejected() throws Exception {
        mvc.perform(post("/api/admin/demo-runs/" + UUID.randomUUID() + "/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedPreviewIds\":[1]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("ADMIN_AUTH_REQUIRED")));
    }
}
