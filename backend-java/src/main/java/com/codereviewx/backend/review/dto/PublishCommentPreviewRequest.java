package com.codereviewx.backend.review.dto;

import jakarta.validation.constraints.AssertTrue;

public class PublishCommentPreviewRequest {

    @AssertTrue(message = "confirmed must be true before publishing GitHub comments")
    private boolean confirmed;

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }
}
