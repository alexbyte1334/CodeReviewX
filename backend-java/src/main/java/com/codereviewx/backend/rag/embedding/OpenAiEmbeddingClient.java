package com.codereviewx.backend.rag.embedding;

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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(429, 502, 503, 504);

    private final RagProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Sleeper sleeper;
    private final URI endpoint;

    public OpenAiEmbeddingClient(RagProperties properties, ObjectMapper objectMapper) {
        this(properties, buildHttpClient(properties), objectMapper, Thread::sleep);
    }

    OpenAiEmbeddingClient(
            RagProperties properties,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Sleeper sleeper) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.endpoint = endpoint(properties.getEmbeddingBaseUrl(), "/embeddings", "Embedding endpoint is invalid");
    }

    @Override
    public List<float[]> embed(List<String> inputs) {
        Objects.requireNonNull(inputs, "inputs");
        if (inputs.isEmpty()) {
            return List.of();
        }

        List<float[]> embeddings = new ArrayList<>(inputs.size());
        int batchSize = properties.getEmbeddingBatchSize();
        for (int start = 0; start < inputs.size(); start += batchSize) {
            int end = Math.min(start + batchSize, inputs.size());
            embeddings.addAll(embedBatch(List.copyOf(inputs.subList(start, end))));
        }
        return List.copyOf(embeddings);
    }

    private List<float[]> embedBatch(List<String> batch) {
        String requestBody = writeRequest(new EmbeddingRequest(properties.getEmbeddingModel(), batch));
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Authorization", "Bearer " + properties.getEmbeddingApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = sendWithRetry(request);
        return parseResponse(response.body(), batch.size());
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
                throw new IllegalStateException("Embedding API returned HTTP " + status);
            }
            sleepBeforeRetry(attempt);
        }
        throw new IllegalStateException("Embedding request failed");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, LimitedBodyHandler.utf8(LimitedBodyHandler.DEFAULT_MAX_BYTES));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding request interrupted");
        } catch (IOException | RuntimeException exception) {
            if (LimitedBodyHandler.isResponseTooLarge(exception)) {
                throw new IllegalStateException("Embedding API response exceeded size limit");
            }
            throw new IllegalStateException("Embedding request failed");
        }
    }

    private void sleepBeforeRetry(int retryNumber) {
        long delayMillis = 100L << Math.min(retryNumber - 1, 10);
        try {
            sleeper.sleep(delayMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding retry interrupted");
        }
    }

    private List<float[]> parseResponse(String responseBody, int expectedCount) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Embedding response JSON is invalid");
        }
        JsonNode data = root == null ? null : root.get("data");
        if (data == null || !data.isArray() || data.size() != expectedCount) {
            throw new IllegalStateException("Embedding response count mismatch");
        }

        float[][] ordered = new float[expectedCount][];
        Set<Integer> indexes = new HashSet<>();
        for (JsonNode item : data) {
            JsonNode indexNode = item.get("index");
            if (indexNode == null || !indexNode.isIntegralNumber() || !indexNode.canConvertToInt()) {
                throw new IllegalStateException("Embedding response index is invalid");
            }
            int index = indexNode.intValue();
            if (index < 0 || index >= expectedCount || !indexes.add(index)) {
                throw new IllegalStateException("Embedding response index is invalid");
            }
            ordered[index] = parseVector(item.get("embedding"));
        }
        return List.of(ordered);
    }

    private float[] parseVector(JsonNode vectorNode) {
        if (vectorNode == null || !vectorNode.isArray()) {
            throw new IllegalStateException("Embedding vector is invalid");
        }
        if (vectorNode.size() != properties.getEmbeddingDimensions()) {
            throw new IllegalStateException("Embedding vector dimension mismatch");
        }
        float[] vector = new float[vectorNode.size()];
        for (int index = 0; index < vectorNode.size(); index++) {
            JsonNode valueNode = vectorNode.get(index);
            if (valueNode == null || !valueNode.isNumber()) {
                throw new IllegalStateException("Embedding vector value is invalid");
            }
            double value = valueNode.doubleValue();
            float floatValue = (float) value;
            if (!Double.isFinite(value) || !Float.isFinite(floatValue)) {
                throw new IllegalStateException("Embedding vector value is invalid");
            }
            vector[index] = floatValue;
        }
        return vector;
    }

    private String writeRequest(EmbeddingRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Embedding request encoding failed");
        }
    }

    private static HttpClient buildHttpClient(RagProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }

    private static URI endpoint(String baseUrl, String path, String message) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(message);
        }
        try {
            URI uri = URI.create(baseUrl.replaceAll("/+$", "") + path);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(message);
        }
    }

    @Override
    public String toString() {
        return "OpenAiEmbeddingClient{model='" + properties.getEmbeddingModel()
                + "', batchSize=" + properties.getEmbeddingBatchSize() + '}';
    }

    private record EmbeddingRequest(String model, List<String> input) {
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
