package com.codereviewx.backend.review.controller;

import com.codereviewx.backend.review.dto.ReviewApiSnapshot;
import com.codereviewx.backend.review.service.ReviewApiService;
import com.codereviewx.backend.config.DeploymentModeProperties;
import com.codereviewx.backend.demo.DemoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewApiController.class)
class ReviewApiControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ReviewApiService service;
    @MockBean DeploymentModeProperties deploymentModeProperties;
    @MockBean DemoProperties demoProperties;

    @Test
    void createReturnsAcceptedAndForwardsIdempotencyKey() throws Exception {
        ReviewApiSnapshot snapshot = new ReviewApiSnapshot("00000000-0000-0000-0000-000000000001", "QUEUED", "LIVE", "https://github.com/a/b", 1, 1L, 1L, null, List.of(), null, null);
        when(service.create(any(), eq("client-key"))).thenReturn(snapshot);
        mvc.perform(post("/api/reviews").header("Idempotency-Key", "client-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repositoryUrl\":\"https://github.com/a/b\",\"prNumber\":1,\"inputMode\":\"GITHUB_PR\"}"))
                .andExpect(status().isAccepted());
        verify(service).create(any(), eq("client-key"));
    }
}
