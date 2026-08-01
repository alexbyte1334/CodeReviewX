package com.codereviewx.backend.demo;

import com.codereviewx.backend.demo.DemoDtos.CreateRequest;
import com.codereviewx.backend.demo.DemoDtos.CreateResponse;
import com.codereviewx.backend.demo.DemoDtos.DecisionRequest;
import com.codereviewx.backend.demo.DemoDtos.Snapshot;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/demo-runs")
public class DemoRunController {
    private final DemoRunService runs;
    private final DemoSseService sse;
    private final DemoClientIpResolver clientIps;

    public DemoRunController(DemoRunService runs, DemoSseService sse, DemoClientIpResolver clientIps) {
        this.runs = runs;
        this.sse = sse;
        this.clientIps = clientIps;
    }

    @PostMapping
    public ResponseEntity<CreateResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreateRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(runs.create(request == null ? null : request.scenarioId(), idempotencyKey,
                        clientIps.resolve(httpRequest)));
    }

    @GetMapping("/{publicId}")
    public Snapshot snapshot(@PathVariable String publicId) {
        return runs.snapshot(publicId);
    }

    @GetMapping(value = "/{publicId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @PathVariable String publicId,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
            @RequestParam(name = "afterSequence", required = false) Long afterSequence) {
        long after = 0;
        if (afterSequence != null) after = Math.max(0, afterSequence);
        if (lastEventId != null && !lastEventId.isBlank()) {
            try {
                after = Math.max(0, Long.parseLong(lastEventId));
            } catch (NumberFormatException ex) {
                throw new DemoApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT_ID",
                        "Last-Event-ID must be a non-negative sequence number.");
            }
        }
        return sse.stream(publicId, after);
    }

    @PostMapping("/{publicId}/decision")
    public Snapshot decide(@PathVariable String publicId, @RequestBody DecisionRequest request) {
        return runs.decide(publicId, request);
    }
}
