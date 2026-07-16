package com.codereviewx.backend.rag.dto;

import java.util.List;

public record RetrievalTraceResponse(boolean degraded, String degradedReason, long latencyMs,
                                     int candidateCount, int selectedCount, String model,
                                     List<Evidence> evidence) {
    public record Evidence(String citationLabel, String path, int startLine, int endLine,
                           String excerpt, int rank, double score) {}
}
