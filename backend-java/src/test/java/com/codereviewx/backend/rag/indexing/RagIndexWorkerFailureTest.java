package com.codereviewx.backend.rag.indexing;

import com.codereviewx.backend.rag.embedding.EmbeddingClient;
import com.codereviewx.backend.rag.model.Language;
import com.codereviewx.backend.rag.model.RepositoryFile;
import com.codereviewx.backend.rag.persistence.RagChunkStore;
import com.codereviewx.backend.rag.persistence.RagDocumentStore;
import com.codereviewx.backend.rag.persistence.RagIndexJobStore;
import com.codereviewx.backend.rag.persistence.RagRepositoryStore;
import com.codereviewx.backend.rag.service.RagIndexJob;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagIndexWorkerFailureTest {
    @Test
    void checkoutFailureUsesStableCodeAndRedactedMessage() {
        Fixture fixture = new Fixture();
        RepositoryCheckoutService checkout = metadata -> { throw new IllegalStateException("https://token@host/private"); };
        fixture.worker(checkout, ignored -> List.of(), texts -> List.of()).process(fixture.job());

        verify(fixture.jobs).fail(11, 1, "CHECKOUT_FAILED", "Repository checkout failed");
    }

    @Test
    void embeddingFailureUsesStableCodeAndRedactedMessage() {
        Fixture fixture = new Fixture();
        RepositoryFile file = new RepositoryFile("src/A.java", Language.JAVA, "class A {}", 10, "hash");
        EmbeddingClient embedding = texts -> { throw new IllegalStateException("api-key=secret-value"); };
        fixture.worker(null, ignored -> List.of(file), embedding).process(fixture.job());

        verify(fixture.jobs).fail(11, 1, "EMBEDDING_UNAVAILABLE", "Embedding service unavailable");
    }

    private static final class Fixture {
        private final RagRepositoryStore repositories = mock(RagRepositoryStore.class);
        private final RagIndexJobStore jobs = mock(RagIndexJobStore.class);
        private final RagDocumentStore documents = mock(RagDocumentStore.class);
        private final RagChunkStore chunks = mock(RagChunkStore.class);
        private final TransactionTemplate transactions = mock(TransactionTemplate.class);
        private final RagRepositoryStore.RepositoryRecord repository = new RagRepositoryStore.RepositoryRecord(
                7, "github", "owner", "repo", "https://example.invalid/repo.git", "main", null,
                "PENDING", 1, "test-model", 1024);

        private Fixture() {
            when(repositories.get(7)).thenReturn(Optional.of(repository));
            doAnswer(invocation -> { invocation.<java.util.function.Consumer<Object>>getArgument(0).accept(null); return null; })
                    .when(transactions).executeWithoutResult(any());
        }

        private RagIndexWorker worker(RepositoryCheckoutService checkout,
                                      java.util.function.Function<CheckedOutRepository, List<RepositoryFile>> files,
                                      EmbeddingClient embedding) {
            return new RagIndexWorker(repositories, jobs, documents, chunks, checkout, files,
                    new LineWindowCodeChunker(), embedding, transactions, Clock.systemUTC(), "test-model", 1024,
                    null, null, new RagIndexLifecycleCoordinator(jobs::releaseForShutdown), null);
        }

        private RagIndexJob job() {
            return new RagIndexJob(11, 7, "abc", null, RagIndexJob.Status.RUNNING, 1,
                    null, null, null, null, null, "test-model", 1024, 1);
        }
    }
}
