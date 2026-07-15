package com.codereviewx.backend.rag.model;

import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CheckedOutRepository implements AutoCloseable {

    private final Path path;
    private final String commitSha;
    private final Repository repository;
    private final boolean cleanup;
    private final AtomicBoolean closed = new AtomicBoolean();

    public CheckedOutRepository(Path path, String commitSha, Repository repository) {
        this(path, commitSha, repository, true);
    }

    private CheckedOutRepository(Path path, String commitSha, Repository repository, boolean cleanup) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        this.commitSha = Objects.requireNonNull(commitSha, "commitSha");
        this.repository = repository;
        this.cleanup = cleanup;
    }

    public static CheckedOutRepository unmanaged(Path path, String commitSha) {
        return new CheckedOutRepository(path, commitSha, null, false);
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
        if (cleanup) {
            deleteTree(path);
        }
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                    if (failure != null) {
                        throw failure;
                    }
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Repository cleanup failed");
        }
    }
}
