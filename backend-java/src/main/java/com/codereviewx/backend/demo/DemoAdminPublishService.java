package com.codereviewx.backend.demo;

import com.codereviewx.backend.demo.DemoDtos.PublishResponse;
import com.codereviewx.backend.review.persistence.entity.ReviewCommentPreviewEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewInputSnapshotEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewCommentPreviewRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewInputSnapshotRepository;
import com.codereviewx.backend.review.service.CommentPreviewPublishService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class DemoAdminPublishService {
    private final DemoProperties properties;
    private final DemoRunService runs;
    private final DemoStore store;
    private final ReviewInputSnapshotRepository snapshots;
    private final ReviewCommentPreviewRepository previews;
    private final CommentPreviewPublishService publisher;
    private final DemoPublishGate publishGate;

    public DemoAdminPublishService(DemoProperties properties, DemoRunService runs, DemoStore store,
                                   ReviewInputSnapshotRepository snapshots,
                                   ReviewCommentPreviewRepository previews,
                                   CommentPreviewPublishService publisher,
                                   DemoPublishGate publishGate) {
        this.properties = properties;
        this.runs = runs;
        this.store = store;
        this.snapshots = snapshots;
        this.previews = previews;
        this.publisher = publisher;
        this.publishGate = publishGate;
    }

    public PublishResponse publish(String publicId, String authorization, List<Long> selectedIds) {
        requireAdmin(authorization);
        DemoStore.DemoRow demo = runs.requireRun(publicId);
        if (!properties.getScenarioId().equals(demo.scenarioId()) || !"READY".equals(demo.status())) {
            throw new DemoApiException(HttpStatus.CONFLICT, "DEMO_NOT_PUBLISHABLE",
                    "Only a ready run from the pinned scenario can be published.");
        }
        if (selectedIds == null || selectedIds.isEmpty()) {
            throw new DemoApiException(HttpStatus.BAD_REQUEST, "NO_PREVIEWS_SELECTED",
                    "Select at least one preview.");
        }
        ReviewInputSnapshotEntity snapshot = snapshots.findByReviewRunId(demo.reviewRunId())
                .orElseThrow(() -> new DemoApiException(HttpStatus.CONFLICT, "SNAPSHOT_MISSING",
                        "The immutable GitHub snapshot is missing."));
        publishGate.validateTarget(demo, snapshot);

        List<ReviewCommentPreviewEntity> all = previews.findByReviewRunIdOrderByIdAsc(demo.reviewRunId());
        List<ReviewCommentPreviewEntity> selected = selectedIds.stream().distinct()
                .map(selectedId -> all.stream()
                        .filter(item -> Objects.equals(item.getId(), selectedId))
                        .findFirst()
                        .orElseThrow(() -> new DemoApiException(
                                HttpStatus.BAD_REQUEST, "PREVIEW_OWNERSHIP_MISMATCH",
                                "A preview does not belong to this run.")))
                .toList();
        selected.forEach(preview -> publishGate.validatePreview(demo, preview));

        all.forEach(preview -> preview.setSelectedForPublish(false));
        for (ReviewCommentPreviewEntity preview : selected) {
            preview.setSelectedForPublish(true);
            String marker = "<!-- codereviewx-demo:" + demo.scenarioId() + ":" + preview.getIssueKey() + " -->";
            if (!preview.getDraftBody().contains(marker)) {
                preview.setDraftBody(preview.getDraftBody() + "\n\n" + marker);
            }
            preview.setUpdatedAt(LocalDateTime.now());
        }
        previews.saveAll(all);
        List<ReviewCommentPreviewEntity> published = publisher.publishSelected(demo.reviewRunId());
        List<String> urls = published.stream().map(ReviewCommentPreviewEntity::getGithubCommentUrl)
                .filter(url -> url != null && !url.isBlank()).toList();
        if (!urls.isEmpty()) {
            store.jdbc().update("""
                    UPDATE demo_run SET published_comment_url=?,updated_at=? WHERE id=?
                    """, urls.get(0), LocalDateTime.now(), demo.id());
            store.appendEvent(demo.id(), "GITHUB_PUBLISHED", "HUMAN_REVIEW", "PUBLISHED",
                    "Owner-controlled publish completed.", null, null);
        }
        return new PublishResponse(urls.isEmpty() ? "FAILED" : "PUBLISHED", urls);
    }

    private void requireAdmin(String authorization) {
        String configured = properties.getAdminToken();
        String supplied = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : "";
        if (configured == null || configured.isBlank()
                || !MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new DemoApiException(HttpStatus.UNAUTHORIZED, "ADMIN_AUTH_REQUIRED",
                    "Owner authorization is required.");
        }
    }
}
