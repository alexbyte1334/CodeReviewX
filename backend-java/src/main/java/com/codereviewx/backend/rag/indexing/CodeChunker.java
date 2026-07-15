package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.rag.model.CodeChunk;
import com.codereviewx.backend.rag.model.RepositoryFile;

import java.util.List;

public interface CodeChunker {
    List<CodeChunk> chunk(RepositoryFile file);
}
