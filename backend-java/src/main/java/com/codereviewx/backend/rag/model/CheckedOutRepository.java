package com.codereviewx.backend.rag.model;

import org.eclipse.jgit.lib.Repository;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CheckedOutRepository implements AutoCloseable {

    private final Path path;
    private final String commitSha;
    private final Repository repository;
    private final AutoCloseable cleanup;
    private final AtomicBoolean closed = new AtomicBoolean();

    public CheckedOutRepository(Path path, String commitSha, Repository repository) {
        this(path, commitSha, repository, null);
    }

    public CheckedOutRepository(Path path, String commitSha, Repository repository, AutoCloseable cleanup) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        this.commitSha = Objects.requireNonNull(commitSha, "commitSha");
        this.repository = repository;
        this.cleanup = cleanup;
    }

    public static CheckedOutRepository unmanaged(Path path, String commitSha) {
        return new CheckedOutRepository(path, commitSha, null);
    }

    public Path path() {
        return path;
    }

    public String commitSha() {
        return commitSha;
    }

    public Repository repository() {
        return repository;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (repository != null) {
            repository.close();
        }
        if (cleanup != null) {
            try {
                cleanup.close();
            } catch (Exception exception) {
                throw new IllegalStateException("Repository cleanup failed");
            }
        }
    }
}
