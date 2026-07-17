package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.controller.RepositoryIndexController;
import com.codereviewx.backend.rag.embedding.EmbeddingClient;
import com.codereviewx.backend.rag.model.Language;
import com.codereviewx.backend.rag.model.RepositoryFile;
import com.codereviewx.backend.rag.persistence.RagChunkStore;
import com.codereviewx.backend.rag.persistence.RagDocumentStore;
import com.codereviewx.backend.rag.persistence.RagIndexJobStore;
import com.codereviewx.backend.rag.persistence.RagRepositoryStore;
import com.codereviewx.backend.rag.service.DefaultRagIndexService;
import com.codereviewx.backend.rag.service.RagIndexJob;
import com.codereviewx.backend.rag.service.RagIndexResolution;
import com.codereviewx.backend.review.github.GithubPrMetadata;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RagIndexWorkerIntegrationTest {

    private static final String SHA_ONE = "1".repeat(40);
    private static final String SHA_TWO = "2".repeat(40);
    @TempDir Path tempDir;
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private RagRepositoryStore repositories;
    private RagIndexJobStore jobs;
    private RagDocumentStore documents;
    private RagChunkStore chunks;
    private RagProperties properties;
    private RecordingEmbeddingClient embeddings;
    private MutableFiles files;
    private RagIndexWorker worker;
    private DefaultRagIndexService service;

    @BeforeAll
    void startPostgres() {
        postgres.start();
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration", "classpath:db/rag/postgresql")
                .initSql("CREATE SCHEMA IF NOT EXISTS flyway_compat; "
                        + "DO $$ BEGIN CREATE DOMAIN flyway_compat.CLOB AS TEXT; "
                        + "EXCEPTION WHEN duplicate_object THEN NULL; END $$; "
                        + "SET search_path TO public, flyway_compat")
                .load()
                .migrate();
        org.springframework.jdbc.datasource.DriverManagerDataSource dataSource =
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE rag_chunk, rag_document, rag_index_job, rag_repository RESTART IDENTITY CASCADE");
        repositories = new RagRepositoryStore(jdbc);
        jobs = new RagIndexJobStore(jdbc);
        documents = new RagDocumentStore(jdbc);
        chunks = new RecordingChunkStore(jdbc);
        properties = new RagProperties();
        properties.setEmbeddingModel("test-model");
        properties.setEmbeddingDimensions(1024);
        properties.setEmbeddingBatchSize(250);
        embeddings = new RecordingEmbeddingClient();
        files = new MutableFiles();
        worker = new RagIndexWorker(repositories, jobs, documents, chunks, ignored -> files.current(),
                new LineWindowCodeChunker(), embeddings, transactions, Clock.systemUTC());
        TaskExecutor pausedExecutor = runnable -> { };
        service = new DefaultRagIndexService(repositories, jobs, worker, pausedExecutor, transactions,
                properties, Clock.systemUTC());
    }

    @Test
    void enforcesStateMachineSingleRunningAndReadyIdempotency() {
        files.set(file("src/A.java", "class A {}"));
        RagIndexResolution first = service.ensureIndexed(metadata(SHA_ONE));
        RagIndexResolution second = service.ensureIndexed(metadata(SHA_TWO));

        RagIndexJob claimed = transactions.execute(ignored -> jobs.claimNextQueued().orElseThrow());
        assertThat(claimed.status()).isEqualTo(RagIndexJob.Status.RUNNING);
        Optional<RagIndexJob> blockedClaim = transactions.execute(ignored -> jobs.claimNextQueued());
        assertThat(blockedClaim).isEmpty();
        assertThatThrownBy(() -> jobs.transition(claimed.id(), RagIndexJob.Status.READY,
                RagIndexJob.Status.RUNNING, claimed.attemptCount()))
                .isInstanceOf(IllegalStateException.class);

        worker.process(claimed);
        assertThat(service.getJob(first.jobId()).status()).isEqualTo(RagIndexJob.Status.READY);
        RagIndexResolution duplicate = service.ensureIndexed(metadata(SHA_ONE));
        assertThat(duplicate.status()).isEqualTo(RagIndexResolution.Status.READY);
        assertThat(duplicate.jobId()).isEqualTo(first.jobId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_index_job", Integer.class)).isEqualTo(2);
        assertThat(service.getJob(second.jobId()).status()).isEqualTo(RagIndexJob.Status.QUEUED);
    }

    @Test
    void resolvesMainBeforeCheckoutAndPersistsRequestedAndResolvedRefs() throws Exception {
        RepositoryFixture fixture = createRepository();
        RagIndexWorker checkoutWorker = workerWithCheckout(fixture);
        long repositoryId = repositories.ensure("github", "owner", "repo", "https://github.com/owner/repo.git",
                "main", "test-model", 1024, RagProperties.INDEX_VERSION).id();
        long jobId = jobs.createOrGetActive(repositoryId, "main", "API", "test-model", 1024,
                RagProperties.INDEX_VERSION).jobId();

        RagIndexJob claimed = transactions.execute(ignored -> jobs.claimNextQueued().orElseThrow());
        checkoutWorker.process(claimed);

        RagIndexJob completed = jobs.get(jobId).orElseThrow();
        assertThat(completed.status()).isEqualTo(RagIndexJob.Status.READY);
        assertThat(completed.requestedRef()).isEqualTo("main");
        assertThat(completed.resolvedCommitSha()).isEqualTo(fixture.commitSha()).matches("[0-9a-f]{40}");
        assertThat(jdbc.queryForObject("SELECT active_commit_sha FROM rag_repository WHERE id=?", String.class,
                repositoryId)).isEqualTo(fixture.commitSha());
        assertThat(jdbc.queryForObject("SELECT commit_sha FROM rag_index_snapshot WHERE job_id=?", String.class,
                jobId)).isEqualTo(fixture.commitSha());
    }

    @Test
    void missingBranchFailsSafelyWithoutSnapshot() throws Exception {
        RepositoryFixture fixture = createRepository();
        RagIndexWorker checkoutWorker = workerWithCheckout(fixture);
        long repositoryId = repositories.ensure("github", "owner", "repo", "https://github.com/owner/repo.git",
                "main", "test-model", 1024, RagProperties.INDEX_VERSION).id();
        long jobId = jobs.createOrGetActive(repositoryId, "missing", "API", "test-model", 1024,
                RagProperties.INDEX_VERSION).jobId();

        RagIndexJob claimed = transactions.execute(ignored -> jobs.claimNextQueued().orElseThrow());
        checkoutWorker.process(claimed);

        RagIndexJob failed = jobs.get(jobId).orElseThrow();
        assertThat(failed.status()).isEqualTo(RagIndexJob.Status.FAILED);
        assertThat(failed.errorCode()).isEqualTo("CHECKOUT_FAILED");
        assertThat(failed.errorMessage()).isEqualTo("Repository checkout failed");
        assertThat(failed.resolvedCommitSha()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_index_snapshot WHERE job_id=?", Integer.class,
                jobId)).isZero();
    }

    @Test
    void directCommitShaStillIndexesThroughShaOnlyCheckout() throws Exception {
        RepositoryFixture fixture = createRepository();
        RagIndexWorker checkoutWorker = workerWithCheckout(fixture);
        long repositoryId = repositories.ensure("github", "owner", "repo", "https://github.com/owner/repo.git",
                "main", "test-model", 1024, RagProperties.INDEX_VERSION).id();
        long jobId = jobs.createOrGetActive(repositoryId, fixture.commitSha(), "API", "test-model", 1024,
                RagProperties.INDEX_VERSION).jobId();

        RagIndexJob claimed = transactions.execute(ignored -> jobs.claimNextQueued().orElseThrow());
        checkoutWorker.process(claimed);

        RagIndexJob completed = jobs.get(jobId).orElseThrow();
        assertThat(completed.status()).isEqualTo(RagIndexJob.Status.READY);
        assertThat(completed.requestedRef()).isEqualTo(fixture.commitSha());
        assertThat(completed.resolvedCommitSha()).isEqualTo(fixture.commitSha());
    }

    @Test
    void incrementallyReusesUnchangedFilesExcludesDeletesAndLimitsBatches() {
        files.set(file("same.java", "class Same {}"), file("gone.java", "class Gone {}"));
        index(metadata(SHA_ONE));
        embeddings.clear();

        String large = java.util.stream.IntStream.range(0, 205)
                .mapToObj(index -> "line" + index + "=" + "x".repeat(7900))
                .reduce((left, right) -> left + "\n" + right).orElseThrow();
        files.set(file("same.java", "class Same {}"), file("large.java", large));
        index(metadata(SHA_TWO));

        assertThat(embeddings.batchSizes()).allMatch(size -> size <= 100);
        assertThat(((RecordingChunkStore) chunks).batchSizes()).allMatch(size -> size <= 100);
        assertThat(embeddings.totalInputs()).isEqualTo(205);
        assertThat(count("rag_document", SHA_TWO, "same.java")).isEqualTo(1);
        assertThat(count("rag_document", SHA_TWO, "gone.java")).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM rag_chunk newer
                JOIN rag_chunk older ON older.commit_sha = ? AND newer.commit_sha = ?
                  AND older.path = newer.path AND older.content_hash = newer.content_hash
                  AND older.embedding = newer.embedding
                WHERE newer.path = 'same.java'
                """, Integer.class, SHA_ONE, SHA_TWO)).isEqualTo(1);
    }

    @Test
    void embedsOnlyChangedChunksWhenFileKeepsStableChunkPositions() {
        files.set(file("windowed.java", windowedSource(-1)));
        index(metadata(SHA_ONE));
        embeddings.clear();

        files.set(file("windowed.java", windowedSource(99)));
        index(metadata(SHA_TWO));

        assertThat(embeddings.totalInputs()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*)
                FROM rag_chunk newer
                JOIN rag_chunk older
                  ON older.repository_id = newer.repository_id
                 AND older.commit_sha = ?
                 AND newer.commit_sha = ?
                 AND older.path = newer.path
                 AND older.start_line = newer.start_line
                 AND older.end_line = newer.end_line
                 AND older.content_hash = newer.content_hash
                 AND older.embedding = newer.embedding
                WHERE newer.path = 'windowed.java'
                """, Integer.class, SHA_ONE, SHA_TWO)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM rag_chunk WHERE commit_sha=? AND path='windowed.java'
                """, Integer.class, SHA_TWO)).isEqualTo(3);
    }

    @Test
    void reusesHistoricalReadySnapshotWithExactIndexTuple() {
        files.set(file("history.java", "class HistoryOne {}"));
        RagIndexResolution first = indexAndResolve(metadata(SHA_ONE));
        files.set(file("history.java", "class HistoryTwo {}"));
        index(metadata(SHA_TWO));

        RagIndexResolution historical = service.ensureIndexed(metadata(SHA_ONE));

        assertThat(historical.status()).isEqualTo(RagIndexResolution.Status.READY);
        assertThat(historical.jobId()).isEqualTo(first.jobId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_index_job", Integer.class)).isEqualTo(2);
    }

    @Test
    void doesNotReuseHistoricalSnapshotWhenModelOrVersionDiffers() {
        files.set(file("tuple.java", "class TupleOne {}"));
        index(metadata(SHA_ONE));
        files.set(file("tuple.java", "class TupleTwo {}"));
        index(metadata(SHA_TWO));

        properties.setEmbeddingModel("different-model");
        RagIndexResolution modelMismatch = service.ensureIndexed(metadata(SHA_ONE));
        assertThat(modelMismatch.status()).isEqualTo(RagIndexResolution.Status.QUEUED);

        properties.setEmbeddingModel("test-model");
        jdbc.update("UPDATE rag_index_snapshot SET index_version=2 WHERE commit_sha=?", SHA_ONE);
        RagIndexResolution versionMismatch = service.ensureIndexed(metadata(SHA_ONE));

        assertThat(versionMismatch.status()).isEqualTo(RagIndexResolution.Status.QUEUED);
        assertThat(jdbc.queryForObject("""
                SELECT embedding_dimensions FROM rag_index_snapshot WHERE commit_sha=?
                """, Integer.class, SHA_ONE)).isEqualTo(1024);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_index_job", Integer.class)).isEqualTo(4);
    }

    @Test
    void copiesUnchangedLargeDocumentInBatchesOfAtMostOneHundred() {
        files.set(file("unchanged-large.java", largeChunkSource(205)));
        index(metadata(SHA_ONE));
        embeddings.clear();
        RecordingChunkStore recordingChunks = (RecordingChunkStore) chunks;
        recordingChunks.clearCopyBatchSizes();

        index(metadata(SHA_TWO));

        assertThat(embeddings.totalInputs()).isZero();
        assertThat(recordingChunks.copyBatchSizes()).containsExactly(100, 100, 5);
        assertThat(recordingChunks.copyBatchSizes()).allMatch(size -> size <= 100);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM rag_chunk WHERE commit_sha=? AND path='unchanged-large.java'
                """, Integer.class, SHA_TWO)).isEqualTo(205);
    }

    @Test
    void rebuildsSameCommitForDifferentModelWithoutLosingEitherReadyTuple() {
        files.set(file("multi-model.java", "class MultiModel {}"));
        RagIndexResolution modelA = indexAndResolve(metadata(SHA_ONE));
        properties.setEmbeddingModel("model-b");
        ReflectionTestUtils.setField(worker, "embeddingModel", "model-b");

        RagIndexResolution queued = service.ensureIndexed(metadata(SHA_ONE));
        RagIndexJob claimed = transactions.execute(ignored -> jobs.claimNextQueued().orElseThrow());
        worker.process(claimed);

        assertThat(jobs.get(queued.jobId()).orElseThrow().status()).isEqualTo(RagIndexJob.Status.READY);
        assertThat(service.ensureIndexed(metadata(SHA_ONE)).jobId()).isEqualTo(queued.jobId());
        properties.setEmbeddingModel("test-model");
        assertThat(service.ensureIndexed(metadata(SHA_ONE)).jobId()).isEqualTo(modelA.jobId());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM rag_index_snapshot WHERE repository_id=? AND commit_sha=?
                """, Integer.class, queued.repositoryId(), SHA_ONE)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM rag_document WHERE repository_id=? AND commit_sha=? AND path='multi-model.java'
                """, Integer.class, queued.repositoryId(), SHA_ONE)).isEqualTo(2);
    }

    @Test
    void reusesShiftedChunkContentOnceAndWritesNewLineMetadata() {
        files.set(file("shifted.java", shiftedSource("A", "B", "C")));
        index(metadata(SHA_ONE));
        embeddings.clear();

        files.set(file("shifted.java", shiftedSource("X", "A", "A", "B", "C")));
        index(metadata(SHA_TWO));

        assertThat(embeddings.totalInputs()).isEqualTo(2);
        assertThat(jdbc.queryForList("""
                SELECT start_line FROM rag_chunk
                WHERE commit_sha=? AND path='shifted.java'
                ORDER BY start_line
                """, Integer.class, SHA_TWO)).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void failsQueuedJobWhenWorkerCannotServePersistedModelTuple() {
        files.set(file("stable.java", "class Stable {}"));
        index(metadata(SHA_ONE));
        files.set(file("next.java", "class Next {}"));
        RagIndexResolution queued = service.ensureIndexed(metadata(SHA_TWO));
        ReflectionTestUtils.setField(worker, "embeddingModel", "model-b");

        RagIndexJob claimed = transactions.execute(ignored -> jobs.claimNextQueued().orElseThrow());
        worker.process(claimed);

        RagIndexJob failed = jobs.get(queued.jobId()).orElseThrow();
        assertThat(failed.status()).isEqualTo(RagIndexJob.Status.FAILED);
        assertThat(failed.errorCode()).isEqualTo("CONFIG_MISMATCH");
        assertThat(jdbc.queryForObject("SELECT embedding_model FROM rag_index_job WHERE id=?",
                String.class, queued.jobId())).isEqualTo("test-model");
        assertThat(jdbc.queryForObject("SELECT active_commit_sha FROM rag_repository", String.class))
                .isEqualTo(SHA_ONE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_index_snapshot", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentExactTupleEnsureReturnsOneActiveJobAndTerminalStateAllowsAnother() throws Exception {
        files.set(file("concurrent.java", "class Concurrent {}"));
        CountDownLatch callersReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<RagIndexResolution> ensure = () -> {
                callersReady.countDown();
                start.await();
                return service.ensureIndexed(metadata(SHA_ONE));
            };
            Future<RagIndexResolution> firstFuture = callers.submit(ensure);
            Future<RagIndexResolution> secondFuture = callers.submit(ensure);
            callersReady.await();
            start.countDown();

            RagIndexResolution first = firstFuture.get();
            RagIndexResolution second = secondFuture.get();
            assertThat(second.jobId()).isEqualTo(first.jobId());
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM rag_index_job WHERE status IN ('QUEUED','RUNNING')
                    """, Integer.class)).isEqualTo(1);

            jdbc.update("UPDATE rag_index_job SET status='FAILED', finished_at=CURRENT_TIMESTAMP WHERE id=?",
                    first.jobId());
            RagIndexResolution replacement = service.ensureIndexed(metadata(SHA_ONE));
            assertThat(replacement.jobId()).isNotEqualTo(first.jobId());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_index_job", Integer.class)).isEqualTo(2);
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void concurrentStoreEnqueueReportsOneCreatedAndOneActive() throws Exception {
        long repositoryId = repositories.ensure("github", "owner", "repo", "https://github.com/owner/repo.git",
                "main", "test-model", 1024, 1).id();
        CountDownLatch callersReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Object> enqueue = () -> {
                callersReady.countDown();
                start.await();
                return jobs.createOrGetActive(repositoryId, SHA_ONE, "API", "test-model", 1024, 1);
            };
            Future<Object> firstFuture = callers.submit(enqueue);
            Future<Object> secondFuture = callers.submit(enqueue);
            callersReady.await();
            start.countDown();

            Object first = firstFuture.get();
            Object second = secondFuture.get();
            assertThat(first).hasFieldOrProperty("created").hasFieldOrProperty("jobId");
            assertThat(second).hasFieldOrProperty("created").hasFieldOrProperty("jobId");
            assertThat(List.of(created(first), created(second))).containsExactlyInAnyOrder(true, false);
            assertThat(jobId(first)).isEqualTo(jobId(second));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_index_job", Integer.class)).isEqualTo(1);
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void branchIndexReusesOlderReadyJobFromCurrentTupleWhenOldTupleFailedLater() {
        TupleOrderingFixture fixture = tupleOrderingFixture();
        RepositoryIndexController controller = new RepositoryIndexController(
                repositories, jobs, service, properties, true);

        var response = controller.index(new RepositoryIndexController.Request(
                "https://github.com/owner/repo", "main"));

        assertThat(response.getData().status()).isEqualTo("READY");
        assertThat(response.getData().jobId()).isEqualTo(fixture.currentReadyJobId());
    }

    @Test
    void branchStatusReturnsOlderReadyJobFromCurrentTupleWhenOldTupleFailedLater() {
        TupleOrderingFixture fixture = tupleOrderingFixture();
        RepositoryIndexController controller = new RepositoryIndexController(
                repositories, jobs, service, properties, true);

        var response = controller.status("owner", "repo", null, "main");

        assertThat(response.getData().status()).isEqualTo("READY");
        assertThat(response.getData().commitSha()).isEqualTo(SHA_ONE);
        assertThat(response.getData().indexedChunks()).isEqualTo(7);
        assertThat(fixture.oldFailedJobId()).isGreaterThan(fixture.currentReadyJobId());
    }

    @Test
    void activeJobCompletingBetweenConflictAndLookupRetriesInsteadOfFailing() throws Exception {
        long repositoryId = repositories.ensure("github", "owner", "repo", "https://github.com/owner/repo.git",
                "main", "test-model", 1024, 1).id();
        long activeJobId = jobs.createOrGetActive(repositoryId, SHA_ONE, "API", "test-model", 1024, 1).jobId();
        CountDownLatch insertConflictObserved = new CountDownLatch(1);
        CountDownLatch allowActiveLookup = new CountDownLatch(1);
        AtomicBoolean interceptConflict = new AtomicBoolean(true);
        JdbcTemplate racingJdbc = new JdbcTemplate(jdbc.getDataSource()) {
            @Override
            public <T> List<T> query(String sql, org.springframework.jdbc.core.RowMapper<T> rowMapper,
                                     Object... args) {
                List<T> result = super.query(sql, rowMapper, args);
                if (sql.contains("INSERT INTO rag_index_job") && result.isEmpty()
                        && interceptConflict.compareAndSet(true, false)) {
                    insertConflictObserved.countDown();
                    await(allowActiveLookup);
                }
                return result;
            }
        };
        RagIndexJobStore racingJobs = new RagIndexJobStore(racingJdbc);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<Object> result = caller.submit(() -> racingJobs.createOrGetActive(
                    repositoryId, SHA_ONE, "API", "test-model", 1024, 1));
            assertThat(await(insertConflictObserved)).isTrue();
            jdbc.update("UPDATE rag_index_job SET status='FAILED', finished_at=CURRENT_TIMESTAMP WHERE id=?",
                    activeJobId);
            allowActiveLookup.countDown();

            assertThatCode(result::get).doesNotThrowAnyException();
            Object replacement = result.get();
            assertThat(replacement).hasFieldOrPropertyWithValue("created", true);
            assertThat(jobId(replacement)).isNotEqualTo(activeJobId);
        } finally {
            caller.shutdownNow();
        }
    }

    @Test
    void staleAttemptCannotPersistOrCompleteAfterRecoveryAndReclaim() {
        files.set(file("fenced.java", "class Fenced {}"));
        RagIndexResolution queued = service.ensureIndexed(metadata(SHA_ONE));
        RagIndexJob firstAttempt = transactions.execute(ignored -> jobs.claimNextQueued().orElseThrow());
        jdbc.update("UPDATE rag_index_job SET started_at=?, heartbeat_at=? WHERE id=?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(16),
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(16), queued.jobId());
        transactions.executeWithoutResult(ignored -> jobs.recoverStale(Duration.ofMinutes(15),
                LocalDateTime.now(ZoneOffset.UTC)));
        RagIndexJob secondAttempt = transactions.execute(ignored -> jobs.claimNextQueued().orElseThrow());

        worker.process(firstAttempt);

        RagIndexJob stillOwnedBySecond = jobs.get(queued.jobId()).orElseThrow();
        assertThat(stillOwnedBySecond.status()).isEqualTo(RagIndexJob.Status.RUNNING);
        assertThat(stillOwnedBySecond.attemptCount()).isEqualTo(secondAttempt.attemptCount());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_index_snapshot", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_document", Integer.class)).isZero();

        worker.process(secondAttempt);
        assertThat(jobs.get(queued.jobId()).orElseThrow().status()).isEqualTo(RagIndexJob.Status.READY);
    }

    @Test
    void recentHeartbeatPreventsRecoveryOfRunningLease() {
        service.ensureIndexed(metadata(SHA_ONE));
        RagIndexJob claimed = transactions.execute(ignored -> jobs.claimNextQueued().orElseThrow());
        jdbc.update("UPDATE rag_index_job SET started_at=?, heartbeat_at=CURRENT_TIMESTAMP WHERE id=?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(16), claimed.id());

        int recovered = transactions.execute(ignored -> jobs.recoverStale(Duration.ofMinutes(15),
                LocalDateTime.now(ZoneOffset.UTC)));

        assertThat(recovered).isZero();
        assertThat(jobs.get(claimed.id()).orElseThrow().status()).isEqualTo(RagIndexJob.Status.RUNNING);
    }

    @Test
    void rejectedBestEffortWakeupsLeaveQueuedJobAndDoNotBreakScheduler() {
        TaskExecutor rejecting = runnable -> {
            throw new TaskRejectedException("saturated");
        };
        DefaultRagIndexService rejectingService = new DefaultRagIndexService(
                repositories, jobs, worker, rejecting, transactions, properties, Clock.systemUTC());

        RagIndexResolution queued = rejectingService.ensureIndexed(metadata(SHA_ONE));

        assertThat(queued.status()).isEqualTo(RagIndexResolution.Status.QUEUED);
        assertThat(jobs.get(queued.jobId())).isPresent();
        ThreadPoolTaskExecutor rejectingPool = new ThreadPoolTaskExecutor() {
            @Override
            public void execute(Runnable task) {
                throw new TaskRejectedException("saturated");
            }
        };
        ReflectionTestUtils.setField(worker, "executor", rejectingPool);
        assertThatCode(worker::submitOne).doesNotThrowAnyException();
    }

    @Test
    void commitsQueuedJobBeforeDispatchingWorkerInsideOuterTransaction() {
        files.set(file("src/Visible.java", "class Visible {}"));
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        CountDownLatch workerFinished = new CountDownLatch(1);
        TaskExecutor asynchronous = runnable -> executorService.execute(() -> {
            try {
                runnable.run();
            } finally {
                workerFinished.countDown();
            }
        });
        DefaultRagIndexService transactionalService = new DefaultRagIndexService(
                repositories, jobs, worker, asynchronous, transactions, properties, Clock.systemUTC());

        try {
            transactions.executeWithoutResult(outer -> {
                RagIndexResolution queued = transactionalService.ensureIndexed(metadata(SHA_ONE));
                assertThat(await(workerFinished)).isTrue();
                assertThat(jobs.get(queued.jobId()).orElseThrow().status()).isEqualTo(RagIndexJob.Status.READY);
            });
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void rejectedHeartbeatSchedulingNeverLeavesClaimedJobRunning() {
        files.set(file("shutdown.java", "class Shutdown {}"));
        RagIndexResolution queued = service.ensureIndexed(metadata(SHA_ONE));
        ScheduledExecutorService stoppedHeartbeat = Executors.newSingleThreadScheduledExecutor();
        stoppedHeartbeat.shutdownNow();
        ReflectionTestUtils.setField(worker, "heartbeatExecutor", stoppedHeartbeat);

        assertThatCode(worker::runOne).doesNotThrowAnyException();

        RagIndexJob failed = jobs.get(queued.jobId()).orElseThrow();
        assertThat(failed.status()).isEqualTo(RagIndexJob.Status.FAILED);
        assertThat(failed.errorCode()).isEqualTo("HEARTBEAT_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_index_snapshot", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_document", Integer.class)).isZero();
    }

    @Test
    void forcedShutdownRequeuesInterruptedWorkerAttemptWithoutOverwritingReadySnapshot() throws Exception {
        files.set(file("stable.java", "class Stable {}"));
        index(metadata(SHA_ONE));

        files.set(file("next.java", "class Next {}"));
        RagIndexResolution queued = service.ensureIndexed(metadata(SHA_TWO));
        InterruptibleEmbeddingClient blockedEmbeddings = new InterruptibleEmbeddingClient();
        ReflectionTestUtils.setField(worker, "embeddings", blockedEmbeddings);

        RagIndexLifecycleCoordinator lifecycle = (RagIndexLifecycleCoordinator)
                ReflectionTestUtils.getField(worker, "lifecycle");
        RagIndexTaskExecutor executor = new RagIndexTaskExecutor(lifecycle, Duration.ofMillis(100));
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(20);
        executor.initialize();
        ReflectionTestUtils.setField(worker, "executor", executor);

        worker.submitOne();
        assertThat(blockedEmbeddings.started.await(1, TimeUnit.SECONDS)).isTrue();

        executor.shutdown();

        assertThat(blockedEmbeddings.interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        awaitShutdownRequeued(queued.jobId());
        assertThat(jdbc.queryForObject("SELECT active_commit_sha FROM rag_repository", String.class))
                .isEqualTo(SHA_ONE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_index_snapshot", Integer.class)).isEqualTo(1);
    }

    private void awaitShutdownRequeued(long jobId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        RagIndexJob lastObserved = null;
        while (System.nanoTime() < deadline) {
            lastObserved = jobs.get(jobId).orElseThrow();
            if (lastObserved.status() == RagIndexJob.Status.QUEUED
                    && "SHUTDOWN_REQUEUED".equals(lastObserved.errorCode())) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for shutdown requeue; last job=" + lastObserved);
    }

    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for index worker", exception);
        }
    }

    private TupleOrderingFixture tupleOrderingFixture() {
        long repositoryId = repositories.ensure("github", "owner", "repo", "https://github.com/owner/repo.git",
                "main", properties.getEmbeddingModel(), properties.getEmbeddingDimensions(),
                RagProperties.INDEX_VERSION).id();
        long currentReadyJobId = jobs.createOrGetActive(repositoryId, "main", "API",
                properties.getEmbeddingModel(), properties.getEmbeddingDimensions(), RagProperties.INDEX_VERSION)
                .jobId();
        jobs.createSnapshot(currentReadyJobId, repositoryId, SHA_ONE, properties.getEmbeddingModel(),
                properties.getEmbeddingDimensions(), RagProperties.INDEX_VERSION);
        jdbc.update("""
                UPDATE rag_index_job
                SET status='READY', resolved_commit_sha=?, indexed_chunk_count=7, finished_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, SHA_ONE, currentReadyJobId);
        long oldFailedJobId = jobs.createOrGetActive(repositoryId, "main", "API", "old-model",
                properties.getEmbeddingDimensions(), RagProperties.INDEX_VERSION).jobId();
        jdbc.update("""
                UPDATE rag_index_job
                SET status='FAILED', error_code='EMBEDDING_UNAVAILABLE', finished_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, oldFailedJobId);
        return new TupleOrderingFixture(currentReadyJobId, oldFailedJobId);
    }

    private record TupleOrderingFixture(long currentReadyJobId, long oldFailedJobId) {
    }

    private static boolean created(Object result) {
        return (boolean) ReflectionTestUtils.getField(result, "created");
    }

    private static long jobId(Object result) {
        return (long) ReflectionTestUtils.getField(result, "jobId");
    }

    @Test
    void failedWriteRollsBackSnapshotAndPreservesPreviousReadyCommit() {
        files.set(file("stable.java", "class Stable {}"));
        index(metadata(SHA_ONE));
        files.set(file("broken.java", "class Broken {}"));
        chunks = new RagChunkStore(jdbc) {
            @Override
            public void insertBatch(long repositoryId, long snapshotId, long documentId, String commitSha,
                                    List<EmbeddedChunk> values) {
                super.insertBatch(repositoryId, snapshotId, documentId, commitSha, values);
                throw new IllegalStateException("simulated write failure");
            }
        };
        worker = new RagIndexWorker(repositories, jobs, documents, chunks, ignored -> files.current(),
                new LineWindowCodeChunker(), embeddings, transactions, Clock.systemUTC());

        RagIndexResolution queued = service.ensureIndexed(metadata(SHA_TWO));
        RagIndexJob claimed = transactions.execute(ignored -> jobs.claimNextQueued().orElseThrow());
        worker.process(claimed);

        assertThat(service.getJob(queued.jobId()).status()).isEqualTo(RagIndexJob.Status.FAILED);
        assertThat(jdbc.queryForObject("SELECT active_commit_sha FROM rag_repository", String.class))
                .isEqualTo(SHA_ONE);
        assertThat(jdbc.queryForObject("SELECT index_status FROM rag_repository", String.class))
                .isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_document WHERE commit_sha = ?",
                Integer.class, SHA_TWO)).isZero();
    }

    @Test
    void recoversStaleRunningJobsUntilThirdAttemptThenFails() {
        long repositoryId = repositories.ensure("github", "owner", "repo", "https://github.com/owner/repo.git",
                "main", properties.getEmbeddingModel(), properties.getEmbeddingDimensions(), 1).id();
        long retryable = jobs.createOrGetActive(repositoryId, SHA_ONE, "PULL_REQUEST", "test-model", 1024, 1).jobId();
        long exhausted = jobs.createOrGetActive(repositoryId, SHA_TWO, "PULL_REQUEST", "test-model", 1024, 1).jobId();
        long legacy = jobs.createOrGetActive(repositoryId, "4".repeat(40), "PULL_REQUEST", "test-model", 1024, 1).jobId();
        jdbc.update("UPDATE rag_index_job SET status='RUNNING', attempt_count=2, started_at=? WHERE id=?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(16), retryable);
        jdbc.update("UPDATE rag_index_job SET status='RUNNING', attempt_count=3, started_at=? WHERE id=?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(16), exhausted);
        jdbc.update("""
                UPDATE rag_index_job
                SET status='RUNNING', attempt_count=1, started_at=NULL, heartbeat_at=NULL, created_at=?
                WHERE id=?
                """, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(16), legacy);

        int recovered = transactions.execute(ignored -> jobs.recoverStale(Duration.ofMinutes(15),
                LocalDateTime.now(ZoneOffset.UTC)));

        assertThat(recovered).isEqualTo(3);
        assertThat(jobs.get(retryable).orElseThrow().status()).isEqualTo(RagIndexJob.Status.QUEUED);
        assertThat(jobs.get(exhausted).orElseThrow().status()).isEqualTo(RagIndexJob.Status.FAILED);
        assertThat(jobs.get(legacy).orElseThrow().status()).isEqualTo(RagIndexJob.Status.QUEUED);
    }

    @Test
    void productionExecutorHasBoundedDedicatedCapacity() {
        ThreadPoolTaskExecutor executor = new com.codereviewx.backend.rag.config.RagIndexingConfiguration()
                .ragIndexExecutor(new RagIndexLifecycleCoordinator((jobId, attempt) -> true), new RagProperties());
        executor.initialize();

        assertThat(executor.getCorePoolSize()).isEqualTo(1);
        assertThat(executor.getMaxPoolSize()).isEqualTo(2);
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(20);
    }

    private void index(GithubPrMetadata metadata) {
        indexAndResolve(metadata);
    }

    private RagIndexWorker workerWithCheckout(RepositoryFixture fixture) throws Exception {
        Path workRoot = tempDir.resolve("checkout-" + System.nanoTime()).toAbsolutePath().normalize();
        Files.createDirectories(workRoot);
        try (var stream = Files.newDirectoryStream(workRoot)) {
            Assumptions.assumeTrue(stream instanceof SecureDirectoryStream<?>,
                    "filesystem provider does not support SecureDirectoryStream");
        }
        JGitRepositoryCheckoutService checkout = JGitRepositoryCheckoutService.forLocalTesting(
                workRoot, 1, fixture.bare().toUri().toString());
        return new RagIndexWorker(repositories, jobs, documents, chunks, checkout,
                new RepositoryFileDiscovery()::discover, new LineWindowCodeChunker(), embeddings, transactions,
                Clock.systemUTC(), "test-model", 1024, null, null,
                new RagIndexLifecycleCoordinator(jobs::releaseForShutdown), null);
    }

    private RepositoryFixture createRepository() throws Exception {
        Path source = tempDir.resolve("source-" + System.nanoTime());
        Files.createDirectories(source);
        String commitSha;
        try (Git git = Git.init().setInitialBranch("main").setDirectory(source.toFile()).call()) {
            Files.writeString(source.resolve("Example.java"), "class Example {}\n");
            git.add().addFilepattern("Example.java").call();
            RevCommit commit = git.commit().setMessage("initial")
                    .setAuthor("test", "test@example.com").call();
            commitSha = commit.name();
        }
        Path bare = tempDir.resolve("bare-" + System.nanoTime() + ".git");
        try (Git ignored = Git.cloneRepository().setURI(source.toUri().toString()).setBare(true)
                .setDirectory(bare.toFile()).call()) {
            return new RepositoryFixture(bare, commitSha);
        }
    }

    private record RepositoryFixture(Path bare, String commitSha) {
    }

    private RagIndexResolution indexAndResolve(GithubPrMetadata metadata) {
        RagIndexResolution resolution = service.ensureIndexed(metadata);
        RagIndexJob claimed = transactions.execute(ignored -> jobs.claimNextQueued().orElseThrow());
        worker.process(claimed);
        assertThat(jobs.get(claimed.id()).orElseThrow().status()).isEqualTo(RagIndexJob.Status.READY);
        return resolution;
    }

    private int count(String table, String sha, String path) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE commit_sha=? AND path=?",
                Integer.class, sha, path);
    }

    private static RepositoryFile file(String path, String content) {
        return new RepositoryFile(path, Language.JAVA, content,
                content.getBytes(StandardCharsets.UTF_8).length, Hashing.sha256(content));
    }

    private static String windowedSource(int changedLine) {
        return java.util.stream.IntStream.range(0, 200)
                .mapToObj(line -> line == changedLine ? "int value99 = 999;" : "int value" + line + " = " + line + ";")
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
    }

    private static String largeChunkSource(int chunks) {
        return java.util.stream.IntStream.range(0, chunks)
                .mapToObj(index -> "line" + index + "=" + "x".repeat(7900))
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
    }

    private static String shiftedSource(String... labels) {
        return java.util.Arrays.stream(labels)
                .map(label -> label + ":" + "x".repeat(7900))
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
    }

    private static GithubPrMetadata metadata(String sha) {
        return new GithubPrMetadata("owner", "repo", 1, "title", "author", "main", "feature",
                "0".repeat(40), sha, "open", "", "", 1, 1, 0);
    }

    private final class MutableFiles {
        private List<RepositoryFile> current = List.of();

        void set(RepositoryFile... values) {
            current = List.of(values);
        }

        List<RepositoryFile> current() {
            return current;
        }
    }

    private static final class RecordingEmbeddingClient implements EmbeddingClient {
        private final List<Integer> batchSizes = new ArrayList<>();

        @Override
        public List<float[]> embed(List<String> inputs) {
            batchSizes.add(inputs.size());
            return inputs.stream().map(ignored -> new float[1024]).toList();
        }

        List<Integer> batchSizes() {
            return List.copyOf(batchSizes);
        }

        int totalInputs() {
            return batchSizes.stream().mapToInt(Integer::intValue).sum();
        }

        void clear() {
            batchSizes.clear();
        }
    }

    private static final class InterruptibleEmbeddingClient implements EmbeddingClient {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch interrupted = new CountDownLatch(1);

        @Override
        public List<float[]> embed(List<String> inputs) {
            started.countDown();
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(10));
            } catch (InterruptedException exception) {
                interrupted.countDown();
                throw new IllegalStateException("embedding interrupted");
            }
            return inputs.stream().map(ignored -> new float[1024]).toList();
        }
    }

    private static final class RecordingChunkStore extends RagChunkStore {
        private final List<Integer> batchSizes = new ArrayList<>();
        private final List<Integer> copyBatchSizes = new ArrayList<>();

        private RecordingChunkStore(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public void insertBatch(long repositoryId, long snapshotId, long documentId, String commitSha,
                                List<EmbeddedChunk> values) {
            batchSizes.add(values.size());
            super.insertBatch(repositoryId, snapshotId, documentId, commitSha, values);
        }

        @Override
        public int copyChunks(long repositoryId, long targetSnapshotId, List<Long> sourceChunkIds,
                              long targetDocumentId,
                              String targetCommitSha) {
            copyBatchSizes.add(sourceChunkIds.size());
            return super.copyChunks(repositoryId, targetSnapshotId, sourceChunkIds, targetDocumentId,
                    targetCommitSha);
        }

        @Override
        public void insertReusedBatch(long repositoryId, long snapshotId, long documentId, String commitSha,
                                      List<ReusedChunk> values) {
            copyBatchSizes.add(values.size());
            super.insertReusedBatch(repositoryId, snapshotId, documentId, commitSha, values);
        }

        List<Integer> batchSizes() {
            return List.copyOf(batchSizes);
        }

        List<Integer> copyBatchSizes() {
            return List.copyOf(copyBatchSizes);
        }

        void clearCopyBatchSizes() {
            copyBatchSizes.clear();
        }
    }
}
