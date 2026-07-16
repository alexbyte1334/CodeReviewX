package com.codereviewx.backend.rag.service;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.retrieval.HybridRagRetrievalService;
import com.codereviewx.backend.rag.retrieval.PrRetrievalQueryBuilder;
import com.codereviewx.backend.rag.retrieval.RagContextAssembler;
import com.codereviewx.backend.rag.retrieval.RagEvidenceBundle;
import com.codereviewx.backend.review.enums.ToolTraceStatus;
import com.codereviewx.backend.review.github.GithubPrDiff;
import com.codereviewx.backend.review.github.GithubPrMetadata;
import com.codereviewx.backend.review.service.RepositoryContextIndexResult;
import com.codereviewx.backend.review.service.RepositoryContextIndexService;
import com.codereviewx.backend.review.service.ReviewTraceRecorder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class RagReviewContextFacade {
    private final RagProperties properties;
    private final ObjectProvider<RagIndexService> indexServices;
    private final ObjectProvider<HybridRagRetrievalService> retrievalServices;
    private final ObjectProvider<RagContextAssembler> assemblers;
    private final RepositoryContextIndexService legacy;
    private final ReviewTraceRecorder traces;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Duration timeout;
    private final Duration pollInterval;

    @Autowired
    public RagReviewContextFacade(RagProperties properties, ObjectProvider<RagIndexService> indexServices,
                                  ObjectProvider<HybridRagRetrievalService> retrievalServices,
                                  ObjectProvider<RagContextAssembler> assemblers, RepositoryContextIndexService legacy,
                                  ReviewTraceRecorder traces) {
        this(properties, indexServices, retrievalServices, assemblers, legacy, traces, Clock.systemUTC(),
                Thread::sleep, Duration.ofSeconds(20), Duration.ofMillis(250));
    }

    RagReviewContextFacade(RagProperties properties, ObjectProvider<RagIndexService> indexServices,
                           ObjectProvider<HybridRagRetrievalService> retrievalServices,
                           ObjectProvider<RagContextAssembler> assemblers, RepositoryContextIndexService legacy,
                           ReviewTraceRecorder traces, Clock clock, Sleeper sleeper, Duration timeout,
                           Duration pollInterval) {
        this.properties = properties; this.indexServices = indexServices; this.retrievalServices = retrievalServices;
        this.assemblers = assemblers; this.legacy = legacy; this.traces = traces; this.clock = clock;
        this.sleeper = sleeper; this.timeout = timeout; this.pollInterval = pollInterval;
    }

    public PreparedContext prepare(GithubPrMetadata metadata, GithubPrDiff diff, Long runId) {
        if (!properties.isEnabled()) return legacy(metadata, diff, runId, "RAG disabled");
        RagIndexService index = indexServices.getIfAvailable();
        HybridRagRetrievalService retrieval = retrievalServices.getIfAvailable();
        RagContextAssembler assembler = assemblers.getIfAvailable();
        if (index == null || retrieval == null || assembler == null) return legacy(metadata, diff, runId, "RAG unavailable");
        LocalDateTime start = LocalDateTime.now(clock);
        RagIndexResolution resolution = index.ensureIndexed(metadata);
        record(runId, "rag.index.ensure", start, "Index state=" + resolution.status());
        long deadline = clock.millis() + timeout.toMillis();
        while (resolution.status() != RagIndexResolution.Status.READY && clock.millis() < deadline) {
            RagIndexJob job = index.getJob(resolution.jobId());
            if (job.status() == RagIndexJob.Status.READY) {
                resolution = new RagIndexResolution(resolution.repositoryId(), resolution.jobId(),
                        resolution.commitSha(), RagIndexResolution.Status.READY);
                break;
            }
            if (job.status() == RagIndexJob.Status.FAILED) break;
            try { sleeper.sleep(pollInterval.toMillis()); } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); break;
            }
        }
        if (resolution.status() != RagIndexResolution.Status.READY) return legacy(metadata, diff, runId, "INDEX_NOT_READY");
        PrRetrievalQueryBuilder.PrQuery query = query(metadata, diff);
        record(runId, "rag.query.build", LocalDateTime.now(clock), "Built bounded PR retrieval query");
        HybridRagRetrievalService.Result retrieved;
        try {
            retrieved = retrieval.retrieve(new HybridRagRetrievalService.Request(
                    resolution.repositoryId(), metadata.headSha(), query));
        } catch (RuntimeException failure) {
            return legacy(metadata, diff, runId, "RETRIEVAL_FAILED");
        }
        if (retrieved.status() != HybridRagRetrievalService.Status.READY) return legacy(metadata, diff, runId, "INDEX_NOT_READY");
        record(runId, "rag.retrieve.hybrid", LocalDateTime.now(clock),
                "Retrieved " + retrieved.matches().size() + " bounded candidate(s)");
        String queryText = new PrRetrievalQueryBuilder().build(query);
        RagEvidenceBundle bundle = assembler.assemble(queryText, metadata.headSha(), retrieved.matches());
        record(runId, "rag.rerank", LocalDateTime.now(clock), "Rerank state=" + bundle.reason());
        record(runId, "rag.context.assemble", LocalDateTime.now(clock),
                "Assembled " + bundle.evidence().size() + " evidence block(s)");
        if (bundle.legacyFallbackRequired()) return legacy(metadata, diff, runId, "RETRIEVAL_FAILED");
        return new PreparedContext(RepositoryContextIndexResult.empty(), bundle, false);
    }

    private PreparedContext legacy(GithubPrMetadata metadata, GithubPrDiff diff, Long runId, String reason) {
        LocalDateTime start = LocalDateTime.now(clock);
        RepositoryContextIndexResult context = legacy.index(metadata, diff);
        record(runId, RepositoryContextIndexService.TOOL_NAME, start,
                "Indexed " + context.fileCount() + " repository context file(s), contextBytes="
                        + context.contextBytes() + ", truncated=" + context.truncated() + ". Fallback=" + reason);
        return new PreparedContext(context, null, true);
    }

    private PrRetrievalQueryBuilder.PrQuery query(GithubPrMetadata metadata, GithubPrDiff diff) {
        List<String> paths = diff.files() == null ? List.of() : diff.files().stream().map(file -> file.filename()).toList();
        List<String> lines = diff.diffText() == null ? List.of() : Arrays.asList(diff.diffText().split("\\R"));
        List<String> hunks = lines.stream().filter(line -> line.startsWith("@@")).toList();
        return new PrRetrievalQueryBuilder.PrQuery(metadata.title(), paths, hunks, List.of(), lines);
    }

    private void record(Long runId, String tool, LocalDateTime started, String summary) {
        LocalDateTime finished = LocalDateTime.now(clock);
        traces.recordToolTrace(runId, traces.countToolTraces(runId) + 1, tool, ToolTraceStatus.SUCCESS,
                summary, null, null, started, finished);
    }

    public record PreparedContext(RepositoryContextIndexResult legacyContext, RagEvidenceBundle evidenceBundle,
                                  boolean legacyFallback) {}
    @FunctionalInterface interface Sleeper { void sleep(long millis) throws InterruptedException; }
}
