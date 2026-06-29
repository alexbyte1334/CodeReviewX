package com.codereviewx.backend.review.service;

import com.codereviewx.backend.review.enums.ReviewMode;
import com.codereviewx.backend.review.enums.ReviewTaskStatus;
import com.codereviewx.backend.review.github.GithubProperties;
import com.codereviewx.backend.review.persistence.entity.ReviewProviderTraceEntity;
import com.codereviewx.backend.review.persistence.entity.ReviewTaskEntity;
import com.codereviewx.backend.review.persistence.repository.ReviewProviderTraceRepository;
import com.codereviewx.backend.review.persistence.repository.ReviewToolTraceRepository;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReviewTraceRecorderTest {

    @Test
    void recordProviderTrace_unknownProviderDoesNotPersistNullFallbackReason() {
        ReviewToolTraceRepository toolTraceRepository = mock(ReviewToolTraceRepository.class);
        ReviewProviderTraceRepository providerTraceRepository = mock(ReviewProviderTraceRepository.class);
        ReviewTraceRecorder recorder = new ReviewTraceRecorder(
                toolTraceRepository,
                providerTraceRepository,
                new GithubProperties()
        );

        ReviewProviderResult providerResult = new ReviewProviderResult(
                List.of(),
                "UnexpectedProvider",
                true,
                null,
                "mimo",
                false
        );
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setRepoUrl("https://github.com/example/repo");
        task.setPrNumber(7);
        task.setReviewMode(ReviewMode.MANUAL_DIFF);
        task.setStatus(ReviewTaskStatus.SUCCESS);

        LocalDateTime startedAt = LocalDateTime.now();
        recorder.recordProviderTrace(99L, task, providerResult, startedAt, startedAt.plusNanos(5_000_000));

        ArgumentCaptor<ReviewProviderTraceEntity> traceCaptor =
                ArgumentCaptor.forClass(ReviewProviderTraceEntity.class);
        verify(providerTraceRepository).save(traceCaptor.capture());
        ReviewProviderTraceEntity trace = traceCaptor.getValue();
        assertThat(trace.getProviderUsed()).isNull();
        assertThat(trace.getProviderHit()).isFalse();
        assertThat(trace.getFallbackReason())
                .isEqualTo("Requested provider was not fulfilled; provider used is unknown.");
        assertThat(trace.getFallbackReason()).doesNotContain("null provider");
    }
}
