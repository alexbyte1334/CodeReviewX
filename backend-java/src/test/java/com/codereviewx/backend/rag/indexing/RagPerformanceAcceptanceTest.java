package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.embedding.EmbeddingClient;
import com.codereviewx.backend.rag.model.Language;
import com.codereviewx.backend.rag.model.RepositoryFile;
import com.codereviewx.backend.rag.persistence.RagChunkStore;
import com.codereviewx.backend.rag.persistence.RagDocumentStore;
import com.codereviewx.backend.rag.persistence.RagIndexJobStore;
import com.codereviewx.backend.rag.persistence.RagRepositoryStore;
import com.codereviewx.backend.rag.retrieval.HybridRagRetrievalService;
import com.codereviewx.backend.rag.retrieval.PrRetrievalQueryBuilder;
import com.codereviewx.backend.rag.retrieval.RagContextAssembler;
import com.codereviewx.backend.rag.retrieval.RagEvidenceBundle;
import com.codereviewx.backend.rag.retrieval.RagRetrievalRequest;
import com.codereviewx.backend.rag.retrieval.RagRetrievalResult;
import com.codereviewx.backend.rag.retrieval.RagRetrievalHealth;
import com.codereviewx.backend.rag.retrieval.RagRetrievalQuery;
import com.codereviewx.backend.rag.retrieval.RerankedChunk;
import com.codereviewx.backend.rag.service.DefaultRagIndexService;
import com.codereviewx.backend.rag.service.RagIndexJob;
import com.codereviewx.backend.rag.service.RagIndexResolution;
import com.codereviewx.backend.review.github.GithubPrMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "rag.performance.enabled", matches = "true")
class RagPerformanceAcceptanceTest {

    private static final String IMAGE = "pgvector/pgvector:pg16";
    private static final String BASELINE_COMMIT = "a".repeat(40);
    private static final String INCREMENTAL_COMMIT = "b".repeat(40);
    private static final int FILES = 1_000;
    private static final int CHANGED_FILES = 20;
    private static final int CHUNKS_PER_FILE = 10;
    private static final int CHUNKS = FILES * CHUNKS_PER_FILE;
    private static final int WARMUP_RUNS = 3;
    private static final int SAMPLE_RUNS = 10;
    private static final Path REPORTS = Path.of("target", "rag-reports");

