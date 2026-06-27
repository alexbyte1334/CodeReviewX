package com.codereviewx.backend.review.exception;

public class CommentPreviewNotFoundException extends RuntimeException {

    public CommentPreviewNotFoundException(Long previewId, Long runId) {
        super("Comment preview " + previewId + " was not found for review run " + runId);
    }
}
