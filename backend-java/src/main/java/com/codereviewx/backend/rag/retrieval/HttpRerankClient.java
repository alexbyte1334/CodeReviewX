package com.codereviewx.backend.rag.retrieval;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.http.LimitedBodyHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.codereviewx.backend.rag.service.RagMetricsService;

public class HttpRerankClient implements RerankClient {

    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(429, 502, 503, 504);

    private final RagProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Sleeper sleeper;
    private final URI endpoint;
    private final RagMetricsService metrics;

    public HttpRerankClient(RagProperties properties, ObjectMapper objectMapper) {
        this(properties, buildHttpClient(properties), objectMapper, Thread::sleep, null);
    }
    public HttpRerankClient(RagProperties properties, ObjectMapper objectMapper, RagMetricsService metrics) {
        this(properties, buildHttpClient(properties), objectMapper, Thread::sleep, metrics);
    }

    HttpRerankClient(
            RagProperties properties,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Sleeper sleeper) { this(properties, httpClient, objectMapper, sleeper, null); }
    HttpRerankClient(RagProperties properties, HttpClient httpClient, ObjectMapper objectMapper,
                     Sleeper sleeper, RagMetricsService metrics) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.endpoint = endpoint(properties.getRerankBaseUrl());
        this.metrics = metrics;
    }

    @Override
    public List<RerankedChunk> rerank(String query, List<RerankCandidate> candidates) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.isEmpty()) {
            return List.of();
        }

        if (metrics != null) metrics.recordRerankRequest();
        List<RerankDocument> requestCandidates = candidates.stream()
                .map(candidate -> new RerankDocument(candidate.chunkId(), com.codereviewx.backend.rag.security.RagSecurityPolicy.redactOutbound(candidate.text())))
                .toList();
        String requestBody = writeRequest(new RerankRequest(properties.getRerankModel(), com.codereviewx.backend.rag.security.RagSecurityPolicy.redactOutbound(query), requestCandidates));
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Authorization", "Bearer " + properties.getRerankApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = sendWithRetry(request);
        return parseResponse(response.body(), candidates);
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request) {
        int maxAttempts = 1 + properties.getMaxRetries();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpResponse<String> response = send(request);
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return response;
            }
            if (!RETRYABLE_STATUSES.contains(status) || attempt == maxAttempts) {
                throw new IllegalStateException("Rerank API returned HTTP " + status);
            }
            sleepBeforeRetry(attempt);
        }
        throw new IllegalStateException("Rerank request failed");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(
                    request,
                    LimitedBodyHandler.boundedSuccessOrDiscardError(LimitedBodyHandler.DEFAULT_MAX_BYTES));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Rerank request interrupted");
        } catch (IOException | RuntimeException exception) {
            if (LimitedBodyHandler.isResponseTooLarge(exception)) {
                throw new IllegalStateException("Rerank API response exceeded size limit");
            }
            throw new IllegalStateException("Rerank request failed");
        }
    }

    private void sleepBeforeRetry(int retryNumber) {
        long delayMillis = 100L << Math.min(retryNumber - 1, 10);
        try {
            sleeper.sleep(delayMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Rerank retry interrupted");
        }
    }

    private List<RerankedChunk> parseResponse(String responseBody, List<RerankCandidate> candidates) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Rerank response JSON is invalid");
        }
        JsonNode results = root == null ? null : root.get("results");
        if (results == null || !results.isArray() || results.size() != candidates.size()) {
            throw new IllegalStateException("Rerank response count mismatch");
        }

        Set<Integer> indexes = new HashSet<>();
        List<IndexedChunk> chunks = new ArrayList<>(results.size());
        for (JsonNode result : results) {
            JsonNode indexNode = result.get("index");
            if (indexNode == null || !indexNode.isIntegralNumber() || !indexNode.canConvertToInt()) {
                throw new IllegalStateException("Rerank response index is invalid");
            }
            int index = indexNode.intValue();
            if (index < 0 || index >= candidates.size() || !indexes.add(index)) {
                throw new IllegalStateException("Rerank response index is invalid");
            }
            JsonNode scoreNode = result.get("relevance_score");
            if (scoreNode == null || !scoreNode.isNumber() || !Double.isFinite(scoreNode.doubleValue())) {
                throw new IllegalStateException("Rerank response score is invalid");
            }
            chunks.add(new IndexedChunk(index, new RerankedChunk(candidates.get(index), scoreNode.doubleValue())));
        }
        chunks.sort(Comparator
                .comparingDouble((IndexedChunk result) -> result.chunk().score()).reversed()
                .thenComparingInt(IndexedChunk::originalIndex));
        return chunks.stream().map(IndexedChunk::chunk).toList();
    }

    private String writeRequest(RerankRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Rerank request encoding failed");
        }
    }

    private static HttpClient buildHttpClient(RagProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }

    private static URI endpoint(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Rerank endpoint is invalid");
        }
        try {
            URI uri = URI.create(baseUrl.replaceAll("/+$", "") + "/rerank");
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Rerank endpoint is invalid");
        }
    }

    @Override
    public String toString() {
        return "HttpRerankClient{model='" + properties.getRerankModel() + "'}";
    }

    private record RerankRequest(String model, String query, List<RerankDocument> candidates) {
    }

    private record RerankDocument(String id, String text) {
    }

    private record IndexedChunk(int originalIndex, RerankedChunk chunk) {
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
