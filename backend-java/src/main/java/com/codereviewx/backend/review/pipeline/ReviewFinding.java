package com.codereviewx.backend.review.pipeline;

import com.codereviewx.backend.review.enums.IssueCategory;
import com.codereviewx.backend.review.enums.IssueSeverity;
import com.codereviewx.backend.review.enums.IssueSource;
import com.codereviewx.backend.review.enums.IssueStatus;

/**
 * Internal normalized finding emitted by any review provider.
 * Not exposed through the public API; mapped to ReviewIssueEntity for persistence.
 */
public class ReviewFinding {

    private final String issueKey;
    private final IssueSeverity severity;
    private final IssueCategory category;
    private final IssueSource source;
    private final IssueStatus status;
    private final String filePath;
    private final Integer startLine;
    private final Integer endLine;
    private final String title;
    private final String description;
    private final String recommendation;

    public ReviewFinding(String issueKey,
                         IssueSeverity severity,
                         IssueCategory category,
                         IssueSource source,
                         IssueStatus status,
                         String filePath,
                         Integer startLine,
                         Integer endLine,
                         String title,
                         String description,
                         String recommendation) {
        this.issueKey = issueKey;
        this.severity = severity;
        this.category = category;
        this.source = source;
        this.status = status;
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
        this.title = title;
        this.description = description;
        this.recommendation = recommendation;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public IssueSeverity getSeverity() {
        return severity;
    }

    public IssueCategory getCategory() {
        return category;
    }

    public IssueSource getSource() {
        return source;
    }

    public IssueStatus getStatus() {
        return status;
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getStartLine() {
        return startLine;
    }

    public Integer getEndLine() {
        return endLine;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getRecommendation() {
        return recommendation;
    }
}
