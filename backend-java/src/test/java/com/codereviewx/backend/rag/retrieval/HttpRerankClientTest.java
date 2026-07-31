package com.codereviewx.backend.rag.retrieval;

import com.codereviewx.backend.rag.config.RagProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpRerankClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int OVERSIZED_RESPONSE_PADDING_BYTES = 2 * 1024 * 1024;
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void rerankReturnsEmptyWithoutMakingRequest() {
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 500, "{}");
        });

        assertThat(client(properties(0), ignored -> { }).rerank("private query", List.of())).isEmpty();
        assertThat(attempts).hasValue(0);
    }

    @Test
    void rerankSendsOnlyStableFieldsAndSortsByScoreWithOriginalOrderTieBreak() {
        List<JsonNode> requests = new ArrayList<>();
        List<String> authorizations = new ArrayList<>();
        startServer(exchange -> {
            requests.add(MAPPER.readTree(exchange.getRequestBody()));
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {"results":[
                      {"index":2,"relevance_score":0.4},
                      {"index":1,"relevance_score":0.9},
                      {"index":0,"relevance_score":0.9}
                    ]}
                    """);
        });
        List<RerankCandidate> candidates = List.of(
                new RerankCandidate("chunk-a", "text-a"),
                new RerankCandidate("chunk-b", "text-b"),
                new RerankCandidate("chunk-c", "text-c"));

        List<RerankedChunk> result = client(properties(0), ignored -> { }).rerank("private query", candidates);

        assertThat(result).extracting(chunk -> chunk.candidate().chunkId())
                .containsExactly("chunk-a", "chunk-b", "chunk-c");
        assertThat(result).extracting(RerankedChunk::score).containsExactly(0.9, 0.9, 0.4);
        assertThat(result.get(0).candidate()).isSameAs(candidates.get(0));
        JsonNode request = requests.get(0);
        assertThat(request.fieldNames()).toIterable().containsExactlyInAnyOrder("model", "query", "documents");
        assertThat(request.path("model").asText()).isEqualTo("BAAI/bge-reranker-v2-m3");
        assertThat(request.path("query").asText()).isEqualTo("private query");
        assertThat(request.path("documents")).hasSize(3);
        assertThat(request.path("documents").get(0).asText()).isEqualTo("text-a");
        assertThat(authorizations).containsExactly("Bearer rerank-secret");
        assertThat(lastPath).isEqualTo("/rerank");
    }

    @Test
    void rerankRejectsCountDuplicateIndexOutOfRangeAndInvalidScore() {
        assertInvalidResponse("{\"results\":[{\"index\":0,\"relevance_score\":1}]}", "count", 2);
        assertInvalidResponse("{\"results\":[{\"index\":0,\"relevance_score\":1},{\"index\":0,\"relevance_score\":2}]}", "index", 2);
        assertInvalidResponse("{\"results\":[{\"index\":2,\"relevance_score\":1},{\"index\":0,\"relevance_score\":2}]}", "index", 2);
        assertInvalidResponse("{\"results\":[{\"index\":0,\"relevance_score\":null}]}", "score", 1);
    }

    @Test
    void rerankRejectsFractionalIndexInsteadOfTruncatingIt() {
        assertInvalidResponse(
                "{\"results\":[{\"index\":0.5,\"relevance_score\":1}]}",
                "index",
                1);
    }

    @Test
    void rerankRejectsOversizedValidResponseWithoutLeakingQueryCandidateOrKey() {
        String oversizedResponse = "{\"results\":[{\"index\":0,\"relevance_score\":1}],\"padding\":\""
                + "x".repeat(OVERSIZED_RESPONSE_PADDING_BYTES) + "\"}";
        startServer(exchange -> respond(exchange, 200, oversizedResponse));
        HttpRerankClient client = client(properties(0), ignored -> { });

        assertThatThrownBy(() -> client.rerank("private oversized query", candidates(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("size limit")
                .hasMessageNotContaining("rerank-secret")
                .hasMessageNotContaining("private oversized query")
                .hasMessageNotContaining("candidate-text");
    }

    @Test
    void rerankRetriesRetryableStatusesWithExponentialBackoff() {
        AtomicInteger attempts = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        startServer(exchange -> {
            int attempt = attempts.getAndIncrement();
            if (attempt == 0) {
                respond(exchange, 502, "private body");
            } else if (attempt == 1) {
                respond(exchange, 504, "private body");
            } else {
                respond(exchange, 200, "{\"results\":[{\"index\":0,\"relevance_score\":0.5}]}");
            }
        });
        RagProperties properties = properties(2);

        assertThat(client(properties, delays::add).rerank("private query", candidates(1))).hasSize(1);
        assertThat(attempts).hasValue(3);
        assertThat(delays).containsExactly(100L, 200L);
    }

    @Test
    void rerankRetriesOversizedRetryableResponseThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        startServer(exchange -> {
            if (attempts.getAndIncrement() == 0) {
                respond(exchange, 502, "private-error-" + "x".repeat(OVERSIZED_RESPONSE_PADDING_BYTES));
            } else {
                respond(exchange, 200, "{\"results\":[{\"index\":0,\"relevance_score\":0.8}]}");
            }
        });

        List<RerankedChunk> result = client(properties(1), delays::add)
                .rerank("private query", candidates(1));

        assertThat(result).extracting(RerankedChunk::score).containsExactly(0.8);
        assertThat(attempts).hasValue(2);
        assertThat(delays).containsExactly(100L);
    }

    @Test
    void rerankDoesNotRetryAuthenticationFailureAndRedactsSecrets() {
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 403, "private query candidate-text rerank-secret");
        });
        HttpRerankClient client = client(properties(3), ignored -> { });

        assertThatThrownBy(() -> client.rerank("private query", candidates(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 403")
                .hasMessageNotContaining("rerank-secret")
                .hasMessageNotContaining("private query")
                .hasMessageNotContaining("candidate-text");
        assertThat(attempts).hasValue(1);
        assertThat(client.toString()).doesNotContain("rerank-secret").doesNotContain(serverBaseUrl());
    }

    @Test
    void rerankTimesOutWithSafeException() {
        startServer(exchange -> {
            try {
                Thread.sleep(1_200);
                respond(exchange, 200, "{\"results\":[{\"index\":0,\"relevance_score\":1}]}");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        RagProperties properties = properties(0);
        properties.setTimeoutSeconds(1);

        assertThatThrownBy(() -> client(properties, ignored -> { })
                .rerank("private timeout query", candidates(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed")
                .hasMessageNotContaining("rerank-secret")
                .hasMessageNotContaining("private timeout query");
    }

    private void assertInvalidResponse(String response, String expectedMessage, int candidateCount) {
        stopServer();
        startServer(exchange -> respond(exchange, 200, response));
        assertThatThrownBy(() -> client(properties(0), ignored -> { })
                .rerank("private query", candidates(candidateCount)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(expectedMessage)
                .hasMessageNotContaining(response);
    }

    private List<RerankCandidate> candidates(int count) {
        List<RerankCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            candidates.add(new RerankCandidate("chunk-" + index, "candidate-text-" + index));
        }
        return candidates;
    }

    private HttpRerankClient client(RagProperties properties, HttpRerankClient.Sleeper sleeper) {
        return new HttpRerankClient(
                properties,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds())).build(),
                MAPPER,
                sleeper);
    }

    private RagProperties properties(int retries) {
        RagProperties properties = new RagProperties();
        properties.setRerankBaseUrl(serverBaseUrl() + "/");
        properties.setRerankApiKey("rerank-secret");
        properties.setMaxRetries(retries);
        return properties;
    }

    private String lastPath;

    private void startServer(ExchangeHandler handler) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                lastPath = exchange.getRequestURI().getPath();
                handler.handle(exchange);
            });
            server.start();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String serverBaseUrl() {
        return server == null ? "http://127.0.0.1:1" : "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
