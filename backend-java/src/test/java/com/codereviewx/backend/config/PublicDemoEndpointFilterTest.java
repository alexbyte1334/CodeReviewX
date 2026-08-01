package com.codereviewx.backend.config;

import com.codereviewx.backend.demo.DemoExecutionWorker;
import com.codereviewx.backend.review.service.ReviewTaskService;
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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "codereviewx.deployment-mode=public-demo",
        "codereviewx.demo.enabled=true",
        "codereviewx.demo.pr-number=1",
        "codereviewx.demo.expected-head-sha=d5aa95a3f43f23ca438e53e94c4d3bed4868904a",
        "codereviewx.demo.admin-token=0123456789abcdef0123456789abcdef",
        "codereviewx.demo.ip-hash-salt=abcdef0123456789abcdef0123456789",
        "codereviewx.demo.requests-per-hour=1",
        "codereviewx.demo.concurrent-per-ip=10",
        "codereviewx.demo.global-concurrency=10"
})
@AutoConfigureMockMvc
class PublicDemoEndpointFilterTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @MockBean ReviewTaskService reviewTaskService;
    @MockBean DemoExecutionWorker worker;

    @BeforeEach
    void cleanDemoState() {
        jdbc.update("DELETE FROM review_run_event");
        jdbc.update("DELETE FROM review_execution_job");
        jdbc.update("DELETE FROM demo_run");
        jdbc.update("DELETE FROM demo_request_bucket");
    }

    @Test
    void allowsOnlyPublicDemoApiSurface() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(options("/api/demo-runs")
                        .header("Origin", "https://alexbyte1334.github.io")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk());

        String runId = createDemo("198.51.100.10", UUID.randomUUID());
        mvc.perform(get("/api/demo-runs/" + runId)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/demo-runs/" + runId + "/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedPreviewIds\":[1]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blocksLegacyReviewRunRetrievalAndIndexApisBeforeControllers() throws Exception {
        mvc.perform(post("/api/review-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://github.com/owner/repo\",\"prNumber\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("ENDPOINT_NOT_AVAILABLE"));
        mvc.perform(get("/api/review-tasks"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/review-runs/1/trace"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/review-runs/1/comment-previews/publish-selected")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/repositories/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://github.com/owner/repo\",\"ref\":\"main\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/repositories/owner/repo/index-status?ref=main"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(reviewTaskService);
    }

    @Test
    void forgedForwardedForCannotBypassRailwayRealIpRateLimit() throws Exception {
        mvc.perform(post("/api/demo-runs")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .header("X-Real-IP", "203.0.113.40")
                        .header("X-Forwarded-For", "198.51.100.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioId\":\"sql-injection-pr\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/demo-runs")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .header("X-Real-IP", "203.0.113.40")
                        .header("X-Forwarded-For", "198.51.100.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioId\":\"sql-injection-pr\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("DEMO_RATE_LIMITED")));
    }

    @Test
    void actuatorExposesHealthButNotMetrics() throws Exception {
        // The test context deliberately has no external provider credentials, so
        // aggregate readiness is DOWN; 503 still proves the health endpoint is exposed.
        mvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isNotFound());
    }

    private String createDemo(String ip, UUID idempotencyKey) throws Exception {
        String response = mvc.perform(post("/api/demo-runs")
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Real-IP", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioId\":\"sql-injection-pr\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("runId").asText();
    }
}
