package com.codereviewx.backend.rag.retrieval;

import java.util.List;

public interface RerankClient {

    List<RerankedChunk> rerank(String query, List<RerankCandidate> candidates);
}
