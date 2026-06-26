package com.codereviewx.backend.review.controller;

import com.codereviewx.backend.review.persistence.repository.ReviewCommentPreviewRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewInputSnapshotRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewIssueRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewProviderTraceRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewRunRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewTaskRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewToolTraceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReviewIssueRepository reviewIssueRepository;

    @Autowired
    private ReviewTaskRepository reviewTaskRepository;

    @Autowired
    private ReviewCommentPreviewRepository commentPreviewRepository;

    @Autowired
    private ReviewToolTraceRepository toolTraceRepository;

    @Autowired
    private ReviewProviderTraceRepository providerTraceRepository;

    @Autowired
    private ReviewInputSnapshotRepository inputSnapshotRepository;

    @Autowired
    private ReviewRunRepository reviewRunRepository;

    private static final String BASE_URL = "/api/review-tasks";

    private static final String SAMPLE_DIFF = "diff --git a/a.txt b/a.txt\\n";

    private static String manualDiffBody(int prNumber) {
        return "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":" + prNumber
                + ",\"diffText\":\"" + SAMPLE_DIFF + "\"}";
    }

    private static final String GITHUB_PR_BODY =
            "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":123}";

    @BeforeEach
    void setUp() {
        commentPreviewRepository.deleteAll();
        toolTraceRepository.deleteAll();
        providerTraceRepository.deleteAll();
        inputSnapshotRepository.deleteAll();
        reviewRunRepository.deleteAll();
        reviewIssueRepository.deleteAll();
        reviewTaskRepository.deleteAll();
    }

    @Test
    void createTask_manualDiff_success() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualDiffBody(123)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("OK")))
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.repoUrl", is("https://github.com/example/repo")))
                .andExpect(jsonPath("$.data.prNumber", is(123)))
                .andExpect(jsonPath("$.data.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.reviewMode", is("MANUAL_DIFF")))
                .andExpect(jsonPath("$.data.latestRunId", notNullValue()))
                .andExpect(jsonPath("$.data.riskLevel", is("HIGH")))
                .andExpect(jsonPath("$.data.summary", containsString("Review completed for PR #123")))
                .andExpect(jsonPath("$.data.summary", not(containsString("Mock"))))
                .andExpect(jsonPath("$.data.issues", hasSize(3)));
    }

    @Test
    void createTask_githubPr_withoutDiff_returnsAuthMissing() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GITHUB_PR_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("FAILED")))
                .andExpect(jsonPath("$.data.reviewMode", is("GITHUB_PR")))
                .andExpect(jsonPath("$.data.latestRunId", notNullValue()))
                .andExpect(jsonPath("$.data.errorCode", is("GITHUB_AUTH_MISSING")))
                .andExpect(jsonPath("$.data.errorMessage", containsString("GITHUB_TOKEN")))
                .andExpect(jsonPath("$.data.traceSummary.toolCount", is(1)))
                .andExpect(jsonPath("$.data.traceSummary.failedToolCount", is(1)))
                .andExpect(jsonPath("$.data.issues", hasSize(0)));
    }

    @Test
    void createTask_issuesHaveTypedFields() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualDiffBody(123)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.issues[0].id", is("ISSUE-1")))
                .andExpect(jsonPath("$.data.issues[0].severity", is("HIGH")))
                .andExpect(jsonPath("$.data.issues[0].category", is("SECURITY")))
                .andExpect(jsonPath("$.data.issues[0].filePath", notNullValue()))
                .andExpect(jsonPath("$.data.issues[0].startLine", notNullValue()))
                .andExpect(jsonPath("$.data.issues[0].endLine", notNullValue()))
                .andExpect(jsonPath("$.data.issues[0].title", notNullValue()))
                .andExpect(jsonPath("$.data.issues[0].description", notNullValue()))
                .andExpect(jsonPath("$.data.issues[0].recommendation", notNullValue()));
    }

    @Test
    void createTask_issuesHaveSourceAndStatus() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualDiffBody(123)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.issues[0].source", is("MOCK")))
                .andExpect(jsonPath("$.data.issues[0].status", is("OPEN")))
                .andExpect(jsonPath("$.data.issues[1].source", is("MOCK")))
                .andExpect(jsonPath("$.data.issues[1].status", is("OPEN")))
                .andExpect(jsonPath("$.data.issues[2].source", is("MOCK")))
                .andExpect(jsonPath("$.data.issues[2].status", is("OPEN")));
    }

    @Test
    void createTask_hasIssueSummary() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualDiffBody(123)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.issueSummary", notNullValue()))
                .andExpect(jsonPath("$.data.issueSummary.totalIssues", is(3)))
                .andExpect(jsonPath("$.data.issueSummary.highCount", is(1)))
                .andExpect(jsonPath("$.data.issueSummary.mediumCount", is(1)))
                .andExpect(jsonPath("$.data.issueSummary.lowCount", is(1)))
                .andExpect(jsonPath("$.data.issueSummary.riskLevel", is("HIGH")));
    }

    @Test
    void createTask_returnsProviderHitFields() throws Exception {
        String body = "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":123,\"provider\":\"mock\",\"diffText\":\""
                + SAMPLE_DIFF + "\"}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestedProvider", is("mock")))
                .andExpect(jsonPath("$.data.providerUsed", is("mock")))
                .andExpect(jsonPath("$.data.providerHit", is(true)));
    }

    @Test
    void createTask_mimoWithoutKey_returnsProviderMiss() throws Exception {
        String body = "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":123,\"provider\":\"mimo\",\"diffText\":\""
                + SAMPLE_DIFF + "\"}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestedProvider", is("mimo")))
                .andExpect(jsonPath("$.data.providerUsed", is("mock")))
                .andExpect(jsonPath("$.data.providerHit", is(false)));
    }

    @Test
    void createTask_riskLevelConsistentWithIssueSummary() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualDiffBody(123)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskLevel", is("HIGH")))
                .andExpect(jsonPath("$.data.issueSummary.riskLevel", is("HIGH")));
    }

    @Test
    void listTasks_success() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    void listTasks_containsIssueSummary() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualDiffBody(200)))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data[0].issueSummary", notNullValue()))
                .andExpect(jsonPath("$.data[0].issueSummary.totalIssues", is(3)));
    }

    @Test
    void getTask_success() throws Exception {
        String createResult = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualDiffBody(99)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Integer id = objectMapper.readTree(createResult).path("data").path("id").intValue();

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(id)))
                .andExpect(jsonPath("$.data.prNumber", is(99)))
                .andExpect(jsonPath("$.data.issues", hasSize(3)));
    }

    @Test
    void getTask_containsIssueSummary() throws Exception {
        String createResult = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualDiffBody(77)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Integer id = objectMapper.readTree(createResult).path("data").path("id").intValue();

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.issueSummary", notNullValue()))
                .andExpect(jsonPath("$.data.issueSummary.totalIssues", is(3)))
                .andExpect(jsonPath("$.data.issueSummary.highCount", is(1)))
                .andExpect(jsonPath("$.data.issueSummary.mediumCount", is(1)))
                .andExpect(jsonPath("$.data.issueSummary.lowCount", is(1)))
                .andExpect(jsonPath("$.data.issueSummary.riskLevel", is("HIGH")))
                .andExpect(jsonPath("$.data.riskLevel", is("HIGH")));
    }

    @Test
    void getTask_notFound_returnsError() throws Exception {
        mockMvc.perform(get(BASE_URL + "/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Review task not found")));
    }

    @Test
    void createTask_invalidRepoUrl_returnsValidationError() throws Exception {
        String body = "{\"repoUrl\":\"\",\"prNumber\":123}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void createTask_invalidPrNumber_returnsValidationError() throws Exception {
        String body = "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":-1}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void createTask_withDiffText_succeeds() throws Exception {
        String body = """
                {"repoUrl":"https://github.com/example/repo","prNumber":10,"diffText":"diff --git a/src/App.tsx b/src/App.tsx\\n+const x = 1;\\n"}
                """;

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.issues", hasSize(3)))
                .andExpect(jsonPath("$.data.riskLevel", is("HIGH")))
                .andExpect(jsonPath("$.data.issueSummary.riskLevel", is("HIGH")))
                .andExpect(jsonPath("$.data.latestRunId", notNullValue()))
                .andExpect(jsonPath("$.data.diffText").doesNotExist());
    }

    @Test
    void createTask_blankDiffText_treatedAsGithubPrFailed() throws Exception {
        String body = "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":10,\"diffText\":\"\"}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("FAILED")))
                .andExpect(jsonPath("$.data.reviewMode", is("GITHUB_PR")))
                .andExpect(jsonPath("$.data.errorCode", is("GITHUB_AUTH_MISSING")))
                .andExpect(jsonPath("$.data.issues", hasSize(0)));
    }

    @Test
    void createTask_whitespaceDiffText_treatedAsGithubPrFailed() throws Exception {
        String body = "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":10,\"diffText\":\"   \\n\\t  \"}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("FAILED")))
                .andExpect(jsonPath("$.data.reviewMode", is("GITHUB_PR")))
                .andExpect(jsonPath("$.data.errorCode", is("GITHUB_AUTH_MISSING")))
                .andExpect(jsonPath("$.data.issues", hasSize(0)));
    }

    @Test
    void createTask_explicitManualDiffWithoutDiffText_returnsValidationError() throws Exception {
        String body = "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":10,\"reviewMode\":\"MANUAL_DIFF\"}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("MANUAL_DIFF requires non-blank diffText")));
    }

    @Test
    void createTask_tooLargeDiffText_returnsValidationError() throws Exception {
        String oversizedDiff = "x".repeat(com.codereviewx.backend.review.dto.CreateReviewTaskRequest.MAX_DIFF_TEXT_LENGTH + 1);
        String body = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "repoUrl", "https://github.com/example/repo",
                        "prNumber", 10,
                        "diffText", oversizedDiff
                )
        );

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("diffText is too large")));
    }

    @Test
    void createTask_invalidProvider_returnsValidationError() throws Exception {
        String body = "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":10,\"provider\":\"openai\"}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }
}
