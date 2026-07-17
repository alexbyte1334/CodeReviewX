package com.codereviewx.backend.rag.indexing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckedOutRepositoryTest {

    @TempDir Path tempDir;

    @Test
    void retriesCleanupAfterFailureAndBecomesIdempotentOnlyAfterSuccess() {
        AtomicInteger attempts = new AtomicInteger();
        CheckedOutRepository repository = new CheckedOutRepository(
                tempDir, "0".repeat(40), null, () -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("first attempt fails");
                    }
                });

        assertThatThrownBy(repository::close).hasMessage("Repository cleanup failed");
        repository.close();
        repository.close();

        assertThat(attempts).hasValue(2);
    }
}
