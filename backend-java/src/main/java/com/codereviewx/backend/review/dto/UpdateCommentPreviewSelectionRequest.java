package com.codereviewx.backend.review.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class UpdateCommentPreviewSelectionRequest {

    @NotNull
    private List<Long> selectedPreviewIds;

    public List<Long> getSelectedPreviewIds() {
        return selectedPreviewIds;
    }

    public void setSelectedPreviewIds(List<Long> selectedPreviewIds) {
        this.selectedPreviewIds = selectedPreviewIds;
    }
}
