package com.codereviewx.backend.review.pipeline.provider.mimo;

public class MiMoAgentException extends RuntimeException {

    private final String errorCode;

    public MiMoAgentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MiMoAgentException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
