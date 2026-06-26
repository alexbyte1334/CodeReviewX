package com.codereviewx.backend.review.pipeline.provider.mimo;

import java.util.List;

public class GateDecision {

    private Boolean approved;
    private String reason;
    private List<String> requiredChanges;

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<String> getRequiredChanges() {
        return requiredChanges;
    }

    public void setRequiredChanges(List<String> requiredChanges) {
        this.requiredChanges = requiredChanges;
    }
}
