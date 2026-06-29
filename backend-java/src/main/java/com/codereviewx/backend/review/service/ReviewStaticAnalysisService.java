package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.enums.IssueCategory;
import com.codereviewx.backend.review.enums.IssueSeverity;
import com.codereviewx.backend.review.enums.IssueSource;
import com.codereviewx.backend.review.enums.IssueStatus;
import com.codereviewx.backend.review.github.GithubPrDiff;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReviewStaticAnalysisService {

    public static final String TOOL_NAME = "static.analysis.findings";

    private static final Pattern HUNK_PATTERN = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*");

    public List<ReviewFinding> analyze(GithubPrDiff diff, RepositoryContextIndexResult repositoryContext) {
        List<ReviewFinding> findings = new ArrayList<>();
        List<ChangedLine> changedLines = parseChangedLines(diff == null ? null : diff.diffText());
        addSemgrepStyleFindings(findings, changedLines);
        addDependencyFindings(findings, repositoryContext);
        return findings;
    }

    private void addSemgrepStyleFindings(List<ReviewFinding> findings, List<ChangedLine> changedLines) {
        int sequence = 1;
        for (ChangedLine line : changedLines) {
            String normalized = line.text().toLowerCase(Locale.ROOT);
            if (normalized.contains("system.out.println") || normalized.contains("system.err.println")) {
                findings.add(finding(
                        "SEMGREP-" + sequence++,
                        IssueSeverity.LOW,
                        IssueCategory.MAINTAINABILITY,
                        IssueSource.SEMGREP,
                        line.filePath(),
                        line.lineNumber(),
                        "Debug output introduced in backend code",
                        "A changed line writes directly to System.out/System.err. Production code should use structured application logging.",
                        "Replace direct console output with the application logger and avoid printing sensitive request details."
                ));
            }
            if (looksLikeRequestSecretRead(normalized)) {
                findings.add(finding(
                        "SEMGREP-" + sequence++,
                        IssueSeverity.HIGH,
                        IssueCategory.SECURITY,
                        IssueSource.SEMGREP,
                        line.filePath(),
                        line.lineNumber(),
                        "Secret-like request parameter introduced",
                        "A changed line appears to read a secret-like value from request input. This can accidentally expose credentials or normalize unsafe secret handling.",
                        "Avoid accepting secrets through query parameters; use server-side secret storage or a protected request body with audit controls."
                ));
            }
            if (looksLikeSqlInjection(normalized)) {
                findings.add(finding(
                        "SEMGREP-" + sequence++,
                        IssueSeverity.HIGH,
                        IssueCategory.SECURITY,
                        IssueSource.SEMGREP,
                        line.filePath(),
                        line.lineNumber(),
                        "Possible SQL injection through string concatenation",
                        "A changed line appears to build a SQL statement by concatenating request or parameter data.",
                        "Use parameterized queries or a query builder that binds user-controlled values instead of concatenating SQL strings."
                ));
            }
        }
    }

    private void addDependencyFindings(List<ReviewFinding> findings, RepositoryContextIndexResult repositoryContext) {
        if (repositoryContext == null || repositoryContext.files() == null) {
            return;
        }
        int sequence = 1;
        for (RepositoryContextFile file : repositoryContext.files()) {
            String path = file.path() == null ? "" : file.path();
            String content = file.content() == null ? "" : file.content();
            if (path.endsWith("package.json")) {
                for (String line : content.split("\\R")) {
                    if (line.contains("\": \"latest\"") || line.contains("\": \"*\"")) {
                        findings.add(finding(
                                "DEPENDENCY-" + sequence++,
                                IssueSeverity.MEDIUM,
                                IssueCategory.SECURITY,
                                IssueSource.DEPENDENCY,
                                path,
                                lineNumber(content, line),
                                "Unpinned npm dependency range",
                                "A package dependency uses latest or wildcard versioning. Builds can become non-reproducible and may pull unsafe updates.",
                                "Pin the dependency to a specific compatible range and keep package-lock.json committed."
                        ));
                    }
                }
            }
            if (path.endsWith("pom.xml")) {
                for (String line : content.split("\\R")) {
                    String lower = line.toLowerCase(Locale.ROOT);
                    if (lower.contains("snapshot</version>")) {
                        findings.add(finding(
                                "DEPENDENCY-" + sequence++,
                                IssueSeverity.MEDIUM,
                                IssueCategory.MAINTAINABILITY,
                                IssueSource.DEPENDENCY,
                                path,
                                lineNumber(content, line),
                                "Snapshot Maven dependency introduced",
                                "A Maven dependency uses a SNAPSHOT version. This makes review results and builds less reproducible.",
                                "Use a released dependency version before merging or isolate snapshot dependencies behind a local-only profile."
                        ));
                    }
                }
            }
        }
    }

    private static List<ChangedLine> parseChangedLines(String diffText) {
        List<ChangedLine> lines = new ArrayList<>();
        if (diffText == null || diffText.isBlank()) {
            return lines;
        }
        String currentFile = "UNKNOWN";
        int currentNewLine = 0;
        for (String line : diffText.split("\\R")) {
            if (line.startsWith("diff --git ")) {
                currentFile = parseFilePath(line);
                currentNewLine = 0;
                continue;
            }
            Matcher hunk = HUNK_PATTERN.matcher(line);
            if (hunk.matches()) {
                currentNewLine = Integer.parseInt(hunk.group(1));
                continue;
            }
            if (line.startsWith("+++") || line.startsWith("---")) {
                continue;
            }
            if (line.startsWith("+")) {
                lines.add(new ChangedLine(currentFile, Math.max(1, currentNewLine), line.substring(1)));
                currentNewLine++;
            } else if (line.startsWith("-")) {
                continue;
            } else if (currentNewLine > 0) {
                currentNewLine++;
            }
        }
        return lines;
    }

    private static String parseFilePath(String line) {
        int marker = line.indexOf(" b/");
        if (marker < 0) {
            return "UNKNOWN";
        }
        return line.substring(marker + 3).trim();
    }

    private static boolean looksLikeRequestSecretRead(String normalizedLine) {
        return (normalizedLine.contains("request.") || normalizedLine.contains("req."))
                && (normalizedLine.contains("password")
                || normalizedLine.contains("token")
                || normalizedLine.contains("secret")
                || normalizedLine.contains("apikey")
                || normalizedLine.contains("api_key"));
    }

    private static boolean looksLikeSqlInjection(String normalizedLine) {
        return (normalizedLine.contains("select ")
                || normalizedLine.contains("insert ")
                || normalizedLine.contains("update ")
                || normalizedLine.contains("delete "))
                && normalizedLine.contains("+")
                && (normalizedLine.contains("request")
                || normalizedLine.contains("param")
                || normalizedLine.contains("query"));
    }

    private static ReviewFinding finding(String key,
                                         IssueSeverity severity,
                                         IssueCategory category,
                                         IssueSource source,
                                         String filePath,
                                         Integer line,
                                         String title,
                                         String description,
                                         String recommendation) {
        return new ReviewFinding(
                key,
                severity,
                category,
                source,
                IssueStatus.OPEN,
                filePath == null || filePath.isBlank() ? "UNKNOWN" : filePath,
                line == null || line < 1 ? 1 : line,
                line == null || line < 1 ? 1 : line,
                title,
                description,
                recommendation
        );
    }

    private static int lineNumber(String content, String needle) {
        String[] lines = content.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].equals(needle)) {
                return i + 1;
            }
        }
        return 1;
    }
}
