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

@Service
@ConditionalOnProperty(prefix = "codereviewx.rag", name = "enabled", havingValue = "true")
public class HybridRagRetrievalService {

    private static final int INDEX_VERSION = 1;
    private static final int MAX_CHANGED_PATHS = 200;
    private static final int MAX_EXCERPT_CHARS = 2_000;
    private final JdbcTemplate jdbc;
    private final EmbeddingClient embeddingClient;
    private final RagProperties properties;
    private final VectorRetriever vectorRetriever;
    private final LexicalRetriever lexicalRetriever;
    private final ReciprocalRankFusion fusion = new ReciprocalRankFusion();

    public HybridRagRetrievalService(JdbcTemplate jdbc, EmbeddingClient embeddingClient, RagProperties properties) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.embeddingClient = Objects.requireNonNull(embeddingClient, "embeddingClient");
        this.properties = Objects.requireNonNull(properties, "properties");
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(jdbc);
        this.vectorRetriever = new VectorRetriever(namedJdbc);
        this.lexicalRetriever = new LexicalRetriever(namedJdbc);
    }

    public Result retrieve(Request request) {
        Objects.requireNonNull(request, "request");
        validateConfiguration();
        SnapshotIdentity snapshot = exactReadySnapshot(request.repositoryId(), request.commitSha());
        if (snapshot == null) {
            return new Result(Status.INDEX_NOT_READY, null, 0, 0, List.of());
        }
        String query = Objects.requireNonNullElse(request.query(), "").trim();
        if (query.isEmpty()) {
            return new Result(Status.READY, snapshot.snapshotId(), 0, 0, List.of());
        }
        List<String> changedPaths = safeChangedPaths(request.changedPaths());
        float[] queryEmbedding = embedQuery(query);
        List<ReciprocalRankFusion.Candidate> vector = vectorRetriever.retrieve(snapshot, queryEmbedding, changedPaths);
        List<ReciprocalRankFusion.Candidate> lexical = lexicalRetriever.retrieve(snapshot, query, changedPaths);
        List<Match> matches = fusion.fuse(vector, lexical).stream().map(this::toMatch).toList();
        return new Result(Status.READY, snapshot.snapshotId(), vector.size(), lexical.size(), matches);
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
        String content = candidate.content();
        String excerpt = content.substring(0, Math.min(content.length(), MAX_EXCERPT_CHARS));
        return new Match(candidate.chunkId(), candidate.path(), candidate.language(), candidate.symbolName(),
                candidate.startLine(), candidate.endLine(), candidate.contentHash(), excerpt,
                candidate.pathBoost(), item.score());
    }

    public enum Status {
        READY,
        INDEX_NOT_READY
    }

    public record Request(long repositoryId, String commitSha, String query, List<String> changedPaths) {
    }

    public record Result(Status status, Long snapshotId, int vectorCandidateCount, int lexicalCandidateCount,
                         List<Match> matches) {
    }

    public record Match(long chunkId, String path, String language, String symbolName, int startLine, int endLine,
                        String contentHash, String excerpt, double pathBoost, double fusedScore) {
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
}
