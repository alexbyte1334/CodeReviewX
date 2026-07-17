package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.rag.model.CodeChunk;
import com.codereviewx.backend.rag.model.Language;
import com.codereviewx.backend.rag.model.RepositoryFile;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class LineWindowCodeChunkerTest {

    private final LineWindowCodeChunker chunker = new LineWindowCodeChunker();

    @Test
    void usesEightyLineWindowsWithTwentyLineOverlap() {
        assertRanges(79, "1-79");
        assertRanges(80, "1-80");
        assertRanges(81, "1-80", "61-81");
        assertRanges(140, "1-80", "61-140");
    }

    @Test
    void returnsNoChunksForEmptyOrBlankFiles() {
        assertThat(chunker.chunk(file(""))).isEmpty();
        assertThat(chunker.chunk(file(" \r\n\t\r"))).isEmpty();
    }

    @Test
    void normalizesLineEndingsAndCreatesStableHashesAndKeys() {
        CodeChunk chunk = chunker.chunk(file("first\r\nsecond\rthird\n")).get(0);
        assertThat(chunk.content()).isEqualTo("first\nsecond\nthird\n");
        assertThat(chunk.startLine()).isEqualTo(1);
        assertThat(chunk.endLine()).isEqualTo(4);
        assertThat(chunk.contentHash()).isEqualTo(sha256(chunk.content()));
        assertThat(chunk.chunkKey()).isEqualTo(sha256(
                "src/App.java:1:4:" + chunk.contentHash()));
        assertThat(chunk.tokenCount()).isEqualTo((chunk.content().length() + 3) / 4);
        assertThat(chunk.symbolName()).isNull();
    }

    @Test
    void splitsLongLinesWithoutDroppingCharactersOrExceedingCharacterBudget() {
        String content = "x".repeat(8_000) + "y".repeat(8_000) + "z".repeat(1_005);
        List<CodeChunk> chunks = chunker.chunk(file(content));

        assertThat(chunks).hasSize(3);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.content().length()).isLessThanOrEqualTo(8_000);
            assertThat(chunk.startLine()).isEqualTo(1);
            assertThat(chunk.endLine()).isEqualTo(1);
        });
        assertThat(chunks.stream().map(CodeChunk::content).reduce("", String::concat)).isEqualTo(content);
        assertThat(chunks).extracting(CodeChunk::chunkKey).doesNotHaveDuplicates();
    }

    @Test
    void singleLineCharacterWindowsAlwaysAdvance() {
        String content = "a".repeat(5_000) + "\n" + "b".repeat(5_000);

        List<CodeChunk> chunks = assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> chunker.chunk(file(content)));

        assertThat(chunks).extracting(CodeChunk::startLine).containsExactly(1, 2);
        assertThat(chunks).extracting(CodeChunk::endLine).containsExactly(1, 2);
    }

    @Test
    void repeatedPeriodicLongLineStillProducesUniqueExactKeysWithoutDataLoss() {
        String content = "abcd".repeat(5_001);

        List<CodeChunk> chunks = chunker.chunk(file(content));

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content()).hasSizeLessThanOrEqualTo(8_000));
        assertThat(chunks.stream().map(CodeChunk::content).reduce("", String::concat)).isEqualTo(content);
        assertThat(chunks).extracting(CodeChunk::chunkKey).doesNotHaveDuplicates();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.contentHash()).isEqualTo(sha256(chunk.content()));
            assertThat(chunk.chunkKey()).isEqualTo(sha256("src/App.java:1:1:" + chunk.contentHash()));
        });
        assertThat(chunker.chunk(file(content))).isEqualTo(chunks);
    }

    @Test
    void characterLimitedWindowsAboveOverlapContractKeepTwentyLines() {
        String content = IntStream.rangeClosed(1, 81)
                .mapToObj(number -> number + "-" + "z".repeat(300))
                .reduce((left, right) -> left + "\n" + right).orElseThrow();
        RepositoryFile file = file(content);

        List<CodeChunk> first = chunker.chunk(file);
        List<CodeChunk> second = chunker.chunk(file);

        assertThat(first).isEqualTo(second);
        assertThat(first).allSatisfy(chunk -> assertThat(chunk.content().length()).isLessThanOrEqualTo(8_000));
        for (int index = 1; index < first.size(); index++) {
            CodeChunk previous = first.get(index - 1);
            CodeChunk current = first.get(index);
            int previousLines = previous.endLine() - previous.startLine() + 1;
            assertThat(previousLines).isGreaterThan(20);
            assertThat(previous.endLine() - current.startLine() + 1).isEqualTo(20);
        }
        assertThat(first.get(first.size() - 1).endLine()).isEqualTo(81);
    }

    @Test
    void characterCapConflictUsesBoundedAdaptiveOverlapWhenTwentyLinesCannotProgress() {
        String content = IntStream.rangeClosed(1, 200)
                .mapToObj(number -> String.format("L%03d:%s", number, "x".repeat(395)))
                .reduce((left, right) -> left + "\n" + right).orElseThrow();
        RepositoryFile file = file(content);

        List<CodeChunk> chunks = chunker.chunk(file);

        assertThat(chunks).hasSizeLessThanOrEqualTo(15);
        assertThat(chunker.chunk(file)).isEqualTo(chunks);
        boolean[] covered = new boolean[200];
        for (int index = 0; index < chunks.size(); index++) {
            CodeChunk chunk = chunks.get(index);
            for (int line = chunk.startLine(); line <= chunk.endLine(); line++) {
                covered[line - 1] = true;
            }
            if (index > 0) {
                CodeChunk previous = chunks.get(index - 1);
                int previousLines = previous.endLine() - previous.startLine() + 1;
                int overlap = previous.endLine() - chunk.startLine() + 1;
                assertThat(previousLines).isLessThanOrEqualTo(20);
                assertThat(overlap).isBetween(1, 5);
                assertThat(overlap).isLessThan(previousLines);
            }
        }
        assertThat(covered).containsOnly(true);
        assertThat(chunks).extracting(CodeChunk::chunkKey).doesNotHaveDuplicates();
    }

    private void assertRanges(int lines, String... ranges) {
        String content = IntStream.rangeClosed(1, lines).mapToObj(number -> "line " + number)
                .reduce((left, right) -> left + "\n" + right).orElse("");
        assertThat(chunker.chunk(file(content))).extracting(chunk -> chunk.startLine() + "-" + chunk.endLine())
                .containsExactly(ranges);
    }

    private RepositoryFile file(String content) {
        return new RepositoryFile("src/App.java", Language.JAVA, content,
                content.getBytes(StandardCharsets.UTF_8).length, sha256(content));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
