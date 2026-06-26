package com.codereviewx.backend.review.pipeline.provider;

import com.codereviewx.backend.review.config.ReviewProperties;
import com.codereviewx.backend.review.pipeline.ReviewContext;
import com.codereviewx.backend.review.pipeline.ReviewProviderResult;
import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoClientException;
import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoParseException;
import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoProperties;
import com.codereviewx.backend.review.pipeline.provider.mimo.XiaomiMiMoReviewProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigurableReviewProviderTest {

    private ReviewProperties reviewProperties;
    private XiaomiMiMoProperties mimoProperties;
    private MockReviewProvider mockReviewProvider;
    private XiaomiMiMoReviewProvider xiaomiMiMoReviewProvider;
    private ConfigurableReviewProvider provider;
    private ReviewContext context;
    private ReviewProviderResult mockResult;
    private ReviewProviderResult mimoResult;

    @BeforeEach
    void setUp() {
        reviewProperties = new ReviewProperties();
        mimoProperties = new XiaomiMiMoProperties();
        mockReviewProvider = mock(MockReviewProvider.class);
        xiaomiMiMoReviewProvider = mock(XiaomiMiMoReviewProvider.class);
        provider = new ConfigurableReviewProvider(
                reviewProperties,
                mimoProperties,
                mockReviewProvider,
                xiaomiMiMoReviewProvider
        );
        context = new ReviewContext(1L, "https://github.com/example/repo", 9, LocalDateTime.now());
        mockResult = new ReviewProviderResult(java.util.List.of(), MockReviewProvider.PROVIDER_NAME, true, null);
        mimoResult = new ReviewProviderResult(java.util.List.of(), XiaomiMiMoReviewProvider.PROVIDER_NAME, true, null);
    }

    @Test
    void defaultModeUsesMockProvider() {
        reviewProperties.setProvider("mock");

        when(mockReviewProvider.review(context)).thenReturn(mockResult);

        ReviewProviderResult result = provider.review(context);

        assertThat(result.getProviderName()).isEqualTo(MockReviewProvider.PROVIDER_NAME);
        verify(mockReviewProvider).review(context);
        verify(xiaomiMiMoReviewProvider, never()).review(context);
    }

    @Test
    void explicitMockModeUsesMockProvider() {
        reviewProperties.setProvider("MOCK");

        when(mockReviewProvider.review(context)).thenReturn(mockResult);

        provider.review(context);

        verify(mockReviewProvider).review(context);
        verify(xiaomiMiMoReviewProvider, never()).review(context);
    }

    @Test
    void mimoModeWithKeyAttemptsMiMoProvider() {
        reviewProperties.setProvider("mimo");
        mimoProperties.setApiKey("test-key");

        when(xiaomiMiMoReviewProvider.review(context)).thenReturn(mimoResult);

        ReviewProviderResult result = provider.review(context);

        assertThat(result.getProviderName()).isEqualTo(XiaomiMiMoReviewProvider.PROVIDER_NAME);
        verify(xiaomiMiMoReviewProvider).review(context);
        verify(mockReviewProvider, never()).review(context);
    }

    @Test
    void mimoModeWithoutKeyFallsBackToMock() {
        reviewProperties.setProvider("mimo");
        mimoProperties.setApiKey("");

        when(mockReviewProvider.review(context)).thenReturn(mockResult);

        ReviewProviderResult result = provider.review(context);

        assertThat(result.getProviderName()).isEqualTo(MockReviewProvider.PROVIDER_NAME);
        verify(mockReviewProvider).review(context);
        verify(xiaomiMiMoReviewProvider, never()).review(context);
    }

    @Test
    void mimoModeClientFailureFallsBackToMock() {
        reviewProperties.setProvider("mimo");
        mimoProperties.setApiKey("test-key");

        when(xiaomiMiMoReviewProvider.review(context))
                .thenThrow(new XiaomiMiMoClientException("network failure"));
        when(mockReviewProvider.review(context)).thenReturn(mockResult);

        ReviewProviderResult result = provider.review(context);

        assertThat(result.getProviderName()).isEqualTo(MockReviewProvider.PROVIDER_NAME);
        verify(mockReviewProvider).review(context);
    }

    @Test
    void mimoModeParserFailureFallsBackToMock() {
        reviewProperties.setProvider("mimo");
        mimoProperties.setApiKey("test-key");

        when(xiaomiMiMoReviewProvider.review(context))
                .thenThrow(new XiaomiMiMoParseException("invalid json"));
        when(mockReviewProvider.review(context)).thenReturn(mockResult);

        ReviewProviderResult result = provider.review(context);

        assertThat(result.getProviderName()).isEqualTo(MockReviewProvider.PROVIDER_NAME);
        verify(mockReviewProvider).review(context);
    }

    @Test
    void unknownProviderModeFallsBackToMockBehavior() {
        reviewProperties.setProvider("unknown");

        when(mockReviewProvider.review(context)).thenReturn(mockResult);

        provider.review(context);

        verify(mockReviewProvider).review(context);
        verify(xiaomiMiMoReviewProvider, never()).review(context);
    }
}
