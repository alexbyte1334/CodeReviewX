package com.codereviewx.backend.review.pipeline.provider.mimo;

import com.codereviewx.backend.review.ReviewErrorCodes;
import com.codereviewx.backend.review.enums.IssueCategory;
import com.codereviewx.backend.review.enums.IssueSeverity;
import com.codereviewx.backend.review.enums.IssueSource;
import com.codereviewx.backend.review.enums.IssueStatus;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MiMoIssueGenerator {

    public List<ReviewFinding> generate(CandidateReview candidateReview) {
        List<ReviewFinding> findings = new ArrayList<>();
        int sequence = 1;
        for (CandidateReview.CandidateFinding candidate : candidateReview.getFindings()) {
            findings.add(toReviewFinding(candidate, sequence));
            sequence++;
        }
        return findings;
    }

    private ReviewFinding toReviewFinding(CandidateReview.CandidateFinding candidate, int sequence) {
        IssueSeverity severity = parseSeverity(candidate.getSeverity());
        IssueCategory category = parseCategory(candidate.getCategory());
        String title = requireText(candidate.getTitle(), "Finding title is missing");
        String description = requireText(candidate.getDescription(), "Finding description is missing");
        String recommendation = requireText(candidate.getRecommendation(), "Finding recommendation is missing");
        String filePath = normalizeFilePath(candidate.getFilePath());
        Integer startLine = normalizeLine(candidate.getStartLine());
        Integer endLine = normalizeLine(candidate.getEndLine());

        if (endLine < startLine) {
            endLine = startLine;
        }

        return new ReviewFinding(
                "MIMO-ISSUE-" + sequence,
                severity,
                category,
                IssueSource.MIMO,
                IssueStatus.OPEN,
                filePath,
                startLine,
                endLine,
                title,
                description,
                recommendation
        );
    }

    private IssueSeverity parseSeverity(String value) {
        try {
            return IssueSeverity.valueOf(requireText(value, "Finding severity is missing").toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new MiMoAgentException(ReviewErrorCodes.MIMO_REVIEW_INVALID,
                    "Finding severity is invalid: " + value);
        }
    }

    private IssueCategory parseCategory(String value) {
        try {
            return IssueCategory.valueOf(requireText(value, "Finding category is missing").toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new MiMoAgentException(ReviewErrorCodes.MIMO_REVIEW_INVALID,
                    "Finding category is invalid: " + value);
        }
    }

    private String normalizeFilePath(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return value.trim();
    }

    private Integer normalizeLine(Integer value) {
        if (value == null || value < 1) {
            return 1;
        }
        return value;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new MiMoAgentException(ReviewErrorCodes.MIMO_REVIEW_INVALID, message);
        }
        return value.trim();
    }
}
