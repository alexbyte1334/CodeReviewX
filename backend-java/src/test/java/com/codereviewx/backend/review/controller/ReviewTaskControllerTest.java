package com.codereviewx.backend.review.controller;

import com.codereviewx.backend.review.persistence.repository.ReviewIssueRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewTaskRepository;
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

    private static final String BASE_URL = "/api/review-tasks";

    @BeforeEach
    void setUp() {
        reviewIssueRepository.deleteAll();
        reviewTaskRepository.deleteAll();
    }

    @Test
    void createTask_success() throws Exception {
        String body = "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":123}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("OK")))
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.repoUrl", is("https://github.com/example/repo")))
                .andExpect(jsonPath("$.data.prNumber", is(123)))
                .andExpect(jsonPath("$.data.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.riskLevel", is("HIGH")))
                .andExpect(jsonPath("$.data.summary", containsString("Review completed for PR #123")))
                .andExpect(jsonPath("$.data.summary", not(containsString("Mock"))))
                .andExpect(jsonPath("$.data.issues", hasSize(3)));
    }

    @Test
    void createTask_issuesHaveTypedFields() throws Exception {
        String body = "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":123}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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
        String body = "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":123}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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
        String body = "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":123}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.issueSummary", notNullValue()))
                .andExpect(jsonPath("$.data.issueSummary.totalIssues", is(3)))
                .andExpect(jsonPath("$.data.issueSummary.highCount", is(1)))
                .andExpect(jsonPath("$.data.issueSummary.mediumCount", is(1)))
                .andExpect(jsonPath("$.data.issueSummary.lowCount", is(1)))
                .andExpect(jsonPath("$.data.issueSummary.riskLevel", is("HIGH")));
    }

    @Test
    void createTask_riskLevelConsistentWithIssueSummary() throws Exception {
        String body = "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":123}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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
        String body = "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":200}";
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data[0].issueSummary", notNullValue()))
                .andExpect(jsonPath("$.data[0].issueSummary.totalIssues", is(3)));
    }

    @Test
    void getTask_success() throws Exception {
        String body = "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":99}";

        String createResult = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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
        String body = "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":77}";

        String createResult = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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
                .andExpect(jsonPath("$.data.diffText").doesNotExist());
    }

    @Test
    void createTask_blankDiffText_treatedAsAbsent() throws Exception {
        String body = "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":10,\"diffText\":\"\"}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.issues", hasSize(3)));
    }

    @Test
    void createTask_whitespaceDiffText_treatedAsAbsent() throws Exception {
        String body = "{\"repoUrl\":\"https://github.com/example/repo\",\"prNumber\":10,\"diffText\":\"   \\n\\t  \"}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.issues", hasSize(3)));
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
}
