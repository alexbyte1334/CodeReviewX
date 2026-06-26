package com.codereviewx.backend.review.pipeline.provider.mimo;

import java.util.List;

public class CandidateReview {

    private String summary;
    private List<CandidateFinding> findings;

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<CandidateFinding> getFindings() {
        return findings;
    }

    public void setFindings(List<CandidateFinding> findings) {
        this.findings = findings;
    }

    public static class CandidateFinding {

        private String severity;
        private String category;
        private String filePath;
        private Integer startLine;
        private Integer endLine;
        private String title;
        private String description;
        private String recommendation;

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public Integer getStartLine() {
            return startLine;
        }

        public void setStartLine(Integer startLine) {
            this.startLine = startLine;
        }

        public Integer getEndLine() {
            return endLine;
        }

        public void setEndLine(Integer endLine) {
            this.endLine = endLine;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getRecommendation() {
            return recommendation;
        }

        public void setRecommendation(String recommendation) {
            this.recommendation = recommendation;
        }
    }
}
