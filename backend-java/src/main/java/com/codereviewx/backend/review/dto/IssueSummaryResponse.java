package com.codereviewx.backend.review.dto;

import com.codereviewx.backend.review.enums.RiskLevel;

public class IssueSummaryResponse {

    private int totalIssues;
    private int highCount;
    private int mediumCount;
    private int lowCount;
    private RiskLevel riskLevel;

    public IssueSummaryResponse() {
    }

    public IssueSummaryResponse(int totalIssues, int highCount, int mediumCount, int lowCount, RiskLevel riskLevel) {
        this.totalIssues = totalIssues;
        this.highCount = highCount;
        this.mediumCount = mediumCount;
        this.lowCount = lowCount;
        this.riskLevel = riskLevel;
    }

    public int getTotalIssues() {
        return totalIssues;
    }

    public void setTotalIssues(int totalIssues) {
        this.totalIssues = totalIssues;
    }

    public int getHighCount() {
        return highCount;
    }

    public void setHighCount(int highCount) {
        this.highCount = highCount;
    }

    public int getMediumCount() {
        return mediumCount;
    }

    public void setMediumCount(int mediumCount) {
        this.mediumCount = mediumCount;
    }

    public int getLowCount() {
        return lowCount;
    }

    public void setLowCount(int lowCount) {
        this.lowCount = lowCount;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }
}
