package com.codereviewx.backend.rag.controller;

import com.codereviewx.backend.common.GlobalExceptionHandler;
import com.codereviewx.backend.rag.persistence.RagIndexJobStore;
import com.codereviewx.backend.rag.persistence.RagRepositoryStore;
import com.codereviewx.backend.rag.service.RagIndexJob;
import com.codereviewx.backend.rag.service.RagIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RepositoryIndexControllerTest {
    private static final String SHA = "a".repeat(40);
    @Mock RagRepositoryStore repositories;
    @Mock RagIndexJobStore jobs;
    @Mock RagIndexService index;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = mvc(new RepositoryIndexController(repositories, jobs, index));
    }

    @Test
    void queueReturns202SafeBodyWithoutRunningIndexerSynchronously() throws Exception {
        when(repositories.ensure(anyString(), eq("acme"), eq("demo"), anyString(), eq("main"), anyString(), anyInt(), anyInt()))
                .thenReturn(repository());
        when(jobs.createOrGetActive(7L, "main", "API", "", 1024, 1)).thenReturn(11L);

        MvcResult result = mvc.perform(post("/api/repositories/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://github.com/acme/demo\",\"ref\":\"main\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.repository").value("acme/demo"))
                .andExpect(jsonPath("$.data.requestedRef").value("main"))
                .andExpect(jsonPath("$.data.jobId").value(11))
                .andReturn();

        assertSafe(result);
        verify(index, never()).ensureIndexed(any());
        verify(index, never()).getJob(anyLong());
        verifyNoMoreInteractions(index);
    }

    @Test
    void gitSuffixIsNormalizedBeforeLookupAndQueue() throws Exception {
        when(repositories.ensure(anyString(), eq("acme"), eq("demo"), eq("https://github.com/acme/demo.git"),
                eq("release-1"), anyString(), anyInt(), anyInt())).thenReturn(repository());
        when(jobs.createOrGetActive(anyLong(), anyString(), anyString(), anyString(), anyInt(), anyInt())).thenReturn(12L);

        mvc.perform(post("/api/repositories/index").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://github.com/acme/demo.git\",\"ref\":\"release-1\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.repository").value("acme/demo"));
    }

    @Test
    void readyStatusReturnsOnlySafePublicState() throws Exception {
        when(repositories.find("github", "acme", "demo")).thenReturn(Optional.of(repository()));
        when(jobs.findReadySnapshot(7L, SHA, "text-embedding", 1024, 1))
                .thenReturn(Optional.of(job(RagIndexJob.Status.READY, SHA)));

        MvcResult result = mvc.perform(get("/api/repositories/acme/demo/index-status").param("commitSha", SHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.commitSha").value(SHA))
                .andExpect(jsonPath("$.data.indexedChunks").value(0))
                .andExpect(jsonPath("$.data.repositoryId").doesNotExist())
                .andReturn();
        assertSafe(result);
    }

    @Test
    void missingSnapshotReturnsNotIndexed() throws Exception {
        when(repositories.find("github", "acme", "demo")).thenReturn(Optional.of(repository()));
        when(jobs.findReadySnapshot(7L, SHA, "text-embedding", 1024, 1)).thenReturn(Optional.empty());
        when(jobs.findLatest(7L, SHA)).thenReturn(Optional.empty());

        mvc.perform(get("/api/repositories/acme/demo/index-status").param("commitSha", SHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NOT_INDEXED"))
                .andExpect(jsonPath("$.data.commitSha").isEmpty());
    }

    @Test
    void requestedShaMissFallsBackToLatestQueuedDefaultBranchJob() throws Exception {
        when(repositories.find("github", "acme", "demo")).thenReturn(Optional.of(repository()));
        when(jobs.findReadySnapshot(7L, SHA, "text-embedding", 1024, 1)).thenReturn(Optional.empty());
        when(jobs.findLatest(7L, SHA)).thenReturn(Optional.empty());
        when(jobs.findLatest(7L, "main")).thenReturn(Optional.of(job(RagIndexJob.Status.QUEUED, null)));

        mvc.perform(get("/api/repositories/acme/demo/index-status").param("commitSha", SHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.commitSha").isEmpty());
    }

    @Test
    void statusByValidatedRefReturnsQueuedJob() throws Exception {
        when(repositories.find("github", "acme", "demo")).thenReturn(Optional.of(repository()));
        when(jobs.findLatest(7L, "release")).thenReturn(Optional.of(job(RagIndexJob.Status.QUEUED, null)));
        mvc.perform(get("/api/repositories/acme/demo/index-status").param("ref", "release"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void statusRequiresCommitShaOrRefAndRejectsUnsafeRef() throws Exception {
        mvc.perform(get("/api/repositories/acme/demo/index-status")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/repositories/acme/demo/index-status").param("ref", "bad/ref")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownRepositoryReturns404() throws Exception {
        when(repositories.find("github", "missing", "repo")).thenReturn(Optional.empty());
        mvc.perform(get("/api/repositories/missing/repo/index-status").param("commitSha", SHA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Not found"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void existingQueuedIndexReturns409() throws Exception { assertIndexConflict(RagIndexJob.Status.QUEUED); }

    @Test
    void existingRunningIndexReturns409() throws Exception { assertIndexConflict(RagIndexJob.Status.RUNNING); }

    @Test
    void disabledIndexReturns503EvenWhenOptionalBeansAreAbsent() throws Exception {
        MockMvc disabled = mvc(new RepositoryIndexController(null, null, null, false));
        disabled.perform(post("/api/repositories/index").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://github.com/acme/demo\",\"ref\":\"main\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("RAG unavailable"));
    }

    @Test
    void disabledStatusReturns503EvenWhenOptionalBeansAreAbsent() throws Exception {
        MockMvc disabled = mvc(new RepositoryIndexController(null, null, null, false));
        disabled.perform(get("/api/repositories/acme/demo/index-status").param("commitSha", SHA))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void reindexQueuesKnownRepositoryWithSafe202Body() throws Exception {
        when(repositories.find("github", "acme", "demo")).thenReturn(Optional.of(repository()));
        when(jobs.createOrGetActive(7L, "release", "API", "text-embedding", 1024, 1)).thenReturn(13L);

        MvcResult result = mvc.perform(post("/api/repositories/acme/demo/reindex").param("ref", "release"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.repository").value("acme/demo"))
                .andExpect(jsonPath("$.data.requestedRef").value("release"))
                .andExpect(jsonPath("$.data.jobId").value(13))
                .andReturn();
        assertSafe(result);
        verifyNoInteractions(index);
    }

    @Test
    void reindexActiveJobReturns409() throws Exception {
        when(repositories.find("github", "acme", "demo")).thenReturn(Optional.of(repository()));
        when(jobs.findLatest(7L, "main")).thenReturn(Optional.of(job(RagIndexJob.Status.RUNNING, null)));
        mvc.perform(post("/api/repositories/acme/demo/reindex").param("ref", "main"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Already queued"));
        verify(jobs, never()).createOrGetActive(anyLong(), anyString(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void invalidRepositoryInputsReturn400WithoutStoreCalls() throws Exception {
        String longUrl = "https://github.com/acme/" + "r".repeat(990);
        String longRef = "r".repeat(256);
        String[][] bodies = {
                {"missing URL", "{\"ref\":\"main\"}"},
                {"blank URL", "{\"repoUrl\":\"\",\"ref\":\"main\"}"},
                {"unsafe URL", "{\"repoUrl\":\"http://evil.test/acme/demo\",\"ref\":\"main\"}"},
                {"URL query", "{\"repoUrl\":\"https://github.com/acme/demo?token=secret\",\"ref\":\"main\"}"},
                {"missing ref", "{\"repoUrl\":\"https://github.com/acme/demo\"}"},
                {"unsafe ref", "{\"repoUrl\":\"https://github.com/acme/demo\",\"ref\":\"main/../../secret\"}"},
                {"oversized URL", "{\"repoUrl\":\"" + longUrl + "\",\"ref\":\"main\"}"},
                {"oversized ref", "{\"repoUrl\":\"https://github.com/acme/demo\",\"ref\":\"" + longRef + "\"}"}
        };
        for (String[] testCase : bodies) {
            mvc.perform(post("/api/repositories/index").contentType(MediaType.APPLICATION_JSON).content(testCase[1]))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(repositories, jobs, index);
    }

    @Test
    void invalidOwnerRepoShaAndReindexRefReturn400() throws Exception {
        mvc.perform(get("/api/repositories/bad!owner/demo/index-status").param("commitSha", SHA)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/repositories/acme/bad!repo/index-status").param("commitSha", SHA)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/repositories/acme/demo/index-status")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/repositories/acme/demo/index-status").param("commitSha", "abc")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/repositories/acme/demo/reindex").param("ref", "bad/ref")).andExpect(status().isBadRequest());
        verifyNoInteractions(repositories, jobs, index);
    }

    private void assertIndexConflict(RagIndexJob.Status status) throws Exception {
        when(repositories.find("github", "acme", "demo")).thenReturn(Optional.of(repository()));
        when(jobs.findLatest(7L, "main")).thenReturn(Optional.of(job(status, null)));
        mvc.perform(post("/api/repositories/index").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://github.com/acme/demo\",\"ref\":\"main\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Already queued"));
        verify(repositories, never()).ensure(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt());
    }

    private static MockMvc mvc(RepositoryIndexController controller) {
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static RagRepositoryStore.RepositoryRecord repository() {
        return new RagRepositoryStore.RepositoryRecord(7, "github", "acme", "demo",
                "https://github.com/acme/demo.git", "main", null, "QUEUED", 1, "text-embedding", 1024);
    }

    private static RagIndexJob job(RagIndexJob.Status status, String sha) {
        return new RagIndexJob(11, 7, "main", sha, status, 0, null, null, null,
                null, null, "text-embedding", 1024, 1);
    }

    private static void assertSafe(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("token", "secret", "query", "prompt", "content", "vector",
                "repositoryId", "chunkId", "traceId");
    }
}
