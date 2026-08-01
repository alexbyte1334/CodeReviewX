package com.codereviewx.backend.review.controller;

import com.codereviewx.backend.common.ApiResponse;
import com.codereviewx.backend.review.dto.CreateReviewRequest;
import com.codereviewx.backend.review.dto.ReviewApiSnapshot;
import com.codereviewx.backend.review.service.ReviewApiService;
import com.codereviewx.backend.review.dto.CommentPreviewListResponse;
import com.codereviewx.backend.review.dto.CommentPreviewItemResponse;
import com.codereviewx.backend.review.dto.ToolTraceListResponse;
import com.codereviewx.backend.review.dto.UpdateCommentPreviewSelectionRequest;
import com.codereviewx.backend.review.dto.PublishCommentPreviewRequest;
import com.codereviewx.backend.rag.dto.RetrievalTraceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/reviews")
public class ReviewApiController {
    private final ReviewApiService reviews;
    private final ExecutorService streams = Executors.newCachedThreadPool();

    public ReviewApiController(ReviewApiService reviews) { this.reviews = reviews; }

    @PreDestroy
    void shutdownStreams() { streams.shutdownNow(); }

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewApiSnapshot>> create(
            @Valid @RequestBody CreateReviewRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        ReviewApiSnapshot snapshot = reviews.create(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(snapshot));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<ReviewApiSnapshot> snapshot(@PathVariable String publicId) {
        return ApiResponse.success(reviews.snapshot(publicId));
    }

    @GetMapping(value = "/{publicId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String publicId,
                             @RequestParam(defaultValue = "0") long afterSequence,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        SseEmitter emitter = new SseEmitter(120_000L);
        streams.submit(() -> {
            long cursor = Math.max(0, parseSequence(lastEventId, afterSequence));
            try {
                for (int i = 0; i < 60; i++) {
                    var events = reviews.events(publicId, cursor);
                    for (var event : events) {
                        emitter.send(SseEmitter.event().id(Long.toString(event.sequence()))
                                .name(event.type()).data(event));
                        cursor = event.sequence();
                    }
                    var snapshot = reviews.snapshot(publicId);
                    if (snapshot.status().equals("SUCCEEDED") || snapshot.status().equals("FAILED")) {
                        emitter.send(SseEmitter.event().name("stream-complete").data(snapshot.status()));
                        emitter.complete();
                        return;
                    }
                    Thread.sleep(2_000L);
                }
                emitter.complete();
            } catch (IOException ex) { emitter.completeWithError(ex); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); emitter.completeWithError(ex); }
        });
        return emitter;
    }

    private long parseSequence(String value, long fallback) {
        try { return value == null ? fallback : Long.parseLong(value); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    @PostMapping("/{publicId}/retry")
    public ResponseEntity<ApiResponse<ReviewApiSnapshot>> retry(@PathVariable String publicId) {
        return ResponseEntity.accepted().body(ApiResponse.success(reviews.retry(publicId)));
    }

    @GetMapping("/{publicId}/trace")
    public ApiResponse<ToolTraceListResponse> trace(@PathVariable String publicId) {
        return ApiResponse.success(reviews.trace(publicId));
    }

    @GetMapping("/{publicId}/previews")
    public ApiResponse<CommentPreviewListResponse> previews(@PathVariable String publicId) {
        return ApiResponse.success(reviews.previews(publicId));
    }

    @PatchMapping("/{publicId}/previews/selection")
    public ApiResponse<CommentPreviewListResponse> selectPreviews(@PathVariable String publicId,
                                                                    @Valid @RequestBody UpdateCommentPreviewSelectionRequest request) {
        return ApiResponse.success(reviews.selectPreviews(publicId, request));
    }

    @PostMapping("/{publicId}/previews/publish")
    public ApiResponse<CommentPreviewListResponse> publishSelected(@PathVariable String publicId,
                                                                     @Valid @RequestBody PublishCommentPreviewRequest request) {
        return ApiResponse.success(reviews.publishSelected(publicId, request));
    }

    @PostMapping("/{publicId}/previews/{previewId}/publish")
    public ApiResponse<CommentPreviewItemResponse> publishOne(@PathVariable String publicId,
                                                               @PathVariable long previewId,
                                                               @Valid @RequestBody PublishCommentPreviewRequest request) {
        return ApiResponse.success(reviews.publishOne(publicId, previewId, request));
    }

    @GetMapping("/{publicId}/issues/{issueKey}/evidence")
    public ApiResponse<List<RetrievalTraceResponse.Evidence>> evidence(@PathVariable String publicId,
                                                                         @PathVariable String issueKey) {
        return ApiResponse.success(reviews.evidence(publicId, issueKey));
    }

    @GetMapping("/{publicId}/retrieval")
    public ApiResponse<RetrievalTraceResponse> retrieval(@PathVariable String publicId) {
        return ApiResponse.success(reviews.retrieval(publicId));
    }
}
