package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.rag.model.CodeChunk;
import com.codereviewx.backend.rag.model.RepositoryFile;

import java.util.ArrayList;
import java.util.List;

public final class LineWindowCodeChunker implements CodeChunker {

    static final int WINDOW_LINES = 80;
    static final int OVERLAP_LINES = 20;
    static final int MAX_CHARS = 8_000;

    @Override
    public List<CodeChunk> chunk(RepositoryFile file) {
        String normalized = file.content().replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.isBlank()) {
            return List.of();
        }
        String[] lines = normalized.split("\n", -1);
        List<CodeChunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < lines.length) {
            String sourceLine = lines[start] + (start < lines.length - 1 ? "\n" : "");
            if (sourceLine.length() > MAX_CHARS) {
                splitLongLine(file, sourceLine, start + 1, chunks);
                start++;
                continue;
            }
            int end = start;
            int characters = 0;
            while (end < lines.length && end - start < WINDOW_LINES) {
                int added = lines[end].length() + (end > start ? 1 : 0);
                if (characters + added > MAX_CHARS) {
                    break;
                }
                characters += added;
                end++;
            }
            if (end == start) {
                throw new IllegalStateException("Code chunking could not make progress");
            }
            String content = join(lines, start, end);
            if (!content.isBlank()) {
                chunks.add(createChunk(file, content, start + 1, end));
            }
            if (end == lines.length) {
                break;
            }
            int windowLines = end - start;
            int overlap = Math.min(OVERLAP_LINES, Math.max(1, windowLines / 4));
            start = end - overlap;
        }
        return List.copyOf(chunks);
    }

    private static String join(String[] lines, int start, int end) {
        StringBuilder content = new StringBuilder();
        for (int index = start; index < end; index++) {
            if (index > start) {
                content.append('\n');
            }
            content.append(lines[index]);
        }
        return content.toString();
    }

    private static void splitLongLine(RepositoryFile file, String sourceLine, int lineNumber,
                                      List<CodeChunk> chunks) {
        int offset = 0;
        int segmentOrdinal = 0;
        int previousLength = MAX_CHARS + 1;
        while (offset < sourceLine.length()) {
            int segmentLimit = Math.min(MAX_CHARS - segmentOrdinal, previousLength - 1);
            if (segmentLimit <= 0) {
                throw new IllegalStateException("Code line exceeds the supported chunk count");
            }
            int limit = Math.min(offset + segmentLimit, sourceLine.length());
            if (limit < sourceLine.length() && limit > offset && Character.isHighSurrogate(sourceLine.charAt(limit - 1))) {
                limit--;
            }
            if (limit <= offset) {
                throw new IllegalStateException("Code chunking could not make progress");
            }
            chunks.add(createChunk(file, sourceLine.substring(offset, limit), lineNumber, lineNumber));
            previousLength = limit - offset;
            offset = limit;
            segmentOrdinal++;
        }
    }

    private static CodeChunk createChunk(RepositoryFile file, String content, int startLine, int endLine) {
        String contentHash = Hashing.sha256(content);
        String key = Hashing.sha256(file.path() + ":" + startLine + ":" + endLine + ":" + contentHash);
        return new CodeChunk(key, file.path(), file.language(), null, startLine, endLine, content,
                contentHash, (content.length() + 3) / 4);
    }
}
