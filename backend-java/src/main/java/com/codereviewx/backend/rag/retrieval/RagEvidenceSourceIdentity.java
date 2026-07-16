package com.codereviewx.backend.rag.retrieval;

import com.fasterxml.jackson.annotation.JsonIgnoreType;

@JsonIgnoreType
public record RagEvidenceSourceIdentity(Long chunkId, String contentHash) {
    public static RagEvidenceSourceIdentity unknown() { return new RagEvidenceSourceIdentity(null, null); }
    @Override public String toString() { return "RagEvidenceSourceIdentity[redacted]"; }
}
