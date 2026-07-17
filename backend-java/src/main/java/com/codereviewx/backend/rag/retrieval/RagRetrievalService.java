package com.codereviewx.backend.rag.retrieval;

public interface RagRetrievalService {
    RagRetrievalResult retrieve(RagRetrievalRequest request);
}
