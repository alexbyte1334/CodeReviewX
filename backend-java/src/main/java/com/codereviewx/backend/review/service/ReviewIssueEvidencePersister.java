package com.codereviewx.backend.review.service;

import com.codereviewx.backend.rag.persistence.ReviewIssueEvidenceStore;
import com.codereviewx.backend.rag.retrieval.RagEvidenceBundle;
import com.codereviewx.backend.review.persistence.entity.ReviewIssueEntity;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReviewIssueEvidencePersister {
    private final ReviewIssueEvidenceStore store;

    public ReviewIssueEvidencePersister(ReviewIssueEvidenceStore store) {
        this.store = store;
    }

    public void persist(List<ReviewFinding> providerFindings, List<ReviewFinding> allFindings,
                        List<ReviewIssueEntity> savedIssues, RagEvidenceBundle bundle) {
        if (providerFindings == null || allFindings == null || savedIssues == null
                || allFindings.size() != savedIssues.size()) throw invalid();
        Set<String> expectedKeys = new HashSet<>();
        for (ReviewFinding finding : allFindings) {
            if (finding.getIssueKey() == null || finding.getIssueKey().isBlank()
                    || !expectedKeys.add(finding.getIssueKey())) throw invalid();
        }
        Map<String, ReviewIssueEntity> savedByKey = new HashMap<>();
        for (ReviewIssueEntity issue : savedIssues) {
            if (issue.getIssueKey() == null || !expectedKeys.contains(issue.getIssueKey())
                    || savedByKey.putIfAbsent(issue.getIssueKey(), issue) != null) throw invalid();
        }
        if (savedByKey.size() != expectedKeys.size()) throw invalid();
        for (ReviewFinding finding : providerFindings) {
            ReviewIssueEntity issue = savedByKey.get(finding.getIssueKey());
            if (issue == null) throw invalid();
            store.save(issue, finding, bundle);
        }
    }

    private IllegalStateException invalid() {
        return new IllegalStateException("Review issue evidence association is invalid");
    }
}
