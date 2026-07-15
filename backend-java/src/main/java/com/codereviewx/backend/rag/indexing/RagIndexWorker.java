package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.embedding.EmbeddingClient;
import com.codereviewx.backend.rag.model.CodeChunk;
import com.codereviewx.backend.rag.model.RepositoryFile;
import com.codereviewx.backend.rag.persistence.RagChunkStore;
import com.codereviewx.backend.rag.persistence.RagChunkStore.EmbeddedChunk;
import com.codereviewx.backend.rag.persistence.RagChunkStore.ReusedChunk;
import com.codereviewx.backend.rag.persistence.RagDocumentStore;
import com.codereviewx.backend.rag.persistence.RagIndexJobStore;
import com.codereviewx.backend.rag.persistence.RagRepositoryStore;
import com.codereviewx.backend.rag.persistence.RagRepositoryStore.RepositoryRecord;
import com.codereviewx.backend.rag.persistence.RagIndexJobStore.SnapshotRecord;
import com.codereviewx.backend.rag.service.RagIndexJob;
import com.codereviewx.backend.review.github.GithubPrMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

@Component
@ConditionalOnProperty(prefix = "codereviewx.rag", name = "enabled", havingValue = "true")
public class RagIndexWorker {

    private static final int INDEX_VERSION = 1;

    private final RagRepositoryStore repositories;
    private final RagIndexJobStore jobs;
    private final RagDocumentStore documents;
    private final RagChunkStore chunks;
    private final RepositoryCheckoutService checkoutService;
    private final Function<CheckedOutRepository, List<RepositoryFile>> fileSource;
    private final CodeChunker chunker;
    private final EmbeddingClient embeddings;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String embeddingModel;
    private final int embeddingDimensions;
    private final ThreadPoolTaskExecutor executor;
    private final ScheduledExecutorService heartbeatExecutor;

    @Autowired
    public RagIndexWorker(RagRepositoryStore repositories, RagIndexJobStore jobs, RagDocumentStore documents,
                          RagChunkStore chunks, RepositoryCheckoutService checkoutService,
                          RepositoryFileDiscovery discovery, CodeChunker chunker, EmbeddingClient embeddings,
                          TransactionTemplate transactions, RagProperties properties,
                          ThreadPoolTaskExecutor ragIndexExecutor,
                          @Qualifier("ragHeartbeatExecutor") ScheduledExecutorService heartbeatExecutor) {
        this(repositories, jobs, documents, chunks, checkoutService, discovery::discover, chunker, embeddings,
                transactions, Clock.systemUTC(), properties.getEmbeddingModel(),
                properties.getEmbeddingDimensions(), ragIndexExecutor, heartbeatExecutor);
    }

    RagIndexWorker(RagRepositoryStore repositories, RagIndexJobStore jobs, RagDocumentStore documents,
                   RagChunkStore chunks, Function<CheckedOutRepository, List<RepositoryFile>> fileSource,
                   CodeChunker chunker, EmbeddingClient embeddings, TransactionTemplate transactions, Clock clock) {
        this(repositories, jobs, documents, chunks, null, fileSource, chunker, embeddings, transactions, clock,
                "test-model", 1024, null, null);
    }

    private RagIndexWorker(RagRepositoryStore repositories, RagIndexJobStore jobs, RagDocumentStore documents,
                           RagChunkStore chunks, RepositoryCheckoutService checkoutService,
                           Function<CheckedOutRepository, List<RepositoryFile>> fileSource, CodeChunker chunker,
                           EmbeddingClient embeddings, TransactionTemplate transactions, Clock clock,
                           String embeddingModel, int embeddingDimensions, ThreadPoolTaskExecutor executor,
                           ScheduledExecutorService heartbeatExecutor) {
        this.repositories = repositories;
        this.jobs = jobs;
        this.documents = documents;
        this.chunks = chunks;
        this.checkoutService = checkoutService;
        this.fileSource = fileSource;
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.transactions = transactions;
        this.clock = clock;
        this.embeddingModel = embeddingModel;
        this.embeddingDimensions = embeddingDimensions;
        this.executor = executor;
        this.heartbeatExecutor = heartbeatExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recoverStale();
        submitOne();
    }

    @Scheduled(fixedDelay = 5000)
    public void pollQueued() {
        recoverStale();
        submitOne();
    }

    public void submitOne() {
        if (executor != null) {
            try {
                executor.execute(this::runOne);
            } catch (TaskRejectedException ignored) { }
        }
    }

    public void runOne() {
        RagIndexJob job = transactions.execute(ignored -> jobs.claimNextQueued().orElse(null));
        if (job != null) {
            process(job);
        }
    }

