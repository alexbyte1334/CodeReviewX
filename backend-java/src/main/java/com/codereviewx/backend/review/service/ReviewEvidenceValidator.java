package com.codereviewx.backend.review.service;

import com.codereviewx.backend.rag.retrieval.RagEvidence;
import com.codereviewx.backend.rag.retrieval.RagEvidenceBundle;
import com.codereviewx.backend.review.github.GithubPrDiff;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ReviewEvidenceValidator {
    public boolean isGrounded(ReviewFinding finding, RagEvidenceBundle bundle, GithubPrDiff diff) {
        if (finding == null || bundle == null || diff == null || finding.getEvidenceChunkIds().isEmpty()) return false;
        Map<String, RagEvidence> evidence = bundle.evidence().stream()
                .collect(Collectors.toMap(RagEvidence::label, Function.identity()));
        for (String label : finding.getEvidenceChunkIds()) {
            RagEvidence item = evidence.get(label);
            if (item == null || !item.path().equals(finding.getFilePath())) return false;
            int line = finding.getStartLine() == null ? -1 : finding.getStartLine();
            if (line < item.startLine() || line > item.endLine() || !diffExplains(diff.diffText(), item.path(), line)) {
                return false;
            }
        }
        return true;
    }

    private boolean diffExplains(String diffText, String path, int line) {
        if (diffText == null || !diffText.contains("b/" + path)) return false;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@").matcher(diffText);
        while (matcher.find()) {
            int start = Integer.parseInt(matcher.group(1));
            int count = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
            if (line >= start && line < start + Math.max(count, 1)) return true;
        }
        return false;
    }
}
