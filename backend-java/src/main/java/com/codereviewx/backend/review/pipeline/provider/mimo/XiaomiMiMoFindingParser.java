package com.codereviewx.backend.review.pipeline.provider.mimo;

import com.codereviewx.backend.review.enums.IssueCategory;
import com.codereviewx.backend.review.enums.IssueSeverity;
import com.codereviewx.backend.review.enums.IssueSource;
import com.codereviewx.backend.review.enums.IssueStatus;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class XiaomiMiMoFindingParser {

    private static final String DEFAULT_FILE_PATH = "unknown";
    private static final int DEFAULT_LINE = 1;
    private static final String DEFAULT_TITLE = "Review finding";
    private static final String DEFAULT_DESCRIPTION = "No description provided.";
    private static final String DEFAULT_RECOMMENDATION = "Review and address this finding.";

    private final ObjectMapper objectMapper;

    public XiaomiMiMoFindingParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ReviewFinding> parse(String modelOutput) {
        if (modelOutput == null || modelOutput.isBlank()) {
            throw new XiaomiMiMoParseException("Model output is empty");
        }

        String json = modelOutput.trim();
        if (!json.startsWith("[")) {
            throw new XiaomiMiMoParseException("Model output is not a JSON array");
        }

        List<Map<String, Object>> records;
        try {
            records = objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new XiaomiMiMoParseException("Model output is not valid JSON", ex);
        }

        if (records.isEmpty()) {
            return List.of();
        }

        List<ReviewFinding> findings = new ArrayList<>();
        for (int index = 0; index < records.size(); index++) {
            findings.add(toFinding(records.get(index), index + 1));
        }
        return List.copyOf(findings);
    }

    private ReviewFinding toFinding(Map<String, Object> record, int sequence) {
        IssueSeverity severity = parseSeverity(record.get("severity"));
        IssueCategory category = parseCategory(record.get("category"));

        String issueKey = sanitizeText(record.get("issueKey"));
        if (issueKey == null) {
            issueKey = "MIMO-ISSUE-" + sequence;
        }

        String filePath = sanitizeText(record.get("filePath"));
        if (filePath == null) {
            filePath = DEFAULT_FILE_PATH;
        }

        Integer startLine = parseLine(record.get("startLine"), DEFAULT_LINE);
        Integer endLine = parseLine(record.get("endLine"), startLine);

        String title = defaultIfBlank(sanitizeText(record.get("title")), DEFAULT_TITLE);
        String description = defaultIfBlank(sanitizeText(record.get("description")), DEFAULT_DESCRIPTION);
        String recommendation = defaultIfBlank(sanitizeText(record.get("recommendation")), DEFAULT_RECOMMENDATION);

        return new ReviewFinding(
                issueKey,
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

    private IssueSeverity parseSeverity(Object value) {
        String text = sanitizeText(value);
        if (text == null) {
            throw new XiaomiMiMoParseException("Finding severity is missing");
        }
        try {
            return IssueSeverity.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new XiaomiMiMoParseException("Finding severity is invalid: " + text);
        }
    }

    private IssueCategory parseCategory(Object value) {
        String text = sanitizeText(value);
        if (text == null) {
            throw new XiaomiMiMoParseException("Finding category is missing");
        }
        try {
            return IssueCategory.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new XiaomiMiMoParseException("Finding category is invalid: " + text);
        }
    }

    private Integer parseLine(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            int line = number.intValue();
            return line > 0 ? line : defaultValue;
        }
        String text = sanitizeText(value);
        if (text == null) {
            return defaultValue;
        }
        try {
            int line = Integer.parseInt(text);
            return line > 0 ? line : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String sanitizeText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode node) {
            if (node.isNull()) {
                return null;
            }
            String text = node.asText();
            return text.isBlank() ? null : text.trim();
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