    void process(RagIndexJob job) {
        RepositoryRecord repository = repositories.get(job.repositoryId()).orElseThrow();
        ScheduledFuture<?> heartbeat = null;
        try {
            heartbeat = startHeartbeat(job);
            validateCapability(job);
            List<RepositoryFile> discovered;
            String resolvedSha;
            if (checkoutService == null) {
                resolvedSha = job.requestedRef();
                discovered = fileSource.apply(null);
            } else {
                try (CheckedOutRepository checkedOut = checkoutService.checkout(metadata(repository, job.requestedRef()))) {
                    resolvedSha = checkedOut.commitSha();
                    if (!job.requestedRef().equals(resolvedSha)) {
                        throw new IllegalStateException("Repository checkout returned a different commit");
                    }
                    discovered = fileSource.apply(checkedOut);
                }
            }
            PreparedSnapshot snapshot = prepare(job, repository, resolvedSha, discovered);
            transactions.executeWithoutResult(ignored -> persist(job, repository, snapshot));
        } catch (Exception exception) {
            failIfOwned(job, repository, exception);
        } finally {
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
        }
    }

    private PreparedSnapshot prepare(RagIndexJob job, RepositoryRecord repository, String commitSha,
                                     List<RepositoryFile> files) {
        List<PreparedDocument> prepared = new ArrayList<>();
        boolean compatibleSnapshot = job.embeddingModel().equals(repository.embeddingModel())
                && job.embeddingDimensions() == repository.embeddingDimensions()
                && job.indexVersion() == repository.indexVersion();
        SnapshotRecord previousSnapshot = !compatibleSnapshot || repository.activeCommitSha() == null ? null
                : jobs.findSnapshot(repository.id(), repository.activeCommitSha(), repository.embeddingModel(),
                        repository.embeddingDimensions(), repository.indexVersion()).orElse(null);
        for (RepositoryFile file : files) {
            RagDocumentStore.DocumentRecord reusable = previousSnapshot == null ? null
                    : documents.find(previousSnapshot.id(), file.path(), file.contentHash()).orElse(null);
            if (reusable != null) {
                prepared.add(new PreparedDocument(file, reusable.id(), List.of(), List.of()));
                continue;
            }
            List<CodeChunk> codeChunks = chunker.chunk(file);
            Map<String, List<Long>> reusableChunks = previousSnapshot == null ? Map.of()
                    : chunks.findReusableChunks(previousSnapshot.id(), file.path());
            Map<String, Deque<Long>> availableByHash = new LinkedHashMap<>();
            reusableChunks.forEach((hash, ids) -> availableByHash.put(hash, new ArrayDeque<>(ids)));
            List<ReusedChunk> reused = new ArrayList<>();
            List<CodeChunk> chunksToEmbed = new ArrayList<>();
            for (CodeChunk codeChunk : codeChunks) {
                Deque<Long> available = availableByHash.get(codeChunk.contentHash());
                Long sourceChunkId = available == null ? null : available.pollFirst();
                if (sourceChunkId == null) {
                    chunksToEmbed.add(codeChunk);
                } else {
                    reused.add(new ReusedChunk(codeChunk, sourceChunkId));
                }
            }
            List<EmbeddedChunk> embedded = new ArrayList<>(chunksToEmbed.size());
            for (int start = 0; start < chunksToEmbed.size(); start += RagChunkStore.MAX_BATCH_SIZE) {
                int end = Math.min(start + RagChunkStore.MAX_BATCH_SIZE, chunksToEmbed.size());
                List<CodeChunk> batch = chunksToEmbed.subList(start, end);
                List<float[]> vectors = embeddings.embed(batch.stream().map(CodeChunk::content).toList());
                if (vectors.size() != batch.size()) {
                    throw new IllegalStateException("Embedding response count mismatch");
                }
                for (int index = 0; index < batch.size(); index++) {
                    if (vectors.get(index).length != embeddingDimensions) {
                        throw new IllegalStateException("Embedding dimensions mismatch");
                    }
                    embedded.add(new EmbeddedChunk(batch.get(index), vectors.get(index)));
                }
            }
            prepared.add(new PreparedDocument(file, null, List.copyOf(reused), List.copyOf(embedded)));
        }
        return new PreparedSnapshot(commitSha, List.copyOf(files), List.copyOf(prepared));
    }

