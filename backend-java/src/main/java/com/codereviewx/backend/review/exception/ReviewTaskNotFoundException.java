package com.codereviewx.backend.review.exception;

public class ReviewTaskNotFoundException extends RuntimeException {

    public ReviewTaskNotFoundException(Long id) {
        super("Review task not found: " + id);
    }
}
