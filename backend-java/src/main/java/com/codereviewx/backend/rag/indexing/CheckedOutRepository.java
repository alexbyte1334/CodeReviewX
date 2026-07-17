package com.codereviewx.backend.rag.indexing;

import org.eclipse.jgit.lib.Repository;

import java.nio.file.Path;
import java.util.Objects;

public final class CheckedOutRepository implements AutoCloseable {

    private final Path path;
    private final String commitSha;
    private final Repository repository;
    private final AutoCloseable cleanup;
    private boolean repositoryClosed;
    private boolean closed;

    CheckedOutRepository(Path path, String commitSha, Repository repository) {
        this(path, commitSha, repository, null);
    }

    CheckedOutRepository(Path path, String commitSha, Repository repository, AutoCloseable cleanup) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        this.commitSha = Objects.requireNonNull(commitSha, "commitSha");
        this.repository = repository;
        this.cleanup = cleanup;
    }

    static CheckedOutRepository unmanaged(Path path, String commitSha) {
        return new CheckedOutRepository(path, commitSha, null);
    }

    public Path path() {
        return path;
    }

    public String commitSha() {
        return commitSha;
    }

    Repository repository() {
        return repository;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        if (repository != null && !repositoryClosed) {
            repository.close();
            repositoryClosed = true;
        }
        if (cleanup != null) {
            try {
                cleanup.close();
            } catch (Exception exception) {
                throw new IllegalStateException("Repository cleanup failed");
            }
        }
        closed = true;
    }
}
