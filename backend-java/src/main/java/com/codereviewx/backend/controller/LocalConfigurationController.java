package com.codereviewx.backend.controller;

import com.codereviewx.backend.common.ApiResponse;
import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.embedding.OpenAiEmbeddingClient;
import com.codereviewx.backend.rag.retrieval.HttpRerankClient;
import com.codereviewx.backend.rag.retrieval.RerankCandidate;
import com.codereviewx.backend.review.github.GithubProperties;
import com.codereviewx.backend.review.pipeline.provider.ModelProviderPresets;
import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoClient;
import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** Local-only configuration bridge used by the desktop shell. It never echoes secrets. */
@RestController
@RequestMapping("/api/local/config")
public class LocalConfigurationController {
    private final XiaomiMiMoProperties model;
    private final GithubProperties github;
    private final RagProperties rag;
    private final XiaomiMiMoClient modelClient;
    private final ObjectMapper objectMapper;

    public LocalConfigurationController(XiaomiMiMoProperties model, GithubProperties github,
                                        RagProperties rag, XiaomiMiMoClient modelClient, ObjectMapper objectMapper) {
        this.model = model;
        this.github = github;
        this.rag = rag;
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/presets")
    public ApiResponse<Map<String, Map<String, String>>> presets() {
        return ApiResponse.success(ModelProviderPresets.all());
    }

    @GetMapping("/status")
    public ApiResponse<LocalConfigStatus> status() {
        return ApiResponse.success(states());
    }

    @PostMapping("/apply")
    public ApiResponse<LocalConfigStatus> apply(@Valid @RequestBody LocalConfigRequest request) {
        model.setProvider(request.provider());
        model.setBaseUrl(request.modelBaseUrl());
        model.setModel(request.modelName());
        model.setApiKey(request.modelApiKey());
        github.setToken(request.githubToken());
        rag.setEmbeddingBaseUrl(request.embeddingBaseUrl());
        rag.setEmbeddingApiKey(request.embeddingApiKey());
        rag.setEmbeddingModel(request.embeddingModel());
        rag.setRerankBaseUrl(request.rerankBaseUrl());
        rag.setRerankApiKey(request.rerankApiKey());
        rag.setRerankModel(request.rerankModel());
        return ApiResponse.success(states());
    }

    @PostMapping("/test-model")
    public ApiResponse<Map<String, String>> testModel(@Valid @RequestBody ModelTestRequest request) {
        String oldProvider = model.getProvider(), oldBaseUrl = model.getBaseUrl(), oldModel = model.getModel(), oldKey = model.getApiKey();
        try {
            model.setProvider(request.provider()); model.setBaseUrl(request.baseUrl()); model.setModel(request.model()); model.setApiKey(request.apiKey());
            modelClient.complete("Return only the JSON object {\"status\":\"ok\"}.", "Health check.");
            return ApiResponse.success(Map.of("model", "READY"));
        } catch (RuntimeException failure) {
            return ApiResponse.failure(safeMessage(failure, "Model connection failed"));
        } finally {
            model.setProvider(oldProvider); model.setBaseUrl(oldBaseUrl); model.setModel(oldModel); model.setApiKey(oldKey);
        }
    }

    @PostMapping("/test-github")
    public ApiResponse<Map<String, String>> testGithub(@Valid @RequestBody GithubTestRequest request) {
        if (request.token().isBlank()) return ApiResponse.success(Map.of("github", "AUTH_REQUIRED"));
        try {
            URI endpoint = URI.create(request.baseUrl().replaceAll("/+$", "") + "/user");
            HttpResponse<Void> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                    .send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                            .header("Authorization", "Bearer " + request.token()).header("Accept", "application/vnd.github+json")
                            .GET().build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) return ApiResponse.success(Map.of("github", "READY"));
            if (response.statusCode() == 401 || response.statusCode() == 403) return ApiResponse.success(Map.of("github", "FAILED", "reason", "GitHub token was rejected or rate limited"));
            return ApiResponse.success(Map.of("github", "FAILED", "reason", "GitHub connection returned HTTP " + response.statusCode()));
        } catch (Exception failure) {
            return ApiResponse.success(Map.of("github", "FAILED", "reason", "GitHub connection failed"));
        }
    }

