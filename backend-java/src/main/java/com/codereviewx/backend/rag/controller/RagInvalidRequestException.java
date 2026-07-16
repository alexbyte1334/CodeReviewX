package com.codereviewx.backend.rag.controller;

public class RagInvalidRequestException extends RuntimeException {
    public RagInvalidRequestException() {
        super("Invalid RAG request");
    }
}
