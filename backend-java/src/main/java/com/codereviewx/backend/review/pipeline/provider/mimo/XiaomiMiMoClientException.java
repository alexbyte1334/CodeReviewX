package com.codereviewx.backend.review.pipeline.provider.mimo;

public class XiaomiMiMoClientException extends RuntimeException {

    private final boolean retryable;

    public XiaomiMiMoClientException(String message) {
        this(message, null, false);
    }

    public XiaomiMiMoClientException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public XiaomiMiMoClientException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