    @PostMapping("/test-rag")
    public ApiResponse<Map<String, String>> testRag(@Valid @RequestBody RagTestRequest request) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!notBlank(request.embeddingBaseUrl()) && !notBlank(request.embeddingApiKey())
                && !notBlank(request.rerankBaseUrl()) && !notBlank(request.rerankApiKey())) {
            result.put("embedding", "OPTIONAL_NOT_CONFIGURED"); result.put("rerank", "OPTIONAL_NOT_CONFIGURED"); return ApiResponse.success(result);
        }
        try {
            RagProperties candidate = new RagProperties();
            candidate.setEmbeddingBaseUrl(request.embeddingBaseUrl()); candidate.setEmbeddingApiKey(request.embeddingApiKey()); candidate.setEmbeddingModel(request.embeddingModel());
            candidate.setRerankBaseUrl(request.rerankBaseUrl()); candidate.setRerankApiKey(request.rerankApiKey()); candidate.setRerankModel(request.rerankModel());
            candidate.setTimeoutSeconds(10); candidate.setMaxRetries(0);
            new OpenAiEmbeddingClient(candidate, objectMapper).embed(List.of("CodeReviewX connection test"));
            result.put("embedding", "READY");
        } catch (RuntimeException failure) { result.put("embedding", "FAILED"); result.put("embeddingReason", safeMessage(failure, "Embedding connection failed")); }
        try {
            RagProperties candidate = new RagProperties();
            candidate.setRerankBaseUrl(request.rerankBaseUrl()); candidate.setRerankApiKey(request.rerankApiKey()); candidate.setRerankModel(request.rerankModel());
            candidate.setTimeoutSeconds(10); candidate.setMaxRetries(0);
            new HttpRerankClient(candidate, objectMapper).rerank("CodeReviewX connection test", List.of(new RerankCandidate("test", "connection test")));
            result.put("rerank", "READY");
        } catch (RuntimeException failure) { result.put("rerank", "FAILED"); result.put("rerankReason", safeMessage(failure, "Rerank connection failed")); }
        return ApiResponse.success(result);
    }

    @DeleteMapping("/credentials")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearCredentials() {
        model.setApiKey("");
        model.setPlannerApiKey("");
        model.setExecutorApiKey("");
        github.setToken("");
        rag.setEmbeddingApiKey("");
        rag.setRerankApiKey("");
    }

    private LocalConfigStatus states() {
        boolean modelReady = model.hasRoleApiKeys() && notBlank(model.getBaseUrl()) && notBlank(model.getModel());
        boolean githubReady = github.hasToken();
        boolean embeddingReady = notBlank(rag.getEmbeddingBaseUrl()) && notBlank(rag.getEmbeddingApiKey()) && notBlank(rag.getEmbeddingModel());
        boolean rerankReady = notBlank(rag.getRerankBaseUrl()) && notBlank(rag.getRerankApiKey()) && notBlank(rag.getRerankModel());
        boolean evidenceAvailable = rag.isEnabled() && embeddingReady && rerankReady;
        String reason = !modelReady ? "Model configuration is required" : !githubReady ? "GitHub token is required" : !evidenceAvailable ? "RAG is not configured" : "Ready";
        return new LocalConfigStatus(modelReady ? "READY" : "AUTH_REQUIRED", githubReady ? "READY" : "AUTH_REQUIRED",
                embeddingReady ? "READY" : "OPTIONAL_NOT_CONFIGURED", rerankReady ? "READY" : "OPTIONAL_NOT_CONFIGURED",
                "READY", evidenceAvailable ? "READY" : "DEGRADED", evidenceAvailable, evidenceAvailable, reason);
    }

    private static boolean notBlank(String value) { return value != null && !value.isBlank(); }
    private static String safeMessage(RuntimeException failure, String fallback) { return failure.getMessage() == null || failure.getMessage().isBlank() ? fallback : failure.getMessage().replaceAll("(?i)(bearer\\s+|api[_ -]?key[=: ]*)[^\\s,;]+", "$1[redacted]"); }

    public record LocalConfigStatus(String model, String github, String embedding, String rerank, String database,
                                    String mode, boolean evidenceAvailable, boolean publishAllowed, String reason) { }

    public record LocalConfigRequest(
            @NotBlank String provider,
            @NotBlank String modelBaseUrl,
            @NotBlank String modelName,
            @NotBlank String modelApiKey,
            @NotBlank String githubToken,
            String embeddingBaseUrl, String embeddingApiKey, String embeddingModel,
            String rerankBaseUrl, String rerankApiKey, String rerankModel) { }

    public record ModelTestRequest(@NotBlank String provider, @NotBlank String baseUrl,
                                   @NotBlank String model, @NotBlank String apiKey) { }
    public record GithubTestRequest(@NotBlank String baseUrl, @NotBlank String token) { }
    public record RagTestRequest(String embeddingBaseUrl, String embeddingApiKey, String embeddingModel,
                                 String rerankBaseUrl, String rerankApiKey, String rerankModel) { }
}
