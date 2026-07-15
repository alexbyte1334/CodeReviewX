package com.codereviewx.backend.rag.embedding;

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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiEmbeddingClientTest {

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
    void embed_returnsEmptyWithoutMakingRequest() {
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 500, "{}");
        });

        OpenAiEmbeddingClient client = client(properties(32, 0), ignored -> { });

        assertThat(client.embed(List.of())).isEmpty();
        assertThat(attempts).hasValue(0);
    }

    @Test
    void embed_batchesRequestsAndRestoresResponseIndexOrder() {
        List<JsonNode> requests = new CopyOnWriteArrayList<>();
        List<String> authorizations = new CopyOnWriteArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            requests.add(MAPPER.readTree(exchange.getRequestBody()));
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            int attempt = attempts.getAndIncrement();
            String body = attempt == 0
                    ? embeddingResponse(List.of(item(1, 20), item(0, 10)))
                    : embeddingResponse(List.of(item(0, 30)));
            respond(exchange, 200, body);
        });

        OpenAiEmbeddingClient client = client(properties(2, 0), ignored -> { });

        List<float[]> result = client.embed(List.of("first", "second", "third"));

        assertThat(result).hasSize(3);
        assertThat(result).extracting(vector -> vector[0]).containsExactly(10.0f, 20.0f, 30.0f);
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("model", "input");
        assertThat(requests.get(0).path("model").asText()).isEqualTo("BAAI/bge-m3");
        assertThat(requests.get(0).path("input")).extracting(JsonNode::asText)
                .containsExactly("first", "second");
        assertThat(requests.get(1).path("input")).extracting(JsonNode::asText).containsExactly("third");
        assertThat(authorizations).containsOnly("Bearer embedding-secret");
        assertThat(serverPath()).isEqualTo("/embeddings");
    }

    @Test
    void embedRejectsWrongCountDuplicateIndexOutOfRangeAndWrongDimension() {
        assertInvalidResponse(embeddingResponse(List.of(item(0, 1))), "count", List.of("a", "b"));
        assertInvalidResponse(embeddingResponse(List.of(item(0, 1), item(0, 2))), "index", List.of("a", "b"));
        assertInvalidResponse(embeddingResponse(List.of(item(2, 1), item(0, 2))), "index", List.of("a", "b"));
        assertInvalidResponse("{\"data\":[{\"index\":0,\"embedding\":[1.0]}]}", "dimension", List.of("a"));
    }

    @Test
    void embedRejectsFractionalIndexInsteadOfTruncatingIt() {
        String fractionalIndexItem = item(0, 1).replace("\"index\":0", "\"index\":0.5");

        assertInvalidResponse(embeddingResponse(List.of(fractionalIndexItem)), "index", List.of("a"));
    }

    @Test
    void embedRejectsNonNumericVectorValueWithoutEchoingResponse() {
        startServer(exchange -> respond(exchange, 200,
                "{\"data\":[{\"index\":0,\"embedding\":[null]}]}"));
        OpenAiEmbeddingClient client = client(properties(1, 0), ignored -> { });

        assertThatThrownBy(() -> client.embed(List.of("private input")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vector")
                .hasMessageNotContaining("private input")
                .hasMessageNotContaining("null");
    }

    @Test
    void embedRejectsOversizedValidResponseWithoutLeakingInputOrKey() {
        String oversizedResponse = "{\"data\":[" + item(0, 1) + "],\"padding\":\""
                + "x".repeat(OVERSIZED_RESPONSE_PADDING_BYTES) + "\"}";
        startServer(exchange -> respond(exchange, 200, oversizedResponse));
        OpenAiEmbeddingClient client = client(properties(1, 0), ignored -> { });

        assertThatThrownBy(() -> client.embed(List.of("private oversized input")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("size limit")
                .hasMessageNotContaining("embedding-secret")
                .hasMessageNotContaining("private oversized input");
    }

    @Test
    void embedRetriesOnlyRetryableStatusesWithExponentialBackoff() {
        AtomicInteger attempts = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        startServer(exchange -> {
            int attempt = attempts.getAndIncrement();
            if (attempt == 0) {
                respond(exchange, 429, "rate limited private body");
            } else if (attempt == 1) {
                respond(exchange, 503, "temporarily unavailable private body");
            } else {
                respond(exchange, 200, embeddingResponse(List.of(item(0, 7))));
            }
        });

        OpenAiEmbeddingClient client = client(properties(1, 2), delays::add);

        assertThat(client.embed(List.of("private input")).get(0)[0]).isEqualTo(7.0f);
        assertThat(attempts).hasValue(3);
        assertThat(delays).containsExactly(100L, 200L);
    }

    @Test
    void embedRetriesOversizedRetryableResponseThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        startServer(exchange -> {
            if (attempts.getAndIncrement() == 0) {
                respond(exchange, 503, "private-error-" + "x".repeat(OVERSIZED_RESPONSE_PADDING_BYTES));
            } else {
                respond(exchange, 200, embeddingResponse(List.of(item(0, 9))));
            }
        });

        OpenAiEmbeddingClient client = client(properties(1, 1), delays::add);

        assertThat(client.embed(List.of("private input")).get(0)[0]).isEqualTo(9.0f);
        assertThat(attempts).hasValue(2);
        assertThat(delays).containsExactly(100L);
    }

    @Test
    void embedDoesNotRetryOversizedAuthenticationFailureAndRedactsSecrets() {
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 401, "private input embedding-secret "
                    + "x".repeat(OVERSIZED_RESPONSE_PADDING_BYTES));
        });
        OpenAiEmbeddingClient client = client(properties(1, 3), ignored -> { });

        assertThatThrownBy(() -> client.embed(List.of("private input")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 401")
                .hasMessageNotContaining("embedding-secret")
                .hasMessageNotContaining("private input");
        assertThat(attempts).hasValue(1);
        assertThat(client.toString()).doesNotContain("embedding-secret").doesNotContain(serverBaseUrl());
    }

    @Test
    void embedTimesOutWithSafeException() {
        startServer(exchange -> {
            try {
                Thread.sleep(1_200);
                respond(exchange, 200, embeddingResponse(List.of(item(0, 1))));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        RagProperties properties = properties(1, 0);
        properties.setTimeoutSeconds(1);
        OpenAiEmbeddingClient client = client(properties, ignored -> { });

        assertThatThrownBy(() -> client.embed(List.of("private timeout input")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed")
                .hasMessageNotContaining("embedding-secret")
                .hasMessageNotContaining("private timeout input");
    }

    private void assertInvalidResponse(String response, String message, List<String> inputs) {
        stopServer();
        startServer(exchange -> respond(exchange, 200, response));
        OpenAiEmbeddingClient client = client(properties(10, 0), ignored -> { });
        assertThatThrownBy(() -> client.embed(inputs))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(message)
                .hasMessageNotContaining(response);
    }

    private OpenAiEmbeddingClient client(RagProperties properties, OpenAiEmbeddingClient.Sleeper sleeper) {
        return new OpenAiEmbeddingClient(
                properties,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds())).build(),
                MAPPER,
                sleeper);
    }

    private RagProperties properties(int batchSize, int retries) {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingBaseUrl(serverBaseUrl() + "/");
        properties.setEmbeddingApiKey("embedding-secret");
        properties.setEmbeddingBatchSize(batchSize);
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

    private String serverPath() {
        return lastPath;
    }

    private static String item(int index, int marker) {
        StringBuilder vector = new StringBuilder("[").append(marker);
        for (int i = 1; i < 1024; i++) {
            vector.append(",0");
        }
        return "{\"index\":" + index + ",\"embedding\":" + vector.append(']') + "}";
    }

    private static String embeddingResponse(List<String> items) {
        return "{\"data\":[" + String.join(",", items) + "]}";
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
