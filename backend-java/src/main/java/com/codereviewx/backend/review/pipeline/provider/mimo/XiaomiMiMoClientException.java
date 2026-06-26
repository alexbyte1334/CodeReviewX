package com.codereviewx.backend.review.pipeline.provider.mimo;

public class XiaomiMiMoClientException extends RuntimeException {

    public XiaomiMiMoClientException(String message) {
        super(message);
    }

    public XiaomiMiMoClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
