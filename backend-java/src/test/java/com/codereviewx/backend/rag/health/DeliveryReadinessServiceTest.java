package com.codereviewx.backend.rag.health;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.review.github.GithubProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.sql.DataSource;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.util.List;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeliveryReadinessServiceTest {
    @Test
    void acceptsOnlyAuthenticatedCompleteModelResponsesAndUsesRealRequestShapes() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(), any())).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            String requestBody = body(request);
            if (request.uri().getPath().endsWith("rerank")) {
                assertThat(new ObjectMapper().readTree(requestBody).path("documents").get(0).path("id").asText()).isEqualTo("health");
                assertThat(new ObjectMapper().readTree(requestBody).path("documents").get(0).path("text").asText()).isEqualTo("health");
            }
            String body = request.uri().getPath().endsWith("embeddings")
                    ? "{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2]}]}"
                    : "{\"results\":[{\"index\":0,\"relevance_score\":0.8}]}";
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(body);
            return response;
        });
        RagProperties rag = properties(); rag.setEmbeddingDimensions(2);
        DataSource source = dataSource();
        DeliveryReadinessService service = new DeliveryReadinessService.Default(source, github(), rag, http, new ObjectMapper());

        assertThat(service.snapshot()).extracting(DeliveryReadinessService.Snapshot::embedding,
                DeliveryReadinessService.Snapshot::rerank).containsExactly(true, true);
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, atLeast(2)).send(requests.capture(), any());
        assertThat(requests.getAllValues().stream().filter(request -> "POST".equals(request.method())).toList())
                .allSatisfy(request -> assertThat(request.headers().firstValue("Authorization")).hasValue("Bearer secret"));
        assertThat(requests.getAllValues()).anyMatch(request -> request.uri().getPath().endsWith("/embeddings"));
        assertThat(requests.getAllValues()).anyMatch(request -> request.uri().getPath().endsWith("/rerank"));
    }

    @Test
    void rejectsMalformedProviderResponsesAndAuthFailures() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(), any())).thenAnswer(invocation -> {
            HttpResponse<String> response = mock(HttpResponse.class);
            HttpRequest request = invocation.getArgument(0);
            when(response.statusCode()).thenReturn(request.uri().getPath().endsWith("embeddings") ? 200 : 401);
            when(response.body()).thenReturn("{\"data\":[{\"index\":0,\"embedding\":[\"NaN\"]}]} ");
            return response;
        });
        RagProperties rag = properties(); rag.setEmbeddingDimensions(2);
        DeliveryReadinessService.Snapshot snapshot = new DeliveryReadinessService.Default(dataSource(), github(), rag, http, new ObjectMapper()).snapshot();
        assertThat(snapshot.embedding()).isFalse();
        assertThat(snapshot.rerank()).isFalse();
    }

    @Test
    void cachesOneProviderProbePerHealthCycleAndRefreshesAfterExpiry() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(), any())).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0); HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(request.uri().getPath().endsWith("embeddings")
                    ? "{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2]}]}"
                    : "{\"results\":[{\"index\":0,\"relevance_score\":0.8}]}"); return response;
        });
        RagProperties rag = properties(); rag.setEmbeddingDimensions(2);
        DeliveryReadinessService service = new DeliveryReadinessService.Default(dataSource(), github(), rag, http, new ObjectMapper(), 5);
        service.snapshot(); service.snapshot(); service.snapshot(); service.snapshot();
        verify(http, times(2)).send(any(), any());
        Thread.sleep(10); service.snapshot();
        verify(http, times(4)).send(any(), any());
    }

    private static RagProperties properties() {
        RagProperties value = new RagProperties(); value.setEnabled(true);
        value.setEmbeddingBaseUrl("https://embedding.test/v1"); value.setEmbeddingApiKey("secret");
        value.setRerankBaseUrl("https://rerank.test/v1"); value.setRerankApiKey("secret"); return value;
    }
    private static GithubProperties github() { GithubProperties value = new GithubProperties(); value.setApiBaseUrl("https://github.test"); return value; }
    private static DataSource dataSource() throws Exception { DataSource source = mock(DataSource.class); Connection connection = mock(Connection.class); when(source.getConnection()).thenReturn(connection); when(connection.isValid(anyInt())).thenReturn(true); return source; }
    private static String body(HttpRequest request) throws Exception {
        if (request.bodyPublisher().isEmpty()) return "";
        CompletableFuture<String> result = new CompletableFuture<>();
        request.bodyPublisher().get().subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer item) { result.complete(StandardCharsets.UTF_8.decode(item).toString()); }
            public void onError(Throwable throwable) { result.completeExceptionally(throwable); }
            public void onComplete() { }
        });
        return result.get();
    }
}
