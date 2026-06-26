package com.codereviewx.backend.review.pipeline.provider.mimo;

import com.codereviewx.backend.review.enums.IssueCategory;
import com.codereviewx.backend.review.enums.IssueSeverity;
import com.codereviewx.backend.review.enums.IssueSource;
import com.codereviewx.backend.review.enums.IssueStatus;
import com.codereviewx.backend.review.pipeline.ReviewFinding;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XiaomiMiMoFindingParserTest {

    private XiaomiMiMoFindingParser parser;

    @BeforeEach
    void setUp() {
        parser = new XiaomiMiMoFindingParser(new ObjectMapper());
    }

    @Test
    void parse_validJsonArray() {
        String json = """
                [
                  {
                    "issueKey": "MIMO-ISSUE-1",
                    "severity": "MEDIUM",
                    "category": "MAINTAINABILITY",
                    "filePath": "src/App.java",
                    "startLine": 5,
                    "endLine": 8,
                    "title": "Long method",
                    "description": "Method is too long.",
                    "recommendation": "Extract helpers."
                  }
                ]
                """;

        List<ReviewFinding> findings = parser.parse(json);

        assertThat(findings).hasSize(1);
        ReviewFinding finding = findings.get(0);
        assertThat(finding.getIssueKey()).isEqualTo("MIMO-ISSUE-1");
        assertThat(finding.getSeverity()).isEqualTo(IssueSeverity.MEDIUM);
        assertThat(finding.getCategory()).isEqualTo(IssueCategory.MAINTAINABILITY);
        assertThat(finding.getSource()).isEqualTo(IssueSource.MIMO);
        assertThat(finding.getStatus()).isEqualTo(IssueStatus.OPEN);
    }

    @Test
    void parse_validEmptyArray() {
        assertThat(parser.parse("[]")).isEmpty();
    }

    @Test
    void parse_rejectsMalformedJson() {
        assertThatThrownBy(() -> parser.parse("{not-json"))
                .isInstanceOf(XiaomiMiMoParseException.class);
    }

    @Test
    void parse_rejectsInvalidSeverity() {
        String json = """
                [
                  {
                    "severity": "CRITICAL",
                    "category": "SECURITY",
                    "filePath": "src/App.java",
                    "startLine": 1,
                    "endLine": 1,
                    "title": "Bad severity",
                    "description": "desc",
                    "recommendation": "fix"
                  }
                ]
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(XiaomiMiMoParseException.class)
                .hasMessageContaining("severity");
    }

    @Test
    void parse_rejectsInvalidCategory() {
        String json = """
                [
                  {
                    "severity": "LOW",
                    "category": "DOCUMENTATION",
                    "filePath": "src/App.java",
                    "startLine": 1,
                    "endLine": 1,
                    "title": "Bad category",
                    "description": "desc",
                    "recommendation": "fix"
                  }
                ]
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(XiaomiMiMoParseException.class)
                .hasMessageContaining("category");
    }

    @Test
    void parse_generatesIssueKeyWhenMissing() {
        String json = """
                [
                  {
                    "severity": "LOW",
                    "category": "STYLE",
                    "filePath": "src/App.java",
                    "startLine": 1,
                    "endLine": 1,
                    "title": "Format issue",
                    "description": "desc",
                    "recommendation": "fix"
                  },
                  {
                    "severity": "HIGH",
                    "category": "BUG",
                    "filePath": "src/App.java",
                    "startLine": 2,
                    "endLine": 2,
                    "title": "Bug",
                    "description": "desc",
                    "recommendation": "fix"
                  }
                ]
                """;

        List<ReviewFinding> findings = parser.parse(json);

        assertThat(findings).extracting(ReviewFinding::getIssueKey)
                .containsExactly("MIMO-ISSUE-1", "MIMO-ISSUE-2");
    }

    @Test
    void parse_handlesBlankOptionalFields() {
        String json = """
                [
                  {
                    "severity": "LOW",
                    "category": "TEST",
                    "filePath": "",
                    "title": "",
                    "description": "",
                    "recommendation": ""
                  }
                ]
                """;

        ReviewFinding finding = parser.parse(json).get(0);

        assertThat(finding.getFilePath()).isEqualTo("unknown");
        assertThat(finding.getStartLine()).isEqualTo(1);
        assertThat(finding.getEndLine()).isEqualTo(1);
        assertThat(finding.getTitle()).isEqualTo("Review finding");
        assertThat(finding.getDescription()).isEqualTo("No description provided.");
        assertThat(finding.getRecommendation()).isEqualTo("Review and address this finding.");
    }

    @Test
    void parse_rejectsNonArrayOutput() {
        assertThatThrownBy(() -> parser.parse("{\"issueKey\":\"x\"}"))
                .isInstanceOf(XiaomiMiMoParseException.class);
    }
}
