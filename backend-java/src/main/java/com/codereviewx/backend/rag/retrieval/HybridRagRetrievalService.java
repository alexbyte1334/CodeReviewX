package com.codereviewx.backend.rag.retrieval;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.embedding.EmbeddingClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataAccessException;

@Service
@ConditionalOnProperty(prefix = "codereviewx.rag", name = "enabled", havingValue = "true")
public class HybridRagRetrievalService {

    private static final int INDEX_VERSION = 1;
    private static final int MAX_CHANGED_PATHS = 200;
    private final JdbcTemplate jdbc;
    private final EmbeddingClient embeddingClient;
    private final RagProperties properties;
    private final VectorRoute vectorRetriever;
    private final LexicalRoute lexicalRetriever;
    private final ReciprocalRankFusion fusion = new ReciprocalRankFusion();
    private final PrRetrievalQueryBuilder queryBuilder = new PrRetrievalQueryBuilder();

    public HybridRagRetrievalService(JdbcTemplate jdbc, EmbeddingClient embeddingClient, RagProperties properties) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.embeddingClient = Objects.requireNonNull(embeddingClient, "embeddingClient");
        this.properties = Objects.requireNonNull(properties, "properties");
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(jdbc);
        this.vectorRetriever = new VectorRetriever(namedJdbc)::retrieve;
        this.lexicalRetriever = new LexicalRetriever(namedJdbc)::retrieve;
    }

    HybridRagRetrievalService(JdbcTemplate jdbc, EmbeddingClient embeddingClient, RagProperties properties,
                              VectorRoute vectorRetriever, LexicalRoute lexicalRetriever) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.embeddingClient = Objects.requireNonNull(embeddingClient, "embeddingClient");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.vectorRetriever = Objects.requireNonNull(vectorRetriever, "vectorRetriever");
        this.lexicalRetriever = Objects.requireNonNull(lexicalRetriever, "lexicalRetriever");
    }

    public Result retrieve(Request request) {
        Objects.requireNonNull(request, "request");
        validateConfiguration();
        SnapshotIdentity snapshot = exactReadySnapshot(request.repositoryId(), request.commitSha());
        if (snapshot == null) {
            return new Result(Status.INDEX_NOT_READY, null, 0, 0, List.of(), RagContextAssembler.RetrievalHealth.HEALTHY);
        }
        String query = queryBuilder.build(request.prQuery());
        if (query.isEmpty()) {
            return new Result(Status.READY, snapshot.snapshotId(), 0, 0, List.of(), RagContextAssembler.RetrievalHealth.HEALTHY);
        }
        List<String> changedPaths = safeChangedPaths(request.prQuery() == null ? null : request.prQuery().changedPaths());
        float[] queryEmbedding;
        try { queryEmbedding = embedQuery(query); }
        catch (RuntimeException embeddingFailure) {
            return new Result(Status.READY, snapshot.snapshotId(), 0, 0, List.of(),
                    RagContextAssembler.RetrievalHealth.EMBEDDING_FAILED);
        }
        List<ReciprocalRankFusion.Candidate> vector = List.of();
        List<ReciprocalRankFusion.Candidate> lexical = List.of();
        boolean vectorFailed = false;
        boolean lexicalFailed = false;
        try { vector = vectorRetriever.retrieve(snapshot, queryEmbedding, changedPaths); }
        catch (RouteUnavailableException | DataAccessException unavailable) { vectorFailed = true; }
        try { lexical = lexicalRetriever.retrieve(snapshot, query, changedPaths); }
        catch (RouteUnavailableException | DataAccessException unavailable) { lexicalFailed = true; }
        RagContextAssembler.RetrievalHealth health = vectorFailed && lexicalFailed
                ? RagContextAssembler.RetrievalHealth.BOTH_ROUTES_FAILED
                : vectorFailed || lexicalFailed ? RagContextAssembler.RetrievalHealth.SINGLE_ROUTE_FAILED
                : RagContextAssembler.RetrievalHealth.HEALTHY;
        List<Match> matches = fusion.fuse(vector, lexical).stream().map(this::toMatch).toList();
        return new Result(Status.READY, snapshot.snapshotId(), vector.size(), lexical.size(), matches, health);
    }

    private SnapshotIdentity exactReadySnapshot(long repositoryId, String commitSha) {
        if (repositoryId <= 0 || commitSha == null || commitSha.isBlank()) {
            return null;
        }
        List<Long> snapshots = jdbc.queryForList("""
                SELECT snapshot.id
                FROM rag_index_snapshot snapshot
                JOIN rag_index_job job ON job.id=snapshot.job_id AND job.status='READY'
                WHERE snapshot.repository_id=? AND snapshot.commit_sha=?
                  AND snapshot.embedding_model=? AND snapshot.embedding_dimensions=?
                  AND snapshot.index_version=?
                ORDER BY snapshot.id
                LIMIT 1
                """, Long.class, repositoryId, commitSha, properties.getEmbeddingModel(),
                properties.getEmbeddingDimensions(), INDEX_VERSION);
        return snapshots.isEmpty() ? null : new SnapshotIdentity(snapshots.get(0), repositoryId, commitSha,
                properties.getEmbeddingModel(), properties.getEmbeddingDimensions(), INDEX_VERSION);
    }

    private float[] embedQuery(String query) {
        List<float[]> embeddings = embeddingClient.embed(List.of(query));
        if (embeddings == null || embeddings.size() != 1 || embeddings.get(0) == null
                || embeddings.get(0).length != properties.getEmbeddingDimensions()) {
            throw new IllegalStateException("Query embedding capability mismatch");
        }
        for (float value : embeddings.get(0)) {
            if (!Float.isFinite(value)) {
                throw new IllegalStateException("Query embedding capability mismatch");
            }
        }
        return embeddings.get(0);
    }

    private void validateConfiguration() {
        if (properties.getEmbeddingModel() == null || properties.getEmbeddingModel().isBlank()
                || properties.getEmbeddingDimensions() != RagProperties.V1_EMBEDDING_DIMENSIONS) {
            throw new IllegalStateException("RAG retrieval embedding configuration is invalid");
        }
    }

    private List<String> safeChangedPaths(List<String> paths) {
        if (paths == null) {
            return List.of();
        }
        return paths.stream().filter(Objects::nonNull).map(String::trim)
                .filter(path -> !path.isBlank() && path.length() <= 1000
                        && path.chars().noneMatch(character -> Character.isISOControl(character)))
                .distinct().limit(MAX_CHANGED_PATHS).toList();
    }

    private Match toMatch(ReciprocalRankFusion.FusedCandidate item) {
        ReciprocalRankFusion.Candidate candidate = item.candidate();
        return new Match(candidate.chunkId(), candidate.path(), candidate.language(), candidate.symbolName(),
                candidate.startLine(), candidate.endLine(), candidate.contentHash(), candidate.content(),
                candidate.pathBoost(), item.score());
    }

    public enum Status {
        READY,
        INDEX_NOT_READY
    }

    public record Request(long repositoryId, String commitSha, PrRetrievalQueryBuilder.PrQuery prQuery) {
    }

    public record Result(Status status, Long snapshotId, int vectorCandidateCount, int lexicalCandidateCount,
                         List<Match> matches, RagContextAssembler.RetrievalHealth retrievalHealth) {
        public boolean legacyFallbackRequired() {
            return retrievalHealth == RagContextAssembler.RetrievalHealth.EMBEDDING_FAILED
                    || retrievalHealth == RagContextAssembler.RetrievalHealth.BOTH_ROUTES_FAILED;
        }
    }

    public record Match(long chunkId, String path, String language, String symbolName, int startLine, int endLine,
                        String contentHash, String content, double pathBoost, double fusedScore) {
    }

    public record SnapshotIdentity(long snapshotId, long repositoryId, String commitSha, String embeddingModel,
                                   int embeddingDimensions, int indexVersion) {
        MapSqlParameterSource parameters() {
            return new MapSqlParameterSource()
                    .addValue("snapshotId", snapshotId)
                    .addValue("repositoryId", repositoryId)
                    .addValue("commitSha", commitSha)
                    .addValue("embeddingModel", embeddingModel)
                    .addValue("embeddingDimensions", embeddingDimensions)
                    .addValue("indexVersion", indexVersion);
        }
    }

    @FunctionalInterface interface VectorRoute {
        List<ReciprocalRankFusion.Candidate> retrieve(SnapshotIdentity snapshot, float[] embedding, List<String> paths);
    }
    @FunctionalInterface interface LexicalRoute {
        List<ReciprocalRankFusion.Candidate> retrieve(SnapshotIdentity snapshot, String query, List<String> paths);
    }
    static class RouteUnavailableException extends RuntimeException {
        RouteUnavailableException(String message) { super(message); }
    }
}
