package com.codereviewx.backend.review.dto;

import java.util.List;

public class ToolTraceListResponse {

    private List<ToolTraceItemResponse> items;

    public ToolTraceListResponse() {
    }

    public ToolTraceListResponse(List<ToolTraceItemResponse> items) {
        this.items = items;
    }

    public List<ToolTraceItemResponse> getItems() {
        return items;
    }

    public void setItems(List<ToolTraceItemResponse> items) {
        this.items = items;
    }
}
