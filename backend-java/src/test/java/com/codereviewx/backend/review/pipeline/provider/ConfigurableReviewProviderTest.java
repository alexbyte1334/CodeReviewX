package com.codereviewx.backend.review.pipeline.provider;

import com.codereviewx.backend.review.pipeline.ReviewContext;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoReviewProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigurableReviewProviderTest {

    private XiaomiMiMoReviewProvider xiaomiMiMoReviewProvider;
    private ConfigurableReviewProvider provider;
    private ReviewContext context;

    @BeforeEach
    void setUp() {
        xiaomiMiMoReviewProvider = mock(XiaomiMiMoReviewProvider.class);
        provider = new ConfigurableReviewProvider(xiaomiMiMoReviewProvider);
        context = new ReviewContext(1L, "https://github.com/example/repo", 9, LocalDateTime.now(), null, "mock");
    }

    @Test
    void review_alwaysDelegatesToMimoProviderEvenWhenLegacyRequestAsksForMock() {
        ReviewProviderResult mimoResult = new ReviewProviderResult(
                java.util.List.of(),
                XiaomiMiMoReviewProvider.PROVIDER_NAME,
                true,
                null
        );
        when(xiaomiMiMoReviewProvider.review(context)).thenReturn(mimoResult);

        ReviewProviderResult result = provider.review(context);

        assertThat(result.getProviderName()).isEqualTo(XiaomiMiMoReviewProvider.PROVIDER_NAME);
        assertThat(result.getRequestedProvider()).isEqualTo("mimo");
        assertThat(result.getProviderUsed()).isEqualTo("mimo");
        assertThat(result.isProviderHit()).isTrue();
        verify(xiaomiMiMoReviewProvider).review(context);
    }

    @Test
    void review_unknownProviderNameDoesNotPretendMockFallback() {
        ReviewProviderResult unknownResult = new ReviewProviderResult(
                java.util.List.of(),
                "UnexpectedProvider",
                true,
                null
        );
        when(xiaomiMiMoReviewProvider.review(context)).thenReturn(unknownResult);

        ReviewProviderResult result = provider.review(context);

        assertThat(result.getProviderName()).isEqualTo("UnexpectedProvider");
        assertThat(result.getRequestedProvider()).isEqualTo("mimo");
        assertThat(result.getProviderUsed()).isNull();
        assertThat(result.isProviderHit()).isFalse();
    }
}
