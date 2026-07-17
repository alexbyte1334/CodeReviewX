package com.codereviewx.backend.rag.http;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitedBodyHandlerTest {

    @Test
    void subscriberUsesBatchBackpressureAndCompletesWithinLimit() {
        HttpResponse.BodySubscriber<String> subscriber = LimitedBodyHandler.utf8(5).apply(responseInfo());
        RecordingSubscription subscription = new RecordingSubscription();

        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(buffer("he")));
        subscriber.onNext(List.of(buffer("llo")));
        subscriber.onComplete();

        assertThat(subscription.requested).isEqualTo(3);
        assertThat(subscription.cancelled).isFalse();
        assertThat(subscriber.getBody().toCompletableFuture()).isCompletedWithValue("hello");
    }

    @Test
    void subscriberCancelsImmediatelyWhenAggregatedBytesExceedLimit() {
        HttpResponse.BodySubscriber<String> subscriber = LimitedBodyHandler.utf8(5).apply(responseInfo());
        RecordingSubscription subscription = new RecordingSubscription();

        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(buffer("hello")));
        subscriber.onNext(List.of(buffer("!private-response")));

        assertThat(subscription.cancelled).isTrue();
        assertThat(subscription.requested).isEqualTo(2);
        assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().join())
                .hasRootCauseInstanceOf(LimitedBodyHandler.ResponseTooLargeException.class)
                .hasMessageNotContaining("private-response");

        subscriber.onComplete();
        subscriber.onError(new IllegalStateException("late-error"));
        assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().join())
                .hasRootCauseInstanceOf(LimitedBodyHandler.ResponseTooLargeException.class)
                .hasMessageNotContaining("late-error");
    }

    private static ByteBuffer buffer(String value) {
        return ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));
    }

    private static HttpResponse.ResponseInfo responseInfo() {
        return new HttpResponse.ResponseInfo() {
            @Override
            public int statusCode() {
                return 200;
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of(), (name, value) -> true);
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    private static final class RecordingSubscription implements Flow.Subscription {
        private long requested;
        private boolean cancelled;

        @Override
        public void request(long amount) {
            requested += amount;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
