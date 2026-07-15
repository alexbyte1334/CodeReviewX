package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.rag.model.CodeChunk;
import com.codereviewx.backend.rag.model.Language;
import com.codereviewx.backend.rag.model.RepositoryFile;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

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
    void partitionsLargeWindowsWithoutTruncationAndIsDeterministic() {
        String content = IntStream.rangeClosed(1, 81)
                .mapToObj(number -> number + "-" + "z".repeat(300))
                .reduce((left, right) -> left + "\n" + right).orElseThrow();
        RepositoryFile file = file(content);

        List<CodeChunk> first = chunker.chunk(file);
        List<CodeChunk> second = chunker.chunk(file);

        assertThat(first).isEqualTo(second);
        assertThat(first).allSatisfy(chunk -> assertThat(chunk.content().length()).isLessThanOrEqualTo(8_000));
        assertThat(first).extracting(CodeChunk::startLine).contains(1, 61);
        assertThat(first).extracting(CodeChunk::endLine).contains(80, 81);
        for (int index = 1; index < first.size(); index++) {
            CodeChunk previous = first.get(index - 1);
            CodeChunk current = first.get(index);
            assertThat(current.startLine()).isEqualTo(Math.max(previous.startLine() + 1, previous.endLine() - 19));
        }
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
