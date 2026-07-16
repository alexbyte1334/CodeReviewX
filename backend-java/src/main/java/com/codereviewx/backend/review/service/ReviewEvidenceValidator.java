package com.codereviewx.backend.review.service;

import com.codereviewx.backend.rag.retrieval.RagEvidence;
import com.codereviewx.backend.rag.retrieval.RagEvidenceBundle;
import com.codereviewx.backend.review.github.GithubPrDiff;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReviewEvidenceValidator {
    public boolean isGrounded(ReviewFinding finding, RagEvidenceBundle bundle, GithubPrDiff diff) {
        if (finding == null || bundle == null || diff == null || finding.getEvidenceChunkIds().isEmpty()
                || finding.getEvidenceChunkIds().stream().anyMatch(label -> label == null || label.isBlank())
                || finding.getEvidenceChunkIds().stream().distinct().count() != finding.getEvidenceChunkIds().size()) return false;
        Map<String, RagEvidence> evidence = bundle.evidence().stream()
                .collect(Collectors.toMap(RagEvidence::label, Function.identity()));
        for (String label : finding.getEvidenceChunkIds()) {
            RagEvidence item = evidence.get(label);
            if (item == null || !item.path().equals(finding.getFilePath())) return false;
            int start = finding.getStartLine() == null ? -1 : finding.getStartLine();
            int end = finding.getEndLine() == null ? start : finding.getEndLine();
            if (start < item.startLine() || end > item.endLine() || end < start || end - start > 10_000
                    || !diffExplains(diff.diffText(), item.path(), start, end)) {
                return false;
            }
        }
        return true;
    }

    private boolean diffExplains(String diffText, String path, int start, int end) {
        Set<Integer> lines = parseNewSideLines(diffText).getOrDefault(path, Set.of());
        for (int line = start; line <= end; line++) if (!lines.contains(line)) return false;
        return true;
    }

    private Map<String, Set<Integer>> parseNewSideLines(String diffText) {
        Map<String, Set<Integer>> byPath = new HashMap<>();
        if (diffText == null) return byPath;
        Pattern hunk = Pattern.compile("@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@");
        String path = null;
        int newLine = -1;
        int remaining = 0;
        for (String line : diffText.split("\\R", -1)) {
            if (line.startsWith("diff --git ")) {
                int marker = line.lastIndexOf(" b/");
                if (marker >= 0) path = unquote(line.substring(marker + 3));
                newLine = -1;
                remaining = 0;
                continue;
            }
            if (line.startsWith("+++ ")) {
                String value = line.substring(4).trim();
                value = unquote(value);
                path = value.equals("/dev/null") ? null : value.startsWith("b/") ? value.substring(2) : value;
                continue;
            }
            Matcher matcher = hunk.matcher(line);
            if (matcher.find()) {
                newLine = Integer.parseInt(matcher.group(1));
                remaining = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
                continue;
            }
            if (path == null || newLine < 0 || remaining <= 0) continue;
            if (line.startsWith("-")) continue;
            if (line.startsWith("+") || line.startsWith(" ")) {
                byPath.computeIfAbsent(path, ignored -> new HashSet<>()).add(newLine++);
                remaining--;
            }
        }
        return byPath;
    }

    private String unquote(String value) {
        return value.startsWith("\"") && value.endsWith("\"") ? value.substring(1, value.length() - 1) : value;
    }
}
