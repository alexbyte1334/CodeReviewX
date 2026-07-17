package com.codereviewx.backend.rag.service;

import com.codereviewx.backend.rag.security.RagSecurityPolicy;
import com.codereviewx.backend.review.service.RepositoryContextFile;
import com.codereviewx.backend.review.service.RepositoryContextIndexResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Repository
@ConditionalOnProperty(prefix = "codereviewx.rag", name = "enabled", havingValue = "true")
public class RagManifestSnapshotReader {
    static final long MAX_MANIFEST_BYTES = 256L * 1024L;
    static final int MAX_MANIFEST_FILES = 50;
    static final long MAX_TOTAL_MANIFEST_BYTES = 1024L * 1024L;

    private final JdbcTemplate jdbc;

    public RagManifestSnapshotReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RepositoryContextIndexResult read(long repositoryId, long jobId, String commitSha,
                                             List<String> changedPaths) {
        Set<String> requestedPaths = new LinkedHashSet<>();
        if (changedPaths != null) {
            changedPaths.stream().filter(RagManifestSnapshotReader::isAllowedManifest)
                    .forEach(requestedPaths::add);
        }
        if (requestedPaths.isEmpty() || commitSha == null || commitSha.isBlank()) {
            return RepositoryContextIndexResult.empty();
        }
        boolean truncated = requestedPaths.size() > MAX_MANIFEST_FILES;
        Set<String> allowedPaths = requestedPaths.stream().limit(MAX_MANIFEST_FILES)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String placeholders = String.join(",", Collections.nCopies(allowedPaths.size(), "?"));
        List<Object> oversizeParameters = new ArrayList<>(List.of(
                repositoryId, jobId, commitSha, repositoryId, commitSha, MAX_MANIFEST_BYTES));
        oversizeParameters.addAll(allowedPaths);
        Integer oversizeCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM rag_index_snapshot snapshot
                JOIN rag_document document ON document.snapshot_id=snapshot.id
                WHERE snapshot.repository_id=? AND snapshot.job_id=? AND snapshot.commit_sha=?
                  AND document.repository_id=? AND document.commit_sha=?
                  AND document.byte_size>?
                  AND document.path IN (%s)
                """.formatted(placeholders), Integer.class, oversizeParameters.toArray());
        truncated = truncated || (oversizeCount != null && oversizeCount > 0);
        List<Object> parameters = new ArrayList<>(List.of(
                repositoryId, jobId, commitSha, repositoryId, commitSha,
                repositoryId, commitSha, MAX_MANIFEST_BYTES));
        parameters.addAll(allowedPaths);
        List<ChunkRow> rows = jdbc.query("""
                SELECT document.id, document.path, document.language, document.byte_size,
                       chunk.id, chunk.start_line, chunk.end_line, chunk.content
                FROM rag_index_snapshot snapshot
                JOIN rag_document document ON document.snapshot_id=snapshot.id
                JOIN rag_chunk chunk ON chunk.snapshot_id=snapshot.id AND chunk.document_id=document.id
                    AND chunk.path=document.path
                WHERE snapshot.repository_id=? AND snapshot.job_id=? AND snapshot.commit_sha=?
                  AND document.repository_id=? AND document.commit_sha=?
                  AND chunk.repository_id=? AND chunk.commit_sha=?
                  AND document.byte_size<=?
                  AND document.path IN (%s)
                ORDER BY document.path, chunk.start_line, chunk.end_line, chunk.id
                """.formatted(placeholders), (result, row) -> new ChunkRow(
                        result.getLong(1), result.getString(2), result.getString(3), result.getLong(4),
                        result.getLong(5), result.getInt(6), result.getInt(7), result.getString(8)),
                parameters.toArray());
        Map<Long, List<ChunkRow>> byDocument = new LinkedHashMap<>();
        for (ChunkRow row : rows) {
            if (allowedPaths.contains(row.path()) && isAllowedManifest(row.path())) {
                byDocument.computeIfAbsent(row.documentId(), ignored -> new ArrayList<>()).add(row);
            }
        }
        List<RepositoryContextFile> files = new ArrayList<>();
        int contextBytes = 0;
        for (List<ChunkRow> documentRows : byDocument.values()) {
            ChunkRow first = documentRows.get(0);
            String content = reconstruct(documentRows);
            if (content == null || content.getBytes(StandardCharsets.UTF_8).length > MAX_MANIFEST_BYTES) {
                truncated = true;
                continue;
            }
            String redacted = RagSecurityPolicy.redactOutbound(content);
            int redactedBytes = redacted.getBytes(StandardCharsets.UTF_8).length;
            if (contextBytes + redactedBytes > MAX_TOTAL_MANIFEST_BYTES) {
                truncated = true;
                continue;
            }
            files.add(new RepositoryContextFile(first.path(), first.language(), (int) first.byteSize(), false,
                    redacted));
            contextBytes += redactedBytes;
        }
        return new RepositoryContextIndexResult(List.copyOf(files), files.size(), contextBytes, truncated, "");
    }

    private static String reconstruct(List<ChunkRow> rows) {
        TreeMap<Integer, StringBuilder> lines = new TreeMap<>();
        int maximumLine = 0;
        for (ChunkRow row : rows) {
            if (row.startLine() < 1 || row.endLine() < row.startLine() || row.content() == null) return null;
            String[] values = row.content().split("\\n", -1);
            int expectedLines = row.endLine() - row.startLine() + 1;
            if (expectedLines == 1) {
                lines.computeIfAbsent(row.startLine(), ignored -> new StringBuilder()).append(row.content());
            } else {
                if (values.length != expectedLines) return null;
                for (int offset = 0; offset < values.length; offset++) {
                    lines.putIfAbsent(row.startLine() + offset, new StringBuilder(values[offset]));
                }
            }
            maximumLine = Math.max(maximumLine, row.endLine());
        }
        if (maximumLine == 0 || lines.size() != maximumLine) return null;
        StringBuilder content = new StringBuilder();
        for (int line = 1; line <= maximumLine; line++) {
            StringBuilder value = lines.get(line);
            if (value == null) return null;
            if (line > 1) content.append('\n');
            String lineValue = value.toString();
            if (lineValue.endsWith("\n")) {
                lineValue = lineValue.substring(0, lineValue.length() - 1);
            }
            content.append(lineValue);
            if (content.length() > MAX_MANIFEST_BYTES) return null;
        }
        return content.toString();
    }

    private static boolean isAllowedManifest(String path) {
        if (path == null || path.isBlank() || path.startsWith("/") || path.contains("\\")
                || path.equals("..") || path.startsWith("../") || path.contains("/../")) return false;
        return path.equals("package.json") || path.endsWith("/package.json")
                || path.equals("pom.xml") || path.endsWith("/pom.xml");
    }

    private record ChunkRow(long documentId, String path, String language, long byteSize, long chunkId,
                            int startLine, int endLine, String content) {}
}
