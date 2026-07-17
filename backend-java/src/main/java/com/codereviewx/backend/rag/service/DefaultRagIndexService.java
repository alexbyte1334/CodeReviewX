package com.codereviewx.backend.rag.service;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.indexing.RagIndexWorker;
import com.codereviewx.backend.rag.persistence.RagIndexJobStore;
import com.codereviewx.backend.rag.persistence.RagRepositoryStore;
import com.codereviewx.backend.rag.persistence.RagRepositoryStore.RepositoryRecord;
import com.codereviewx.backend.review.github.GithubPrMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(prefix = "codereviewx.rag", name = "enabled", havingValue = "true")
public class DefaultRagIndexService implements RagIndexService {

    private static final Pattern REPOSITORY_PART = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern COMMIT_SHA = Pattern.compile("[0-9a-f]{40}");

    private final RagRepositoryStore repositories;
    private final RagIndexJobStore jobs;
    private final RagIndexWorker worker;
    private final TaskExecutor executor;
    private final TransactionTemplate transactions;
    private final RagProperties properties;

    @Autowired
    public DefaultRagIndexService(RagRepositoryStore repositories, RagIndexJobStore jobs, RagIndexWorker worker,
                                  @Qualifier("ragIndexExecutor") TaskExecutor executor,
                                  TransactionTemplate transactions, RagProperties properties) {
        this(repositories, jobs, worker, executor, transactions, properties, Clock.systemUTC());
    }

    public DefaultRagIndexService(RagRepositoryStore repositories, RagIndexJobStore jobs, RagIndexWorker worker,
                                  TaskExecutor executor, TransactionTemplate transactions, RagProperties properties,
                                  Clock clock) {
        this.repositories = repositories;
        this.jobs = jobs;
        this.worker = worker;
        this.executor = executor;
        this.transactions = new TransactionTemplate(Objects.requireNonNull(
                transactions.getTransactionManager(), "transactionManager"));
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.properties = properties;
        Objects.requireNonNull(clock, "clock");
    }

    @Override
    public RagIndexResolution ensureIndexed(GithubPrMetadata metadata) {
        validate(metadata);
        ResolutionTransaction result = transactions.execute(ignored -> {
            RepositoryRecord repository = repositories.ensure("github", metadata.owner(), metadata.repo(),
                    "https://github.com/" + metadata.owner() + "/" + metadata.repo() + ".git",
                    metadata.baseRef(), properties.getEmbeddingModel(), properties.getEmbeddingDimensions(),
                    RagProperties.INDEX_VERSION);
            RagIndexJob ready = jobs.findReadySnapshot(repository.id(), metadata.headSha(),
                    properties.getEmbeddingModel(), properties.getEmbeddingDimensions(), RagProperties.INDEX_VERSION).orElse(null);
            if (ready != null) {
                return new ResolutionTransaction(new RagIndexResolution(repository.id(), ready.id(),
                        metadata.headSha(), RagIndexResolution.Status.READY), false);
            }
            long jobId = jobs.createOrGetActive(repository.id(), metadata.headSha(), "PULL_REQUEST",
                    properties.getEmbeddingModel(), properties.getEmbeddingDimensions(), RagProperties.INDEX_VERSION)
                    .jobId();
            return new ResolutionTransaction(new RagIndexResolution(repository.id(), jobId,
                    metadata.headSha(), RagIndexResolution.Status.QUEUED), true);
        });
        if (result.submit()) {
            try {
                executor.execute(worker::runOne);
            } catch (TaskRejectedException ignored) { }
        }
        return result.resolution();
    }

    @Override
    public RagIndexJob getJob(long jobId) {
        return jobs.get(jobId).orElseThrow(() -> new IllegalArgumentException("RAG index job not found"));
    }

    private static void validate(GithubPrMetadata metadata) {
        if (metadata == null || !validPart(metadata.owner()) || !validPart(metadata.repo())
                || metadata.baseRef() == null || metadata.baseRef().isBlank()
                || metadata.headSha() == null || !COMMIT_SHA.matcher(metadata.headSha()).matches()) {
            throw new IllegalArgumentException("Repository metadata is invalid");
        }
    }

    private static boolean validPart(String value) {
        return value != null && REPOSITORY_PART.matcher(value).matches() && !value.equals(".") && !value.equals("..");
    }

    private record ResolutionTransaction(RagIndexResolution resolution, boolean submit) {
    }
}