    private void persist(RagIndexJob job, RepositoryRecord repository, PreparedSnapshot snapshot) {
        long snapshotId = jobs.createSnapshot(job.id(), repository.id(), snapshot.commitSha(), job.embeddingModel(),
                job.embeddingDimensions(), job.indexVersion());
        int chunkCount = 0;
        for (PreparedDocument prepared : snapshot.documents()) {
            long documentId = documents.insert(repository.id(), snapshotId, snapshot.commitSha(), prepared.file());
            if (prepared.sourceDocumentId() != null) {
                chunkCount += copyDocumentChunks(repository.id(), snapshotId, prepared.sourceDocumentId(), documentId,
                        snapshot.commitSha());
            } else {
                chunkCount += insertReusedChunks(repository.id(), snapshotId, prepared.reusedChunks(), documentId,
                        snapshot.commitSha());
                for (int start = 0; start < prepared.chunks().size(); start += RagChunkStore.MAX_BATCH_SIZE) {
                    int end = Math.min(start + RagChunkStore.MAX_BATCH_SIZE, prepared.chunks().size());
                    List<EmbeddedChunk> batch = prepared.chunks().subList(start, end);
                    chunks.insertBatch(repository.id(), snapshotId, documentId, snapshot.commitSha(), batch);
                    chunkCount += batch.size();
                }
            }
        }
        jobs.complete(job.id(), job.attemptCount(), snapshot.commitSha(), snapshot.files().size(),
                snapshot.files().size(), chunkCount, 0);
        repositories.activate(repository.id(), snapshot.commitSha(), job.embeddingModel(), job.embeddingDimensions(),
                job.indexVersion());
    }

    private int copyDocumentChunks(long repositoryId, long targetSnapshotId, long sourceDocumentId,
                                   long targetDocumentId, String targetCommitSha) {
        int copied = 0;
        long afterId = 0;
        while (true) {
            List<Long> sourceIds = chunks.findDocumentChunkIds(repositoryId, sourceDocumentId, afterId);
            if (sourceIds.isEmpty()) {
                return copied;
            }
            copied += chunks.copyChunks(repositoryId, targetSnapshotId, sourceIds, targetDocumentId, targetCommitSha);
            afterId = sourceIds.get(sourceIds.size() - 1);
        }
    }

    private int insertReusedChunks(long repositoryId, long snapshotId, List<ReusedChunk> reused,
                                   long targetDocumentId, String targetCommitSha) {
        for (int start = 0; start < reused.size(); start += RagChunkStore.MAX_BATCH_SIZE) {
            int end = Math.min(start + RagChunkStore.MAX_BATCH_SIZE, reused.size());
            chunks.insertReusedBatch(repositoryId, snapshotId, targetDocumentId, targetCommitSha,
                    reused.subList(start, end));
        }
        return reused.size();
    }

    private void validateCapability(RagIndexJob job) {
        if (!embeddingModel.equals(job.embeddingModel())
                || embeddingDimensions != job.embeddingDimensions()
                || INDEX_VERSION != job.indexVersion()) {
            throw new ConfigurationMismatchException();
        }
    }

    private ScheduledFuture<?> startHeartbeat(RagIndexJob job) {
        if (heartbeatExecutor == null) {
            return null;
        }
        try {
            return heartbeatExecutor.scheduleAtFixedRate(() -> {
                try {
                    jobs.heartbeat(job.id(), job.attemptCount());
                } catch (RuntimeException ignored) { }
            }, 5, 5, TimeUnit.SECONDS);
        } catch (RejectedExecutionException exception) {
            throw new HeartbeatUnavailableException();
        }
    }

    private void failIfOwned(RagIndexJob job, RepositoryRecord repository, Exception exception) {
        try {
            transactions.executeWithoutResult(ignored -> {
                String errorCode = exception instanceof ConfigurationMismatchException
                        ? "CONFIG_MISMATCH" : exception instanceof HeartbeatUnavailableException
                        ? "HEARTBEAT_UNAVAILABLE" : "INDEXING_FAILED";
                jobs.fail(job.id(), job.attemptCount(), errorCode, safeMessage(exception));
                repositories.markInitialFailure(repository.id());
            });
        } catch (RagIndexJobStore.StaleJobLeaseException ignored) { }
    }

    private void recoverStale() {
        transactions.executeWithoutResult(ignored -> jobs.recoverStale(Duration.ofMinutes(15),
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
    }

    private static GithubPrMetadata metadata(RepositoryRecord repository, String sha) {
        return new GithubPrMetadata(repository.owner(), repository.name(), 0, "RAG indexing", "system",
                repository.defaultBranch(), sha, sha, sha, "open", "", "", 0, 0, 0);
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record PreparedSnapshot(String commitSha, List<RepositoryFile> files,
                                    List<PreparedDocument> documents) {
    }

    private record PreparedDocument(RepositoryFile file, Long sourceDocumentId, List<ReusedChunk> reusedChunks,
                                    List<EmbeddedChunk> chunks) {
    }

    private static final class ConfigurationMismatchException extends IllegalStateException {
        private ConfigurationMismatchException() {
            super("Worker configuration does not match queued index tuple");
        }
    }

    private static final class HeartbeatUnavailableException extends IllegalStateException {
        private HeartbeatUnavailableException() {
            super("RAG index heartbeat is unavailable");
        }
    }
}
