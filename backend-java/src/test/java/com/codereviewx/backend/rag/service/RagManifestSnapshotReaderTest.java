package com.codereviewx.backend.rag.service;

import com.codereviewx.backend.review.github.GithubPrDiff;
import com.codereviewx.backend.review.service.RepositoryContextIndexResult;
import com.codereviewx.backend.review.service.ReviewStaticAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagManifestSnapshotReaderTest {
    private JdbcTemplate jdbc;
    private RagManifestSnapshotReader reader;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:manifest-reader;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE rag_index_snapshot(id BIGINT PRIMARY KEY, repository_id BIGINT, job_id BIGINT, commit_sha VARCHAR(64))");
        jdbc.execute("CREATE TABLE rag_document(id BIGINT PRIMARY KEY, snapshot_id BIGINT, repository_id BIGINT, commit_sha VARCHAR(64), path VARCHAR(1024), language VARCHAR(64), byte_size BIGINT)");
        jdbc.execute("CREATE TABLE rag_chunk(id BIGINT PRIMARY KEY, snapshot_id BIGINT, document_id BIGINT, repository_id BIGINT, commit_sha VARCHAR(64), path VARCHAR(1024), start_line INT, end_line INT, content CLOB)");
        reader = new RagManifestSnapshotReader(jdbc);
    }

    @Test
    void readsCompleteChangedManifestFromExactImmutableSnapshotWhenDangerousLineWasNotChanged() {
        insertSnapshot(1, 7, 9, "target");
        insertManifest(10, 1, 7, "target", "package.json", 80,
                "{\n  \"dependencies\": {\n    \"unsafe\": \"latest\"\n  },\n  \"name\": \"changed\"\n}");

        RepositoryContextIndexResult context = reader.read(7, 9, "target", List.of("package.json"));
        GithubPrDiff diff = new GithubPrDiff("""
                diff --git a/package.json b/package.json
                @@ -5 +5 @@
                +  "name": "changed"
                """, 1, 80, false, List.of());

        assertThat(context.files()).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("package.json");
            assertThat(file.content()).contains("\"unsafe\": \"latest\"");
        });
        assertThat(new ReviewStaticAnalysisService().analyze(diff, context)).singleElement()
                .satisfies(finding -> assertThat(finding.getTitle()).contains("Unpinned npm dependency"));
    }

    @Test
    void rejectsWrongCommitJobPathAndOversizeRows() {
        insertSnapshot(1, 7, 9, "target");
        insertSnapshot(2, 7, 10, "wrong");
        insertManifest(10, 1, 7, "target", "src/package.json", 20, "{\"unsafe\": \"latest\"}");
        insertManifest(11, 2, 7, "wrong", "package.json", 20, "{\"wrong\": \"latest\"}");
        insertManifest(12, 1, 7, "target", "pom.xml", RagManifestSnapshotReader.MAX_MANIFEST_BYTES + 1L,
                "<version>1-SNAPSHOT</version>");

        RepositoryContextIndexResult context = reader.read(
                7, 9, "target", List.of("package.json", "README.md", "pom.xml"));
        assertThat(context.files()).isEmpty();
        assertThat(context.truncated()).isTrue();
    }

    @Test
    void redactsSecretsWithoutReturningNonManifestContent() {
        insertSnapshot(1, 7, 9, "target");
        String secret = "ghp" + "_abcdefghijklmnopqrstuvwxyz123456";
        insertManifest(10, 1, 7, "target", "package.json", 80,
                "{\"token\":\"" + secret + "\",\"unsafe\": \"latest\"}");
        insertManifest(11, 1, 7, "target", "src/config.json", 20, secret);

        RepositoryContextIndexResult context = reader.read(
                7, 9, "target", List.of("package.json", "src/config.json"));

        assertThat(context.files()).singleElement().satisfies(file -> {
            assertThat(file.content()).contains("[REDACTED]");
            assertThat(file.content()).doesNotContain(secret);
        });
        assertThat(context.contextText()).isEmpty();
    }

    @Test
    void requiresChunkPathBindingAndReportsChangedManifestCountTruncation() {
        insertSnapshot(1, 7, 9, "target");
        insertManifest(10, 1, 7, "target", "package.json", 20, "{\"safe\":\"1.0.0\"}");
        jdbc.update("UPDATE rag_chunk SET path='other/package.json' WHERE document_id=10");
        assertThat(reader.read(7, 9, "target", List.of("package.json")).files()).isEmpty();

        List<String> paths = new java.util.ArrayList<>();
        for (int index = 0; index <= RagManifestSnapshotReader.MAX_MANIFEST_FILES; index++) {
            String path = "module-" + index + "/package.json";
            paths.add(path);
            insertManifest(100 + index, 1, 7, "target", path, 16, "{\"safe\":\"1\"}");
        }
        RepositoryContextIndexResult limited = reader.read(7, 9, "target", paths);
        assertThat(limited.files()).hasSize(RagManifestSnapshotReader.MAX_MANIFEST_FILES);
        assertThat(limited.truncated()).isTrue();
    }

    @Test
    void reconstructsSplitLongLineWithoutInsertingAnExtraLine() {
        insertSnapshot(1, 7, 9, "target");
        jdbc.update("INSERT INTO rag_document(id,snapshot_id,repository_id,commit_sha,path,language,byte_size) "
                        + "VALUES (10,1,7,'target','package.json','JSON',9000)");
        insertChunk(101, 10, 1, 1, "a".repeat(8000));
        insertChunk(102, 10, 1, 1, "b".repeat(1000) + "\n");
        insertChunk(103, 10, 2, 2, "{\"unsafe\":\"latest\"}");

        RepositoryContextIndexResult context = reader.read(7, 9, "target", List.of("package.json"));

        assertThat(context.files()).singleElement().satisfies(file -> {
            assertThat(file.content().lines()).hasSize(2);
            assertThat(new ReviewStaticAnalysisService().analyze(
                    new GithubPrDiff("", 0, 0, false, List.of()), context)).singleElement()
                    .satisfies(finding -> assertThat(finding.getStartLine()).isEqualTo(2));
        });
    }

    @Test
    void enforcesAggregateManifestByteBudget() {
        insertSnapshot(1, 7, 9, "target");
        String content = "a".repeat(225 * 1024);
        List<String> paths = new java.util.ArrayList<>();
        for (int index = 0; index < 5; index++) {
            String path = "module-" + index + "/package.json";
            paths.add(path);
            insertManifest(100 + index, 1, 7, "target", path, content.length(), content);
        }

        RepositoryContextIndexResult context = reader.read(7, 9, "target", paths);

        assertThat(context.files()).hasSize(4);
        assertThat(context.contextBytes()).isLessThanOrEqualTo((int) RagManifestSnapshotReader.MAX_TOTAL_MANIFEST_BYTES);
        assertThat(context.truncated()).isTrue();
    }

    private void insertSnapshot(long id, long repositoryId, long jobId, String commit) {
        jdbc.update("INSERT INTO rag_index_snapshot(id,repository_id,job_id,commit_sha) VALUES (?,?,?,?)",
                id, repositoryId, jobId, commit);
    }

    private void insertManifest(long documentId, long snapshotId, long repositoryId, String commit,
                                String path, long byteSize, String content) {
        jdbc.update("INSERT INTO rag_document(id,snapshot_id,repository_id,commit_sha,path,language,byte_size) VALUES (?,?,?,?,?,'JSON',?)",
                documentId, snapshotId, repositoryId, commit, path, byteSize);
        jdbc.update("INSERT INTO rag_chunk(id,snapshot_id,document_id,repository_id,commit_sha,path,start_line,end_line,content) VALUES (?,?,?,?,?,?,?,?,?)",
                documentId * 10, snapshotId, documentId, repositoryId, commit, path, 1,
                Math.max(1, (int) content.lines().count()), content);
    }

    private void insertChunk(long chunkId, long documentId, int startLine, int endLine, String content) {
        jdbc.update("INSERT INTO rag_chunk(id,snapshot_id,document_id,repository_id,commit_sha,path,start_line,end_line,content) "
                        + "VALUES (?,1,?,7,'target','package.json',?,?,?)",
                chunkId, documentId, startLine, endLine, content);
    }
}
