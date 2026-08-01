package com.codereviewx.backend.rag.service;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.retrieval.PrRetrievalQueryBuilder;
import com.codereviewx.backend.rag.retrieval.RagContextAssembler;
import com.codereviewx.backend.rag.retrieval.RagEvidenceBundle;
import com.codereviewx.backend.rag.retrieval.RagRetrievalTraceStore;
import com.codereviewx.backend.rag.retrieval.RagRetrievalQuery;
import com.codereviewx.backend.rag.retrieval.RagRetrievalRequest;
import com.codereviewx.backend.rag.retrieval.RagRetrievalResult;
import com.codereviewx.backend.rag.retrieval.RagRetrievalService;
import com.codereviewx.backend.review.enums.ToolTraceStatus;
import com.codereviewx.backend.review.github.GithubPrDiff;
import com.codereviewx.backend.review.github.GithubPrMetadata;
import com.codereviewx.backend.review.service.RepositoryContextIndexResult;
import com.codereviewx.backend.review.service.RepositoryContextIndexService;
import com.codereviewx.backend.review.service.ReviewTraceRecorder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
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
    private final ObjectProvider<RagRetrievalService> retrievalServices;
    private final ObjectProvider<RagContextAssembler> assemblers;
    private final RepositoryContextIndexService legacy;
    private final ReviewTraceRecorder traces;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Duration timeout;
    private final Duration pollInterval;
    private final RagRetrievalTraceStore retrievalTraces;
    private final ObjectProvider<RagManifestSnapshotReader> manifestReaders;

    @Autowired
    public RagReviewContextFacade(RagProperties properties, ObjectProvider<RagIndexService> indexServices,
                                  ObjectProvider<RagRetrievalService> retrievalServices,
                                  ObjectProvider<RagContextAssembler> assemblers, RepositoryContextIndexService legacy,
                                  ReviewTraceRecorder traces, RagRetrievalTraceStore retrievalTraces,
                                  ObjectProvider<RagManifestSnapshotReader> manifestReaders) {
        this(properties, indexServices, retrievalServices, assemblers, legacy, traces, Clock.systemUTC(),
                Thread::sleep, Duration.ofSeconds(20), Duration.ofMillis(250), retrievalTraces, manifestReaders);
    }

    RagReviewContextFacade(RagProperties properties, ObjectProvider<RagIndexService> indexServices,
                           ObjectProvider<RagRetrievalService> retrievalServices,
                           ObjectProvider<RagContextAssembler> assemblers, RepositoryContextIndexService legacy,
                           ReviewTraceRecorder traces, Clock clock, Sleeper sleeper, Duration timeout,
                           Duration pollInterval) {
        this(properties, indexServices, retrievalServices, assemblers, legacy, traces, clock, sleeper, timeout,
                pollInterval, null, null);
    }
    RagReviewContextFacade(RagProperties properties, ObjectProvider<RagIndexService> indexServices,
                           ObjectProvider<RagRetrievalService> retrievalServices,
                           ObjectProvider<RagContextAssembler> assemblers, RepositoryContextIndexService legacy,
                           ReviewTraceRecorder traces, Clock clock, Sleeper sleeper, Duration timeout,
                           Duration pollInterval, RagRetrievalTraceStore retrievalTraces) {
        this(properties, indexServices, retrievalServices, assemblers, legacy, traces, clock, sleeper, timeout,
                pollInterval, retrievalTraces, null);
    }
    RagReviewContextFacade(RagProperties properties, ObjectProvider<RagIndexService> indexServices,
                           ObjectProvider<RagRetrievalService> retrievalServices,
                           ObjectProvider<RagContextAssembler> assemblers, RepositoryContextIndexService legacy,
                           ReviewTraceRecorder traces, Clock clock, Sleeper sleeper, Duration timeout,
                           Duration pollInterval, RagRetrievalTraceStore retrievalTraces,
                           ObjectProvider<RagManifestSnapshotReader> manifestReaders) {
        this.properties = properties; this.indexServices = indexServices; this.retrievalServices = retrievalServices;
        this.assemblers = assemblers; this.legacy = legacy; this.traces = traces; this.clock = clock;
        this.sleeper = sleeper; this.timeout = timeout; this.pollInterval = pollInterval;
        this.retrievalTraces = retrievalTraces;
        this.manifestReaders = manifestReaders;
    }

    public PreparedContext prepare(GithubPrMetadata metadata, GithubPrDiff diff, Long runId) {
        return prepare(metadata, diff, runId, runId);
    }

    public PreparedContext prepare(GithubPrMetadata metadata, GithubPrDiff diff, Long reviewApiRunId, Long runId) {
        if (reviewApiRunId == null || !properties.shouldUseRag(reviewApiRunId)) {
            return legacy(metadata, diff, runId, "RAG disabled or rollout bucket excluded");
        }
        RagIndexService index = indexServices.getIfAvailable();
        RagRetrievalService retrieval = retrievalServices.getIfAvailable();
        RagContextAssembler assembler = assemblers.getIfAvailable();
        if (index == null || retrieval == null || assembler == null) return fallback(metadata, diff, runId, "RAG unavailable");
        LocalDateTime start = LocalDateTime.now(clock);
        RagIndexResolution resolution = index.ensureIndexed(metadata);
        if (resolution == null) return fallback(metadata, diff, runId, "INDEX_RESOLUTION_UNAVAILABLE");
        record(runId, "rag.index.ensure", start, "Index state=" + resolution.status());
        long deadline = clock.millis() + timeout.toMillis();
        String fallbackReason = "INDEX_NOT_READY";
        while (resolution.status() != RagIndexResolution.Status.READY && clock.millis() < deadline) {
            if (resolution.jobId() == null) {
                fallbackReason = "INDEX_JOB_ID_UNAVAILABLE";
                break;
            }
            RagIndexJob job = index.getJob(resolution.jobId());
            if (job == null) {
                fallbackReason = "INDEX_JOB_UNAVAILABLE";
                break;
            }
            if (job.status() == RagIndexJob.Status.READY) {
                resolution = new RagIndexResolution(resolution.repositoryId(), resolution.jobId(),
                        resolution.commitSha(), RagIndexResolution.Status.READY);
                break;
            }
            if (job.status() == RagIndexJob.Status.FAILED) {
                fallbackReason = "INDEX_JOB_FAILED";
                break;
            }
            try { sleeper.sleep(pollInterval.toMillis()); } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fallbackReason = "INDEX_WAIT_INTERRUPTED";
                break;
            }
        }
        if (resolution.status() != RagIndexResolution.Status.READY) return fallback(metadata, diff, runId, fallbackReason);
        RagRetrievalQuery query = query(metadata, diff);
        record(runId, "rag.query.build", LocalDateTime.now(clock), "Built bounded PR retrieval query");
        long retrievalStarted = clock.millis();
        RagRetrievalResult retrieved = retrieval.retrieve(new RagRetrievalRequest(
                resolution.repositoryId(), metadata.headSha(), query));
        if (retrieved.status() != RagRetrievalResult.Status.READY) {
            if (retrievalTraces != null) retrievalTraces.save(runId, resolution.repositoryId(), metadata.headSha(),
                    new PrRetrievalQueryBuilder().build(query), retrieved, 0, 0, clock.millis() - retrievalStarted);
            return fallback(metadata, diff, runId, "INDEX_NOT_READY");
        }
        record(runId, "rag.retrieve.hybrid", LocalDateTime.now(clock),
                "Retrieved " + retrieved.matches().size() + " bounded candidate(s), health="
                        + retrieved.retrievalHealth());
        if (retrieved.legacyFallbackRequired()) {
            if (retrievalTraces != null) retrievalTraces.save(runId, resolution.repositoryId(), metadata.headSha(),
                    new PrRetrievalQueryBuilder().build(query), retrieved, 0, 0, clock.millis() - retrievalStarted);
            return fallback(metadata, diff, runId, retrieved.retrievalHealth().name());
        }
        String queryText = new PrRetrievalQueryBuilder().build(query);
        RagEvidenceBundle bundle = assembler.assemble(queryText, metadata.headSha(), retrieved.matches(),
                retrieved.retrievalHealth());
        if (retrievalTraces != null) retrievalTraces.save(runId, resolution.repositoryId(), metadata.headSha(),
                queryText, retrieved, bundle.evidence().size(), bundle.evidence().stream()
                        .mapToInt(evidence -> evidence.content().length()).sum(), clock.millis() - retrievalStarted);
        record(runId, "rag.rerank", LocalDateTime.now(clock), "Rerank state=" + bundle.reason());
        if (bundle.legacyFallbackRequired()) {
            record(runId, "rag.context.assemble", LocalDateTime.now(clock),
                    "Legacy fallback; evidence assembly was not usable: " + bundle.reason(), ToolTraceStatus.FAILED);
            return fallback(metadata, diff, runId, "RETRIEVAL_FAILED");
        }
        RepositoryContextIndexResult manifests;
        try {
            manifests = loadChangedManifests(resolution, metadata, diff);
        } catch (DataAccessException manifestUnavailable) {
            record(runId, "rag.context.assemble", LocalDateTime.now(clock),
                    "Manifest snapshot unavailable; using bounded fallback", ToolTraceStatus.FAILED);
            return fallback(metadata, diff, runId, "MANIFEST_SNAPSHOT_UNAVAILABLE");
        }
        record(runId, "rag.context.assemble", LocalDateTime.now(clock),
                "Assembled " + bundle.evidence().size() + " evidence block(s)");
        return new PreparedContext(manifests, bundle, false);
    }

    private RepositoryContextIndexResult loadChangedManifests(RagIndexResolution resolution,
                                                               GithubPrMetadata metadata,
                                                               GithubPrDiff diff) {
        if (manifestReaders == null || resolution.jobId() == null) return RepositoryContextIndexResult.empty();
        RagManifestSnapshotReader reader = manifestReaders.getIfAvailable();
        if (reader == null) return RepositoryContextIndexResult.empty();
        List<String> changedPaths = diff.files() == null ? List.of()
                : diff.files().stream().map(file -> file.filename()).toList();
        RepositoryContextIndexResult manifests = reader.read(
                resolution.repositoryId(), resolution.jobId(), metadata.headSha(), changedPaths);
        return manifests == null ? RepositoryContextIndexResult.empty() : manifests;
    }

    private PreparedContext legacy(GithubPrMetadata metadata, GithubPrDiff diff, Long runId, String reason) {
        LocalDateTime start = LocalDateTime.now(clock);
        RepositoryContextIndexResult context = legacy.index(metadata, diff);
        record(runId, RepositoryContextIndexService.TOOL_NAME, start,
                "Indexed " + context.fileCount() + " repository context file(s), contextBytes="
                        + context.contextBytes() + ", truncated=" + context.truncated() + ". Fallback=" + reason);
        return new PreparedContext(context, null, true);
    }

    private PreparedContext fallback(GithubPrMetadata metadata, GithubPrDiff diff, Long runId, String reason) {
        if (!properties.isFallbackEnabled()) {
            throw new IllegalStateException("RAG review failed and fallback is disabled: " + reason);
        }
        return legacy(metadata, diff, runId, reason);
    }

    private RagRetrievalQuery query(GithubPrMetadata metadata, GithubPrDiff diff) {
        List<String> paths = diff.files() == null ? List.of() : diff.files().stream().map(file -> file.filename()).toList();
        List<String> lines = diff.diffText() == null ? List.of() : Arrays.asList(diff.diffText().split("\\R"));
        List<String> hunks = lines.stream().filter(line -> line.startsWith("@@")).toList();
        return new RagRetrievalQuery(metadata.title(), paths, hunks, List.of(), lines);
    }

    private void record(Long runId, String tool, LocalDateTime started, String summary) {
        record(runId, tool, started, summary, ToolTraceStatus.SUCCESS);
    }

    private void record(Long runId, String tool, LocalDateTime started, String summary, ToolTraceStatus status) {
        LocalDateTime finished = LocalDateTime.now(clock);
        traces.recordToolTrace(runId, traces.countToolTraces(runId) + 1, tool, status,
                summary, null, null, started, finished);
    }

    public record PreparedContext(RepositoryContextIndexResult legacyContext, RagEvidenceBundle evidenceBundle,
                                  boolean legacyFallback) {}
    @FunctionalInterface interface Sleeper { void sleep(long millis) throws InterruptedException; }
}
