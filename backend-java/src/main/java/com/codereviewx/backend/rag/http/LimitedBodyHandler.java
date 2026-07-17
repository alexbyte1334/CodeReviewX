package com.codereviewx.backend.rag.http;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public final class LimitedBodyHandler {

    public static final int DEFAULT_MAX_BYTES = 2 * 1024 * 1024;

    private LimitedBodyHandler() {
    }

    public static HttpResponse.BodyHandler<String> utf8(int maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("Response byte limit must be positive");
        }
        return responseInfo -> new LimitedUtf8Subscriber(
                maxBytes,
                responseInfo.headers().firstValueAsLong("Content-Length").orElse(-1L));
    }

    public static HttpResponse.BodyHandler<String> boundedSuccessOrDiscardError(int maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("Response byte limit must be positive");
        }
        return responseInfo -> responseInfo.statusCode() >= 200 && responseInfo.statusCode() < 300
                ? new LimitedUtf8Subscriber(
                        maxBytes,
                        responseInfo.headers().firstValueAsLong("Content-Length").orElse(-1L))
                : HttpResponse.BodySubscribers.replacing("");
    }

    public static boolean isResponseTooLarge(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof ResponseTooLargeException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static final class ResponseTooLargeException extends RuntimeException {

        private ResponseTooLargeException() {
            super("HTTP response exceeded byte limit");
        }
    }

    private static final class LimitedUtf8Subscriber implements HttpResponse.BodySubscriber<String> {

        private final int maxBytes;
        private final boolean contentLengthExceedsLimit;
        private final ByteArrayOutputStream body;
        private final CompletableFuture<String> completion = new CompletableFuture<>();

        private Flow.Subscription subscription;
        private int receivedBytes;
        private boolean done;

        private LimitedUtf8Subscriber(int maxBytes, long contentLength) {
            this.maxBytes = maxBytes;
            this.contentLengthExceedsLimit = contentLength > maxBytes;
            int initialCapacity = contentLength >= 0 && contentLength <= maxBytes
                    ? (int) contentLength
                    : Math.min(maxBytes, 8192);
            this.body = new ByteArrayOutputStream(initialCapacity);
        }

        @Override
        public CompletionStage<String> getBody() {
            return completion;
        }

        @Override
        public synchronized void onSubscribe(Flow.Subscription newSubscription) {
            Objects.requireNonNull(newSubscription, "subscription");
            if (subscription != null || done) {
                newSubscription.cancel();
                return;
            }
            subscription = newSubscription;
            if (contentLengthExceedsLimit) {
                failTooLarge();
                return;
            }
            subscription.request(1);
        }

        @Override
        public synchronized void onNext(List<ByteBuffer> buffers) {
            if (done) {
                return;
            }
            int batchBytes = 0;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > maxBytes - receivedBytes - batchBytes) {
                    failTooLarge();
                    return;
                }
                batchBytes += buffer.remaining();
            }
            for (ByteBuffer buffer : buffers) {
                ByteBuffer copy = buffer.asReadOnlyBuffer();
                byte[] bytes = new byte[copy.remaining()];
                copy.get(bytes);
                body.writeBytes(bytes);
            }
            receivedBytes += batchBytes;
            subscription.request(1);
        }

        @Override
        public synchronized void onError(Throwable throwable) {
            if (done) {
                return;
            }
            done = true;
            completion.completeExceptionally(throwable);
        }

        @Override
        public synchronized void onComplete() {
            if (done) {
                return;
            }
            done = true;
            completion.complete(body.toString(StandardCharsets.UTF_8));
        }

        private void failTooLarge() {
            done = true;
            subscription.cancel();
            completion.completeExceptionally(new ResponseTooLargeException());
        }
    }
}
