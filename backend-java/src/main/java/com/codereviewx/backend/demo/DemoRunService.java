package com.codereviewx.backend.demo;

import com.codereviewx.backend.demo.DemoDtos.CreateResponse;
import com.codereviewx.backend.demo.DemoDtos.DecisionRequest;
import com.codereviewx.backend.demo.DemoDtos.Evidence;
import com.codereviewx.backend.demo.DemoDtos.Finding;
import com.codereviewx.backend.demo.DemoDtos.Preview;
import com.codereviewx.backend.demo.DemoDtos.Snapshot;
import com.codereviewx.backend.demo.DemoDtos.Step;
import com.codereviewx.backend.demo.DemoDtos.ToolTrace;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DemoRunService {
    private static final List<String> STEP_ORDER = List.of(
            "PR_INGEST", "REPOSITORY_INDEX", "HYBRID_RAG", "AI_PLAN",
            "AI_REVIEW", "EVIDENCE_GATE", "HUMAN_REVIEW"
    );
    private static final Map<String, String> STEP_LABELS = Map.of(
            "PR_INGEST", "PR ingest",
            "REPOSITORY_INDEX", "Repository index",
            "HYBRID_RAG", "Hybrid RAG",
            "AI_PLAN", "AI plan",
            "AI_REVIEW", "AI review",
            "EVIDENCE_GATE", "Evidence gate",
            "HUMAN_REVIEW", "Human review"
    );

    private final DemoProperties properties;
    private final DemoStore store;
    private final DemoTaskLifecycleService lifecycle;

    public DemoRunService(DemoProperties properties, DemoStore store,
                          DemoTaskLifecycleService lifecycle) {
        this.properties = properties;
        this.store = store;
        this.lifecycle = lifecycle;
    }

    public CreateResponse create(String scenarioId, String idempotencyKey, String remoteIp) {
        validateIdempotencyKey(idempotencyKey);
        DemoStore.DemoRow existing = store.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) return toCreateResponse(existing);
        if (!properties.isLiveReady()) {
            throw new DemoApiException(HttpStatus.SERVICE_UNAVAILABLE, "LIVE_DEMO_NOT_READY",
                    "Live Demo is not configured with a pinned pull request. Use Replay Mode.");
        }
        if (!properties.getScenarioId().equals(scenarioId)) {
            throw new DemoApiException(HttpStatus.NOT_FOUND, "SCENARIO_NOT_ALLOWED",
                    "Only the pinned public demo scenario is allowed.");
        }

        String ipHash = hashIp(remoteIp);
        LocalDateTime window = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        int count = store.incrementRateBucket(ipHash, window);
        if (count > properties.getRequestsPerHour()) {
            throw new DemoApiException(HttpStatus.TOO_MANY_REQUESTS, "DEMO_RATE_LIMITED",
                    "This client has reached the hourly live-demo limit.");
        }
        if (store.activeRunsForIp(ipHash) >= properties.getConcurrentPerIp()) {
            throw new DemoApiException(HttpStatus.CONFLICT, "DEMO_ALREADY_RUNNING",
                    "This client already has a live demo in progress.");
        }
        if (store.activeRuns() >= properties.getGlobalConcurrency()) {
            throw new DemoApiException(HttpStatus.TOO_MANY_REQUESTS, "DEMO_CAPACITY_REACHED",
                    "Live Demo is at capacity. Retry later or use Replay Mode.");
        }

        DemoTaskLifecycleService.CreatedTask task =
                lifecycle.create(properties.getRepoUrl(), properties.getPrNumber());
        DemoStore.DemoRow demo =
                store.create(scenarioId, idempotencyKey, ipHash, task.taskId(), task.runId());
        return toCreateResponse(demo);
    }

    public Snapshot snapshot(String publicId) {
        DemoStore.DemoRow demo = requireRun(publicId);
        var jdbc = store.jdbc();
        List<Finding> findings = jdbc.query("""
                SELECT issue_key,severity,category,file_path,start_line,title,description,recommendation
                FROM review_issue WHERE review_run_id=? ORDER BY id
                """, (rs, row) -> new Finding(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getInt(5), rs.getString(6), rs.getString(7), rs.getString(8)),
                demo.reviewRunId());
        List<Evidence> evidence;
        try {
            evidence = jdbc.query("""
                    SELECT i.issue_key,e.citation_label,e.path,e.start_line,e.end_line,e.evidence_excerpt,
                      e.retrieval_rank,e.retrieval_score
                    FROM review_issue_evidence e JOIN review_issue i ON i.id=e.review_issue_id
                    WHERE i.review_run_id=? ORDER BY i.id,e.retrieval_rank
                    """, (rs, row) -> new Evidence(rs.getString(1), rs.getString(2), rs.getString(3),
                    (Integer) rs.getObject(4), (Integer) rs.getObject(5), rs.getString(6),
                    (Integer) rs.getObject(7), rs.getDouble(8)), demo.reviewRunId());
        } catch (DataAccessException ignored) {
            evidence = List.of();
        }
        List<ToolTrace> traces = jdbc.query("""
                SELECT sequence_number,tool_name,status,input_summary,output_summary,error_code,duration_ms
                FROM review_tool_trace WHERE review_run_id=? ORDER BY sequence_number
                """, (rs, row) -> new ToolTrace(rs.getLong(1), rs.getString(2), rs.getString(3),
                redact(rs.getString(4)), redact(rs.getString(5)), rs.getString(6),
                (Long) rs.getObject(7)), demo.reviewRunId());
        List<Preview> previews = jdbc.query("""
                SELECT id,issue_key,file_path,line_number,severity,category,draft_body,
                  selected_for_publish,publish_status,github_comment_url
                FROM review_comment_preview WHERE review_run_id=? ORDER BY id
                """, (rs, row) -> new Preview(rs.getLong(1), rs.getString(2), rs.getString(3),
                (Integer) rs.getObject(4), rs.getString(5), rs.getString(6), rs.getString(7),
                rs.getBoolean(8), rs.getString(9), rs.getString(10)), demo.reviewRunId());
        var events = store.events(demo.id(), 0);
        return new Snapshot(
                demo.publicId(), demo.scenarioId(), "LIVE", demo.status(), demo.decision(),
                demo.replayReason(), demo.safeDiffText(), buildSteps(events, demo.status()),
                findings, evidence, traces, previews, events, demo.publishedCommentUrl(),
                demo.createdAt(), demo.updatedAt()
        );
    }

    public Snapshot decide(String publicId, DecisionRequest request) {
        DemoStore.DemoRow demo = requireRun(publicId);
        String decision = request == null || request.decision() == null
                ? "" : request.decision().toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVE_PREVIEW", "REJECT").contains(decision)) {
            throw new DemoApiException(HttpStatus.BAD_REQUEST, "INVALID_DEMO_DECISION",
                    "decision must be APPROVE_PREVIEW or REJECT");
        }
        if (!Set.of("READY", "FAILED").contains(demo.status())) {
            throw new DemoApiException(HttpStatus.CONFLICT, "DEMO_NOT_READY",
                    "A decision can only be recorded after the run reaches review.");
        }
        var jdbc = store.jdbc();
        jdbc.update("UPDATE review_comment_preview SET selected_for_publish=FALSE,updated_at=? WHERE review_run_id=?",
                LocalDateTime.now(), demo.reviewRunId());
        if ("APPROVE_PREVIEW".equals(decision)) {
            List<Long> selected = request.selectedPreviewIds() == null ? List.of() : request.selectedPreviewIds();
            if (selected.isEmpty()) {
                throw new DemoApiException(HttpStatus.BAD_REQUEST, "NO_PREVIEWS_SELECTED",
                        "Select at least one preview.");
            }
            for (Long id : selected) {
                int changed = jdbc.update("""
                        UPDATE review_comment_preview SET selected_for_publish=TRUE,updated_at=?
                        WHERE id=? AND review_run_id=?
                        """, LocalDateTime.now(), id, demo.reviewRunId());
                if (changed != 1) {
                    throw new DemoApiException(HttpStatus.BAD_REQUEST, "PREVIEW_OWNERSHIP_MISMATCH",
                            "A selected preview does not belong to this demo run.");
                }
            }
        }
        store.setDecision(demo.id(), decision);
        return snapshot(publicId);
    }

    public DemoStore.DemoRow requireRun(String publicId) {
        try {
            UUID.fromString(publicId);
        } catch (Exception ex) {
            throw new DemoApiException(HttpStatus.NOT_FOUND, "DEMO_RUN_NOT_FOUND", "Demo run not found.");
        }
        return store.findByPublicId(publicId)
                .orElseThrow(() -> new DemoApiException(
                        HttpStatus.NOT_FOUND, "DEMO_RUN_NOT_FOUND", "Demo run not found."));
    }

    private List<Step> buildSteps(List<DemoDtos.Event> events, String runStatus) {
        Map<String, DemoDtos.Event> latest = new LinkedHashMap<>();
        events.stream().filter(event -> event.step() != null)
                .forEach(event -> latest.put(event.step(), event));
        return STEP_ORDER.stream().map(id -> {
            DemoDtos.Event event = latest.get(id);
            String status = event == null ? "PENDING" : event.status();
            if ("READY".equals(runStatus) && "HUMAN_REVIEW".equals(id)) status = "READY";
            return new Step(id, STEP_LABELS.get(id), status,
                    event == null ? null : event.durationMs(),
                    event == null ? null : event.summary(),
                    event == null ? null : event.errorCode());
        }).toList();
    }

    private CreateResponse toCreateResponse(DemoStore.DemoRow demo) {
        String path = "/api/demo-runs/" + demo.publicId();
        return new CreateResponse(demo.publicId(), demo.status(), path, path + "/events", "LIVE");
    }

    private void validateIdempotencyKey(String key) {
        try {
            if (key == null || key.length() > 64) throw new IllegalArgumentException();
            UUID.fromString(key);
        } catch (Exception ex) {
            throw new DemoApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must be a UUID.");
        }
    }

    private String hashIp(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (properties.getIpHashSalt() + ":" + (value == null ? "unknown" : value))
                            .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String redact(String value) {
        if (value == null) return null;
        return value.replaceAll("(?i)(token|key|authorization)=[^,\\s]+", "$1=[redacted]");
    }
}
