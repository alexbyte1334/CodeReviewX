package com.codereviewx.backend.review.dto;

import java.util.List;

public class CommentPreviewListResponse {

    private List<CommentPreviewItemResponse> items;

    public CommentPreviewListResponse() {
    }

    public CommentPreviewListResponse(List<CommentPreviewItemResponse> items) {
        this.items = items;
    }

    public List<CommentPreviewItemResponse> getItems() {
        return items;
    }

    public void setItems(List<CommentPreviewItemResponse> items) {
        this.items = items;
    }
}
