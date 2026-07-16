package com.codereviewx.backend.rag.service;

import com.codereviewx.backend.rag.config.RagProperties;
import com.codereviewx.backend.rag.retrieval.*;
import com.codereviewx.backend.review.github.*;
import com.codereviewx.backend.review.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagReviewContextFacadeTest {
    @Test
    void readyRecordsExactRagTraceAndPropagatesInternalFailure() {
        Fixture fixture = new Fixture();
        when(fixture.index.ensureIndexed(any())).thenReturn(new RagIndexResolution(1, 2, "a".repeat(40), RagIndexResolution.Status.READY));
        when(fixture.retrieval.retrieve(any())).thenReturn(new HybridRagRetrievalService.Result(
                HybridRagRetrievalService.Status.READY, 3L, 1, 1, List.of(), RagContextAssembler.RetrievalHealth.SINGLE_ROUTE_FAILED));

        fixture.facade.prepare(metadata(), diff(), 9L);

        var names = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(fixture.traces, times(5)).recordToolTrace(eq(9L), anyInt(), names.capture(), any(), any(),
                isNull(), isNull(), any(), any());
        assertThat(names.getAllValues()).containsExactly("rag.index.ensure", "rag.query.build", "rag.retrieve.hybrid",
                "rag.rerank", "rag.context.assemble");
        verify(fixture.legacy, never()).index(any(), any());

        reset(fixture.retrieval);
        when(fixture.retrieval.retrieve(any())).thenThrow(new IllegalArgumentException("programming bug"));
        assertThatThrownBy(() -> fixture.facade.prepare(metadata(), diff(), 10L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("programming bug");
        verify(fixture.legacy, never()).index(any(), any());
    }

    @Test
    void pollingTimeoutUsesInjectedClockWithoutWallClockWaitAndFallsBack() {
        MutableClock clock = new MutableClock();
        Fixture fixture = new Fixture(clock);
        when(fixture.index.ensureIndexed(any())).thenReturn(new RagIndexResolution(1, 2, "a".repeat(40), RagIndexResolution.Status.QUEUED));
        when(fixture.index.getJob(2)).thenReturn(new RagIndexJob(2, 1, "ref", "a".repeat(40), RagIndexJob.Status.RUNNING,
                1, null, null, null, null, null, "m", 1024, 1));
        when(fixture.legacy.index(any(), any())).thenReturn(RepositoryContextIndexResult.empty());

        RagReviewContextFacade.PreparedContext result = fixture.facade.prepare(metadata(), diff(), 9L);

        assertThat(result.legacyFallback()).isTrue();
        assertThat(clock.millis()).isEqualTo(20_000);
        verify(fixture.index, atMost(81)).getJob(2);
    }

    private static GithubPrMetadata metadata() { return new GithubPrMetadata("owner", "repo", 1, "title", "a", "main", "f", "b".repeat(40), "a".repeat(40), "open", "x", "x", 1, 1, 1); }
    private static GithubPrDiff diff() { return new GithubPrDiff("diff --git a/A b/A\n@@ -1 +1 @@\n+x", 1, 20, false, List.of()); }

    private static final class Fixture {
        final RagIndexService index = mock(RagIndexService.class);
        final HybridRagRetrievalService retrieval = mock(HybridRagRetrievalService.class);
        final RagContextAssembler assembler = new RagContextAssembler((query, candidates) -> {
            throw new IllegalStateException("rerank unavailable");
        });
        final RepositoryContextIndexService legacy = mock(RepositoryContextIndexService.class);
        final ReviewTraceRecorder traces = mock(ReviewTraceRecorder.class);
        final RagReviewContextFacade facade;
        Fixture() { this(new MutableClock()); }
        @SuppressWarnings("unchecked") Fixture(MutableClock clock) {
            RagProperties properties = new RagProperties(); properties.setEnabled(true);
            ObjectProvider<RagIndexService> indexes = mock(ObjectProvider.class);
            ObjectProvider<HybridRagRetrievalService> retrievals = mock(ObjectProvider.class);
            ObjectProvider<RagContextAssembler> assemblers = mock(ObjectProvider.class);
            when(indexes.getIfAvailable()).thenReturn(index); when(retrievals.getIfAvailable()).thenReturn(retrieval);
            when(assemblers.getIfAvailable()).thenReturn(assembler);
            facade = new RagReviewContextFacade(properties, indexes, retrievals, assemblers, legacy, traces,
                    clock, millis -> clock.advance(millis), Duration.ofSeconds(20), Duration.ofMillis(250));
        }
    }
    private static final class MutableClock extends Clock {
        long millis;
        void advance(long value) { millis += value; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(millis); }
        public long millis() { return millis; }
    }
}
