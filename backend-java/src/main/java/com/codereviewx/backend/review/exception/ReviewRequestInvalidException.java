package com.codereviewx.backend.review.exception;

public class ReviewRequestInvalidException extends RuntimeException {

    public ReviewRequestInvalidException(String message) {
        super(message);
    }
}