    @Test
    void measuresIncrementalIndexAndProductionRetrievalAgainstDeliveryBudgets() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(IMAGE)) {
            postgres.start();
            migrate(postgres);
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            IndexFixture indexing = new IndexFixture(jdbc, transactions);

            indexing.files.set(repositoryFiles(false));
            long baselineStarted = System.nanoTime();
            indexing.index(metadata(BASELINE_COMMIT));
            double baselineIndexMs = elapsedMs(baselineStarted);
            assertSnapshot(jdbc, BASELINE_COMMIT, FILES, CHUNKS);

            indexing.embeddings.clear();
            indexing.files.set(repositoryFiles(true));
            long incrementalStarted = System.nanoTime();
            RagIndexResolution incremental = indexing.index(metadata(INCREMENTAL_COMMIT));
            double incrementalIndexMs = elapsedMs(incrementalStarted);
            int incrementalEmbeddedChunks = indexing.embeddings.totalInputs();
            assertSnapshot(jdbc, INCREMENTAL_COMMIT, FILES, CHUNKS);
            int reusedChunks = reusedChunkCount(jdbc);

            RagProperties retrievalProperties = properties();
            HybridRagRetrievalService retrieval = new HybridRagRetrievalService(
                    jdbc, inputs -> List.of(vector()), retrievalProperties);
            AtomicInteger maximumRerankCandidates = new AtomicInteger();
            RagContextAssembler assembler = new RagContextAssembler((query, candidates) -> {
                maximumRerankCandidates.accumulateAndGet(candidates.size(), Math::max);
                List<RerankedChunk> reranked = new ArrayList<>(candidates.size());
                for (int index = 0; index < candidates.size(); index++) {
                    reranked.add(new RerankedChunk(candidates.get(index), candidates.size() - index));
                }
                return reranked;
            });
            RagRetrievalRequest request = new RagRetrievalRequest(incremental.repositoryId(), INCREMENTAL_COMMIT,
                    new RagRetrievalQuery("needle performance changed",
                            changedPaths(), List.of(), List.of(), List.of("+needle performance changed")));

            for (int index = 0; index < WARMUP_RUNS; index++) {
                executeSample(retrieval, assembler, request);
            }
            List<Sample> samples = new ArrayList<>();
            for (int index = 0; index < SAMPLE_RUNS; index++) {
                samples.add(executeSample(retrieval, assembler, request));
            }

            double pipelineP95Ms = p95(samples.stream().map(Sample::pipelineMs).toList());
            int maximumContextChars = samples.stream().mapToInt(Sample::contextChars).max().orElseThrow();
            int maximumEvidence = samples.stream().mapToInt(Sample::evidenceCount).max().orElseThrow();
            int maximumFusedCandidates = samples.stream().mapToInt(Sample::fusedCandidates).max().orElseThrow();
            boolean passed = incrementalIndexMs <= 60_000.0 && pipelineP95Ms <= 3_000.0
                    && pipelineP95Ms <= 5_000.0 && maximumContextChars <= 36_000
                    && maximumRerankCandidates.get() <= 30 && maximumEvidence <= 12
                    && incrementalEmbeddedChunks == CHANGED_FILES
                    && reusedChunks == CHUNKS - CHANGED_FILES;
            writeReports(postgres, passed, baselineIndexMs, incrementalIndexMs, incrementalEmbeddedChunks,
                    reusedChunks, pipelineP95Ms, maximumContextChars, maximumEvidence,
                    maximumRerankCandidates.get(), maximumFusedCandidates, samples);

            assertThat(incrementalIndexMs).isLessThanOrEqualTo(60_000.0);
            assertThat(incrementalEmbeddedChunks).isEqualTo(CHANGED_FILES);
            assertThat(reusedChunks).isEqualTo(CHUNKS - CHANGED_FILES);
            assertThat(pipelineP95Ms).isLessThanOrEqualTo(3_000.0);
            assertThat(pipelineP95Ms).isLessThanOrEqualTo(5_000.0);
            assertThat(maximumContextChars).isLessThanOrEqualTo(36_000);
            assertThat(maximumRerankCandidates).hasValueLessThanOrEqualTo(30);
            assertThat(maximumEvidence).isLessThanOrEqualTo(12);
            assertThat(Files.exists(REPORTS.resolve("performance-runtime.json"))).isTrue();
            assertThat(Files.exists(REPORTS.resolve("performance-runtime.md"))).isTrue();
        }
    }

    private static Sample executeSample(HybridRagRetrievalService retrieval, RagContextAssembler assembler,
                                        RagRetrievalRequest request) {
        long started = System.nanoTime();
        RagRetrievalResult result = retrieval.retrieve(request);
        RagEvidenceBundle bundle = assembler.assemble("needle performance changed", INCREMENTAL_COMMIT,
                result.matches(), result.retrievalHealth());
        double pipelineMs = elapsedMs(started);
        assertThat(result.status()).isEqualTo(RagRetrievalResult.Status.READY);
        assertThat(result.retrievalHealth()).isEqualTo(RagRetrievalHealth.HEALTHY);
        assertThat(result.vectorCandidateCount()).isLessThanOrEqualTo(40);
        assertThat(result.lexicalCandidateCount()).isLessThanOrEqualTo(40);
        return new Sample(pipelineMs, bundle.promptBlock().length(), bundle.evidence().size(),
                result.matches().size());
    }

    private static List<RepositoryFile> repositoryFiles(boolean incremental) {
        return java.util.stream.IntStream.range(0, FILES).mapToObj(fileIndex -> {
            String content = java.util.stream.IntStream.range(0, 620)
                    .mapToObj(lineIndex -> sourceLine(fileIndex, lineIndex,
                            incremental && fileIndex < CHANGED_FILES && lineIndex == 10))
                    .reduce((left, right) -> left + "\n" + right).orElseThrow();
            String path = "src/performance/File%04d.java".formatted(fileIndex);
            return new RepositoryFile(path, Language.JAVA, content,
                    content.getBytes(StandardCharsets.UTF_8).length, Hashing.sha256(content));
        }).toList();
    }

    private static String sourceLine(int fileIndex, int lineIndex, boolean changed) {
        return "String file%04dLine%03d = \"needle performance repository context %s\";"
                .formatted(fileIndex, lineIndex, changed ? "changed" : "stable");
    }

    private static void assertSnapshot(JdbcTemplate jdbc, String commit, int files, int chunks) {
        assertThat(jdbc.queryForObject("SELECT status FROM rag_index_job WHERE resolved_commit_sha=?",
                String.class, commit)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_index_snapshot WHERE commit_sha=?",
                Integer.class, commit)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_document WHERE commit_sha=?",
                Integer.class, commit)).isEqualTo(files);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_chunk WHERE commit_sha=?",
                Integer.class, commit)).isEqualTo(chunks);
    }

    private static int reusedChunkCount(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM rag_chunk newer
                JOIN rag_chunk older
                  ON older.commit_sha=? AND newer.commit_sha=?
                 AND older.path=newer.path
                 AND older.start_line=newer.start_line
                 AND older.end_line=newer.end_line
                 AND older.content_hash=newer.content_hash
                 AND older.embedding=newer.embedding
                """, Integer.class, BASELINE_COMMIT, INCREMENTAL_COMMIT);
    }

    private static void migrate(PostgreSQLContainer<?> postgres) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration", "classpath:db/rag/postgresql")
                .initSql("CREATE SCHEMA IF NOT EXISTS flyway_compat; "
                        + "DO $$ BEGIN CREATE DOMAIN flyway_compat.CLOB AS TEXT; "
                        + "EXCEPTION WHEN duplicate_object THEN NULL; END $$; "
                        + "SET search_path TO public, flyway_compat")
                .load().migrate();
    }

    private static void writeReports(PostgreSQLContainer<?> postgres, boolean passed, double baselineIndexMs,
                                     double incrementalIndexMs, int incrementalEmbeddedChunks, int reusedChunks,
                                     double pipelineP95Ms, int contextChars, int evidenceCount,
                                     int rerankCandidates, int fusedCandidates, List<Sample> samples) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 2);
        report.put("generatedAt", Instant.now().toString());
        report.put("status", passed ? "PASS" : "FAIL");
        report.put("environment", Map.of(
                "postgresImage", IMAGE,
                "postgresVersion", postgres.execInContainer("postgres", "--version").getStdout().trim(),
                "os", System.getProperty("os.name") + " " + System.getProperty("os.version"),
                "arch", System.getProperty("os.arch"),
                "javaVersion", System.getProperty("java.version"),
                "reranker", "deterministic in-process acceptance fixture",
                "checkoutScope", "controlled RepositoryFile provider; network JGit fetch excluded"));
        report.put("dataset", Map.of("files", FILES, "chunksPerSnapshot", CHUNKS,
                "snapshots", 2, "changedFiles", CHANGED_FILES, "embeddingDimensions", 1024));
        report.put("sampling", Map.of("warmupRuns", WARMUP_RUNS, "sampleRuns", SAMPLE_RUNS));
        report.put("metrics", Map.of(
                "baselineIndexMs", baselineIndexMs,
                "incrementalIndex20ChangedFilesMs", incrementalIndexMs,
                "incrementalEmbeddedChunks", incrementalEmbeddedChunks,
                "reusedChunks", reusedChunks,
                "hybridRerankP95Ms", pipelineP95Ms,
                "ragAdditionalContextP95Ms", pipelineP95Ms,
                "maximumContextChars", contextChars,
                "maximumEvidence", evidenceCount,
                "maximumRerankCandidates", rerankCandidates,
                "maximumFusedCandidates", fusedCandidates));
        report.put("thresholds", Map.of("incrementalIndex20ChangedFilesMs", 60_000,
                "incrementalEmbeddedChunks", CHANGED_FILES, "hybridRerankP95Ms", 3_000,
                "ragAdditionalContextP95Ms", 5_000, "maximumContextChars", 36_000,
                "maximumEvidence", 12, "maximumRerankCandidates", 30));
        report.put("incrementalIndex20ChangedFiles", Map.of(
                "measured", true,
                "includes", "DefaultRagIndexService, lease claim, RagIndexWorker, LineWindowCodeChunker, deterministic embedding, transactions and PostgreSQL stores",
                "excludes", "network JGit fetch and remote repository file discovery"));
        report.put("ragAdditionalContext", Map.of(
                "includes", "query embedding fixture, PostgreSQL vector/FTS retrieval, RRF, deterministic rerank and RagContextAssembler",
                "excludes", "MiMo generation and network model latency"));
        report.put("samples", samples);
        Files.createDirectories(REPORTS);
        new ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValue(REPORTS.resolve("performance-runtime.json").toFile(), report);
        String markdown = """
                # RAG PostgreSQL performance acceptance

                Status: %s

                Environment: `%s`, `%s`, Java `%s`, `%s`

                Dataset: 1,000 files / 10,000 chunks per snapshot / 2 snapshots / 20 changed files.
                Retrieval sampling: 3 warmup runs, then 10 measured production hybrid + assembler runs.

                - 20-file incremental index: %.3f ms (gate <= 60,000 ms)
                - incremental embedded chunks: %d (expected exactly 20)
                - reused chunks: %d (expected 9,980)
                - hybrid + rerank p95: %.3f ms (gate <= 3,000 ms)
                - RAG additional context p95: %.3f ms (gate <= 5,000 ms)
                - maximum context chars: %d (gate <= 36,000)
                - maximum rerank candidates: %d (gate <= 30)
                - maximum evidence blocks: %d (gate <= 12)
                - baseline index: %.3f ms (informational)

                Incremental timing includes the real index service, leased worker, line-window chunker,
                deterministic embedding, transactions and PostgreSQL stores. It excludes network JGit fetch and
                remote discovery because a controlled `RepositoryFile` provider supplies both commits.

                RAG additional context includes query embedding fixture, PostgreSQL vector/FTS retrieval, RRF,
                deterministic rerank and context assembly. It excludes MiMo generation and network model latency.
                """.formatted(passed ? "PASS" : "FAIL", IMAGE,
                System.getProperty("os.name") + " " + System.getProperty("os.arch"),
                System.getProperty("java.version"), "deterministic in-process reranker", incrementalIndexMs,
                incrementalEmbeddedChunks, reusedChunks, pipelineP95Ms, pipelineP95Ms, contextChars,
                rerankCandidates, evidenceCount, baselineIndexMs);
        Files.writeString(REPORTS.resolve("performance-runtime.md"), markdown);
    }

    private static RagProperties properties() {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingModel("test-model");
        properties.setEmbeddingDimensions(1024);
        properties.setEmbeddingBatchSize(100);
        return properties;
    }

    private static GithubPrMetadata metadata(String commit) {
        return new GithubPrMetadata("performance", "fixture", 1, "performance", "system", "main", "feature",
                BASELINE_COMMIT, commit, "open", "", "", CHANGED_FILES, CHANGED_FILES, 0);
    }

    private static List<String> changedPaths() {
        return java.util.stream.IntStream.range(0, CHANGED_FILES)
                .mapToObj(index -> "src/performance/File%04d.java".formatted(index)).toList();
    }

    private static float[] vector() {
        float[] vector = new float[1024];
        vector[0] = 1.0f;
        return vector;
    }

    private static double elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000.0;
    }

    private static double p95(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.95) - 1));
    }

    private static final class IndexFixture {
        private final AtomicReference<List<RepositoryFile>> files = new AtomicReference<>(List.of());
        private final RecordingEmbeddingClient embeddings = new RecordingEmbeddingClient();
        private final RagIndexJobStore jobs;
        private final RagIndexWorker worker;
        private final DefaultRagIndexService service;
        private final TransactionTemplate transactions;

        private IndexFixture(JdbcTemplate jdbc, TransactionTemplate transactions) {
            this.transactions = transactions;
            RagRepositoryStore repositories = new RagRepositoryStore(jdbc);
            jobs = new RagIndexJobStore(jdbc);
            RagDocumentStore documents = new RagDocumentStore(jdbc);
            RagChunkStore chunks = new RagChunkStore(jdbc);
            RagProperties properties = properties();
            worker = new RagIndexWorker(repositories, jobs, documents, chunks, ignored -> files.get(),
                    new LineWindowCodeChunker(), embeddings, transactions, Clock.systemUTC());
            TaskExecutor pausedExecutor = runnable -> { };
            service = new DefaultRagIndexService(repositories, jobs, worker, pausedExecutor, transactions,
                    properties, Clock.systemUTC());
        }

        private RagIndexResolution index(GithubPrMetadata metadata) {
            RagIndexResolution resolution = service.ensureIndexed(metadata);
            RagIndexJob claimed = transactions.execute(ignored -> jobs.claimNextQueued().orElseThrow());
            worker.process(claimed);
            assertThat(jobs.get(claimed.id()).orElseThrow().status()).isEqualTo(RagIndexJob.Status.READY);
            return resolution;
        }
    }

    private static final class RecordingEmbeddingClient implements EmbeddingClient {
        private final List<Integer> batchSizes = new ArrayList<>();

        @Override
        public List<float[]> embed(List<String> inputs) {
            batchSizes.add(inputs.size());
            return inputs.stream().map(ignored -> vector()).toList();
        }

        private int totalInputs() {
            return batchSizes.stream().mapToInt(Integer::intValue).sum();
        }

        private void clear() {
            batchSizes.clear();
        }
    }

    private record Sample(double pipelineMs, int contextChars, int evidenceCount, int fusedCandidates) {
    }
}
