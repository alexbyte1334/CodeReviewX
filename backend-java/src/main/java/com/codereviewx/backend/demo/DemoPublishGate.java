package com.codereviewx.backend.demo;

import com.codereviewx.backend.review.persistence.entity.ReviewCommentPreviewEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewInputSnapshotEntity;
import com.codereviewx.backend.review.service.ReviewEvidenceValidator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class DemoPublishGate {
    private static final Pattern SAFE_ISSUE_KEY =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,79}");

    private final DemoProperties properties;
    private final DemoStore store;
    private final ReviewEvidenceValidator evidenceValidator;

    public DemoPublishGate(DemoProperties properties, DemoStore store,
                           ReviewEvidenceValidator evidenceValidator) {
        this.properties = properties;
        this.store = store;
        this.evidenceValidator = evidenceValidator;
    }

    public void validateTarget(DemoStore.DemoRow demo, ReviewInputSnapshotEntity snapshot) {
        boolean pinned = properties.getScenarioId().equals(demo.scenarioId())
                && normalize(properties.getRepoUrl()).equals(normalize(snapshot.getRepoUrl()))
                && Objects.equals(properties.getPrNumber(), snapshot.getPrNumber())
                && properties.getExpectedHeadSha().equalsIgnoreCase(snapshot.getHeadSha());
        if (!pinned) {
            throw new DemoApiException(HttpStatus.CONFLICT, "DEMO_TARGET_DRIFT",
                    "The run target does not match the pinned demo repository, PR, and head SHA.");
        }
    }

    public void validatePreview(DemoStore.DemoRow demo, ReviewCommentPreviewEntity preview) {
        if (!Objects.equals(demo.reviewRunId(), preview.getReviewRunId())) {
            throw new DemoApiException(HttpStatus.BAD_REQUEST, "PREVIEW_OWNERSHIP_MISMATCH",
                    "A preview does not belong to this run.");
        }
        if (preview.getIssueKey() == null
                || !SAFE_ISSUE_KEY.matcher(preview.getIssueKey()).matches()) {
            throw new DemoApiException(HttpStatus.CONFLICT, "DEMO_MARKER_INVALID",
                    "The preview issue key is not safe for an idempotency marker.");
        }
        if (preview.getLineNumber() == null || !evidenceValidator.isChangedLine(
                demo.safeDiffText(), preview.getFilePath(), preview.getLineNumber())) {
            throw new DemoApiException(HttpStatus.CONFLICT, "DEMO_CHANGED_LINE_INVALID",
                    "The preview no longer targets a changed line in the pinned diff.");
        }
        if (preview.getReviewIssueId() == null) {
            throw missingEvidence();
        }
        Integer count = store.jdbc().queryForObject("""
                SELECT COUNT(*) FROM review_issue_evidence
                WHERE review_issue_id=? AND path=? AND start_line<=? AND end_line>=?
                """, Integer.class, preview.getReviewIssueId(), preview.getFilePath(),
                preview.getLineNumber(), preview.getLineNumber());
        if (count == null || count < 1) throw missingEvidence();
    }

    private DemoApiException missingEvidence() {
        return new DemoApiException(HttpStatus.CONFLICT, "DEMO_EVIDENCE_INVALID",
                "The preview does not have persisted evidence covering its changed line.");
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.strip().replaceAll("/+$", "");
    }
}
