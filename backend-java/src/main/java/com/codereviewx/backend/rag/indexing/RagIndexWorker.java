package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.embedding.EmbeddingClient;
import com.codereviewx.backend.rag.model.CodeChunk;
import com.codereviewx.backend.rag.model.RepositoryFile;
import com.codereviewx.backend.rag.persistence.RagChunkStore;
import com.codereviewx.backend.rag.persistence.RagChunkStore.EmbeddedChunk;
import com.codereviewx.backend.rag.persistence.RagDocumentStore;
import com.codereviewx.backend.rag.persistence.RagIndexJobStore;
import com.codereviewx.backend.rag.persistence.RagRepositoryStore;
import com.codereviewx.backend.rag.persistence.RagRepositoryStore.RepositoryRecord;
import com.codereviewx.backend.rag.service.RagIndexJob;
import com.codereviewx.backend.review.github.GithubPrMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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

    @Autowired
    public RagIndexWorker(RagRepositoryStore repositories, RagIndexJobStore jobs, RagDocumentStore documents,
                          RagChunkStore chunks, RepositoryCheckoutService checkoutService,
                          RepositoryFileDiscovery discovery, CodeChunker chunker, EmbeddingClient embeddings,
                          TransactionTemplate transactions, RagProperties properties,
                          ThreadPoolTaskExecutor ragIndexExecutor) {
        this(repositories, jobs, documents, chunks, checkoutService, discovery::discover, chunker, embeddings,
                transactions, Clock.systemUTC(), properties.getEmbeddingModel(),
                properties.getEmbeddingDimensions(), ragIndexExecutor);
    }

    RagIndexWorker(RagRepositoryStore repositories, RagIndexJobStore jobs, RagDocumentStore documents,
                   RagChunkStore chunks, Function<CheckedOutRepository, List<RepositoryFile>> fileSource,
                   CodeChunker chunker, EmbeddingClient embeddings, TransactionTemplate transactions, Clock clock) {
        this(repositories, jobs, documents, chunks, null, fileSource, chunker, embeddings, transactions, clock,
                "test-model", 1024, null);
    }

    private RagIndexWorker(RagRepositoryStore repositories, RagIndexJobStore jobs, RagDocumentStore documents,
                           RagChunkStore chunks, RepositoryCheckoutService checkoutService,
                           Function<CheckedOutRepository, List<RepositoryFile>> fileSource, CodeChunker chunker,
                           EmbeddingClient embeddings, TransactionTemplate transactions, Clock clock,
                           String embeddingModel, int embeddingDimensions, ThreadPoolTaskExecutor executor) {
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
            executor.execute(this::runOne);
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
        try {
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
            PreparedSnapshot snapshot = prepare(repository, resolvedSha, discovered);
            transactions.executeWithoutResult(ignored -> persist(job, repository, snapshot));
        } catch (Exception exception) {
            transactions.executeWithoutResult(ignored -> {
                jobs.fail(job.id(), "INDEXING_FAILED", safeMessage(exception));
                repositories.markInitialFailure(repository.id());
            });
        }
    }

    private PreparedSnapshot prepare(RepositoryRecord repository, String commitSha, List<RepositoryFile> files) {
        List<PreparedDocument> prepared = new ArrayList<>();
        boolean compatibleSnapshot = embeddingModel.equals(repository.embeddingModel())
                && embeddingDimensions == repository.embeddingDimensions()
                && INDEX_VERSION == repository.indexVersion();
        String previousCommit = compatibleSnapshot ? repository.activeCommitSha() : null;
        for (RepositoryFile file : files) {
            RagDocumentStore.DocumentRecord reusable = previousCommit == null ? null
                    : documents.find(repository.id(), previousCommit, file.path(), file.contentHash()).orElse(null);
            if (reusable != null) {
                prepared.add(new PreparedDocument(file, reusable.id(), List.of()));
                continue;
            }
            List<CodeChunk> codeChunks = chunker.chunk(file);
            List<EmbeddedChunk> embedded = new ArrayList<>(codeChunks.size());
            for (int start = 0; start < codeChunks.size(); start += RagChunkStore.MAX_BATCH_SIZE) {
                int end = Math.min(start + RagChunkStore.MAX_BATCH_SIZE, codeChunks.size());
                List<CodeChunk> batch = codeChunks.subList(start, end);
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
            prepared.add(new PreparedDocument(file, null, List.copyOf(embedded)));
        }
        return new PreparedSnapshot(commitSha, List.copyOf(files), List.copyOf(prepared));
    }

    private void persist(RagIndexJob job, RepositoryRecord repository, PreparedSnapshot snapshot) {
        int chunkCount = 0;
        for (PreparedDocument prepared : snapshot.documents()) {
            long documentId = documents.insert(repository.id(), snapshot.commitSha(), prepared.file());
            if (prepared.sourceDocumentId() != null) {
                chunkCount += chunks.copyDocumentChunks(repository.id(), prepared.sourceDocumentId(), documentId,
                        snapshot.commitSha());
            } else {
                for (int start = 0; start < prepared.chunks().size(); start += RagChunkStore.MAX_BATCH_SIZE) {
                    int end = Math.min(start + RagChunkStore.MAX_BATCH_SIZE, prepared.chunks().size());
                    List<EmbeddedChunk> batch = prepared.chunks().subList(start, end);
                    chunks.insertBatch(repository.id(), documentId, snapshot.commitSha(), batch);
                    chunkCount += batch.size();
                }
            }
        }
        jobs.complete(job.id(), snapshot.commitSha(), snapshot.files().size(), snapshot.files().size(), chunkCount, 0);
        repositories.activate(repository.id(), snapshot.commitSha(), embeddingModel, embeddingDimensions, INDEX_VERSION);
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

    private record PreparedDocument(RepositoryFile file, Long sourceDocumentId, List<EmbeddedChunk> chunks) {
    }
}
