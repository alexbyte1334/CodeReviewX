package com.codereviewx.backend.rag.retrieval;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.embedding.EmbeddingClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RagRetrievalProductionEvaluation {

    private static final String ENGINE = "java-production";
    private static final String IMAGE = "pgvector/pgvector:pg16";
    private static final String MODEL = "java-production-deterministic-token-hash-v1";
    private static final int DIMENSIONS = RagProperties.V1_EMBEDDING_DIMENSIONS;
    private static final Pattern WORD_BOUNDARY = Pattern.compile(
            "(?<=[a-z0-9])(?=[A-Z])|[^\\p{L}\\p{N}]+");

    private RagRetrievalProductionEvaluation() {
    }

    static Result run() throws Exception {
        Path repositoryRoot = repositoryRoot();
        Path ragRoot = repositoryRoot.resolve("evals/rag");
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CorpusManifest manifest = mapper.readValue(
                ragRoot.resolve("corpus/manifest.json").toFile(), CorpusManifest.class);
        List<EvaluationCase> cases = loadCases(mapper, ragRoot.resolve("cases"));

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(IMAGE)) {
            postgres.start();
            migrate(postgres);
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
            RagProperties properties = properties();
            long repositoryId = insertRepository(jdbc, manifest.targetCommit());
            Map<String, Long> snapshots = insertSnapshots(jdbc, repositoryId, manifest.chunks());
            loadCorpus(jdbc, repositoryId, snapshots, manifest.chunks(), ragRoot.resolve("corpus/sample-repo"));

            EmbeddingClient embeddingFixture = inputs -> inputs.stream()
                    .map(RagRetrievalProductionEvaluation::tokenHashEmbedding).toList();
            HybridRagRetrievalService retrieval = new HybridRagRetrievalService(
                    jdbc, embeddingFixture, properties);
            RagContextAssembler assembler = new RagContextAssembler(
                    RagRetrievalProductionEvaluation::deterministicRerank);

            List<RagRetrievalQualityMetrics.CaseResult> caseResults = new ArrayList<>();
            List<Map<String, Object>> caseReports = new ArrayList<>();
            for (EvaluationCase evaluationCase : cases) {
                RagRetrievalResult retrieved = retrieval.retrieve(new RagRetrievalRequest(
                        repositoryId,
                        evaluationCase.targetCommit(),
                        new RagRetrievalQuery(evaluationCase.query(), evaluationCase.changedPaths(),
                                List.of(), List.of(), List.of())));
                if (retrieved.status() != RagRetrievalResult.Status.READY) {
                    throw new AssertionError("Production retrieval index was not ready for " + evaluationCase.id());
                }
                RagEvidenceBundle bundle = assembler.assemble(
                        evaluationCase.query(), evaluationCase.targetCommit(), retrieved.matches(),
                        retrieved.retrievalHealth());
                List<ChunkIdentity> selectedChunks = bundle.evidence().stream()
                        .map(evidence -> chunkIdentity(jdbc, evidence.sourceIdentity().chunkId())).toList();
                List<String> selectedKeys = selectedChunks.stream().map(ChunkIdentity::chunkKey).toList();
                List<String> selectedCommits = selectedChunks.stream().map(ChunkIdentity::commitSha).toList();
                RagRetrievalQualityMetrics.CaseResult caseResult = new RagRetrievalQualityMetrics.CaseResult(
                        evaluationCase.id(), evaluationCase.relevantChunkKeys(), selectedKeys,
                        evaluationCase.forbiddenChunkKeys(), evaluationCase.targetCommit(), selectedCommits,
                        bundle.promptBlock().length());
                caseResults.add(caseResult);
                caseReports.add(caseReport(jdbc, evaluationCase, retrieved, bundle, selectedChunks));
            }

            RagRetrievalQualityMetrics metrics = RagRetrievalQualityMetrics.from(caseResults);
            Path jsonReport = ragRoot.resolve("reports/java-production-latest.json");
            Path markdownReport = ragRoot.resolve("reports/java-production-latest.md");
            writeReports(mapper, jsonReport, markdownReport, metrics, caseReports);
            return new Result(ENGINE, metrics, jsonReport, markdownReport);
        }
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

    private static RagProperties properties() {
        RagProperties properties = new RagProperties();
        properties.setEnabled(false);
        properties.setEmbeddingModel(MODEL);
        properties.setEmbeddingDimensions(DIMENSIONS);
        return properties;
    }

    private static long insertRepository(JdbcTemplate jdbc, String activeCommit) {
        return jdbc.queryForObject("""
                INSERT INTO rag_repository
                  (provider, owner_name, repository_name, clone_url, default_branch, active_commit_sha,
                   index_status, index_version, embedding_model, embedding_dimensions, created_at, updated_at)
                VALUES ('fixture','evals','sample-repo','fixture://evals/rag/corpus/sample-repo','main',?,
                        'READY',1,?,?,?,?)
                RETURNING id
                """, Long.class, activeCommit, MODEL, DIMENSIONS, fixedTime(), fixedTime());
    }

    private static Map<String, Long> insertSnapshots(JdbcTemplate jdbc, long repositoryId,
                                                      List<CorpusChunk> chunks) {
        Set<String> commits = new LinkedHashSet<>();
        chunks.forEach(chunk -> commits.add(chunk.commit()));
        Map<String, Long> snapshots = new LinkedHashMap<>();
        for (String commit : commits) {
            long jobId = jdbc.queryForObject("""
                    INSERT INTO rag_index_job
                      (repository_id, requested_ref, resolved_commit_sha, trigger_type, status, attempt_count,
                       discovered_file_count, indexed_file_count, indexed_chunk_count, skipped_file_count,
                       created_at, finished_at, embedding_model, embedding_dimensions, index_version)
                    VALUES (?,?,?,'QUALITY_ACCEPTANCE','READY',1,0,0,0,0,?,?,?,?,?)
                    RETURNING id
                    """, Long.class, repositoryId, commit, commit, fixedTime(), fixedTime(), MODEL, DIMENSIONS, 1);
            long snapshotId = jdbc.queryForObject("""
                    INSERT INTO rag_index_snapshot
                      (repository_id, job_id, commit_sha, embedding_model, embedding_dimensions, index_version,
                       created_at)
                    VALUES (?,?,?,?,?,?,?)
                    RETURNING id
                    """, Long.class, repositoryId, jobId, commit, MODEL, DIMENSIONS, 1, fixedTime());
            snapshots.put(commit, snapshotId);
        }
        return snapshots;
    }

    private static void loadCorpus(JdbcTemplate jdbc, long repositoryId, Map<String, Long> snapshots,
                                   List<CorpusChunk> chunks, Path corpusRoot) throws IOException {
        for (CorpusChunk chunk : chunks) {
            Path file = corpusRoot.resolve(chunk.key()).normalize();
            if (!file.startsWith(corpusRoot) || !Files.isRegularFile(file)) {
                throw new IllegalArgumentException("Invalid corpus path: " + chunk.key());
            }
            String content = Files.readString(file);
            long snapshotId = snapshots.get(chunk.commit());
            String language = chunk.key().endsWith(".md") ? "MARKDOWN" : "TYPESCRIPT";
            String contentHash = Integer.toUnsignedString(content.hashCode(), 16);
            long documentId = jdbc.queryForObject("""
                    INSERT INTO rag_document
                      (repository_id, snapshot_id, commit_sha, path, language, content_hash, byte_size, created_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    RETURNING id
                    """, Long.class, repositoryId, snapshotId, chunk.commit(), chunk.key(), language,
                    contentHash, content.getBytes(StandardCharsets.UTF_8).length, fixedTime());
            jdbc.update("""
                    INSERT INTO rag_chunk
                      (repository_id, snapshot_id, document_id, commit_sha, chunk_key, path, language, symbol_name,
                       start_line, end_line, content, token_count, content_hash, embedding, created_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, repositoryId, snapshotId, documentId, chunk.commit(), chunk.key() + "#1", chunk.key(),
                    language, chunk.key(), 1, Math.max(1, (int) content.lines().count()), content,
                    tokens(content).size(), contentHash,
                    new PGvector(tokenHashEmbedding(chunk.key() + " " + content)), fixedTime());
        }
    }

    private static List<RerankedChunk> deterministicRerank(String query, List<RerankCandidate> candidates) {
        Set<String> queryTokens = tokens(query);
        List<ScoredCandidate> scored = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            RerankCandidate candidate = candidates.get(index);
            Set<String> candidateTokens = tokens(candidate.text());
            long overlap = queryTokens.stream().filter(candidateTokens::contains).count();
            double score = overlap / Math.sqrt(Math.max(1.0, queryTokens.size() * candidateTokens.size()))
                    + 1.0 / (10_000 + index);
            scored.add(new ScoredCandidate(candidate, score, index));
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                        .thenComparingInt(ScoredCandidate::originalRank))
                .map(item -> new RerankedChunk(item.candidate(), item.score()))
                .toList();
    }

    private static float[] tokenHashEmbedding(String value) {
        float[] vector = new float[DIMENSIONS];
        for (String token : tokens(value)) {
            int hash = token.hashCode();
            int coordinate = Math.floorMod(hash, vector.length);
            vector[coordinate] += (hash & 1) == 0 ? 1.0f : -1.0f;
        }
        double norm = 0.0;
        for (float component : vector) {
            norm += component * component;
        }
        norm = Math.sqrt(norm);
        if (norm == 0.0) {
            vector[0] = 1.0f;
            return vector;
        }
        for (int index = 0; index < vector.length; index++) {
            vector[index] /= (float) norm;
        }
        return vector;
    }

    private static Set<String> tokens(String value) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("[\\p{L}\\p{N}_]+").matcher(value);
        while (matcher.find()) {
            String raw = matcher.group();
            for (String token : WORD_BOUNDARY.split(raw)) {
                if (!token.isBlank()) {
                    result.add(token.toLowerCase(Locale.ROOT));
                }
            }
        }
        return result;
    }

    private static ChunkIdentity chunkIdentity(JdbcTemplate jdbc, long chunkId) {
        return jdbc.queryForObject("SELECT chunk_key, path, commit_sha FROM rag_chunk WHERE id=?",
                (result, row) -> new ChunkIdentity(result.getString("chunk_key"), result.getString("path"),
                        result.getString("commit_sha")), chunkId);
    }

    private static Map<String, Object> caseReport(JdbcTemplate jdbc, EvaluationCase evaluationCase,
                                                   RagRetrievalResult retrieved,
                                                   RagEvidenceBundle bundle,
                                                   List<ChunkIdentity> selectedChunks) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("id", evaluationCase.id());
        report.put("targetCommit", evaluationCase.targetCommit());
        report.put("query", evaluationCase.query());
        report.put("changedPaths", evaluationCase.changedPaths());
        report.put("relevantChunkKeys", evaluationCase.relevantChunkKeys());
        report.put("forbiddenChunkKeys", evaluationCase.forbiddenChunkKeys());
        report.put("vectorCandidateCount", retrieved.vectorCandidateCount());
        report.put("lexicalCandidateCount", retrieved.lexicalCandidateCount());
        report.put("retrievedCandidateCount", retrieved.matches().size());
        report.put("forbiddenCandidateKeys", retrieved.matches().stream()
                .map(match -> chunkIdentity(jdbc, match.chunkId()).chunkKey())
                .filter(evaluationCase.forbiddenChunkKeys()::contains).toList());
        report.put("selectedChunkKeys", selectedChunks.stream().map(ChunkIdentity::chunkKey).toList());
        report.put("selectedPaths", selectedChunks.stream().map(ChunkIdentity::path).toList());
        report.put("selectedCommits", selectedChunks.stream().map(ChunkIdentity::commitSha).toList());
        report.put("contextCharacters", bundle.promptBlock().length());
        return report;
    }

    private static void writeReports(ObjectMapper mapper, Path jsonPath, Path markdownPath,
                                     RagRetrievalQualityMetrics metrics,
                                     List<Map<String, Object>> rawCaseReports) throws IOException {
        Map<String, Map<String, Object>> metricCases = new LinkedHashMap<>();
        for (RagRetrievalQualityMetrics.CaseMetrics item : metrics.cases()) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("recallAt10", item.recallAt10());
            values.put("mrrAt10", item.mrrAt10());
            values.put("ndcgAt10", item.ndcgAt10());
            values.put("forbiddenHits", item.forbiddenHits());
            values.put("crossCommitContamination", item.crossCommitContamination());
            values.put("contextBudgetViolation", item.contextBudgetViolation());
            metricCases.put(item.id(), values);
        }
        rawCaseReports.forEach(report -> report.put("metrics", metricCases.get(report.get("id"))));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 2);
        report.put("engine", ENGINE);
        report.put("status", metrics.failures().isEmpty() ? "PASS" : "FAIL");
        report.put("database", linkedMap(
                "product", "PostgreSQL 16",
                "extension", "pgvector",
                "containerImage", IMAGE));
        report.put("externalFixtures", linkedMap(
                "embedding", MODEL,
                "rerank", "deterministic-query-content-token-overlap-v1",
                "purpose", "stable external model substitutes; retrieval, RRF, and selection remain production Java"));
        report.put("covers", List.of("Recall@10", "MRR@10", "nDCG@10", "forbidden hits",
                "cross-commit contamination", "context budget"));
        report.put("excludes", List.of("MiMo finding generation", "finding quality",
                "evidence validation pass rate", "grounded finding precision", "network model latency"));
        report.put("metrics", metrics.metrics());
        report.put("thresholds", RagRetrievalQualityMetrics.THRESHOLDS);
        report.put("failures", metrics.failures());
        report.put("cases", rawCaseReports);
        Files.createDirectories(jsonPath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), report);

        StringBuilder markdown = new StringBuilder("# Java production RAG retrieval quality\n\n")
                .append("Engine: `java-production`\n\n")
                .append("Result: **").append(metrics.failures().isEmpty() ? "PASS" : "FAIL").append("**\n\n")
                .append("Runtime: PostgreSQL 16 with pgvector (`pgvector/pgvector:pg16`).\n\n")
                .append("External fixtures: deterministic token-hash embedding and deterministic query/content ")
                .append("token-overlap rerank. Production Java performs snapshot scoping, vector/FTS retrieval, ")
                .append("RRF, and context selection.\n\n")
                .append("## Metrics\n\n");
        metrics.metrics().forEach((name, value) -> markdown.append("- ").append(name).append(": ")
                .append(String.format(Locale.ROOT, "%.3f", value)).append(" (threshold ")
                .append(String.format(Locale.ROOT, "%.3f", RagRetrievalQualityMetrics.THRESHOLDS.get(name)))
                .append(")\n"));
        markdown.append("\n## Cases\n\n");
        for (RagRetrievalQualityMetrics.CaseMetrics item : metrics.cases()) {
            markdown.append("- `").append(item.id()).append("`: selected=")
                    .append(item.selectedKeys()).append(", Recall@10=")
                    .append(String.format(Locale.ROOT, "%.3f", item.recallAt10())).append(", MRR@10=")
                    .append(String.format(Locale.ROOT, "%.3f", item.mrrAt10())).append(", nDCG@10=")
                    .append(String.format(Locale.ROOT, "%.3f", item.ndcgAt10())).append(", forbidden=")
                    .append(item.forbiddenHits()).append(", cross-commit=")
                    .append(item.crossCommitContamination()).append(", context-chars=")
                    .append(item.contextCharacters()).append("\n");
        }
        markdown.append("\n## Failures\n\n")
                .append(metrics.failures().isEmpty() ? "None.\n" : String.join("\n", metrics.failures()))
                .append("\n\n## Excludes\n\n")
                .append("This gate does not cover MiMo finding generation, finding quality, evidence-validation ")
                .append("pass rate, grounded finding precision, or network model latency.\n");
        Files.writeString(markdownPath, markdown.toString());
    }

    private static Map<String, Object> linkedMap(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }

    private static List<EvaluationCase> loadCases(ObjectMapper mapper, Path casesDirectory) throws IOException {
        try (var files = Files.list(casesDirectory)) {
            List<Path> caseFiles = files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted().toList();
            List<EvaluationCase> result = new ArrayList<>();
            for (Path caseFile : caseFiles) {
                result.add(mapper.readValue(caseFile.toFile(), EvaluationCase.class));
            }
            return List.copyOf(result);
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("evals/rag"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("evals/rag"))) {
            return parent;
        }
        throw new IllegalStateException("Cannot locate evals/rag from " + current);
    }

    private static Timestamp fixedTime() {
        return Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    record Result(String engine, RagRetrievalQualityMetrics metrics, Path jsonReport, Path markdownReport) {
    }

    private record CorpusManifest(String targetCommit, List<CorpusChunk> chunks) {
    }

    private record CorpusChunk(String key, String commit) {
    }

    private record EvaluationCase(String id, String targetCommit, String query, List<String> changedPaths,
                                  List<String> relevantChunkKeys, List<String> forbiddenChunkKeys) {
    }

    private record ScoredCandidate(RerankCandidate candidate, double score, int originalRank) {
    }

    private record ChunkIdentity(String chunkKey, String path, String commitSha) {
    }
}
