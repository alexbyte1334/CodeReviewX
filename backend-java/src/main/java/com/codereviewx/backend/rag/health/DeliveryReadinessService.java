package com.codereviewx.backend.rag.health;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.review.github.GithubProperties;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@FunctionalInterface
public interface DeliveryReadinessService {

    Snapshot snapshot();

    record Snapshot(boolean database, boolean github, boolean embedding, boolean rerank) {
        public boolean ragReady() {
            return database && github && embedding && rerank;
        }
    }

    @Service
    class Default implements DeliveryReadinessService {
        private final DataSource dataSource;
        private final GithubProperties github;
        private final RagProperties rag;
        private final HttpClient client;
        private final ObjectMapper mapper;
        private final long cacheMillis;
        private volatile Snapshot cached;
        private volatile long cachedAt;

        @Autowired
        public Default(DataSource dataSource, GithubProperties github, RagProperties rag) {
            this(dataSource, github, rag, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), new ObjectMapper(), 30_000);
        }

        Default(DataSource dataSource, GithubProperties github, RagProperties rag, HttpClient client) {
            this(dataSource, github, rag, client, new ObjectMapper(), 30_000);
        }

        Default(DataSource dataSource, GithubProperties github, RagProperties rag, HttpClient client, ObjectMapper mapper) {
            this(dataSource, github, rag, client, mapper, 30_000);
        }

        Default(DataSource dataSource, GithubProperties github, RagProperties rag, HttpClient client, ObjectMapper mapper, long cacheMillis) {
            this.dataSource = dataSource;
            this.github = github;
            this.rag = rag;
            this.client = client;
            this.mapper = mapper;
            this.cacheMillis = cacheMillis;
        }

        @Override
        public synchronized Snapshot snapshot() {
            long now = System.currentTimeMillis();
            if (cached != null && now - cachedAt < cacheMillis) return cached;
            boolean database = databaseReady();
            boolean githubReady = github.hasToken() && endpointReady(github.getApiBaseUrl(), github.getToken(), Probe.GITHUB);
            boolean embedding = rag.isEnabled() && endpointReady(rag.getEmbeddingBaseUrl(), rag.getEmbeddingApiKey(), Probe.EMBEDDING);
            boolean rerank = rag.isEnabled() && endpointReady(rag.getRerankBaseUrl(), rag.getRerankApiKey(), Probe.RERANK);
            cached = new Snapshot(database, githubReady, embedding, rerank);
            cachedAt = now;
            return cached;
        }

        private boolean databaseReady() {
            try (var connection = dataSource.getConnection()) {
                return connection.isValid(2);
            } catch (Exception ignored) {
                return false;
            }
        }

        private boolean endpointReady(String baseUrl, String apiKey, Probe probe) {
            if (baseUrl == null || baseUrl.isBlank()) {
                return false;
            }
            if (apiKey != null && apiKey.isBlank()) {
                return false;
            }
            try {
                URI endpoint = URI.create(baseUrl.replaceAll("/+$", "") + probe.path);
                HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3));
                if (apiKey != null) builder.header("Authorization", "Bearer " + apiKey);
                if (probe == Probe.GITHUB) builder.header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28");
                if (probe != Probe.GITHUB) {
                    String body = probe == Probe.EMBEDDING
                            ? "{\"model\":\"" + rag.getEmbeddingModel() + "\",\"input\":[\"health\"]}"
                            : "{\"model\":\"" + rag.getRerankModel() + "\",\"query\":\"health\",\"documents\":[{\"id\":\"health\",\"text\":\"health\"}]}";
                    builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
                } else builder.GET();
                HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) return false;
                JsonNode root = mapper.readTree(response.body());
                if (probe == Probe.GITHUB) return root.isObject() && !root.path("login").asText("").isBlank();
                if (probe == Probe.EMBEDDING) {
                    JsonNode data = root.path("data");
                    JsonNode vector = data.size() == 1 ? data.get(0).path("embedding") : null;
                    if (vector == null || !vector.isArray() || vector.size() != rag.getEmbeddingDimensions()) return false;
                    for (JsonNode value : vector) if (!value.isNumber() || !Double.isFinite(value.doubleValue())) return false;
                    return data.get(0).path("index").asInt(-1) == 0;
                }
                JsonNode results = root.path("results");
                if (!results.isArray() || results.size() != 1) return false;
                JsonNode row = results.get(0);
                return row.path("index").asInt(-1) == 0 && row.path("relevance_score").isNumber()
                        && Double.isFinite(row.path("relevance_score").doubleValue());
            } catch (Exception ignored) {
                return false;
            }
        }
        private enum Probe { GITHUB("/user"), EMBEDDING("/embeddings"), RERANK("/rerank"); final String path; Probe(String path) { this.path = path; } }
    }
}
